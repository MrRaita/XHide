package dev.fadalias.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import dev.fadalias.R
import dev.fadalias.FadaliasViewModel
import dev.fadalias.BuildConfig
import dev.fadalias.data.Caps
import dev.fadalias.data.ModuleState
import dev.fadalias.data.IconSize
import dev.fadalias.data.LanguageMode
import dev.fadalias.data.ThemeMode
import dev.fadalias.ui.components.SectionTitle
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    vm: FadaliasViewModel,
    listState: LazyListState,
    onShowModule: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showLog by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    if (showLog) dev.fadalias.ui.components.LogSheet(vm.logLines, vm.logLoading, onRefresh = { vm.loadLog() }, onDismiss = { showLog = false })
    Scaffold(
        modifier = modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
                scrollBehavior = scroll,
            )
        },
    ) { pad ->
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = pad.calculateTopPadding(),
                bottom = pad.calculateBottomPadding() + 120.dp,
            ),
        ) {
            // ---- appearance ----
            item(key = "appearance") {
                Group(R.string.group_appearance) {
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
                    SwitchRow(
                        icon = Icons.Rounded.AutoAwesome,
                        title = stringResource(R.string.set_dynamic),
                        subtitle = stringResource(R.string.set_dynamic_sub),
                        checked = vm.dynamicColor,
                        onChange = { vm.dynamicColor = it },
                    )
                    Divider()
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
                    ChoiceRow(
                        title = stringResource(R.string.set_language),
                        options = listOf(
                            stringResource(R.string.lang_system),
                            "Türkçe",
                            "English",
                        ),
                        selected = vm.language.ordinal,
                        onSelect = { vm.language = LanguageMode.entries[it] },
                    )
                }
            }

            // ---- icons ----
            item(key = "icons") {
                Group(if (Caps.icon) R.string.group_icons else R.string.group_launcher) {
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
                Group(R.string.group_hiding) {
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

            // ---- scope ----
            item(key = "scope") {
                Group(R.string.group_scope) {
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
                Group(R.string.group_diag) {
                    InfoRow(
                        icon = Icons.Rounded.Refresh,
                        title = stringResource(R.string.set_sync),
                        subtitle = stringResource(R.string.set_sync_sub),
                        ok = vm.link == dev.fadalias.app.Syncer.Link.ONLINE && vm.pending == 0,
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

            // ---- about ----
            item(key = "about") {
                Group(R.string.group_about) {
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

@Composable
private fun Group(titleRes: Int, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(titleRes))
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(vertical = 4.dp), content = content)
        }
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 20.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

@Composable
private fun RowIcon(icon: ImageVector) {
    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
    ListItem(
        modifier = Modifier.clickable(enabled = enabled) { onChange(!checked) },
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

@Composable
private fun InfoRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    ok: Boolean,
    onClick: (() -> Unit)? = null,
) {
    ListItem(
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { RowIcon(icon) },
        headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(subtitle) },
        trailingContent = {
            Icon(
                imageVector = if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.Download,
                contentDescription = null,
                tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

@Composable
private fun ChoiceRow(
    title: String,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    footnote: String? = null,
) {
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

@Composable
private fun SliderRow(
    icon: ImageVector,
    title: String,
    valueText: String,
    value: Float,
    onChange: (Float) -> Unit,
) {
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
