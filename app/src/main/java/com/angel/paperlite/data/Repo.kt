package com.angel.paperlite.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Repo(private val context: Context, private val dao: DocDao) {
    private val root = File(context.filesDir, "scans")

    val docs: Flow<List<Doc>> = dao.observeAll()

    fun observe(id: Long): Flow<Doc?> = dao.observe(id)

    fun pages(id: Long): List<File> =
        File(root, id.toString()).listFiles { f -> f.extension == "jpg" }
            ?.sortedBy { it.name }
            .orEmpty()

    suspend fun createDocument(uris: List<Uri>): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val title = "Escaneo " + SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()).format(Date(now))
        val id = dao.insert(Doc(title = title, createdAt = now, updatedAt = now, pageCount = 0))
        appendPages(id, uris)
        id
    }

    suspend fun appendPages(id: Long, uris: List<Uri>) = withContext(Dispatchers.IO) {
        val dir = File(root, id.toString()).also { it.mkdirs() }
        val start = pages(id).size
        uris.forEachIndexed { i, uri ->
            val target = File(dir, pageName(start + i))
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { input.copyTo(it) }
            }
        }
        touch(id)
    }

    suspend fun deletePage(id: Long, page: File) = withContext(Dispatchers.IO) {
        page.delete()
        renumber(id, pages(id))
        touch(id)
    }

    suspend fun movePage(id: Long, from: Int, to: Int) = withContext(Dispatchers.IO) {
        val list = pages(id).toMutableList()
        if (from !in list.indices || to !in list.indices) return@withContext
        list.add(to, list.removeAt(from))
        renumber(id, list)
        touch(id)
    }

    suspend fun rotatePage(id: Long, page: File) = withContext(Dispatchers.IO) {
        val src = BitmapFactory.decodeFile(page.path) ?: return@withContext
        val rotated = Bitmap.createBitmap(src, 0, 0, src.width, src.height, Matrix().apply { postRotate(90f) }, true)
        page.outputStream().use { rotated.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        src.recycle()
        rotated.recycle()
        touch(id)
    }

    suspend fun rename(id: Long, title: String) = withContext(Dispatchers.IO) {
        dao.get(id)?.let { dao.update(it.copy(title = title.trim().ifEmpty { it.title })) }
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        File(root, id.toString()).deleteRecursively()
        dao.delete(id)
    }

    private suspend fun touch(id: Long) {
        dao.get(id)?.let { dao.update(it.copy(pageCount = pages(id).size, updatedAt = System.currentTimeMillis())) }
    }

    /** Renombra en dos fases para no pisar archivos mientras se reordena. */
    private fun renumber(id: Long, ordered: List<File>) {
        val dir = File(root, id.toString())
        val temps = ordered.mapIndexed { i, f -> File(dir, "tmp_$i").also { f.renameTo(it) } }
        temps.forEachIndexed { i, f -> f.renameTo(File(dir, pageName(i))) }
    }

    private fun pageName(index: Int) = "page_%03d.jpg".format(index)
}
