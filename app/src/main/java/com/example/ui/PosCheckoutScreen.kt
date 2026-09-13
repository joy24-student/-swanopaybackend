@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONObject


@Composable
fun PosCheckoutScreen(viewModel: AppViewModel) {
    val posCart by viewModel.posCart.collectAsState()
    val customers by viewModel.customers.collectAsState()
    val allProducts by viewModel.products.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    SideEffect { isDarkModeGlobal = isDarkMode }

    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var checkoutPaymentType by remember { mutableStateOf("Cash") } // "Cash", "MFS", "Card", "CustomerCredit"
    var selectedCustomerId by remember { mutableStateOf<String?>(null) }
    var selectedCustomerName by remember { mutableStateOf("Walk-in Customer") }
    var selectedCustomerPhone by remember { mutableStateOf("") }
    var showCustomerDialog by remember { mutableStateOf(false) }
    var showAddNewCustomerDialog by remember { mutableStateOf(false) }
    var showDiscountDialog by remember { mutableStateOf(false) }

    // State for Add New Customer Dialog
    var newCustName by remember { mutableStateOf("") }
    var newCustPhone by remember { mutableStateOf("") }
    var newCustAddress by remember { mutableStateOf("") }
    var newCustOpeningBal by remember { mutableStateOf("") }

    var completedOrderSummary by remember { mutableStateOf<JSONObject?>(null) }

    val categories = remember(allProducts) {
        val set = mutableListOf("All")
        allProducts.forEach { p ->
            val cat = p.category?.trim()
            if (!cat.isNullOrBlank() && !set.contains(cat)) {
                set.add(cat)
            }
        }
        set
    }

    val filteredProducts = remember(allProducts, searchQuery, selectedCategory) {
        allProducts.filter { prod ->
            val matchesCategory = selectedCategory == "All" || prod.category.equals(selectedCategory, ignoreCase = true)
            val query = searchQuery.trim()
            val matchesQuery = query.isBlank() ||
                prod.name.contains(query, ignoreCase = true) ||
                (prod.code?.contains(query, ignoreCase = true) == true) ||
                (prod.qrCode?.contains(query, ignoreCase = true) == true) ||
                (prod.category?.contains(query, ignoreCase = true) == true)
            matchesCategory && matchesQuery
        }
    }

    // Dynamic color theme definitions matching pixel-perfect white mood image
    val bgCanvas = if (isDarkMode) Color(0xFF090806) else Color(0xFFFFFFFF)
    val cardBg = if (isDarkMode) Color(0xFF13100C) else Color(0xFFFFFFFF)
    val cardBorder = if (isDarkMode) Color(0xFF2C2213) else Color(0xFFE2E8F0)
    val primaryText = if (isDarkMode) Color.White else Color(0xFF0F172A)
    val secondaryText = if (isDarkMode) Color(0xFF9CA3AF) else Color(0xFF64748B)
    val headerLabelColor = if (isDarkMode) Color(0xFFE5A93C) else Color(0xFF64748B)

    val yellowPrimary = if (isDarkMode) Color(0xFFE5A93C) else Color(0xFFFFC800)
    val yellowLightBg = if (isDarkMode) Color(0xFF2B200E) else Color(0xFFFFFBEB)
    val stepperMinusBg = if (isDarkMode) Color(0xFF261F13) else Color(0xFFF8FAFC)
    val discountRed = Color(0xFFDC2626)

    val cartDisplayList = posCart

    val totalItemCount = cartDisplayList.sumOf { it.quantity.toInt() }
    val calculatedSubtotal = cartDisplayList.sumOf { it.sellingPrice * it.quantity }
    var discountAmount by remember { mutableStateOf(0.0) }
    val calculatedTotal = (calculatedSubtotal - discountAmount).coerceAtLeast(0.0)

    androidx.activity.compose.BackHandler {
        viewModel.navigateTo("Dashboard")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgCanvas)
    ) {
        Scaffold(
            containerColor = bgCanvas,
            topBar = {
                GradientTopBar(
                    title = "New Sale",
                    subtitle = "Create new sale and add items",
                    onBack = { viewModel.navigateTo("Dashboard") },
                    actions = {
                        TopHeaderActionPill(
                            text = "Items ($totalItemCount)",
                            icon = Icons.Outlined.ShoppingCart,
                            onClick = { viewModel.navigateTo("SalesHistory") }
                        )
                        TopHeaderActionPill(
                            text = "History",
                            icon = Icons.Outlined.History,
                            onClick = { viewModel.navigateTo("Sales") }
                        )
                    }
                )
            },
            bottomBar = {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = cardBg,
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, cardBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "TOTAL AMOUNT",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = secondaryText,
                                letterSpacing = 1.sp
                            )
                            Text(
                                "৳ ${String.format("%,.2f", calculatedTotal)}",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = primaryText
                            )
                        }

                        Button(
                            onClick = {
                                viewModel.checkoutPosCart(
                                    paymentType = checkoutPaymentType,
                                    customerId = selectedCustomerId,
                                    discount = discountAmount,
                                    onSuccess = { orderId, totalAmt ->
                                        val summaryObj = JSONObject().apply {
                                            put("orderId", orderId)
                                            put("totalAmount", totalAmt)
                                            put("paymentType", checkoutPaymentType)
                                            put("itemCount", totalItemCount)
                                            put("discount", discountAmount)
                                        }
                                        completedOrderSummary = summaryObj
                                    },
                                    onError = { err ->
                                        viewModel.logFirebaseStatus("POS Checkout error: $err")
                                    }
                                )
                            },
                            modifier = Modifier.height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = yellowPrimary),
                            contentPadding = PaddingValues(horizontal = 20.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("Confirm Sale", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)
            ) {

                // ── 1. SEARCH & SCAN INPUT ──────────────────────────────────────
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "SEARCH & SCAN INPUT",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = headerLabelColor,
                            letterSpacing = 1.2.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Enter SKU / Product Name / Barcode", fontSize = 12.sp, color = secondaryText) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = secondaryText) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = cardBg,
                                    unfocusedContainerColor = cardBg,
                                    focusedBorderColor = yellowPrimary,
                                    unfocusedBorderColor = cardBorder,
                                    focusedTextColor = primaryText,
                                    unfocusedTextColor = primaryText
                                ),
                                singleLine = true
                            )

                            Button(
                                onClick = { viewModel.navigateTo("QrScanner") },
                                modifier = Modifier.height(52.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = yellowPrimary),
                                contentPadding = PaddingValues(horizontal = 16.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                    Text("Scan QR", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                }
                            }
                        }
                    }
                }

                // ── 2. PRODUCT CATALOG & QUICK ADD ─────────────────────────────
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "PRODUCT CATALOG (${filteredProducts.size})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = headerLabelColor,
                                letterSpacing = 1.2.sp
                            )
                            Row(
                                modifier = Modifier.clickable { viewModel.navigateTo("Inventory") },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.AddCircle, null, tint = yellowPrimary, modifier = Modifier.size(14.dp))
                                Text("+ New Product", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = yellowPrimary)
                            }
                        }

                        // Category Filter Chips
                        if (categories.size > 1) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(categories) { cat ->
                                    val isCatSelected = selectedCategory == cat
                                    Surface(
                                        modifier = Modifier.clickable { selectedCategory = cat },
                                        shape = RoundedCornerShape(20.dp),
                                        color = if (isCatSelected) yellowPrimary else cardBg,
                                        border = BorderStroke(1.dp, if (isCatSelected) yellowPrimary else cardBorder)
                                    ) {
                                        Text(
                                            text = cat,
                                            fontSize = 12.sp,
                                            fontWeight = if (isCatSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isCatSelected) Color.Black else secondaryText,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Products List / Cards
                        if (filteredProducts.isEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = cardBg),
                                border = BorderStroke(1.dp, cardBorder)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        if (allProducts.isEmpty()) "ইনভেন্টরিতে কোনো পণ্য নেই (No products in inventory)" else "কোনো পণ্য পাওয়া যায়নি (No matching products)",
                                        fontSize = 13.sp,
                                        color = secondaryText,
                                        textAlign = TextAlign.Center
                                    )
                                    Button(
                                        onClick = { viewModel.navigateTo("Inventory") },
                                        colors = ButtonDefaults.buttonColors(containerColor = yellowPrimary),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("পণ্য যোগ করুন (Add Product)", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        } else {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = cardBg),
                                border = BorderStroke(1.dp, cardBorder)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    filteredProducts.take(15).forEachIndexed { idx, prod ->
                                        if (idx > 0) HorizontalDivider(color = cardBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
                                        val inCartItem = posCart.find { it.productId == prod.id }
                                        val stockInt = prod.stockQuantity.toInt()
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Avatar
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(yellowLightBg),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    prod.name.take(1).uppercase(),
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = yellowPrimary
                                                )
                                            }

                                            // Name & SKU & Stock
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    prod.name,
                                                    fontSize = 13.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = primaryText,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text("৳ ${String.format("%,.0f", prod.salePrice)}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = yellowPrimary)
                                                    Text("•", fontSize = 10.sp, color = secondaryText)
                                                    Text(
                                                        if (stockInt <= 0) "Stock Out" else "Stock: $stockInt ${prod.unit}",
                                                        fontSize = 11.sp,
                                                        color = if (stockInt <= 0) discountRed else if (stockInt <= 5) Color(0xFFF59E0B) else Color(0xFF10B981)
                                                    )
                                                }
                                            }

                                            // Cart action button
                                            if (inCartItem != null) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(28.dp)
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(stepperMinusBg)
                                                            .border(BorderStroke(1.dp, cardBorder), RoundedCornerShape(6.dp))
                                                            .clickable {
                                                                viewModel.updatePosCartItemQuantity(inCartItem.variantId, inCartItem.quantity - 1.0)
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(Icons.Default.Remove, contentDescription = null, tint = primaryText, modifier = Modifier.size(14.dp))
                                                    }
                                                    Text(
                                                        "${inCartItem.quantity.toInt()}",
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = primaryText
                                                    )
                                                    Box(
                                                        modifier = Modifier
                                                            .size(28.dp)
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(yellowPrimary)
                                                            .clickable {
                                                                viewModel.updatePosCartItemQuantity(inCartItem.variantId, inCartItem.quantity + 1.0)
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                                                    }
                                                }
                                            } else {
                                                Button(
                                                    onClick = { viewModel.addProductToPosCart(prod, 1.0) },
                                                    shape = RoundedCornerShape(8.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = yellowPrimary),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                    modifier = Modifier.height(32.dp)
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                                                        Text("Add", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── 3. CART ITEMS ──────────────────────────────────────────────
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "CART ITEMS ($totalItemCount)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = headerLabelColor,
                                letterSpacing = 1.2.sp
                            )
                            if (cartDisplayList.isNotEmpty()) {
                                Text(
                                    "Clear All",
                                    fontSize = 12.sp,
                                    color = discountRed,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clickable { viewModel.clearPosCart() }
                                )
                            }
                        }

                        if (cartDisplayList.isEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = cardBg),
                                border = BorderStroke(1.dp, cardBorder)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Outlined.ShoppingCart, contentDescription = null, tint = secondaryText, modifier = Modifier.size(36.dp))
                                    Text("Your cart is empty", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = primaryText)
                                    Text("Tap '+ Add' on products above or scan barcodes to begin sale", fontSize = 12.sp, color = secondaryText, textAlign = TextAlign.Center)
                                }
                            }
                        } else {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = cardBg),
                                border = BorderStroke(1.dp, cardBorder)
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    cartDisplayList.forEachIndexed { index, item ->
                                        if (index > 0) {
                                            HorizontalDivider(color = cardBorder)
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Avatar
                                            Box(
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(if (isDarkMode) Color(0xFF1E1911) else Color(0xFFF1F5F9))
                                                    .border(BorderStroke(1.dp, cardBorder), RoundedCornerShape(10.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    item.productName.take(1).uppercase(),
                                                    fontSize = 18.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = yellowPrimary
                                                )
                                            }

                                            // Column Details + Stepper
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = item.productName,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = primaryText
                                                )
                                                Text(
                                                    text = item.variantName,
                                                    fontSize = 12.sp,
                                                    color = secondaryText
                                                )
                                                Spacer(modifier = Modifier.height(6.dp))

                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "৳ ${String.format("%,.2f", item.sellingPrice)}",
                                                        fontSize = 13.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = primaryText
                                                    )

                                                    // Stepper
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(28.dp)
                                                                .clip(RoundedCornerShape(6.dp))
                                                                .background(stepperMinusBg)
                                                                .border(BorderStroke(1.dp, cardBorder), RoundedCornerShape(6.dp))
                                                                .clickable {
                                                                    if (posCart.isNotEmpty()) {
                                                                        viewModel.updatePosCartItemQuantity(item.variantId, item.quantity - 1.0)
                                                                    }
                                                                },
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(Icons.Default.Remove, contentDescription = null, tint = primaryText, modifier = Modifier.size(14.dp))
                                                        }

                                                        Text(
                                                            text = "${item.quantity.toInt()}",
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = primaryText
                                                        )

                                                        Box(
                                                            modifier = Modifier
                                                                .size(28.dp)
                                                                .clip(RoundedCornerShape(6.dp))
                                                                .background(yellowLightBg)
                                                                .border(BorderStroke(1.dp, yellowPrimary), RoundedCornerShape(6.dp))
                                                                .clickable {
                                                                    if (posCart.isNotEmpty()) {
                                                                        viewModel.updatePosCartItemQuantity(item.variantId, item.quantity + 1.0)
                                                                    }
                                                                },
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text("+", color = if (isDarkMode) yellowPrimary else Color(0xFFB45309), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                                        }
                                                    }

                                                    Text(
                                                        text = "= ৳ ${String.format("%,.2f", item.sellingPrice * item.quantity)}",
                                                        fontSize = 13.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = primaryText
                                                    )

                                                    Icon(
                                                        Icons.Default.DeleteOutline,
                                                        contentDescription = "Delete",
                                                        tint = secondaryText,
                                                        modifier = Modifier
                                                            .size(20.dp)
                                                            .clickable {
                                                                if (posCart.isNotEmpty()) {
                                                                    viewModel.updatePosCartItemQuantity(item.variantId, 0.0)
                                                                }
                                                            }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── 4. CUSTOMER ASSIGNMENT ────────────────────────────────────
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "CUSTOMER ASSIGNMENT",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = headerLabelColor,
                            letterSpacing = 1.2.sp
                        )

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = cardBg),
                            border = BorderStroke(1.dp, cardBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(if (isDarkMode) Color(0xFF231B0E) else Color(0xFF475569)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                                    }

                                    Column {
                                        Text(
                                            text = selectedCustomerName,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = primaryText
                                        )
                                        if (selectedCustomerPhone.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = selectedCustomerPhone,
                                                fontSize = 12.sp,
                                                color = secondaryText
                                            )
                                        }
                                    }
                                }

                                Surface(
                                    modifier = Modifier.clickable { showCustomerDialog = true },
                                    shape = RoundedCornerShape(8.dp),
                                    color = cardBg,
                                    border = BorderStroke(1.dp, yellowPrimary)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text("Change / Add", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = primaryText)
                                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = primaryText, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // ── 5. PAYMENT METHOD ──────────────────────────────────────────
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "PAYMENT METHOD",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = headerLabelColor,
                            letterSpacing = 1.2.sp
                        )

                        val paymentMethods = listOf(
                            Triple("Cash", "Cash\n(নগদ)", Icons.Default.Money),
                            Triple("MFS", "Mobile /\nMFS", Icons.Default.Smartphone),
                            Triple("Card", "Card\n(কার্ড)", Icons.Default.CreditCard),
                            Triple("Due", "Customer\nDue (বাকি)", Icons.Default.AccountBalance)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            paymentMethods.forEach { (typeKey, label, icon) ->
                                val isSelected = checkoutPaymentType == typeKey
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(84.dp)
                                        .clickable { checkoutPaymentType = typeKey },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) yellowLightBg else cardBg
                                    ),
                                    border = BorderStroke(1.5.dp, if (isSelected) yellowPrimary else cardBorder)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                tint = primaryText,
                                                modifier = Modifier.size(22.dp)
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = label,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = primaryText,
                                                textAlign = TextAlign.Center,
                                                lineHeight = 13.sp
                                            )
                                        }

                                        if (isSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .size(16.dp)
                                                    .clip(CircleShape)
                                                    .background(yellowPrimary),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(12.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── 6. ORDER SUMMARY & CALCULATIONS ────────────────────────────
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "ORDER SUMMARY & CALCULATIONS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = headerLabelColor,
                            letterSpacing = 1.2.sp
                        )

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = cardBg),
                            border = BorderStroke(1.dp, cardBorder)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Subtotal", fontSize = 13.sp, color = secondaryText)
                                    Text("৳ ${String.format("%,.2f", calculatedSubtotal)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = primaryText)
                                }

                                HorizontalDivider(color = cardBorder)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Discount (Coupon)", fontSize = 13.sp, color = secondaryText)
                                    Row(
                                        modifier = Modifier.clickable { showDiscountDialog = true },
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text("- ৳ ${String.format("%.2f", discountAmount)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = discountRed)
                                        Icon(Icons.Default.Edit, contentDescription = "Edit Discount", tint = discountRed, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Customer Selection Dialog
        if (showCustomerDialog) {
            var customerSearch by remember { mutableStateOf("") }
            val filteredCustomers = remember(customers, customerSearch) {
                if (customerSearch.isBlank()) customers else {
                    customers.filter {
                        it.name.contains(customerSearch, ignoreCase = true) ||
                        it.phone.contains(customerSearch)
                    }
                }
            }

            EnterpriseGestureModal(
                onDismissRequest = { showCustomerDialog = false },
                title = "Select or Add Customer",
                subtitle = "কাস্টমার নির্বাচন বা নতুন তৈরি করুন",
                icon = Icons.Default.Person
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Search customer
                    OutlinedTextField(
                        value = customerSearch,
                        onValueChange = { customerSearch = it },
                        placeholder = { Text("Search by name or phone...", fontSize = 12.sp, color = secondaryText) },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = secondaryText) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = yellowPrimary,
                            unfocusedBorderColor = cardBorder,
                            focusedTextColor = primaryText,
                            unfocusedTextColor = primaryText
                        )
                    )

                    // Quick Actions: Walk-in vs Add New
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clickable {
                                    selectedCustomerId = null
                                    selectedCustomerName = "Walk-in Customer"
                                    selectedCustomerPhone = ""
                                    showCustomerDialog = false
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = cardBg,
                            border = BorderStroke(1.dp, if (selectedCustomerId == null) yellowPrimary else cardBorder)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Storefront, null, tint = primaryText, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Walk-in", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = primaryText)
                            }
                        }

                        Button(
                            onClick = { showAddNewCustomerDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = yellowPrimary)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.PersonAdd, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("+ New", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                            }
                        }
                    }

                    HorizontalDivider(color = cardBorder)

                    Text("EXISTING CUSTOMERS (${filteredCustomers.size})", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = secondaryText)

                    if (filteredCustomers.isEmpty()) {
                        Text("No customers found. Click '+ New' above to create one.", fontSize = 12.sp, color = secondaryText, modifier = Modifier.padding(vertical = 12.dp))
                    } else {
                        filteredCustomers.forEach { cust ->
                            val isSelected = selectedCustomerId == cust.id
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedCustomerId = cust.id
                                        selectedCustomerName = cust.name
                                        selectedCustomerPhone = cust.phone
                                        showCustomerDialog = false
                                    },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) yellowLightBg else cardBg,
                                border = BorderStroke(1.dp, if (isSelected) yellowPrimary else cardBorder)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(cust.name, color = primaryText, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                                        if (cust.phone.isNotBlank()) {
                                            Text(cust.phone, color = secondaryText, fontSize = 11.sp)
                                        }
                                    }
                                    if (cust.currentBalance != 0.0) {
                                        Text(
                                            "Due: ৳${String.format("%,.0f", cust.currentBalance)}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (cust.currentBalance > 0) discountRed else Color(0xFF10B981)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Add New Customer Modal
        if (showAddNewCustomerDialog) {
            EnterpriseGestureModal(
                onDismissRequest = { showAddNewCustomerDialog = false },
                title = "নতুন কাস্টমার যোগ করুন",
                subtitle = "Add New Customer Profile",
                icon = Icons.Default.PersonAdd
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = newCustName,
                        onValueChange = { newCustName = it },
                        label = { Text("Customer Name (নাম)*") },
                        placeholder = { Text("e.g. Rahim Ahmed") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = yellowPrimary,
                            unfocusedBorderColor = cardBorder,
                            focusedTextColor = primaryText,
                            unfocusedTextColor = primaryText
                        )
                    )

                    OutlinedTextField(
                        value = newCustPhone,
                        onValueChange = { newCustPhone = it },
                        label = { Text("Mobile Number (মোবাইল)*") },
                        placeholder = { Text("e.g. 01712345678") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = yellowPrimary,
                            unfocusedBorderColor = cardBorder,
                            focusedTextColor = primaryText,
                            unfocusedTextColor = primaryText
                        )
                    )

                    OutlinedTextField(
                        value = newCustAddress,
                        onValueChange = { newCustAddress = it },
                        label = { Text("Address (ঠিকানা - Optional)") },
                        placeholder = { Text("e.g. Mirpur, Dhaka") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = yellowPrimary,
                            unfocusedBorderColor = cardBorder,
                            focusedTextColor = primaryText,
                            unfocusedTextColor = primaryText
                        )
                    )

                    OutlinedTextField(
                        value = newCustOpeningBal,
                        onValueChange = { newCustOpeningBal = it },
                        label = { Text("Previous Due / Balance (পূর্বের বাকি ৳ - Optional)") },
                        placeholder = { Text("0.0") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = yellowPrimary,
                            unfocusedBorderColor = cardBorder,
                            focusedTextColor = primaryText,
                            unfocusedTextColor = primaryText
                        )
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Button(
                        onClick = {
                            val cName = newCustName.trim()
                            if (cName.isBlank()) {
                                Toast.makeText(context, "Customer name is required", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val cPhone = newCustPhone.trim()
                            if (cPhone.isBlank()) {
                                Toast.makeText(context, "Phone number is required", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val cBal = newCustOpeningBal.toDoubleOrNull() ?: 0.0

                            viewModel.addCustomer(
                                name = cName,
                                phone = cPhone,
                                initialBalance = cBal,
                                address = newCustAddress.trim().ifBlank { null },
                                onResult = { success, msg, newCust ->
                                    if (success && newCust != null) {
                                        selectedCustomerId = newCust.id
                                        selectedCustomerName = newCust.name
                                        selectedCustomerPhone = newCust.phone
                                        newCustName = ""
                                        newCustPhone = ""
                                        newCustAddress = ""
                                        newCustOpeningBal = ""
                                        showAddNewCustomerDialog = false
                                        showCustomerDialog = false
                                        Toast.makeText(context, "Customer added & selected", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = yellowPrimary),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save & Select Customer", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }

        // Discount Edit Dialog
        if (showDiscountDialog) {
            var tempDiscountText by remember { mutableStateOf(discountAmount.toString()) }
            EnterpriseGestureModal(
                onDismissRequest = { showDiscountDialog = false },
                title = "Set Discount Amount",
                subtitle = "Swipe down or drag handle to dismiss",
                icon = Icons.Default.LocalOffer
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = tempDiscountText,
                        onValueChange = { tempDiscountText = it },
                        label = { Text("Discount (৳)", color = secondaryText) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = primaryText,
                            unfocusedTextColor = primaryText,
                            focusedBorderColor = yellowPrimary,
                            unfocusedBorderColor = cardBorder
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            discountAmount = tempDiscountText.toDoubleOrNull() ?: discountAmount
                            showDiscountDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = yellowPrimary),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }

        // Completed Order Modal
        if (completedOrderSummary != null) {
            val json = completedOrderSummary!!
            val orderId = json.optString("orderId")
            val totalAmt = json.optDouble("totalAmount")
            val payType = json.optString("paymentType")
            val discount = json.optDouble("discount")

            EnterpriseGestureModal(
                onDismissRequest = { completedOrderSummary = null },
                title = "Receipt / Invoice Generated",
                subtitle = "Swipe down or drag handle to dismiss",
                icon = Icons.Default.Receipt
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Invoice ID: #$orderId", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = primaryText)
                    Text("Payment Method: $payType", fontSize = 12.sp, color = primaryText)
                    Text("Total Discount: ৳${String.format("%.2f", discount)}", fontSize = 12.sp, color = discountRed)
                    HorizontalDivider(color = cardBorder)
                    Text("Net Paid: ৳${String.format("%,.2f", totalAmt)} BDT", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = primaryText)
                    Text("Stock items auto-deducted from inventory.", fontSize = 11.sp, color = secondaryText)

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            viewModel.clearPosCart()
                            completedOrderSummary = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = yellowPrimary),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Start New Sale", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}