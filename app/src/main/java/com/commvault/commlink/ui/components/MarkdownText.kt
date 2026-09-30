package com.commvault.commlink.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.commvault.commlink.ui.theme.CommvaultNavy

sealed class MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock()
    data class Heading(val level: Int, val text: String) : MarkdownBlock()
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock()
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MarkdownBlock()
    data class ListItem(val isNumbered: Boolean, val index: Int, val text: String) : MarkdownBlock()
    data class ImageBlock(val alt: String, val url: String) : MarkdownBlock()
}

@Composable
fun MarkdownContent(
    content: String,
    modifier: Modifier = Modifier,
    textColor: Color = CommvaultNavy,
    primaryColor: Color = Color(0xFF039855),
    isStreaming: Boolean = false
) {
    val context = LocalContext.current
    val blocks = remember(content) { parseMarkdown(content) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> {
                    val fontSize = when (block.level) {
                        1 -> 18.sp
                        2 -> 16.sp
                        else -> 15.sp
                    }
                    ClickableMarkdownParagraph(
                        text = block.text,
                        fontSize = fontSize,
                        fontWeight = FontWeight.Bold,
                        textColor = textColor,
                        lineHeight = 22.sp,
                        onUrlClick = { url -> openUrl(context, url) }
                    )
                }
                is MarkdownBlock.Paragraph -> {
                    ClickableMarkdownParagraph(
                        text = block.text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        textColor = textColor,
                        lineHeight = 20.sp,
                        onUrlClick = { url -> openUrl(context, url) }
                    )
                }
                is MarkdownBlock.ListItem -> {
                    Row(
                        modifier = Modifier.padding(start = 6.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = if (block.isNumbered) "${block.index}. " else "• ",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (textColor == Color.White) Color.White else primaryColor
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        ClickableMarkdownParagraph(
                            text = block.text,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                            textColor = textColor,
                            lineHeight = 20.sp,
                            onUrlClick = { url -> openUrl(context, url) }
                        )
                    }
                }
                is MarkdownBlock.CodeBlock -> {
                    CodeBlockCard(language = block.language, code = block.code)
                }
                is MarkdownBlock.Table -> {
                    MarkdownTableView(headers = block.headers, rows = block.rows, primaryColor = primaryColor)
                }
                is MarkdownBlock.ImageBlock -> {
                    GeneratedImageCard(alt = block.alt, url = block.url, primaryColor = primaryColor)
                }
            }
        }
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    try {
        val validUrl = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(validUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Could not open link: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun ClickableMarkdownParagraph(
    text: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    fontWeight: FontWeight,
    textColor: Color,
    lineHeight: androidx.compose.ui.unit.TextUnit,
    onUrlClick: (String) -> Unit
) {
    val annotatedString = remember(text, textColor) { parseInlineMarkdownWithLinks(text, textColor) }

    ClickableText(
        text = annotatedString,
        style = MaterialTheme.typography.bodyMedium.copy(
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = textColor,
            lineHeight = lineHeight
        ),
        onClick = { offset ->
            annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    onUrlClick(annotation.item)
                }
        }
    )
}

@Composable
fun GeneratedImageCard(alt: String, url: String, primaryColor: Color) {
    val context = LocalContext.current

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 220.dp, max = 340.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(Color(0xFFF1F5F9)),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(url)
                        .crossfade(true)
                        .build(),
                    contentDescription = alt,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = alt.ifEmpty { "AI Generated Image" },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = CommvaultNavy,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = { openUrl(context, url) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp), tint = primaryColor)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("View HD", fontSize = 12.sp, color = primaryColor, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun CodeBlockCard(language: String, code: String) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF1E293B),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.ifEmpty { "code" },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8)
                )
                IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(code))
                        Toast.makeText(context, "Code copied!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Code",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                SelectionContainer {
                    Text(
                        text = code,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = Color(0xFFE2E8F0)
                    )
                }
            }
        }
    }
}

