package dev.hfw.ui.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.hfw.BuildConfig
import dev.hfw.HfwViewModel
import dev.hfw.R
import dev.hfw.app.Syncer
import dev.hfw.data.ActivityEntry
import dev.hfw.data.ActivityKind
import dev.hfw.data.Caps
import dev.hfw.data.ModuleState
import dev.hfw.ui.components.AppIcon
import dev.hfw.ui.components.HfwTopBar
import dev.hfw.ui.components.HookCardBackdrop
import dev.hfw.ui.components.glassSource
import dev.hfw.ui.components.segmentShape
import dev.hfw.ui.components.rememberGlassLayers
import dev.hfw.ui.components.SectionTitle
import dev.hfw.ui.components.ShapedIcon
import dev.hfw.ui.components.cookieShape

@Composable
fun HomeScreen(
    vm: HfwViewModel,
    listState: LazyListState,
    onShowModule: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = true,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val layers = rememberGlassLayers()
    Scaffold(
        modifier = modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { HfwTopBar(stringResource(R.string.home_title), scroll, layers) },
    ) { pad ->
        LazyColumn(
            state = listState,
            modifier = Modifier.glassSource(layers),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = pad.calculateTopPadding() + 8.dp,
                bottom = pad.calculateBottomPadding() + 120.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "hero") { StatusHero(vm, active) }
            item(key = "stats") { StatRow(vm) }

            item(key = "moduleTitle") { SectionTitle(stringResource(R.string.info_module_title)) }
            item(key = "module") { ModuleInfo(vm, onShowModule) }

            item(key = "deviceTitle") { SectionTitle(stringResource(R.string.info_device_title)) }
            item(key = "device") { DeviceInfo() }

            item(key = "recentTitle") { SectionTitle(stringResource(R.string.recent_title)) }
            item(key = "recent") {
                Panel(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        val recent = vm.visibleActivity().take(5)
                        if (recent.isEmpty()) {
                            Text(
                                text = stringResource(R.string.recent_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            )
                        }
                        recent.forEach { entry -> ActivityRow(vm, entry) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusHero(vm: HfwViewModel, active: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val online = vm.link == Syncer.Link.ONLINE
    val good = online && vm.hooksProblems == 0 && !vm.restartNeeded
    val container = if (good) scheme.primaryContainer else scheme.tertiaryContainer
    val onContainer = if (good) scheme.onPrimaryContainer else scheme.onTertiaryContainer
    val title = when {
        vm.link == Syncer.Link.REJECTED -> R.string.hero_rejected
        vm.link != Syncer.Link.ONLINE -> R.string.hero_offline
        vm.restartNeeded -> R.string.hero_restart
        vm.hooksProblems > 0 -> R.string.hero_partial
        else -> R.string.hero_active
    }
    val sub = when {
        vm.link == Syncer.Link.REJECTED -> R.string.hero_rejected_sub
        vm.link != Syncer.Link.ONLINE -> R.string.hero_offline_sub
        vm.restartNeeded -> R.string.hero_restart_sub
        vm.hooksProblems > 0 -> R.string.hero_partial_sub
        !Caps.icon && !Caps.hide -> R.string.hero_active_sub_name
        !Caps.icon -> R.string.hero_active_sub_hide
        else -> R.string.hero_active_sub
    }
    Panel(shape = RoundedCornerShape(28.dp), tint = container, modifier = Modifier.fillMaxWidth()) {
        Box {
            HookCardBackdrop(
                accent = if (good) scheme.primary else scheme.tertiary,
                eyeColor = onContainer.copy(alpha = 0.14f),
                modifier = Modifier.matchParentSize(),
                active = active,
            )
            Row(Modifier.padding(horizontal = 20.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                ShapedIcon(
                    icon = if (good) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                    background = if (good) scheme.primary else scheme.tertiary,
                    tint = if (good) scheme.onPrimary else scheme.onTertiary,
                    size = 56.dp,
                    shape = cookieShape(),
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = stringResource(title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = onContainer,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(sub),
                        style = MaterialTheme.typography.bodyMedium,
                        color = onContainer.copy(alpha = 0.85f),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatRow(vm: HfwViewModel) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatTile(vm.renamedCount, stringResource(R.string.tile_renamed), Modifier.weight(1f))
        if (Caps.icon) StatTile(vm.iconCount, stringResource(R.string.tile_icons), Modifier.weight(1f))
        if (Caps.hide) StatTile(vm.hiddenCount, stringResource(R.string.tile_hidden), Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(value: Int, label: String, modifier: Modifier = Modifier) {
    Panel(shape = RoundedCornerShape(20.dp), modifier = modifier) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = value.toString(), style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

private class InfoItem(val title: String, val value: String, val onClick: (() -> Unit)? = null)

@Composable
private fun ModuleInfo(vm: HfwViewModel, onShowModule: () -> Unit) {
    val online = vm.link == Syncer.Link.ONLINE
    val items = buildList {
        add(
            InfoItem(
                stringResource(R.string.status_hook),
                stringResource(
                    when (vm.link) {
                        Syncer.Link.ONLINE -> R.string.status_connected
                        Syncer.Link.REJECTED -> R.string.hero_rejected
                        else -> R.string.status_unreachable
                    },
                ),
            ),
        )
        add(InfoItem(stringResource(R.string.info_hook_version), if (online && vm.hookVersion != 0) "v${vm.hookVersion}" else "-"))
        add(
            InfoItem(
                stringResource(R.string.status_hooks),
                if (online) stringResource(R.string.info_hooks_value, vm.hooksOk, vm.hooksProblems) else "-",
            ),
        )
        add(InfoItem(stringResource(R.string.status_scope), "system, android"))
        if (Caps.icon) {
            val verified = vm.moduleState == ModuleState.VERIFIED
            add(
                InfoItem(
                    stringResource(R.string.module_tile_title),
                    stringResource(
                        when (vm.moduleState) {
                            ModuleState.VERIFIED -> R.string.module_tile_ok
                            ModuleState.MISSING -> R.string.module_tile_missing
                            ModuleState.UNKNOWN -> R.string.module_tile_unknown
                        },
                    ),
                    onClick = if (verified) null else onShowModule,
                ),
            )
        }
        if (vm.pending > 0) add(InfoItem(stringResource(R.string.status_pending), vm.pending.toString()))
    }
    InfoCard(items)
}

@Composable
private fun DeviceInfo() {
    val features = buildList {
        add(stringResource(R.string.feature_name))
        if (Caps.hide) add(stringResource(R.string.feature_hide))
        if (Caps.icon) add(stringResource(R.string.feature_icon))
    }.joinToString(", ")
    InfoCard(
        listOf(
            InfoItem(stringResource(R.string.info_model), "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"),
            InfoItem(stringResource(R.string.info_system), stringResource(R.string.info_system_fmt, Build.VERSION.RELEASE, Build.VERSION.SDK_INT)),
            InfoItem(stringResource(R.string.info_app_version), "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"),
            InfoItem(stringResource(R.string.info_features), features),
        ),
    )
}

/** One small box per fact, cut from one block: big outer corners, small corners between neighbours. */
@Composable
private fun InfoCard(items: List<InfoItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        items.forEachIndexed { index, item ->
            val click = item.onClick
            Panel(
                shape = segmentShape(index, items.size),
                modifier = Modifier.fillMaxWidth().then(if (click != null) Modifier.clickable(onClick = click) else Modifier),
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text(text = item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = item.value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Plain opaque card. The glass look is kept for the top bar only. */
@Composable
private fun Panel(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(24.dp),
    tint: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: @Composable () -> Unit,
) {
    Surface(shape = shape, color = tint, modifier = modifier, content = content)
}

@Composable
private fun ActivityRow(vm: HfwViewModel, entry: ActivityEntry) {
    val app = vm.apps.firstOrNull { it.pkg == entry.pkg }
    val minutes = ((System.currentTimeMillis() - entry.at) / 60_000L).coerceAtLeast(0).toInt()
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (app != null) AppIcon(app, 40.dp) else Spacer(Modifier.width(40.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(
                    when (entry.kind) {
                        ActivityKind.HIDDEN -> R.string.activity_hidden
                        ActivityKind.UNHIDDEN -> R.string.activity_unhidden
                        ActivityKind.RENAMED -> R.string.activity_renamed
                        ActivityKind.ICON_CHANGED -> R.string.activity_icon
                        ActivityKind.RESET -> R.string.activity_reset
                    },
                    entry.label,
                ),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
            )
            Text(
                text = if (minutes < 1) {
                    stringResource(R.string.time_now)
                } else if (minutes < 60) {
                    stringResource(R.string.time_minutes_fmt, minutes)
                } else if (minutes < 1440) {
                    stringResource(R.string.time_hours_fmt, minutes / 60)
                } else {
                    stringResource(R.string.time_days_fmt, minutes / 1440)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
