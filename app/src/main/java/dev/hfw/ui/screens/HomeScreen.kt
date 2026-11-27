package dev.hfw.ui.screens

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hfw.R
import dev.hfw.HfwViewModel
import dev.hfw.app.Syncer
import dev.hfw.data.ActivityKind
import dev.hfw.data.AppFilter
import dev.hfw.data.AppItem
import dev.hfw.data.Caps
import dev.hfw.ui.components.AppIcon
import dev.hfw.ui.components.HookStatusBadge
import dev.hfw.ui.components.LocalListBottomPadding
import dev.hfw.ui.components.LocalListTopPadding
import dev.hfw.ui.components.pressScale
import dev.hfw.ui.components.rememberCountUp
import dev.hfw.ui.components.staggerIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Home, laid out like XHide's: status card with the drifting eyes, 2x2 counters, the active launcher and a strip of the
 * hidden apps. The header above it is the shared app header.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    vm: HfwViewModel,
    listState: LazyListState,
    onShowModule: () -> Unit,
    onOpenAppsFilter: (AppFilter?) -> Unit,
    onOpenApp: (String) -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = true,
) {
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(
            start = 14.dp,
            end = 14.dp,
            top = LocalListTopPadding.current + 10.dp,
            bottom = LocalListBottomPadding.current,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "status") { StatusCard(vm, active, onShowModule, Modifier.staggerIn(0, listState)) }
        item(key = "stats") { StatGrid(vm, onOpenAppsFilter, listState) }
        item(key = "launcher") { LauncherCard(vm, Modifier.staggerIn(3, listState)) }
        item(key = "recent") { RecentCard(vm, onOpenApp, Modifier.staggerIn(4, listState)) }
    }
}

// ---- status card ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StatusCard(vm: HfwViewModel, active: Boolean, onShowModule: () -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val online = vm.link == Syncer.Link.ONLINE
    val good = online && vm.hooksProblems == 0 && !vm.restartNeeded
    // true = working, false = needs attention, null = still checking
    val state: Boolean? = if (vm.link == Syncer.Link.UNKNOWN) null else good

    val bgTarget = when (state) { true -> scheme.primaryContainer; false -> scheme.errorContainer; null -> scheme.surfaceVariant }
    val fgTarget = when (state) { true -> scheme.onPrimaryContainer; false -> scheme.onErrorContainer; null -> scheme.onSurfaceVariant }
    val dotTarget = when (state) { true -> scheme.primary; false -> scheme.error; null -> scheme.outline }
    val spec = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    val bg by animateColorAsState(bgTarget, spec, label = "statusBg")
    val fg by animateColorAsState(fgTarget, spec, label = "statusFg")
    val dot by animateColorAsState(dotTarget, spec, label = "statusDot")

    val title = when {
        vm.link == Syncer.Link.UNKNOWN -> R.string.hero_checking
        vm.link == Syncer.Link.REJECTED -> R.string.hero_rejected
        vm.link != Syncer.Link.ONLINE -> R.string.hero_offline
        vm.restartNeeded -> R.string.hero_restart
        vm.hooksProblems > 0 -> R.string.hero_partial
        else -> R.string.hero_active
    }
    val sub = when {
        vm.link == Syncer.Link.UNKNOWN -> R.string.hero_checking_sub
        vm.link == Syncer.Link.REJECTED -> R.string.hero_rejected_sub
        vm.link != Syncer.Link.ONLINE -> R.string.hero_offline_sub
        vm.restartNeeded -> R.string.hero_restart_sub
        vm.hooksProblems > 0 -> R.string.hero_partial_sub
        !Caps.icon && !Caps.hide -> R.string.hero_active_sub_name
        !Caps.icon -> R.string.hero_active_sub_hide
        else -> R.string.hero_active_sub
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = bg, contentColor = fg),
    ) {
        Box {
            if (Build.VERSION.SDK_INT >= 31) {
                // Android 12+: the picture is blurred live, only its colours come through.
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(R.drawable.status_bg),
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.matchParentSize().graphicsLayer { scaleX = 1.25f; scaleY = 1.25f }
                        .blur(28.dp, androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded),
                )
            } else {
                // Older Android has no blur: use the same picture, pre-blurred.
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(R.drawable.status_bg_blur),
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            }
            // State tint over the picture: green-ish when working, red when there is a problem, neutral while checking.
            Box(Modifier.matchParentSize().background(bg.copy(alpha = 0.62f)))
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HookStatusBadge(state = state, color = dot, size = 26.dp, active = active)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = fg,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.home_model_fmt, Build.MODEL, Build.VERSION.SDK_INT),
                        style = MaterialTheme.typography.labelLarge,
                        color = fg.copy(alpha = 0.82f),
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(stringResource(sub), style = MaterialTheme.typography.bodyMedium, color = fg.copy(alpha = 0.9f))
                AnimatedVisibility(visible = state == null) {
                    LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                }
                if (Caps.icon && vm.moduleState == dev.hfw.data.ModuleState.MISSING && online) {
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(
                        onClick = onShowModule,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    ) { Text(stringResource(R.string.home_install_module), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold) }
                }
                if (vm.pending > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "${stringResource(R.string.status_pending)}: ${vm.pending}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = fg,
                    )
                }
            }
        }
    }
}

// ---- counters ------------------------------------------------------------------------------

