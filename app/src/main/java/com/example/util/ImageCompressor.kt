package com.example.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File

object ImageCompressor {
    const val MAX_IMAGE_CHARS = 400000
    const val MAX_LONGEST_SIDE = 1024
    const val INITIAL_JPEG_QUALITY = 60
    const val TARGET_BYTES = 250 * 1024
    const val MAX_ALLOWED_BYTES = 350 * 1024

    /**
     * Resizes the bitmap so its longest side is at most 1024 px.
     * Compresses to JPEG with starting quality 60, targeting 250 KB or less.
     * If still over 350 KB, reduces quality again.
     * Encodes as JPEG data URI string: "data:image/jpeg;base64,...", ensuring max 400,000 characters.
     */
    fun compressBitmapToDataUri(original: Bitmap): String {
        // 1. Resize longest side to max 1024 px
        val width = original.width
        val height = original.height
        val maxDim = 1024
        val scaledBitmap = if (width > maxDim || height > maxDim) {
            val ratio = if (width >= height) {
                maxDim.toFloat() / width
            } else {
                maxDim.toFloat() / height
            }
            val targetW = (width * ratio).toInt().coerceAtLeast(1)
            val targetH = (height * ratio).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(original, targetW, targetH, true)
        } else {
            original
        }

        // 2. Compress JPEG starting at quality 60
        var quality = 60
        var outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        var bytes = outputStream.toByteArray()

        // Target <= 250 KB (256,000 bytes). If still over 350 KB (358,400 bytes), reduce quality again
        val targetBytes = 250 * 1024
        val maxAllowedBytes = 350 * 1024
        while (bytes.size > maxAllowedBytes && quality > 15) {
            quality -= 15
            outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            bytes = outputStream.toByteArray()
        }

        // If between 250 KB and 350 KB, step down once more if possible to hit the target <= 250 KB
        if (bytes.size > targetBytes && quality > 25) {
            val nextQuality = quality - 10
            val trialStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, nextQuality, trialStream)
            val trialBytes = trialStream.toByteArray()
            if (trialBytes.size <= maxAllowedBytes) {
                quality = nextQuality
                bytes = trialBytes
            }
        }

        // 3. Base64 encode and format as data URI
        var base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        var dataUri = "data:image/jpeg;base64,$base64"

        // Contract: max 400,000 characters
        while (dataUri.length > 400000 && quality > 10) {
            quality -= 10
            outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            bytes = outputStream.toByteArray()
            base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            dataUri = "data:image/jpeg;base64,$base64"
        }

        return dataUri
    }

    /**
     * Decode an image file from disk and compress to Data URI.
     */
    fun compressFileToDataUri(file: File): String? {
        if (!file.exists() || file.length() == 0L) return null
        return try {
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, boundsOptions)

            var inSampleSize = 1
            val maxSide = maxOf(boundsOptions.outWidth, boundsOptions.outHeight)
            while (maxSide / (inSampleSize * 2) >= 1024) {
                inSampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
            }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOptions) ?: return null
            compressBitmapToDataUri(bitmap)
        } catch (_: Exception) {
            null
        }
    }
}
