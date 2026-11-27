package dev.hfw.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.hfw.R
import dev.hfw.data.Caps

enum class MainTab { HOME, APPS, SETTINGS }

/**
 * Bottom navigation: one wide, edge-lit pill (translucent on Android 14+) with three equal tabs, icon over label.
 * The selected tab is a dark pill that slides to the new position.
 */
@Composable
fun FloatingNav(tab: MainTab, onTab: (MainTab) -> Unit, modifier: Modifier = Modifier, layers: GlassLayers? = null) {
    val scheme = MaterialTheme.colorScheme
    val tabs = listOf(
        Triple(MainTab.HOME, Icons.Rounded.Home, R.string.nav_home),
        Triple(MainTab.APPS, Icons.Rounded.Apps, R.string.nav_apps),
        Triple(MainTab.SETTINGS, Icons.Rounded.Settings, R.string.nav_settings),
    )
    BoxWithConstraints(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(76.dp)
            .navGlass(layers)
            .padding(6.dp),
    ) {
        val itemWidth = maxWidth / tabs.size
        val pillX by animateDpAsState(
            targetValue = itemWidth * tab.ordinal,
            animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
            label = "navPill",
        )
        Box(
            Modifier
                .offset(x = pillX)
                .width(itemWidth)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(scheme.inverseSurface.copy(alpha = 0.85f)),
        )
        Row(Modifier.fillMaxSize()) {
            tabs.forEach { (t, icon, label) ->
                NavItem(t == tab, icon, stringResource(label), Modifier.weight(1f)) { onTab(t) }
            }
        }
    }
}

@Composable
private fun NavItem(selected: Boolean, icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val fg by animateColorAsState(
        targetValue = if (selected) scheme.inverseOnSurface else scheme.onSurfaceVariant,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "navFg",
    )
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(CircleShape)
            .clickable(role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = fg)
        Text(text = label, color = fg, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}
