package dev.fadalias.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dev.fadalias.state.Limits
import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Turns a user-picked image into the PNG the hook accepts (square, bounded size) and decodes stored PNGs safely. */
object IconCodec {
    /** Decodes with a bounded size so a huge photo cannot exhaust the manager's memory. */
    fun decodeSampled(open: () -> InputStream?, maxSide: Int = 1024): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) null
        else {
            var sample = 1
            while (bounds.outWidth / sample > maxSide || bounds.outHeight / sample > maxSide) sample *= 2
            open()?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
        }
    } catch (_: Throwable) {
        null
    }

    /** Centre-crops to a square and scales to [px]; falls back to smaller sizes until the PNG fits the hook's limit. */
    fun encodePng(src: Bitmap, px: Int): ByteArray? {
        val side = minOf(src.width, src.height)
        if (side <= 0) return null
        val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        try {
            for (size in listOf(px, 384, 256, 192, 128).filter { it <= px.coerceAtMost(Limits.MAX_ICON_PX) }.distinct()) {
                val scaled = Bitmap.createScaledBitmap(square, size, size, true)
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
                if (scaled !== square) scaled.recycle()
                if (out.size() <= Limits.MAX_ICON_BYTES) return out.toByteArray()
            }
            return null
        } finally {
            if (square !== src) square.recycle()
        }
    }

    fun decodePng(bytes: ByteArray): Bitmap? = try {
        val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, b)
        if (b.outWidth <= 0 || b.outWidth > Limits.MAX_ICON_PX || b.outHeight <= 0 || b.outHeight > Limits.MAX_ICON_PX) null
        else BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (_: Throwable) {
        null
    }
}
