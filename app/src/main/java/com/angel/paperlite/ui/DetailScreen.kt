package com.angel.paperlite.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.angel.paperlite.AppViewModel
import com.angel.paperlite.PdfViewerActivity
import com.angel.paperlite.pdf.PdfExporter
import com.angel.paperlite.scan.rememberDocumentScanner
import com.angel.paperlite.share.Sharer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(vm: AppViewModel, docId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val doc by remember(docId) { vm.doc(docId) }.collectAsStateWithLifecycle(null)
    val pages by remember(docId) { vm.pages(docId) }.collectAsStateWithLifecycle(emptyList())
    val scan = rememberDocumentScanner { vm.appendPages(docId, it) }

    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var pdfMenu by remember { mutableStateOf(false) }
    var imgMenu by remember { mutableStateOf(false) }

    // Si el documento se borra, volver atrás.
    val current = doc
    val title = current?.title ?: ""

    fun withPdf(action: (File) -> Unit) {
        if (pages.isEmpty()) return
        scope.launch {
            working = true
            try {
                val pdf = withContext(Dispatchers.Default) { PdfExporter.create(context, title, pages) }
                action(pdf)
            } catch (e: Exception) {
                Toast.makeText(context, "Error al crear el PDF: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                working = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") }
                },
                actions = {
                    IconButton(onClick = { renaming = true }) { Icon(Icons.Filled.Edit, "Renombrar") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Eliminar documento") }
                },
            )
        },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.weight(1f)) {
                    Button({ pdfMenu = true }, Modifier.fillMaxWidth(), enabled = !working) {
                        Text("PDF")
                    }
                    DropdownMenu(pdfMenu, { pdfMenu = false }) {
                        DropdownMenuItem(text = { Text("Ver") }, onClick = {
                            pdfMenu = false
                            withPdf { pdf ->
                                context.startActivity(
                                    Intent(context, PdfViewerActivity::class.java)
                                        .setData(Sharer.uriFor(context, pdf))
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                                )
                            }
                        })
                        DropdownMenuItem(text = { Text("Compartir") }, onClick = {
                            pdfMenu = false
                            withPdf { Sharer.sharePdf(context, it) }
                        })
                        DropdownMenuItem(text = { Text("Guardar en Descargas") }, onClick = {
                            pdfMenu = false
                            withPdf {
                                Sharer.savePdfToDownloads(context, it)
                                Toast.makeText(context, "Guardado en Descargas", Toast.LENGTH_SHORT).show()
                            }
                        })
                    }
                }
                Box(Modifier.weight(1f)) {
                    Button({ imgMenu = true }, Modifier.fillMaxWidth(), enabled = pages.isNotEmpty()) {
                        Text("Imágenes")
                    }
                    DropdownMenu(imgMenu, { imgMenu = false }) {
                        DropdownMenuItem(text = { Text("Compartir") }, onClick = {
                            imgMenu = false
                            Sharer.shareImages(context, pages)
                        })
                        DropdownMenuItem(text = { Text("Guardar en galería") }, onClick = {
                            imgMenu = false
                            Sharer.saveImagesToGallery(context, PdfExporter.safeName(title), pages)
                            Toast.makeText(context, "Guardadas en Imágenes/PaperLite", Toast.LENGTH_SHORT).show()
                        })
                    }
                }
                Button(onClick = scan, Modifier.weight(1f)) {
                    Icon(Icons.Filled.Add, null)
                    Text(" Página")
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(pages, key = { _, f -> f.name }) { index, file ->
                    PageCard(
                        file = file,
                        index = index,
                        last = index == pages.lastIndex,
                        onRotate = { vm.rotatePage(docId, file) },
                        onLeft = { vm.movePage(docId, index, index - 1) },
                        onRight = { vm.movePage(docId, index, index + 1) },
                        onDelete = { vm.deletePage(docId, file) },
                    )
                }
            }
            if (working) CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }

    if (renaming) {
        var text by remember { mutableStateOf(title) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Renombrar") },
            text = { OutlinedTextField(text, { text = it }, singleLine = true) },
            confirmButton = {
                TextButton({ vm.rename(docId, text); renaming = false }) { Text("Guardar") }
            },
            dismissButton = { TextButton({ renaming = false }) { Text("Cancelar") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Eliminar documento?") },
            text = { Text("Se borrarán todas sus páginas. No se puede deshacer.") },
            confirmButton = {
                TextButton({ confirmDelete = false; onBack(); vm.delete(docId) }) { Text("Eliminar") }
            },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun PageCard(
    file: File,
    index: Int,
    last: Boolean,
    onRotate: () -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onDelete: () -> Unit,
) {
    Card {
        Column {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(file)
                    .memoryCacheKey("${file.path}:${file.lastModified()}")
                    .build(),
                contentDescription = "Página ${index + 1}",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().aspectRatio(0.75f),
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onLeft, enabled = index > 0) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Mover atrás") }
                IconButton(onRotate) { Icon(Icons.Filled.Refresh, "Rotar") }
                IconButton(onRight, enabled = !last) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Mover adelante") }
                IconButton(onDelete) { Icon(Icons.Filled.Delete, "Eliminar página") }
            }
            Text(
                "${index + 1}",
                Modifier.align(Alignment.CenterHorizontally).padding(bottom = 6.dp),
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
            )
        }
    }
}