@Composable
fun MarkdownTableView(
    headers: List<String>,
    rows: List<List<String>>,
    primaryColor: Color
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .background(primaryColor.copy(alpha = 0.12f))
                        .border(1.dp, Color(0xFFE2E8F0))
                        .padding(vertical = 8.dp)
                ) {
                    headers.forEach { header ->
                        Box(
                            modifier = Modifier
                                .widthIn(min = 90.dp, max = 220.dp)
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = parseInlineMarkdownWithLinks(header.trim(), CommvaultNavy),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = CommvaultNavy
                            )
                        }
                    }
                }

                rows.forEachIndexed { rowIndex, row ->
                    val bgColor = if (rowIndex % 2 == 0) Color.White else Color(0xFFF8FAFC)
                    Row(
                        modifier = Modifier
                            .background(bgColor)
                            .border(0.5.dp, Color(0xFFF1F5F9))
                            .padding(vertical = 8.dp)
                    ) {
                        headers.indices.forEach { colIndex ->
                            val cellText = if (colIndex < row.size) row[colIndex].trim() else ""
                            Box(
                                modifier = Modifier
                                    .widthIn(min = 90.dp, max = 220.dp)
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                SelectionContainer {
                                    Text(
                                        text = parseInlineMarkdownWithLinks(cellText, CommvaultNavy),
                                        fontSize = 12.sp,
                                        color = CommvaultNavy
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

fun parseMarkdown(text: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = text.lines()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]

        // 1. Image block ![alt](url)
        val imgMatch = Regex("^!\\[(.*?)\\]\\((https?://[^)]+)\\)").find(line.trim())
        if (imgMatch != null) {
            val alt = imgMatch.groupValues[1]
            val url = imgMatch.groupValues[2]
            blocks.add(MarkdownBlock.ImageBlock(alt, url))
            i++
            continue
        }

        // 2. Code block fence ```
        if (line.trim().startsWith("```")) {
            val language = line.trim().removePrefix("```").trim()
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            blocks.add(MarkdownBlock.CodeBlock(language, codeLines.joinToString("\n")))
            i++
            continue
        }

        // 3. Table detection
        if (line.trim().startsWith("|") && line.trim().endsWith("|")) {
            val tableLines = mutableListOf<String>()
            while (i < lines.size && lines[i].trim().startsWith("|") && lines[i].trim().endsWith("|")) {
                tableLines.add(lines[i].trim())
                i++
            }

            if (tableLines.size >= 2) {
                val headerRow = tableLines[0].split("|").filter { it.isNotBlank() }
                val rows = mutableListOf<List<String>>()

                for (rIndex in 1 until tableLines.size) {
                    val rawRow = tableLines[rIndex]
                    if (rawRow.contains("---") || rawRow.matches(Regex("\\|[\\s\\-:]+\\|.*"))) {
                        continue
                    }
                    val cols = rawRow.split("|").filterIndexed { idx, _ -> idx > 0 && idx <= headerRow.size }
                    rows.add(cols)
                }
                blocks.add(MarkdownBlock.Table(headerRow, rows))
                continue
            }
        }

        // 4. Headings (#, ##, ###)
        if (line.startsWith("#")) {
            val hashes = line.takeWhile { it == '#' }.length
            val headingText = line.substring(hashes).trim()
            blocks.add(MarkdownBlock.Heading(hashes, headingText))
            i++
            continue
        }

        // 5. Bullet lists (- or *)
        if (line.trim().startsWith("- ") || line.trim().startsWith("* ")) {
            val listText = line.trim().substring(2).trim()
            blocks.add(MarkdownBlock.ListItem(isNumbered = false, index = 0, text = listText))
            i++
            continue
        }

        // 6. Numbered lists (1. , 2. )
        val numberedMatch = Regex("^(\\d+)\\.\\s+(.*)").find(line.trim())
        if (numberedMatch != null) {
            val num = numberedMatch.groupValues[1].toIntOrNull() ?: 1
            val listText = numberedMatch.groupValues[2]
            blocks.add(MarkdownBlock.ListItem(isNumbered = true, index = num, text = listText))
            i++
            continue
        }

        // 7. Normal Paragraph
        if (line.isNotBlank()) {
            blocks.add(MarkdownBlock.Paragraph(line))
        }

        i++
    }

    return blocks
}

fun parseInlineMarkdownWithLinks(text: String, baseColor: Color): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        val length = text.length
        val linkColor = Color(0xFF2563EB) // Royal blue

        while (cursor < length) {
            // Check for [link text](url)
            if (text[cursor] == '[') {
                val textEnd = text.indexOf(']', cursor + 1)
                if (textEnd != -1 && textEnd + 1 < length && text[textEnd + 1] == '(') {
                    val urlEnd = text.indexOf(')', textEnd + 2)
                    if (urlEnd != -1) {
                        val linkTitle = text.substring(cursor + 1, textEnd)
                        val url = text.substring(textEnd + 2, urlEnd)

                        val startPos = this.length
                        pushStringAnnotation(tag = "URL", annotation = url)
                        pushStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline, fontWeight = FontWeight.SemiBold))
                        append(linkTitle)
                        pop()
                        pop()

                        cursor = urlEnd + 1
                        continue
                    }
                }
            }

            // Check for raw https:// or http:// URLs
            if (text.startsWith("http://", cursor) || text.startsWith("https://", cursor)) {
                val endOfUrl = text.indexOfAny(charArrayOf(' ', '\n', '\t', ')', ']'), cursor).let { if (it == -1) length else it }
                val rawUrl = text.substring(cursor, endOfUrl)

                pushStringAnnotation(tag = "URL", annotation = rawUrl)
                pushStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline, fontWeight = FontWeight.SemiBold))
                append(rawUrl)
                pop()
                pop()

                cursor = endOfUrl
                continue
            }

            // Check for **bold**
            if (cursor + 1 < length && text[cursor] == '*' && text[cursor + 1] == '*') {
                val endIdx = text.indexOf("**", cursor + 2)
                if (endIdx != -1) {
                    val boldText = text.substring(cursor + 2, endIdx)
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(boldText)
                    pop()
                    cursor = endIdx + 2
                    continue
                }
            }

            // Check for *italic*
            if (text[cursor] == '*' && (cursor == 0 || text[cursor - 1] != '*')) {
                val endIdx = text.indexOf('*', cursor + 1)
                if (endIdx != -1 && (endIdx + 1 >= length || text[endIdx + 1] != '*')) {
                    val italicText = text.substring(cursor + 1, endIdx)
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(italicText)
                    pop()
                    cursor = endIdx + 1
                    continue
                }
            }

            // Check for `inline code`
            if (text[cursor] == '`') {
                val endIdx = text.indexOf('`', cursor + 1)
                if (endIdx != -1) {
                    val codeText = text.substring(cursor + 1, endIdx)
                    pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            background = if (baseColor == Color.White) Color.White.copy(alpha = 0.2f) else Color(0xFFF1F5F9)
                        )
                    )
                    append(" $codeText ")
                    pop()
                    cursor = endIdx + 1
                    continue
                }
            }

            append(text[cursor])
            cursor++
        }
    }
}
