package com.angel.paperlite.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import java.text.DateFormat
import java.util.Date

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
    var exportMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var selectedName by remember { mutableStateOf<String?>(null) }

    // Si el documento se borra, volver atrás.
    val current = doc
    val title = current?.title ?: ""
    val selectedIndex = pages.indexOfFirst { it.name == selectedName }

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
                title = {
                    Column {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge)
                        if (current != null) {
                            Text(
                                "${pages.size} páginas · " +
                                    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(current.createdAt)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") }
                },
                actions = {
                    Box {
                        IconButton(onClick = { moreMenu = true }) { Icon(Icons.Filled.MoreVert, "Más opciones") }
                        DropdownMenu(moreMenu, { moreMenu = false }) {
                            DropdownMenuItem(text = { Text("Renombrar") }, onClick = {
                                moreMenu = false
                                renaming = true
                            })
                            DropdownMenuItem(text = { Text("Eliminar documento") }, onClick = {
                                moreMenu = false
                                confirmDelete = true
                            })
                        }
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column {
                    if (selectedIndex >= 0) {
                        val file = pages[selectedIndex]
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                        ) {
                            PageAction(Icons.Filled.Refresh, "Rotar", true) { vm.rotatePage(docId, file) }
                            PageAction(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", selectedIndex > 0) {
                                vm.movePage(docId, selectedIndex, selectedIndex - 1)
                            }
                            PageAction(Icons.AutoMirrored.Filled.ArrowForward, "Adelante", selectedIndex < pages.lastIndex) {
                                vm.movePage(docId, selectedIndex, selectedIndex + 1)
                            }
                            PageAction(Icons.Filled.Delete, "Borrar", true, MaterialTheme.colorScheme.error) {
                                selectedName = null
                                vm.deletePage(docId, file)
                            }
                        }
                        HorizontalDivider()
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.weight(1f)) {
                            Button(
                                { exportMenu = true },
                                Modifier.fillMaxWidth().size(width = 0.dp, height = 48.dp),
                                enabled = pages.isNotEmpty() && !working,
                            ) { Text("Exportar") }
                            DropdownMenu(exportMenu, { exportMenu = false }) {
                                DropdownMenuItem(text = { Text("Ver PDF") }, onClick = {
                                    exportMenu = false
                                    withPdf { pdf ->
                                        context.startActivity(
                                            Intent(context, PdfViewerActivity::class.java)
                                                .setData(Sharer.uriFor(context, pdf))
                                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                                        )
                                    }
                                })
                                DropdownMenuItem(text = { Text("Guardar PDF en Descargas") }, onClick = {
                                    exportMenu = false
                                    withPdf {
                                        Sharer.savePdfToDownloads(context, it)
                                        Toast.makeText(context, "Guardado en Descargas", Toast.LENGTH_SHORT).show()
                                    }
                                })
                                HorizontalDivider()
                                DropdownMenuItem(text = { Text("Compartir imágenes") }, onClick = {
                                    exportMenu = false
                                    Sharer.shareImages(context, pages)
                                })
                                DropdownMenuItem(text = { Text("Guardar imágenes en galería") }, onClick = {
                                    exportMenu = false
                                    Sharer.saveImagesToGallery(context, PdfExporter.safeName(title), pages)
                                    Toast.makeText(context, "Guardadas en Imágenes/PaperLite", Toast.LENGTH_SHORT).show()
                                })
                            }
                        }
                        OutlinedIconButton(
                            onClick = { withPdf { Sharer.sharePdf(context, it) } },
                            enabled = pages.isNotEmpty() && !working,
                            modifier = Modifier.size(48.dp),
                        ) { Icon(Icons.Filled.Share, "Compartir PDF") }
                        FilledTonalIconButton(onClick = scan, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Filled.Add, "Añadir página")
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                itemsIndexed(pages, key = { _, f -> f.name }) { index, file ->
                    PageCard(
                        file = file,
                        index = index,
                        selected = file.name == selectedName,
                        onClick = { selectedName = if (file.name == selectedName) null else file.name },
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
private fun PageAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit,
) {
    val color = if (enabled) tint else tint.copy(alpha = 0.38f)
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = color)
        Text(label, color = color, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun PageCard(file: File, index: Int, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val ring = if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape) else Modifier
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(0.75f)
            .clip(shape)
            .background(Color.White)
            .then(ring)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(file)
                .memoryCacheKey("${file.path}:${file.lastModified()}")
                .build(),
            contentDescription = "Página ${index + 1}",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        Text(
            "${index + 1}",
            Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.65f))
                .padding(top = 3.dp),
            color = if (selected) MaterialTheme.colorScheme.onPrimary else Color.White,
            style = MaterialTheme.typography.labelMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
