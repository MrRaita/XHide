package dev.hfw.ui.screens

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.ui.layout.onSizeChanged
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
    var pendingIconKey by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    var pendingIconAll by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val key = pendingIconKey
        val all = pendingIconAll
        pendingIconKey = null
        if (uri != null && key != null) {
            vm.find(key)?.let { vm.setIconFromUri(it, uri, all) }
        }
    }

    val density = androidx.compose.ui.platform.LocalDensity.current
    val scroll = rememberScrollState()
    val statusPx = WindowInsets.statusBars.getTop(density)
    val barDp = with(density) { statusPx.toDp() } + 56.dp
    val travel = with(density) { 112.dp.toPx() }.coerceAtLeast(1f)
    val collapseP: () -> Float = { (scroll.value / travel).coerceIn(0f, 1f) }
    val flightReg = dev.hfw.ui.components.LocalIconFlight.current
    val blurredIcon = dev.hfw.ui.components.rememberBlurredAppIcon(app)

    // Hero geometry (px, content space), derived from measured sizes only so graphicsLayer transforms cannot pollute it.
    // The icon and the name are drawn by an overlay above the top bar; the card holds invisible placeholders for them.
    val colH = androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val nameH = androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val cardW = androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val cardH = androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val heroReady = colH.floatValue > 0f && cardW.floatValue > 0f
    val iconPx = with(density) { 76.dp.toPx() }
    val padPx = with(density) { 18.dp.toPx() }
    val sidePx = with(density) { 16.dp.toPx() }
    val cardTopPx = with(density) { barDp.toPx() + 8.dp.toPx() }
    val rowH = maxOf(iconPx, colH.floatValue)
    val iconRestX = sidePx + padPx
    val iconRestY = cardTopPx + padPx + (rowH - iconPx) / 2f
    val nameRestX = iconRestX + iconPx + sidePx
    val nameRestY = cardTopPx + padPx + (rowH - colH.floatValue) / 2f
    val nameWidthPx = (cardW.floatValue - 2f * padPx - iconPx - sidePx).coerceAtLeast(1f)
    val targetIconCx = with(density) { 76.dp.toPx() }
    val targetCy = statusPx + with(density) { 28.dp.toPx() }
    val targetIconScale = 36f / 76f
    val targetNameX = with(density) { 104.dp.toPx() }
    val targetNameScale = 16f / 22f
    fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t
    fun iconRectAt(scrollV: Int): androidx.compose.ui.geometry.Rect {
        val p = (scrollV / travel).coerceIn(0f, 1f)
        val cx = lerpF(iconRestX + iconPx / 2f, targetIconCx, p)
        val cy = lerpF(iconRestY - scrollV + iconPx / 2f, targetCy, p)
        val half = iconPx * lerpF(1f, targetIconScale, p) / 2f
        return androidx.compose.ui.geometry.Rect(cx - half, cy - half, cx + half, cy + half)
    }
    // Keep the icon-flight registry on the icon's real current position (also when collapsed into the bar);
    // frozen while a flight (open or predictive back) is running.
    androidx.compose.runtime.LaunchedEffect(app.key, heroReady, iconRestY, cardW.floatValue) {
        androidx.compose.runtime.snapshotFlow { if (heroReady) iconRectAt(scroll.value) else null }.collect { r ->
            if (r != null && (flightReg.flying == null || scroll.value == 0)) {
                flightReg.detailRect = r
                flightReg.detailKey = app.key
            }
        }
    }

    Surface(modifier = modifier, color = scheme.background, contentColor = scheme.onBackground) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(horizontal = 16.dp),
            ) {
                Spacer(Modifier.height(barDp + 8.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { cardW.floatValue = it.width.toFloat(); cardH.floatValue = it.height.toFloat() }
                        .graphicsLayer {
                            // The soft backdrop melts into the icon's centre while the icon travels up into the bar.
                            val q = (collapseP() / 0.85f).coerceIn(0f, 1f)
                            val px = ((iconRestX + iconPx / 2f - sidePx) / cardW.floatValue.coerceAtLeast(1f)).coerceIn(0f, 1f)
                            val py = ((iconRestY + iconPx / 2f - cardTopPx) / cardH.floatValue.coerceAtLeast(1f)).coerceIn(0f, 1f)
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(px, py)
                            val sc = 1f - q * 0.97f
                            scaleX = sc
                            scaleY = sc
                            alpha = 1f - q
                        },
                    shape = RoundedCornerShape(28.dp),
                    color = scheme.surfaceContainerLow,
                ) {
                    Box(Modifier.fillMaxWidth()) {
                        if (blurredIcon != null) {
                            androidx.compose.foundation.Image(
                                bitmap = blurredIcon,
                                contentDescription = null,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.matchParentSize(),
                            )
                            Box(
                                Modifier
                                    .matchParentSize()
                                    .background(
                                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                                            colors = listOf(
                                                scheme.surfaceContainerLow.copy(alpha = 0.62f),
                                                scheme.surfaceContainerLow.copy(alpha = 0.86f),
                                            ),
                                        ),
                                    ),
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                        Spacer(Modifier.size(76.dp))
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f).onSizeChanged { colH.floatValue = it.height.toFloat() }) {
                            Text(
                                text = app.displayLabel,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = scheme.onSurface,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .onSizeChanged { nameH.floatValue = it.height.toFloat() }
                                    .graphicsLayer { alpha = 0f },
                            )
                            Column(Modifier.graphicsLayer { alpha = (1f - collapseP() * 2.2f).coerceIn(0f, 1f) }) {
                            if (app.renamed) {
                                Text(
                                    text = stringResource(R.string.original_name_fmt, app.label),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.primary,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = app.pkg,
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(10.dp))
                            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                            androidx.compose.foundation.layout.FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Pill(stringResource(profileLabel(app.profile)))
                                if (app.isSystem) Pill(stringResource(R.string.pill_system))
                                if (app.isModule) Pill(stringResource(R.string.pill_module))
                                if (app.hidden) Pill(stringResource(R.string.pill_hidden))
                            }
                            }
                        }
                    }
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
                    HideScopeCard(vm, app, onToggleHide = requestHide)
                } else {
                    ActionCard(
                        icon = if (app.hidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        title = stringResource(R.string.action_hide),
                        subtitle = stringResource(if (app.hidden) R.string.action_hide_on else R.string.action_hide_off),
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

            if (app.modified || (Caps.hide && vm.hasCustomHideScope(app))) {
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

        // Top bar: opaque strip over the scrolling content, smoothly revealing the small icon and app name on scroll.
        Box(
            Modifier.fillMaxWidth().height(barDp)
                .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                .drawBehind {
                    drawRect(androidx.compose.ui.graphics.lerp(scheme.background, scheme.surfaceContainer, collapseP()))
                },
        ) {
            Box(Modifier.fillMaxSize().padding(top = with(density) { statusPx.toDp() })) {
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back), tint = scheme.onSurface)
                }
            }
        }

        // The icon and the name travel together from the hero card into the top bar (and back when scrolling down).
        if (heroReady) {
            Box(
                Modifier
                    .size(76.dp)
                    .graphicsLayer {
                        val r = iconRectAt(scroll.value)
                        translationX = r.center.x - iconPx / 2f
                        translationY = r.center.y - iconPx / 2f
                        val sc = r.width / iconPx
                        scaleX = sc
                        scaleY = sc
                    },
            ) {
                AppIcon(app, 76.dp, track = IconTrack.DETAIL, dimmed = app.hidden)
            }
            Text(
                text = app.displayLabel,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier
                    .width(with(density) { nameWidthPx.toDp() })
                    .graphicsLayer {
                        val p = collapseP()
                        val sv = scroll.value
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                        val cy = lerpF(nameRestY - sv + nameH.floatValue / 2f, targetCy, p)
                        translationX = lerpF(nameRestX, targetNameX, p)
                        translationY = cy - nameH.floatValue / 2f
                        val sc = lerpF(1f, targetNameScale, p)
                        scaleX = sc
                        scaleY = sc
                    },
            )
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
            onGallery = {
                pendingIconKey = app.key
                pendingIconAll = applyAll
                picker.launch("image/*")
                showIcons = false
            },
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

/** Advanced mode: the hide card grows downward on tap and the per-place switches slide in from the top. */
@Composable
private fun HideScopeCard(vm: HfwViewModel, app: AppItem, onToggleHide: (Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val H = dev.hfw.state.HideScope
    val mask = vm.hideScopeOf(app)
    val custom = vm.hasCustomHideScope(app)
    val on = mask != 0
    val count = Integer.bitCount(mask and H.ALL)
    var expanded by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val arrow by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = androidx.compose.animation.core.tween(260, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "hideArrow",
    )
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
    Surface(shape = RoundedCornerShape(28.dp), color = scheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShapedIcon(
                    icon = if (on) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    background = scheme.primaryContainer,
                    tint = scheme.onPrimaryContainer,
                    size = 52.dp,
                    shape = RoundedCornerShape(18.dp),
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_hide), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = when {
                            !on -> stringResource(R.string.action_hide_off)
                            custom -> stringResource(R.string.scope_tap_custom, count, 7)
                            else -> stringResource(R.string.scope_tap_customize, count, 7)
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Light),
                        color = scheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Switch(checked = on, onCheckedChange = onToggleHide)
            }
            // Arrow on the card's bottom edge: down while closed, up while open.
            Box(Modifier.fillMaxWidth().height(28.dp).clickable { expanded = !expanded }, contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.graphicsLayer { rotationZ = arrow },
                )
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = expanded,
                enter = androidx.compose.animation.expandVertically(
                    animationSpec = androidx.compose.animation.core.tween(260, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                    expandFrom = Alignment.Top,
                ) + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200)),
                exit = androidx.compose.animation.shrinkVertically(
                    animationSpec = androidx.compose.animation.core.tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                    shrinkTowards = Alignment.Top,
                ) + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(140)),
            ) {
                Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                    places.forEachIndexed { i, pl ->
                        val checked = (mask and pl.flag) != 0
                        val reveal by androidx.compose.animation.core.animateFloatAsState(
                            targetValue = if (expanded) 1f else 0f,
                            animationSpec = androidx.compose.animation.core.tween(
                                durationMillis = 240,
                                delayMillis = if (expanded) 60 + i * 40 else 0,
                                easing = androidx.compose.animation.core.FastOutSlowInEasing,
                            ),
                            label = "hideRow$i",
                        )
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    alpha = reveal
                                    translationY = -(1f - reveal) * 14.dp.toPx()
                                }
                                .clip(RoundedCornerShape(18.dp))
                                .clickable { vm.toggleHideScope(app, pl.flag) }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(pl.icon, contentDescription = null, tint = if (checked) scheme.primary else scheme.onSurfaceVariant)
                            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                                Text(stringResource(pl.title), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                Text(stringResource(pl.sub), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                            }
                            Switch(checked = checked, onCheckedChange = { vm.toggleHideScope(app, pl.flag) })
                        }
                    }
                    if (custom) {
                        androidx.compose.material3.TextButton(onClick = { vm.setHideScope(app, null) }) {
                            Text(stringResource(R.string.scope_reset))
                        }
                    }
                }
            }
        }
    }
}
