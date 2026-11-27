package dev.hfw.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.coroutineScope
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import dev.hfw.ui.screens.AppsToolbar
import dev.hfw.ui.components.SystemAppsWarning
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.activity.compose.BackHandler
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import dev.hfw.data.BulkAction
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import dev.hfw.ui.components.LocalHeaderHeight
import dev.hfw.ui.components.LocalListTopPadding
import dev.hfw.ui.components.LocalListBottomPadding
import dev.hfw.ui.components.LocalReduceMotion
import dev.hfw.ui.components.MainTab
import dev.hfw.ui.components.SelectionBar
import dev.hfw.ui.components.FloatingNavBar
import dev.hfw.ui.components.ExpandingNavBar
import dev.hfw.ui.components.AppHeader
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.Icons
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.animation.core.snap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.hfw.R
import dev.hfw.HfwViewModel
import dev.hfw.ui.components.ModuleRequiredSheet
import dev.hfw.ui.components.AppIcon
import dev.hfw.ui.components.IconFlight
import dev.hfw.ui.components.LocalIconFlight
import dev.hfw.ui.screens.AppsScreen
import dev.hfw.ui.screens.DetailScreen
import dev.hfw.ui.screens.HomeScreen
import dev.hfw.ui.screens.SettingsScreen
import kotlinx.coroutines.launch

/**
 * Root of the template: three tabs behind a floating toolbar plus a detail screen that is
 * pushed on top (the app icon flies between the list row and the detail page).
 * Navigation is plain state on purpose, no navigation library needed for a template.
 */
