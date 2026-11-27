package dev.fadalias.ui

import androidx.activity.compose.BackHandler
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
import dev.fadalias.R
import dev.fadalias.FadaliasViewModel
import dev.fadalias.ui.components.FloatingNav
import dev.fadalias.ui.components.MainTab
import dev.fadalias.ui.components.ModuleRequiredSheet
import dev.fadalias.ui.components.SharedCtx
import dev.fadalias.ui.screens.AppsScreen
import dev.fadalias.ui.screens.DetailScreen
import dev.fadalias.ui.screens.HomeScreen
import dev.fadalias.ui.screens.SettingsScreen
import kotlinx.coroutines.launch

/**
 * Root of the template: three tabs behind a floating toolbar plus a detail screen that is
 * pushed on top (the app icon is a shared element between the list and the detail).
 * Navigation is plain state on purpose, no navigation library needed for a template.
 */
@Composable
fun FadaliasApp(vm: FadaliasViewModel) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val notify: (String) -> Unit = { message -> scope.launch { snackbar.showSnackbar(message) } }

    var tab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    var detailKey by rememberSaveable { mutableStateOf<String?>(null) }
    var showModuleSheet by remember { mutableStateOf(false) }

    val homeState = rememberLazyListState()
    val appsState = rememberLazyListState()
    val settingsState = rememberLazyListState()

    BackHandler(enabled = detailKey != null) { detailKey = null }

    val context = LocalContext.current
    val savedMessage = stringResource(R.string.module_saved)
    val saveFailed = stringResource(R.string.module_save_failed)
    LaunchedEffect(vm) {
        vm.messages.collect { res -> notify(context.getString(res)) }
    }
    val saveModule = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = try {
            context.assets.open("fadalias_sepolicy.zip").use { input ->
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

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SharedTransitionLayout {
            val sharedScope = this
            AnimatedContent(
                targetState = detailKey,
                transitionSpec = {
                    (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
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
                        modifier = Modifier.fillMaxSize(),
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
                saveModule.launch("Fadalias-SELinux-module.zip")
            },
            onDismiss = { showModuleSheet = false },
        )
    }
}

@Composable
private fun MainShell(
    vm: FadaliasViewModel,
    tab: MainTab,
    onTab: (MainTab) -> Unit,
    homeState: androidx.compose.foundation.lazy.LazyListState,
    appsState: androidx.compose.foundation.lazy.LazyListState,
    settingsState: androidx.compose.foundation.lazy.LazyListState,
    shared: SharedCtx,
    onOpenApp: (String) -> Unit,
    onShowModule: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)) +
                    slideInHorizontally(animationSpec = spring<IntOffset>(stiffness = Spring.StiffnessMediumLow)) { full -> full / 10 * dir }) togetherWith
                    (fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium)) +
                        slideOutHorizontally(animationSpec = spring<IntOffset>(stiffness = Spring.StiffnessMediumLow)) { full -> -full / 10 * dir })
            },
            label = "tab",
            modifier = Modifier.fillMaxSize(),
        ) { current ->
            when (current) {
                MainTab.HOME -> HomeScreen(vm, homeState, onShowModule, Modifier.fillMaxSize())
                MainTab.APPS -> AppsScreen(vm, appsState, shared, onOpenApp, Modifier.fillMaxSize())
                MainTab.SETTINGS -> SettingsScreen(vm, settingsState, onShowModule, Modifier.fillMaxSize())
            }
        }
        FloatingNav(
            tab = tab,
            onTab = onTab,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 16.dp),
        )
    }
}
