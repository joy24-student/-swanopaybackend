package com.example.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class InvoiceLine(
    val description: String,
    val quantity: Double,
    val unitPrice: Double,
    val lineTotal: Double
)

data class InvoiceDocument(
    val invoiceNumber: String,
    val issuedAt: Long,
    val businessName: String,
    val businessEmail: String,
    val businessPhone: String,
    val customerName: String,
    val customerPhone: String,
    val paymentMethod: String,
    val paymentStatus: String,
    val lines: List<InvoiceLine>,
    val subtotal: Double,
    val discount: Double,
    val grandTotal: Double
)

object InvoiceDocumentManager {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val LEFT = 42f
    private const val RIGHT = 553f
    private const val ROWS_PER_PAGE = 17

    fun writePdf(output: OutputStream, invoice: InvoiceDocument) {
        val document = PdfDocument()
        try {
            val chunks = invoice.lines.ifEmpty {
                listOf(InvoiceLine("No line-item detail", 1.0, invoice.grandTotal, invoice.grandTotal))
            }.chunked(ROWS_PER_PAGE)
            chunks.forEachIndexed { pageIndex, lines ->
                val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIndex + 1).create()
                val page = document.startPage(pageInfo)
                drawPage(page.canvas, invoice, lines, pageIndex + 1, chunks.size, pageIndex == chunks.lastIndex)
                document.finishPage(page)
            }
            document.writeTo(output)
        } finally {
            document.close()
        }
    }

    fun print(context: Context, invoice: InvoiceDocument) {
        val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        manager.print(
            "Invoice-${safeFileName(invoice.invoiceNumber)}",
            InvoicePrintAdapter(invoice),
            PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                .build()
        )
    }

    fun share(context: Context, invoice: InvoiceDocument) {
        val directory = File(context.cacheDir, "invoices").apply { mkdirs() }
        val file = File(directory, "Invoice-${safeFileName(invoice.invoiceNumber)}.pdf")
        FileOutputStream(file).use { writePdf(it, invoice) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Invoice ${invoice.invoiceNumber} from ${invoice.businessName}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Share invoice PDF"))
    }

    private fun drawPage(
        canvas: android.graphics.Canvas,
        invoice: InvoiceDocument,
        lines: List<InvoiceLine>,
        pageNumber: Int,
        pageCount: Int,
        showTotals: Boolean
    ) {
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42); textSize = 22f; typeface = Typeface.DEFAULT_BOLD
        }
        val heading = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42); textSize = 11f; typeface = Typeface.DEFAULT_BOLD
        }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(51, 65, 85); textSize = 10f }
        val muted = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(100, 116, 139); textSize = 9f }
        val money = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42); textSize = 10f; textAlign = Paint.Align.RIGHT
        }
        val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(226, 232, 240); strokeWidth = 1f }

        canvas.drawText(invoice.businessName.take(55), LEFT, 52f, title)
        canvas.drawText("OFFICIAL INVOICE", RIGHT, 52f, heading.apply { textAlign = Paint.Align.RIGHT })
        heading.textAlign = Paint.Align.LEFT
        canvas.drawText(invoice.businessEmail.take(65), LEFT, 70f, muted)
        canvas.drawText(invoice.businessPhone.take(35), LEFT, 84f, muted)
        canvas.drawText(invoice.invoiceNumber, RIGHT, 70f, money)
        canvas.drawText(
            SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(invoice.issuedAt)),
            RIGHT, 84f, money
        )
        canvas.drawLine(LEFT, 100f, RIGHT, 100f, rule)
        canvas.drawText("BILLED TO", LEFT, 120f, heading)
        canvas.drawText(invoice.customerName.take(60), LEFT, 137f, body)
        canvas.drawText(invoice.customerPhone.take(35), LEFT, 152f, muted)
        canvas.drawText("Payment: ${invoice.paymentMethod}", 350f, 125f, body)
        canvas.drawText("Status: ${invoice.paymentStatus}", 350f, 142f, body)

        var y = 184f
        canvas.drawRect(LEFT, y - 15f, RIGHT, y + 5f, Paint().apply { color = Color.rgb(241, 245, 249) })
        canvas.drawText("DESCRIPTION", LEFT + 5f, y, heading)
        canvas.drawText("QTY", 385f, y, heading)
        canvas.drawText("UNIT", 465f, y, heading)
        canvas.drawText("TOTAL", RIGHT, y, heading.apply { textAlign = Paint.Align.RIGHT })
        heading.textAlign = Paint.Align.LEFT
        y += 25f
        lines.forEach { line ->
            canvas.drawText(line.description.take(52), LEFT + 5f, y, body)
            canvas.drawText(formatQuantity(line.quantity), 385f, y, body)
            canvas.drawText("BDT ${moneyText(line.unitPrice)}", 465f, y, money)
            canvas.drawText("BDT ${moneyText(line.lineTotal)}", RIGHT, y, money)
            y += 26f
            canvas.drawLine(LEFT, y - 15f, RIGHT, y - 15f, rule)
        }

        if (showTotals) {
            y = maxOf(y + 15f, 665f)
            canvas.drawText("Subtotal", 400f, y, body)
            canvas.drawText("BDT ${moneyText(invoice.subtotal)}", RIGHT, y, money)
            y += 20f
            canvas.drawText("Discount", 400f, y, body)
            canvas.drawText("- BDT ${moneyText(invoice.discount)}", RIGHT, y, money)
            y += 24f
            canvas.drawLine(395f, y - 14f, RIGHT, y - 14f, rule)
            canvas.drawText("GRAND TOTAL", 400f, y, heading)
            canvas.drawText("BDT ${moneyText(invoice.grandTotal)}", RIGHT, y, money.apply {
                typeface = Typeface.DEFAULT_BOLD; textSize = 12f
            })
        }
        canvas.drawText("Generated by SwapnoPay • Merchant-verified data", LEFT, 808f, muted)
        canvas.drawText("Page $pageNumber of $pageCount", RIGHT, 808f, money.apply {
            textSize = 9f; typeface = Typeface.DEFAULT
        })
    }

    private class InvoicePrintAdapter(private val invoice: InvoiceDocument) : PrintDocumentAdapter() {
        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes?,
            cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback,
            extras: Bundle?
        ) {
            if (cancellationSignal?.isCanceled == true) {
                callback.onLayoutCancelled()
                return
            }
            val pages = maxOf(1, (invoice.lines.size + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE)
            callback.onLayoutFinished(
                PrintDocumentInfo.Builder("Invoice-${safeFileName(invoice.invoiceNumber)}.pdf")
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(pages)
                    .build(),
                true
            )
        }

        override fun onWrite(
            pages: Array<out PageRange>?,
            destination: ParcelFileDescriptor,
            cancellationSignal: CancellationSignal?,
            callback: WriteResultCallback
        ) {
            if (cancellationSignal?.isCanceled == true) {
                callback.onWriteCancelled()
                return
            }
            runCatching {
                FileOutputStream(destination.fileDescriptor).use { writePdf(it, invoice) }
            }.onSuccess {
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            }.onFailure {
                callback.onWriteFailed(it.message)
            }
        }
    }

    private fun formatQuantity(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.2f", value)

    private fun moneyText(value: Double): String = String.format(Locale.US, "%,.2f", value)

    private fun safeFileName(value: String): String =
        value.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80).ifBlank { "invoice" }
}
