package dev.fadalias.core

import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.content.pm.ShortcutInfo
import android.graphics.Rect
import android.os.Binder
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import dev.fadalias.Identity
import dev.fadalias.state.HideScope
import dev.fadalias.state.Overlay

/**
 * Installs every system_server hook and owns the hot-reload lifecycle.
 *
 * Hooks, grouped by class (verified against AOSP Android 11 - 17):
 *  - LauncherAppsService$LauncherAppsImpl
 *      getUserProfiles / canAccessProfile(2 forms) / ensureInUserProfiles / canAccessHiddenProfile /
 *      injectHasInteractAcrossUsersFullPermission     -> manager sees every profile
 *      getLauncherActivities                          -> IPC, launcher signals, hiding and names in ONE pass
 *      getShortcuts / resolve* / isPackageEnabled / isActivityEnabled / getApplicationInfo /
 *      shouldHideFromSuggestions / getActivityLaunchIntent -> hiding for every other entry point
 *  - UserManagerService.getProfileIds                  -> manager sees every profile id
 *  - PackageInfoUtils (or PackageParser)               -> names, see [NameHooks]
 */
object LauncherHook {
    private const val TARGET_CLASS = "com.android.server.pm.LauncherAppsService\$LauncherAppsImpl"
    private const val USER_MANAGER_SERVICE_CLASS = "com.android.server.pm.UserManagerService"

    internal const val RESUMER_KEY = "dev.hfw.lifecycle_resumer"
    internal const val RETIRER_KEY = "dev.hfw.lifecycle_retirer"

    private val abortRecoveryTriggered = java.util.concurrent.atomic.AtomicBoolean(false)
    private val hideGuardErrorLogged = java.util.concurrent.atomic.AtomicBoolean(false)
    private val hideSupported: Boolean get() = android.os.Build.VERSION.SDK_INT >= 30

    // ---- install -------------------------------------------------------------------------------------------------

    internal fun install(classLoader: ClassLoader, hookBackend: HookBackend): Boolean {
        synchronized(Runtime.lifecycleLock) {
            Runtime.backend = hookBackend
            Runtime.acceptingReload = false
            Runtime.onReloadTimeout = { triggerAbortRecoveryAsync() }
            stopEngineLocked()
        }
        Callers.invalidate()
        Notifier.reset()
        HealthLog.resetHooks()
        // Synchronous: no hook may ever run against an empty default while the real state sits on disk.
        val state = StateStore.load()
        HealthLog.event("state loaded from ${StateStore.loadedFrom} (rev ${state.rev}, ${state.explicitHideCount} hide choices, ${state.apps.size} app rules)")

        val clazz = hookBackend.findClassIfExists(TARGET_CLASS, classLoader)
        if (clazz == null) {
            HealthLog.event("target class not found: $TARGET_CLASS")
            HealthLog.hook("LauncherAppsImpl", HealthLog.FAILED)
            return false
        }
        val h = Hooks(hookBackend)
        h.group("profiles") { hookGetUserProfiles(h, clazz); hookUserManagerGetProfileIds(h, classLoader); hookProfileAccessChecks(h, clazz) }
        h.group("launcherActivities") { hookGetLauncherActivities(h, clazz) }
        if (hideSupported) {
            h.group("shortcuts") { hookGetShortcuts(h, clazz); hookShortcutService(h, classLoader); hookAppWidgets(h, classLoader) }
            h.group("resolve") { hookResolveAndEnabledChecks(h, clazz); hookQueryIntentActivities(h, classLoader) }
        }
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            h.group("overlays") { hookIdmapDaemon(h, classLoader) }
        }
        h.group("names") { if (NameHooks.install(classLoader) == 0) h.ok = false }

