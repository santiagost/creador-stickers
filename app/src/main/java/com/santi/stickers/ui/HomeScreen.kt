package com.santi.stickers.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.santi.stickers.data.PackRules
import com.santi.stickers.data.PackStatus
import com.santi.stickers.data.SyncState
import java.io.File

private data class DeleteTarget(val packId: String, val file: String)

/** Pantalla Inicio (spec 001, sección 11.1). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeState,
    packDir: (packId: String) -> File,
    snackbar: SnackbarHostState,
    onNewSticker: () -> Unit,
    onSendToWhatsApp: (PackStatus) -> Unit,
    onDelete: (packId: String, file: String) -> Unit,
) {
    var toDelete by remember { mutableStateOf<DeleteTarget?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Stickers") }) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onNewSticker) { Text("+  Nuevo sticker") }
        },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            state.packs.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), Alignment.Center) {
                Text(
                    "Creá tu primer sticker",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(state.packs, key = { it.pack.identifier }) { status ->
                    PackCard(
                        status = status,
                        dir = packDir(status.pack.identifier),
                        onSend = { onSendToWhatsApp(status) },
                        onLongPress = { file -> toDelete = DeleteTarget(status.pack.identifier, file) },
                    )
                }
            }
        }
    }

    toDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("¿Borrar este sticker?") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(target.packId, target.file)
                    toDelete = null
                }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PackCard(status: PackStatus, dir: File, onSend: () -> Unit, onLongPress: (file: String) -> Unit) {
    val pack = status.pack
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(pack.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${pack.stickers.size}/${PackRules.MAX_STICKERS} · ${statusText(status)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            pack.stickers.chunked(COLUMNS).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { sticker ->
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .combinedClickable(onClick = {}, onLongClick = { onLongPress(sticker.file) }),
                        ) {
                            StickerThumbnail(File(dir, sticker.file), contentDescription = "Sticker", modifier = Modifier.fillMaxSize().padding(4.dp))
                        }
                    }
                    repeat(COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            WhatsAppButton(status, onSend)
        }
    }
}

@Composable
private fun WhatsAppButton(status: PackStatus, onSend: () -> Unit) {
    val pack = status.pack
    val modifier = Modifier.fillMaxWidth()
    when {
        // AC-10
        !pack.canSendToWhatsApp -> OutlinedButton(onClick = {}, enabled = false, modifier = modifier) {
            Text(if (pack.stickersMissing == 1) "Falta 1 sticker para WhatsApp" else "Faltan ${pack.stickersMissing} stickers para WhatsApp")
        }
        status.state == SyncState.NOT_ADDED -> Button(onClick = onSend, modifier = modifier) { Text("Agregar a WhatsApp") }
        status.state == SyncState.NEEDS_UPDATE -> Button(onClick = onSend, modifier = modifier) { Text("Actualizar en WhatsApp") }
        else -> Unit // Al día: no hace falta botón.
    }
}

private fun statusText(status: PackStatus): String = when {
    !status.pack.canSendToWhatsApp -> "todavía no se puede agregar"
    status.state == SyncState.NOT_ADDED -> "no está en WhatsApp"
    status.state == SyncState.UP_TO_DATE -> "al día en WhatsApp"
    else -> "hay cambios sin pasar a WhatsApp"
}

private const val COLUMNS = 4
