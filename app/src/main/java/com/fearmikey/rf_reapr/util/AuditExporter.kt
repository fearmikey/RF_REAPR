package com.fearmikey.rf_reapr.util

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.fearmikey.rf_reapr.domain.model.ComplianceControl
import com.fearmikey.rf_reapr.domain.model.ComplianceStatus
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AuditExporter {

    private const val CYBER_CYAN = 0xFF00B0FF.toInt()
    private const val MIDNIGHT_BLUE = 0xFF00050A.toInt()

    fun generateTextReport(frameworkId: String, controls: List<ComplianceControl>): String {
        val sb = StringBuilder()
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        
        sb.append("RF_REAPR AUDIT REPORT\n")
        sb.append("=====================\n")
        sb.append("Framework: $frameworkId\n")
        sb.append("Generated: $date\n\n")
        
        controls.forEach { control ->
            val status = when (control.status) {
                ComplianceStatus.COMPLIANT -> "[COMPLIANT]"
                ComplianceStatus.NON_COMPLIANT -> "[NON-COMPLIANT]"
                ComplianceStatus.NONE -> "[NOT AUDITED]"
            }
            sb.append("${control.id}: ${control.name} $status\n")
            sb.append("Description: ${control.description}\n")
            if (control.notes.isNotBlank()) {
                sb.append("Auditor Notes: ${control.notes}\n")
            }
            sb.append("---------------------\n")
        }
        
        return sb.toString()
    }

    fun generatePdfReport(
        outputStream: OutputStream,
        frameworkId: String,
        controls: List<ComplianceControl>
    ) {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 Size
        
        var currentPage = pdfDocument.startPage(pageInfo)
        var canvas = currentPage.canvas
        var yPosition = 40f
        
        val textPaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 12f
            typeface = Typeface.MONOSPACE
        }
        
        val titlePaint = TextPaint().apply {
            color = CYBER_CYAN
            textSize = 24f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        
        val headerPaint = TextPaint().apply {
            color = MIDNIGHT_BLUE
            textSize = 14f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }

        // Draw Header
        canvas.drawRect(0f, 0f, 595f, 80f, Paint().apply { color = MIDNIGHT_BLUE })
        canvas.drawText("RF_REAPR AUDIT", 20f, 45f, titlePaint)
        
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        textPaint.color = Color.WHITE
        canvas.drawText("Framework: $frameworkId", 20f, 65f, textPaint)
        canvas.drawText("Date: $date", 450f, 65f, textPaint)
        
        yPosition = 110f
        textPaint.color = Color.BLACK

        controls.forEach { control ->
            // Check for new page
            val approxHeight = 150f
            if (yPosition + approxHeight > 780f) {
                pdfDocument.finishPage(currentPage)
                currentPage = pdfDocument.startPage(pageInfo)
                canvas = currentPage.canvas
                yPosition = 40f
            }

            // Draw ID and Status
            canvas.drawText(control.id, 20f, yPosition, headerPaint)
            
            val statusColor = when (control.status) {
                ComplianceStatus.COMPLIANT -> 0xFF00E676.toInt()
                ComplianceStatus.NON_COMPLIANT -> 0xFFEF5350.toInt()
                ComplianceStatus.NONE -> Color.LTGRAY
            }
            val statusText = when (control.status) {
                ComplianceStatus.COMPLIANT -> "COMPLIANT"
                ComplianceStatus.NON_COMPLIANT -> "NON-COMPLIANT"
                ComplianceStatus.NONE -> "NOT AUDITED"
            }
            
            val statusPaint = Paint().apply { color = statusColor }
            canvas.drawRect(400f, yPosition - 15f, 575f, yPosition + 5f, statusPaint)
            
            val statusTextPaint = Paint().apply {
                color = if (control.status == ComplianceStatus.NONE) Color.DKGRAY else Color.WHITE
                textSize = 10f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            }
            canvas.drawText(statusText, 410f, yPosition - 2f, statusTextPaint)
            
            yPosition += 25f
            
            // Draw Control Name
            canvas.drawText(control.name, 20f, yPosition, Paint(headerPaint).apply { textSize = 12f })
            yPosition += 20f
            
            // Draw Description (Wrapped)
            val descLayout = StaticLayout.Builder.obtain(control.description, 0, control.description.length, textPaint, 555)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1f)
                .setIncludePad(false)
                .build()
            
            canvas.save()
            canvas.translate(20f, yPosition)
            descLayout.draw(canvas)
            canvas.restore()
            yPosition += descLayout.height + 10f
            
            // Draw Notes if present
            if (control.notes.isNotBlank()) {
                val notesHeader = "Notes:"
                canvas.drawText(notesHeader, 20f, yPosition, Paint(textPaint).apply { typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD_ITALIC) })
                yPosition += 15f
                
                val notesLayout = StaticLayout.Builder.obtain(control.notes, 0, control.notes.length, textPaint, 555)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, 1f)
                    .setIncludePad(false)
                    .build()
                
                canvas.save()
                canvas.translate(20f, yPosition)
                notesLayout.draw(canvas)
                canvas.restore()
                yPosition += notesLayout.height + 20f
            } else {
                yPosition += 10f
            }
            
            // Draw Separator Line
            canvas.drawLine(20f, yPosition, 575f, yPosition, Paint().apply { color = Color.LTGRAY; strokeWidth = 1f })
            yPosition += 30f
        }

        pdfDocument.finishPage(currentPage)
        pdfDocument.writeTo(outputStream)
        pdfDocument.close()
    }
}