@Composable
fun HfwApp(vm: HfwViewModel) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val notify: (String) -> Unit = { message -> scope.launch { snackbar.showSnackbar(message) } }

    var tab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    var detailKey by rememberSaveable { mutableStateOf<String?>(null) }
    var showModuleSheet by remember { mutableStateOf(false) }

    val homeState = rememberLazyListState()
    val appsState = rememberLazyListState()
    val settingsState = rememberLazyListState()

    // Predictive back for the detail screen: it shrinks and slides with the finger; letting go commits, backing out springs it back.
    val backAnim = remember { Animatable(0f) }
    var backDir by remember { mutableFloatStateOf(1f) }
    // The icon flight: 0 = icon sits in the detail page, 1 = icon sits in its list row.
    val iconFlight = remember { IconFlight() }
    val flight = remember { Animatable(0f) }
    val flightEnabled = !vm.reduceMotion
    PredictiveBackHandler(enabled = detailKey != null) { progress ->
        try {
            progress.collect { event ->
                backDir = if (event.swipeEdge == BackEventCompat.EDGE_RIGHT) -1f else 1f
                if (flightEnabled && iconFlight.flying == null &&
                    iconFlight.rowRect(detailKey ?: "") != null && iconFlight.detailKey == detailKey
                ) iconFlight.flying = detailKey
                backAnim.snapTo(event.progress)
                if (iconFlight.flying != null) flight.snapTo(event.progress)
            }
            detailKey = null
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                coroutineScope {
                    launch { backAnim.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
                    launch { flight.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
                }
                iconFlight.flying = null
            }
        }
    }
    var shownKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(detailKey) {
        val key = detailKey
        if (key != null) {
            shownKey = key
            backAnim.snapTo(0f) // a gesture from a previous visit must never leak into this one
            // opening: the icon flies from its row to the detail page
            val canFly = flightEnabled && iconFlight.rowRect(key) != null
            if (canFly) {
                iconFlight.flying = key
                flight.snapTo(1f)
                var frames = 0
                while ((iconFlight.detailRect == null || iconFlight.detailKey != key) && frames < 3) { withFrameNanos { }; frames++ }
                if (iconFlight.detailRect != null && iconFlight.detailKey == key) flight.animateTo(0f, tween(320, easing = FastOutSlowInEasing))
                iconFlight.flying = null
            }
        } else if (shownKey != null) {
            // closing: finish the flight to the row (a gesture may already have moved it part of the way)
            val closing = shownKey
            if (iconFlight.flying == null && flightEnabled && closing != null &&
                iconFlight.rowRect(closing) != null && iconFlight.detailRect != null && iconFlight.detailKey == closing
            ) {
                flight.snapTo(0f)
                iconFlight.flying = closing
            }
            if (iconFlight.flying != null) {
                flight.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
                iconFlight.flying = null
            }
            delay(300)
            backAnim.snapTo(0f)
            flight.snapTo(0f)
        }
    }
    val context = LocalContext.current
    val savedMessage = stringResource(R.string.module_saved)
    val saveFailed = stringResource(R.string.module_save_failed)
    val moduleOpenFailed = stringResource(R.string.module_open_failed)
    LaunchedEffect(vm) {
        vm.messages.collect { res -> notify(context.getString(res)) }
    }
    val saveModule = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    context.assets.open("hfw_sepolicy.zip").use { input ->
                        context.contentResolver.openOutputStream(uri)?.use { out -> input.copyTo(out) } != null
                    }
                } catch (_: Throwable) {
                    false
                }
            }
            notify(if (ok) savedMessage else saveFailed)
            if (ok) {
                // open the root manager if one is installed, so the user can pick "install from storage"
                val managers = listOf("me.weishu.kernelsu", "com.rifsxd.ksunext", "com.topjohnwu.magisk", "me.bmax.apatch")
                managers.firstNotNullOfOrNull { context.packageManager.getLaunchIntentForPackage(it) }?.let {
                    try { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Throwable) {}
                }
            }
        }
    }

    CompositionLocalProvider(LocalIconFlight provides iconFlight) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // The tabs stay composed under the detail page, so the predictive back gesture shows them exactly as they were.
        MainShell(
            vm = vm,
            tab = tab,
            onTab = { tab = it },
            homeState = homeState,
            appsState = appsState,
            settingsState = settingsState,
            onOpenApp = { detailKey = it },
            onShowModule = { showModuleSheet = true },
            interactive = detailKey == null,
        )
        AnimatedVisibility(
            visible = detailKey != null,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(180)),
        ) {
            val key = detailKey ?: shownKey
            if (key != null) {
                DetailScreen(
                    vm = vm,
                    itemKey = key,
                    onBack = { detailKey = null },
                    onShowModule = { showModuleSheet = true },
                    modifier = Modifier
                        .fillMaxSize()
                        // swallow touches so nothing reaches the tabs underneath
                        .pointerInput(Unit) { detectTapGestures { } }
                        .graphicsLayer {
                            val p = backAnim.value
                            val scale = 1f - 0.10f * p
                            scaleX = scale
                            scaleY = scale
                            translationX = backDir * p * 28.dp.toPx()
                            shape = RoundedCornerShape((28f * p).dp)
                            clip = p > 0f
                        },
                )
            }
        }

        // The travelling icon, drawn above everything while a flight runs.
        val flyKey = iconFlight.flying
        val flyApp = flyKey?.let { vm.find(it) }
        val from = flyKey?.let { iconFlight.rowRect(it) }
        val to = iconFlight.detailRect
        if (flyApp != null && from != null && to != null) {
            val density = LocalDensity.current
            AppIcon(
                flyApp,
                with(density) { to.width.toDp() },
                dimmed = flyApp.hidden,
                modifier = Modifier.graphicsLayer {
                    val t = flight.value
                    val w = to.width + (from.width - to.width) * t
                    val sc = w / to.width
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                    scaleX = sc
                    scaleY = sc
                    translationX = to.left + (from.left - to.left) * t
                    translationY = to.top + (from.top - to.top) * t
                },
            )
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 96.dp),
        )
    }
    }

    if (showModuleSheet) {
        ModuleRequiredSheet(
            onOpen = {
                showModuleSheet = false
                if (!openModuleWithManager(context)) notify(moduleOpenFailed)
            },
            onInstall = {
                showModuleSheet = false
                saveModule.launch("${dev.hfw.Identity.APP_NAME}-SELinux-module.zip")
            },
            onDismiss = { showModuleSheet = false },
        )
    }
}

/**
 * The three tabs live in a pager (like XHide): tapping a tab animates across the pages in between, and the predictive
 * back gesture drives the very same scroll position, so swiping back slides toward Home as far as the gesture goes.
 */
