package com.example.data.local

import org.json.JSONArray
import org.json.JSONObject

data class ProductMedia(val reference: String, val url: String = "", val bytes: Long = 0) {
    val preview: String get() = url.ifBlank { reference }
    fun json() = JSONObject().put("reference", reference).put("url", url).put("bytes", bytes)
}

/** Persisted with inventory so an offline save retains the complete website draft. */
data class ProductStorefrontDetails(
    val oldPrice: Double = 0.0,
    val topCategory: String = "Shop",
    val midCategory: String = "All products",
    val endCategory: String = "General",
    val description: String = "",
    val shortDescription: String = "",
    val features: String = "",
    val condition: String = "",
    val returnPolicy: String = "",
    val videoUrl: String = "",
    val sizes: List<String> = emptyList(),
    val colors: List<String> = emptyList(),
    val featuredImage: ProductMedia? = null,
    val gallery: List<ProductMedia> = emptyList(),
    val isFeatured: Boolean = false,
    val isActive: Boolean = true
) {
    fun validate() {
        require(oldPrice.isFinite() && oldPrice in 0.0..99999999.0) { "Enter a valid old/list price." }
        require(listOf(topCategory, midCategory, endCategory).all { it.isNotBlank() && it.length <= 100 && !it.contains(Regex("[<>\\p{Cntrl}]")) }) { "Enter all three category names, up to 100 characters each." }
        require(listOf(description, shortDescription, features, condition, returnPolicy).all { it.length <= 20000 }) { "Keep each product text field within 20,000 characters." }
        require(sizes.size <= 50 && colors.size <= 50 && (sizes + colors).all { it.isNotBlank() && it.length <= 100 && !it.contains(Regex("[<>\\p{Cntrl}]")) }) { "Use up to 50 sizes or colors, with plain text names." }
        require(gallery.size <= 10) { "Choose up to 10 gallery images." }
        if (videoUrl.isNotBlank()) {
            val uri = runCatching { java.net.URI(videoUrl) }.getOrNull()
            require(videoUrl.length <= 255 && uri?.scheme == "https" && uri.host in listOf("youtube.com", "www.youtube.com", "youtu.be")) { "Enter an HTTPS YouTube video URL." }
        }
    }

    fun webJson() = JSONObject().apply {
        put("old_price", oldPrice); put("top_category", topCategory.trim()); put("mid_category", midCategory.trim()); put("end_category", endCategory.trim())
        put("description", description); put("short_description", shortDescription); put("features", features)
        put("condition", condition); put("return_policy", returnPolicy); put("video_url", videoUrl.trim())
        put("sizes", JSONArray(sizes)); put("colors", JSONArray(colors))
        put("is_featured", isFeatured); put("is_active", isActive)
        put("featured_image", featuredImage?.reference.orEmpty()); put("gallery", JSONArray(gallery.map { it.reference }))
    }
    fun json() = webJson().apply {
        put("featured_image", featuredImage?.json() ?: JSONObject.NULL)
        put("gallery", JSONArray().apply { gallery.forEach { put(it.json()) } })
    }

    companion object {
        fun parse(raw: String): ProductStorefrontDetails {
            val obj = runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
            fun strings(key: String): List<String> = obj.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } } ?: emptyList()
            fun media(value: JSONObject?) = value?.optString("reference")?.takeIf { it.isNotBlank() }?.let { ProductMedia(it, value.optString("url"), value.optLong("bytes")) }
            val photos = obj.optJSONArray("gallery") ?: JSONArray()
            return ProductStorefrontDetails(
                oldPrice = obj.optDouble("old_price", 0.0), topCategory = obj.optString("top_category", "Shop"), midCategory = obj.optString("mid_category", "All products"), endCategory = obj.optString("end_category", "General"),
                description = obj.optString("description"), shortDescription = obj.optString("short_description"), features = obj.optString("features"), condition = obj.optString("condition"), returnPolicy = obj.optString("return_policy"), videoUrl = obj.optString("video_url"),
                sizes = strings("sizes"), colors = strings("colors"), featuredImage = media(obj.optJSONObject("featured_image")), gallery = (0 until photos.length()).mapNotNull { media(photos.optJSONObject(it)) },
                isFeatured = obj.optBoolean("is_featured", false), isActive = obj.optBoolean("is_active", true)
            )
        }
    }
}
