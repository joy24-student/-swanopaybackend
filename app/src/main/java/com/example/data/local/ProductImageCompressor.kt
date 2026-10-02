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
    // Highly compressed budget: 250 KB ceiling, 1080px max edge for crisp Full-HD mobile/web display
    const val MAX_BYTES = 250 * 1024
    const val MAX_EDGE = 1080
    const val MAX_READ_BYTES = 10L * 1024 * 1024

    /**
     * Bounded decoding, full EXIF orientation correction, adaptive multi-stage high compression,
     * strips private metadata, and guarantees output <= MAX_BYTES (250 KB) and <= MAX_EDGE (1080px).
     */
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

            // 1. Check bounds without full allocation
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.path, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= 100000000L) {
                "Choose a supported photo under 100 megapixels."
            }

            // 2. Compute power-of-two inSampleSize to avoid OOM on huge camera shots
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_EDGE * 2) {
                sample *= 2
            }
            val decoded = requireNotNull(
                BitmapFactory.decodeFile(
                    source.path,
                    BitmapFactory.Options().apply {
                        inSampleSize = sample
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                )
            ) { "This photo could not be decoded." }
            bitmap = decoded

            // 3. Normalize EXIF orientation
            val orientation = runCatching {
                ExifInterface(source.path).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

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

            // 4. Initial scale to fit within MAX_EDGE (1080px)
            val maxCurrentDim = maxOf(oriented.width, oriented.height)
            val scale = if (maxCurrentDim > MAX_EDGE) MAX_EDGE.toDouble() / maxCurrentDim else 1.0
            val targetW = maxOf(1, (oriented.width * scale).toInt())
            val targetH = maxOf(1, (oriented.height * scale).toInt())

            var current = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
            Canvas(current).apply {
                drawColor(Color.WHITE)
                drawBitmap(
                    oriented,
                    null,
                    android.graphics.Rect(0, 0, current.width, current.height),
                    android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
                )
            }
            bitmap = current
            if (oriented !== current) oriented.recycle()

            // 5. High-efficiency adaptive compression pipeline
            val encoded = compressBitmapToBudget(current, MAX_BYTES)

            val directory = File(context.filesDir, "product-images").apply { mkdirs() }
            val output = File(directory, "${UUID.randomUUID()}.jpg")
            output.writeBytes(encoded)
            ProductMedia(Uri.fromFile(output).toString(), bytes = encoded.size.toLong())
        } finally {
            bitmap?.takeUnless { it.isRecycled }?.recycle()
            source.delete()
        }
    }

    /**
     * Adaptive compression loop: iterates quality and progressive downscaling to guarantee <= maxBytes.
     */
    fun compressBitmapToBudget(
        initialBitmap: Bitmap,
        maxBytes: Int = MAX_BYTES
    ): ByteArray {
        var current = initialBitmap
        val qualityLadder = intArrayOf(78, 72, 65, 58, 50, 42)
        try {
            while (true) {
                for (q in qualityLadder) {
                    val baos = ByteArrayOutputStream()
                    val ok = current.compress(Bitmap.CompressFormat.JPEG, q, baos)
                    if (ok) {
                        val encoded = baos.toByteArray()
                        if (encoded.size <= maxBytes) {
                            return encoded
                        }
                    }
                }

                // If still > maxBytes, downscale dimensions by 20% and repeat
                val currentMaxDim = maxOf(current.width, current.height)
                if (currentMaxDim <= 360) {
                    val baos = ByteArrayOutputStream()
                    current.compress(Bitmap.CompressFormat.JPEG, 35, baos)
                    return baos.toByteArray()
                }

                val nextW = maxOf(1, (current.width * 0.80).toInt())
                val nextH = maxOf(1, (current.height * 0.80).toInt())
                val scaled = Bitmap.createScaledBitmap(current, nextW, nextH, true)
                if (current !== initialBitmap && !current.isRecycled) {
                    current.recycle()
                }
                current = scaled
            }
        } finally {
            if (current !== initialBitmap && !current.isRecycled) {
                current.recycle()
            }
        }
    }

    /** Compress raw byte array down to budget (e.g. for camera byte buffers or network payloads) */
    fun compressBytes(
        rawBytes: ByteArray,
        maxBytes: Int = MAX_BYTES,
        maxEdge: Int = MAX_EDGE
    ): ByteArray {
        if (rawBytes.isEmpty()) return rawBytes
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return rawBytes

            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxEdge * 2) {
                sample *= 2
            }

            val decoded = BitmapFactory.decodeByteArray(
                rawBytes,
                0,
                rawBytes.size,
                BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
            ) ?: return rawBytes

            val maxDim = maxOf(decoded.width, decoded.height)
            val scale = if (maxDim > maxEdge) maxEdge.toDouble() / maxDim else 1.0
            val targetW = maxOf(1, (decoded.width * scale).toInt())
            val targetH = maxOf(1, (decoded.height * scale).toInt())

            val current = if (targetW != decoded.width || targetH != decoded.height) {
                Bitmap.createScaledBitmap(decoded, targetW, targetH, true).also {
                    if (it !== decoded) decoded.recycle()
                }
            } else {
                decoded
            }

            val compressed = compressBitmapToBudget(current, maxBytes)
            if (!current.isRecycled) current.recycle()
            compressed
        }.getOrDefault(rawBytes)
    }

    fun readUpload(context: Context, reference: String): ByteArray {
        val uri = Uri.parse(reference)
        require(uri.scheme == "file") { "Choose this product image again before syncing." }
        val file = File(requireNotNull(uri.path)).canonicalFile
        val directory = File(context.filesDir, "product-images").canonicalFile
        require(file.parentFile == directory && file.isFile && file.length() in 1..MAX_READ_BYTES) {
            "The saved photo is unavailable. Choose it again."
        }
        val bytes = file.readBytes()
        if (bytes.size > MAX_BYTES) {
            val compressed = compressBytes(bytes, maxBytes = MAX_BYTES, maxEdge = MAX_EDGE)
            runCatching { file.writeBytes(compressed) }
            return compressed
        }
        return bytes
    }
}
