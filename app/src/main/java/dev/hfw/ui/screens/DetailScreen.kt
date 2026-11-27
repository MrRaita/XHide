package dev.hfw.ui.screens

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import dev.hfw.ui.components.SharedCtx
import dev.hfw.ui.components.ShapedIcon
import java.util.Date

@Composable
fun DetailScreen(
    vm: HfwViewModel,
    itemKey: String,
    shared: SharedCtx,
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

    Scaffold(
        modifier = modifier,
        containerColor = scheme.background,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = scheme.background),
            )
        },
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = pad.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // header
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                AppIcon(app, 120.dp, shared = shared, dimmed = app.hidden)
                Spacer(Modifier.height(16.dp))
                Text(
                    text = app.displayLabel,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                ActionCard(
                    icon = if (app.hidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    title = stringResource(R.string.action_hide),
                    subtitle = stringResource(if (app.hidden) R.string.action_hide_on else R.string.action_hide_off),
                    onClick = { requestHide(!app.hidden) },
                    trailing = {
                        Switch(checked = app.hidden, onCheckedChange = { requestHide(it) })
                    },
                )
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

            if (Caps.hide && vm.advancedMode && !isSelf) {
                SectionTitle(stringResource(R.string.detail_scope))
                HideScopeCard(vm, app)
            }

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

private fun profileLabel(profile: AppProfile): Int = when (profile) {
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

/** Advanced mode: where this app is hidden, one switch per place. Off-by-default places follow the Settings defaults. */
@Composable
private fun HideScopeCard(vm: HfwViewModel, app: AppItem) {
    val scheme = MaterialTheme.colorScheme
    val mask = vm.hideScopeOf(app)
    val custom = vm.hasCustomHideScope(app)
    val rows = listOf(
        Triple(dev.hfw.state.HideScope.LAUNCHER, R.string.scope_launcher, R.string.scope_launcher_sub),
        Triple(dev.hfw.state.HideScope.SUGGESTIONS, R.string.scope_suggestions, R.string.scope_suggestions_sub),
        Triple(dev.hfw.state.HideScope.SHORTCUT_CONFIGS, R.string.scope_shortcut_configs, R.string.scope_shortcut_configs_sub),
        Triple(dev.hfw.state.HideScope.SHORTCUTS, R.string.scope_shortcuts, R.string.scope_shortcuts_sub),
        Triple(dev.hfw.state.HideScope.INTENT_CHOOSER, R.string.scope_chooser, R.string.scope_chooser_sub),
        Triple(dev.hfw.state.HideScope.SHARE_TARGETS, R.string.scope_share_targets, R.string.scope_share_targets_sub),
        Triple(dev.hfw.state.HideScope.WIDGETS, R.string.scope_widgets, R.string.scope_widgets_sub),
    )
    Surface(shape = RoundedCornerShape(28.dp), color = scheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text(
                text = stringResource(if (custom) R.string.scope_custom_sub else R.string.scope_default_sub),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            rows.forEach { (flag, title, sub) ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(title), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(stringResource(sub), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                    Switch(checked = (mask and flag) != 0, onCheckedChange = { vm.toggleHideScope(app, flag) })
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
