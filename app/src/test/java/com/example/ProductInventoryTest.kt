package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProductInventoryTest {
    @Test
    fun `all website fields survive a local save and image updates preserve stock`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val details = ProductStorefrontDetails(oldPrice = 200.0, topCategory = "Clothing", midCategory = "Adult", endCategory = "Shirts", description = "Cotton", shortDescription = "Soft shirt", features = "Washable", condition = "New", returnPolicy = "Seven days", videoUrl = "https://youtu.be/abcdefghijk", sizes = listOf("M", "L"), colors = listOf("Blue"), featuredImage = ProductMedia("file:///draft.jpg"), gallery = listOf(ProductMedia("file:///gallery.jpg")), isFeatured = true, isActive = false)
            details.validate()
            val dao = db.appDao()
            dao.insertProduct(ProductItemEntity("p1", "merchant-a", "Shirt", stockQuantity = 8.0, storefrontDetailsJson = details.json().toString()))
            val saved = requireNotNull(dao.getProductById("p1", "merchant-a"))
            assertEquals(details, ProductStorefrontDetails.parse(saved.storefrontDetailsJson))
            assertEquals(0, dao.updateProductMedia("p1", "merchant-b", null, "{}", saved.storefrontDetailsJson))
            assertEquals(0, dao.updateProductMedia("p1", "merchant-a", null, "{}", "stale draft"))
            assertEquals(1, dao.updateProductMedia("p1", "merchant-a", "https://store.example/photo.jpg", "{}", saved.storefrontDetailsJson))
            assertEquals(8.0, requireNotNull(dao.getProductById("p1", "merchant-a")).stockQuantity, 0.0)
            assertThrows(IllegalArgumentException::class.java) { details.copy(oldPrice = Double.NaN).validate() }
            assertThrows(IllegalArgumentException::class.java) { details.copy(videoUrl = "javascript:alert(1)").validate() }
        } finally { db.close() }
    }

    @Test
    fun `photo compression bounds bytes and dimensions and removes EXIF`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = File(context.cacheDir, "large-product.jpg")
        val bitmap = Bitmap.createBitmap(3000, 1600, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.BLUE)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 98, it) }
        bitmap.recycle()
        ExifInterface(source.path).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            setAttribute(ExifInterface.TAG_MAKE, "Private camera metadata")
            saveAttributes()
        }
        try {
            val image = ProductImageCompressor.import(context, Uri.fromFile(source))
            val file = File(requireNotNull(Uri.parse(image.reference).path))
            try {
                assertTrue(file.length() in 1..ProductImageCompressor.MAX_BYTES.toLong())
                val dimensions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, dimensions)
                assertTrue(dimensions.outHeight > dimensions.outWidth)
                assertTrue(maxOf(dimensions.outWidth, dimensions.outHeight) <= ProductImageCompressor.MAX_EDGE)
                assertNull(ExifInterface(file.path).getAttribute(ExifInterface.TAG_MAKE))
                assertEquals(file.length(), ProductImageCompressor.readUpload(context, image.reference).size.toLong())
                assertThrows(IllegalArgumentException::class.java) { ProductImageCompressor.readUpload(context, Uri.fromFile(source).toString()) }
            } finally { file.delete() }
        } finally { source.delete() }
    }
}
