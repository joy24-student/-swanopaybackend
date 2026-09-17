package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeesScreen(viewModel: AppViewModel) {
    val context = LocalContext.current
    val isDark by viewModel.isDarkMode.collectAsState()
    SideEffect { isDarkModeGlobal = isDark }
    val employeeList by viewModel.employees.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var selectedFilterTab by remember { mutableStateOf("All") } // "All", "Active", "Inactive"
    var selectedRoleFilter by remember { mutableStateOf("All") } // "All", "Admin", "Finance", "Sales", "Inventory", "Support"
    var showFilterModal by remember { mutableStateOf(false) }

    var showAddModal by remember { mutableStateOf(false) }
    var selectedEmployeeForEdit by remember { mutableStateOf<EmployeeItem?>(null) }
    var selectedEmployeeForQr by remember { mutableStateOf<EmployeeItem?>(null) }

    // Pagination state
    var currentPage by remember { mutableIntStateOf(1) }
    val itemsPerPage = 5

    // Exact Theme Colors matching reference image
    val screenBg = if (isDark) Color(0xFF0F1117) else Color(0xFFF8FAFC)
    val cardBg = if (isDark) Color(0xFF1C1F2B) else Color.White
    val cardBorder = if (isDark) Color(0xFF2D3243) else Color(0xFFF1F5F9)
    val primaryText = if (isDark) Color.White else Color(0xFF0F172A)
    val secondaryText = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
    val goldAccent = Color(0xFFF59E0B)
    val goldBgSoft = if (isDark) Color(0xFF382A0F) else Color(0xFFFEF3C7)
    val goldText = if (isDark) Color(0xFFFBBF24) else Color(0xFFB45309)

    // Filtered list
    val filteredList = remember(employeeList, searchQuery, selectedFilterTab, selectedRoleFilter) {
        employeeList.filter { emp ->
            val matchesTab = when (selectedFilterTab) {
                "Active" -> emp.status.equals("Active", ignoreCase = true)
                "Inactive" -> emp.status.equals("Inactive", ignoreCase = true)
                else -> true
            }
            val matchesRole = if (selectedRoleFilter == "All") true else emp.role.equals(selectedRoleFilter, ignoreCase = true)
            val matchesQuery = searchQuery.isBlank() ||
                    emp.name.contains(searchQuery, ignoreCase = true) ||
                    emp.designation.contains(searchQuery, ignoreCase = true) ||
                    emp.role.contains(searchQuery, ignoreCase = true) ||
                    emp.email.contains(searchQuery, ignoreCase = true) ||
                    emp.phone.contains(searchQuery, ignoreCase = true) ||
                    emp.department.contains(searchQuery, ignoreCase = true)

            matchesTab && matchesRole && matchesQuery
        }
    }

    // Pagination calculations
    val totalPages = remember(filteredList) {
        val count = (filteredList.size + itemsPerPage - 1) / itemsPerPage
        if (count == 0) 1 else count
    }

    val pageItems = remember(filteredList, currentPage) {
        val safePage = currentPage.coerceIn(1, totalPages)
        val startIndex = (safePage - 1) * itemsPerPage
        filteredList.drop(startIndex).take(itemsPerPage)
    }

    // Stats
    val totalEmployees = employeeList.size
    val activeEmployees = employeeList.count { it.status.equals("Active", ignoreCase = true) }
    val inactiveEmployees = employeeList.count { it.status.equals("Inactive", ignoreCase = true) }
    val totalDepartments = employeeList.map { it.department }.distinct().size

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(screenBg)
    ) {
        // Subtle Background Organic Curved Wave Graph at Top
        if (!isDark) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                val path = Path().apply {
                    moveTo(size.width, 0f)
                    lineTo(size.width, size.height * 0.7f)
                    quadraticTo(
                        size.width * 0.5f, size.height * 1.1f,
                        0f, size.height * 0.4f
                    )
                    lineTo(0f, 0f)
                    close()
                }
                drawPath(
                    path = path,
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFFF1F5F9).copy(alpha = 0.6f), Color.Transparent)
                    )
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 90.dp)
        ) {
            // 1. TOP HEADER WITH SAFEAREA INSET
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(cardBg)
                                    .border(1.dp, cardBorder, RoundedCornerShape(14.dp))
                                    .clickable { viewModel.navigateTo("More") },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = primaryText,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column {
                                Text(
                                    text = "Employees",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryText
                                )
                                Text(
                                    text = "Manage your team members",
                                    fontSize = 13.sp,
                                    color = secondaryText
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Live Staff Monitor Button
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(cardBg)
                                    .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                                    .clickable { viewModel.navigateTo("EmployeeMonitor") },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sensors,
                                    contentDescription = "লাইভ স্টাফ মনিটর",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            // Plus (+) Button - Yellow Circle
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(goldAccent)
                                    .clickable { showAddModal = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add Employee",
                                    tint = Color.Black,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            // Search Button
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(cardBg)
                                    .border(1.dp, cardBorder, RoundedCornerShape(14.dp))
                                    .clickable { isSearchExpanded = !isSearchExpanded },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = primaryText,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Expandable Search Bar
                    AnimatedVisibility(
                        visible = isSearchExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                currentPage = 1
                            },
                            placeholder = { Text("Search by name, role, email or phone...", fontSize = 13.sp, color = secondaryText) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = goldAccent) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = secondaryText)
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = cardBg,
                                unfocusedContainerColor = cardBg,
                                focusedBorderColor = goldAccent,
                                unfocusedBorderColor = cardBorder,
                                focusedTextColor = primaryText,
                                unfocusedTextColor = primaryText
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                        )
                    }
                }
            }

            // 2. SUMMARY STAT CARDS (4 Horizontal Cards)
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(bottom = 18.dp)
                ) {
                    item {
                        EmployeeStatCardPixel(
                            title = "Total Employees",
                            count = totalEmployees.toString(),
                            subtitle = "All Members >",
                            subtitleColor = secondaryText,
                            icon = Icons.Outlined.Person,
                            isDark = isDark,
                            cardBg = cardBg,
                            cardBorder = cardBorder,
                            primaryText = primaryText,
                            onClick = {
                                selectedFilterTab = "All"
                                selectedRoleFilter = "All"
                                searchQuery = ""
                                currentPage = 1
                            }
                        )
                    }
                    item {
                        EmployeeStatCardPixel(
                            title = "Active",
                            count = activeEmployees.toString(),
                            subtitle = "Currently Working >",
                            subtitleColor = Color(0xFF10B981),
                            icon = Icons.Outlined.CheckCircle,
                            isDark = isDark,
                            cardBg = cardBg,
                            cardBorder = cardBorder,
                            primaryText = primaryText,
                            onClick = {
                                selectedFilterTab = "Active"
                                currentPage = 1
                            }
                        )
                    }
                    item {
                        EmployeeStatCardPixel(
                            title = "Inactive",
                            count = inactiveEmployees.toString(),
                            subtitle = "Not Active >",
                            subtitleColor = Color(0xFFEF4444),
                            icon = Icons.Outlined.Cancel,
                            isDark = isDark,
                            cardBg = cardBg,
                            cardBorder = cardBorder,
                            primaryText = primaryText,
                            onClick = {
                                selectedFilterTab = "Inactive"
                                currentPage = 1
                            }
                        )
                    }
                    item {
                        EmployeeStatCardPixel(
                            title = "Departments",
                            count = totalDepartments.toString(),
                            subtitle = "Total Departments >",
                            subtitleColor = secondaryText,
                            icon = Icons.Outlined.Shield,
                            isDark = isDark,
                            cardBg = cardBg,
                            cardBorder = cardBorder,
                            primaryText = primaryText,
                            onClick = { showFilterModal = true }
                        )
                    }
                }
            }

            // 3. TABS AND FILTER ROW
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Filter Tabs
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("All", "Active", "Inactive").forEach { tab ->
                            val isSelected = selectedFilterTab == tab
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) goldBgSoft else cardBg)
                                    .border(1.dp, if (isSelected) Color(0xFFFDE68A) else cardBorder, RoundedCornerShape(12.dp))
                                    .clickable {
                                        selectedFilterTab = tab
                                        currentPage = 1
                                    }
                                    .padding(horizontal = 18.dp, vertical = 9.dp)
                            ) {
                                Text(
                                    text = tab,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = primaryText
                                )
                            }
                        }
                    }

                    // Filter Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(cardBg)
                            .border(1.dp, if (selectedRoleFilter != "All") goldAccent else cardBorder, RoundedCornerShape(12.dp))
                            .clickable { showFilterModal = true }
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = "Filter",
                                tint = if (selectedRoleFilter != "All") goldAccent else primaryText,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (selectedRoleFilter != "All") selectedRoleFilter else "Filter",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = primaryText
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = secondaryText,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // 4. EMPLOYEE ITEMS LIST
            if (pageItems.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.GroupOff,
                            contentDescription = null,
                            tint = secondaryText,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No employees found", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = primaryText)
                        Text("Try resetting filters or adding a new team member.", fontSize = 13.sp, color = secondaryText)
                    }
                }
            } else {
                items(pageItems, key = { it.id }) { employee ->
                    EmployeeCardItemPixel(
                        employee = employee,
                        isDark = isDark,
                        cardBg = cardBg,
                        cardBorder = cardBorder,
                        primaryText = primaryText,
                        secondaryText = secondaryText,
                        goldBgSoft = goldBgSoft,
                        goldText = goldText,
                        onClick = { selectedEmployeeForEdit = employee },
                        onShowQr = { selectedEmployeeForQr = employee },
                        onRevoke = {
                            viewModel.revokeEmployeeAccess(employee.id, "মার্চেন্ট কর্তৃক অ্যাক্সেস বন্ধ")
                            Toast.makeText(context, "${employee.name}-এর অ্যাক্সেস বন্ধ করা হয়েছে", Toast.LENGTH_SHORT).show()
                        },
                        onRestore = {
                            viewModel.restoreEmployeeAccess(employee.id,
                                onSuccess = {
                                    Toast.makeText(context, "${employee.name}-এর অ্যাক্সেস পুনরায় চালু করা হয়েছে", Toast.LENGTH_SHORT).show()
                                },
                                onError = { err ->
                                    Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    )
                }
            }

            // 5. PAGINATION BAR
            if (totalPages > 1 || filteredList.size > itemsPerPage) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 22.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Prev Arrow
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(cardBg)
                                .border(1.dp, cardBorder, RoundedCornerShape(10.dp))
                                .clickable {
                                    if (currentPage > 1) currentPage--
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronLeft,
                                contentDescription = "Previous",
                                tint = if (currentPage > 1) primaryText else secondaryText.copy(alpha = 0.4f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Page Numbers: 1, 2, 3, ..., 5
                        val pagesToShow = listOf("1", "2", "3", "...", "5")
                        pagesToShow.forEach { label ->
                            if (label == "...") {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("...", fontSize = 14.sp, color = secondaryText, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                val pageNum = label.toIntOrNull() ?: 1
                                val isCurrent = pageNum == currentPage
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isCurrent) goldBgSoft else cardBg)
                                        .border(1.dp, if (isCurrent) Color(0xFFFDE68A) else cardBorder, RoundedCornerShape(10.dp))
                                        .clickable {
                                            currentPage = pageNum
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 13.sp,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                        color = primaryText
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                        }

                        // Next Arrow
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(cardBg)
                                .border(1.dp, cardBorder, RoundedCornerShape(10.dp))
                                .clickable {
                                    if (currentPage < totalPages) currentPage++
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Next",
                                tint = if (currentPage < totalPages) primaryText else secondaryText.copy(alpha = 0.4f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // MODAL: Add Employee
    if (showAddModal) {
        EmployeeFormDialogPixel(
            isDark = isDark,
            employee = null,
            onDismiss = { showAddModal = false },
            onSave = { newEmp ->
                viewModel.addEmployee(
                    name = newEmp.name,
                    designation = newEmp.designation,
                    role = newEmp.role,
                    email = newEmp.email,
                    phone = newEmp.phone,
                    department = newEmp.department,
                    status = newEmp.status,
                    permissions = newEmp.permissions
                )
                Toast.makeText(context, "Added ${newEmp.name} to team!", Toast.LENGTH_SHORT).show()
                showAddModal = false
            }
        )
    }

    // MODAL: Edit Employee
    if (selectedEmployeeForEdit != null) {
        EmployeeFormDialogPixel(
            isDark = isDark,
            employee = selectedEmployeeForEdit,
            onDismiss = { selectedEmployeeForEdit = null },
            onSave = { updatedEmp ->
                viewModel.updateEmployee(updatedEmp)
                Toast.makeText(context, "Updated details for ${updatedEmp.name}", Toast.LENGTH_SHORT).show()
                selectedEmployeeForEdit = null
            },
            onDelete = { empId ->
                viewModel.deleteEmployee(empId)
                viewModel.revokeEmployeeAccess(empId)
                Toast.makeText(context, "Member removed", Toast.LENGTH_SHORT).show()
                selectedEmployeeForEdit = null
            }
        )
    }

    // MODAL: Employee Login QR
    if (selectedEmployeeForQr != null) {
        val merchantId = viewModel.activeProfile.collectAsState().value.id
        val storeName = viewModel.activeProfile.collectAsState().value.businessName.ifBlank { "SwapnoPay Merchant Store" }
        EmployeeLoginQrDialog(
            employee = selectedEmployeeForQr!!,
            merchantId = merchantId,
            storeName = storeName,
            viewModel = viewModel,
            isDark = isDark,
            onDismiss = { selectedEmployeeForQr = null }
        )
    }

    // MODAL: Filter Dialog
    if (showFilterModal) {
        Dialog(onDismissRequest = { showFilterModal = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = cardBg,
                border = BorderStroke(1.dp, cardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Filter Team Members", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = primaryText)
                        IconButton(onClick = { showFilterModal = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = secondaryText)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Filter by Role:", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = primaryText)
                    Spacer(modifier = Modifier.height(8.dp))

                    val roles = listOf("All", "Admin", "Finance", "Sales", "Inventory", "Support")
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        roles.forEach { r ->
                            val isSel = selectedRoleFilter == r
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSel) goldBgSoft else Color.Transparent)
                                    .clickable {
                                        selectedRoleFilter = r
                                        currentPage = 1
                                        showFilterModal = false
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSel,
                                    onClick = {
                                        selectedRoleFilter = r
                                        currentPage = 1
                                        showFilterModal = false
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = goldAccent)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = r,
                                    fontSize = 14.sp,
                                    color = if (isSel) goldText else primaryText,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            selectedRoleFilter = "All"
                            selectedFilterTab = "All"
                            searchQuery = ""
                            currentPage = 1
                            showFilterModal = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = cardBorder, contentColor = primaryText),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Reset All Filters")
                    }
                }
            }
        }
    }
}

// ── COMPONENT: Stat Card ───────────────────────────────────────────────────
@Composable
private fun EmployeeStatCardPixel(
    title: String,
    count: String,
    subtitle: String,
    subtitleColor: Color,
    icon: ImageVector,
    isDark: Boolean,
    cardBg: Color,
    cardBorder: Color,
    primaryText: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(138.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, cardBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon in Light Amber Pill / Circle
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isDark) Color(0xFF382A0F) else Color(0xFFFEF3C7)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = title,
                fontSize = 12.sp,
                color = primaryText,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = count,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = primaryText
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = subtitle,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = subtitleColor
            )
        }
    }
}

// ── COMPONENT: Employee Item Card ──────────────────────────────────────────
@Composable
private fun EmployeeCardItemPixel(
    employee: EmployeeItem,
    isDark: Boolean,
    cardBg: Color,
    cardBorder: Color,
    primaryText: Color,
    secondaryText: Color,
    goldBgSoft: Color,
    goldText: Color,
    onClick: () -> Unit,
    onShowQr: () -> Unit,
    onRevoke: () -> Unit,
    onRestore: () -> Unit
) {
    val isActive = employee.status.equals("Active", ignoreCase = true)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, if (!isActive) Color(0xFFEF4444).copy(alpha = 0.35f) else cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Avatar with Status Indicator Dot
                    Box(contentAlignment = Alignment.BottomEnd) {
                        val avatarBg = remember(employee.name) {
                            val colors = listOf(
                                Color(0xFF3B82F6), Color(0xFF10B981), Color(0xFFF59E0B),
                                Color(0xFFEC4899), Color(0xFF8B5CF6), Color(0xFF0EA5E9)
                            )
                            colors[Math.abs(employee.name.hashCode()) % colors.size]
                        }
                        val initials = remember(employee.name) {
                            employee.name.split(" ")
                                .mapNotNull { it.firstOrNull() }
                                .take(2)
                                .joinToString("")
                                .uppercase()
                        }

                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(if (isActive) avatarBg else Color(0xFF64748B)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = initials,
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Online / Offline Dot Indicator
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(if (isActive) Color(0xFF10B981) else Color(0xFF9CA3AF))
                                .background(if (isActive) Color(0xFF10B981) else Color(0xFFEF4444))
                                .border(2.dp, cardBg, CircleShape)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    // Employee Info
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = employee.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = employee.name,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = primaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            // Status Pill
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (isActive) Color(0xFF10B981).copy(alpha = 0.15f)
                                        else Color(0xFFEF4444).copy(alpha = 0.15f)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isActive) "সক্রিয়" else "লকড",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isActive) Color(0xFF10B981) else Color(0xFFEF4444)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = employee.designation,
                            fontSize = 13.sp,
                            color = secondaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = employee.email,
                            fontSize = 12.sp,
                            color = secondaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Top Right Role Badge & Chevron
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Top
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(goldBgSoft)
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = employee.role,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = goldText
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Details",
                        tint = primaryText,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bottom Action Row: Controls on left, Phone on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // QR Code Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isDark) Color(0xFF232D42) else Color(0xFFEFF6FF))
                            .border(1.dp, Color(0xFF3B82F6).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .clickable { onShowQr() }
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.QrCode2,
                                contentDescription = "Login QR",
                                tint = Color(0xFF2563EB),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "লগইন QR",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color(0xFF93C5FD) else Color(0xFF1D4ED8)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Instant Lock / Unlock 1-Tap Control
                    if (isActive) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFEF4444).copy(alpha = 0.12f))
                                .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .clickable { onRevoke() }
                                .padding(horizontal = 9.dp, vertical = 5.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Lock",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "অ্যাক্সেস বন্ধ",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFEF4444)
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF10B981).copy(alpha = 0.12f))
                                .border(1.dp, Color(0xFF10B981).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .clickable { onRestore() }
                                .padding(horizontal = 9.dp, vertical = 5.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.LockOpen,
                                    contentDescription = "Unlock",
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "চালু করুন",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF10B981)
                                )
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = null,
                        tint = secondaryText,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = employee.phone,
                        fontSize = 12.sp,
                        color = secondaryText,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

// ── COMPONENT: Add / Edit Employee Dialog ──────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmployeeFormDialogPixel(
    isDark: Boolean,
    employee: EmployeeItem?,
    onDismiss: () -> Unit,
    onSave: (EmployeeItem) -> Unit,
    onDelete: ((String) -> Unit)? = null
) {
    var name by remember { mutableStateOf(employee?.name ?: "") }
    var designation by remember { mutableStateOf(employee?.designation ?: "") }
    var role by remember { mutableStateOf(employee?.role ?: "Sales") }
    var email by remember { mutableStateOf(employee?.email ?: "") }
    var phone by remember { mutableStateOf(employee?.phone ?: "") }
    var department by remember { mutableStateOf(employee?.department ?: "Store") }
    var status by remember { mutableStateOf(employee?.status ?: "Active") }

    val allPermissionsList = listOf(
        "POS & Billing Access",
        "Customer & Supplier Ledgers",
        "Financial Reports & Expenses",
        "Stock & Inventory Management",
        "Supabase Cloud Backup Admin",
        "System Settings & Gateways"
    )

    var selectedPermissions by remember {
        mutableStateOf(employee?.permissions?.toSet() ?: setOf("POS & Billing Access", "Customer & Supplier Ledgers"))
    }

    val cardBg = if (isDark) Color(0xFF1E212B) else Color.White
    val cardBorder = if (isDark) Color(0xFF2E3444) else Color(0xFFE2E8F0)
    val primaryText = if (isDark) Color.White else Color(0xFF0F172A)
    val secondaryText = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
    val goldAccent = Color(0xFFF59E0B)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = cardBg,
            border = BorderStroke(1.dp, cardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            LazyColumn(modifier = Modifier.padding(20.dp)) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (employee == null) "Add Team Member" else "Employee Details & Permissions",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryText
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = secondaryText)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Name
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Full Name", fontSize = 12.sp) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Designation
                    OutlinedTextField(
                        value = designation,
                        onValueChange = { designation = it },
                        label = { Text("Designation / Title", fontSize = 12.sp) },
                        placeholder = { Text("e.g. Store Manager, Accountant", fontSize = 12.sp) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Email & Phone
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email", fontSize = 12.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("Phone", fontSize = 12.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Role Selector
                    Text("Role:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = primaryText)
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf("Admin", "Finance", "Sales", "Inventory", "Support")) { r ->
                            val isSel = role == r
                            FilterChip(
                                selected = isSel,
                                onClick = { role = r },
                                label = { Text(r) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = goldAccent,
                                    selectedLabelColor = Color.Black
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Department Selector
                    Text("Department:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = primaryText)
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf("Store", "Finance", "Sales", "Inventory", "Support", "IT", "Management")) { dept ->
                            val isSel = department == dept
                            FilterChip(
                                selected = isSel,
                                onClick = { department = dept },
                                label = { Text(dept) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = if (isDark) Color(0xFF382A0F) else Color(0xFFFEF3C7),
                                    selectedLabelColor = goldAccent
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Status Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Account Active", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = primaryText)
                        Switch(
                            checked = status == "Active",
                            onCheckedChange = { status = if (it) "Active" else "Inactive" },
                            colors = SwitchDefaults.colors(checkedThumbColor = goldAccent, checkedTrackColor = goldAccent.copy(alpha = 0.3f))
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Granular Permissions
                    Text("Granular Access Permissions:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = primaryText)
                    Spacer(modifier = Modifier.height(8.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        allPermissionsList.forEach { perm ->
                            val isChecked = selectedPermissions.contains(perm)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedPermissions = if (isChecked) {
                                            selectedPermissions - perm
                                        } else {
                                            selectedPermissions + perm
                                        }
                                    }
                                    .padding(vertical = 4.dp)
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = {
                                        selectedPermissions = if (it) {
                                            selectedPermissions + perm
                                        } else {
                                            selectedPermissions - perm
                                        }
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = goldAccent)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(perm, fontSize = 12.sp, color = primaryText)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Action Buttons
                    Button(
                        onClick = {
                            if (name.isBlank()) return@Button
                            val item = EmployeeItem(
                                id = employee?.id ?: java.util.UUID.randomUUID().toString(),
                                name = name.trim(),
                                designation = designation.ifBlank { "Staff" },
                                role = role,
                                email = email.ifBlank { "${name.lowercase().replace(" ", ".")}@shopbd.com" },
                                phone = phone.ifBlank { "01700-000000" },
                                department = department,
                                status = status,
                                permissions = selectedPermissions.toList()
                            )
                            onSave(item)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = goldAccent, contentColor = Color.Black),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text("Save Employee", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }

                    if (employee != null && onDelete != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { onDelete(employee.id) },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Remove Member")
                        }
                    }
                }
            }
        }
    }
}

// ── COMPONENT: Secure Employee Login QR Dialog ──────────────────────────────
@Composable
private fun EmployeeLoginQrDialog(
    employee: EmployeeItem,
    merchantId: String,
    storeName: String,
    viewModel: AppViewModel,
    isDark: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val cardBg = if (isDark) Color(0xFF1E293B) else Color.White
    val textPrimary = if (isDark) Color.White else Color(0xFF0F172A)
    val textSecondary = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    var staffPin by remember { mutableStateOf("") }
    var validityHours by remember { mutableIntStateOf(24) }
    var encryptedToken by remember { mutableStateOf<String?>(null) }
    var hasPinConfigured by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }

    fun requestSecureToken() {
        isGenerating = true
        viewModel.generateEmployeePairingToken(
            employeeId = employee.id,
            employeeName = employee.name,
            employeeRole = employee.role,
            pin = staffPin.trim().ifBlank { null },
            expiresInHours = validityHours,
            onSuccess = { token, hasPin, _ ->
                encryptedToken = token
                hasPinConfigured = hasPin
                isGenerating = false
            },
            onError = {
                isGenerating = false
            }
        )
    }

    LaunchedEffect(employee.id, validityHours) {
        requestSecureToken()
    }

    val fallbackJson = remember(employee, merchantId) {
        org.json.JSONObject().apply {
            put("merchant_id", merchantId)
            put("employee_id", employee.id)
            put("employee_name", employee.name)
            put("employee_role", employee.role)
        }.toString()
    }

    val activePayload = encryptedToken ?: fallbackJson
    val encodedPayload = remember(activePayload) {
        java.net.URLEncoder.encode(activePayload, "UTF-8")
    }
    val qrImageUrl = "https://api.qrserver.com/v1/create-qr-code/?size=500x500&data=$encodedPayload"

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(22.dp),
            color = cardBg,
            border = BorderStroke(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "এনক্রিপ্টেড স্টাফ কিউআর",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = textPrimary
                        )
                        Text(
                            text = "${employee.name} (${employee.role})",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textSecondary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = textSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Security Shield Badge
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF10B981).copy(alpha = 0.12f))
                        .border(1.dp, Color(0xFF10B981).copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (encryptedToken != null) "AES-256 এনক্রিপ্টেড ও অ্যান্টি-রিপ্লে সিকিউরড" else "স্টাফ অ্যাক্সেস টোকেন",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF10B981)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // QR Code Image Box
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(color = Color(0xFF2563EB), modifier = Modifier.size(36.dp))
                    } else {
                        coil.compose.AsyncImage(
                            model = qrImageUrl,
                            contentDescription = "Secure Employee QR Code",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "পদবী: ${employee.role} • দোকান: $storeName",
                    fontSize = 12.sp,
                    color = textSecondary,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Optional Staff PIN Field
                OutlinedTextField(
                    value = staffPin,
                    onValueChange = { if (it.length <= 6) staffPin = it },
                    label = { Text("ঐচ্ছিক স্টাফ পিন (২-ফ্যাক্টর নিরাপত্তা)", fontSize = 12.sp) },
                    placeholder = { Text("৪-৬ সংখ্যার পিন লিখুন", fontSize = 11.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    trailingIcon = {
                        IconButton(onClick = { requestSecureToken() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Regenerate", tint = Color(0xFF2563EB), modifier = Modifier.size(18.dp))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF2563EB),
                        unfocusedBorderColor = if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1)
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "কর্মচারী তার ফোনে 'SwapnoPay Staff' অ্যাপে এই কিউআর কোডটি স্ক্যান করে নিজস্ব পোর্টালে লগইন করতে পারবেন।",
                    fontSize = 11.sp,
                    color = textSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Token Expiry Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("মেয়াদ:", fontSize = 12.sp, color = textSecondary)
                    Row {
                        listOf(24 to "২৪ ঘণ্টা", 168 to "৭ দিন", 720 to "৩০ দিন").forEach { (h, label) ->
                            val isSel = validityHours == h
                            Box(
                                modifier = Modifier
                                    .padding(start = 6.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) Color(0xFF2563EB) else if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0))
                                    .clickable { validityHours = h }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSel) Color.White else textSecondary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Copy Token Button
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Employee Token", activePayload))
                        Toast.makeText(context, "এনক্রিপ্টেড পেয়ারিং কোড কপি হয়েছে!", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("এনক্রিপ্টেড টোকেন কপি করুন", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
