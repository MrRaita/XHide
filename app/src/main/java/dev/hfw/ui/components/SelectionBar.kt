package dev.hfw.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hfw.R
import dev.hfw.data.BulkAction
import dev.hfw.data.Caps

/** Replaces the bottom navigation while apps are selected; same pill, one button per bulk action. */
@Composable
fun SelectionBar(onAction: (BulkAction) -> Unit, modifier: Modifier = Modifier, layers: GlassLayers? = null) {
    val scheme = MaterialTheme.colorScheme
    val actions = buildList {
        add(Triple(BulkAction.RESET_ALL, Icons.Rounded.RestartAlt, R.string.sel_reset_all))
        add(Triple(BulkAction.RESET_NAME, Icons.Rounded.Title, R.string.sel_reset_name))
        if (Caps.icon) add(Triple(BulkAction.RESET_ICON, Icons.Rounded.Image, R.string.sel_reset_icon))
        if (Caps.hide) {
            add(Triple(BulkAction.HIDE, Icons.Rounded.VisibilityOff, R.string.sel_hide))
            add(Triple(BulkAction.SHOW, Icons.Rounded.Visibility, R.string.sel_show))
        }
    }
    Row(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(76.dp)
            .navGlass(layers)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        actions.forEach { (action, icon, label) ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(vertical = 6.dp)
                    .clip(CircleShape)
                    .clickable { onAction(action) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(icon, contentDescription = null, tint = scheme.onSurface)
                Text(
                    text = stringResource(label),
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}
