package com.santi.stickers

import android.content.ActivityNotFoundException
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
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.santi.stickers.data.Packs
import com.santi.stickers.ui.CropScreen
import com.santi.stickers.ui.HomeScreen
import com.santi.stickers.ui.MainViewModel
import com.santi.stickers.ui.Screen
import com.santi.stickers.ui.TrimScreen
import com.santi.stickers.whatsapp.AddStickerPackContract

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StickersTheme {
                App(viewModel)
            }
        }
    }

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
    val sendToWhatsApp = rememberLauncherForActivityResult(AddStickerPackContract(), viewModel::onWhatsAppResult)

    when (val screen = viewModel.screen) {
        Screen.Home -> HomeScreen(
            state = viewModel.home,
            packDir = repository::packDir,
            snackbar = snackbar,
            onNewSticker = { animated ->
                viewModel.tryStartNewSticker(animated) {
                    if (animated) {
                        pickVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                    } else {
                        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                }
            },
            onSendToWhatsApp = { status ->
                viewModel.prepareWhatsAppRequest(status)?.let { request ->
                    try {
                        sendToWhatsApp.launch(request)
                    } catch (_: ActivityNotFoundException) {
                        viewModel.onWhatsAppLaunchFailed()
                    }
                }
            },
            onDelete = viewModel::deleteSticker,
        )
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
        is Screen.Trim -> TrimScreen(
            state = screen,
            onStartChange = viewModel::setTrimStart,
            onCancel = viewModel::cancelTrim,
            onNext = viewModel::confirmTrim,
        )
    }
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
