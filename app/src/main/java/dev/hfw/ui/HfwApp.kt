package dev.hfw.ui

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
import dev.hfw.ui.components.SelectionBar
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SharedTransitionLayout
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
import dev.hfw.ui.components.FloatingNav
import dev.hfw.ui.components.MainTab
import dev.hfw.ui.components.glassSource
import dev.hfw.ui.components.rememberGlassLayers
import dev.hfw.ui.components.ModuleRequiredSheet
import dev.hfw.ui.components.SharedCtx
import dev.hfw.ui.screens.AppsScreen
import dev.hfw.ui.screens.DetailScreen
import dev.hfw.ui.screens.HomeScreen
import dev.hfw.ui.screens.SettingsScreen
import kotlinx.coroutines.launch

/**
 * Root of the template: three tabs behind a floating toolbar plus a detail screen that is
 * pushed on top (the app icon is a shared element between the list and the detail).
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
    PredictiveBackHandler(enabled = detailKey != null) { progress ->
        try {
            progress.collect { event ->
                backDir = if (event.swipeEdge == BackEventCompat.EDGE_RIGHT) -1f else 1f
                backAnim.snapTo(event.progress)
            }
            detailKey = null
        } catch (e: CancellationException) {
            withContext(NonCancellable) { backAnim.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
        }
    }
    LaunchedEffect(detailKey) {
        if (detailKey == null) {
            delay(450) // let the exit transition finish before resetting the gesture state
            backAnim.snapTo(0f)
        }
    }

    val context = LocalContext.current
    val savedMessage = stringResource(R.string.module_saved)
    val saveFailed = stringResource(R.string.module_save_failed)
    LaunchedEffect(vm) {
        vm.messages.collect { res -> notify(context.getString(res)) }
    }
    val saveModule = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = try {
            context.assets.open("hfw_sepolicy.zip").use { input ->
                context.contentResolver.openOutputStream(uri)?.use { out -> input.copyTo(out) } != null
            }
        } catch (_: Throwable) {
            false
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

    // The screen underneath a detail page stays composed (frozen, not interactive), so the predictive back gesture
    // shows the previous screen exactly as it was. It is dropped once the return transition has finished.
    var backdrop by remember { mutableStateOf(detailKey != null) }
    LaunchedEffect(detailKey) {
        if (detailKey != null) backdrop = true else { delay(600); backdrop = false }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (backdrop) {
            MainShell(
                vm = vm,
                tab = tab,
                onTab = {},
                homeState = homeState,
                appsState = appsState,
                settingsState = settingsState,
                shared = null,
                onOpenApp = {},
                onShowModule = {},
                interactive = false,
            )
        }
        SharedTransitionLayout {
            val sharedScope = this
            AnimatedContent(
                targetState = detailKey,
                transitionSpec = {
                    if (targetState == null) {
                        // back to the (already visible) backdrop: a plain cross-fade, no scale, so nothing ghosts
                        fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)) togetherWith
                            fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium))
                    } else (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                        scaleIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow), initialScale = 0.96f)) togetherWith
                        (fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium)) +
                            scaleOut(animationSpec = spring(stiffness = Spring.StiffnessMedium), targetScale = 1.02f))
                },
                label = "root",
            ) { pkg ->
                val ctx = SharedCtx(sharedScope, this)
                if (pkg == null) {
                    MainShell(
                        vm = vm,
                        tab = tab,
                        onTab = { tab = it },
                        homeState = homeState,
                        appsState = appsState,
                        settingsState = settingsState,
                        shared = ctx,
                        onOpenApp = { detailKey = it },
                        onShowModule = { showModuleSheet = true },
                    )
                } else {
                    DetailScreen(
                        vm = vm,
                        itemKey = pkg,
                        shared = ctx,
                        onBack = { detailKey = null },
                        onShowModule = { showModuleSheet = true },
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val p = backAnim.value
                                val scale = 1f - 0.10f * p
                                scaleX = scale
                                scaleY = scale
                                translationX = backDir * p * 28.dp.toPx()
                                alpha = 1f - 0.25f * p
                                shape = RoundedCornerShape((28f * p).dp)
                                clip = p > 0f
                            },
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 96.dp),
        )
    }

    if (showModuleSheet) {
        ModuleRequiredSheet(
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
    shared: SharedCtx?,
    onOpenApp: (String) -> Unit,
    onShowModule: () -> Unit,
    interactive: Boolean = true,
) {
    val navLayers = rememberGlassLayers()
    val pagerState = rememberPagerState(initialPage = tab.ordinal) { MainTab.entries.size }
    // Compose every tab right after the first frame so switching never has to build a page.
    var preloadPages by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        withFrameNanos { }
        preloadPages = MainTab.entries.size - 1
    }
    // Home's endless decoration (the drifting eyes) only runs while Home is at least partly on screen.
    val homeActive by remember {
        derivedStateOf { interactive && pagerState.currentPage + pagerState.currentPageOffsetFraction < 1f }
    }
    var backActive by remember { mutableStateOf(false) }
    var fastSettle by remember { mutableStateOf(false) }
    LaunchedEffect(tab) {
        val spec = if (fastSettle) {
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
    LaunchedEffect(backActive) {
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

    Box(Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().glassSource(navLayers),
            userScrollEnabled = false,
            beyondViewportPageCount = preloadPages,
            key = { it },
        ) { page ->
            when (MainTab.entries[page]) {
                MainTab.HOME -> HomeScreen(vm, homeState, onShowModule, Modifier.fillMaxSize(), active = homeActive)
                MainTab.APPS -> AppsScreen(vm, appsState, shared, onOpenApp, Modifier.fillMaxSize())
                MainTab.SETTINGS -> SettingsScreen(vm, settingsState, onShowModule, Modifier.fillMaxSize())
            }
        }
        val barModifier = Modifier
            .align(Alignment.BottomCenter)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = 16.dp)
        AnimatedVisibility(
            visible = !vm.selecting,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = barModifier,
        ) { FloatingNav(tab = tab, onTab = onTab, layers = navLayers) }
        AnimatedVisibility(
            visible = vm.selecting,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = barModifier,
        ) {
            SelectionBar(layers = navLayers, onAction = { action ->
                if (action == BulkAction.RESET_ALL) confirmReset = true else vm.bulk(action)
            })
        }
    }
}
