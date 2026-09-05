package dev.vic41148.somn.feature.analytics.ui

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import dev.vic41148.somn.core.domain.usecase.ReportPdfModel
import java.io.File

/**
 * R3 Reports: on-device PDF export — the Tier-2 doctor-ready report, no cloud.
 * Draws [ReportPdfModel] (unit-tested words) with framework PdfDocument primitives
 * plus a simple score-trend bar strip. Callers share the file via FileProvider.
 */
object ReportPdfRenderer {

    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 48f
    private const val CONTENT_W = PAGE_W - 2 * MARGIN

    fun render(
        context: Context,
        model: ReportPdfModel,
        scoreTrend: List<Int>,
        fileName: String
    ): File {
        val doc = PdfDocument()
        val titlePaint = Paint().apply {
            color = 0xFF111111.toInt()
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subPaint = Paint().apply {
            color = 0xFF555555.toInt()
            textSize = 11f
        }
        val headPaint = Paint().apply {
            color = 0xFF111111.toInt()
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val rowPaint = Paint().apply {
            color = 0xFF222222.toInt()
            textSize = 11f
        }
        val barPaint = Paint().apply { color = 0xFF4CAF50.toInt() }
        val barTrackPaint = Paint().apply { color = 0xFFE0E0E0.toInt() }
        val linePaint = Paint().apply {
            color = 0xFFCCCCCC.toInt()
            strokeWidth = 1f
        }

        var page = startPage(doc)
        var y = 64f

        y = drawWrapped(page, model.title, MARGIN, y, CONTENT_W, titlePaint, 26f)
        y = drawWrapped(page, model.subtitle, MARGIN, y + 4f, CONTENT_W, subPaint, 16f)
        y += 12f

        if (scoreTrend.isNotEmpty()) {
            val (np, ny) = ensureSpace(doc, page, y, 96f, headPaint, subPaint)
            page = np
            y = ny
            y = drawWrapped(page, "Nightly scores", MARGIN, y, CONTENT_W, headPaint, 20f)
            y += 8f
            val bars = scoreTrend.takeLast(30)
            val barW = CONTENT_W / 30f
            bars.forEachIndexed { i, score ->
                val clamped = score.coerceIn(0, 100) / 100f
                val x = MARGIN + i * barW + 1f
                page.canvas.drawRect(x, y, x + barW - 2f, y + 44f, barTrackPaint)
                page.canvas.drawRect(x, y + 44f * (1f - clamped), x + barW - 2f, y + 44f, barPaint)
            }
            y += 60f
        }

        model.sections.forEach { section ->
            val (np, ny) = ensureSpace(doc, page, y, 64f, headPaint, subPaint)
            page = np
            y = ny
            y = drawWrapped(page, section.heading, MARGIN, y, CONTENT_W, headPaint, 20f)
            y += 4f
            section.rows.forEach { (label, value) ->
                val (np2, ny2) = ensureSpace(doc, page, y, 40f, headPaint, subPaint)
                page = np2
                y = ny2
                y = drawWrapped(page, "$label: $value", MARGIN, y, CONTENT_W, rowPaint, 15f)
            }
            page.canvas.drawLine(MARGIN, y + 4f, MARGIN + CONTENT_W, y + 4f, linePaint)
            y += 16f
        }

        doc.finishPage(page)

        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val out = File(dir, fileName)
        out.outputStream().use { doc.writeTo(it) }
        doc.close()
        return out
    }

    private fun startPage(doc: PdfDocument): PdfDocument.Page =
        doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create())

    /** Finishes the page and opens a fresh one when fewer than [need] points remain. */
    private fun ensureSpace(
        doc: PdfDocument,
        page: PdfDocument.Page,
        y: Float,
        need: Float,
        headPaint: Paint,
        subPaint: Paint
    ): Pair<PdfDocument.Page, Float> {
        if (y + need > PAGE_H - MARGIN) {
            drawFooter(page, subPaint)
            doc.finishPage(page)
            return startPage(doc) to 64f
        }
        return page to y
    }

    private fun drawFooter(page: PdfDocument.Page, paint: Paint) {
        page.canvas.drawText(
            "Generated on-device by Somn — your data never left this phone.",
            MARGIN, PAGE_H - 32f, paint
        )
    }

    /** Draws [text] wrapped to [maxWidth]. It returns the y for the next element. */
    private fun drawWrapped(
        page: PdfDocument.Page,
        text: String,
        x: Float,
        y: Float,
        maxWidth: Float,
        paint: Paint,
        lineHeight: Float
    ): Float {
        var cy = y
        var line = StringBuilder()
        text.split(" ").forEach { word ->
            val trial = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(trial) > maxWidth && line.isNotEmpty()) {
                page.canvas.drawText(line.toString(), x, cy, paint)
                cy += lineHeight
                line = StringBuilder(word)
            } else {
                line = StringBuilder(trial)
            }
        }
        if (line.isNotEmpty()) {
            page.canvas.drawText(line.toString(), x, cy, paint)
            cy += lineHeight
        }
        return cy
    }
}
