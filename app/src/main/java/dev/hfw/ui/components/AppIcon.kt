package dev.hfw.ui.components

import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import dev.hfw.app.IconCodec
import dev.hfw.app.Services
import dev.hfw.data.AppItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private object IconCache {
    val cache = LruCache<String, ImageBitmap>(96)
    val blurCache = LruCache<String, ImageBitmap>(24)
}

/**
 * Produces a soft, GPU-independent blurred backdrop from the app's icon so every
 * Android version and device renders the exact same gradient-like blur.
 */
@Composable
fun rememberBlurredAppIcon(app: AppItem): ImageBitmap? {
    val ctx = LocalContext.current
    val sv = remember { Services.get(ctx) }
    val key = "${app.key}|${app.iconRev}|${app.iconToken}|blur"
    var bmp by remember(app.key) { mutableStateOf(IconCache.blurCache.get(key)) }
    LaunchedEffect(key) {
        val hit = IconCache.blurCache.get(key)
        if (hit != null) { bmp = hit; return@LaunchedEffect }
        val fresh = withContext(Dispatchers.IO) {
            val custom = if (app.iconRev > 0) {
                (sv.store.iconBytes(app.pkg, app.userId) ?: sv.store.iconBytes(app.pkg))
                    ?.takeIf { it.second == app.iconRev }?.first?.let { IconCodec.decodePng(it) }
            } else null
            val src = custom ?: sv.catalog.original(app.key, 64) ?: return@withContext null
            // Multi-step bilinear downscale + upscale yields a smooth, deterministic blur on all API levels.
            val s1 = android.graphics.Bitmap.createScaledBitmap(src, 16, 16, true)
            val s2 = android.graphics.Bitmap.createScaledBitmap(s1, 6, 6, true)
            val s3 = android.graphics.Bitmap.createScaledBitmap(s2, 32, 32, true)
            if (s1 !== src) s1.recycle()
            s2.recycle()
            s3.asImageBitmap()
        }
        if (fresh != null) { IconCache.blurCache.put(key, fresh); bmp = fresh }
    }
    return bmp
}

/** The app's real launcher icon, or the custom icon the user chose (shown from the manager's own copy of the PNG). */
@Composable
fun AppIcon(
    app: AppItem,
    size: Dp,
    modifier: Modifier = Modifier,
    track: IconTrack = IconTrack.NONE,
    dimmed: Boolean = false,
) {
    val ctx = LocalContext.current
    val sv = remember { Services.get(ctx) }
    val px = with(LocalDensity.current) { size.roundToPx() }.coerceIn(48, 256)
    val cacheKey = "${app.key}|${app.iconRev}|${app.iconToken}|$px"
    // keyed by the item only: the previous bitmap stays visible until the new one is ready (no flash, no stale letter)
    var bmp by remember(app.key) { mutableStateOf(IconCache.cache.get(cacheKey)) }
    LaunchedEffect(cacheKey) {
        val hit = IconCache.cache.get(cacheKey)
        if (hit != null) { bmp = hit; return@LaunchedEffect }
        val fresh = withContext(Dispatchers.IO) {
            val custom = if (app.iconRev > 0) (sv.store.iconBytes(app.pkg, app.userId) ?: sv.store.iconBytes(app.pkg))?.takeIf { it.second == app.iconRev }?.first?.let { IconCodec.decodePng(it) } else null
            (custom ?: sv.catalog.original(app.key, px))?.asImageBitmap()
        }
        if (fresh != null) { IconCache.cache.put(cacheKey, fresh); bmp = fresh }
    }
    val shape = SystemIconShape
    Box(
        modifier = modifier
            .flightTrack(app.key, track)
            .size(size)
            .alpha(if (dimmed) 0.45f else 1f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b != null) {
            Image(bitmap = b, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            val fontSize = with(LocalDensity.current) { (size * 0.46f).toSp() }
            Text(
                text = app.label.take(1).uppercase(),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.Bold,
                fontSize = fontSize,
            )
        }
    }
}