private class Tile(
    val title: String,
    val icon: ImageVector,
    val count: Int,
    val subtitle: String,
    val onClick: (() -> Unit)?,
)

@Composable
private fun StatGrid(vm: HfwViewModel, onOpenAppsFilter: (AppFilter?) -> Unit, listState: LazyListState) {
    val viewAll = stringResource(R.string.home_view_all)
    val profiles = vm.apps.map { it.profile }.distinct()
    val profileNames = profiles.map { stringResource(profileLabel(it)) }
    val tiles = buildList {
        if (Caps.hide) add(Tile(stringResource(R.string.tile_hidden), Icons.Rounded.VisibilityOff, vm.hiddenCount, viewAll) { onOpenAppsFilter(AppFilter.HIDDEN) })
        add(Tile(stringResource(R.string.tile_renamed), Icons.Rounded.Edit, vm.renamedCount, viewAll) { onOpenAppsFilter(AppFilter.RENAMED) })
        if (Caps.icon) add(Tile(stringResource(R.string.tile_icons), Icons.Rounded.Image, vm.iconCount, viewAll) { onOpenAppsFilter(AppFilter.ICON_CHANGED) })
        add(
            Tile(
                stringResource(R.string.home_profiles),
                Icons.Rounded.Group,
                profiles.size.coerceAtLeast(1),
                profileNames.joinToString(" · "),
                null,
            ),
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.chunked(2).forEachIndexed { row, pair ->
            Row(
                Modifier.fillMaxWidth().staggerIn(1 + row, listState),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                pair.forEach { StatCard(it, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatCard(tile: Tile, modifier: Modifier) {
    val source = remember { MutableInteractionSource() }
    val counted = rememberCountUp(tile.count, animateFromZero = true, startDelayMillis = 380L)
    val body: @Composable () -> Unit = {
        Box {
            Icon(
                imageVector = tile.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f),
                modifier = Modifier.align(Alignment.BottomEnd).offset(x = 18.dp, y = 18.dp).size(84.dp),
            )
            Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                Text(tile.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(counted.toString(), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(tile.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    val click = tile.onClick
    if (click != null) {
        Surface(
            onClick = click,
            modifier = modifier.pressScale(source),
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 2.dp,
            interactionSource = source,
            content = body,
        )
    } else {
        Surface(modifier = modifier, shape = RoundedCornerShape(24.dp), tonalElevation = 2.dp, content = body)
    }
}

// ---- active launcher -----------------------------------------------------------------------

private class LauncherInfo(val label: String, val pkg: String, val icon: ImageBitmap?)

private fun detectLauncher(context: android.content.Context): LauncherInfo? {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val info = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo ?: return null
    if (info.packageName == "android") return null
    return LauncherInfo(info.loadLabel(pm).toString(), info.packageName, drawableToBitmap(info.loadIcon(pm)))
}

private fun drawableToBitmap(d: Drawable?, px: Int = 96): ImageBitmap? {
    if (d == null) return null
    return try {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        d.setBounds(0, 0, px, px)
        d.draw(canvas)
        bmp.asImageBitmap()
    } catch (_: Throwable) {
        null
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LauncherCard(vm: HfwViewModel, modifier: Modifier) {
    val context = LocalContext.current
    val launcher by produceState<LauncherInfo?>(null, vm.refreshing) {
        value = withContext(Dispatchers.IO) { detectLauncher(context) }
    }
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), tonalElevation = 2.dp) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                launcher?.icon?.let {
                    Image(bitmap = it, contentDescription = launcher?.label, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)))
                    Spacer(Modifier.width(14.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.home_launcher_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        launcher?.label ?: stringResource(R.string.home_launcher_default),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(launcher?.pkg ?: "android", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                FilledTonalButton(
                    onClick = { vm.refreshLaunchers() },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) { Text(stringResource(R.string.home_launcher_refresh), style = MaterialTheme.typography.labelLarge) }
            }
            AnimatedVisibility(visible = vm.refreshing) {
                LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 14.dp))
            }
        }
    }
}

// ---- recent changes ------------------------------------------------------------------------

@Composable
private fun RecentCard(vm: HfwViewModel, onOpenApp: (String) -> Unit, modifier: Modifier) {
    val entries = vm.visibleActivity().take(5)
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), tonalElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text(stringResource(R.string.recent_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            if (entries.isEmpty()) {
                Text(stringResource(R.string.home_recent_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
            } else {
                entries.forEach { e ->
                    val app = vm.apps.firstOrNull { it.pkg == e.pkg }
                    val text = stringResource(
                        when (e.kind) {
                            ActivityKind.HIDDEN -> R.string.activity_hidden
                            ActivityKind.UNHIDDEN -> R.string.activity_unhidden
                            ActivityKind.RENAMED -> R.string.activity_renamed
                            ActivityKind.ICON_CHANGED -> R.string.activity_icon
                            ActivityKind.RESET -> R.string.activity_reset
                        },
                        e.label,
                    )
                    val source = remember { MutableInteractionSource() }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(enabled = app != null, interactionSource = source, indication = androidx.compose.material3.ripple()) { app?.let { onOpenApp(it.key) } }
                            .padding(vertical = 8.dp, horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (app != null) {
                            AppIcon(app, 36.dp)
                            Spacer(Modifier.width(12.dp))
                        }
                        Text(text, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