        HealthLog.event(if (h.ok) "hooks installed (SDK ${android.os.Build.VERSION.SDK_INT}, API ${Runtime.apiVersion})" else "hooks installed with failures: ${HealthLog.hooksJson()}")
        Work.execute { StateStore.pruneOrphanedIcons() }
        return h.ok
    }

    /** Collects hook installation outcomes: a missing optional method is fine, a hook that fails to install is not. */
    private class Hooks(val be: HookBackend) {
        var ok = true

        fun group(name: String, block: () -> Unit) {
            try {
                block()
            } catch (t: Throwable) {
                ok = false
                HealthLog.hook("group.$name", HealthLog.FAILED)
                HealthLog.event("$name hook group failed", t)
            }
        }

        /** Hook a method that only exists on some Android versions. */
        fun optional(name: String, clazz: Class<*>, method: String, vararg types: Class<*>, cb: HookCallback) {
            try {
                be.findAndHookMethod(clazz, method, *types, callback = cb)
                HealthLog.hook(name, HealthLog.OK)
            } catch (_: NoSuchMethodException) {
                HealthLog.hook(name, HealthLog.MISSING)
            } catch (t: Throwable) {
                ok = false
                HealthLog.hook(name, HealthLog.FAILED)
                HealthLog.event("hook $name failed", t)
            }
        }

        /** Hook a method every supported Android version has. */
        fun required(name: String, clazz: Class<*>, method: String, vararg types: Class<*>, cb: HookCallback) {
            try {
                be.findAndHookMethod(clazz, method, *types, callback = cb)
                HealthLog.hook(name, HealthLog.OK)
            } catch (t: Throwable) {
                ok = false
                HealthLog.hook(name, HealthLog.FAILED)
                HealthLog.event("required hook $name failed", t)
            }
        }
    }

    private val INT = Int::class.javaPrimitiveType!!

    // ---- profiles ------------------------------------------------------------------------------------------------

    /** Android 15-17: LauncherApps.getProfiles() asks the service; add every user so the manager sees them all. */
    private fun hookGetUserProfiles(h: Hooks, clazz: Class<*>) {
        h.optional("LauncherApps.getUserProfiles", clazz, "getUserProfiles", cb = object : HookCallback() {
            override fun after(param: HookParam) {
                try {
                    if (!Callers.isSelf(Binder.getCallingUid(), param.thisObject)) return
                    val existing = (param.result as? List<*>)?.filterIsInstance<UserHandle>() ?: emptyList()
                    val merged = LinkedHashSet<UserHandle>(existing)
                    merged.addAll(Notifier.userHandles(param.thisObject ?: return, null))
                    param.setResult(ArrayList(merged))
                } catch (t: Throwable) {
                    HealthLog.event("getUserProfiles hook failed", t)
                }
            }
        })
    }

    /** Android 11-17: UserManagerService.getProfileIds(int, boolean) used by LauncherApps/UserManager. */
    private fun hookUserManagerGetProfileIds(h: Hooks, classLoader: ClassLoader) {
        val ums = h.be.findClassIfExists(USER_MANAGER_SERVICE_CLASS, classLoader)
        if (ums == null) {
            HealthLog.hook("UserManager.getProfileIds", HealthLog.MISSING)
            return
        }
        h.optional("UserManager.getProfileIds", ums, "getProfileIds", INT, Boolean::class.javaPrimitiveType!!, cb = object : HookCallback() {
            override fun after(param: HookParam) {
                try {
                    if (!Callers.isSelf(Binder.getCallingUid(), param.thisObject)) return
                    val base = param.result as? IntArray ?: IntArray(0)
                    val all = try { Runtime.be().callMethod(param.thisObject!!, "getUserIds") as? IntArray } catch (_: Throwable) { null } ?: return
                    val merged = LinkedHashSet<Int>()
                    for (id in base) merged.add(id)
                    for (id in all) merged.add(id)
                    param.setResult(merged.toIntArray())
                } catch (_: Throwable) {
                }
            }
        })
    }

    /** Lets the verified manager through every profile access check (secondary users, work, clone, private space). */
    private fun hookProfileAccessChecks(h: Hooks, clazz: Class<*>) {
        fun allowSelf(uidArg: Int?): HookCallback = object : HookCallback() {
            override fun before(param: HookParam) {
                try {
                    val binderUid = Binder.getCallingUid()
                    val argUid = if (uidArg == null) binderUid else (param.args[uidArg] as? Int ?: binderUid)
                    if (argUid == binderUid && Callers.isSelf(binderUid, param.thisObject)) param.setResult(true)
                } catch (_: Throwable) {
                }
            }
        }
        h.optional("LauncherApps.canAccessProfile(2)", clazz, "canAccessProfile", INT, String::class.java, cb = allowSelf(null))
        h.optional("LauncherApps.canAccessProfile(5)", clazz, "canAccessProfile", INT, INT, INT, INT, String::class.java, cb = allowSelf(0))
        h.optional("LauncherApps.ensureInUserProfiles", clazz, "ensureInUserProfiles", INT, String::class.java, cb = object : HookCallback() {
            override fun before(param: HookParam) {
                try {
                    if (Callers.isSelf(Binder.getCallingUid(), param.thisObject)) param.setResult(null)
                } catch (_: Throwable) {
                }
            }
        })
        h.optional("LauncherApps.canAccessHiddenProfile", clazz, "canAccessHiddenProfile", INT, INT, cb = allowSelf(0))
        h.optional("LauncherApps.injectHasInteractAcrossUsersFullPermission", clazz, "injectHasInteractAcrossUsersFullPermission", INT, INT, cb = allowSelf(1))
    }

    // ---- getLauncherActivities: IPC, signals, hiding and names in one pass ---------------------------------------

    private fun hookGetLauncherActivities(h: Hooks, clazz: Class<*>) {
        h.required("LauncherApps.getLauncherActivities", clazz, "getLauncherActivities",
            String::class.java, String::class.java, UserHandle::class.java, cb = object : HookCallback() {
                override fun before(param: HookParam) {
                    try {
                        Callers.leaveSelfScope()
                        Runtime.remember(param.thisObject)
                        if (Callers.roleOf(Binder.getCallingUid(), param.thisObject) == Role.SELF) Callers.enterSelfScope()
                        val target = param.args[1] as? String ?: return
                        if (!target.startsWith(Identity.IPC_PREFIX)) return
                        Ipc.before(param, param.args[0] as? String, target, param.args[2] as? UserHandle)
                    } catch (t: Throwable) {
                        HealthLog.event("getLauncherActivities before failed", t)
                    }
                }

                override fun after(param: HookParam) {
                    try {
                        onLauncherActivities(param)
                    } catch (t: Throwable) {
                        HealthLog.event("getLauncherActivities after failed", t)
                    } finally {
                        Callers.leaveSelfScope()
                    }
                }
            })
    }

    private fun onLauncherActivities(param: HookParam) {
        val impl = param.thisObject
        val uid = Binder.getCallingUid()

        // 1) a manager command: attach its reply, or answer the all-users query
        (param.getObjectExtra(Ipc.EXTRA_REPLY) as? String)?.let { attachReply(param, it, uid); return }
        if (param.getObjectExtra(Ipc.EXTRA_ALL_USERS) == true) {
            if (Callers.isSelf(uid, impl)) allUsers(param)
            return
        }

        // 2) who asks? The manager sees the unmodified list.
        val role = Callers.roleOf(uid, impl)
        if (role == Role.SELF) return
        val be = Runtime.be()
        val target = param.args[1] as? String
        val user = param.args[2] as? UserHandle
        val userId = userIdOf(user, be)

        // 3) launcher query signal: a full-list query schedules the pulse, a query for one package confirms a change
        if (role != Role.SYSTEM) {
            if (target.isNullOrEmpty()) Notifier.pulse(impl, user, userId)
            else if (role == Role.LAUNCHER) Notifier.confirm(target)
        }

        // 4) one pass: drop hidden entries (never for the system), then apply custom names/icons to the rest
        val slice = param.result ?: return
        val raw = Slices.list(slice) ?: return
        if (raw.isEmpty()) return
        val ctx = Runtime.contextOf(impl)
        val snap = StateStore.snapshot
        val kept = ArrayList<Any>(raw.size)
        var modified = false
        for (item in raw) {
            if (item == null) continue
            val ai = Slices.activityInfo(item)
            val pkg = ai?.packageName ?: target
            if (hideSupported && role != Role.SYSTEM && !pkg.isNullOrEmpty()) {
                val isModule = ModuleCache.isXposedModule(pkg, ai?.applicationInfo, ctx, user, be)
                if (snap.shouldHideScope(pkg, userId, isModule, HideScope.LAUNCHER)) {
                    modified = true
                    continue
                }
            }
            kept.add(item)
        }
        applyLooks(kept, snap, userId)
        if (modified) param.setResult(Slices.rebuild(slice, kept))
    }

    /**
     * Launcher entries are often activity-aliases with their own label and icon, which the generic ActivityInfo rewrite
     * deliberately skips. The launcher list is the one place where those are known to be launcher entries: a package with
     * a single launcher entry always gets the custom name; with several, only entries sharing the app's own label.
     */
    private fun applyLooks(items: List<Any>, snap: dev.fadalias.state.StateData, userId: Int) {
        if (snap.apps.isEmpty() && snap.labels[userId].isNullOrEmpty() && snap.icons[userId].isNullOrEmpty()) return
        val groups = LinkedHashMap<String, MutableList<Any>>()
        for (item in items) {
            val pkg = Slices.activityInfo(item)?.packageName ?: continue
            if (snap.apps.containsKey(pkg) || snap.labels[userId]?.containsKey(pkg) == true || snap.icons[userId]?.containsKey(pkg) == true) {
                groups.getOrPut(pkg) { ArrayList() }.add(item)
            }
        }
        for ((pkg, group) in groups) {
            val rule = snap.iconRuleOf(pkg, userId)
            val label = snap.labelOf(pkg, userId)
            val icon = (rule?.overlayRev ?: 0L) > 0L && rule?.overlayStatus == Overlay.OK
            if (label == null && !icon) continue
            val targets = if (group.size == 1) group else group.filter { Slices.activityInfo(it)?.let(NameHooks::labelApplies) == true }
            for (item in targets) {
                val orig = Slices.activityInfo(item) ?: continue
                val copy = ActivityInfo(orig)
                if (label != null) copy.nonLocalizedLabel = label
                val appIcon = copy.applicationInfo?.icon ?: 0
                if (icon && appIcon != 0) copy.icon = appIcon // the launcher then loads the app icon, which carries the overlay
                Slices.setActivityInfo(item, copy)
            }
        }
    }

    /** Attaches [json] to copies of the reply's ActivityInfo/ApplicationInfo so no cached PackageManager object is mutated. */
    private fun attachReply(param: HookParam, json: String, uid: Int) {
        if (!Callers.isSelf(uid, param.thisObject)) return
        val slice = param.result ?: return
        val item = Slices.list(slice)?.firstOrNull() ?: return
        val ai = Slices.activityInfo(item) ?: return
        val appCopy = ApplicationInfo(ai.applicationInfo ?: return)
        appCopy.metaData = Bundle().apply { putString(Identity.REPLY_META_KEY, json) }
        val aiCopy = ActivityInfo(ai)
        aiCopy.applicationInfo = appCopy
        Slices.setActivityInfo(item, aiCopy)
    }

    /** The manager asked for every user's launcher activities in one call. */
    private fun allUsers(param: HookParam) {
        val impl = param.thisObject ?: return
        val user = param.args[2] as? UserHandle
        val userId = userIdOf(user, Runtime.be())
        val slice = param.result ?: return
        val base = Slices.list(slice) ?: emptyList<Any>()
        val combined = ArrayList<Any>(base.filterNotNull())
        val expected = base.firstOrNull()?.javaClass
        for (profile in Notifier.userHandles(impl, user)) {
            if (userIdOf(profile, Runtime.be()) == userId) continue
            for (item in queryLauncherActivitiesForProfile(impl, profile)) {
                if (item != null && (expected == null || expected.isInstance(item))) combined.add(item)
            }
        }
        param.setResult(Slices.rebuild(slice, combined))
    }

    private fun queryLauncherActivitiesForProfile(impl: Any, profile: UserHandle): List<*> {
        val be = Runtime.be()
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        // Android 12-17: queryIntentLauncherActivities(Intent, int filterCallingUid, UserHandle)
        val token = Binder.clearCallingIdentity()
        try {
            val res = be.callMethod(impl, "queryIntentLauncherActivities", intent, 1000, profile) as? List<*>
            if (!res.isNullOrEmpty()) return res
        } catch (_: Throwable) {
        } finally {
            Binder.restoreCallingIdentity(token)
        }
        // Android 11-17: queryActivitiesForUser(String, Intent, UserHandle); keeps the caller identity so the access hooks recognize the manager
        try {
            val s = be.callMethod(impl, "queryActivitiesForUser", Identity.PACKAGE, intent, profile)
            val list = if (s != null) Slices.list(s) else null
            if (!list.isNullOrEmpty()) return list
        } catch (_: Throwable) {
        }
        // Legacy 2-arg form
        try {
            val s = be.callMethod(impl, "queryActivitiesForUser", intent, profile)
            val list = if (s != null) Slices.list(s) else null
            if (!list.isNullOrEmpty()) return list
        } catch (_: Throwable) {
        }
        return emptyList<Any>()
    }

    // ---- shortcuts -------------------------------------------------------------------------------------------------

    private fun dev.fadalias.state.StateData.mayHideAnything(): Boolean =
        autoHide || hide.values.any { it.containsValue(true) } || hideScopes.values.any { it.values.any { m -> m != 0 } }

    private fun dev.fadalias.state.StateData.mayCustomizeLooks(): Boolean =
        apps.isNotEmpty() || labels.isNotEmpty() || icons.isNotEmpty()

    private fun filterSliceByPackage(
        param: HookParam,
        user: UserHandle?,
        userId: Int,
        fallbackPkg: String? = null,
        scope: Int,
    ) {
        val slice = param.result ?: return
        val snap = StateStore.snapshot
        if (!snap.mayHideAnything()) return
        val impl = param.thisObject
        if (Callers.isExempt(Binder.getCallingUid(), impl)) return
        val raw = Slices.list(slice) ?: return
        if (raw.isEmpty()) return
        val be = Runtime.be()
        val ctx = Runtime.contextOf(impl)
        var kept: ArrayList<Any>? = null
        for (i in raw.indices) {
            val item = raw[i] ?: continue
            val ai = Slices.activityInfo(item)
            val pkg = ai?.packageName
                ?: (item as? ShortcutInfo)?.`package`
                ?: fallbackPkg
                ?: (try { (be.callMethod(item, "getTargetComponent") as? ComponentName)?.packageName } catch (_: Throwable) { null })
            val hidden = !pkg.isNullOrEmpty() &&
                snap.shouldHideScope(pkg, userId, ModuleCache.isXposedModule(pkg, ai?.applicationInfo, ctx, user, be), scope)
            if (hidden) {
                if (kept == null) {
                    kept = ArrayList<Any>(raw.size).apply {
                        for (j in 0 until i) raw[j]?.let(::add)
                    }
                }
            } else {
                kept?.add(item)
            }
        }
        if (kept != null) param.setResult(Slices.rebuild(slice, kept))
    }

    private fun hookGetShortcuts(h: Hooks, clazz: Class<*>) {
        val cb = object : HookCallback() {
            override fun after(param: HookParam) {
                try {
                    val user = param.args.lastOrNull() as? UserHandle
                    filterSliceByPackage(param, user, userIdOf(user, Runtime.be()), scope = HideScope.SHORTCUTS)
                } catch (t: Throwable) {
                    HealthLog.event("getShortcuts hook failed", t)
                }
            }
        }
        // Android 12-17: getShortcuts(String, ShortcutQueryWrapper, UserHandle)
        val wrapper = h.be.findClassIfExists("android.content.pm.ShortcutQueryWrapper", clazz.classLoader ?: return)
        if (wrapper != null) h.optional("LauncherApps.getShortcuts(wrapper)", clazz, "getShortcuts", String::class.java, wrapper, UserHandle::class.java, cb = cb)
        else HealthLog.hook("LauncherApps.getShortcuts(wrapper)", HealthLog.MISSING)
        // Android 11: getShortcuts(String, long, String, List, ComponentName, int, UserHandle)
        h.optional("LauncherApps.getShortcuts(legacy)", clazz, "getShortcuts",
            String::class.java, Long::class.javaPrimitiveType!!, String::class.java, java.util.List::class.java,
            ComponentName::class.java, INT, UserHandle::class.java, cb = cb)
        // Android 11-17: getShortcutConfigActivities(String callingPackage, String packageName, UserHandle user)
        h.optional("LauncherApps.getShortcutConfigActivities", clazz, "getShortcutConfigActivities",
            String::class.java, String::class.java, UserHandle::class.java, cb = object : HookCallback() {
                override fun after(param: HookParam) {
                    try {
                        val user = param.args[2] as? UserHandle
                        filterSliceByPackage(param, user, userIdOf(user, Runtime.be()), param.args[1] as? String, scope = HideScope.SHORTCUT_CONFIGS)
                    } catch (t: Throwable) {
                        HealthLog.event("getShortcutConfigActivities hook failed", t)
                    }
                }
            })
    }

    private fun hookShortcutService(h: Hooks, classLoader: ClassLoader) {
        val ss = h.be.findClassIfExists("com.android.server.pm.ShortcutService", classLoader)
        if (ss == null) {
            HealthLog.hook("ShortcutService", HealthLog.MISSING)
            return
        }
        fun shortcutCb(scope: Int) = object : HookCallback() {
            override fun after(param: HookParam) {
                try {
                    val userId = (param.args.lastOrNull() as? Int) ?: return
                    filterSliceByPackage(param, null, userId, scope = scope)
                } catch (t: Throwable) {
                    HealthLog.event("ShortcutService hook failed", t)
                }
            }
        }
        h.optional("ShortcutService.getShortcuts", ss, "getShortcuts", String::class.java, INT, INT, cb = shortcutCb(HideScope.SHORTCUTS))
        h.optional("ShortcutService.getShareTargets", ss, "getShareTargets", String::class.java, IntentFilter::class.java, INT, cb = shortcutCb(HideScope.SHARE_TARGETS))
    }

    private fun hookAppWidgets(h: Hooks, classLoader: ClassLoader) {
        val aws = h.be.findClassIfExists("com.android.server.appwidget.AppWidgetServiceImpl", classLoader)
        if (aws == null) {
            HealthLog.hook("AppWidgetServiceImpl", HealthLog.MISSING)
            return
        }
        h.optional("AppWidgetService.getInstalledProvidersForProfile", aws, "getInstalledProvidersForProfile",
            INT, INT, String::class.java, cb = object : HookCallback() {
                override fun after(param: HookParam) {
                    try {
                        val slice = param.result ?: return
                        val snap = StateStore.snapshot
                        if (!snap.mayHideAnything()) return
                        if (Callers.isExempt(Binder.getCallingUid(), null)) return
                        val raw = Slices.list(slice) ?: return
                        if (raw.isEmpty()) return
                        val profileId = (param.args[1] as? Int) ?: 0
                        val be = Runtime.be()
                        var kept: ArrayList<Any>? = null
                        for (i in raw.indices) {
                            val item = raw[i] ?: continue
                            val info = item as? AppWidgetProviderInfo
                            val pkg = info?.provider?.packageName
                            val userId = info?.profile?.let { userIdOf(it, be) } ?: profileId
                            val hidden = pkg != null && snap.shouldHideScope(
                                pkg,
                                userId,
                                ModuleCache.isXposedModule(pkg, null, null, info.profile, be),
                                HideScope.WIDGETS,
                            )
                            if (hidden) {
                                if (kept == null) {
                                    kept = ArrayList<Any>(raw.size).apply {
                                        for (j in 0 until i) raw[j]?.let(::add)
                                    }
                                }
                            } else {
                                kept?.add(item)
                            }
                        }
                        if (kept != null) param.setResult(Slices.rebuild(slice, kept))
                    } catch (t: Throwable) {
                        HealthLog.event("AppWidgetService hook failed", t)
                    }
                }
            })
    }

    // ---- resolve / enabled / suggestions: hiding for every other entry point ---------------------------------------

    /**
     * Builds an `after` callback that acts when the package it concerns is hidden for the caller's user.
     * The system and the manager are never affected.
     */
    private fun hideGuardByUserId(
        precondition: (HookParam) -> Boolean,
        pkgOf: (HookParam) -> String?,
        userIdOfParam: (HookParam) -> Int,
        userOf: (HookParam) -> UserHandle? = { null },
        appInfoOf: (HookParam) -> ApplicationInfo? = { null },
        scope: Int = HideScope.LAUNCHER,
        onHidden: (HookParam) -> Unit,
    ): HookCallback = object : HookCallback() {
        override fun after(param: HookParam) {
            try {
                if (!precondition(param)) return
                val impl = param.thisObject
                if (Callers.isExempt(Binder.getCallingUid(), impl)) return
                val pkg = pkgOf(param) ?: return
                val be = Runtime.be()
                val mod = ModuleCache.isXposedModule(pkg, appInfoOf(param), Runtime.contextOf(impl), userOf(param), be)
                if (StateStore.snapshot.shouldHideScope(pkg, userIdOfParam(param), mod, scope)) onHidden(param)
            } catch (t: Throwable) {
                if (hideGuardErrorLogged.compareAndSet(false, true)) HealthLog.event("hideGuard failed", t)
            }
        }
    }

    private fun hideGuard(
        precondition: (HookParam) -> Boolean,
        pkgOf: (HookParam) -> String?,
        userOf: (HookParam) -> UserHandle?,
        appInfoOf: (HookParam) -> ApplicationInfo? = { null },
        scope: Int = HideScope.LAUNCHER,
        onHidden: (HookParam) -> Unit,
    ): HookCallback = hideGuardByUserId(
        precondition = precondition,
        pkgOf = pkgOf,
        userIdOfParam = { p -> userIdOf(userOf(p), Runtime.be()) },
        userOf = userOf,
        appInfoOf = appInfoOf,
        scope = scope,
        onHidden = onHidden,
    )

    private fun hookResolveAndEnabledChecks(h: Hooks, clazz: Class<*>) {
        val resultNull = { p: HookParam -> p.result != null }
        val resultTrue = { p: HookParam -> p.result == true }
        val toNull = { p: HookParam -> p.setResult(null) }
        val toFalse = { p: HookParam -> p.setResult(false) }
        val componentPkg = { p: HookParam -> (p.args[1] as? ComponentName)?.packageName }
        val componentUser = { p: HookParam -> p.args[2] as? UserHandle }

        val resolve = hideGuard(resultNull, componentPkg, componentUser,
            appInfoOf = { p -> p.result?.let { Slices.activityInfo(it)?.applicationInfo } }, onHidden = toNull)
        // Android 12-17
        h.optional("LauncherApps.resolveLauncherActivityInternal", clazz, "resolveLauncherActivityInternal",
            String::class.java, ComponentName::class.java, UserHandle::class.java, cb = resolve)
        // Android 11
        h.optional("LauncherApps.resolveActivity", clazz, "resolveActivity",
            String::class.java, ComponentName::class.java, UserHandle::class.java, cb = resolve)

        h.optional("LauncherApps.isPackageEnabled", clazz, "isPackageEnabled", String::class.java, String::class.java, UserHandle::class.java,
            cb = hideGuard(resultTrue, { p -> p.args[1] as? String }, { p -> p.args[2] as? UserHandle }, onHidden = toFalse))
        h.optional("LauncherApps.isActivityEnabled", clazz, "isActivityEnabled", String::class.java, ComponentName::class.java, UserHandle::class.java,
            cb = hideGuard(resultTrue, componentPkg, componentUser, onHidden = toFalse))

        val appInfo = hideGuard(resultNull, { p -> p.args[1] as? String }, { p -> p.args[3] as? UserHandle },
            appInfoOf = { p -> p.result as? ApplicationInfo }, onHidden = toNull)
        h.optional("LauncherApps.getApplicationInfo(int)", clazz, "getApplicationInfo", String::class.java, String::class.java, INT, UserHandle::class.java, cb = appInfo)
        h.optional("LauncherApps.getApplicationInfo(long)", clazz, "getApplicationInfo", String::class.java, String::class.java, Long::class.javaPrimitiveType!!, UserHandle::class.java, cb = appInfo)

        // Android 12-17: asked for any caller (also non-launcher apps); never hides the manager's own entry
        h.optional("LauncherApps.shouldHideFromSuggestions", clazz, "shouldHideFromSuggestions", String::class.java, UserHandle::class.java, cb = object : HookCallback() {
            override fun after(param: HookParam) {
                try {
                    if (param.result == true) return
                    val pkg = param.args[0] as? String ?: return
                    if (pkg == Identity.PACKAGE) return
                    val be = Runtime.be()
                    val user = param.args[1] as? UserHandle
                    val mod = ModuleCache.isXposedModule(pkg, null, Runtime.contextOf(param.thisObject), user, be)
                    if (StateStore.snapshot.shouldHideScope(pkg, userIdOf(user, be), mod, HideScope.SUGGESTIONS)) param.setResult(true)
                } catch (_: Throwable) {
                }
            }
        })

        h.optional("LauncherApps.getActivityLaunchIntent", clazz, "getActivityLaunchIntent", String::class.java, ComponentName::class.java, UserHandle::class.java,
            cb = hideGuard(resultNull, componentPkg, componentUser, onHidden = toNull))
        h.optional("LauncherApps.getShortcutConfigActivityIntent", clazz, "getShortcutConfigActivityIntent",
            String::class.java, ComponentName::class.java, UserHandle::class.java,
            cb = hideGuard(resultNull, componentPkg, componentUser, scope = HideScope.SHORTCUT_CONFIGS, onHidden = toNull))
        h.optional("LauncherApps.getShortcutIntent", clazz, "getShortcutIntent",
            String::class.java, String::class.java, String::class.java, Bundle::class.java, UserHandle::class.java,
            cb = hideGuard(resultNull, { p -> p.args[1] as? String }, { p -> p.args[4] as? UserHandle }, scope = HideScope.SHORTCUTS, onHidden = toNull))
        h.optional("LauncherApps.startShortcut", clazz, "startShortcut",
            String::class.java, String::class.java, String::class.java, String::class.java, Rect::class.java, Bundle::class.java, INT,
            cb = hideGuardByUserId(resultTrue, { p -> p.args[1] as? String }, { p -> (p.args[6] as? Int) ?: 0 }, scope = HideScope.SHORTCUTS, onHidden = toFalse))
    }

    /**
     * Filters hidden packages out of implicit intent queries (e.g. ResolverActivity / ChooserActivity / IntentResolver
     * profile pickers and drawer search queries) while preserving explicit ComponentName launches, resolveForStart,
     * Fadalias manager queries, and internal system_server bookkeeping.
     */
    private fun hookQueryIntentActivities(h: Hooks, classLoader: ClassLoader) {
        val target = h.be.findClassIfExists("com.android.server.pm.ComputerEngine", classLoader)
            ?: h.be.findClassIfExists("com.android.server.pm.PackageManagerService", classLoader)
        if (target == null) {
            HealthLog.hook("queryIntentActivitiesInternal", HealthLog.MISSING)
            return
        }
        val method = target.declaredMethods
            .filter { m ->
                if (m.name != "queryIntentActivitiesInternal") return@filter false
                val t = m.parameterTypes
                t.size >= 5 && t[0] == Intent::class.java && t[1] == String::class.java &&
                    (t[2] == Long::class.javaPrimitiveType || t[2] == Int::class.javaPrimitiveType) &&
                    t[3] == Int::class.javaPrimitiveType && t[4] == Int::class.javaPrimitiveType
            }
            .maxByOrNull { it.parameterCount }
        if (method == null) {
            HealthLog.hook("${target.simpleName}.queryIntentActivitiesInternal", HealthLog.MISSING)
            return
        }
        val paramCount = method.parameterCount
        val userIdIdx = if (paramCount == 5) 4 else 5
        h.be.hookMethod(method, object : HookCallback() {
            override fun after(param: HookParam) {
                try {
                    val list = param.result as? List<*> ?: return
                    if (list.isEmpty()) return
                    val snap = StateStore.snapshot
                    val canHide = snap.mayHideAnything()
                    val canLook = snap.mayCustomizeLooks()
                    if (!canHide && !canLook) return
                    val intent = param.args[0] as? Intent
                    if (intent?.component != null) return
                    val callingUid = Binder.getCallingUid()
                    if (Callers.isSelf(callingUid, null) || Callers.inSelfScope()) return

                    val userId = (param.args[userIdIdx] as? Int) ?: return
                    val be = Runtime.be()
                    var appQuery = -1
                    fun isAppQuery(): Boolean {
                        if (appQuery != -1) return appQuery == 1
                        val filterUid = (param.args[3] as? Int) ?: callingUid
                        val res = callingUid >= Process.FIRST_APPLICATION_UID ||
                            filterUid >= Process.FIRST_APPLICATION_UID ||
                            Binder.getCallingPid() != Process.myPid() ||
                            isResolverCaller()
                        appQuery = if (res) 1 else 0
                        return res
                    }

                    var kept: ArrayList<Any?>? = null
                    for (i in list.indices) {
                        val item = list[i]
                        val ri = item as? ResolveInfo
                        val ai = ri?.activityInfo
                        val pkg = ai?.packageName
                        val itemUserId = ai?.applicationInfo?.uid?.let { it / 100_000 } ?: userId
                        val hidden = canHide && pkg != null && snap.shouldHideScope(
                            pkg,
                            itemUserId,
                            ModuleCache.isXposedModule(pkg, ai.applicationInfo, null, null, be),
                            HideScope.INTENT_CHOOSER,
                        )
                        if (hidden && isAppQuery()) {
                            if (kept == null) {
                                if (list.size == 1 && !canLook) {
                                    param.setResult(emptyList<Any>())
                                    return
                                }
                                kept = ArrayList<Any?>(list.size).apply {
                                    for (j in 0 until i) add(list[j])
                                }
                            }
                            continue
                        }
                        val outItem = if (canLook && ri != null && pkg != null && isAppQuery()) {
                            applyResolveLooks(ri, snap, itemUserId)
                        } else {
                            item
                        }
                        if (outItem !== item && kept == null) {
                            kept = ArrayList<Any?>(list.size).apply {
                                for (j in 0 until i) add(list[j])
                            }
                        }
                        kept?.add(outItem)
                    }
                    if (kept != null) param.setResult(kept)
                } catch (t: Throwable) {
                    if (hideGuardErrorLogged.compareAndSet(false, true)) HealthLog.event("queryIntentActivitiesInternal hook failed", t)
                }
            }
        })
        HealthLog.hook("${target.simpleName}.queryIntentActivitiesInternal", HealthLog.OK)
    }

    private fun applyResolveLooks(ri: ResolveInfo, snap: dev.fadalias.state.StateData, userId: Int): ResolveInfo {
        val ai = ri.activityInfo ?: return ri
        val pkg = ai.packageName ?: return ri
        val label = snap.labelOf(pkg, userId)
        val rule = snap.iconRuleOf(pkg, userId)
        val hasIcon = (rule?.overlayRev ?: 0L) > 0L && rule?.overlayStatus == Overlay.OK
        if (label == null && !hasIcon) return ri
        val applyLabel = label != null && (ri.nonLocalizedLabel == null || ri.labelRes == 0 || NameHooks.labelApplies(ai))
        val appIcon = ai.applicationInfo?.icon ?: 0
        val applyIcon = hasIcon && appIcon != 0
        if (!applyLabel && !applyIcon) return ri
        val aiCopy = ActivityInfo(ai)
        val riCopy = ResolveInfo(ri)
        if (applyLabel) {
            aiCopy.nonLocalizedLabel = label
            riCopy.nonLocalizedLabel = label
        }
        if (applyIcon) {
            aiCopy.icon = appIcon
            riCopy.icon = appIcon
        }
        riCopy.activityInfo = aiCopy
        return riCopy
    }

    private fun isResolverCaller(): Boolean {
        for (frame in Thread.currentThread().stackTrace) {
            val cn = frame.className
            if (cn.contains("ResolverActivity") || cn.contains("ChooserActivity") ||
                cn.contains("ResolverListController") || cn.contains("IntentForwarderActivity")
            ) return true
        }
        return false
    }

    /**
     * Android 14+: disables `<overlayable>` enforcement in `IdmapDaemon` for Fadalias's own fabricated overlays
     * so pre-installed/system apps (e.g. Google Maps) can also have their icons overlaid.
     */
    private fun hookIdmapDaemon(h: Hooks, classLoader: ClassLoader) {
        val idmapDaemon = h.be.findClassIfExists("com.android.server.om.IdmapDaemon", classLoader)
        if (idmapDaemon == null) {
            HealthLog.hook("IdmapDaemon", HealthLog.MISSING)
            return
        }
        val cb = object : HookCallback() {
            override fun before(param: HookParam) {
                val name = param.args.getOrNull(2) as? String
                if (name != null && name.startsWith("hfw_")) {
                    param.args[4] = false
                }
            }
        }
        var hooked = 0
        for (m in idmapDaemon.declaredMethods) {
            if (m.name != "createIdmap" && m.name != "verifyIdmap") continue
            val types = m.parameterTypes
            if (types.size >= 6 && types[1] == String::class.java && types[2] == String::class.java &&
                types[4] == Boolean::class.javaPrimitiveType
            ) {
                h.be.hookMethod(m, cb)
                hooked++
            }
        }
        HealthLog.hook("IdmapDaemon.enforceOverlayable", if (hooked > 0) HealthLog.OK else HealthLog.MISSING)
    }

    // ---- hot reload lifecycle (API 102) ----------------------------------------------------------------------------

    private fun stopEngineLocked() {
        PackageEvents.stop()
        Work.stop()
    }

    internal fun updateBackend(hookBackend: HookBackend) {
        synchronized(Runtime.lifecycleLock) { Runtime.backend = hookBackend }
    }

    private fun triggerAbortRecoveryAsync() {
        if (!abortRecoveryTriggered.compareAndSet(false, true)) return
        try {
            Thread({
                try {
                    synchronized(Runtime.lifecycleLock) {
                        if (Runtime.acceptingReload && System.nanoTime() - Runtime.reloadStartNanos > Runtime.RELOAD_TIMEOUT_NANOS) {
                            HealthLog.event("hot reload preparation timed out; restoring lifecycle")
                            System.getProperties().remove(RESUMER_KEY)
                            System.getProperties().remove(RETIRER_KEY)
                            resumeLocked()
                        }
                    }
                } catch (t: Throwable) {
                    HealthLog.event("could not recover from aborted reload", t)
                } finally {
                    abortRecoveryTriggered.set(false)
                }
            }, "fadalias-abort-recovery").apply { isDaemon = true }.start()
        } catch (_: Throwable) {
            abortRecoveryTriggered.set(false)
        }
    }

    fun prepareForHotReload(): Boolean {
        synchronized(Runtime.lifecycleLock) {
            Runtime.acceptingReload = true
            Runtime.reloadStartNanos = System.nanoTime()
            System.getProperties()[RESUMER_KEY] = Runnable { synchronized(Runtime.lifecycleLock) { resumeLocked() } }
            System.getProperties()[RETIRER_KEY] = Runnable { synchronized(Runtime.lifecycleLock) { retireLocked() } }
            Callers.invalidate()
            Notifier.reset()
            stopEngineLocked()
            if (!ModuleCache.prepareForHotReload()) {
                ModuleCache.resumeAfterHotReload()
                resumeLocked()
                return false
            }
            return true
        }
    }

    fun resumeAfterHotReload() {
        System.getProperties().remove(RESUMER_KEY)
        (System.getProperties().remove(RETIRER_KEY) as? Runnable)?.run()
        synchronized(Runtime.lifecycleLock) { resumeLocked() }
    }

    /** The old generation after a successful reload: stop everything it owns; the new generation takes over. */
    private fun retireLocked() {
        Runtime.acceptingReload = false
        stopEngineLocked()
        ModuleCache.prepareForHotReload()
    }

    private fun resumeLocked() {
        Runtime.acceptingReload = false
        Callers.invalidate()
        Notifier.reset()
        ModuleCache.resumeAfterHotReload()
    }
}
