package dev.fadalias.ui.screens

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
import dev.fadalias.R
import dev.fadalias.FadaliasViewModel
import dev.fadalias.data.AppItem
import dev.fadalias.data.AppProfile
import dev.fadalias.data.Caps
import dev.fadalias.data.ModuleState
import dev.fadalias.state.Overlay
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import dev.fadalias.ui.components.AppIcon
import dev.fadalias.ui.components.IconPickerSheet
import dev.fadalias.ui.components.Pill
import dev.fadalias.ui.components.RenameSheet
import dev.fadalias.ui.components.SectionTitle
import dev.fadalias.ui.components.SharedCtx
import dev.fadalias.ui.components.ShapedIcon
import java.util.Date

@Composable
fun DetailScreen(
    vm: FadaliasViewModel,
    itemKey: String,
    shared: SharedCtx,
    onBack: () -> Unit,
    onShowModule: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val app = vm.find(itemKey) ?: return
    var showRename by remember { mutableStateOf(false) }
    var showIcons by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    // warnings: system apps and Fadalias itself. 0 = none, 1 = name pending, 2 = icon pending, 3 = hide (Fadalias only) pending
    val isSelf = app.pkg == dev.fadalias.Identity.PACKAGE
    val riskKind = when {
        isSelf -> dev.fadalias.ui.components.RiskKind.SELF
        app.lookRisky -> dev.fadalias.ui.components.RiskKind.SYSTEM
        else -> null
    }
    val warnSuppressed = when (riskKind) {
        dev.fadalias.ui.components.RiskKind.SELF -> vm.hideSelfWarning
        dev.fadalias.ui.components.RiskKind.SYSTEM -> vm.hideProtectedWarning
        else -> true
    }
    var pendingRisky by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(0) }
    if (pendingRisky != 0 && riskKind != null) {
        dev.fadalias.ui.components.ProtectedDialog(
            kind = if (pendingRisky == 3) dev.fadalias.ui.components.RiskKind.SELF_HIDE else riskKind,
            onCancel = { pendingRisky = 0 },
            onConfirm = { dontShow ->
                if (dontShow) {
                    if (isSelf) vm.hideSelfWarning = true else vm.hideProtectedWarning = true
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
        if (uri != null) vm.setIconFromUri(app, uri)
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

            SectionTitle(stringResource(R.string.detail_info))
            InfoCard(app)
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showRename) {
        RenameSheet(app = app, onSave = { vm.setCustomLabel(app, it) }, onDismiss = { showRename = false })
    }
    if (showIcons) {
        IconPickerSheet(
            app = app,
            onGallery = { picker.launch("image/*"); showIcons = false },
            onReset = { vm.resetIcon(app) },
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
                Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
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
