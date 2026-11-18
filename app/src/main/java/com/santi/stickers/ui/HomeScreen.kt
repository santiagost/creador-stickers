package com.santi.stickers.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.santi.stickers.data.PackRules
import com.santi.stickers.data.PackStatus
import com.santi.stickers.data.StickerPack
import com.santi.stickers.data.SyncState
import com.santi.stickers.share.SharedKind
import com.santi.stickers.share.packsFor
import java.io.File

/** Inicio: lista de packs (spec 004, sección 11). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeState,
    packDir: (packId: String) -> File,
    snackbar: SnackbarHostState,
    suggestedName: (animated: Boolean) -> String,
    onCreatePack: (name: String, animated: Boolean) -> Unit,
    onOpenPack: (packId: String) -> Unit,
) {
    var creating by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Stickers") }) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { creating = true }) { Text("+  Nuevo pack") }
        },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            state.packs.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), Alignment.Center) {
                Text("Creá tu primer pack", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center) // AC-1
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.packs, key = { it.pack.identifier }) { status ->
                    PackRow(status, packDir(status.pack.identifier)) { onOpenPack(status.pack.identifier) } // AC-4
                }
            }
        }
    }

    if (creating) {
        NewPackDialog(
            suggestedName = suggestedName,
            onDismiss = { creating = false },
            onCreate = { name, animated ->
                creating = false
                onCreatePack(name, animated)
            },
        )
    }
}

@Composable
private fun PackRow(status: PackStatus, dir: File, enabled: Boolean = true, onClick: () -> Unit) {
    val pack = status.pack
    Card(Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.45f).clickable(enabled = enabled, onClick = onClick)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                pack.stickers.firstOrNull()?.let {
                    StickerThumbnail(File(dir, it.file), contentDescription = null, modifier = Modifier.fillMaxSize().padding(4.dp))
                }
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(packTitle(pack), style = MaterialTheme.typography.titleMedium)
                Text(
                    if (enabled) "${pack.stickers.size}/${PackRules.MAX_STICKERS} · ${statusText(status)}" else "lleno (${PackRules.MAX_STICKERS}/${PackRules.MAX_STICKERS})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Spec 006, sección 11: elegir a qué pack va lo compartido (RN-2, D3). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChoosePackScreen(
    state: Screen.ChoosePack,
    home: HomeState,
    packDir: (packId: String) -> File,
    snackbar: SnackbarHostState,
    suggestedName: (animated: Boolean) -> String,
    onChoose: (packId: String) -> Unit,
    onCreatePack: (name: String) -> Unit,
    onCancel: () -> Unit,
) {
    BackHandler(onBack = onCancel)
    var creating by remember { mutableStateOf(false) }
    val packs = home.packs.filter { it.pack in packsFor(state.kind, home.packs.map { s -> s.pack }) }
    val what = when (state.kind) {
        SharedKind.PHOTO -> "la foto"
        SharedKind.VIDEO -> "el video"
        SharedKind.GIF -> "el GIF"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { TextButton(onClick = onCancel) { Text("Cancelar") } },
                title = { Text("¿A qué pack?") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { creating = true }) { Text("+  Nuevo pack") }
        },
    ) { padding ->
        when {
            home.loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        if (packs.isEmpty()) {
                            if (state.kind.animated) "No tenés packs animados. Creá uno para $what." else "No tenés packs normales. Creá uno para $what."
                        } else {
                            "Elegí el pack para $what."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                items(packs, key = { it.pack.identifier }) { status ->
                    // AC-5: los llenos se ven pero no se eligen.
                    PackRow(status, packDir(status.pack.identifier), enabled = !status.pack.isFull) { onChoose(status.pack.identifier) }
                }
            }
        }
    }

    if (creating) {
        NewPackDialog(
            suggestedName = suggestedName,
            fixedAnimated = state.kind.animated,
            onDismiss = { creating = false },
            onCreate = { name, _ ->
                creating = false
                onCreatePack(name)
            },
        )
    }
}

/**
 * AC-2: tipo + nombre sugerido (D2). El nombre sugerido sigue al tipo mientras no se lo edite.
 * Spec 006, D3: con [fixedAnimated] el tipo ya viene elegido y no se muestra.
 */
