package org.lsposed.hidexposed.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import kotlin.math.roundToInt
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import kotlinx.coroutines.NonCancellable
import androidx.compose.material3.LinearWavyProgressIndicator
import org.lsposed.hidexposed.ui.components.HookCardBackdrop
import org.lsposed.hidexposed.ui.components.SortMenuButton
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.runtime.mutableLongStateOf
import org.lsposed.hidexposed.BuildConfig
import org.lsposed.hidexposed.core.AppDiagnostics
import org.lsposed.hidexposed.data.AppSortMode
import org.lsposed.hidexposed.data.ConfigStore
import org.lsposed.hidexposed.data.PendingOp
import org.lsposed.hidexposed.data.applyTo
import org.lsposed.hidexposed.ui.theme.HideXposedTheme
import android.util.LruCache
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import org.lsposed.hidexposed.ui.components.AppIcon
import org.lsposed.hidexposed.ui.components.CollapsePart
import org.lsposed.hidexposed.ui.components.CollapsingHeaderState
import org.lsposed.hidexposed.ui.components.HookStatusBadge
import org.lsposed.hidexposed.ui.components.WavyLoading
import org.lsposed.hidexposed.ui.components.WavyPullIndicator
import org.lsposed.hidexposed.ui.components.bouncyClickable
import org.lsposed.hidexposed.ui.components.collapsible
import org.lsposed.hidexposed.ui.components.pressScale
import org.lsposed.hidexposed.ui.components.rememberCountUp
import org.lsposed.hidexposed.ui.components.staggeredEnter

// Bounded LRU cache (max 256 entries) to prevent unlimited memory growth
private val appIconLruCache = object : LruCache<String, ImageBitmap>(256) {}
private val xposedAppCache = ConcurrentHashMap<String, Boolean>()

@Composable
internal fun rememberAppIcon(packageName: String, userId: Int, lastUpdateTime: Long = 0L): ImageBitmap? {
    val context = LocalContext.current
    val key = "$userId:$packageName:$lastUpdateTime"
    var bitmap by remember(key) {
        mutableStateOf(synchronized(appIconLruCache) { appIconLruCache.get(key) })
    }

    LaunchedEffect(key) {
        if (bitmap == null) {
            val bmp = withContext(Dispatchers.IO) {
                loadAppIcon(context, packageName, userId, lastUpdateTime)
            }
            if (bmp != null) {
                bitmap = bmp
            }
        }
    }
    return bitmap
}

private fun loadAppIcon(context: Context, packageName: String, userId: Int, lastUpdateTime: Long = 0L): ImageBitmap? {
    val key = "$userId:$packageName:$lastUpdateTime"
    synchronized(appIconLruCache) {
        val cached = appIconLruCache.get(key)
        if (cached != null) return cached
    }
    return try {
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        val userHandle = if (userId == 0) {
            Process.myUserHandle()
        } else {
            try {
                UserHandle::class.java.getDeclaredConstructor(Int::class.javaPrimitiveType).apply {
                    isAccessible = true
                }.newInstance(userId)
            } catch (_: Throwable) {
                Process.myUserHandle()
            }
        }
        val activities = launcherApps?.getActivityList(packageName, userHandle)
        val rawDrawable = activities?.firstOrNull()?.getIcon(0)
            ?: activities?.firstOrNull()?.getBadgedIcon(0)
            ?: try {
                context.packageManager.getApplicationInfo(packageName, 0).loadIcon(context.packageManager)
            } catch (_: Throwable) {
                null
            }
        val targetSizePx = (context.resources.displayMetrics.density * 44).roundToInt().coerceIn(96, 192)
        val bmp = drawableToImageBitmap(rawDrawable, targetSizePx)
        if (bmp != null) {
            synchronized(appIconLruCache) {
                appIconLruCache.put(key, bmp)
            }
        }
        bmp
    } catch (_: Throwable) {
        null
    }
}

data class AppEntry(
    val userId: Int,
    val packageName: String,
    val label: String,
    val isXposedModule: Boolean,
    val isSystemApp: Boolean,
    val firstInstallTime: Long = 0L,
    val lastUpdateTime: Long = 0L,
    val hasLauncherIcon: Boolean = true
) {
    val uniqueKey: String get() = ConfigStore.userKey(userId, packageName)
}

data class LauncherInfo(
    val label: String,
    val packageName: String,
    val icon: ImageBitmap?
)

enum class MainTab {
    HOME, APPS, SETTINGS
}

sealed interface FilterMode {
    data object AllApps : FilterMode
    data object XposedOnly : FilterMode
    data object HiddenOnly : FilterMode
    data class ByUser(val userId: Int) : FilterMode
}

class MainActivity : ComponentActivity() {
    /**
     * Android 13+ owns the app language (system "App languages" screen). A language the user
     * picked in the old in-app selector is handed over once so the choice is not lost.
     */
    private fun migrateLanguageToSystemPicker() {
        if (Build.VERSION.SDK_INT < 33) return
        val prefs = getSharedPreferences("hidexposed_prefs", MODE_PRIVATE)
        val saved = prefs.getString("appLanguage", "SYSTEM") ?: "SYSTEM"
        if (saved == "SYSTEM") return
        try {
            val lm = getSystemService(android.app.LocaleManager::class.java)
            if (lm != null && lm.applicationLocales.isEmpty) {
                lm.applicationLocales = android.os.LocaleList.forLanguageTags(saved.lowercase())
            }
        } catch (_: Throwable) {
        }
        prefs.edit().putString("appLanguage", "SYSTEM").apply()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        enableEdgeToEdge()
        migrateLanguageToSystemPicker()
        ConfigStore.loadUiPrefs(this)
        setContent {
            var lang by remember { mutableStateOf(ConfigStore.appLanguage) }
            var themeMode by remember { mutableStateOf(ConfigStore.themeMode) }
            var amoledBlack by remember { mutableStateOf(ConfigStore.amoledBlack) }
            var dynamicColor by remember { mutableStateOf(ConfigStore.dynamicColor) }
            var uiScale by remember { mutableStateOf(ConfigStore.uiScale) }

            val systemDark = isSystemInDarkTheme()
            val isDarkEffective = when (themeMode) {
                "DARK" -> true
                "LIGHT" -> false
                else -> systemDark
            }

            DisposableEffect(isDarkEffective) {
                enableEdgeToEdge(
                    statusBarStyle = if (isDarkEffective) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    },
                    navigationBarStyle = if (isDarkEffective) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    }
                )
                onDispose {}
            }

            val density = LocalDensity.current
            val customDensity = remember(density, uiScale) {
                Density(
                    density = density.density * uiScale,
                    fontScale = density.fontScale
                )
            }

            CompositionLocalProvider(LocalDensity provides customDensity) {
                HideXposedTheme(
                    themeMode = themeMode,
                    amoledBlack = amoledBlack,
                    dynamicColor = dynamicColor
                ) {
                    MainScreen(
                        langCode = lang,
                        themeMode = themeMode,
                        amoledBlack = amoledBlack,
                        dynamicColor = dynamicColor,
                        uiScale = uiScale,
                        onUpdateAppearance = { newLang, newTheme, newAmoled, newDynamic, newShowSys, newScale ->
                            lang = newLang
                            themeMode = newTheme
                            amoledBlack = newAmoled
                            dynamicColor = newDynamic
                            uiScale = newScale
                            ConfigStore.saveUiAppearancePrefs(
                                context = this@MainActivity,
                                language = newLang,
                                theme = newTheme,
                                amoled = newAmoled,
                                dynamic = newDynamic,
                                showSystem = newShowSys,
                                scale = newScale
                            )
                        }
                    )
                }
            }
        }
    }
}

data class PendingImportConfig(
    val autoHide: Boolean,
    val autoHideClone: Boolean,
    val whitelist: Set<String>,
    val blacklist: Set<String>,
    val validCount: Int,
    val skippedCount: Int
)

