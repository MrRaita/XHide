package dev.hfw.ui.screens

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hfw.R
import dev.hfw.HfwViewModel
import dev.hfw.data.AppItem
import dev.hfw.data.AppProfile
import dev.hfw.data.Caps
import dev.hfw.data.ModuleState
import dev.hfw.state.Overlay
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import dev.hfw.ui.components.AppIcon
import dev.hfw.ui.components.IconPickerSheet
import dev.hfw.ui.components.Pill
import dev.hfw.ui.components.RenameSheet
import dev.hfw.ui.components.SectionTitle
import dev.hfw.ui.components.IconTrack
import dev.hfw.ui.components.ShapedIcon
import java.util.Date

@Composable
fun DetailScreen(
    vm: HfwViewModel,
    itemKey: String,
    onBack: () -> Unit,
    onShowModule: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The profile shown can be switched inside the screen (the same package in main / work / clone / private).
    var shownKey by androidx.compose.runtime.saveable.rememberSaveable(itemKey) { mutableStateOf(itemKey) }
    val app = vm.find(shownKey) ?: vm.find(itemKey) ?: return
    val siblings = vm.siblings(app)
    var allProfiles by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val applyAll = allProfiles && siblings.size > 1
    var confirmReset by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showIcons by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    // warnings: system apps and HFW itself. 0 = none, 1 = name pending, 2 = icon pending, 3 = hide (HFW only) pending
    val isSelf = app.pkg == dev.hfw.Identity.PACKAGE
    val riskKind = when {
        isSelf -> dev.hfw.ui.components.RiskKind.SELF
        else -> null
    }
    val warnSuppressed = when (riskKind) {
        dev.hfw.ui.components.RiskKind.SELF -> vm.hideSelfWarning
        else -> true
    }
    var pendingRisky by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(0) }
    if (pendingRisky != 0 && riskKind != null) {
        dev.hfw.ui.components.ProtectedDialog(
            kind = if (pendingRisky == 3) dev.hfw.ui.components.RiskKind.SELF_HIDE else riskKind,
            onCancel = { pendingRisky = 0 },
            onConfirm = { dontShow ->
                if (dontShow) {
                    if (isSelf) vm.hideSelfWarning = true
                }
                when (pendingRisky) {
                    1 -> showRename = true
                    2 -> showIcons = true
                    3 -> vm.setHidden(app, true)
                }
                pendingRisky = 0
            },
        )
    }
    val requestName: () -> Unit = {
        if (!app.lookProtected) {
            if (warnSuppressed) showRename = true else pendingRisky = 1
        }
    }
    val requestHide: (Boolean) -> Unit = { hide ->
        if (hide && isSelf && !vm.hideSelfWarning) pendingRisky = 3 else vm.setHidden(app, hide)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.setIconFromUri(app, uri, applyAll)
    }

    val density = androidx.compose.ui.platform.LocalDensity.current
    val scroll = rememberScrollState()
    val statusPx = WindowInsets.statusBars.getTop(density)
    val barDp = with(density) { statusPx.toDp() } + 56.dp
    // Collapse geometry (px): the icon travels from its resting centre (c0) to the slot in the top bar (ct).
    val barPx = with(density) { barDp.toPx() }
    val c0 = barPx + with(density) { 68.dp.toPx() }
    val ct = statusPx + with(density) { 28.dp.toPx() }
    val travel = (c0 - ct).coerceAtLeast(1f)
    val collapseP: () -> Float = { (scroll.value / travel).coerceIn(0f, 1f) }
    Surface(modifier = modifier, color = scheme.background, contentColor = scheme.onBackground) {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = with(density) { maxWidth.toPx() }
        val cxt = with(density) { 76.dp.toPx() }
        val flightReg = dev.hfw.ui.components.LocalIconFlight.current
        val iconPx = with(density) { 120.dp.toPx() }
        // Keep the flight's idea of "where the icon is now" current (also when collapsed into the bar). Frozen while a flight runs.
        androidx.compose.runtime.LaunchedEffect(widthPx, travel, cxt) {
            androidx.compose.runtime.snapshotFlow { scroll.value }.collect { v ->
                if (flightReg.flying == null || v == 0) {
                    val p = (v / travel).coerceIn(0f, 1f)
                    val cx = androidx.compose.ui.util.lerp(widthPx / 2f, cxt, p)
                    val cy = androidx.compose.ui.util.lerp(c0 - v, ct, p)
                    val half = iconPx * androidx.compose.ui.util.lerp(1f, 0.3f, p) / 2f
                    flightReg.detailRect = androidx.compose.ui.geometry.Rect(cx - half, cy - half, cx + half, cy + half)
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(barDp + 8.dp))
            // header
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.size(120.dp))
                Spacer(Modifier.height(16.dp))
                Text(
                    text = app.displayLabel,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.graphicsLayer { alpha = (1f - collapseP() * 2f).coerceIn(0f, 1f) },
                )
                if (app.renamed) {
                    Text(
                        text = stringResource(R.string.original_name_fmt, app.label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.primary,
                    )
                }
                Text(
                    text = app.pkg,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(stringResource(profileLabel(app.profile)))
                    if (app.isSystem) Pill(stringResource(R.string.pill_system))
                    if (app.isModule) Pill(stringResource(R.string.pill_module))
                    if (app.hidden) Pill(stringResource(R.string.pill_hidden))
                }
            }

            if (siblings.size > 1) {
                SectionTitle(stringResource(R.string.detail_profiles))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    siblings.forEach { sib ->
                        androidx.compose.material3.FilterChip(
                            selected = sib.key == app.key,
                            onClick = { shownKey = sib.key },
                            label = { Text(stringResource(profileLabel(sib.profile)), maxLines = 1) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                ActionCard(
                    icon = Icons.Rounded.Layers,
                    title = stringResource(R.string.profile_apply_all),
                    subtitle = stringResource(R.string.profile_apply_all_sub),
                    onClick = { allProfiles = !allProfiles },
                    trailing = { Switch(checked = allProfiles, onCheckedChange = { allProfiles = it }) },
                )
            }

            SectionTitle(stringResource(R.string.detail_actions))

            if (Caps.hide) {
                if (vm.advancedMode && !isSelf) {
                    HideScopeCard(vm, app)
                } else {
                    ActionCard(
                        icon = if (app.hidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        title = stringResource(R.string.action_hide),
                        subtitle = if (app.hidden) scopeStatus(vm.hideScopeOf(app)) else stringResource(R.string.action_hide_off),
                        onClick = { requestHide(!app.hidden) },
                        trailing = {
                            Switch(checked = app.hidden, onCheckedChange = { requestHide(it) })
                        },
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
            if (Caps.icon) {
                ActionCard(
                    icon = Icons.Rounded.Image,
                    title = stringResource(R.string.action_icon),
                    subtitle = when {
                        app.lookProtected -> stringResource(R.string.action_protected)
                        vm.moduleState == ModuleState.MISSING -> stringResource(R.string.action_icon_needs_module)
                        app.iconChanged && app.iconStatus == Overlay.OK -> stringResource(R.string.action_icon_custom)
                        app.iconChanged && (app.iconStatus == Overlay.FAILED || app.iconStatus == Overlay.BLOCKED) -> stringResource(R.string.action_icon_failed)
                        app.iconChanged -> stringResource(R.string.action_icon_pending)
                        else -> stringResource(R.string.action_icon_default)
                    },
                    onClick = {
                        when {
                            app.lookProtected -> Unit
                            vm.moduleState == ModuleState.MISSING -> onShowModule()
                            !warnSuppressed -> pendingRisky = 2
                            else -> showIcons = true
                        }
                    },
                    trailing = {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = scheme.onSurfaceVariant)
                    },
                )
                Spacer(Modifier.height(12.dp))
            }
            ActionCard(
                icon = Icons.Rounded.Edit,
                title = stringResource(R.string.action_name),
                subtitle = if (app.lookProtected) stringResource(R.string.action_protected) else (app.customLabel ?: stringResource(R.string.action_name_default)),
                onClick = { requestName() },
                trailing = {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = scheme.onSurfaceVariant)
                },
            )

            if (app.modified) {
                Spacer(Modifier.height(12.dp))
                ActionCard(
                    icon = Icons.Rounded.RestartAlt,
                    title = stringResource(R.string.action_reset),
                    subtitle = stringResource(R.string.action_reset_sub),
                    onClick = { if (siblings.size > 1) confirmReset = true else vm.resetApp(app, false) },
                    trailing = {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = scheme.onSurfaceVariant)
                    },
                )
            }

            SectionTitle(stringResource(R.string.detail_info))
            InfoCard(app)
            Spacer(Modifier.height(32.dp))
        }

        // Top bar: opaque strip over the scrolling content, with the back button and (once collapsed) the app name.
        Box(
            Modifier.fillMaxWidth().height(barDp)
                .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                .drawBehind {
                    // Same colour as the page at the top, fading to the card colour as the icon collapses into the bar.
                    drawRect(androidx.compose.ui.graphics.lerp(scheme.background, scheme.surfaceContainer, collapseP()))
                },
        ) {
            Box(Modifier.fillMaxSize().padding(top = with(density) { statusPx.toDp() })) {
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back), tint = scheme.onSurface)
                }
                Text(
                    text = app.displayLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 104.dp, end = 16.dp)
                        .graphicsLayer { alpha = ((collapseP() - 0.5f) * 2f).coerceIn(0f, 1f) },
                )
            }
        }

        // The app icon: rests in the header, then slides up and left and shrinks into the bar as the page scrolls.
        Box(
            Modifier
                .size(120.dp)
                .graphicsLayer {
                    val p = collapseP()
                    val cx = androidx.compose.ui.util.lerp(widthPx / 2f, cxt, p)
                    val cy = androidx.compose.ui.util.lerp(c0 - scroll.value, ct, p)
                    val sc = androidx.compose.ui.util.lerp(1f, 0.3f, p)
                    translationX = cx - size.width / 2f
                    translationY = cy - size.height / 2f
                    scaleX = sc
                    scaleY = sc
                },
        ) {
            AppIcon(app, 120.dp, track = IconTrack.DETAIL, dimmed = app.hidden)
        }
    }
    }

    if (confirmReset) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.reset_title)) },
            text = {
                Column {
                    androidx.compose.material3.TextButton(onClick = { confirmReset = false; vm.resetApp(app, false) }) {
                        Text(stringResource(R.string.reset_this_profile))
                    }
                    androidx.compose.material3.TextButton(onClick = { confirmReset = false; vm.resetApp(app, true) }) {
                        Text(stringResource(R.string.reset_all_profiles))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.protected_cancel)) }
            },
        )
    }
    if (showRename) {
        RenameSheet(app = app, onSave = { vm.setCustomLabel(app, it, applyAll) }, onDismiss = { showRename = false })
    }
    if (showIcons) {
        IconPickerSheet(
            app = app,
            onGallery = { picker.launch("image/*"); showIcons = false },
            onReset = { vm.resetIcon(app, applyAll) },
            onDismiss = { showIcons = false },
        )
    }
}

