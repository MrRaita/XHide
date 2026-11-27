package dev.fadalias.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.fadalias.data.AppItem

/** A rounded polygon "badge" that holds an icon. */
@Composable
fun ShapedIcon(
    icon: ImageVector,
    background: Color,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    shape: Shape = RoundedCornerShape(16.dp),
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun cookieShape(): Shape = MaterialShapes.Cookie9Sided.toShape()

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 8.dp, top = 20.dp, bottom = 8.dp),
    )
}

/** Large tile used on the Home bento grid. */
@Composable
fun BentoTile(
    icon: ImageVector,
    value: String,
    label: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    big: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(32.dp)
    val inner: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            ShapedIcon(
                icon = icon,
                background = content.copy(alpha = 0.14f),
                tint = content,
                size = 44.dp,
                shape = cookieShape(),
            )
            Spacer(Modifier.height(8.dp))
            Column {
                Text(
                    text = value,
                    style = if (big) MaterialTheme.typography.displayMedium else MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = content,
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = content.copy(alpha = 0.8f),
                )
            }
        }
    }
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = container, contentColor = content) { inner() }
    } else {
        Surface(modifier = modifier, shape = shape, color = container, contentColor = content) { inner() }
    }
}

/** Tiny status icons shown at the end of an app row. */
@Composable
fun StatusBadges(app: AppItem, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (app.renamed) Badge(Icons.Rounded.Edit)
        if (app.iconChanged) Badge(Icons.Rounded.Image)
        if (app.hidden) Badge(Icons.Rounded.VisibilityOff)
    }
}

@Composable
private fun Badge(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(16.dp))
    }
}

/** Small pill with text, e.g. profile or "system". */
@Composable
fun Pill(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** One "label ......... value" status row with a coloured dot. */
@Composable
fun StatusLine(label: String, value: String, ok: Boolean, content: Color, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error),
        )
        Spacer(Modifier.width(12.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = content.copy(alpha = 0.85f), modifier = Modifier.weight(1f))
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = content)
    }
}

@Composable
fun ClickableCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: @Composable () -> Unit,
) {
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = container) {
        content()
    }
}

fun Modifier.clickableRow(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)
