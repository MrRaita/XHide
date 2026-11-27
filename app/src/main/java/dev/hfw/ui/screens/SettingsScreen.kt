package dev.hfw.ui.screens

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import dev.hfw.ui.components.LocalSegments
import dev.hfw.ui.components.SegmentCounter
import dev.hfw.ui.components.segmentShape
import androidx.compose.foundation.BorderStroke
import dev.hfw.ui.components.HfwTopBar
import dev.hfw.ui.components.glassSource
import dev.hfw.ui.components.rememberGlassLayers
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.hfw.R
import dev.hfw.HfwViewModel
import dev.hfw.BuildConfig
import dev.hfw.data.Caps
import dev.hfw.data.ModuleState
import dev.hfw.data.IconSize
import dev.hfw.data.LanguageMode
import dev.hfw.data.ThemeMode
import dev.hfw.ui.components.SectionTitle
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    vm: HfwViewModel,
    listState: LazyListState,
    onShowModule: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val layers = rememberGlassLayers()
    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> if (uri != null) vm.exportBackup(uri) }
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) vm.importBackup(uri) }
    var confirmImport by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    if (confirmImport) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmImport = false },
            title = { Text(stringResource(R.string.backup_import_title)) },
            text = { Text(stringResource(R.string.backup_import_body)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirmImport = false
                    importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed"))
                }) { Text(stringResource(R.string.backup_import_ok)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmImport = false }) { Text(stringResource(R.string.protected_cancel)) }
            },
        )
    }
    var showLog by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    if (showLog) dev.hfw.ui.components.LogSheet(vm.logLines, vm.logLoading, onRefresh = { vm.loadLog() }, onDismiss = { showLog = false })
    Scaffold(
        modifier = modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { HfwTopBar(stringResource(R.string.settings_title), scroll, layers) },
    ) { pad ->
        LazyColumn(
            state = listState,
            modifier = Modifier.glassSource(layers),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = pad.calculateTopPadding(),
                bottom = pad.calculateBottomPadding() + 120.dp,
            ),
        ) {
            // ---- appearance ----
            item(key = "appearance") {
                Group(R.string.group_appearance, if (android.os.Build.VERSION.SDK_INT >= 31) 5 else 4) {
                    ChoiceRow(
                        title = stringResource(R.string.set_theme),
                        options = listOf(
                            stringResource(R.string.theme_system),
                            stringResource(R.string.theme_light),
                            stringResource(R.string.theme_dark),
                        ),
                        selected = vm.themeMode.ordinal,
                        onSelect = { vm.themeMode = ThemeMode.entries[it] },
                    )
                    Divider()
                    if (android.os.Build.VERSION.SDK_INT >= 31) {
                    SwitchRow(
                        icon = Icons.Rounded.AutoAwesome,
                        title = stringResource(R.string.set_dynamic),
                        subtitle = stringResource(R.string.set_dynamic_sub),
                        checked = vm.dynamicColor,
                        onChange = { vm.dynamicColor = it },
                    )
                    }
                    SwitchRow(
                        icon = Icons.Rounded.DarkMode,
                        title = stringResource(R.string.set_amoled),
                        subtitle = stringResource(R.string.set_amoled_sub),
                        checked = vm.amoled,
                        onChange = { vm.amoled = it },
                    )
                    Divider()
                    SliderRow(
                        icon = Icons.Rounded.FormatSize,
                        title = stringResource(R.string.set_scale),
                        valueText = "${(vm.uiScale * 100).roundToInt()}%",
                        value = vm.uiScale,
                        onChange = { vm.uiScale = (it * 20).roundToInt() / 20f },
                    )
                    Divider()
                    LanguageRow(vm)
                }
            }

            // ---- icons ----
            item(key = "icons") {
                Group(if (Caps.icon) R.string.group_icons else R.string.group_launcher, if (Caps.icon) 2 else 1) {
                    if (Caps.icon) {
                        ChoiceRow(
                            title = stringResource(R.string.set_icon_size),
                            options = IconSize.entries.map { "${it.px} px" },
                            selected = vm.iconSize.ordinal,
                            onSelect = { vm.iconSize = IconSize.entries[it] },
                        )
                        Divider()
                    }
                    InfoRow(
                        icon = Icons.Rounded.Refresh,
                        title = stringResource(R.string.set_refresh),
                        subtitle = stringResource(R.string.set_refresh_sub),
                        ok = true,
                        onClick = { vm.refreshLaunchers() },
                    )
                }
            }

            // ---- hiding ----
            if (Caps.hide) item(key = "hiding") {
                Group(R.string.group_hiding, 2) {
                    SwitchRow(
                        icon = Icons.Rounded.Extension,
                        title = stringResource(R.string.set_auto_modules),
                        subtitle = stringResource(R.string.set_auto_modules_sub),
                        checked = vm.autoHideModules,
                        onChange = { vm.autoHideModules = it },
                    )
                    Divider()
                    SwitchRow(
                        icon = Icons.Rounded.Layers,
                        title = stringResource(R.string.set_auto_clones),
                        subtitle = stringResource(R.string.set_auto_clones_sub),
                        checked = vm.autoHideClones,
                        onChange = { vm.autoHideClones = it },
                    )
                }
            }

            // ---- advanced (hide scope) ----
            if (Caps.hide) item(key = "advanced") {
                Group(R.string.group_advanced, if (vm.advancedMode) 6 else 1) {
                    SwitchRow(
                        icon = Icons.Rounded.Tune,
                        title = stringResource(R.string.set_advanced),
                        subtitle = stringResource(R.string.set_advanced_sub),
                        checked = vm.advancedMode,
                        onChange = { vm.advancedMode = it },
                    )
                    if (vm.advancedMode) {
                        SwitchRow(Icons.Rounded.Extension, stringResource(R.string.scope_chooser), stringResource(R.string.scope_global_sub), vm.hideIntentChooser, { vm.hideIntentChooser = it })
                        SwitchRow(Icons.Rounded.Extension, stringResource(R.string.scope_shortcuts), stringResource(R.string.scope_global_sub), vm.hideShortcuts, { vm.hideShortcuts = it })
                        SwitchRow(Icons.Rounded.Extension, stringResource(R.string.scope_shortcut_configs), stringResource(R.string.scope_global_sub), vm.hideShortcutConfigs, { vm.hideShortcutConfigs = it })
                        SwitchRow(Icons.Rounded.Extension, stringResource(R.string.scope_share_targets), stringResource(R.string.scope_global_sub), vm.hideShareTargets, { vm.hideShareTargets = it })
                        SwitchRow(Icons.Rounded.Extension, stringResource(R.string.scope_widgets), stringResource(R.string.scope_global_sub), vm.hideWidgets, { vm.hideWidgets = it })
                    }
                }
            }

            // ---- scope ----
            item(key = "scope") {
                Group(R.string.group_scope, if (Caps.icon) 3 else 2) {
                    InfoRow(Icons.Rounded.Tune, "system", stringResource(R.string.scope_system_sub), true)
                    Divider()
                    InfoRow(Icons.Rounded.Tune, "android", stringResource(R.string.scope_android_sub), true)
                    if (Caps.icon) Divider()
                    if (Caps.icon) InfoRow(
                        icon = Icons.Rounded.Security,
                        title = stringResource(R.string.status_selinux),
                        subtitle = stringResource(
                            when (vm.moduleState) {
                                ModuleState.VERIFIED -> R.string.status_installed
                                ModuleState.MISSING -> R.string.status_missing
                                ModuleState.UNKNOWN -> R.string.status_unverified
                            },
                        ),
                        ok = vm.moduleState == ModuleState.VERIFIED,
                        onClick = if (vm.moduleState == ModuleState.VERIFIED) null else onShowModule,
                    )
                }
            }

            // ---- diagnostics ----
            item(key = "diagnostics") {
                Group(R.string.group_diag, 2) {
                    InfoRow(
                        icon = Icons.Rounded.Refresh,
                        title = stringResource(R.string.set_sync),
                        subtitle = stringResource(R.string.set_sync_sub),
                        ok = vm.link == dev.hfw.app.Syncer.Link.ONLINE && vm.pending == 0,
                        onClick = { vm.refresh() },
                    )
                    Divider()
                    InfoRow(
                        icon = Icons.Rounded.Info,
                        title = stringResource(R.string.set_log),
                        subtitle = stringResource(R.string.set_log_sub),
                        ok = true,
                        onClick = { vm.loadLog(); showLog = true },
                    )
                }
            }

            // ---- backup ----
            item(key = "backup") {
                Group(R.string.group_backup, 2) {
                    InfoRow(
                        icon = Icons.Rounded.Upload,
                        title = stringResource(R.string.backup_export),
                        subtitle = stringResource(R.string.backup_export_sub),
                        ok = true,
                        onClick = { exportLauncher.launch("${dev.hfw.Identity.APP_NAME}-backup.zip") },
                        chevron = true,
                    )
                    InfoRow(
                        icon = Icons.Rounded.Download,
                        title = stringResource(R.string.backup_import),
                        subtitle = stringResource(R.string.backup_import_sub),
                        ok = true,
                        onClick = { confirmImport = true },
                        chevron = true,
                    )
                }
            }

            // ---- about ----
            item(key = "about") {
                Group(R.string.group_about, 2) {
                    InfoRow(Icons.Rounded.Info, stringResource(R.string.about_version), BuildConfig.VERSION_NAME, true)
                    Divider()
                    InfoRow(Icons.Rounded.Security, stringResource(R.string.about_hook), if (vm.hookVersion == 0) "—" else vm.hookVersion.toString(), !vm.restartNeeded)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------
// building blocks

/** A titled stack of small boxes, one per setting, cut from one block (see [segmentShape]). [count] = number of rows. */
@Composable
private fun Group(titleRes: Int, count: Int, content: @Composable ColumnScope.() -> Unit) {
    val segments = remember(count) { SegmentCounter(count) }
    Column(Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(titleRes))
        CompositionLocalProvider(LocalSegments provides segments) {
            Column(
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(3.dp),
                content = content,
            )
        }
    }
}

/** Rows used to be separated by hairlines; every row is its own box now, so nothing is drawn. */
@Composable
private fun Divider() {}

/** One setting: a box whose corners come from its position in the group. */
@Composable
private fun RowCard(onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val segments = LocalSegments.current
    val shape = remember(segments) { segments?.let { segmentShape(it.take(), it.count) } ?: RoundedCornerShape(24.dp) }
    val color = MaterialTheme.colorScheme.surfaceContainerHigh
    if (onClick != null) {
        Surface(onClick = onClick, shape = shape, color = color, modifier = Modifier.fillMaxWidth(), content = content)
    } else {
        Surface(shape = shape, color = color, modifier = Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
private fun RowIcon(icon: ImageVector) {
    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    RowCard(onClick = if (enabled) { { onChange(!checked) } } else null) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = { RowIcon(icon) },
            headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
            supportingContent = if (subtitle != null) {
                { Text(subtitle) }
            } else {
                null
            },
            trailingContent = { Switch(checked = checked, onCheckedChange = onChange, enabled = enabled) },
        )
    }
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    ok: Boolean,
    onClick: (() -> Unit)? = null,
    chevron: Boolean = false,
) {
    RowCard(onClick = onClick) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = { RowIcon(icon) },
            headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
            supportingContent = { Text(subtitle) },
            trailingContent = {
                if (chevron) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) else Icon(
                    imageVector = if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.Download,
                    contentDescription = null,
                    tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
    }
}

@Composable
private fun ChoiceRow(
    title: String,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    footnote: String? = null,
) {
    RowCard {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                options.forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = index == selected,
                        onClick = { onSelect(index) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    ) { Text(label, maxLines = 1) }
                }
            }
            if (footnote != null) {
                Text(
                    text = footnote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun SliderRow(
    icon: ImageVector,
    title: String,
    valueText: String,
    value: Float,
    onChange: (Float) -> Unit,
) {
    RowCard {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                RowIcon(icon)
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f).padding(start = 16.dp),
                )
                Text(text = valueText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = value,
                onValueChange = onChange,
                valueRange = 0.8f..1.3f,
                steps = 9,
            )
        }
    }
}

/**
 * Language: one tile. Android 13+ hands over to the system's per-app language screen (the app declares its languages in
 * `locales_config.xml`); Android 12 and below pick the language inside the app.
 */
@Composable
private fun LanguageRow(vm: HfwViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var dialog by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val system = stringResource(R.string.lang_system)
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val current = if (android.os.Build.VERSION.SDK_INT >= 33) {
        locale.getDisplayLanguage(locale).replaceFirstChar { c -> c.titlecase(locale) }
    } else {
        when (vm.language) {
            LanguageMode.SYSTEM -> system
            LanguageMode.TURKISH -> "T\u00fcrk\u00e7e"
            LanguageMode.ENGLISH -> "English"
        }
    }
    RowCard(onClick = {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            try {
                context.startActivity(
                    android.content.Intent(
                        android.provider.Settings.ACTION_APP_LOCALE_SETTINGS,
                        android.net.Uri.fromParts("package", context.packageName, null),
                    ),
                )
            } catch (_: Throwable) {
                dialog = true
            }
        } else {
            dialog = true
        }
    }) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = { RowIcon(Icons.Rounded.Language) },
            headlineContent = { Text(stringResource(R.string.set_language), fontWeight = FontWeight.Medium) },
            supportingContent = { Text(current) },
            trailingContent = {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
        )
    }
    if (dialog) {
        val names = listOf(system, "T\u00fcrk\u00e7e", "English")
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { dialog = false },
            title = { Text(stringResource(R.string.set_language)) },
            text = {
                Column {
                    LanguageMode.entries.forEachIndexed { index, mode ->
                        androidx.compose.foundation.layout.Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { vm.language = mode; dialog = false }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            androidx.compose.material3.RadioButton(selected = vm.language == mode, onClick = null)
                            Text(names[index], modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { dialog = false }) { Text(stringResource(R.string.protected_cancel)) }
            },
        )
    }
}