internal fun profileLabel(profile: AppProfile): Int = when (profile) {
    AppProfile.MAIN -> R.string.profile_main
    AppProfile.WORK -> R.string.profile_work
    AppProfile.CLONE -> R.string.profile_clone
    AppProfile.PRIVATE -> R.string.profile_private
}

@Composable
private fun ActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = scheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            ShapedIcon(
                icon = icon,
                background = scheme.primaryContainer,
                tint = scheme.onPrimaryContainer,
                size = 52.dp,
                shape = RoundedCornerShape(18.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Light),
                    color = scheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
private fun InfoCard(app: AppItem) {
    val context = LocalContext.current
    val date = DateFormat.getMediumDateFormat(context)
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            InfoLine(stringResource(R.string.info_version), app.version)
            InfoLine(stringResource(R.string.info_size), formatSize(app.sizeMb))
            InfoLine(stringResource(R.string.info_installed), date.format(Date(app.installedAt)))
            InfoLine(stringResource(R.string.info_updated), date.format(Date(app.updatedAt)))
            InfoLine(stringResource(R.string.info_profile), stringResource(profileLabel(app.profile)))
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

/** Real coverage of the hide, grouped the way the user thinks about it. */
@Composable
private fun scopeStatus(mask: Int): String {
    val H = dev.hfw.state.HideScope
    if (mask and H.ALL == H.ALL) return stringResource(R.string.scope_everywhere)
    val parts = buildList {
        if (mask and H.LAUNCHER != 0) add(stringResource(R.string.scope_cat_launcher))
        if (mask and H.SUGGESTIONS != 0) add(stringResource(R.string.scope_cat_suggestions))
        if (mask and (H.SHORTCUT_CONFIGS or H.SHORTCUTS or H.WIDGETS) != 0) add(stringResource(R.string.scope_cat_shortcuts))
        if (mask and (H.INTENT_CHOOSER or H.SHARE_TARGETS) != 0) add(stringResource(R.string.scope_cat_chooser))
    }
    return if (parts.isEmpty()) stringResource(R.string.action_hide_off) else stringResource(R.string.scope_hidden_in, parts.joinToString(", "))
}

/** Advanced mode: the master card (same look as the other action cards) and, right below, one tile per place. */
@Composable
private fun HideScopeCard(vm: HfwViewModel, app: AppItem) {
    val scheme = MaterialTheme.colorScheme
    val H = dev.hfw.state.HideScope
    val mask = vm.hideScopeOf(app)
    val custom = vm.hasCustomHideScope(app)
    val on = mask != 0
    val count = Integer.bitCount(mask and H.ALL)
    val setAll: (Boolean) -> Unit = { want -> if (want) vm.setHideScope(app, H.ALL) else vm.setHidden(app, false) }
    ActionCard(
        icon = if (on) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
        title = stringResource(R.string.scope_all),
        subtitle = when {
            !on -> stringResource(R.string.action_hide_off)
            count == 7 -> stringResource(R.string.scope_everywhere)
            else -> stringResource(R.string.scope_count_fmt, count, 7) + " · " + scopeStatus(mask)
        },
        onClick = { setAll(!on) },
        trailing = { Switch(checked = on, onCheckedChange = { setAll(it) }) },
    )
    Spacer(Modifier.height(12.dp))
    class Place(val flag: Int, val icon: ImageVector, val title: Int, val sub: Int)
    val places = listOf(
        Place(H.LAUNCHER, Icons.Rounded.Home, R.string.scope_launcher, R.string.scope_launcher_sub),
        Place(H.SUGGESTIONS, Icons.Rounded.Search, R.string.scope_suggestions, R.string.scope_suggestions_sub),
        Place(H.SHORTCUT_CONFIGS, Icons.Rounded.Tune, R.string.scope_shortcut_configs, R.string.scope_shortcut_configs_sub),
        Place(H.SHORTCUTS, Icons.Rounded.Bolt, R.string.scope_shortcuts, R.string.scope_shortcuts_sub),
        Place(H.INTENT_CHOOSER, Icons.AutoMirrored.Rounded.OpenInNew, R.string.scope_chooser, R.string.scope_chooser_sub),
        Place(H.SHARE_TARGETS, Icons.Rounded.Share, R.string.scope_share_targets, R.string.scope_share_targets_sub),
        Place(H.WIDGETS, Icons.Rounded.Widgets, R.string.scope_widgets, R.string.scope_widgets_sub),
    )
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        places.forEachIndexed { i, pl ->
            val checked = (mask and pl.flag) != 0
            Surface(
                onClick = { vm.toggleHideScope(app, pl.flag) },
                shape = dev.hfw.ui.components.segmentShape(i, places.size, big = 28.dp, small = 8.dp),
                color = scheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(pl.icon, contentDescription = null, tint = if (checked) scheme.primary else scheme.onSurfaceVariant)
                    Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                        Text(stringResource(pl.title), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(
                            stringResource(pl.sub),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = checked, onCheckedChange = { vm.toggleHideScope(app, pl.flag) })
                }
            }
        }
    }
    if (custom) {
        androidx.compose.material3.TextButton(onClick = { vm.setHideScope(app, null) }) {
            Text(stringResource(R.string.scope_reset))
        }
    }
}
