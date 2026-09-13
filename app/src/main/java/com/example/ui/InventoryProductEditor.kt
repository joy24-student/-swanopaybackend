package com.example.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.local.ProductImageCompressor
import com.example.data.local.ProductItemEntity
import com.example.data.local.ProductMedia
import com.example.data.local.ProductStorefrontDetails
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun InventoryProductEditor(viewModel: AppViewModel, product: ProductItemEntity?, initialCode: String = "", onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val key = product?.id ?: "new"
    var name by rememberSaveable(key) { mutableStateOf(product?.name.orEmpty()) }
    var code by rememberSaveable(key) { mutableStateOf(product?.code ?: initialCode) }
    var unit by rememberSaveable(key) { mutableStateOf(product?.unit ?: "pcs") }
    var purchase by rememberSaveable(key) { mutableStateOf(product?.purchasePrice?.toString() ?: "0") }
    var price by rememberSaveable(key) { mutableStateOf(product?.salePrice?.toString() ?: "") }
    var stock by rememberSaveable(key) { mutableStateOf(product?.stockQuantity?.toString() ?: "0") }
    var detailsJson by rememberSaveable(key) { mutableStateOf(product?.storefrontDetailsJson ?: "{}") }
    val details = remember(detailsJson) { ProductStorefrontDetails.parse(detailsJson) }
    var oldPrice by rememberSaveable(key) { mutableStateOf(details.oldPrice.toString()) }
    var sizes by rememberSaveable(key) { mutableStateOf(details.sizes.joinToString(", ")) }
    var colors by rememberSaveable(key) { mutableStateOf(details.colors.joinToString(", ")) }
    var importing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val busy = importing || saving
    fun change(value: ProductStorefrontDetails) { detailsJson = value.json().toString() }
    val featuredPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            importing = true; error = ""
            try { change(details.copy(featuredImage = ProductImageCompressor.import(context, uri))) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "The photo could not be imported." }
            finally { importing = false }
        }
    }
    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            importing = true; error = ""
            try {
                require(details.gallery.size + uris.size <= 10) { "Choose up to 10 gallery images in total." }
                var updated = details.gallery
                for (uri in uris) {
                    updated = updated + ProductImageCompressor.import(context, uri)
                    change(details.copy(gallery = updated))
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "A gallery photo could not be imported." }
            finally { importing = false }
        }
    }
    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().fillMaxHeight(0.94f).padding(horizontal = 12.dp), shape = MaterialTheme.shapes.extraLarge, tonalElevation = 2.dp) {
            Column {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (product == null) "Add product" else "Edit product", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("Inventory & online store", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onDismiss, enabled = !busy) { Icon(Icons.Outlined.Close, "Close product editor") }
                }
                HorizontalDivider()
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Product basics", style = MaterialTheme.typography.titleMedium)
                    ProductField("Product name *", name, { name = it }, !busy)
                    ProductField("SKU / barcode", code, { code = it }, !busy)
                    ProductField("Unit (pcs, kg, etc.)", unit, { unit = it }, !busy)
                    ProductField("Purchase price (BDT)", purchase, { purchase = it }, !busy, number = true)
                    ProductField("Selling price (BDT) *", price, { price = it }, !busy, number = true)
                    ProductField("Old / list price (BDT)", oldPrice, { oldPrice = it }, !busy, number = true)
                    ProductField(if (product == null) "Opening stock" else "Stock quantity", stock, { stock = it }, !busy, number = true)
                    Text("Website products use whole-number stock quantities.", style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider()
                    Text("Categories & options", style = MaterialTheme.typography.titleMedium)
                    Text("Use existing category names or enter new ones. They are created in your store when you sync.", style = MaterialTheme.typography.bodySmall)
                    ProductField("Top-level category *", details.topCategory, { change(details.copy(topCategory = it)) }, !busy)
                    ProductField("Mid-level category *", details.midCategory, { change(details.copy(midCategory = it)) }, !busy)
                    ProductField("End category *", details.endCategory, { change(details.copy(endCategory = it)) }, !busy)
                    ProductField("Sizes, separated by commas", sizes, { sizes = it }, !busy)
                    ProductField("Colors, separated by commas", colors, { colors = it }, !busy)
                    HorizontalDivider()
                    Text("Product images", style = MaterialTheme.typography.titleMedium)
                    Text("Photos are resized and compressed before upload. Add one featured photo and up to 10 gallery photos.", style = MaterialTheme.typography.bodySmall)
                    details.featuredImage?.let { photo ->
                        ProductPhoto(photo, "Featured image", !busy) { change(details.copy(featuredImage = null)) }
                    }
                    OutlinedButton(onClick = { featuredPicker.launch("image/*") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.AddPhotoAlternate, null); Spacer(Modifier.width(8.dp)); Text(if (details.featuredImage == null) "Add featured photo" else "Replace featured photo")
                    }
                    if (details.gallery.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        itemsIndexed(details.gallery) { index, photo ->
                            Column(Modifier.width(145.dp)) {
                                ProductPhoto(photo, "Gallery photo ${index + 1}", !busy) { change(details.copy(gallery = details.gallery.filterIndexed { i, _ -> i != index })) }
                                if (index > 0) TextButton(enabled = !busy, onClick = {
                                    val ordered = details.gallery.toMutableList(); ordered.add(0, ordered.removeAt(index)); change(details.copy(gallery = ordered))
                                }) { Text("Move to first") }
                            }
                        }
                    }
                    OutlinedButton(onClick = { galleryPicker.launch("image/*") }, enabled = !busy && details.gallery.size < 10, modifier = Modifier.fillMaxWidth()) { Text("Add gallery photos (${details.gallery.size}/10)") }
                    if (importing) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Compressing photos…") }
                    HorizontalDivider()
                    Text("Product information", style = MaterialTheme.typography.titleMedium)
                    ProductField("Short description", details.shortDescription, { change(details.copy(shortDescription = it)) }, !busy, multiline = true)
                    ProductField("Full description", details.description, { change(details.copy(description = it)) }, !busy, multiline = true)
                    ProductField("Features", details.features, { change(details.copy(features = it)) }, !busy, multiline = true)
                    ProductField("Condition", details.condition, { change(details.copy(condition = it)) }, !busy, multiline = true)
                    ProductField("Return policy", details.returnPolicy, { change(details.copy(returnPolicy = it)) }, !busy, multiline = true)
                    ProductField("YouTube video URL", details.videoUrl, { change(details.copy(videoUrl = it)) }, !busy)
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("Featured product", Modifier.weight(1f)); Switch(details.isFeatured, { change(details.copy(isFeatured = it)) }, enabled = !busy) }
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("Visible on website", Modifier.weight(1f)); Switch(details.isActive, { change(details.copy(isActive = it)) }, enabled = !busy) }
                    Text("Your draft is saved on this device. Use Sync inventory on the website screen to publish changes and upload photos.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                }
                HorizontalDivider()
                Button(enabled = !busy, modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 48.dp), onClick = {
                    try {
                        val buy = purchase.toDoubleOrNull(); val sell = price.toDoubleOrNull(); val quantity = stock.toDoubleOrNull(); val old = oldPrice.toDoubleOrNull()
                        require(name.isNotBlank() && name.length <= 255 && !name.contains(Regex("[<>\\p{Cntrl}]"))) { "Enter a product name up to 255 characters." }
                        require(buy != null && sell != null && quantity != null && old != null && listOf(buy, sell, quantity, old).all { it.isFinite() && it >= 0 }) { "Enter valid prices and stock quantities." }
                        require(sell <= 99999999 && quantity <= Int.MAX_VALUE.toDouble()) { "Price or stock exceeds the supported limit." }
                        val finalDetails = details.copy(oldPrice = old, sizes = sizes.split(',').map { it.trim() }.filter { it.isNotBlank() }.distinct(), colors = colors.split(',').map { it.trim() }.filter { it.isNotBlank() }.distinct())
                        finalDetails.validate()
                        saving = true; error = ""
                        val complete: (Boolean, String) -> Unit = { success, message ->
                            saving = false
                            if (success) { Toast.makeText(context, message, Toast.LENGTH_SHORT).show(); onDismiss() } else error = message
                        }
                        if (product == null) viewModel.addProduct(name.trim(), code.trim().ifBlank { null }, finalDetails.endCategory, buy, sell, quantity, unit.trim(), storefront = finalDetails, onResult = complete)
                        else viewModel.updateProduct(product.copy(name = name.trim(), code = code.trim().ifBlank { null }, category = finalDetails.endCategory, purchasePrice = buy, salePrice = sell, stockQuantity = quantity, unit = unit.trim(), imageUrl = finalDetails.featuredImage?.preview, storefrontDetailsJson = finalDetails.json().toString()), complete)
                    } catch (e: Exception) { saving = false; error = e.message ?: "Check your product details." }
                }) { Text(if (saving) "Saving…" else "Save product") }
            }
        }
    }
}

@Composable
private fun ProductField(label: String, value: String, change: (String) -> Unit, enabled: Boolean, number: Boolean = false, multiline: Boolean = false) {
    OutlinedTextField(value, change, Modifier.fillMaxWidth(), enabled = enabled, label = { Text(label) }, singleLine = !multiline, minLines = if (multiline) 3 else 1, keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Decimal else KeyboardType.Text))
}

@Composable
private fun ProductPhoto(photo: ProductMedia, label: String, enabled: Boolean, remove: () -> Unit) {
    Column {
        Box {
            AsyncImage(photo.preview, label, Modifier.fillMaxWidth().height(145.dp), contentScale = ContentScale.Fit)
            IconButton(remove, Modifier.align(Alignment.TopEnd), enabled = enabled) { Icon(Icons.Outlined.Close, "Remove $label") }
        }
        Text(if (photo.bytes > 0) "$label · ${photo.bytes / 1024} KB" else label, style = MaterialTheme.typography.labelSmall)
    }
}
