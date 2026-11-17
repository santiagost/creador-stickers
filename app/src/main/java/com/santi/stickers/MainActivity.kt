package com.santi.stickers

import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.santi.stickers.data.Packs
import com.santi.stickers.ui.AnimatedSourceDialog
import com.santi.stickers.ui.ChoosePackScreen
import com.santi.stickers.ui.CropScreen
import com.santi.stickers.ui.DownloadScreen
import com.santi.stickers.ui.HomeScreen
import com.santi.stickers.ui.MainViewModel
import com.santi.stickers.ui.PackScreen
import com.santi.stickers.ui.Screen
import com.santi.stickers.data.PackRepository
import com.santi.stickers.data.PackStatus
import com.santi.stickers.data.SyncState
import com.santi.stickers.whatsapp.AddPackRequest
import androidx.activity.result.ActivityResultLauncher
import com.santi.stickers.ui.TrimScreen
import com.santi.stickers.whatsapp.AddStickerPackContract

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Spec 006, RN-5: lo compartido se lee solo al crear la pantalla, no al recrearla (por ejemplo, al girar).
        if (savedInstanceState == null) readShared(intent)
        setContent {
            StickersTheme {
                App(viewModel)
            }
        }
    }

    /** Spec 006: ACTION_SEND / ACTION_SEND_MULTIPLE con fotos, videos o GIF (D1, D4). */
    private fun readShared(intent: Intent) {
        // Spec 007, D3: texto (un link de X).
        if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true && streamExtra(intent) == null) {
            viewModel.onSharedText(intent.getStringExtra(Intent.EXTRA_TEXT))
            return
        }
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(streamExtra(intent))
            Intent.ACTION_SEND_MULTIPLE -> streamListExtra(intent)
            else -> return
        }.ifEmpty {
            // Algunas apps solo lo ponen en el ClipData.
            intent.clipData?.let { clip -> (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri } }.orEmpty()
        }
        val uri = uris.firstOrNull()
        // RN-1: primero el tipo que informa el sistema para ese archivo; si no, el que dice quien comparte.
        val mime = uri?.let { runCatching { contentResolver.getType(it) }.getOrNull() } ?: intent.type
        viewModel.onShared(uri, mime, more = uris.size > 1)
    }

    private fun streamExtra(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }

    private fun streamListExtra(intent: Intent): List<Uri> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
        }.orEmpty()

    override fun onResume() {
        super.onResume()
        viewModel.refresh() // RN-9: el usuario pudo cambiar algo en WhatsApp
    }
}

@Composable
private fun App(viewModel: MainViewModel) {
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val repository = remember { Packs.repository(context) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    // D3: Photo Picker del sistema, sin permisos. Cancelar no hace nada (AC-5).
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::startCrop)
    }
    // Spec 003, AC-1: el mismo selector, solo videos.
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::startVideo)
    }
    // Spec 005, D2: el mismo selector, solo GIFs.
    val pickGif = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::startGif)
    }
    val pickers = Pickers(pickPhoto, pickVideo, pickGif)
    val sendToWhatsApp = rememberLauncherForActivityResult(AddStickerPackContract(), viewModel::onWhatsAppResult)

    when (val screen = viewModel.screen) {
        Screen.Home -> HomeScreen(
            state = viewModel.home,
            packDir = repository::packDir,
            snackbar = snackbar,
            suggestedName = viewModel::suggestedName,
            onCreatePack = viewModel::createPack,
            onOpenPack = viewModel::openPack,
        )
        is Screen.Pack -> {
            val status = viewModel.home.packs.firstOrNull { it.pack.identifier == screen.packId }
            if (status == null) {
                // Recién creado: hasta que llegue el refresh se muestra un pack vacío con el estado básico.
                val pack = repository.load().packs.firstOrNull { it.identifier == screen.packId }
                if (pack == null) {
                    viewModel.closePack()
                    return
                }
                PackScreenFor(PackStatus(pack, SyncState.NOT_ADDED), repository, snackbar, viewModel, pickers, sendToWhatsApp)
            } else {
                PackScreenFor(status, repository, snackbar, viewModel, pickers, sendToWhatsApp)
            }
        }
        is Screen.Crop -> CropScreen(
            state = screen,
            saving = viewModel.saving,
            snackbar = snackbar,
            onToggleBackground = viewModel::toggleBackground,
            onCancel = viewModel::cancelCrop,
            onSave = { photo, crop, frame ->
                if (screen.video != null) viewModel.saveAnimated(crop, frame) else viewModel.saveSticker(photo, crop, frame)
            },
        )
        is Screen.ChoosePack -> ChoosePackScreen(
            state = screen,
            home = viewModel.home,
            packDir = repository::packDir,
            snackbar = snackbar,
            suggestedName = viewModel::suggestedName,
            onChoose = viewModel::chooseSharedPack,
            onCreatePack = viewModel::createPackForShared,
            onCancel = viewModel::cancelShared,
        )
        is Screen.Downloading -> DownloadScreen(state = screen, onCancel = viewModel::cancelDownload)
        is Screen.Trim -> TrimScreen(
            state = screen,
            onRangeChange = viewModel::setTrimRange,
            onCancel = viewModel::cancelTrim,
            onNext = viewModel::confirmTrim,
        )
    }
}

private class Pickers(
    val photo: ActivityResultLauncher<PickVisualMediaRequest>,
    val video: ActivityResultLauncher<PickVisualMediaRequest>,
    val gif: ActivityResultLauncher<PickVisualMediaRequest>,
)

@Composable
private fun PackScreenFor(
    status: PackStatus,
    repository: PackRepository,
    snackbar: SnackbarHostState,
    viewModel: MainViewModel,
    pickers: Pickers,
    sendToWhatsApp: ActivityResultLauncher<AddPackRequest>,
) {
    val packId = status.pack.identifier
    val context = LocalContext.current
    var choosingSource by remember { mutableStateOf(false) }
    if (choosingSource) {
        // Spec 005, D2 y AC-1.
        AnimatedSourceDialog(
            onVideo = {
                choosingSource = false
                pickers.video.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
            },
            onGif = {
                choosingSource = false
                pickers.gif.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.SingleMimeType("image/gif")))
            },
            onPasteLink = {
                // Spec 007, RN-7: el portapapeles se lee solo al tocar (Android avisa que se pegó).
                choosingSource = false
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                val text = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                viewModel.onPastedLink(text)
            },
            onDismiss = { choosingSource = false },
        )
    }
    PackScreen(
        status = status,
        dir = repository.packDir(packId),
        snackbar = snackbar,
        onBack = viewModel::closePack,
        onNewSticker = {
            // Spec 004, AC-5: normal → fotos; animado → "Video" o "GIF" (spec 005, D2).
            viewModel.tryStartNewSticker(packId) { animated ->
                if (animated) {
                    choosingSource = true
                } else {
                    pickers.photo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            }
        },
        onSendToWhatsApp = {
            viewModel.prepareWhatsAppRequest(status)?.let { request ->
                try {
                    sendToWhatsApp.launch(request)
                } catch (_: ActivityNotFoundException) {
                    viewModel.onWhatsAppLaunchFailed()
                }
            }
        },
        onDelete = { file -> viewModel.deleteSticker(packId, file) },
    )
}

/** Material 3 con los colores del fondo de pantalla (Android 12+), sin diseño propio (sección 11). */
@Composable
private fun StickersTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}
