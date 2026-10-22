package com.angel.paperlite.share

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

object Sharer {
    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun sharePdf(context: Context, pdf: File) = sharePdfUri(context, uriFor(context, pdf))

    fun sharePdfUri(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartir PDF"))
    }

    fun shareImages(context: Context, images: List<File>) {
        val uris = ArrayList(images.map { uriFor(context, it) })
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/jpeg"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartir imágenes"))
    }

    fun savePdfToDownloads(context: Context, pdf: File) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, pdf.name)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        copyInto(context, MediaStore.Downloads.EXTERNAL_CONTENT_URI, values, pdf)
    }

    fun saveImagesToGallery(context: Context, title: String, images: List<File>) {
        images.forEachIndexed { i, file ->
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "${title}_${i + 1}.jpg")
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PaperLite")
            }
            copyInto(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values, file)
        }
    }

    private fun copyInto(context: Context, collection: Uri, values: ContentValues, source: File) {
        val resolver = context.contentResolver
        val target = resolver.insert(collection, values) ?: return
        resolver.openOutputStream(target)?.use { out -> source.inputStream().use { it.copyTo(out) } }
    }
}
