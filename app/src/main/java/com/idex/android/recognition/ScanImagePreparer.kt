package com.idex.android.recognition

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlin.math.min

/**
 * Image preparation shared by camera, photo import and sample scans.
 * Every path produces an opaque, centered square (white behind transparency),
 * matching the iOS ScanImagePreparer.
 */
object ScanImagePreparer {
    const val MAXIMUM_SIDE = 1200
    private const val MAX_DECODE_PIXELS = 4_000_000L

    /** Decode a picked photo with bounded memory, honour EXIF rotation, then center-crop. */
    fun photo(resolver: ContentResolver, uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IllegalStateException("Image unavailable")

        val sample = computeInSampleSize(bounds.outWidth, bounds.outHeight, MAXIMUM_SIDE, MAX_DECODE_PIXELS)
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IllegalStateException("Image unavailable")

        var rotated: Bitmap? = null
        return try {
            val orientation = runCatching {
                resolver.openInputStream(uri)?.use {
                    ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                }
            }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
            rotated = applyOrientation(decoded, orientation)
            centerSquare(rotated, MAXIMUM_SIDE)
        } finally {
            if (rotated != null && rotated !== decoded) {
                rotated.runCatching { recycle() }
            }
            decoded.runCatching { recycle() }
        }
    }

    /** Compute power-of-two subsampling respecting both short side and total pixel budget. */
    internal fun computeInSampleSize(
        width: Int,
        height: Int,
        targetShortSide: Int = MAXIMUM_SIDE,
        maxPixels: Long = MAX_DECODE_PIXELS,
    ): Int {
        if (width <= 0 || height <= 0) return 1
        var sample = 1
        val shortSide = min(width, height).toLong()
        while (shortSide / (sample.toLong() * 2L) >= targetShortSide) {
            sample *= 2
        }
        val w = width.toLong()
        val h = height.toLong()
        while ((w / sample) * (h / sample) > maxPixels && sample < 1024) {
            sample *= 2
        }
        return sample
    }

    /** Center-crop to an opaque square no larger than [maxSide]. */
    fun centerSquare(source: Bitmap, maxSide: Int = MAXIMUM_SIDE): Bitmap {
        val shortSide = min(source.width, source.height).coerceAtLeast(1)
        val side = min(maxSide, shortSide)
        val out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        val left = (source.width - shortSide) / 2
        val top = (source.height - shortSide) / 2
        canvas.drawBitmap(
            source,
            Rect(left, top, left + shortSide, top + shortSide),
            RectF(0f, 0f, side.toFloat(), side.toFloat()),
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG),
        )
        return out
    }

    /** Composite a transparent game sprite on white at 384px, nearest-neighbour (sample target). */
    fun opaqueSprite(sprite: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(384, 384, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(sprite, Rect(0, 0, sprite.width, sprite.height), RectF(0f, 0f, 384f, 384f), Paint())
        return out
    }

    private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
    }
}
