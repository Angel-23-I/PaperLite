package com.angel.paperlite

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.angel.paperlite.share.Sharer
import com.angel.paperlite.ui.PaperLiteTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Lector de PDF. También es el destino de «Abrir con» para archivos application/pdf. */
class PdfViewerActivity : ComponentActivity() {
    private var renderer: PdfRenderer? = null
    private var descriptor: ParcelFileDescriptor? = null
    private val lock = Mutex()
    private val cache = object : android.util.LruCache<Int, Bitmap>(8) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val uri: Uri? = intent?.data
        val pageCount = try {
            descriptor = uri?.let { contentResolver.openFileDescriptor(it, "r") }
            renderer = descriptor?.let { PdfRenderer(it) }
            renderer?.pageCount ?: 0
        } catch (e: Exception) {
            0
        }
        val error = renderer == null

        setContent {
            PaperLiteTheme { Viewer(uri, pageCount, error) }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Viewer(uri: Uri?, pageCount: Int, error: Boolean) {
        val listState = rememberLazyListState()
        val current by remember { derivedStateOf { listState.firstVisibleItemIndex + 1 } }
        var scale by remember { mutableFloatStateOf(1f) }
        var offsetX by remember { mutableFloatStateOf(0f) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (error) "PDF" else "$current / $pageCount") },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cerrar") }
                    },
                    actions = {
                        if (uri != null) {
                            IconButton(onClick = { Sharer.sharePdfUri(this@PdfViewerActivity, uri) }) {
                                Icon(Icons.Filled.Share, "Compartir")
                            }
                        }
                    },
                )
            },
        ) { padding ->
            if (error) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("No se pudo abrir este PDF")
                }
                return@Scaffold
            }
            val widthPx = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    // Pellizco para zoom: solo se consume con dos dedos, un dedo sigue desplazando la lista.
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.changes.size > 1) {
                                    scale = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                                    val maxX = size.width * (scale - 1f) / 2f
                                    offsetX = (offsetX + event.calculatePan().x).coerceIn(-maxX, maxX)
                                    event.changes.forEach { it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                            if (scale == 1f) offsetX = 0f
                        }
                    },
            ) {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX),
                ) {
                    itemsIndexed(List(pageCount) { it }) { _, index ->
                        PageView(index, widthPx)
                    }
                }
            }
        }
    }

    @Composable
    private fun PageView(index: Int, widthPx: Int) {
        val bitmap by produceState<Bitmap?>(cache.get(index), index) {
            if (value == null) value = render(index, widthPx * 2)
        }
        val bmp = bitmap
        if (bmp == null) {
            Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Página ${index + 1}",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    private suspend fun render(index: Int, targetWidth: Int): Bitmap? = withContext(Dispatchers.IO) {
        lock.withLock {
            val r = renderer ?: return@withLock null
            r.openPage(index).use { page ->
                val width = targetWidth.coerceAtMost(2400)
                val height = (width.toFloat() * page.height / page.width).toInt()
                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                cache.put(index, bmp)
                bmp
            }
        }
    }

    override fun onDestroy() {
        renderer?.close()
        descriptor?.close()
        super.onDestroy()
    }
}