@Composable
private fun MainShell(
    vm: HfwViewModel,
    tab: MainTab,
    onTab: (MainTab) -> Unit,
    homeState: androidx.compose.foundation.lazy.LazyListState,
    appsState: androidx.compose.foundation.lazy.LazyListState,
    settingsState: androidx.compose.foundation.lazy.LazyListState,
    onOpenApp: (String) -> Unit,
    onShowModule: () -> Unit,
    interactive: Boolean = true,
) {
    val density = LocalDensity.current
    val reduce = vm.reduceMotion
    val pagerState = rememberPagerState(initialPage = tab.ordinal) { MainTab.entries.size }
    // Compose every tab right after the first frame so switching never has to build a page.
    var preloadPages by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        withFrameNanos { }
        preloadPages = MainTab.entries.size - 1
    }
    // Home's endless decoration (the drifting eyes) only runs while Home is at least partly on screen.
    val homeActive by remember(interactive) {
        derivedStateOf { interactive && pagerState.currentPage + pagerState.currentPageOffsetFraction < 1f }
    }
    var backActive by remember { mutableStateOf(false) }
    var fastSettle by remember { mutableStateOf(false) }
    LaunchedEffect(tab) {
        val spec = if (reduce) {
            snap<Float>()
        } else if (fastSettle) {
            tween<Float>(240, easing = LinearOutSlowInEasing)
        } else {
            tween<Float>(420, easing = FastOutSlowInEasing)
        }
        fastSettle = false
        pagerState.animateScrollToPage(tab.ordinal, animationSpec = spec)
    }
    PredictiveBackHandler(enabled = interactive && tab != MainTab.HOME) { progress ->
        backActive = true
        val start = tab.ordinal
        try {
            progress.collect { event ->
                val size = pagerState.layoutInfo.pageSize + pagerState.layoutInfo.pageSpacing
                val current = (pagerState.currentPage + pagerState.currentPageOffsetFraction) * size
                val target = start * (1f - event.progress) * size
                pagerState.scroll { scrollBy(target - current) }
            }
            fastSettle = true
            onTab(MainTab.HOME)
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                pagerState.animateScrollToPage(start, animationSpec = tween<Float>(200, easing = LinearOutSlowInEasing))
            }
        } finally {
            backActive = false
        }
    }
    // Safety net: never stay half-way between pages after an interrupted gesture.
    LaunchedEffect(backActive, tab) {
        if (!backActive) {
            delay(500)
            if (!backActive && (pagerState.currentPage != tab.ordinal ||
                    kotlin.math.abs(pagerState.currentPageOffsetFraction) > 0.001f)) {
                pagerState.animateScrollToPage(tab.ordinal)
            }
        }
    }

    // Back closes the selection first (registered after the tab handler above, so it takes precedence).
    androidx.activity.compose.BackHandler(enabled = interactive && vm.selecting) { vm.clearSelection() }
    var showSystemWarning by remember { mutableStateOf(false) }
    if (interactive && showSystemWarning) {
        SystemAppsWarning(
            onCancel = { showSystemWarning = false }, // cancelled: the next attempt shows the warning again
            onConfirm = {
                vm.systemWarningAccepted = true
                vm.showSystemApps = true
                showSystemWarning = false
            },
        )
    }
    var confirmReset by remember { mutableStateOf(false) }
    if (interactive && confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.sel_confirm_title)) },
            text = { Text(stringResource(R.string.sel_confirm_body, vm.selection.size)) },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; vm.bulk(BulkAction.RESET_ALL) }) {
                    Text(stringResource(R.string.protected_ok))
                }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.protected_cancel)) } },
        )
    }

    // ---- header: wide at the top of a list, narrow once the list has scrolled (with a little hysteresis) ----
    val listStates = listOf(homeState, appsState, settingsState)
    val collapse = remember { Animatable(0f) }
    val collapseHigh = with(density) { 24.dp.toPx() }
    val collapseLow = with(density) { 6.dp.toPx() }
    LaunchedEffect(tab, reduce) {
        var last = 0f
        snapshotFlow {
            val ls = listStates[tab.ordinal]
            // Selection mode: the header stays narrow whatever the scroll position.
            if (vm.selecting || ls.firstVisibleItemIndex > 0) Float.MAX_VALUE else ls.firstVisibleItemScrollOffset.toFloat()
        }
            .map { offset ->
                last = when {
                    offset > collapseHigh -> 1f
                    offset < collapseLow -> 0f
                    else -> last
                }
                last
            }
            .distinctUntilChanged()
            .collectLatest { target ->
                if (reduce) collapse.snapTo(target)
                else collapse.animateTo(target, spring(dampingRatio = 0.85f, stiffness = 450f))
            }
    }
    val collapseFn = remember(collapse) { { collapse.value } }
    var headerPx by remember { mutableIntStateOf(0) }
    val headerInset = with(density) { headerPx.toDp() }
    val statusTopPx = WindowInsets.statusBars.getTop(density)
    val collapsedBandPx = with(density) { 56.dp.roundToPx() }
    val headerHeightFn = remember(collapse, statusTopPx, collapsedBandPx) {
        {
            val c = collapse.value.coerceIn(0f, 1f)
            val collapsed = statusTopPx + collapsedBandPx
            (headerPx + (collapsed - headerPx) * c).roundToInt()
        }
    }
    // The Apps header carries the search field and filters; they stay put, only the title block narrows.
    var toolbarPx by remember { mutableIntStateOf(0) }
    val onApps = tab == MainTab.APPS
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val openApp: (String) -> Unit = {
        focus.clearFocus()
        keyboard?.hide()
        onOpenApp(it)
    }
    LaunchedEffect(tab) {
        if (tab != MainTab.APPS) {
            focus.clearFocus()
            keyboard?.hide()
        }
    }
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val listBottom = 96.dp + navInset
    val title = if (vm.selecting) stringResource(R.string.sel_count_fmt, vm.selection.size) else stringResource(tab.titleRes)

    CompositionLocalProvider(
        LocalReduceMotion provides reduce,
        LocalListBottomPadding provides listBottom,
        LocalListTopPadding provides headerInset,
        LocalHeaderHeight provides headerHeightFn,
    ) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = false,
                beyondViewportPageCount = preloadPages,
                key = { it },
            ) { page ->
                when (MainTab.entries[page]) {
                    MainTab.HOME -> HomeScreen(
                        vm, homeState, onShowModule,
                        onOpenAppsFilter = { f ->
                            vm.query = ""
                            vm.clearFilters()
                            if (f != null) vm.toggleFilter(f)
                            onTab(MainTab.APPS)
                        },
                        onOpenApp = openApp,
                        modifier = Modifier.fillMaxSize(),
                        active = homeActive,
                    )
                    MainTab.APPS -> CompositionLocalProvider(
                        LocalListTopPadding provides headerInset + with(density) { toolbarPx.toDp() },
                    ) { AppsScreen(vm, appsState, openApp, Modifier.fillMaxSize()) }
                    MainTab.SETTINGS -> SettingsScreen(vm, settingsState, onShowModule, Modifier.fillMaxSize())
                }
            }

            AppHeader(
                appName = stringResource(R.string.app_name),
                title = title,
                collapse = collapseFn,
                reduceMotion = reduce,
                modifier = Modifier.align(Alignment.TopCenter),
                onExpandedHeight = { headerPx = it },
                bottomContent = if (onApps) {
                    {
                        Box(Modifier.onSizeChanged { toolbarPx = it.height }) {
                            AppsToolbar(vm, onSystemWarning = { showSystemWarning = true })
                        }
                    }
                } else {
                    null
                },
                actions = {
                if (vm.selecting) {
                    IconButton(onClick = { vm.selectAll(vm.selectableKeys) }) {
                        Icon(
                            Icons.Rounded.SelectAll,
                            contentDescription = stringResource(R.string.sel_select_all),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(onClick = { vm.clearSelection() }) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.sel_close),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                },
            )

            val barModifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            AnimatedVisibility(
                visible = !vm.selecting,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = barModifier,
            ) {
                if (vm.navStyle == 1) {
                    FloatingNavBar(MainTab.entries, tab, reduce, onTab)
                } else {
                    ExpandingNavBar(MainTab.entries, tab, reduce, onTab)
                }
            }
            AnimatedVisibility(
                visible = vm.selecting,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = barModifier,
            ) {
                SelectionBar(onAction = { action ->
                    if (action == BulkAction.RESET_ALL) confirmReset = true else vm.bulk(action)
                })
            }
        }
    }
}

/**
 * Hands the bundled SELinux module zip to a root manager (KernelSU, Magisk, APatch) with "open with", so its own
 * install screen appears. Returns false when nothing could open it.
 */
private fun openModuleWithManager(context: Context): Boolean {
    val uri = try {
        val dir = java.io.File(context.cacheDir, "module").apply { mkdirs() }
        val file = java.io.File(dir, "${dev.hfw.Identity.APP_NAME}-SELinux-module.zip")
        context.assets.open("hfw_sepolicy.zip").use { input -> file.outputStream().use { input.copyTo(it) } }
        androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    } catch (_: Throwable) {
        return false
    }
    fun view() = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, "application/zip")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    // Known root managers first, then whatever else can open a zip.
    val managers = listOf("me.weishu.kernelsu", "com.rifsxd.ksunext", "me.bmax.apatch", "com.topjohnwu.magisk")
    for (pkg in managers) {
        try {
            context.startActivity(view().setPackage(pkg))
            return true
        } catch (_: Throwable) {
        }
    }
    val fallbackIntent = view()
    if (context.packageManager.queryIntentActivities(fallbackIntent, 0).isEmpty()) return false
    return try {
        context.startActivity(Intent.createChooser(fallbackIntent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: Throwable) {
        false
    }
}