@Composable
private fun NewPackDialog(
    suggestedName: (animated: Boolean) -> String,
    onDismiss: () -> Unit,
    onCreate: (name: String, animated: Boolean) -> Unit,
    fixedAnimated: Boolean? = null,
) {
    var animated by remember { mutableStateOf(fixedAnimated ?: false) }
    var name by remember { mutableStateOf(suggestedName(animated)) }
    var edited by remember { mutableStateOf(false) }
    val valid = name.trim().isNotEmpty() && name.trim().length <= PackRules.MAX_NAME_LENGTH

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (fixedAnimated == null) "Nuevo pack" else if (fixedAnimated) "Nuevo pack animado" else "Nuevo pack normal") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (fixedAnimated == null) listOf(false to "Normal (fotos)", true to "Animado (videos o GIF)").forEach { (value, label) ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = animated == value) {
                            animated = value
                            if (!edited) name = suggestedName(value)
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = animated == value, onClick = null)
                        Text(label, Modifier.padding(start = 8.dp))
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it.take(PackRules.MAX_NAME_LENGTH)
                        edited = true
                    },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(name, animated) }, enabled = valid) { Text("Crear") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Spec 005, D2: en un pack animado, el sticker nuevo sale de un video o de un GIF; spec 007, D3: o de un link de X. */
@Composable
fun AnimatedSourceDialog(onVideo: () -> Unit, onGif: () -> Unit, onPasteLink: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo sticker animado") },
        text = {
            Column {
                Text("¿Desde dónde?")
                listOf("Video" to onVideo, "GIF" to onGif, "Pegar link de X" to onPasteLink).forEach { (label, action) ->
                    TextButton(onClick = action, modifier = Modifier.fillMaxWidth()) { Text(label) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

private data class DeleteTarget(val packId: String, val file: String)

/** Pantalla de un pack (spec 004, sección 11). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PackScreen(
    status: PackStatus,
    dir: File,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onNewSticker: () -> Unit,
    onSendToWhatsApp: () -> Unit,
    onDelete: (file: String) -> Unit,
    onRename: (name: String) -> Unit,
    onDeletePack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val pack = status.pack
    var toDelete by remember { mutableStateOf<DeleteTarget?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deletingPack by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { TextButton(onClick = onBack) { Text("←", style = MaterialTheme.typography.titleLarge) } },
                title = { Text(packTitle(pack)) },
                actions = {
                    // Spec 008, sección 11.
                    Box {
                        TextButton(onClick = { menuOpen = true }) { Text("⋮", style = MaterialTheme.typography.titleLarge) }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("Renombrar") }, onClick = {
                                menuOpen = false
                                renaming = true
                            })
                            DropdownMenuItem(text = { Text("Borrar pack") }, onClick = {
                                menuOpen = false
                                deletingPack = true
                            })
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            // AC-6: con 30 se avisa desde el ViewModel.
            ExtendedFloatingActionButton(onClick = onNewSticker) { Text("+  Nuevo sticker") }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "${pack.stickers.size}/${PackRules.MAX_STICKERS} · ${statusText(status)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            WhatsAppButton(status, onSendToWhatsApp)
            if (pack.stickers.isEmpty()) {
                Text(
                    if (pack.animated) "Todavía no tiene stickers. Creá uno desde un video o un GIF." else "Todavía no tiene stickers. Creá uno desde una foto.",
                    modifier = Modifier.padding(vertical = 32.dp).fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
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
                                .combinedClickable(onClick = {}, onLongClick = { toDelete = DeleteTarget(pack.identifier, sticker.file) }),
                        ) {
                            StickerThumbnail(File(dir, sticker.file), contentDescription = "Sticker", modifier = Modifier.fillMaxSize().padding(4.dp))
                        }
                    }
                    repeat(COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.size(96.dp))
        }
    }

    if (renaming) {
        RenamePackDialog(
            current = pack.name,
            onDismiss = { renaming = false },
            onSave = {
                renaming = false
                onRename(it)
            },
        )
    }

    if (deletingPack) {
        // Spec 008, D1, D2, AC-2, AC-3.
        val count = pack.stickers.size
        val stickers = if (count == 1) "su único sticker" else "sus $count stickers"
        AlertDialog(
            onDismissRequest = { deletingPack = false },
            title = { Text("¿Borrar \"${pack.name}\"?") },
            text = {
                Text(
                    buildString {
                        append(if (count == 0) "El pack está vacío." else "Se borran $stickers.")
                        append(" No se puede deshacer.")
                        if (pack.everAdded) append("\n\nVa a seguir en WhatsApp: quitalo desde ahí (Ajustes → Chats → Stickers).")
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deletingPack = false
                    onDeletePack()
                }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { deletingPack = false }) { Text("Cancelar") } },
        )
    }

    toDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("¿Borrar este sticker?") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(target.file)
                    toDelete = null
                }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar") } },
        )
    }
}

/** Spec 008, D3: mismas reglas que al crear. */
@Composable
private fun RenamePackDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(current) }
    val valid = name.trim().isNotEmpty() && name.trim().length <= PackRules.MAX_NAME_LENGTH
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Renombrar pack") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(PackRules.MAX_NAME_LENGTH) },
                label = { Text("Nombre") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(name) }, enabled = valid) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun WhatsAppButton(status: PackStatus, onSend: () -> Unit) {
    val pack = status.pack
    val modifier = Modifier.fillMaxWidth()
    when {
        // AC-10 (spec 001)
        !pack.canSendToWhatsApp -> OutlinedButton(onClick = {}, enabled = false, modifier = modifier) {
            Text(if (pack.stickersMissing == 1) "Falta 1 sticker para WhatsApp" else "Faltan ${pack.stickersMissing} stickers para WhatsApp")
        }
        status.state == SyncState.NOT_ADDED -> Button(onClick = onSend, modifier = modifier) { Text("Agregar a WhatsApp") }
        status.state == SyncState.NEEDS_UPDATE -> Button(onClick = onSend, modifier = modifier) { Text("Actualizar en WhatsApp") }
        else -> Unit // Al día: no hace falta botón.
    }
}

private fun packTitle(pack: StickerPack) = if (pack.animated) "${pack.name} · animado" else pack.name

private fun statusText(status: PackStatus): String = when {
    !status.pack.canSendToWhatsApp -> "todavía no se puede agregar"
    status.state == SyncState.NOT_ADDED -> "no está en WhatsApp"
    status.state == SyncState.UP_TO_DATE -> "al día en WhatsApp"
    else -> "hay cambios sin pasar a WhatsApp"
}

private const val COLUMNS = 4