@Composable
fun MainScreen(
    langCode: String,
    themeMode: String,
    amoledBlack: Boolean,
    dynamicColor: Boolean,
    uiScale: Float,
    onUpdateAppearance: (String, String, Boolean, Boolean, Boolean, Float) -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()
    val strings = remember(langCode, configuration) { Localization.resolveStrings(langCode) }

    var currentTab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    var isHookActive by remember { mutableStateOf<Boolean?>(null) }
    var autoHideModules by remember { mutableStateOf(ConfigStore.autoHideXposedModules) }
    var autoHideCloneModules by remember { mutableStateOf(ConfigStore.autoHideCloneModules) }
    var showSystemApps by remember { mutableStateOf(ConfigStore.showSystemApps) }
    var whitelist by remember { mutableStateOf(ConfigStore.getWhitelist()) }
    var blacklist by remember { mutableStateOf(ConfigStore.getBlacklist()) }
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var availableUserIds by remember { mutableStateOf<List<Int>>(listOf(0)) }
    var defaultLauncher by remember { mutableStateOf<LauncherInfo?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var isFetchingData by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var showDiscardPendingDialog by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<PendingImportConfig?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var debouncedQuery by rememberSaveable { mutableStateOf("") }
    var filterMode by remember { mutableStateOf<FilterMode>(FilterMode.AllApps) }
    var sortMode by remember { mutableStateOf(ConfigStore.appSortMode) }

    val homeScrollState = rememberScrollState()
    val appsListState = rememberLazyListState()
    val settingsScrollState = rememberScrollState()

    val snackbarHostState = remember { SnackbarHostState() }
    fun showNotice(message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = actionLabel,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) onAction?.invoke()
        }
    }

    // Tabs live in a pager: tapping a tab animates across the pages in between, and the
    // back gesture drives the very same scroll position (swipe progress = how far it slides).
    val pagerState = rememberPagerState(initialPage = currentTab.ordinal) { MainTab.entries.size }
    // Preload every tab right after the first frame so switching never has to compose a page.
    var preloadPages by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        withFrameNanos { }
        preloadPages = MainTab.entries.size - 1
    }
    // Home's endless decorations only run while Home is (partly) on screen.
    val homeActive by remember {
        derivedStateOf { pagerState.currentPage + pagerState.currentPageOffsetFraction < 1f }
    }
    var backActive by remember { mutableStateOf(false) }
    var fastSettle by remember { mutableStateOf(false) }
    LaunchedEffect(currentTab) {
        val spec = if (fastSettle) {
            tween<Float>(240, easing = LinearOutSlowInEasing)
        } else {
            tween<Float>(420, easing = FastOutSlowInEasing)
        }
        fastSettle = false
        pagerState.animateScrollToPage(currentTab.ordinal, animationSpec = spec)
    }
    PredictiveBackHandler(enabled = currentTab != MainTab.HOME) { progress ->
        backActive = true
        val start = currentTab.ordinal
        try {
            progress.collect { event ->
                val size = pagerState.layoutInfo.pageSize + pagerState.layoutInfo.pageSpacing
                val current = (pagerState.currentPage + pagerState.currentPageOffsetFraction) * size
                val target = start * (1f - event.progress) * size
                pagerState.scroll { scrollBy(target - current) }
            }
            fastSettle = true
            currentTab = MainTab.HOME
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                pagerState.animateScrollToPage(
                    start,
                    animationSpec = tween<Float>(200, easing = LinearOutSlowInEasing)
                )
            }
        } finally {
            backActive = false
        }
    }
    // Safety net: never stay half-way between pages after an interrupted gesture.
    LaunchedEffect(backActive) {
        if (!backActive) {
            delay(500)
            if (!backActive && (pagerState.currentPage != currentTab.ordinal ||
                    kotlin.math.abs(pagerState.currentPageOffsetFraction) > 0.001f)) {
                pagerState.animateScrollToPage(currentTab.ordinal)
            }
        }
    }
    var homeEntered by rememberSaveable { mutableStateOf(false) }
    var settingsEntered by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isEmpty()) {
            debouncedQuery = ""
        } else {
            delay(200)
            debouncedQuery = searchQuery
        }
    }

    var pendingCrashReport by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val f = File(context.filesDir, "last_crash.txt")
            if (f.exists() && f.length() <= 256 * 1024) {
                try {
                    val report = f.readText()
                    withContext(Dispatchers.Main) {
                        pendingCrashReport = report
                    }
                } catch (_: Throwable) {
                }
            }
        }
    }

    fun isEntryHidden(
        entry: AppEntry,
        wl: Set<String> = whitelist,
        bl: Set<String> = blacklist,
        autoHide: Boolean = autoHideModules,
        autoHideClone: Boolean = autoHideCloneModules
    ): Boolean {
        if (!entry.hasLauncherIcon) return false
        val uKey = entry.uniqueKey
        return when {
            bl.contains(uKey) || bl.contains(entry.packageName) -> true
            wl.contains(uKey) || wl.contains(entry.packageName) -> false
            !entry.isXposedModule -> false
            // Mirrors ConfigStore: XHide is never auto-hidden, only hidden via an explicit blacklist entry.
            uKey == ConfigStore.SELF_USER0_KEY -> false
            entry.userId == 0 -> autoHide
            else -> autoHide && autoHideClone
        }
    }

    fun showRules(r: ConfigStore.HideRules) {
        autoHideModules = r.autoHide
        autoHideCloneModules = r.autoHideClone
        whitelist = r.whitelist
        blacklist = r.blacklist
    }

    // Fair (FIFO) mutex taken on the main dispatcher in submission order, so edits commit in the
    // order the user made them. ConfigStore's monitor lock gives no ordering guarantee between
    // IO threads; it only coordinates with the stale-rule cleanup.
    val syncMutex = remember { Mutex() }

    // Number of submitted edits not yet finished. Only touched on the main dispatcher. While edits
    // are pending, the on-screen state holds their optimistic values and must not be replaced.
    var pendingSyncs by remember { mutableStateOf(0) }

    // Number of edits queued while the hook was inactive; mirrors ConfigStore.pendingCount().
    var pendingCount by remember { mutableStateOf(ConfigStore.pendingCount()) }

    /**
     * Runs [action] (a ConfigStore sync) on IO under the fair mutex, then shows the effective rules
     * (committed plus queued) once no other edit is pending, and reports the outcome. [onOk] runs
     * only when the change was applied to the system.
     */
    fun runSync(
        onOk: (() -> Unit)? = null,
        action: () -> ConfigStore.SyncResult
    ) {
        pendingSyncs++
        scope.launch {
            val result = syncMutex.withLock {
                withContext(Dispatchers.IO) { action() }
            }
            pendingSyncs--
            // result.rules is the snapshot taken inside the lock; a later cleanup commit may already
            // have superseded it. Show the latest effective rules, and only once no edit is pending
            // so a queued edit's optimistic state does not flicker back.
            if (pendingSyncs == 0) showRules(ConfigStore.effectiveRules())
            pendingCount = ConfigStore.pendingCount()
            when (result.status) {
                ConfigStore.SyncStatus.OK -> onOk?.invoke()
                ConfigStore.SyncStatus.QUEUED ->
                    Toast.makeText(context, strings.changeQueuedToast, Toast.LENGTH_SHORT).show()
                ConfigStore.SyncStatus.TOO_LARGE ->
                    Toast.makeText(context, strings.configTooLargeToast, Toast.LENGTH_LONG).show()
                ConfigStore.SyncStatus.FAILED ->
                    Toast.makeText(context, strings.syncFailedToast, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Applies a single edit; queued for later delivery if the hook is inactive. */
    fun syncWithSystem(changedPkgs: List<String>, op: PendingOp) {
        // Same module-key input as the flush path, used if a queued auto-hide change is delivered here.
        val moduleKeys = apps.filter { it.isXposedModule }.map { it.uniqueKey }.distinct()
        runSync { ConfigStore.updateRules(context, changedPkgs, op, moduleKeys) }
    }

    fun refreshState(showToastOnDone: Boolean = false) {
        if (isFetchingData) return
        isFetchingData = true
        scope.launch {
            try {
                if (apps.isEmpty()) isLoading = true
                else isRefreshing = true

                val startTime = System.currentTimeMillis()

                // Read the rules persisted by system_server before anything can be sent, so a reinstall
                // or cleared app data never overwrites the system rules with an empty local state.
                withContext(Dispatchers.IO) { ConfigStore.adoptSystemRules(context) }

                val (active, loadedApps, launcher) = withContext(Dispatchers.IO) {
                    val activeDef = async { ConfigStore.pingSystemHook(context) }
                    val loadedAppsDef = async { loadInstalledAppsAcrossProfiles(context) }
                    val launcherDef = async { detectDefaultLauncher(context) }
                    Triple(activeDef.await(), loadedAppsDef.await(), launcherDef.await())
                }
                isHookActive = active
                apps = loadedApps
                if (ConfigStore.consumeRestoredNotice()) {
                    Toast.makeText(context, strings.rulesRestoredToast, Toast.LENGTH_SHORT).show()
                }

                // Deliver edits queued while the hook was inactive. Runs under the same fair mutex as
                // user edits so it cannot interleave with them. Module keys are included because a
                // queued auto-hide change affects every detected module.
                if (active) {
                    if (ConfigStore.pendingCount() > 0) {
                        val moduleKeys = loadedApps.filter { it.isXposedModule }.map { it.uniqueKey }
                        val flushStatus = syncMutex.withLock {
                            withContext(Dispatchers.IO) { ConfigStore.flushPendingOps(context, moduleKeys) }
                        }
                        pendingCount = ConfigStore.pendingCount()
                        when (flushStatus) {
                            ConfigStore.SyncStatus.OK ->
                                Toast.makeText(context, strings.pendingAppliedToast, Toast.LENGTH_SHORT).show()
                            ConfigStore.SyncStatus.TOO_LARGE ->
                                Toast.makeText(context, strings.configTooLargeToast, Toast.LENGTH_LONG).show()
                            else -> Unit
                        }
                    }
                }
                // The app list loader may have purged stale rules; show what is actually committed,
                // unless an edit is still pending (it refreshes the state itself when it finishes).
                if (pendingSyncs == 0) showRules(ConfigStore.effectiveRules())
                defaultLauncher = launcher

                val userManager = context.getSystemService(UserManager::class.java)
                val launcherApps = context.getSystemService(LauncherApps::class.java)
                val sysUserIds = try {
                    val pmProfiles = launcherApps?.profiles ?: userManager?.userProfiles
                    pmProfiles?.map { getUserIdFromHandle(it, userManager) } ?: listOf(0)
                } catch (_: Throwable) {
                    listOf(0)
                }
                availableUserIds = (loadedApps.map { it.userId } + sysUserIds).distinct().sorted()

                if (isRefreshing) {
                    val elapsed = System.currentTimeMillis() - startTime
                    val minDurationMs = 1200L
                    if (elapsed < minDurationMs) {
                        delay(minDurationMs - elapsed)
                    }
                }

                if (showToastOnDone) {
                    showNotice(strings.launcherRefreshedToast)
                }
            } finally {
                isLoading = false
                isRefreshing = false
                isFetchingData = false
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshState()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        val packageFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        val packageReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                refreshState()
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(packageReceiver, packageFilter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(packageReceiver, packageFilter)
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            try {
                context.unregisterReceiver(packageReceiver)
            } catch (_: Throwable) {
            }
        }
    }

    fun updateAppHidden(entry: AppEntry, hideInLauncher: Boolean) {
        val op = PendingOp.Toggle(
            key = entry.uniqueKey,
            packageName = entry.packageName,
            userId = entry.userId,
            isXposedModule = entry.isXposedModule,
            hide = hideInLauncher
        )
        // Optimistic update from the on-screen state; replaced by the effective rules afterwards.
        showRules(op.applyTo(ConfigStore.HideRules(autoHideModules, autoHideCloneModules, whitelist, blacklist)))
        syncWithSystem(changedPkgs = listOf(entry.uniqueKey), op = op)
    }

    fun toggleWithUndo(entry: AppEntry, hide: Boolean) {
        updateAppHidden(entry, hide)
        showNotice(
            message = String.format(
                java.util.Locale.getDefault(),
                if (hide) strings.snackHiddenFmt else strings.snackShownFmt,
                entry.label
            ),
            actionLabel = strings.undoBtn,
            onAction = { updateAppHidden(entry, !hide) }
        )
    }

    // SAF File Launchers for JSON Backup Export & Import
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val jsonBytes = ConfigStore.exportConfigJson().toByteArray(Charsets.UTF_8)
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(jsonBytes)
                        out.flush()
                    }
                    withContext(Dispatchers.Main) {
                        showNotice(strings.exportSuccessToast)
                    }
                } catch (_: Throwable) {
                    withContext(Dispatchers.Main) {
                        showNotice(strings.exportFailedToast)
                    }
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val maxImportBytes = 512 * 1024
                    val jsonStr = context.contentResolver.openInputStream(uri)?.use { input ->
                        val bytes = input.readNBytes(maxImportBytes + 1)
                        if (bytes.size > maxImportBytes) {
                            throw IllegalArgumentException("Backup file too large")
                        }
                        String(bytes, Charsets.UTF_8)
                    } ?: throw IllegalArgumentException("Empty file")

                    val json = JSONObject(jsonStr)
                    val newAuto = json.optBoolean("autoHide", true)
                    val newAutoClone = json.optBoolean("autoHideClone", true)
                    val newWl = mutableSetOf<String>()
                    var skipped = 0
                    val wlArr = json.optJSONArray("whitelist")
                    if (wlArr != null) {
                        val limit = minOf(wlArr.length(), 4096)
                        for (i in 0 until limit) {
                            val s = wlArr.optString(i)
                            if (ConfigStore.isValidConfigKey(s)) newWl.add(s)
                            else skipped++
                        }
                    }
                    val newBl = mutableSetOf<String>()
                    val blArr = json.optJSONArray("blacklist")
                    if (blArr != null) {
                        val limit = minOf(blArr.length(), 4096)
                        for (i in 0 until limit) {
                            val s = blArr.optString(i)
                            if (ConfigStore.isValidConfigKey(s)) newBl.add(s)
                            else skipped++
                        }
                    }
                    withContext(Dispatchers.Main) {
                        pendingImport = PendingImportConfig(
                            autoHide = newAuto,
                            autoHideClone = newAutoClone,
                            whitelist = newWl,
                            blacklist = newBl,
                            validCount = newWl.size + newBl.size,
                            skippedCount = skipped
                        )
                    }
                } catch (_: Throwable) {
                    withContext(Dispatchers.Main) {
                        showNotice(strings.importFailedToast)
                    }
                }
            }
        }
    }

    if (showDiscardPendingDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardPendingDialog = false },
            title = { Text(text = strings.discardPendingTitle, fontWeight = FontWeight.Bold) },
            text = { Text(text = strings.discardPendingMessage) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardPendingDialog = false
                        scope.launch {
                            syncMutex.withLock {
                                withContext(Dispatchers.IO) { ConfigStore.discardPendingOps(context) }
                            }
                            pendingCount = ConfigStore.pendingCount()
                            if (pendingSyncs == 0) showRules(ConfigStore.effectiveRules())
                        }
                    }
                ) {
                    Text(text = strings.discardPendingBtn, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardPendingDialog = false }) {
                    Text(text = strings.dismissBtn)
                }
            }
        )
    }

    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = { Text(text = strings.resetAllTitle, fontWeight = FontWeight.Bold) },
            text = { Text(text = strings.resetConfirmMessage) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetConfirmDialog = false
                        val defaults = ConfigStore.HideRules(
                            autoHide = true,
                            autoHideClone = true,
                            whitelist = setOf(ConfigStore.SELF_USER0_KEY),
                            blacklist = emptySet()
                        )
                        val prevRules = ConfigStore.HideRules(autoHideModules, autoHideCloneModules, whitelist, blacklist)
                        showRules(defaults)
                        val allPkgs = apps.map { it.uniqueKey }.distinct()
                        runSync(onOk = {
                            showNotice(strings.resetDoneToast, strings.undoBtn) {
                                showRules(prevRules)
                                runSync { ConfigStore.replaceRules(context, allPkgs, prevRules) }
                            }
                        }) { ConfigStore.replaceRules(context, allPkgs, defaults) }
                    }
                ) {
                    Text(text = strings.resetConfirmBtn, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text(text = strings.dismissBtn)
                }
            }
        )
    }

    pendingImport?.let { cfg ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(text = strings.importConfirmTitle, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = String.format(
                        java.util.Locale.getDefault(),
                        strings.importConfirmMessage,
                        cfg.validCount,
                        cfg.skippedCount
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val prevRules = ConfigStore.HideRules(autoHideModules, autoHideCloneModules, whitelist, blacklist)
                        val imported = ConfigStore.HideRules(
                            autoHide = cfg.autoHide,
                            autoHideClone = cfg.autoHideClone,
                            whitelist = cfg.whitelist,
                            blacklist = cfg.blacklist
                        )
                        showRules(imported)
                        pendingImport = null

                        val allPkgs = apps.map { it.uniqueKey }.distinct()
                        runSync(onOk = {
                            showNotice(strings.importedToast, strings.undoBtn) {
                                showRules(prevRules)
                                runSync { ConfigStore.replaceRules(context, allPkgs, prevRules) }
                            }
                        }) { ConfigStore.replaceRules(context, allPkgs, imported) }
                    }
                ) {
                    Text(text = strings.importConfirmBtn, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) {
                    Text(text = strings.dismissBtn)
                }
            }
        )
    }

    if (pendingCrashReport != null) {
        AlertDialog(
            onDismissRequest = {
                File(context.filesDir, "last_crash.txt").delete()
                (context as? android.app.Activity)?.intent?.removeExtra("has_crash_report")
                pendingCrashReport = null
            },
            title = { Text(strings.crashDetectedTitle, fontWeight = FontWeight.Bold) },
            text = { Text(strings.crashDetectedDesc) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val report = pendingCrashReport
                        File(context.filesDir, "last_crash.txt").delete()
                        (context as? android.app.Activity)?.intent?.removeExtra("has_crash_report")
                        pendingCrashReport = null
                        AppDiagnostics.openGitHubIssue(
                            context = context,
                            isHookActive = isHookActive == true,
                            userNotes = report
                        )
                    }
                ) {
                    Text(strings.sendCrashReportBtn, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        File(context.filesDir, "last_crash.txt").delete()
                        (context as? android.app.Activity)?.intent?.removeExtra("has_crash_report")
                        pendingCrashReport = null
                    }
                ) {
                    Text(strings.dismissBtn)
                }
            }
        )
    }

    val hiddenBadgeCount = remember(apps, showSystemApps, autoHideModules, autoHideCloneModules, whitelist, blacklist) {
        apps.count { isEntryHidden(it) && (showSystemApps || it.hasLauncherIcon) }
    }

    LaunchedEffect(currentTab, isLoading) {
        if (!isLoading) {
            delay(900)
            when (currentTab) {
                MainTab.HOME -> homeEntered = true
                MainTab.SETTINGS -> settingsEntered = true
                MainTab.APPS -> Unit
            }
        }
    }


    Scaffold(
        contentWindowInsets = WindowInsets.statusBars,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(snackbarData = data, shape = RoundedCornerShape(14.dp))
            }
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.heightIn(min = 64.dp),
                tonalElevation = 3.dp
            ) {
                NavigationBarItem(
                    selected = currentTab == MainTab.HOME,
                    onClick = { currentTab = MainTab.HOME },
                    icon = {
                        NavTabIcon(selected = currentTab == MainTab.HOME) { fill -> HomeVectorIcon(fill) }
                    },
                    label = { Text(strings.navHome, fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = currentTab == MainTab.APPS,
                    onClick = { currentTab = MainTab.APPS },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (hiddenBadgeCount > 0) {
                                    Badge { Text(hiddenBadgeCount.toString()) }
                                }
                            }
                        ) {
                            NavTabIcon(selected = currentTab == MainTab.APPS) { fill -> AppsVectorIcon(fill) }
                        }
                    },
                    label = { Text(strings.navApps, fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = currentTab == MainTab.SETTINGS,
                    onClick = { currentTab = MainTab.SETTINGS },
                    icon = {
                        NavTabIcon(selected = currentTab == MainTab.SETTINGS) { fill -> SettingsVectorIcon(fill) }
                    },
                    label = { Text(strings.navSettings, fontSize = 11.sp) }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Crossfade(
                targetState = isLoading,
                animationSpec = tween(260),
                label = "loadingFade"
            ) { loading ->
                if (loading) {
                    WavyLoading()
                } else {
                    val isHiddenPredicate = remember(whitelist, blacklist, autoHideModules, autoHideCloneModules) {
                        { entry: AppEntry -> isEntryHidden(entry, whitelist, blacklist, autoHideModules, autoHideCloneModules) }
                    }

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = false,
                        beyondViewportPageCount = preloadPages,
                        key = { it }
                    ) { page ->
                        val tab = MainTab.entries[page]
                        when (tab) {
                            MainTab.HOME -> HomeTabScreen(
                                strings = strings,
                                pendingCount = pendingCount,
                                onDiscardPending = { showDiscardPendingDialog = true },
                                isHookActive = isHookActive,
                                apps = apps,
                                availableUserIds = availableUserIds,
                                autoHideModules = autoHideModules,
                                autoHideCloneModules = autoHideCloneModules,
                                showSystemApps = showSystemApps,
                                whitelist = whitelist,
                                blacklist = blacklist,
                                defaultLauncher = defaultLauncher,
                                isEntryHidden = isHiddenPredicate,
                                scrollState = homeScrollState,
                                isRefreshing = isRefreshing,
                                playEntrance = !homeEntered,
                                isActive = homeActive,
                                onOpenAppsFilter = { mode ->
                                    filterMode = mode
                                    currentTab = MainTab.APPS
                                },
                                onOpenSettings = {
                                    currentTab = MainTab.SETTINGS
                                },
                                onRefreshLauncher = {
                                    refreshState(showToastOnDone = true)
                                },
                                onToggleAppHidden = { entry, hide ->
                                    toggleWithUndo(entry, hide)
                                }
                            )

                            MainTab.APPS -> {
                                val pullState = rememberPullToRefreshState()
                                PullToRefreshBox(
                                    isRefreshing = isRefreshing,
                                    onRefresh = { refreshState(showToastOnDone = false) },
                                    state = pullState,
                                    modifier = Modifier.fillMaxSize(),
                                    indicator = {
                                        WavyPullIndicator(state = pullState, isRefreshing = isRefreshing)
                                    }
                                ) {
                                    AppsTabScreen(
                                        strings = strings,
                                        apps = apps,
                                        availableUserIds = availableUserIds,
                                        showSystemApps = showSystemApps,
                                        autoHideModules = autoHideModules,
                                        autoHideCloneModules = autoHideCloneModules,
                                        whitelist = whitelist,
                                        blacklist = blacklist,
                                        searchQuery = searchQuery,
                                        debouncedQuery = debouncedQuery,
                                        onSearchChange = { searchQuery = it },
                                        filterMode = filterMode,
                                        onFilterModeChange = { filterMode = it },
                                        sortMode = sortMode,
                                        onSortModeChange = { newMode ->
                                            sortMode = newMode
                                            ConfigStore.saveSortMode(context, newMode)
                                        },
                                        isEntryHidden = isHiddenPredicate,
                                        listState = appsListState,
                                        onToggleAppHidden = { entry, hide ->
                                            toggleWithUndo(entry, hide)
                                        }
                                    )
                                }
                            }

                            MainTab.SETTINGS -> SettingsTabScreen(
                                context = context,
                                isHookActive = isHookActive,
                                availableUserIds = availableUserIds,
                                strings = strings,
                                langCode = langCode,
                                themeMode = themeMode,
                                amoledBlack = amoledBlack,
                                dynamicColor = dynamicColor,
                                uiScale = uiScale,
                                autoHideModules = autoHideModules,
                                autoHideCloneModules = autoHideCloneModules,
                                showSystemApps = showSystemApps,
                                scrollState = settingsScrollState,
                                playEntrance = !settingsEntered,
                                onToggleAutoHide = { checked ->
                                    autoHideModules = checked
                                    val modPkgs = apps.filter { it.isXposedModule }.map { it.uniqueKey }.distinct()
                                    syncWithSystem(changedPkgs = modPkgs, op = PendingOp.SetAutoHide(checked))
                                },
                                onToggleAutoHideClone = { checked ->
                                    autoHideCloneModules = checked
                                    val modPkgs = apps.filter { it.isXposedModule && it.userId != 0 }
                                        .map { it.uniqueKey }
                                        .distinct()
                                    syncWithSystem(changedPkgs = modPkgs, op = PendingOp.SetAutoHideClone(checked))
                                },
                                onToggleShowSystemApps = { showSys ->
                                    showSystemApps = showSys
                                    onUpdateAppearance(langCode, themeMode, amoledBlack, dynamicColor, showSys, uiScale)
                                },
                                onUpdateAppearance = { newLang, newTheme, newAmoled, newDynamic, newScale ->
                                    onUpdateAppearance(newLang, newTheme, newAmoled, newDynamic, showSystemApps, newScale)
                                },
                                onRequestExportFile = {
                                    exportLauncher.launch("hidexposed_backup.json")
                                },
                                onRequestImportFile = {
                                    importLauncher.launch(arrayOf("application/json", "*/*"))
                                },
                                onResetAll = {
                                    showResetConfirmDialog = true
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatUserProfileName(userId: Int, strings: AppStrings): String {
    return when (userId) {
        0 -> strings.userMainProfile
        10 -> strings.userCloneProfile
        else -> String.format(java.util.Locale.getDefault(), strings.userProfileFormat, userId)
    }
}

@Composable
private fun HomeTabScreen(
    strings: AppStrings,
    isHookActive: Boolean?,
    apps: List<AppEntry>,
    availableUserIds: List<Int>,
    autoHideModules: Boolean,
    autoHideCloneModules: Boolean,
    showSystemApps: Boolean,
    whitelist: Set<String>,
    blacklist: Set<String>,
    defaultLauncher: LauncherInfo?,
    isEntryHidden: (AppEntry) -> Boolean,
    scrollState: ScrollState,
    isRefreshing: Boolean,
    playEntrance: Boolean,
    isActive: Boolean,
    onOpenAppsFilter: (FilterMode) -> Unit,
    onOpenSettings: () -> Unit,
    onRefreshLauncher: () -> Unit,
    pendingCount: Int,
    onDiscardPending: () -> Unit,
    onToggleAppHidden: (AppEntry, Boolean) -> Unit
) {
    val hiddenApps = remember(apps, showSystemApps, autoHideModules, autoHideCloneModules, whitelist, blacklist) {
        apps.filter { isEntryHidden(it) && (showSystemApps || it.hasLauncherIcon) }
    }
    val xposedCount = remember(apps, showSystemApps) {
        apps.count { it.isXposedModule && (showSystemApps || it.hasLauncherIcon) }
    }
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val colorSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "XHide",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 2.dp, top = 4.dp, bottom = 2.dp)
        )
        val cardBgTarget = when (isHookActive) {
            true -> MaterialTheme.colorScheme.primaryContainer
            false -> MaterialTheme.colorScheme.errorContainer
            null -> MaterialTheme.colorScheme.surfaceVariant
        }
        val cardContentTarget = when (isHookActive) {
            true -> MaterialTheme.colorScheme.onPrimaryContainer
            false -> MaterialTheme.colorScheme.onErrorContainer
            null -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        val dotTarget = when (isHookActive) {
            true -> MaterialTheme.colorScheme.primary
            false -> MaterialTheme.colorScheme.error
            null -> MaterialTheme.colorScheme.outline
        }
        val cardBg by animateColorAsState(cardBgTarget, colorSpec, label = "statusBg")
        val cardContentColor by animateColorAsState(cardContentTarget, colorSpec, label = "statusContent")
        val dotColor by animateColorAsState(dotTarget, colorSpec, label = "statusDot")
        val statusTitle = when (isHookActive) {
            true -> strings.hookActiveTitle
            false -> strings.hookInactiveTitle
            null -> strings.hookCheckingTitle
        }
        val statusDesc = when (isHookActive) {
            true -> strings.hookActiveDesc
            false -> strings.hookInactiveDesc
            null -> strings.hookCheckingDesc
        }

        // 1. Compact Status Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .staggeredEnter(0, playEntrance),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = cardBg,
                contentColor = cardContentColor
            )
        ) {
            Box {
            HookCardBackdrop(
                accent = dotColor,
                eyeColor = cardContentColor.copy(alpha = 0.14f),
                modifier = Modifier.matchParentSize(),
                active = isActive
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HookStatusBadge(
                        state = isHookActive,
                        color = dotColor,
                        size = 18.dp,
                        active = isActive
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = statusTitle,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = cardContentColor
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "${Build.MODEL} · SDK ${Build.VERSION.SDK_INT}",
                        fontSize = 11.sp,
                        color = cardContentColor.copy(alpha = 0.82f)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = statusDesc.replace(" · ", "\n"),
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp,
                    color = cardContentColor.copy(alpha = 0.9f)
                )
                AnimatedVisibility(visible = isHookActive == null) {
                    LinearWavyProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                }
                if (isHookActive == false) {
                    Spacer(modifier = Modifier.height(8.dp))
                    FilledTonalButton(
                        onClick = {
                            AppDiagnostics.openGitHubIssue(
                                context = context,
                                isHookActive = false
                            )
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text(strings.hookInactiveHelpBtn, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (pendingCount > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = String.format(java.util.Locale.getDefault(), strings.pendingChangesFormat, pendingCount),
                            fontSize = 11.5.sp,
                            lineHeight = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = cardContentColor,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = onDiscardPending,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text(strings.discardPendingBtn, fontSize = 11.5.sp, color = cardContentColor)
                        }
                    }
                }
            }
            }
        }

        // 2. 2x2 Compact Clickable Dashboard Stats
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .staggeredEnter(1, playEntrance),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DashboardStatCard(
                modifier = Modifier.weight(1f),
                title = strings.statHiddenIcons,
                iconRes = org.lsposed.hidexposed.R.drawable.ic_eye_off,
                value = hiddenApps.size.toString(),
                countTo = hiddenApps.size,
                animateCount = playEntrance,
                countDelayMillis = 380L,
                subtitle = strings.viewAll,
                onClick = { onOpenAppsFilter(FilterMode.HiddenOnly) }
            )
            DashboardStatCard(
                modifier = Modifier.weight(1f),
                title = strings.statXposedModules,
                iconRes = org.lsposed.hidexposed.R.drawable.ic_extension,
                value = xposedCount.toString(),
                countTo = xposedCount,
                animateCount = playEntrance,
                countDelayMillis = 380L,
                subtitle = strings.statXposedSubtitle,
                onClick = { onOpenAppsFilter(FilterMode.XposedOnly) }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .staggeredEnter(2, playEntrance),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val profileSummary = availableUserIds.joinToString(" · ") { formatUserProfileName(it, strings) }
            DashboardStatCard(
                modifier = Modifier.weight(1f),
                title = strings.statActiveProfiles,
                iconRes = org.lsposed.hidexposed.R.drawable.ic_group,
                value = availableUserIds.size.toString(),
                countTo = availableUserIds.size,
                animateCount = playEntrance,
                countDelayMillis = 440L,
                subtitle = profileSummary,
                onClick = {
                    val targetUser = availableUserIds.lastOrNull() ?: 0
                    onOpenAppsFilter(FilterMode.ByUser(targetUser))
                }
            )
            DashboardStatCard(
                modifier = Modifier.weight(1f),
                title = strings.statAutoHide,
                iconRes = org.lsposed.hidexposed.R.drawable.ic_auto_awesome,
                value = if (autoHideModules) strings.enabled else strings.disabled,
                subtitle = strings.navSettings,
                onClick = onOpenSettings
            )
        }

        // 3. Active Default Launcher & Quick Refresh Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .staggeredEnter(3, playEntrance),
            shape = RoundedCornerShape(14.dp),
            tonalElevation = 2.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (defaultLauncher?.icon != null) {
                        Image(
                            bitmap = defaultLauncher.icon,
                            contentDescription = defaultLauncher.label,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.activeLauncherTitle,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = defaultLauncher?.label ?: strings.defaultSystemLauncher,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = defaultLauncher?.packageName ?: "android",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    FilledTonalButton(
                        onClick = onRefreshLauncher,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text(strings.refreshLauncherBtn, fontSize = 11.5.sp)
                    }
                }
                AnimatedVisibility(visible = isRefreshing) {
                    LinearWavyProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
                    )
                }
            }
        }

        // 4. Currently Hidden Icons Quick Preview Strip
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .staggeredEnter(4, playEntrance),
            shape = RoundedCornerShape(14.dp),
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${strings.quickHiddenPreviewTitle} (${hiddenApps.size})",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = strings.tapToUnhideHint,
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        onClick = { onOpenAppsFilter(FilterMode.HiddenOnly) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text(strings.viewAll, fontSize = 11.5.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (hiddenApps.isEmpty()) {
                    EmptyHiddenState(text = strings.noHiddenAppsYet, active = isActive)
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 2.dp)
                    ) {
                        items(hiddenApps, key = { it.uniqueKey }) { app ->
                            Column(
                                modifier = Modifier
                                    .animateItem(
                                        fadeInSpec = tween(durationMillis = 180, easing = LinearOutSlowInEasing),
                                        fadeOutSpec = tween(durationMillis = 150, easing = FastOutLinearInEasing),
                                        placementSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                    .width(62.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .bouncyClickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.ToggleOff)
                                        onToggleAppHidden(app, false)
                                    }
                                    .padding(vertical = 4.dp, horizontal = 2.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box {
                                    AppIcon(
                                        packageName = app.packageName,
                                        userId = app.userId,
                                        lastUpdateTime = app.lastUpdateTime,
                                        label = app.label,
                                        size = 38.dp,
                                        corner = 9.dp,
                                        placeholderFontSize = 15.sp
                                    )
                                    if (app.userId != 0) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.tertiary,
                                            shape = RoundedCornerShape(4.dp),
                                            modifier = Modifier.align(Alignment.BottomEnd)
                                        ) {
                                            Text(
                                                text = "U${app.userId}",
                                                fontSize = 8.sp,
                                                color = MaterialTheme.colorScheme.onTertiary,
                                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = app.label,
                                    fontSize = 10.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHiddenState(text: String, active: Boolean = true) {
    // Bobs only while Home is on screen (the pager keeps neighbouring tabs composed).
    val bob = remember { Animatable(-3f) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        while (true) {
            bob.animateTo(3f, tween(1800, easing = FastOutSlowInEasing))
            bob.animateTo(-3f, tween(1800, easing = FastOutSlowInEasing))
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            painter = painterResource(id = org.lsposed.hidexposed.R.drawable.ic_eye_off),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(28.dp)
                .graphicsLayer { translationY = bob.value.dp.toPx() }
        )
        Text(
            text = text,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DashboardStatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String,
    onClick: () -> Unit,
    iconRes: Int,
    countTo: Int? = null,
    animateCount: Boolean = false,
    countDelayMillis: Long = 0L
) {
    val source = remember { MutableInteractionSource() }
    val counted = if (countTo != null) rememberCountUp(countTo, animateCount, countDelayMillis) else 0
    Surface(
        onClick = onClick,
        modifier = modifier.pressScale(source),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
        interactionSource = source
    ) {
        Box {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 16.dp, y = 16.dp)
                .size(68.dp)
        )
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                text = title,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (countTo != null) counted.toString() else value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        }
    }
}

private class AppsListScopeTracker {
    var prevIndices: Map<String, Int> = emptyMap()
    var prevHiddenKeys: Set<String> = emptySet()
    val keyVersions = HashMap<String, Int>()
}

@Composable
private fun AppsTabScreen(
    strings: AppStrings,
    apps: List<AppEntry>,
    availableUserIds: List<Int>,
    showSystemApps: Boolean,
    autoHideModules: Boolean,
    autoHideCloneModules: Boolean,
    whitelist: Set<String>,
    blacklist: Set<String>,
    searchQuery: String,
    debouncedQuery: String,
    onSearchChange: (String) -> Unit,
    filterMode: FilterMode,
    onFilterModeChange: (FilterMode) -> Unit,
    sortMode: AppSortMode,
    onSortModeChange: (AppSortMode) -> Unit,
    isEntryHidden: (AppEntry) -> Boolean,
    listState: LazyListState,
    onToggleAppHidden: (AppEntry, Boolean) -> Unit
) {
    val viewScopeKey = remember(filterMode, debouncedQuery, sortMode, showSystemApps) {
        val filterTag = when (val mode = filterMode) {
            is FilterMode.AllApps -> "all"
            is FilterMode.XposedOnly -> "xposed"
            is FilterMode.HiddenOnly -> "hidden"
            is FilterMode.ByUser -> "u${mode.userId}"
        }
        "${filterTag}_${debouncedQuery}_${sortMode.code}_$showSystemApps"
    }

    val actualHiddenKeys = remember(apps, autoHideModules, autoHideCloneModules, whitelist, blacklist) {
        apps.asSequence().filter { isEntryHidden(it) }.mapTo(HashSet()) { it.uniqueKey }
    }

    var settledHiddenKeys by remember { mutableStateOf(actualHiddenKeys) }
    var hasInitializedHiddenKeys by remember { mutableStateOf(apps.isNotEmpty()) }

    val effectiveSettledHiddenKeys = if (!hasInitializedHiddenKeys && apps.isNotEmpty()) {
        actualHiddenKeys
    } else {
        settledHiddenKeys
    }

    LaunchedEffect(actualHiddenKeys, apps.isEmpty()) {
        if (apps.isEmpty()) {
            settledHiddenKeys = actualHiddenKeys
        } else if (!hasInitializedHiddenKeys) {
            hasInitializedHiddenKeys = true
            settledHiddenKeys = actualHiddenKeys
        } else if (settledHiddenKeys != actualHiddenKeys) {
            delay(190)
            settledHiddenKeys = actualHiddenKeys
        }
    }

    val scopeTracker = remember(viewScopeKey) { AppsListScopeTracker() }

    val header = remember { CollapsingHeaderState() }
    val (filteredApps, itemKeyVersions) = remember(
        apps,
        debouncedQuery,
        filterMode,
        sortMode,
        showSystemApps,
        effectiveSettledHiddenKeys,
        viewScopeKey
    ) {
        val collator = java.text.Collator.getInstance().apply {
            strength = java.text.Collator.PRIMARY
        }
        val isSettledHidden: (AppEntry) -> Boolean = { it.uniqueKey in effectiveSettledHiddenKeys }
        val comparator = when (sortMode) {
            AppSortMode.NAME_ASC -> {
                compareByDescending<AppEntry> { isSettledHidden(it) }
                    .thenByDescending { it.isXposedModule }
                    .thenBy { it.userId }
                    .thenComparing { a, b -> collator.compare(a.label, b.label) }
            }
            AppSortMode.NAME_DESC -> {
                compareByDescending<AppEntry> { isSettledHidden(it) }
                    .thenByDescending { it.isXposedModule }
                    .thenBy { it.userId }
                    .thenComparing { a, b -> collator.compare(b.label, a.label) }
            }
            AppSortMode.INSTALL_DATE_DESC -> {
                compareByDescending<AppEntry> { isSettledHidden(it) }
                    .thenByDescending { it.firstInstallTime }
                    .thenBy { it.userId }
                    .thenComparing { a, b -> collator.compare(a.label, b.label) }
            }
            AppSortMode.INSTALL_DATE_ASC -> {
                compareByDescending<AppEntry> { isSettledHidden(it) }
                    .thenBy { it.firstInstallTime }
                    .thenBy { it.userId }
                    .thenComparing { a, b -> collator.compare(a.label, b.label) }
            }
            AppSortMode.UPDATE_DATE_DESC -> {
                compareByDescending<AppEntry> { isSettledHidden(it) }
                    .thenByDescending { it.lastUpdateTime }
                    .thenBy { it.userId }
                    .thenComparing { a, b -> collator.compare(a.label, b.label) }
            }
        }
        val sorted = apps.filter { app ->
            val allowSystem = showSystemApps || (!app.isSystemApp && app.hasLauncherIcon) || (app.userId != 0 && app.hasLauncherIcon)
            val matchesMode = when (val mode = filterMode) {
                is FilterMode.AllApps -> allowSystem
                is FilterMode.XposedOnly -> app.isXposedModule && allowSystem
                is FilterMode.HiddenOnly -> isSettledHidden(app)
                is FilterMode.ByUser -> app.userId == mode.userId && allowSystem
            }
            val matchesQuery = debouncedQuery.isBlank() ||
                app.label.contains(debouncedQuery, ignoreCase = true) ||
                app.packageName.contains(debouncedQuery, ignoreCase = true)
            matchesMode && matchesQuery
        }.sortedWith(comparator)

        if (scopeTracker.prevIndices.isNotEmpty() && effectiveSettledHiddenKeys != scopeTracker.prevHiddenKeys) {
            for ((newIdx, entry) in sorted.withIndex()) {
                val uKey = entry.uniqueKey
                val wasHidden = uKey in scopeTracker.prevHiddenKeys
                val nowHidden = uKey in effectiveSettledHiddenKeys
                val oldIdx = scopeTracker.prevIndices[uKey]
                if (wasHidden != nowHidden && oldIdx != null && oldIdx != newIdx) {
                    scopeTracker.keyVersions[uKey] = (scopeTracker.keyVersions[uKey] ?: 0) + 1
                }
            }
        }
        scopeTracker.prevIndices = sorted.withIndex().associate { (i, e) -> e.uniqueKey to i }
        scopeTracker.prevHiddenKeys = effectiveSettledHiddenKeys

        sorted to HashMap(scopeTracker.keyVersions)
    }

    val allCount = remember(apps, showSystemApps) {
        apps.count { showSystemApps || (!it.isSystemApp && it.hasLauncherIcon) || (it.userId != 0 && it.hasLauncherIcon) }
    }
    val xposedCount = remember(apps, showSystemApps) {
        apps.count { it.isXposedModule && (showSystemApps || (!it.isSystemApp && it.hasLauncherIcon)) }
    }
    val hiddenCount = remember(apps, showSystemApps, autoHideModules, autoHideCloneModules, whitelist, blacklist) {
        apps.count { isEntryHidden(it) && (showSystemApps || (!it.isSystemApp && it.hasLauncherIcon)) }
    }
    val userCounts = remember(apps, showSystemApps) {
        val map = mutableMapOf<Int, Int>()
        for (app in apps) {
            if (showSystemApps || (!app.isSystemApp && app.hasLauncherIcon) || (app.userId != 0 && app.hasLauncherIcon)) {
                map[app.userId] = (map[app.userId] ?: 0) + 1
            }
        }
        map
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(header.connection)
            .padding(horizontal = 12.dp)
    ) {
        Text(
            text = strings.navApps,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .collapsible(header, CollapsePart.TITLE)
                .onSizeChanged { header.titleHeight = it.height.toFloat() }
                .padding(start = 2.dp, top = 4.dp, bottom = 6.dp)
        )

        val chipCenters = remember { HashMap<Int, Float>() }
        var listRootX by remember { mutableFloatStateOf(0f) }
        var listWidth by remember { mutableFloatStateOf(1f) }
        val enter = remember { Animatable(1f) }
        var originFraction by remember { mutableFloatStateOf(0.5f) }
        var slideDir by remember { mutableFloatStateOf(0f) }
        var filterChangedAt by remember { mutableLongStateOf(0L) }
        fun chipIndexOf(mode: FilterMode): Int = when (mode) {
            is FilterMode.AllApps -> 0
            is FilterMode.XposedOnly -> 1
            is FilterMode.HiddenOnly -> 2
            is FilterMode.ByUser -> 3 + availableUserIds.indexOf(mode.userId).coerceAtLeast(0)
        }
        val selectFilter: (FilterMode) -> Unit = { mode ->
            if (mode != filterMode) {
                slideDir = if (chipIndexOf(mode) > chipIndexOf(filterMode)) 1f else -1f
                val cx = chipCenters[chipIndexOf(mode)]
                originFraction = if (cx != null && listWidth > 1f) {
                    ((cx - listRootX) / listWidth).coerceIn(0.05f, 0.95f)
                } else 0.5f
                filterChangedAt = System.currentTimeMillis()
                onFilterModeChange(mode)
            }
        }
        LaunchedEffect(filterMode, sortMode) {
            if (filterChangedAt != 0L) {
                enter.snapTo(0f)
                enter.animateTo(1f, spring(dampingRatio = 0.92f, stiffness = Spring.StiffnessMediumLow))
            }
        }

        // Sort changes replay the same list reveal, growing out of the sort button (top-right).
        val selectSort: (AppSortMode) -> Unit = { mode ->
            if (mode != sortMode) {
                slideDir = 0f
                originFraction = 1f
                filterChangedAt = System.currentTimeMillis()
                onSortModeChange(mode)
            }
        }

        // Compact Search Bar (38dp height) & Filter/Sort Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp),
                shape = RoundedCornerShape(10.dp),
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SearchVectorIcon()
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = strings.searchPlaceholder,
                                fontSize = 12.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        val focusManager = LocalFocusManager.current
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = onSearchChange,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                            textStyle = TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 12.5.sp
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    AnimatedVisibility(
                        visible = searchQuery.isNotEmpty(),
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut()
                    ) {
                        val focusManager = LocalFocusManager.current
                        IconButton(
                            onClick = {
                                onSearchChange("")
                                focusManager.clearFocus()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = org.lsposed.hidexposed.R.drawable.ic_close),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.width(6.dp))
            SortMenuButton(
                title = strings.sortTitle,
                options = listOf(
                    AppSortMode.NAME_ASC to strings.sortNameAsc,
                    AppSortMode.NAME_DESC to strings.sortNameDesc,
                    AppSortMode.INSTALL_DATE_DESC to strings.sortInstallDateDesc,
                    AppSortMode.INSTALL_DATE_ASC to strings.sortInstallDateAsc,
                    AppSortMode.UPDATE_DATE_DESC to strings.sortUpdateDateDesc
                ),
                selected = sortMode,
                highlighted = sortMode != AppSortMode.NAME_ASC,
                onSelect = selectSort
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Filter Chips Row (order: Tümü -> Xposed -> Gizli -> User 0 -> User 10...)
        Box(
            modifier = Modifier
                .collapsible(header, CollapsePart.CHIPS)
                .onSizeChanged { header.chipsHeight = it.height.toFloat() }
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CompactFilterChip(
                selected = filterMode is FilterMode.AllApps,
                label = "${strings.filterAll} ($allCount)",
                onClick = { selectFilter(FilterMode.AllApps) },
                modifier = Modifier.onGloballyPositioned { chipCenters[0] = it.positionInRoot().x + it.size.width / 2f }
            )
            CompactFilterChip(
                selected = filterMode is FilterMode.XposedOnly,
                label = "${strings.filterXposed} ($xposedCount)",
                onClick = { selectFilter(FilterMode.XposedOnly) },
                modifier = Modifier.onGloballyPositioned { chipCenters[1] = it.positionInRoot().x + it.size.width / 2f }
            )
            CompactFilterChip(
                selected = filterMode is FilterMode.HiddenOnly,
                label = "${strings.filterHidden} ($hiddenCount)",
                onClick = { selectFilter(FilterMode.HiddenOnly) },
                modifier = Modifier.onGloballyPositioned { chipCenters[2] = it.positionInRoot().x + it.size.width / 2f }
            )
            for ((uidIndex, uid) in availableUserIds.withIndex()) {
                val countForUser = userCounts[uid] ?: 0
                CompactFilterChip(
                    selected = (filterMode as? FilterMode.ByUser)?.userId == uid,
                    label = "${formatUserProfileName(uid, strings)} ($countForUser)",
                    onClick = { selectFilter(FilterMode.ByUser(uid)) },
                    modifier = Modifier.onGloballyPositioned { chipCenters[3 + uidIndex] = it.positionInRoot().x + it.size.width / 2f }
                )
            }
        }
        }

        var initialScopeKey by remember { mutableStateOf(viewScopeKey) }

        LaunchedEffect(viewScopeKey) {
            if (initialScopeKey != viewScopeKey) {
                listState.scrollToItem(0, 0)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned {
                    listRootX = it.positionInRoot().x
                    listWidth = it.size.width.toFloat()
                }
        ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = enter.value
                    val rest = 1f - p
                    transformOrigin = TransformOrigin(originFraction, 0f)
                    scaleX = 0.96f + 0.04f * p
                    scaleY = 0.93f + 0.07f * p
                    translationX = slideDir * 22.dp.toPx() * rest
                    rotationZ = slideDir * 1.5f * rest
                    alpha = (p * 2.5f).coerceIn(0f, 1f)
                }
        ) {
        key(viewScopeKey) {
            val scopedListState = remember {
                if (initialScopeKey == viewScopeKey) {
                    listState
                } else {
                    initialScopeKey = viewScopeKey
                    LazyListState(0, 0)
                }
            }

            if (filteredApps.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = strings.emptyAppsList,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = scopedListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(
                        items = filteredApps,
                        key = { _, entry -> "${itemKeyVersions[entry.uniqueKey] ?: 0}_${entry.uniqueKey}" }
                    ) { index, entry ->
                        AppItemRow(
                            entry = entry,
                            isHidden = entry.uniqueKey in actualHiddenKeys,
                            strings = strings,
                            sortMode = sortMode,
                            modifier = Modifier.staggeredEnter(
                                index = index,
                                enabled = index < 8 && System.currentTimeMillis() - filterChangedAt < 900L,
                                stepMillis = 18L,
                                riseFrom = (-10).dp
                            ).animateItem(
                                fadeInSpec = tween(durationMillis = 180, easing = LinearOutSlowInEasing),
                                fadeOutSpec = tween(durationMillis = 150, easing = FastOutLinearInEasing),
                                placementSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ),
                            onToggle = { hide ->
                                onToggleAppHidden(entry, hide)
                            }
                        )
                    }
                }
            }
        }
        }
        }
    }
}

@Composable
private fun AppItemRow(
    entry: AppEntry,
    isHidden: Boolean,
    strings: AppStrings,
    sortMode: AppSortMode = AppSortMode.NAME_ASC,
    modifier: Modifier = Modifier,
    onToggle: (Boolean) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(10.dp)

    val rowColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interaction, 0.985f),
        shape = shape,
        color = rowColor
    ) {
        Column(
            modifier = Modifier.combinedClickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                role = Role.Switch,
                onClick = {
                    haptic.performHapticFeedback(
                        if (isHidden) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn
                    )
                    onToggle(!isHidden)
                }
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIcon(
                    packageName = entry.packageName,
                    userId = entry.userId,
                    lastUpdateTime = entry.lastUpdateTime,
                    label = entry.label,
                    size = 34.dp,
                    corner = 8.dp
                )
                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = entry.label,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (entry.userId != 0) {
                            Spacer(modifier = Modifier.width(5.dp))
                            MiniBadge(
                                text = formatUserProfileName(entry.userId, strings),
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        if (entry.isXposedModule) {
                            Spacer(modifier = Modifier.width(5.dp))
                            MiniBadge(
                                text = "Xposed",
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        if (!entry.hasLauncherIcon) {
                            Spacer(modifier = Modifier.width(5.dp))
                            MiniBadge(
                                text = strings.badgeNoIcon,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (entry.isSystemApp && !entry.isXposedModule) {
                            Spacer(modifier = Modifier.width(5.dp))
                            MiniBadge(
                                text = strings.badgeSystem,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    val dateInfo = remember(entry, sortMode) {
                        when (sortMode) {
                            AppSortMode.INSTALL_DATE_DESC, AppSortMode.INSTALL_DATE_ASC -> {
                                if (entry.firstInstallTime > 0L) {
                                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                                    sdf.format(java.util.Date(entry.firstInstallTime))
                                } else null
                            }
                            AppSortMode.UPDATE_DATE_DESC -> {
                                if (entry.lastUpdateTime > 0L) {
                                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                                    sdf.format(java.util.Date(entry.lastUpdateTime))
                                } else null
                            }
                            else -> null
                        }
                    }
                    Text(
                        text = if (dateInfo != null) "${entry.packageName} · $dateInfo" else entry.packageName,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                OneUiSwitch(
                    checked = isHidden,
                    pressed = pressed
                )
            }
        }
    }
}

@Composable
private fun OneUiSwitch(
    checked: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    pressed: Boolean = false
) {
    val trackWidth = 46.dp
    val trackHeight = 26.dp
    val thumbHeight = 20.dp
    val thumbPadding = 3.dp

    val dpSpec = MaterialTheme.motionScheme.fastSpatialSpec<Dp>()
    val floatSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val colorSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()

    // The thumb stretches while pressed (One UI style) and springs between the ends.
    val thumbWidth by animateDpAsState(
        targetValue = if (pressed && enabled) 26.dp else thumbHeight,
        animationSpec = dpSpec,
        label = "switchThumbWidth"
    )
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = floatSpec,
        label = "switchProgress"
    )

    val trackColor by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            checked -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = colorSpec,
        label = "switchTrackColor"
    )

    val thumbColor by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            checked -> MaterialTheme.colorScheme.onPrimary
            else -> MaterialTheme.colorScheme.outline
        },
        animationSpec = colorSpec,
        label = "switchThumbColor"
    )

    Box(
        modifier = modifier
            .size(width = trackWidth, height = trackHeight)
            .clip(CircleShape)
            .background(trackColor)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset {
                    val left = thumbPadding.toPx()
                    val right = (trackWidth - thumbPadding - thumbWidth).toPx()
                    IntOffset((left + (right - left) * progress).roundToInt(), 0)
                }
                .size(width = thumbWidth, height = thumbHeight)
                .clip(CircleShape)
                .background(thumbColor)
        )
    }
}

@Composable
private fun CompactFilterChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 11.5.sp) },
        leadingIcon = if (selected) {
            {
                Icon(
                    painter = painterResource(id = org.lsposed.hidexposed.R.drawable.ic_check),
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                )
            }
        } else {
            null
        },
        modifier = modifier
            .heightIn(min = 32.dp)
            .animateContentSize(animationSpec = MaterialTheme.motionScheme.fastSpatialSpec())
    )
}

@Composable
private fun MiniBadge(
    text: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color
) {
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = text,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Medium,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
        )
    }
}

@Composable
private fun SettingsTabScreen(
    context: Context,
    isHookActive: Boolean?,
    availableUserIds: List<Int>,
    strings: AppStrings,
    langCode: String,
    themeMode: String,
    amoledBlack: Boolean,
    dynamicColor: Boolean,
    uiScale: Float,
    autoHideModules: Boolean,
    autoHideCloneModules: Boolean,
    showSystemApps: Boolean,
    onToggleAutoHide: (Boolean) -> Unit,
    onToggleAutoHideClone: (Boolean) -> Unit,
    onToggleShowSystemApps: (Boolean) -> Unit,
    onUpdateAppearance: (String, String, Boolean, Boolean, Float) -> Unit,
    onRequestExportFile: () -> Unit,
    onRequestImportFile: () -> Unit,
    scrollState: ScrollState,
    playEntrance: Boolean,
    onResetAll: () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val isDarkEffective = when (themeMode) {
        "DARK" -> true
        "LIGHT" -> false
        else -> systemDark
    }

    var localUiScale by remember(uiScale) { mutableFloatStateOf(uiScale) }
    var showReportConfirmDialog by remember { mutableStateOf(false) }
    val defaultScale = ConfigStore.DEFAULT_UI_SCALE

    if (showReportConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showReportConfirmDialog = false },
            title = { Text(text = strings.reportIssueTitle, fontWeight = FontWeight.Bold) },
            text = { Text(text = strings.reportIssueConfirmDesc) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showReportConfirmDialog = false
                        AppDiagnostics.openGitHubIssue(
                            context = context,
                            isHookActive = isHookActive == true
                        )
                    }
                ) {
                    Text(text = strings.reportIssueConfirmBtn, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showReportConfirmDialog = false }) {
                    Text(text = strings.dismissBtn)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = strings.navSettings,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 2.dp, top = 4.dp, bottom = 2.dp)
        )

        // Section 1: Hiding Rules
        SettingsGroup(
            title = strings.sectionHidingRules,
            modifier = Modifier.staggeredEnter(0, playEntrance)
        ) {
            item {
                CompactSwitchRow(
                    title = strings.autoHideTitle,
                    subtitle = strings.autoHideDesc,
                    checked = autoHideModules,
                    onCheckedChange = onToggleAutoHide
                )
            }
            item {
                CompactSwitchRow(
                    title = strings.autoHideCloneTitle,
                    subtitle = strings.autoHideCloneDesc,
                    checked = autoHideCloneModules,
                    onCheckedChange = onToggleAutoHideClone
                )
            }
            item {
                CompactSwitchRow(
                    title = strings.showSystemAppsTitle,
                    subtitle = strings.showSystemAppsDesc,
                    checked = showSystemApps,
                    onCheckedChange = onToggleShowSystemApps
                )
            }
        }

        // Section 2: Appearance & Language
        SettingsGroup(
            title = strings.sectionAppearance,
            modifier = Modifier.staggeredEnter(1, playEntrance)
        ) {
            item {
                var showLangDialog by remember { mutableStateOf(false) }
                val newSystemPicker = Build.VERSION.SDK_INT >= 33
                val currentLangName = if (newSystemPicker) {
                    val loc = java.util.Locale.getDefault()
                    loc.getDisplayLanguage(loc).replaceFirstChar { it.titlecase(loc) }
                } else if (langCode == "SYSTEM") {
                    strings.languageSystem
                } else {
                    Localization.languageOptions.firstOrNull { it.first == langCode }?.second ?: langCode
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (newSystemPicker) {
                                try {
                                    context.startActivity(
                                        Intent(
                                            android.provider.Settings.ACTION_APP_LOCALE_SETTINGS,
                                            android.net.Uri.fromParts("package", context.packageName, null)
                                        )
                                    )
                                } catch (e: Exception) {
                                    showLangDialog = true
                                }
                            } else {
                                showLangDialog = true
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.languageTitle,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = currentLangName,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (showLangDialog) {
                    AlertDialog(
                        onDismissRequest = { showLangDialog = false },
                        title = { Text(strings.languageTitle, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                        text = {
                            Column {
                                for ((code, displayName) in Localization.languageOptions) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable {
                                                showLangDialog = false
                                                onUpdateAppearance(code, themeMode, amoledBlack, dynamicColor, uiScale)
                                            }
                                            .padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(selected = langCode == code, onClick = null)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = if (code == "SYSTEM") strings.languageSystem else displayName,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showLangDialog = false }) { Text(strings.dismissBtn) }
                        }
                    )
                }
            }
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = strings.themeTitle,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    val themes = listOf(
                        "SYSTEM" to strings.themeSystem,
                        "DARK" to strings.themeDark,
                        "LIGHT" to strings.themeLight
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        themes.forEachIndexed { index, (code, label) ->
                            SegmentedButton(
                                selected = themeMode == code,
                                onClick = {
                                    onUpdateAppearance(langCode, code, amoledBlack, dynamicColor, uiScale)
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = themes.size)
                            ) {
                                Text(label, fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
            item {
                CompactSwitchRow(
                    title = strings.amoledTitle,
                    subtitle = strings.amoledDesc,
                    checked = isDarkEffective && amoledBlack,
                    enabled = isDarkEffective,
                    onCheckedChange = { checked ->
                        if (isDarkEffective) {
                            onUpdateAppearance(langCode, themeMode, checked, dynamicColor, uiScale)
                        }
                    }
                )
            }
            item {
                CompactSwitchRow(
                    title = strings.dynamicColorTitle,
                    subtitle = strings.dynamicColorDesc,
                    checked = dynamicColor,
                    onCheckedChange = { checked ->
                        onUpdateAppearance(langCode, themeMode, amoledBlack, checked, uiScale)
                    }
                )
            }
            item {
                // Display Size / DPI Slider
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.uiScaleTitle,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = strings.uiScaleDesc,
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        val pct = (localUiScale * 100).roundToInt()
                        val isDefaultScale = abs(localUiScale - defaultScale) < 0.001f
                        val scaleBadgeText = if (isDefaultScale) "$pct% (${strings.defaultLabel})" else "$pct%"
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.clickable {
                                if (!isDefaultScale) {
                                    localUiScale = defaultScale
                                    onUpdateAppearance(langCode, themeMode, amoledBlack, dynamicColor, defaultScale)
                                }
                            }
                        ) {
                            Text(
                                text = scaleBadgeText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Slider(
                            value = localUiScale,
                            onValueChange = { newVal ->
                                localUiScale = (newVal * 100f).roundToInt() / 100f
                            },
                            onValueChangeFinished = {
                                onUpdateAppearance(langCode, themeMode, amoledBlack, dynamicColor, localUiScale)
                            },
                            valueRange = 0.90f..1.50f,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "A",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                }
            }
        }

        // Section 3: Backup & Reset (SAF File Export / Import)
        SettingsGroup(
            title = strings.sectionBackupReset,
            modifier = Modifier.staggeredEnter(2, playEntrance)
        ) {
            item {
                CompactActionRow(
                    title = strings.exportConfigTitle,
                    subtitle = strings.exportConfigDesc,
                    onClick = onRequestExportFile
                )
            }
            item {
                CompactActionRow(
                    title = strings.importConfigTitle,
                    subtitle = if (isHookActive == true) strings.importConfigDesc else strings.requiresActiveHook,
                    enabled = isHookActive == true,
                    onClick = onRequestImportFile
                )
            }
            item {
                CompactActionRow(
                    title = strings.resetAllTitle,
                    subtitle = if (isHookActive == true) strings.resetAllDesc else strings.requiresActiveHook,
                    isDestructive = true,
                    enabled = isHookActive == true,
                    onClick = onResetAll
                )
            }
        }

        // Section 4: About & System
        SettingsGroup(
            title = strings.sectionAbout,
            modifier = Modifier.staggeredEnter(3, playEntrance)
        ) {
            item {
                CompactInfoRow(
                    title = strings.hookTargetTitle,
                    value = "LauncherAppsService\$LauncherAppsImpl"
                )
            }
            item {
                CompactInfoRow(
                    title = strings.versionTitle,
                    value = "v${BuildConfig.VERSION_NAME} (Android 11–17 Multi-User)"
                )
            }
        }

        // Section 5: GitHub & Community
        SettingsGroup(
            title = strings.githubSectionTitle,
            modifier = Modifier.staggeredEnter(4, playEntrance)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { AppDiagnostics.openGitHubRepo(context) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GitHubVectorIcon()
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.githubRepoTitle,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = strings.githubRepoDesc,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = { AppDiagnostics.openGitHubRepo(context) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("GitHub", fontSize = 11.sp)
                    }
                }
            }
            item {
                CompactActionRow(
                    title = strings.reportIssueTitle,
                    subtitle = strings.reportIssueDesc,
                    onClick = { showReportConfirmDialog = true }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

private class SettingsGroupScope {
    val items = mutableListOf<@Composable () -> Unit>()
    fun item(content: @Composable () -> Unit) {
        items.add(content)
    }
}

/** Connected list group: large outer corners, small inner corners, thin gaps between rows. */
@Composable
private fun SettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    build: SettingsGroupScope.() -> Unit
) {
    val group = SettingsGroupScope().apply(build)
    val outer = 18.dp
    val inner = 6.dp
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val last = group.items.lastIndex
            group.items.forEachIndexed { index, content ->
                val top = if (index == 0) outer else inner
                val bottom = if (index == last) outer else inner
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(
                        topStart = top,
                        topEnd = top,
                        bottomStart = bottom,
                        bottomEnd = bottom
                    ),
                    tonalElevation = 2.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        content()
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.42f)
            .toggleable(
                value = checked,
                interactionSource = source,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = { value ->
                    haptic.performHapticFeedback(
                        if (value) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff
                    )
                    onCheckedChange(value)
                }
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        OneUiSwitch(
            checked = checked,
            enabled = enabled,
            pressed = pressed
        )
    }
}

@Composable
private fun CompactActionRow(
    title: String,
    subtitle: String,
    isDestructive: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    // Disabled rows stay visible with an explanation instead of disappearing, so the user knows
    // the action exists and what unlocks it.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.38f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 6.dp)
    ) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = subtitle,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CompactInfoRow(
    title: String,
    value: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = title,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun detectDefaultLauncher(context: Context): LauncherInfo? {
    return try {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            ?: return null
        val actInfo = resolveInfo.activityInfo ?: return null
        val pkg = actInfo.packageName ?: return null
        val label = resolveInfo.loadLabel(pm)?.toString() ?: pkg
        val icon = drawableToImageBitmap(resolveInfo.loadIcon(pm), 96)
        LauncherInfo(label = label, packageName = pkg, icon = icon)
    } catch (_: Throwable) {
        null
    }
}

private fun getUserIdFromHandle(userHandle: UserHandle?, userManager: UserManager? = null): Int {
    if (userHandle == null) return 0
    return try {
        val method = UserHandle::class.java.getDeclaredMethod("getIdentifier")
        method.isAccessible = true
        (method.invoke(userHandle) as? Int) ?: userHandle.hashCode()
    } catch (_: Throwable) {
        userHandle.hashCode()
    }
}

private fun loadInstalledAppsAcrossProfiles(context: Context): List<AppEntry> {
    val pm = context.packageManager
    val launcherApps = context.getSystemService(LauncherApps::class.java)
    val userManager = context.getSystemService(UserManager::class.java)

    val profiles = LinkedHashSet<UserHandle>()
    profiles.add(Process.myUserHandle())
    try {
        launcherApps?.profiles?.let { profiles.addAll(it) }
    } catch (_: Throwable) {
    }
    try {
        userManager?.userProfiles?.let { profiles.addAll(it) }
    } catch (_: Throwable) {
    }

    val entriesMap = LinkedHashMap<String, AppEntry>()
    val installedPackagesSet = HashSet<String>()

    // Query all launcher activities across all profiles (User 0, User 10 Clone/Work Profile, etc.)
    // Only apps with real launcher activities are included so every entry maps directly to a visible home screen / drawer icon.
    if (launcherApps != null) {
        val allActivities = mutableListOf<android.content.pm.LauncherActivityInfo>()
        for (profile in profiles) {
            try {
                val list = launcherApps.getActivityList(null, profile)
                if (!list.isNullOrEmpty()) {
                    allActivities.addAll(list)
                }
            } catch (_: Throwable) {
            }
        }

        // Fallback if only 1 profile was returned by UserManager
        if (profiles.size <= 1) {
            try {
                val fromIpc = launcherApps.getActivityList(ConfigStore.IPC_GET_ALL_USERS, Process.myUserHandle())
                if (fromIpc != null && fromIpc.size > 1) {
                    allActivities.addAll(fromIpc)
                }
            } catch (_: Throwable) {
            }
        }

        for (info in allActivities) {
            val appInfo = info.applicationInfo ?: continue
            val pkg = appInfo.packageName ?: continue
            installedPackagesSet.add(pkg)
            if (pkg == "android") continue

            val userId = getUserIdFromHandle(info.user, userManager)
            val key = ConfigStore.userKey(userId, pkg)
            if (entriesMap.containsKey(key)) continue

            val isXposed = detectIsXposedApp(appInfo, pm, info.user)
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0

            val label = try {
                info.label?.toString() ?: pm.getApplicationLabel(appInfo).toString()
            } catch (_: Throwable) {
                pkg
            }

            val pkgInfo = try {
                pm.getPackageInfo(pkg, 0)
            } catch (_: Throwable) {
                null
            }
            val firstInstallTime = pkgInfo?.firstInstallTime ?: 0L
            val updateTime = pkgInfo?.lastUpdateTime ?: firstInstallTime

            entriesMap[key] = AppEntry(
                userId = userId,
                packageName = pkg,
                label = label,
                isXposedModule = isXposed,
                isSystemApp = isSystem,
                firstInstallTime = firstInstallTime,
                lastUpdateTime = updateTime,
                hasLauncherIcon = true
            )
        }
    }

    // 2. Secondary discovery pass: Ensure 100% of installed Xposed modules are present in XHide,
    // even if they do not declare an android.intent.category.LAUNCHER activity or were
    // omitted from launcher queries after device reboot.
    // Null when the query failed. An empty list is also treated as a failure: every device has
    // installed packages, so emptiness can only mean the inventory was not actually retrieved.
    val installedAppsResult: List<ApplicationInfo>? = try {
        pm.getInstalledApplications(PackageManager.GET_META_DATA).takeIf { it.isNotEmpty() }
    } catch (_: Throwable) {
        null
    }
    val ownInventoryOk = installedAppsResult != null
    val installedApps = installedAppsResult ?: emptyList()

    for (appInfo in installedApps) {
        val pkg = appInfo.packageName ?: continue
        installedPackagesSet.add(pkg)
        if (pkg == "android") continue

        val key = ConfigStore.userKey(0, pkg)
        if (entriesMap.containsKey(key)) continue

        val isXposed = detectIsXposedApp(appInfo, pm, Process.myUserHandle())
        if (!isXposed) continue

        val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
            (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0

        val hasLauncherIntent = try {
            pm.getLaunchIntentForPackage(pkg) != null
        } catch (_: Throwable) {
            false
        }

        val label = try {
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Throwable) {
            pkg
        }

        val pkgInfo = try {
            pm.getPackageInfo(pkg, 0)
        } catch (_: Throwable) {
            null
        }
        val firstInstallTime = pkgInfo?.firstInstallTime ?: 0L
        val updateTime = pkgInfo?.lastUpdateTime ?: firstInstallTime

        // Iconless / headless modules (pure hooks) are classified as system-level components
        entriesMap[key] = AppEntry(
            userId = 0,
            packageName = pkg,
            label = label,
            isXposedModule = true,
            isSystemApp = isSystem || !hasLauncherIntent,
            firstInstallTime = firstInstallTime,
            lastUpdateTime = updateTime,
            hasLauncherIcon = hasLauncherIntent
        )
    }

    // 3. Automatically purge stale/uninstalled packages from whitelist & blacklist
    // Prevents phantom ghost entries for packages that were uninstalled from the device.
    // Without the system hook, other users' apps are invisible to us, so packages installed only
    // in those users would look uninstalled and their rules would be wiped. Skip cleanup entirely.
    val hookActive = try {
        ConfigStore.pingSystemHook(context)
    } catch (_: Throwable) {
        false
    }
    val ownUserId = getUserIdFromHandle(Process.myUserHandle(), userManager)
    fun isStale(key: String): Boolean {
        val pkg = if (key.contains(":")) key.substringAfter(":") else key
        val keyUserId = if (key.contains(":")) key.substringBefore(":").toIntOrNull() else null
        if (keyUserId != null) {
            if (keyUserId != ownUserId) {
                // The companion app has no authoritative user list: a successful hook ping does not
                // prove the profile-expansion hooks are installed, and a device with a work or clone
                // profile already returns several profiles without them. A user missing from that
                // list is therefore not proof of deletion, so rules for other users are kept.
                return false
            }
        }
        // Absence only proves removal when our own package inventory was actually retrieved.
        if (!ownInventoryOk) return false
        if (installedPackagesSet.contains(pkg)) return false
        // The inventory was taken while the list was loading; a package installed since then would
        // look missing. Re-check right before purging so the decision is always current.
        val stillInstalled = try {
            pm.getPackageInfo(pkg, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Throwable) {
            // Unknown state: keep the rule rather than risk deleting it.
            true
        }
        return !stillInstalled
    }
    // Re-reads the current rules under the shared sync lock, so a user edit made while the list
    // was loading is never overwritten by an outdated snapshot.
    if (hookActive) {
        val status = ConfigStore.purgeStaleRules(context, ::isStale)
        if (status != ConfigStore.SyncStatus.OK) {
            // Not fatal: stale keys are retried on the next refresh.
            android.util.Log.w("XHide", "Stale-rule cleanup was not applied: $status")
        }
    }

    val collator = java.text.Collator.getInstance(java.util.Locale.getDefault()).apply {
        strength = java.text.Collator.PRIMARY
    }
    return entriesMap.values.sortedWith(
        compareByDescending<AppEntry> { it.isXposedModule }
            .thenBy { it.userId }
            .thenComparing { a, b -> collator.compare(a.label, b.label) }
    )
}

private fun drawableToImageBitmap(drawable: Drawable?, sizePx: Int = 96): ImageBitmap? {
    if (drawable == null) return null
    return try {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(canvas)
        bitmap.asImageBitmap()
    } catch (_: Throwable) {
        null
    }
}

private fun detectIsXposedApp(appInfo: ApplicationInfo, pm: PackageManager, userHandle: UserHandle? = null): Boolean {
    val pkg = appInfo.packageName ?: return false
    val sourceDir = appInfo.sourceDir ?: ""
    val cacheKey = "$pkg|$sourceDir"
    xposedAppCache[cacheKey]?.let { return it }

    var metaChecked = false
    var meta = appInfo.metaData
    if (meta == null) {
        try {
            meta = pm.getApplicationInfo(pkg, PackageManager.GET_META_DATA).metaData
            metaChecked = true
        } catch (_: Throwable) {
            // Do not poison negative cache if metadata resolution failed
            return false
        }
    } else {
        metaChecked = true
    }

    val isModule = meta != null && (
        meta.containsKey("xposedmodule") ||
            meta.containsKey("xposedminversion") ||
            meta.containsKey("xposeddescription") ||
            meta.containsKey("xposedscope") ||
            meta.containsKey("libxposed") ||
            meta.containsKey("libxposed_minversion") ||
            meta.containsKey("libxposed_api") ||
            meta.containsKey("libxposed_scope")
    )

    if (isModule) {
        if (xposedAppCache.size > 2048) xposedAppCache.clear()
        xposedAppCache[cacheKey] = true
        return true
    }

    // Fast check for modern LibXposed module.prop or classic xposed_init
    if (sourceDir.startsWith("/data/")) {
        try {
            java.util.zip.ZipFile(File(sourceDir)).use { zip ->
                if (zip.getEntry("META-INF/xposed/module.prop") != null ||
                    zip.getEntry("assets/xposed_init") != null
                ) {
                    if (xposedAppCache.size > 2048) xposedAppCache.clear()
                    xposedAppCache[cacheKey] = true
                    return true
                }
            }
        } catch (_: Throwable) {
        }
    }

    if (metaChecked) {
        if (xposedAppCache.size > 2048) xposedAppCache.clear()
        xposedAppCache[cacheKey] = false
    }
    return false
}

@Composable
private fun NavTabIcon(selected: Boolean, icon: @Composable (Float) -> Unit) {
    val progress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>(),
        label = "navIconSelected"
    )
    Box(
        modifier = Modifier.graphicsLayer {
            val s = 1f + 0.12f * progress
            scaleX = s
            scaleY = s
        }
    ) {
        icon(progress.coerceIn(0f, 1f))
    }
}

@Composable
private fun HomeVectorIcon(fill: Float = 0f) {
    val color = LocalContentColor.current
    Canvas(modifier = Modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.14f)
            lineTo(w * 0.14f, h * 0.46f)
            lineTo(w * 0.24f, h * 0.46f)
            lineTo(w * 0.24f, h * 0.84f)
            lineTo(w * 0.43f, h * 0.84f)
            lineTo(w * 0.43f, h * 0.58f)
            lineTo(w * 0.57f, h * 0.58f)
            lineTo(w * 0.57f, h * 0.84f)
            lineTo(w * 0.76f, h * 0.84f)
            lineTo(w * 0.76f, h * 0.46f)
            lineTo(w * 0.86f, h * 0.46f)
            close()
        }
        if (fill > 0f) {
            drawPath(path = path, color = color.copy(alpha = 0.35f * fill))
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 1.9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
private fun AppsVectorIcon(fill: Float = 0f) {
    val color = LocalContentColor.current
    Canvas(modifier = Modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val boxSize = w * 0.30f
        val r = CornerRadius(2.dp.toPx(), 2.dp.toPx())
        val stroke = Stroke(width = 1.8.dp.toPx())
        val corners = listOf(
            Offset(w * 0.15f, h * 0.15f),
            Offset(w * 0.55f, h * 0.15f),
            Offset(w * 0.15f, h * 0.55f),
            Offset(w * 0.55f, h * 0.55f)
        )
        for (corner in corners) {
            if (fill > 0f) {
                drawRoundRect(color = color.copy(alpha = 0.35f * fill), topLeft = corner, size = Size(boxSize, boxSize), cornerRadius = r)
            }
            drawRoundRect(color = color, topLeft = corner, size = Size(boxSize, boxSize), cornerRadius = r, style = stroke)
        }
    }
}

@Composable
private fun SettingsVectorIcon(fill: Float = 0f) {
    val color = LocalContentColor.current
    Canvas(modifier = Modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.6.dp.toPx()
        val r = 2.4.dp.toPx()
        // Fixed geometry: only the knob fill and a small slide change with selection, so the
        // icon never grows or overlaps itself.
        val gap = r + 1.1.dp.toPx()
        fun slider(y: Float, x: Float) {
            drawLine(color, Offset(w * 0.12f, y), Offset(x - gap, y), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(x + gap, y), Offset(w * 0.88f, y), strokeWidth = stroke, cap = StrokeCap.Round)
            drawCircle(color, radius = r - stroke / 2f, center = Offset(x, y), style = Stroke(width = stroke))
            if (fill > 0f) drawCircle(color.copy(alpha = fill), radius = r, center = Offset(x, y))
        }
        slider(h * 0.22f, w * (0.62f - 0.06f * fill))
        slider(h * 0.50f, w * (0.36f + 0.06f * fill))
        slider(h * 0.78f, w * (0.56f - 0.04f * fill))
    }
}

@Composable
private fun SearchVectorIcon() {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = Modifier.size(15.dp)) {
        val w = size.width
        val h = size.height
        drawCircle(
            color = color,
            radius = w * 0.32f,
            center = Offset(w * 0.42f, h * 0.42f),
            style = Stroke(width = 1.6.dp.toPx())
        )
        drawLine(
            color = color,
            start = Offset(w * 0.65f, h * 0.65f),
            end = Offset(w * 0.88f, h * 0.88f),
            strokeWidth = 1.8.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun GitHubVectorIcon() {
    Icon(
        painter = painterResource(id = org.lsposed.hidexposed.R.drawable.ic_github),
        contentDescription = "GitHub",
        modifier = Modifier.size(24.dp),
        tint = MaterialTheme.colorScheme.onSurface
    )
}
