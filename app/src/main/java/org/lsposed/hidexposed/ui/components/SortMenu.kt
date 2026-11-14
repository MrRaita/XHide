package org.lsposed.hidexposed.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/**
 * Filter/sort button that expands in place into a rounded panel holding the
 * options (selected one in a soft pill). The panel grows out of the button's
 * top-right corner with a spring.
 */
@Composable
fun <T> SortMenuButton(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    highlighted: Boolean,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var popupVisible by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(expanded) {
        if (expanded) {
            popupVisible = true
            progress.animateTo(
                1f,
                spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)
            )
        } else if (popupVisible) {
            progress.animateTo(0f, tween(160))
            popupVisible = false
        }
    }

    val tint = if (highlighted) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant

    Box(modifier = modifier) {
        Surface(
            modifier = Modifier.size(38.dp),
            shape = RoundedCornerShape(10.dp),
            tonalElevation = 2.dp,
            onClick = { expanded = true }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(id = org.lsposed.hidexposed.R.drawable.ic_sort),
                    contentDescription = title,
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (popupVisible) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset.Zero,
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true)
            ) {
                val screenW = LocalConfiguration.current.screenWidthDp
                val panelWidth: Dp = minOf(236, screenW - 32).dp
                // Corner radius grows from the button's 10dp to 28dp as the panel opens.
                val corner = (10f + 18f * progress.value.coerceIn(0f, 1f)).dp
                Surface(
                    modifier = Modifier
                        .width(panelWidth)
                        .graphicsLayer {
                            val p = progress.value
                            // Top-right corner stays pinned on the button's top-right corner.
                            transformOrigin = TransformOrigin(1f, 0f)
                            val sx0 = 38f / panelWidth.value
                            scaleX = sx0 + (1f - sx0) * p
                            scaleY = 0.14f + 0.86f * p
                            alpha = (p * 4f).coerceIn(0f, 1f)
                        },
                    shape = RoundedCornerShape(corner),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = 8.dp
                ) {
                    Column {
                        // Header is exactly button-sized at the top-right so the icon sits where the button's was.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .padding(start = 20.dp, end = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = title,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                painter = painterResource(id = org.lsposed.hidexposed.R.drawable.ic_sort),
                                contentDescription = null,
                                tint = tint,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)) {
                        options.forEachIndexed { index, (value, label) ->
                            val isSelected = value == selected
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        val a = ((progress.value - 0.2f - index * 0.07f) / 0.4f).coerceIn(0f, 1f)
                                        alpha = a
                                        translationY = (1f - a) * 8.dp.toPx()
                                    }
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.surfaceContainerHighest
                                        else androidx.compose.ui.graphics.Color.Transparent
                                    )
                                    .clickable {
                                        onSelect(value)
                                        expanded = false
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isSelected) {
                                    Icon(
                                        painter = painterResource(id = org.lsposed.hidexposed.R.drawable.ic_check),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                        }
                    }
                }
            }
        }
    }
}
