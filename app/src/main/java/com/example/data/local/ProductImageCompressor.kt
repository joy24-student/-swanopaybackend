package com.example.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

object ProductImageCompressor {
    const val MAX_BYTES = 1024 * 1024
    const val MAX_EDGE = 2048

    /** Bounded decoding, full EXIF orientation, JPEG re-encoding with no metadata. */
    suspend fun import(context: Context, uri: Uri): ProductMedia = withContext(Dispatchers.IO) {
        val source = File.createTempFile("product-import-", ".tmp", context.cacheDir)
        var bitmap: Bitmap? = null
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "The selected photo could not be opened." }
                source.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= 40L * 1024 * 1024) { "Choose a photo smaller than 40 MB." }
                        output.write(buffer, 0, count)
                    }
                }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.path, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= 100000000L) { "Choose a supported photo under 100 megapixels." }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_EDGE * 2) sample *= 2
            val decoded = requireNotNull(BitmapFactory.decodeFile(source.path, BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 })) { "This photo could not be decoded." }
            bitmap = decoded
            val orientation = runCatching { ExifInterface(source.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            val matrix = Matrix().apply {
                when (orientation) {
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                    ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(270f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
                }
            }
            val oriented = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            bitmap = oriented
            if (oriented !== decoded) decoded.recycle()
            val scale = minOf(1.0, MAX_EDGE.toDouble() / maxOf(oriented.width, oriented.height))
            var current = Bitmap.createBitmap(maxOf(1, (oriented.width * scale).toInt()), maxOf(1, (oriented.height * scale).toInt()), Bitmap.Config.ARGB_8888)
            Canvas(current).apply {
                drawColor(Color.WHITE)
                drawBitmap(oriented, null, android.graphics.Rect(0, 0, current.width, current.height), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
            }
            bitmap = current
            oriented.recycle()
            var encoded: ByteArray
            while (true) {
                var quality = 86
                do {
                    encoded = ByteArrayOutputStream().use { output -> check(current.compress(Bitmap.CompressFormat.JPEG, quality, output)); output.toByteArray() }
                    quality -= 8
                } while (encoded.size > MAX_BYTES && quality >= 54)
                if (encoded.size <= MAX_BYTES) break
                require(maxOf(current.width, current.height) > 512) { "The photo could not be compressed. Choose a different image." }
                val smaller = Bitmap.createScaledBitmap(current, maxOf(1, current.width * 3 / 4), maxOf(1, current.height * 3 / 4), true)
                current.recycle(); current = smaller; bitmap = current
            }
            val directory = File(context.filesDir, "product-images").apply { mkdirs() }
            val output = File(directory, "${UUID.randomUUID()}.jpg")
            output.writeBytes(encoded)
            ProductMedia(Uri.fromFile(output).toString(), bytes = encoded.size.toLong())
        } finally {
            bitmap?.takeUnless { it.isRecycled }?.recycle()
            source.delete()
        }
    }

    fun readUpload(context: Context, reference: String): ByteArray {
        val uri = Uri.parse(reference)
        require(uri.scheme == "file") { "Choose this product image again before syncing." }
        val file = File(requireNotNull(uri.path)).canonicalFile
        val directory = File(context.filesDir, "product-images").canonicalFile
        require(file.parentFile == directory && file.isFile && file.length() in 1..MAX_BYTES.toLong()) { "The saved photo is unavailable. Choose it again." }
        return file.readBytes()
    }
}
