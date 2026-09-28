package com.example.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Resolves any image reference supported by SwapnoPay (HTTPS URLs, relative backend paths,
 * `data:image/...;base64,...` data URIs, `file://` URIs, `content://` URIs, or raw filesystem paths)
 * into a model object that Coil 2 (`AsyncImage`) can reliably decode and render.
 */
fun resolveCoilImageModel(raw: String?): Any? {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty() || trimmed.equals("null", ignoreCase = true) || trimmed.equals("undefined", ignoreCase = true)) {
        return null
    }

    // 1. Handle inline Base64 data URIs (e.g., "data:image/jpeg;base64,...")
    // Coil 2.7.0 does not have a Fetcher for scheme "data:", but natively supports ByteArray.
    if (trimmed.startsWith("data:image/", ignoreCase = true)) {
        val commaIndex = trimmed.indexOf(',')
        if (commaIndex != -1 && commaIndex + 1 < trimmed.length) {
            val base64Payload = trimmed.substring(commaIndex + 1).trim()
            val decoded = runCatching {
                Base64.decode(base64Payload, Base64.DEFAULT)
            }.getOrNull()
            if (decoded != null && decoded.isNotEmpty()) {
                return decoded
            }
        }
        return null
    }

    // 2. Handle file:// URIs directly as java.io.File
    if (trimmed.startsWith("file://", ignoreCase = true)) {
        val path = runCatching { Uri.parse(trimmed).path }.getOrNull()
        if (!path.isNullOrBlank()) {
            return File(path)
        }
    }

    // 3. Handle relative backend upload paths (e.g. "/uploads/products/..." or "uploads/products/...")
    if (trimmed.startsWith("/uploads/", ignoreCase = true)) {
        return "https://api.swapnopay.top$trimmed"
    }
    if (trimmed.startsWith("uploads/", ignoreCase = true)) {
        return "https://api.swapnopay.top/$trimmed"
    }

    // 4. Handle absolute local filesystem paths (e.g. "/data/user/0/...")
    if (trimmed.startsWith("/")) {
        return File(trimmed)
    }

    // 5. Handle content:// URIs
    if (trimmed.startsWith("content://", ignoreCase = true)) {
        return runCatching { Uri.parse(trimmed) }.getOrDefault(trimmed)
    }

    return trimmed
}

/**
 * Memoizes the resolved Coil image model for a given raw image string/URI so Base64 decoding
 * or URI resolution only runs when [raw] changes.
 */
@Composable
fun rememberResolvedImageModel(raw: String?): Any? {
    return remember(raw) { resolveCoilImageModel(raw) }
}

/**
 * Returns true if [raw] is an inline `data:image/...` URI. Used by URL input fields so they do not
 * render multi-hundred-kilobyte Base64 strings inside single-line text boxes.
 */
fun isInlineDataImageUri(raw: String?): Boolean {
    return raw?.trim()?.startsWith("data:image/", ignoreCase = true) == true
}

/**
 * Compresses raw image [bytes] into a compact JPEG (default max 640px edge, <= 64 KB)
 * and returns a `data:image/jpeg;base64,...` URI safe for SQLite Room columns, JSON state,
 * and instant rendering via [resolveCoilImageModel].
 */
fun encodeCompactDataImageUri(
    bytes: ByteArray,
    maxEdge: Int = 640,
    maxBytes: Int = 65_536
): String {
    val compactBytes = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching bytes
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxEdge * 2) {
            sample *= 2
        }
        val decoded = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: return@runCatching bytes
        val scale = minOf(1.0, maxEdge.toDouble() / maxOf(decoded.width, decoded.height))
        val targetW = maxOf(1, (decoded.width * scale).toInt())
        val targetH = maxOf(1, (decoded.height * scale).toInt())
        val scaled = if (targetW != decoded.width || targetH != decoded.height) {
            Bitmap.createScaledBitmap(decoded, targetW, targetH, true).also {
                if (it !== decoded) decoded.recycle()
            }
        } else {
            decoded
        }
        var quality = 78
        var out: ByteArray
        do {
            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            out = baos.toByteArray()
            quality -= 10
        } while (out.size > maxBytes && quality >= 38)
        scaled.recycle()
        out
    }.getOrDefault(bytes)

    return "data:image/jpeg;base64,${Base64.encodeToString(compactBytes, Base64.NO_WRAP)}"
}
