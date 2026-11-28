package dev.hfw.ui.screens

import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import dev.hfw.ui.components.LocalSegments
import dev.hfw.ui.components.SegmentCounter
import dev.hfw.ui.components.segmentShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected as semanticsSelected
import androidx.compose.ui.semantics.semantics
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ProvideTextStyle
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
    run {
        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 14.dp,
                end = 14.dp,
                top = dev.hfw.ui.components.LocalListTopPadding.current + 6.dp,
                bottom = dev.hfw.ui.components.LocalListBottomPadding.current,
            ),
        ) {
            // ---- appearance ----
            item(key = "appearance") {
                Group(R.string.group_appearance, if (android.os.Build.VERSION.SDK_INT >= 31) 8 else 7) {
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
                    PaletteRow(vm)
                    SwitchRow(
                        icon = Icons.Rounded.Layers,
                        title = stringResource(R.string.set_reduce_motion),
                        subtitle = stringResource(R.string.set_reduce_motion_sub),
                        checked = vm.reduceMotion,
                        onChange = { vm.reduceMotion = it },
                    )
                    ChoiceRow(
                        title = stringResource(R.string.set_navbar),
                        options = listOf(stringResource(R.string.navbar_wide), stringResource(R.string.navbar_compact)),
                        selected = vm.navStyle,
                        onSelect = { vm.navStyle = it },
                    )
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
                // Plain centred footer: versions side by side, the source link below.
                val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                val hookOk = !vm.restartNeeded && (vm.hookVersion == 0 || vm.hookVersion == BuildConfig.VERSION_CODE)
                val hookText = when (vm.hookVersion) {
                    0 -> "—"
                    BuildConfig.VERSION_CODE -> "${BuildConfig.VERSION_NAME} (${vm.hookVersion})"
                    else -> "${vm.hookVersion} ≠ ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
                }
                val muted = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                Column(
                    Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(20.dp)) {
                        Text(
                            "${stringResource(R.string.about_app)} ${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = muted,
                        )
                        Text(
                            "${stringResource(R.string.about_module)} $hookText",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (hookOk) muted else MaterialTheme.colorScheme.error,
                        )
                    }
                    Text(
                        stringResource(R.string.about_source),
                        style = MaterialTheme.typography.bodyMedium,
                        color = muted,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                            .clickable { runCatching { uriHandler.openUri("https://github.com/everyoneexe/HFW") } }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
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
private fun RowCard(onClick: (() -> Unit)? = null, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val segments = LocalSegments.current
    val shape = remember(segments) { segments?.let { segmentShape(it.take(), it.count) } ?: RoundedCornerShape(24.dp) }
    val color = MaterialTheme.colorScheme.surfaceContainerHigh
    if (onClick != null) {
        Surface(onClick = onClick, shape = shape, color = color, modifier = modifier.fillMaxWidth(), content = content)
    } else {
        Surface(shape = shape, color = color, modifier = Modifier.fillMaxWidth().clip(shape).then(modifier), content = content)
    }
}

/** Row with leading/trailing content centered against the whole text block (ListItem pins them to the top). */
@Composable
private fun SettingItem(
    leadingContent: @Composable () -> Unit,
    headlineContent: @Composable () -> Unit,
    supportingContent: (@Composable () -> Unit)? = null,
    trailingContent: @Composable () -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        leadingContent()
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            ProvideTextStyle(MaterialTheme.typography.bodyLarge) { headlineContent() }
            if (supportingContent != null) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                    ProvideTextStyle(MaterialTheme.typography.bodyMedium) { supportingContent() }
                }
            }
        }
        trailingContent()
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
    RowCard(
        modifier = Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
    ) {
        SettingItem(
            leadingContent = { RowIcon(icon) },
            headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
            supportingContent = if (subtitle != null) {
                { Text(subtitle) }
            } else {
                null
            },
            trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
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
        SettingItem(
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
        SettingItem(
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
                                .clickable {
                                    vm.language = mode
                                    if (android.os.Build.VERSION.SDK_INT >= 33) {
                                        val list = when (mode) {
                                            LanguageMode.SYSTEM -> android.os.LocaleList.getEmptyLocaleList()
                                            LanguageMode.TURKISH -> android.os.LocaleList.forLanguageTags("tr")
                                            LanguageMode.ENGLISH -> android.os.LocaleList.forLanguageTags("en")
                                        }
                                        context.getSystemService(android.app.LocaleManager::class.java)?.applicationLocales = list
                                    }
                                    dialog = false
                                }
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

/** Palette picker: a sideways-scrolling row of swatches, the last one opens the custom color dialog. */
@Composable
private fun PaletteRow(vm: HfwViewModel) {
    var showCustom by remember { mutableStateOf(false) }
    val palettesInUse = android.os.Build.VERSION.SDK_INT < 31 || !vm.dynamicColor
    RowCard {
        Column(Modifier.padding(vertical = 12.dp)) {
            Text(stringResource(R.string.set_palette), fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 16.dp))
            Text(
                stringResource(R.string.set_palette_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            androidx.compose.foundation.layout.Spacer(Modifier.height(10.dp))
            androidx.compose.foundation.lazy.LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
            ) {
                items(dev.hfw.ui.theme.Palettes.list.size) { i ->
                    val p = dev.hfw.ui.theme.Palettes.list[i]
                    Swatch(dev.hfw.ui.theme.Palettes.swatch(p), vm.palette == i && palettesInUse, true, stringResource(p.nameRes)) { vm.palette = i; vm.dynamicColor = false }
                }
                item {
                    val custom = dev.hfw.ui.theme.Palettes.fromColor(vm.customColor)
                    Swatch(
                        color = dev.hfw.ui.theme.Palettes.swatch(custom),
                        selected = vm.palette == dev.hfw.ui.theme.Palettes.CUSTOM && palettesInUse,
                        enabled = true,
                        description = stringResource(R.string.set_palette_custom),
                        icon = Icons.Rounded.Edit,
                    ) { showCustom = true }
                }
            }
        }
    }
    if (showCustom) {
        CustomColorDialog(
            initial = vm.customColor,
            onDismiss = { showCustom = false },
            onApply = { vm.customColor = it; vm.palette = dev.hfw.ui.theme.Palettes.CUSTOM; vm.dynamicColor = false; showCustom = false },
        )
    }
}

@Composable
private fun Swatch(
    color: Color,
    selected: Boolean,
    enabled: Boolean,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit,
) {
    androidx.compose.foundation.layout.Box(
        Modifier
            .size(40.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(color)
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, androidx.compose.foundation.shape.CircleShape) else Modifier)
            .semantics {
                contentDescription = description
                semanticsSelected = selected
            }
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected && icon == null) Icon(Icons.Rounded.CheckCircle, null, tint = Color.White, modifier = Modifier.size(20.dp))
        if (icon != null) Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

/** "#RRGGBB" / "RRGGBB" to an opaque ARGB int, or null when it is not a valid color. */
private fun parseHex(text: String): Int? {
    val t = text.trim().removePrefix("#")
    if (t.length != 6 || !t.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
    return (0xFF000000L or t.toLong(16)).toInt()
}

@Composable
private fun CustomColorDialog(initial: Int, onDismiss: () -> Unit, onApply: (Int) -> Unit) {
    val start = remember { dev.hfw.ui.theme.Palettes.fromColor(initial) }
    var hue by remember { mutableStateOf(start.hue) }
    var sat by remember { mutableStateOf((start.sat * 0.75f).coerceIn(0f, 1f)) }
    fun current(): Int = Color.hsl(hue, sat, 0.5f).toArgb()
    var hex by remember { mutableStateOf("%06X".format(initial and 0xFFFFFF)) }
    val valid = parseHex(hex) != null
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.set_palette_custom)) },
        text = {
            Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(56.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(Color(parseHex(hex) ?: current())),
                )
                Text(stringResource(R.string.custom_hue), style = MaterialTheme.typography.labelLarge)
                androidx.compose.material3.Slider(
                    value = hue,
                    onValueChange = { hue = it; hex = "%06X".format(current() and 0xFFFFFF) },
                    valueRange = 0f..360f,
                )
                Text(stringResource(R.string.custom_sat), style = MaterialTheme.typography.labelLarge)
                androidx.compose.material3.Slider(
                    value = sat,
                    onValueChange = { sat = it; hex = "%06X".format(current() and 0xFFFFFF) },
                    valueRange = 0f..1f,
                )
                androidx.compose.material3.OutlinedTextField(
                    value = hex,
                    onValueChange = { v ->
                        hex = v.trim().removePrefix("#").uppercase()
                        parseHex(hex)?.let {
                            val p = dev.hfw.ui.theme.Palettes.fromColor(it)
                            hue = p.hue
                            sat = (p.sat * 0.75f).coerceIn(0f, 1f)
                        }
                    },
                    prefix = { Text("#") },
                    label = { Text(stringResource(R.string.custom_hex)) },
                    isError = !valid,
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(enabled = valid, onClick = { onApply(parseHex(hex) ?: current()) }) {
                Text(stringResource(R.string.custom_apply))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.protected_cancel)) }
        },
    )
}
