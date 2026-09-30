package com.commvault.commlink.ui.assistant

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DocumentExporter {

    /**
     * Converts AI text / markdown response into a formatted PDF file and opens the share/view intent.
     */
    fun exportToPdf(context: Context, title: String, content: String) {
        try {
            val pdfDocument = PdfDocument()
            val pageWidth = 595 // A4 standard width in points
            val pageHeight = 842 // A4 standard height in points
            val margin = 40
            val contentWidth = pageWidth - (margin * 2)

            // Clean markdown markup for clean PDF printing
            val cleanContent = content
                .replace(Regex("!\\[.*?\\]\\(.*?\\)"), "[Image]")
                .replace(Regex("[#*`>]"), "")

            val titlePaint = TextPaint().apply {
                color = Color.parseColor("#0F172A") // Commvault Navy
                textSize = 18f
                isFakeBoldText = true
                isAntiAlias = true
            }

            val metaPaint = TextPaint().apply {
                color = Color.parseColor("#64748B")
                textSize = 10f
                isAntiAlias = true
            }

            val bodyPaint = TextPaint().apply {
                color = Color.parseColor("#1E293B")
                textSize = 12f
                isAntiAlias = true
            }

            val linePaint = Paint().apply {
                color = Color.parseColor("#039855") // Primary Emerald accent
                strokeWidth = 2f
            }

            val dateStr = SimpleDateFormat("MMMM dd, yyyy HH:mm", Locale.getDefault()).format(Date())

            val staticLayout = StaticLayout.Builder
                .obtain(cleanContent, 0, cleanContent.length, bodyPaint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(4f, 1f)
                .build()

            val linesPerPage = 45
            val totalLines = staticLayout.lineCount
            var currentLine = 0
            var pageNumber = 1

            while (currentLine < totalLines || currentLine == 0) {
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                var yPos = margin.toFloat()

                // Header on First Page
                if (pageNumber == 1) {
                    canvas.drawText("CommLink AI Document", margin.toFloat(), yPos + 15, titlePaint)
                    canvas.drawText("Generated on $dateStr", margin.toFloat(), yPos + 32, metaPaint)
                    canvas.drawLine(margin.toFloat(), yPos + 40, (pageWidth - margin).toFloat(), yPos + 40, linePaint)
                    yPos += 60f
                } else {
                    canvas.drawText("CommLink AI Document (Page $pageNumber)", margin.toFloat(), yPos + 10, metaPaint)
                    yPos += 30f
                }

                val startLine = currentLine
                val endLine = minOf(startLine + linesPerPage, totalLines)

                val startOffset = staticLayout.getLineStart(startLine)
                val endOffset = if (endLine < totalLines) staticLayout.getLineEnd(endLine - 1) else cleanContent.length
                val pageText = cleanContent.substring(startOffset, endOffset)

                val pageLayout = StaticLayout.Builder
                    .obtain(pageText, 0, pageText.length, bodyPaint, contentWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(4f, 1f)
                    .build()

                canvas.save()
                canvas.translate(margin.toFloat(), yPos)
                pageLayout.draw(canvas)
                canvas.restore()

                currentLine = endLine
                pdfDocument.finishPage(page)
                pageNumber++

                if (startLine == endLine) break
            }

            // Save PDF file to cache directory
            val fileName = "CommLink_Doc_${System.currentTimeMillis()}.pdf"
            val file = File(context.cacheDir, fileName)
            val outputStream = FileOutputStream(file)
            pdfDocument.writeTo(outputStream)
            outputStream.flush()
            outputStream.close()
            pdfDocument.close()

            openOrShareFile(context, file, "application/pdf", "Generated PDF Document")
        } catch (e: Exception) {
            Toast.makeText(context, "PDF export failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Exports presentation slides as an interactive HTML slide deck.
     */
    fun exportToPresentation(context: Context, title: String, content: String) {
        try {
            val slides = content.split(Regex("(?m)^#{1,2}\\s+")).filter { it.isNotBlank() }
            val slidesHtml = StringBuilder()

            slides.forEachIndexed { index, slideText ->
                val lines = slideText.trim().lines()
                val slideTitle = lines.firstOrNull() ?: "Slide ${index + 1}"
                val slideBody = lines.drop(1).joinToString("<br>") { "• ${it.removePrefix("- ").removePrefix("* ").trim()}" }

                slidesHtml.append(
                    """
                    <div class="slide">
                        <div class="slide-number">Slide ${index + 1} of ${slides.size}</div>
                        <h2>$slideTitle</h2>
                        <div class="slide-content">$slideBody</div>
                    </div>
                    """.trimIndent()
                )
            }

            val fullHtml = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="utf-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <title>$title</title>
                    <style>
                        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #0F172A; color: white; margin: 0; padding: 20px; }
                        .deck-container { max-width: 800px; margin: 0 auto; display: flex; flex-direction: column; gap: 24px; }
                        .slide { background: #1E293B; border-radius: 16px; padding: 32px; border: 1px solid #334155; box-shadow: 0 10px 25px rgba(0,0,0,0.3); min-height: 280px; }
                        .slide-number { font-size: 12px; color: #039855; font-weight: bold; letter-spacing: 1px; text-transform: uppercase; margin-bottom: 12px; }
                        h2 { color: #FFFFFF; font-size: 24px; margin-top: 0; border-bottom: 2px solid #039855; padding-bottom: 10px; }
                        .slide-content { font-size: 16px; line-height: 1.8; color: #CBD5E1; margin-top: 16px; }
                    </style>
                </head>
                <body>
                    <div class="deck-container">
                        $slidesHtml
                    </div>
                </body>
                </html>
            """.trimIndent()

            val fileName = "CommLink_Presentation_${System.currentTimeMillis()}.html"
            val file = File(context.cacheDir, fileName)
            file.writeText(fullHtml)

            openOrShareFile(context, file, "text/html", "Interactive Presentation Deck")
        } catch (e: Exception) {
            Toast.makeText(context, "Presentation export failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Exports raw text, code, or markdown.
     */
    fun exportToTextFile(context: Context, fileName: String, content: String, mimeType: String = "text/plain") {
        try {
            val file = File(context.cacheDir, fileName)
            file.writeText(content)
            openOrShareFile(context, file, mimeType, "CommLink Document")
        } catch (e: Exception) {
            Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openOrShareFile(context: Context, file: File, mimeType: String, title: String) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(intent, "Open or Share $title").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
