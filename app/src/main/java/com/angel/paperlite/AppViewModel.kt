package com.angel.paperlite

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.angel.paperlite.data.AppDb
import com.angel.paperlite.data.Doc
import com.angel.paperlite.data.Repo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repo(app, AppDb.get(app).dao())

    val docs: StateFlow<List<Doc>> =
        repo.docs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun doc(id: Long): Flow<Doc?> = repo.observe(id)

    /** Se vuelve a leer cada vez que el documento cambia (el repo actualiza `updatedAt`). */
    fun pages(id: Long): Flow<List<File>> =
        repo.observe(id).filterNotNull().map { repo.pages(id) }.flowOn(Dispatchers.IO)

    suspend fun firstPage(id: Long): File? = withContext(Dispatchers.IO) { repo.pages(id).firstOrNull() }

    fun createDocument(uris: List<Uri>, onCreated: (Long) -> Unit) {
        viewModelScope.launch { onCreated(repo.createDocument(uris)) }
    }

    fun appendPages(id: Long, uris: List<Uri>) {
        viewModelScope.launch { repo.appendPages(id, uris) }
    }

    fun deletePage(id: Long, page: File) {
        viewModelScope.launch { repo.deletePage(id, page) }
    }

    fun movePage(id: Long, from: Int, to: Int) {
        viewModelScope.launch { repo.movePage(id, from, to) }
    }

    fun rotatePage(id: Long, page: File) {
        viewModelScope.launch { repo.rotatePage(id, page) }
    }

    fun rename(id: Long, title: String) {
        viewModelScope.launch { repo.rename(id, title) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repo.delete(id) }
    }
}
