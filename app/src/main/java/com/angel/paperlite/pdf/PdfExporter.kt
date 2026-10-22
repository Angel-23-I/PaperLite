package com.angel.paperlite.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import java.io.File

object PdfExporter {
    private const val PAGE_WIDTH_PT = 595 // A4 en puntos
    private const val MAX_EDGE_PX = 2339 // A4 a ~200 dpi

    /** Crea un PDF (una imagen por página, ajustada al ancho A4) en el caché compartible. */
    fun create(context: Context, title: String, pages: List<File>): File {
        val dir = File(context.cacheDir, "shared").also { it.mkdirs() }
        val out = File(dir, safeName(title) + ".pdf")
        val doc = PdfDocument()
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        try {
            pages.forEachIndexed { i, file ->
                val bmp = decode(file) ?: return@forEachIndexed
                val h = (PAGE_WIDTH_PT * bmp.height.toFloat() / bmp.width).toInt()
                val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH_PT, h, i + 1).create())
                page.canvas.drawBitmap(bmp, null, Rect(0, 0, PAGE_WIDTH_PT, h), paint)
                doc.finishPage(page)
                bmp.recycle()
            }
            out.outputStream().use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
        return out
    }

    fun safeName(title: String) =
        title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifEmpty { "documento" }

    private fun decode(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE_PX) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
