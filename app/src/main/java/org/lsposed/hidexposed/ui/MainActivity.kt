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
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.rotate
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
import androidx.compose.ui.draw.scale
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
import org.lsposed.hidexposed.BuildConfig
import org.lsposed.hidexposed.core.AppDiagnostics
import org.lsposed.hidexposed.core.ModuleCache
import org.lsposed.hidexposed.data.ConfigStore
import org.lsposed.hidexposed.ui.theme.HideXposedTheme
import android.util.LruCache
import java.io.File
import java.util.concurrent.ConcurrentHashMap

// Bounded LRU cache (max 256 entries) to prevent unlimited memory growth
private val appIconLruCache = object : LruCache<String, ImageBitmap>(256) {}
private val xposedAppCache = ConcurrentHashMap<String, Boolean>()

@Composable
private fun rememberAppIcon(packageName: String, userId: Int, lastUpdateTime: Long = 0L): ImageBitmap? {
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
        val rawDrawable = activities?.firstOrNull()?.getBadgedIcon(0)
            ?: activities?.firstOrNull()?.getIcon(0)
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        enableEdgeToEdge()
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
    var pendingImport by remember { mutableStateOf<PendingImportConfig?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var debouncedQuery by rememberSaveable { mutableStateOf("") }
    var filterMode by remember { mutableStateOf<FilterMode>(FilterMode.AllApps) }

    val homeScrollState = rememberScrollState()
    val appsListState = rememberLazyListState()
    val settingsScrollState = rememberScrollState()

    BackHandler(enabled = currentTab != MainTab.HOME) {
        currentTab = MainTab.HOME
    }

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
            entry.userId == 0 -> autoHide
            else -> autoHide && autoHideClone
        }
    }

    val syncMutex = remember { Mutex() }

    fun syncWithSystem(
        newAutoHide: Boolean = autoHideModules,
        newAutoHideClone: Boolean = autoHideCloneModules,
        newWl: Set<String> = whitelist,
        newBl: Set<String> = blacklist,
        changedPkgs: List<String>,
        onRevert: (() -> Unit)? = null
    ) {
        scope.launch {
            val success = withContext(Dispatchers.IO) {
                syncMutex.withLock {
                    ConfigStore.saveUiPrefsAndSync(
                        context = context,
                        autoHide = newAutoHide,
                        autoHideClone = newAutoHideClone,
                        whitelist = newWl,
                        blacklist = newBl,
                        changedPackages = changedPkgs
                    )
                }
            }
            if (!success) {
                onRevert?.invoke()
                Toast.makeText(context, strings.syncFailedToast, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun refreshState(showToastOnDone: Boolean = false) {
        if (isFetchingData) return
        isFetchingData = true
        scope.launch {
            try {
                if (apps.isEmpty()) isLoading = true
                else isRefreshing = true

                val startTime = System.currentTimeMillis()

                val (active, loadedApps, launcher) = withContext(Dispatchers.IO) {
                    val activeDef = async { ConfigStore.pingSystemHook(context) }
                    val loadedAppsDef = async { loadInstalledAppsAcrossProfiles(context) }
                    val launcherDef = async { detectDefaultLauncher(context) }
                    Triple(activeDef.await(), loadedAppsDef.await(), launcherDef.await())
                }
                isHookActive = active
                apps = loadedApps
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
                    Toast.makeText(context, strings.launcherRefreshedToast, Toast.LENGTH_SHORT).show()
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
        val prevWl = whitelist
        val prevBl = blacklist

        val newWl = whitelist.toMutableSet()
        val newBl = blacklist.toMutableSet()
        val uKey = entry.uniqueKey

        newWl.remove(entry.packageName)
        newBl.remove(entry.packageName)

        val defaultHidden = entry.isXposedModule &&
            (if (entry.userId == 0) autoHideModules else (autoHideModules && autoHideCloneModules))

        if (hideInLauncher) {
            newWl.remove(uKey)
            if (!defaultHidden) {
                newBl.add(uKey)
            }
        } else {
            newBl.remove(uKey)
            if (defaultHidden) {
                newWl.add(uKey)
            }
        }

        whitelist = newWl
        blacklist = newBl
        syncWithSystem(
            newWl = newWl,
            newBl = newBl,
            changedPkgs = listOf(entry.packageName),
            onRevert = {
                whitelist = prevWl
                blacklist = prevBl
            }
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
                        Toast.makeText(context, strings.exportSuccessToast, Toast.LENGTH_SHORT).show()
                    }
                } catch (_: Throwable) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, strings.exportFailedToast, Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(context, strings.importFailedToast, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
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
                        val defaultWl = setOf(ConfigStore.SELF_USER0_KEY)
                        val defaultBl = emptySet<String>()
                        val prevAutoHide = autoHideModules
                        val prevAutoHideClone = autoHideCloneModules
                        val prevWl = whitelist
                        val prevBl = blacklist

                        autoHideModules = true
                        autoHideCloneModules = true
                        whitelist = defaultWl
                        blacklist = defaultBl
                        val allPkgs = apps.map { it.packageName }.distinct()
                        syncWithSystem(
                            newAutoHide = true,
                            newAutoHideClone = true,
                            newWl = defaultWl,
                            newBl = defaultBl,
                            changedPkgs = allPkgs,
                            onRevert = {
                                autoHideModules = prevAutoHide
                                autoHideCloneModules = prevAutoHideClone
                                whitelist = prevWl
                                blacklist = prevBl
                            }
                        )
                        Toast.makeText(context, strings.resetDoneToast, Toast.LENGTH_SHORT).show()
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
                        val prevAutoHide = autoHideModules
                        val prevAutoHideClone = autoHideCloneModules
                        val prevWl = whitelist
                        val prevBl = blacklist

                        autoHideModules = cfg.autoHide
                        autoHideCloneModules = cfg.autoHideClone
                        whitelist = cfg.whitelist
                        blacklist = cfg.blacklist
                        pendingImport = null

                        val allPkgs = apps.map { it.packageName }.distinct()
                        syncWithSystem(
                            newAutoHide = cfg.autoHide,
                            newAutoHideClone = cfg.autoHideClone,
                            newWl = cfg.whitelist,
                            newBl = cfg.blacklist,
                            changedPkgs = allPkgs,
                            onRevert = {
                                autoHideModules = prevAutoHide
                                autoHideCloneModules = prevAutoHideClone
                                whitelist = prevWl
                                blacklist = prevBl
                            }
                        )
                        Toast.makeText(context, strings.importedToast, Toast.LENGTH_SHORT).show()
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

    Scaffold(
        contentWindowInsets = WindowInsets.statusBars,
        bottomBar = {
            NavigationBar(
                modifier = Modifier.heightIn(min = 64.dp),
                tonalElevation = 3.dp
            ) {
                NavigationBarItem(
                    selected = currentTab == MainTab.HOME,
                    onClick = { currentTab = MainTab.HOME },
                    icon = { HomeVectorIcon() },
                    label = { Text(strings.navHome, fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = currentTab == MainTab.APPS,
                    onClick = { currentTab = MainTab.APPS },
                    icon = { AppsVectorIcon() },
                    label = { Text(strings.navApps, fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = currentTab == MainTab.SETTINGS,
                    onClick = { currentTab = MainTab.SETTINGS },
                    icon = { SettingsVectorIcon() },
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
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
                }
            } else {
                val isHiddenPredicate = remember(whitelist, blacklist, autoHideModules, autoHideCloneModules) {
                    { entry: AppEntry -> isEntryHidden(entry, whitelist, blacklist, autoHideModules, autoHideCloneModules) }
                }

                when (currentTab) {
                    MainTab.HOME -> HomeTabScreen(
                        strings = strings,
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
                            updateAppHidden(entry, hide)
                        }
                    )

                    MainTab.APPS -> PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = { refreshState(showToastOnDone = false) },
                        modifier = Modifier.fillMaxSize()
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
                            isEntryHidden = isHiddenPredicate,
                            listState = appsListState,
                            isRefreshing = isRefreshing,
                            onRefresh = { refreshState(showToastOnDone = true) },
                            onToggleAppHidden = { entry, hide ->
                                updateAppHidden(entry, hide)
                            }
                        )
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
                        onToggleAutoHide = { checked ->
                            val prevAutoHide = autoHideModules
                            autoHideModules = checked
                            val modPkgs = apps.filter { it.isXposedModule }.map { it.packageName }.distinct()
                            syncWithSystem(
                                newAutoHide = checked,
                                changedPkgs = modPkgs,
                                onRevert = { autoHideModules = prevAutoHide }
                            )
                        },
                        onToggleAutoHideClone = { checked ->
                            val prevAutoHideClone = autoHideCloneModules
                            autoHideCloneModules = checked
                            val modPkgs = apps.filter { it.isXposedModule && it.userId != 0 }.map { it.packageName }.distinct()
                            syncWithSystem(
                                newAutoHideClone = checked,
                                changedPkgs = modPkgs,
                                onRevert = { autoHideCloneModules = prevAutoHideClone }
                            )
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
    onOpenAppsFilter: (FilterMode) -> Unit,
    onOpenSettings: () -> Unit,
    onRefreshLauncher: () -> Unit,
    onToggleAppHidden: (AppEntry, Boolean) -> Unit
) {
    val hiddenApps = remember(apps, showSystemApps, autoHideModules, autoHideCloneModules, whitelist, blacklist) {
        apps.filter { isEntryHidden(it) && (showSystemApps || it.hasLauncherIcon) }
    }
    val xposedCount = remember(apps, showSystemApps) {
        apps.count { it.isXposedModule && (showSystemApps || it.hasLauncherIcon) }
    }
    val context = LocalContext.current

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
        val cardBg = when (isHookActive) {
            true -> MaterialTheme.colorScheme.primaryContainer
            false -> MaterialTheme.colorScheme.errorContainer
            null -> MaterialTheme.colorScheme.surfaceVariant
        }
        val cardContentColor = when (isHookActive) {
            true -> MaterialTheme.colorScheme.onPrimaryContainer
            false -> MaterialTheme.colorScheme.onErrorContainer
            null -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        val dotColor = when (isHookActive) {
            true -> MaterialTheme.colorScheme.primary
            false -> MaterialTheme.colorScheme.error
            null -> MaterialTheme.colorScheme.outline
        }
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
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = cardBg,
                contentColor = cardContentColor
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(dotColor)
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
                    text = statusDesc,
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp,
                    color = cardContentColor.copy(alpha = 0.9f)
                )
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
            }
        }

        // 2. 2x2 Compact Clickable Dashboard Stats
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DashboardStatCard(
                modifier = Modifier.weight(1f),
                title = strings.statHiddenIcons,
                value = hiddenApps.size.toString(),
                subtitle = strings.viewAll,
                onClick = { onOpenAppsFilter(FilterMode.HiddenOnly) }
            )
            DashboardStatCard(
                modifier = Modifier.weight(1f),
                title = strings.statXposedModules,
                value = xposedCount.toString(),
                subtitle = strings.statXposedSubtitle,
                onClick = { onOpenAppsFilter(FilterMode.XposedOnly) }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val profileSummary = availableUserIds.joinToString(" · ") { formatUserProfileName(it, strings) }
            DashboardStatCard(
                modifier = Modifier.weight(1f),
                title = strings.statActiveProfiles,
                value = availableUserIds.size.toString(),
                subtitle = profileSummary,
                onClick = {
                    val targetUser = availableUserIds.lastOrNull() ?: 0
                    onOpenAppsFilter(FilterMode.ByUser(targetUser))
                }
            )
            DashboardStatCard(
                modifier = Modifier.weight(1f),
                title = strings.statAutoHide,
                value = if (autoHideModules) strings.enabled else strings.disabled,
                subtitle = strings.navSettings,
                onClick = onOpenSettings
            )
        }

        // 3. Active Default Launcher & Quick Refresh Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            tonalElevation = 2.dp
        ) {
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
        }

        // 4. Currently Hidden Icons Quick Preview Strip
        Surface(
            modifier = Modifier.fillMaxWidth(),
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
                    Text(
                        text = strings.noHiddenAppsYet,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 2.dp)
                    ) {
                        items(hiddenApps, key = { it.uniqueKey }) { app ->
                            Column(
                                modifier = Modifier
                                    .width(62.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onToggleAppHidden(app, false) }
                                    .padding(vertical = 4.dp, horizontal = 2.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box {
                                    val icon = rememberAppIcon(app.packageName, app.userId, app.lastUpdateTime)
                                    if (icon != null) {
                                        Image(
                                            bitmap = icon,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(RoundedCornerShape(9.dp))
                                        )
                                    } else {
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(9.dp),
                                            modifier = Modifier.size(38.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = app.label.take(1).uppercase(),
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
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
private fun DashboardStatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp
    ) {
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
                text = value,
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
    isEntryHidden: (AppEntry) -> Boolean,
    listState: LazyListState,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit,
    onToggleAppHidden: (AppEntry, Boolean) -> Unit
) {
    val filteredApps = remember(
        apps,
        debouncedQuery,
        filterMode,
        showSystemApps,
        autoHideModules,
        autoHideCloneModules,
        whitelist,
        blacklist
    ) {
        val collator = java.text.Collator.getInstance().apply {
            strength = java.text.Collator.PRIMARY
        }
        apps.filter { app ->
            val allowSystem = showSystemApps || (!app.isSystemApp && app.hasLauncherIcon) || (app.userId != 0 && app.hasLauncherIcon)
            val matchesMode = when (val mode = filterMode) {
                is FilterMode.AllApps -> allowSystem
                is FilterMode.XposedOnly -> app.isXposedModule && allowSystem
                is FilterMode.HiddenOnly -> isEntryHidden(app)
                is FilterMode.ByUser -> app.userId == mode.userId && allowSystem
            }
            val matchesQuery = debouncedQuery.isBlank() ||
                app.label.contains(debouncedQuery, ignoreCase = true) ||
                app.packageName.contains(debouncedQuery, ignoreCase = true)
            matchesMode && matchesQuery
        }.sortedWith(
            compareByDescending<AppEntry> { isEntryHidden(it) }
                .thenByDescending { it.isXposedModule }
                .thenBy { it.userId }
                .thenComparing { a, b -> collator.compare(a.label, b.label) }
        )
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
            .padding(horizontal = 12.dp)
    ) {
        Text(
            text = strings.navApps,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 2.dp, top = 4.dp, bottom = 6.dp)
        )

        // Compact Search Bar (38dp height) & Refresh Button
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
                    if (searchQuery.isNotEmpty()) {
                        val focusManager = LocalFocusManager.current
                        IconButton(
                            onClick = {
                                onSearchChange("")
                                focusManager.clearFocus()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Text(
                                text = "✕",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                modifier = Modifier.size(38.dp),
                shape = RoundedCornerShape(10.dp),
                tonalElevation = 2.dp
            ) {
                IconButton(
                    onClick = onRefresh,
                    enabled = !isRefreshing,
                    modifier = Modifier.fillMaxSize()
                ) {
                    RefreshVectorIcon(isRefreshing = isRefreshing)
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Filter Chips Row (order: Tümü -> Xposed -> Gizli -> User 0 -> User 10...)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CompactFilterChip(
                selected = filterMode is FilterMode.AllApps,
                label = "${strings.filterAll} ($allCount)",
                onClick = { onFilterModeChange(FilterMode.AllApps) }
            )
            CompactFilterChip(
                selected = filterMode is FilterMode.XposedOnly,
                label = "${strings.filterXposed} ($xposedCount)",
                onClick = { onFilterModeChange(FilterMode.XposedOnly) }
            )
            CompactFilterChip(
                selected = filterMode is FilterMode.HiddenOnly,
                label = "${strings.filterHidden} ($hiddenCount)",
                onClick = { onFilterModeChange(FilterMode.HiddenOnly) }
            )
            for (uid in availableUserIds) {
                val countForUser = userCounts[uid] ?: 0
                CompactFilterChip(
                    selected = (filterMode as? FilterMode.ByUser)?.userId == uid,
                    label = "${formatUserProfileName(uid, strings)} ($countForUser)",
                    onClick = { onFilterModeChange(FilterMode.ByUser(uid)) }
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

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
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filteredApps, key = { it.uniqueKey }) { entry ->
                    AppItemRow(
                        entry = entry,
                        isHidden = isEntryHidden(entry),
                        strings = strings,
                        modifier = Modifier.animateItem(),
                        onToggle = { hide -> onToggleAppHidden(entry, hide) }
                    )
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
    modifier: Modifier = Modifier,
    onToggle: (Boolean) -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .toggleable(
                value = isHidden,
                role = Role.Switch,
                onValueChange = onToggle
            ),
        shape = RoundedCornerShape(10.dp),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon = rememberAppIcon(entry.packageName, entry.userId, entry.lastUpdateTime)
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = entry.label.take(1).uppercase(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
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
                Text(
                    text = entry.packageName,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Switch(
                checked = isHidden,
                onCheckedChange = null,
                modifier = Modifier.scale(0.95f)
            )
        }
    }
}

@Composable
private fun CompactFilterChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 11.5.sp) },
        modifier = Modifier.heightIn(min = 32.dp)
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
        SettingsSectionCard(title = strings.sectionHidingRules) {
            CompactSwitchRow(
                title = strings.autoHideTitle,
                subtitle = strings.autoHideDesc,
                checked = autoHideModules,
                onCheckedChange = onToggleAutoHide
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp)
            CompactSwitchRow(
                title = strings.autoHideCloneTitle,
                subtitle = strings.autoHideCloneDesc,
                checked = autoHideCloneModules,
                onCheckedChange = onToggleAutoHideClone
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp)
            CompactSwitchRow(
                title = strings.showSystemAppsTitle,
                subtitle = strings.showSystemAppsDesc,
                checked = showSystemApps,
                onCheckedChange = onToggleShowSystemApps
            )
        }

        // Section 2: Appearance & Language
        SettingsSectionCard(title = strings.sectionAppearance) {
            Text(
                text = strings.languageTitle,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for ((code, displayName) in Localization.languageOptions) {
                    CompactFilterChip(
                        selected = langCode == code,
                        label = if (code == "SYSTEM") strings.languageSystem else displayName,
                        onClick = {
                            onUpdateAppearance(code, themeMode, amoledBlack, dynamicColor, uiScale)
                        }
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp)

            Text(
                text = strings.themeTitle,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val themes = listOf(
                    "SYSTEM" to strings.themeSystem,
                    "DARK" to strings.themeDark,
                    "LIGHT" to strings.themeLight
                )
                for ((code, label) in themes) {
                    CompactFilterChip(
                        selected = themeMode == code,
                        label = label,
                        onClick = {
                            onUpdateAppearance(langCode, code, amoledBlack, dynamicColor, uiScale)
                        }
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), thickness = 0.5.dp)
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
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp)
            CompactSwitchRow(
                title = strings.dynamicColorTitle,
                subtitle = strings.dynamicColorDesc,
                checked = dynamicColor,
                onCheckedChange = { checked ->
                    onUpdateAppearance(langCode, themeMode, amoledBlack, checked, uiScale)
                }
            )

            // Display Size / DPI Slider
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), thickness = 0.5.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
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
                    val scaleBadgeText = if (pct == 100) "100% (${strings.defaultLabel})" else "$pct%"
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.clickable {
                            if (localUiScale != 1.0f) {
                                localUiScale = 1.0f
                                onUpdateAppearance(langCode, themeMode, amoledBlack, dynamicColor, 1.0f)
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

        // Section 3: Backup & Reset (SAF File Export / Import)
        SettingsSectionCard(title = strings.sectionBackupReset) {
            CompactActionRow(
                title = strings.exportConfigTitle,
                subtitle = strings.exportConfigDesc,
                onClick = onRequestExportFile
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp)
            CompactActionRow(
                title = strings.importConfigTitle,
                subtitle = strings.importConfigDesc,
                onClick = onRequestImportFile
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp)
            CompactActionRow(
                title = strings.resetAllTitle,
                subtitle = strings.resetAllDesc,
                isDestructive = true,
                onClick = onResetAll
            )
        }

        // Section 4: About & System
        SettingsSectionCard(title = strings.sectionAbout) {
            CompactInfoRow(
                title = strings.hookTargetTitle,
                value = "LauncherAppsService\$LauncherAppsImpl"
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp)
            CompactInfoRow(
                title = strings.versionTitle,
                value = "v${BuildConfig.VERSION_NAME} (Android 11–17 Multi-User)"
            )
        }

        // Section 5: GitHub & Community
        SettingsSectionCard(title = strings.githubSectionTitle) {
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
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp)
            CompactActionRow(
                title = strings.reportIssueTitle,
                subtitle = strings.reportIssueDesc,
                onClick = { showReportConfirmDialog = true }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                content()
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.42f)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange
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
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = null,
            modifier = Modifier.scale(0.95f)
        )
    }
}

@Composable
private fun CompactActionRow(
    title: String,
    subtitle: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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

            val updateTime = try {
                pm.getPackageInfo(pkg, 0).lastUpdateTime
            } catch (_: Throwable) {
                0L
            }

            entriesMap[key] = AppEntry(
                userId = userId,
                packageName = pkg,
                label = label,
                isXposedModule = isXposed,
                isSystemApp = isSystem,
                lastUpdateTime = updateTime,
                hasLauncherIcon = true
            )
        }
    }

    // 2. Secondary discovery pass: Ensure 100% of installed Xposed modules are present in XHide,
    // even if they do not declare an android.intent.category.LAUNCHER activity or were
    // omitted from launcher queries after device reboot.
    val installedApps = try {
        pm.getInstalledApplications(PackageManager.GET_META_DATA)
    } catch (_: Throwable) {
        emptyList()
    }

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

        val updateTime = try {
            pm.getPackageInfo(pkg, 0).lastUpdateTime
        } catch (_: Throwable) {
            0L
        }

        // Iconless / headless modules (pure hooks) are classified as system-level components
        entriesMap[key] = AppEntry(
            userId = 0,
            packageName = pkg,
            label = label,
            isXposedModule = true,
            isSystemApp = isSystem || !hasLauncherIntent,
            lastUpdateTime = updateTime,
            hasLauncherIcon = hasLauncherIntent
        )
    }

    // 3. Automatically purge stale/uninstalled packages from whitelist & blacklist
    // Prevents phantom ghost entries for packages that were uninstalled from the device.
    val currentWl = ConfigStore.getWhitelist()
    val currentBl = ConfigStore.getBlacklist()
    val staleWl = currentWl.filter { key ->
        val pkg = if (key.contains(":")) key.substringAfter(":") else key
        !installedPackagesSet.contains(pkg)
    }
    val staleBl = currentBl.filter { key ->
        val pkg = if (key.contains(":")) key.substringAfter(":") else key
        !installedPackagesSet.contains(pkg)
    }

    if (staleWl.isNotEmpty() || staleBl.isNotEmpty()) {
        val cleanedWl = currentWl - staleWl.toSet()
        val cleanedBl = currentBl - staleBl.toSet()
        ConfigStore.saveUiPrefsAndSync(
            context = context,
            autoHide = ConfigStore.autoHideXposedModules,
            autoHideClone = ConfigStore.autoHideCloneModules,
            whitelist = cleanedWl,
            blacklist = cleanedBl,
            changedPackages = (staleWl + staleBl).map {
                if (it.contains(":")) it.substringAfter(":") else it
            }.distinct()
        )
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
private fun HomeVectorIcon() {
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
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 1.9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
private fun AppsVectorIcon() {
    val color = LocalContentColor.current
    Canvas(modifier = Modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val boxSize = w * 0.30f
        val r = CornerRadius(2.dp.toPx(), 2.dp.toPx())
        val stroke = Stroke(width = 1.8.dp.toPx())
        drawRoundRect(color = color, topLeft = Offset(w * 0.15f, h * 0.15f), size = Size(boxSize, boxSize), cornerRadius = r, style = stroke)
        drawRoundRect(color = color, topLeft = Offset(w * 0.55f, h * 0.15f), size = Size(boxSize, boxSize), cornerRadius = r, style = stroke)
        drawRoundRect(color = color, topLeft = Offset(w * 0.15f, h * 0.55f), size = Size(boxSize, boxSize), cornerRadius = r, style = stroke)
        drawRoundRect(color = color, topLeft = Offset(w * 0.55f, h * 0.55f), size = Size(boxSize, boxSize), cornerRadius = r, style = stroke)
    }
}

@Composable
private fun SettingsVectorIcon() {
    val color = LocalContentColor.current
    Canvas(modifier = Modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.15f, h * 0.25f), Offset(w * 0.85f, h * 0.25f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawCircle(color, radius = 2.4.dp.toPx(), center = Offset(w * 0.35f, h * 0.25f))
        drawLine(color, Offset(w * 0.15f, h * 0.50f), Offset(w * 0.85f, h * 0.50f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawCircle(color, radius = 2.4.dp.toPx(), center = Offset(w * 0.65f, h * 0.50f))
        drawLine(color, Offset(w * 0.15f, h * 0.75f), Offset(w * 0.85f, h * 0.75f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawCircle(color, radius = 2.4.dp.toPx(), center = Offset(w * 0.42f, h * 0.75f))
    }
}

@Composable
private fun RefreshVectorIcon(
    modifier: Modifier = Modifier,
    isRefreshing: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "refresh_spin")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "refresh_angle"
    )
    val rotationAngle = if (isRefreshing) spinAngle else 0f

    Icon(
        painter = painterResource(id = org.lsposed.hidexposed.R.drawable.ic_refresh),
        contentDescription = "Refresh",
        modifier = modifier
            .size(20.dp)
            .rotate(rotationAngle),
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
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
