package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// PARSED MARKDOWN BLOCK TYPES
// ─────────────────────────────────────────────────────────────────────────────
sealed class MarkdownBlock {
    data class Header(val level: Int, val text: String) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
    data class BulletItem(val level: Int, val text: String) : MarkdownBlock()
    data class NumberedItem(val number: String, val text: String) : MarkdownBlock()
    data class KpiGrid(val items: List<KpiItem>) : MarkdownBlock()
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MarkdownBlock()
    data class Callout(val text: String, val type: CalloutType) : MarkdownBlock()
    data class Code(val language: String, val code: String) : MarkdownBlock()
    object Divider : MarkdownBlock()
}

data class KpiItem(val label: String, val value: String, val isPositive: Boolean? = null)
enum class CalloutType { NOTE, TIP, WARNING }

// ─────────────────────────────────────────────────────────────────────────────
// MAIN STRUCTURED AI MESSAGE BUBBLE COMPOSABLE
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun StructuredAiMessageBubble(
    message: Map<String, String>,
    isDarkMode: Boolean,
    viewModel: AppViewModel,
    ttsEngine: TextToSpeech? = null,
    onSpeak: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val role = message["role"] ?: "assistant"
    val isUser = role == "user"
    val rawContent = message["content"] ?: ""
    val actionStr = message["action"]

    val textMain = if (isDarkMode) Color.White else Color(0xFF0F172A)
    val textMuted = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)
    val cardBg = if (isDarkMode) Color(0xFF131722) else Color.White
    val cardBorder = if (isDarkMode) Color(0xFF232B3E) else Color(0xFFE2E8F0)
    val userBubbleBg = if (isDarkMode) Color(0xFF1E293B) else Color(0xFF0F172A)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (isUser) {
            // USER MESSAGE BUBBLE: Crisp, compact, right-aligned with avatar
            Row(
                modifier = Modifier.widthIn(max = 320.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.Bottom
            ) {
                Card(
                    shape = RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = 18.dp,
                        bottomEnd = 4.dp
                    ),
                    colors = CardDefaults.cardColors(containerColor = userBubbleBg),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Text(
                        text = rawContent,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        color = Color.White,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        } else {
            // AI COPILOT ASSISTANT MESSAGE CARD: Rich, full-width, highly structured
            Card(
                shape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = 4.dp,
                    bottomEnd = 18.dp
                ),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, cardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    // PARSED STRUCTURED CONTENT BLOCKS
                    val parsedBlocks = remember(rawContent) { parseMarkdownBlocks(rawContent) }
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        parsedBlocks.forEach { block ->
                            RenderMarkdownBlock(
                                block = block,
                                isDarkMode = isDarkMode,
                                textMain = textMain,
                                textMuted = textMuted
                            )
                        }
                    }

                    // ACTION CONFIRMATION CARD (if actionable task is attached)
                    if (actionStr != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        ActionConfirmationCard(
                            actionJsonStr = actionStr,
                            isDarkMode = isDarkMode,
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// RENDER INDIVIDUAL MARKDOWN BLOCKS
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun RenderMarkdownBlock(
    block: MarkdownBlock,
    isDarkMode: Boolean,
    textMain: Color,
    textMuted: Color
) {
    val context = LocalContext.current

    when (block) {
        is MarkdownBlock.Header -> {
            val (fontSize, topPadding) = when (block.level) {
                1 -> 18.sp to 10.dp
                2 -> 16.sp to 8.dp
                3 -> 14.5.sp to 6.dp
                else -> 13.5.sp to 4.dp
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = topPadding, bottom = 2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.5.dp)
                            .height(16.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFFA855F7), Color(0xFF6366F1))
                                )
                            )
                    )
                    Text(
                        text = block.text,
                        fontSize = fontSize,
                        fontWeight = FontWeight.Bold,
                        color = textMain,
                        lineHeight = (fontSize.value * 1.3).sp
                    )
                }
            }
        }

        is MarkdownBlock.KpiGrid -> {
            // Visual KPI Stat Cards Grid (e.g. Total Sales, Net Profit, Dues)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                block.items.chunked(2).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowItems.forEach { kpi ->
                            KpiTile(
                                item = kpi,
                                modifier = Modifier.weight(1f),
                                isDarkMode = isDarkMode
                            )
                        }
                        if (rowItems.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        is MarkdownBlock.BulletItem -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = (block.level * 8).dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 7.dp)
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(if (isDarkMode) Color(0xFFA855F7) else Color(0xFF7C3AED))
                )
                Text(
                    text = parseInlineMarkdown(block.text, isDarkMode),
                    fontSize = 13.5.sp,
                    color = textMain,
                    lineHeight = 19.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        is MarkdownBlock.NumberedItem -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isDarkMode) Color(0xFF201B34) else Color(0xFFF3E8FF),
                    modifier = Modifier.size(20.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = block.number,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkMode) Color(0xFFC084FC) else Color(0xFF7C3AED)
                        )
                    }
                }
                Text(
                    text = parseInlineMarkdown(block.text, isDarkMode),
                    fontSize = 13.5.sp,
                    color = textMain,
                    lineHeight = 19.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        is MarkdownBlock.Callout -> {
            val (badgeBg, borderCol, icon, iconColor) = when (block.type) {
                CalloutType.WARNING -> Quad(
                    if (isDarkMode) Color(0xFF2E1A11) else Color(0xFFFFF7ED),
                    if (isDarkMode) Color(0xFF9A3412) else Color(0xFFFDBA74),
                    Icons.Default.Warning,
                    Color(0xFFF97316)
                )
                CalloutType.TIP -> Quad(
                    if (isDarkMode) Color(0xFF1E281F) else Color(0xFFF0FDF4),
                    if (isDarkMode) Color(0xFF166534) else Color(0xFF86EFAC),
                    Icons.Default.Lightbulb,
                    Color(0xFF22C55E)
                )
                CalloutType.NOTE -> Quad(
                    if (isDarkMode) Color(0xFF172033) else Color(0xFFEFF6FF),
                    if (isDarkMode) Color(0xFF1E40AF) else Color(0xFF93C5FD),
                    Icons.Outlined.Info,
                    Color(0xFF3B82F6)
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = badgeBg,
                border = BorderStroke(1.dp, borderCol),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = parseInlineMarkdown(block.text, isDarkMode),
                        fontSize = 13.sp,
                        color = textMain,
                        lineHeight = 18.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        is MarkdownBlock.Table -> {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isDarkMode) Color(0xFF0F121C) else Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, if (isDarkMode) Color(0xFF232B3E) else Color(0xFFE2E8F0)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(8.dp)
                ) {
                    // Header Row
                    Row(
                        modifier = Modifier
                            .background(
                                if (isDarkMode) Color(0xFF1A1F30) else Color(0xFFE2E8F0),
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        block.headers.forEach { header ->
                            Text(
                                text = header.trim(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = textMain,
                                modifier = Modifier
                                    .widthIn(min = 90.dp)
                                    .padding(horizontal = 6.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Data Rows
                    block.rows.forEachIndexed { idx, row ->
                        val rowBg = if (idx % 2 == 0) {
                            if (isDarkMode) Color(0xFF141824) else Color.White
                        } else {
                            if (isDarkMode) Color(0xFF0F121C) else Color(0xFFF8FAFC)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(rowBg, RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            row.forEach { cell ->
                                Text(
                                    text = parseInlineMarkdown(cell.trim(), isDarkMode),
                                    fontSize = 12.sp,
                                    color = textMain,
                                    modifier = Modifier
                                        .widthIn(min = 90.dp)
                                        .padding(horizontal = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        is MarkdownBlock.Code -> {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isDarkMode) Color(0xFF0D1117) else Color(0xFF1E293B),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = block.language.ifEmpty { "CODE" }.uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFA855F7)
                        )

                        Text(
                            text = "Copy",
                            fontSize = 10.sp,
                            color = Color(0xFF94A3B8),
                            modifier = Modifier
                                .clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Code", block.code))
                                    Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
                                }
                                .padding(4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = block.code,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color(0xFFE2E8F0),
                        lineHeight = 17.sp,
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    )
                }
            }
        }

        is MarkdownBlock.Divider -> {
            HorizontalDivider(
                color = if (isDarkMode) Color(0xFF1E2638) else Color(0xFFE2E8F0),
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }

        is MarkdownBlock.Paragraph -> {
            Text(
                text = parseInlineMarkdown(block.text, isDarkMode),
                fontSize = 13.5.sp,
                color = textMain,
                lineHeight = 20.sp
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// KPI MINI METRIC CARD
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun KpiTile(
    item: KpiItem,
    modifier: Modifier = Modifier,
    isDarkMode: Boolean
) {
    val isTaka = item.value.contains("৳") || item.value.contains("tk", ignoreCase = true)
    val cardBg = if (isDarkMode) Color(0xFF171B2A) else Color(0xFFF8FAFC)
    val cardBorder = if (isDarkMode) Color(0xFF263148) else Color(0xFFE2E8F0)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = cardBg,
        border = BorderStroke(1.dp, cardBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.TrendingUp,
                    contentDescription = null,
                    tint = if (isDarkMode) Color(0xFFA855F7) else Color(0xFF7C3AED),
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = item.label,
                    fontSize = 11.sp,
                    color = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B),
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = item.value,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (isTaka) {
                    if (isDarkMode) Color(0xFF34D399) else Color(0xFF059669)
                } else {
                    if (isDarkMode) Color.White else Color(0xFF0F172A)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

// ─────────────────────────────────────────────────────────────────────────────
// MARKDOWN PARSER: CONVERTS RAW STRING INTO STRUCTURED BLOCKS
// ─────────────────────────────────────────────────────────────────────────────
fun parseMarkdownBlocks(raw: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = raw.lines()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        if (trimmed.isEmpty()) {
            i++
            continue
        }

        // 1. Code Block Fence ```
        if (trimmed.startsWith("```")) {
            val lang = trimmed.removePrefix("```").trim()
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            if (i < lines.size) i++ // skip closing ```
            blocks.add(MarkdownBlock.Code(lang, codeLines.joinToString("\n")))
            continue
        }

        // 2. Horizontal Rule --- or ***
        if (trimmed.matches(Regex("^[-*_]{3,}$"))) {
            blocks.add(MarkdownBlock.Divider)
            i++
            continue
        }

        // 3. Headings #, ##, ###, ####
        if (trimmed.startsWith("#")) {
            val level = trimmed.takeWhile { it == '#' }.length
            val headingText = trimmed.drop(level).trim()
            blocks.add(MarkdownBlock.Header(level, headingText))
            i++
            continue
        }

        // 4. Blockquotes / Callouts >
        if (trimmed.startsWith(">")) {
            val calloutLines = mutableListOf<String>()
            while (i < lines.size && lines[i].trim().startsWith(">")) {
                calloutLines.add(lines[i].trim().removePrefix(">").trim())
                i++
            }
            val fullText = calloutLines.joinToString(" ")
            val type = when {
                fullText.contains("[!WARNING]", ignoreCase = true) || fullText.contains("⚠️") || fullText.contains("সতর্কতা") -> CalloutType.WARNING
                fullText.contains("[!TIP]", ignoreCase = true) || fullText.contains("💡") || fullText.contains("পরামর্শ") -> CalloutType.TIP
                else -> CalloutType.NOTE
            }
            val cleanText = fullText
                .replace(Regex("\\[!(NOTE|TIP|WARNING)\\]", RegexOption.IGNORE_CASE), "")
                .trim()
            blocks.add(MarkdownBlock.Callout(cleanText, type))
            continue
        }

        // 5. Table Rows | Col 1 | Col 2 |
        if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
            val tableLines = mutableListOf<String>()
            while (i < lines.size && lines[i].trim().startsWith("|") && lines[i].trim().endsWith("|")) {
                tableLines.add(lines[i].trim())
                i++
            }
            if (tableLines.size >= 2) {
                val headerCells = tableLines[0].split("|").filter { it.isNotBlank() }
                // Check if line 1 is divider |---|---|
                val dataStartIndex = if (tableLines.size > 1 && tableLines[1].contains("---")) 2 else 1
                val rows = mutableListOf<List<String>>()
                for (r in dataStartIndex until tableLines.size) {
                    val cells = tableLines[r].split("|").filter { it.isNotBlank() }
                    if (cells.isNotEmpty()) rows.add(cells)
                }
                blocks.add(MarkdownBlock.Table(headerCells, rows))
                continue
            }
        }

        // 6. Ordered List 1. , 2.
        val numberedMatch = Regex("^(\\d+)[.)]\\s+(.+)").find(trimmed)
        if (numberedMatch != null) {
            val num = numberedMatch.groupValues[1]
            val content = numberedMatch.groupValues[2]
            blocks.add(MarkdownBlock.NumberedItem(num, content))
            i++
            continue
        }

        // 7. Bullet Lists *, -, •
        if (trimmed.startsWith("* ") || trimmed.startsWith("- ") || trimmed.startsWith("• ") || trimmed.startsWith("+ ")) {
            val bulletContent = trimmed.substring(2).trim()
            
            // Check if this bullet is actually a KPI metric like `* **মোট বিক্রি:** ৳৪,৩৫০`
            val kpiMatch = Regex("^\\*\\*([^:]+):\\*\\*\\s*(৳?[0-9০-৯,.]+(?:\\s*[a-zA-Z\u0980-\u09FF]+)?)").find(bulletContent)
            if (kpiMatch != null) {
                // Collect adjacent KPI metrics to render in a grid
                val kpis = mutableListOf<KpiItem>()
                kpis.add(KpiItem(kpiMatch.groupValues[1].trim(), kpiMatch.groupValues[2].trim()))
                i++
                while (i < lines.size) {
                    val nextTrim = lines[i].trim()
                    if (nextTrim.startsWith("* ") || nextTrim.startsWith("- ")) {
                        val nextContent = nextTrim.substring(2).trim()
                        val nextMatch = Regex("^\\*\\*([^:]+):\\*\\*\\s*(৳?[0-9০-৯,.]+(?:\\s*[a-zA-Z\u0980-\u09FF]+)?)").find(nextContent)
                        if (nextMatch != null) {
                            kpis.add(KpiItem(nextMatch.groupValues[1].trim(), nextMatch.groupValues[2].trim()))
                            i++
                            continue
                        }
                    }
                    break
                }
                if (kpis.size >= 2) {
                    blocks.add(MarkdownBlock.KpiGrid(kpis))
                    continue
                } else {
                    blocks.add(MarkdownBlock.BulletItem(0, bulletContent))
                    continue
                }
            } else {
                blocks.add(MarkdownBlock.BulletItem(0, bulletContent))
                i++
                continue
            }
        }

        // 8. Standalone Key-Value Metrics e.g. **মোট বিক্রি:** ৳৪,৩৫০
        val standaloneKpiMatch = Regex("^\\*\\*([^:]+):\\*\\*\\s*(৳?[0-9০-৯,.]+(?:\\s*[a-zA-Z\u0980-\u09FF]+)?)$").find(trimmed)
        if (standaloneKpiMatch != null) {
            val kpis = mutableListOf<KpiItem>()
            kpis.add(KpiItem(standaloneKpiMatch.groupValues[1].trim(), standaloneKpiMatch.groupValues[2].trim()))
            i++
            while (i < lines.size) {
                val nextTrim = lines[i].trim()
                val nextMatch = Regex("^\\*\\*([^:]+):\\*\\*\\s*(৳?[0-9০-৯,.]+(?:\\s*[a-zA-Z\u0980-\u09FF]+)?)$").find(nextTrim)
                if (nextMatch != null) {
                    kpis.add(KpiItem(nextMatch.groupValues[1].trim(), nextMatch.groupValues[2].trim()))
                    i++
                    continue
                }
                break
            }
            blocks.add(MarkdownBlock.KpiGrid(kpis))
            continue
        }

        // 9. Standard Paragraph (consume consecutive plain lines)
        val paraLines = mutableListOf<String>()
        while (i < lines.size && lines[i].trim().isNotEmpty() &&
            !lines[i].trim().startsWith("#") &&
            !lines[i].trim().startsWith("```") &&
            !lines[i].trim().startsWith(">") &&
            !lines[i].trim().startsWith("|") &&
            !lines[i].trim().startsWith("* ") &&
            !lines[i].trim().startsWith("- ") &&
            !Regex("^(\\d+)[.)]\\s+").containsMatchIn(lines[i].trim())
        ) {
            paraLines.add(lines[i].trim())
            i++
        }
        if (paraLines.isNotEmpty()) {
            blocks.add(MarkdownBlock.Paragraph(paraLines.joinToString(" ")))
        }
    }

    return blocks
}

// ─────────────────────────────────────────────────────────────────────────────
// INLINE MARKDOWN PARSER: STYLES **BOLD**, *ITALIC*, `CODE`, AND ৳ AMOUNTS
// ─────────────────────────────────────────────────────────────────────────────
fun parseInlineMarkdown(text: String, isDarkMode: Boolean): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        val len = text.length

        // Token regex to find **bold**, *italic*, `code`, and currency ৳amounts
        val tokenRegex = Regex("(\\*\\*(.*?)\\*\\*|\\*(.*?)\\*|`(.*?)`|(৳[0-9০-৯,]+(?:\\.[0-9০-৯]+)?))")
        val matches = tokenRegex.findAll(text)

        for (match in matches) {
            val matchStart = match.range.first
            val matchEnd = match.range.last + 1

            // Append plain text before the match
            if (matchStart > cursor) {
                append(text.substring(cursor, matchStart))
            }

            val fullMatch = match.value
            when {
                // Bold **text**
                fullMatch.startsWith("**") && fullMatch.endsWith("**") -> {
                    val inner = fullMatch.substring(2, fullMatch.length - 2)
                    withStyle(
                        SpanStyle(
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkMode) Color.White else Color(0xFF0F172A)
                        )
                    ) {
                        append(inner)
                    }
                }
                // Inline Code `text`
                fullMatch.startsWith("`") && fullMatch.endsWith("`") -> {
                    val inner = fullMatch.substring(1, fullMatch.length - 1)
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = if (isDarkMode) Color(0xFF262C40) else Color(0xFFF1F5F9),
                            color = if (isDarkMode) Color(0xFFE879F9) else Color(0xFF9333EA),
                            fontWeight = FontWeight.SemiBold
                        )
                    ) {
                        append(" $inner ")
                    }
                }
                // Italic *text*
                fullMatch.startsWith("*") && fullMatch.endsWith("*") -> {
                    val inner = fullMatch.substring(1, fullMatch.length - 1)
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(inner)
                    }
                }
                // Currency ৳ amount highlight
                fullMatch.startsWith("৳") -> {
                    withStyle(
                        SpanStyle(
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkMode) Color(0xFF34D399) else Color(0xFF059669)
                        )
                    ) {
                        append(fullMatch)
                    }
                }
                else -> append(fullMatch)
            }

            cursor = matchEnd
        }

        if (cursor < len) {
            append(text.substring(cursor, len))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// STRIP MARKDOWN UTILITY FOR TTS / AUDIO PLAYBACK
// ─────────────────────────────────────────────────────────────────────────────
fun stripMarkdownToPlainText(markdown: String): String {
    return markdown
        .replace(Regex("```[a-zA-Z]*\\n[\\s\\S]*?```"), "কোড ব্লক।")
        .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
        .replace(Regex("\\*(.*?)\\*"), "$1")
        .replace(Regex("`(.*?)`"), "$1")
        .replace(Regex("^#+\\s*", RegexOption.MULTILINE), "")
        .replace(Regex("^[-*•]\\s*", RegexOption.MULTILINE), "")
        .replace(Regex("^>+\\s*", RegexOption.MULTILINE), "")
        .replace(Regex("\\|"), " ")
        .replace(Regex("[-*_]{3,}"), "")
        .trim()
}

