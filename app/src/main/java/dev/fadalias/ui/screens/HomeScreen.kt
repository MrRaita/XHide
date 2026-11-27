package dev.fadalias.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.fadalias.R
import dev.fadalias.FadaliasViewModel
import dev.fadalias.data.ActivityEntry
import dev.fadalias.data.ActivityKind
import dev.fadalias.data.Caps
import dev.fadalias.data.ModuleState
import dev.fadalias.app.Syncer
import dev.fadalias.ui.components.BentoTile
import dev.fadalias.ui.components.AppIcon
import dev.fadalias.ui.components.Pill
import dev.fadalias.ui.components.SectionTitle
import dev.fadalias.ui.components.ShapedIcon
import dev.fadalias.ui.components.StatusLine
import dev.fadalias.ui.components.cookieShape

@Composable
fun HomeScreen(
    vm: FadaliasViewModel,
    listState: LazyListState,
    onShowModule: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = { Text(text = stringResource(R.string.home_title)) },
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
                top = pad.calculateTopPadding() + 8.dp,
                bottom = pad.calculateBottomPadding() + 120.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "hero") { StatusHero(vm) }
            item(key = "bento") {
                Row(
                    modifier = Modifier.fillMaxWidth().height(if (Caps.hide) 208.dp else 104.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (Caps.hide) {
                        BentoTile(
                            icon = Icons.Rounded.VisibilityOff,
                            value = vm.hiddenCount.toString(),
                            label = stringResource(R.string.tile_hidden),
                            container = MaterialTheme.colorScheme.primaryContainer,
                            content = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            big = true,
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        BentoTile(
                            icon = Icons.Rounded.Edit,
                            value = vm.renamedCount.toString(),
                            label = stringResource(R.string.tile_renamed),
                            container = MaterialTheme.colorScheme.secondaryContainer,
                            content = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                        if (Caps.icon) {
                            BentoTile(
                                icon = Icons.Rounded.Image,
                                value = vm.iconCount.toString(),
                                label = stringResource(R.string.tile_icons),
                                container = MaterialTheme.colorScheme.tertiaryContainer,
                                content = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                            )
                        }
                    }
                }
            }
            if (Caps.icon) item(key = "module") { ModuleTile(vm.moduleState, onClick = onShowModule) }
            item(key = "recentTitle") { SectionTitle(stringResource(R.string.recent_title)) }
            item(key = "recent") {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
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
private fun StatusHero(vm: FadaliasViewModel) {
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
    Surface(shape = RoundedCornerShape(36.dp), color = container, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShapedIcon(
                    icon = if (good) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                    background = if (good) scheme.primary else scheme.tertiary,
                    tint = if (good) scheme.onPrimary else scheme.onTertiary,
                    size = 68.dp,
                    shape = cookieShape(),
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = stringResource(title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = onContainer,
                    )
                    Text(
                        text = stringResource(sub),
                        style = MaterialTheme.typography.bodyMedium,
                        color = onContainer.copy(alpha = 0.8f),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            StatusLine(
                stringResource(R.string.status_hook),
                stringResource(if (online) R.string.status_connected else R.string.status_unreachable),
                online, onContainer,
            )
            StatusLine(stringResource(R.string.status_scope), "system, android", true, onContainer)
            if (online) {
                StatusLine(
                    stringResource(R.string.status_hooks),
                    stringResource(R.string.status_hooks_fmt, vm.hooksOk),
                    vm.hooksProblems == 0, onContainer,
                )
            }
            if (vm.pending > 0) {
                StatusLine(stringResource(R.string.status_pending), vm.pending.toString(), false, onContainer)
            }
        }
    }
}

@Composable
private fun ModuleTile(state: ModuleState, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val verified = state == ModuleState.VERIFIED
    Surface(
        onClick = onClick,
        enabled = !verified,
        shape = RoundedCornerShape(28.dp),
        color = scheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            ShapedIcon(
                icon = Icons.Rounded.Extension,
                background = scheme.secondaryContainer,
                tint = scheme.onSecondaryContainer,
                size = 52.dp,
                shape = RoundedCornerShape(18.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.module_tile_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(
                        when (state) {
                            ModuleState.VERIFIED -> R.string.module_tile_ok
                            ModuleState.MISSING -> R.string.module_tile_missing
                            ModuleState.UNKNOWN -> R.string.module_tile_unknown
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = if (verified) Icons.Rounded.CheckCircle else Icons.Rounded.Download,
                contentDescription = null,
                tint = if (verified) scheme.primary else scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ActivityRow(vm: FadaliasViewModel, entry: ActivityEntry) {
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
