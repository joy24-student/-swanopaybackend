package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AutoSetupDatabaseCard(
    viewModel: AppViewModel,
    isDarkMode: Boolean,
    isBangla: Boolean = false,
    isRunningSystemTest: Boolean = false
) {
    var isAutoSettingUp by remember { mutableStateOf(false) }
    var autoSetupResultMsg by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = if (isDarkMode) Color(0xFF1E1B4B) else Color(0xFFEEF2FF)),
        border = BorderStroke(1.dp, Color(0xFF6366F1).copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier.size(36.dp).background(Color(0xFF6366F1), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isBangla) "স্বয়ংক্রিয় ডাটাবেস ও ক্লাউড সেটআপ" else "Auto-Setup / Repair Database",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (isDarkMode) Color(0xFFC7D2FE) else Color(0xFF312E81)
                    )
                    Text(
                        text = if (isBangla) "সকল ডাটাবেস টেবিল, স্টোরেজ বাকেট, রিয়েলটাইম ও এজ ফাংশন এক ক্লিকে অটোমেটিক সেটআপ করুন" else "Automatically create all tables, storage buckets, realtime & edge functions with 1 click.",
                        fontSize = 11.sp,
                        color = if (isDarkMode) Color(0xFFA5B4FC) else Color(0xFF4338CA)
                    )
                }
            }

            if (!autoSetupResultMsg.isNullOrBlank()) {
                Text(
                    text = autoSetupResultMsg ?: "",
                    fontSize = 11.sp,
                    color = if (autoSetupResultMsg?.contains("fail", ignoreCase = true) == true) Color(0xFFEF4444) else SuccessGreen,
                    fontWeight = FontWeight.Medium
                )
            }

            Button(
                onClick = {
                    isAutoSettingUp = true
                    autoSetupResultMsg = if (isBangla) "স্বয়ংক্রিয় সেটআপ চলছে (টেবিল, বাকেট, রিয়েলটাইম ও ফাংশন)..." else "Running auto-setup (tables, buckets, realtime & edge functions)..."
                    viewModel.triggerAutoSetupConnectedDatabase { _, msg ->
                        isAutoSettingUp = false
                        autoSetupResultMsg = msg
                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                    }
                },
                enabled = !isAutoSettingUp && !isRunningSystemTest,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(42.dp)
            ) {
                if (isAutoSettingUp || isRunningSystemTest) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isBangla) "সেটআপ হচ্ছে..." else "Configuring Cloud...", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.Bolt, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isBangla) "⚡ স্বয়ংক্রিয় ডাটাবেস সেটআপ রান করুন" else "⚡ Run Auto-Setup & Repair Database", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
