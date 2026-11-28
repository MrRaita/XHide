package dev.hfw.ui.components

import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hfw.R
import dev.hfw.data.BulkAction
import dev.hfw.data.Caps
import kotlin.math.roundToInt

enum class MainTab(val icon: ImageVector, val titleRes: Int) {
    HOME(Icons.Rounded.Home, R.string.nav_home),
    APPS(Icons.Rounded.Apps, R.string.nav_apps),
    SETTINGS(Icons.Rounded.Settings, R.string.nav_settings),
}

/**
 * Wide floating bottom bar: the selected tab shows its icon and label inside a pill, the others only the icon.
 * When the tab changes the selected pill widens and the old one narrows, so the highlight flows from one place to the other.
 */
@Composable
fun ExpandingNavBar(
    tabs: List<MainTab>,
    current: MainTab,
    reduceMotion: Boolean,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        // Narrower than the screen (about 300 dp, centred), the height matches the compact bar.
        modifier = modifier.widthIn(max = 300.dp).fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = cs.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 12.dp,
    ) {
        Row(
            Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                val selected = tab == current
                val itemWeight by animateFloatAsState(
                    targetValue = if (selected) 2.4f else 1f,
                    animationSpec = if (reduceMotion) snap() else spring(dampingRatio = 0.8f, stiffness = 380f),
                    label = "navweight",
                )
                val bg by animateColorAsState(
                    targetValue = if (selected) cs.primaryContainer else Color.Transparent,
                    animationSpec = if (reduceMotion) snap() else tween(220),
                    label = "navbg",
                )
                val fg by animateColorAsState(
                    targetValue = if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant,
                    animationSpec = if (reduceMotion) snap() else tween(220),
                    label = "navfg",
                )
                val title = stringResource(tab.titleRes)
                Box(
                    Modifier
                        .weight(itemWeight)
                        .height(56.dp)
                        .clip(CircleShape)
                        .background(bg)
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(tab) }),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(tab.icon, contentDescription = title, tint = fg)
                        AnimatedVisibility(
                            visible = selected,
                            enter = fadeIn(tween(180)) + expandHorizontally(tween(220)),
                            exit = fadeOut(tween(100)) + shrinkHorizontally(tween(180)),
                        ) {
                            Text(
                                title,
                                color = fg,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Compact floating bar: a narrow pill with icons only; the selected tab's highlight (same colour and shape as the wide bar) slides between them. */
@Composable
fun FloatingNavBar(
    tabs: List<MainTab>,
    current: MainTab,
    reduceMotion: Boolean,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val itemSize = 56.dp
    val gap = 4.dp
    val stepPx = with(LocalDensity.current) { (itemSize + gap).toPx() }
    val index = tabs.indexOf(current).coerceAtLeast(0)
    val x by animateFloatAsState(
        targetValue = index * stepPx,
        animationSpec = if (reduceMotion) snap() else spring(dampingRatio = 0.72f, stiffness = 420f),
        label = "floatx",
    )
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(30.dp),
        color = cs.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 12.dp,
    ) {
        Box(Modifier.padding(8.dp)) {
            Box(
                Modifier
                    .offset { IntOffset(x.roundToInt(), 0) }
                    .size(itemSize)
                    .clip(CircleShape)
                    .background(cs.primaryContainer),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                tabs.forEach { tab ->
                    val selected = tab == current
                    val fg by animateColorAsState(
                        targetValue = if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant,
                        animationSpec = if (reduceMotion) snap() else tween(200),
                        label = "floatfg",
                    )
                    Box(
                        Modifier
                            .size(itemSize)
                            .clip(CircleShape)
                            .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(tab) }),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(tab.icon, contentDescription = stringResource(tab.titleRes), tint = fg)
                    }
                }
            }
        }
    }
}

/** Replaces the bottom bar while apps are selected: same floating pill, one button per bulk action. */
@Composable
fun SelectionBar(onAction: (BulkAction) -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val actions = buildList {
        add(Triple(BulkAction.RESET_ALL, Icons.Rounded.RestartAlt, R.string.sel_reset_all))
        add(Triple(BulkAction.RESET_NAME, Icons.Rounded.Title, R.string.sel_reset_name))
        if (Caps.icon) add(Triple(BulkAction.RESET_ICON, Icons.Rounded.Image, R.string.sel_reset_icon))
        if (Caps.hide) {
            add(Triple(BulkAction.HIDE, Icons.Rounded.VisibilityOff, R.string.sel_hide))
            add(Triple(BulkAction.SHOW, Icons.Rounded.Visibility, R.string.sel_show))
        }
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = cs.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 12.dp,
    ) {
        Row(
            Modifier
                .height(76.dp)
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            actions.forEach { (action, icon, label) ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .clickable { onAction(action) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = cs.onSurface)
                    Text(
                        text = stringResource(label),
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        maxLines = 2,
                        textAlign = TextAlign.Center,
                        color = cs.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
