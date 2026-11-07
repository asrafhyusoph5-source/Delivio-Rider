package com.example.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min

object ImageUtils {

    private const val MAX_DIMENSION = 1024
    private const val TARGET_MAX_BYTES = 300 * 1024 // 300 KB

    /**
     * Compresses a Bitmap to JPEG format with longest side <= 1024 px,
     * starting at quality 70, ensuring total size is under 300 KB,
     * and returns the base64 data URL string ("data:image/jpeg;base64,...").
     */
    fun compressBitmapToBase64(bitmap: Bitmap, quality: Int = 70): String {
        val scaled = scaleBitmap(bitmap, MAX_DIMENSION)
        var currentQuality = quality
        var byteArray = compressToBytes(scaled, currentQuality)

        // If larger than 300 KB, iteratively lower quality to ensure < 300 KB
        while (byteArray.size > TARGET_MAX_BYTES && currentQuality > 20) {
            currentQuality -= 10
            byteArray = compressToBytes(scaled, currentQuality)
        }

        val base64 = Base64.encodeToString(byteArray, Base64.NO_WRAP)
        return "data:image/jpeg;base64,$base64"
    }

    /**
     * Reads an image Uri, correctly respects EXIF rotation, scales to max 1024px,
     * compresses to JPEG quality 70 (< 300 KB), and returns a Base64 data string.
     */
    fun compressUriToBase64(context: Context, uri: Uri): String? {
        return try {
            val contentResolver = context.contentResolver

            // 1. Decode bounds to avoid OutOfMemory
            val firstInput = contentResolver.openInputStream(uri) ?: return null
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            firstInput.use { BitmapFactory.decodeStream(it, null, options) }

            val origWidth = options.outWidth
            val origHeight = options.outHeight
            if (origWidth <= 0 || origHeight <= 0) return null

            // Calculate sample size
            var sampleSize = 1
            val maxSide = max(origWidth, origHeight)
            while (maxSide / (sampleSize * 2) >= MAX_DIMENSION) {
                sampleSize *= 2
            }

            // 2. Decode sampled bitmap
            val secondInput = contentResolver.openInputStream(uri) ?: return null
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val sampledBitmap = secondInput.use { BitmapFactory.decodeStream(it, null, decodeOptions) } ?: return null

            // 3. Check EXIF orientation
            val rotatedBitmap = try {
                contentResolver.openInputStream(uri)?.use { exifInput ->
                    val exif = ExifInterface(exifInput)
                    val orientation = exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                    rotateBitmap(sampledBitmap, orientation)
                } ?: sampledBitmap
            } catch (_: Exception) {
                sampledBitmap
            }

            compressBitmapToBase64(rotatedBitmap)
        } catch (_: Exception) {
            null
        }
    }

    private fun scaleBitmap(bitmap: Bitmap, maxDim: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val maxSide = max(width, height)
        if (maxSide <= maxDim) return bitmap

        val scale = maxDim.toFloat() / maxSide.toFloat()
        val targetWidth = (width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    private fun compressToBytes(bitmap: Bitmap, quality: Int): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(10, 100), stream)
        return stream.toByteArray()
    }

    private fun rotateBitmap(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return try {
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            rotated
        } catch (_: Exception) {
            bitmap
        }
    }
}
