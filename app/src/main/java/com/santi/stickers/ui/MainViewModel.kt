package com.santi.stickers.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.santi.stickers.data.AddResult
import com.santi.stickers.data.DeleteResult
import com.santi.stickers.data.PackRules
import com.santi.stickers.data.PackStatus
import com.santi.stickers.data.Packs
import com.santi.stickers.data.packStatuses
import com.santi.stickers.image.CropMath
import com.santi.stickers.image.CropTransform
import com.santi.stickers.image.StickerImages
import com.santi.stickers.whatsapp.AddPackOutcome
import com.santi.stickers.whatsapp.AddPackRequest
import com.santi.stickers.whatsapp.WhatsApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Pantalla actual: sin librería de navegación (D1). */
sealed interface Screen {
    data object Home : Screen

    /** [photo] es null mientras se decodifica. */
    data class Crop(val photo: Bitmap?) : Screen
}

data class HomeState(
    val loading: Boolean = true,
    val packs: List<PackStatus> = emptyList(),
    val canAddSticker: Boolean = true,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = Packs.repository(app)

    var screen: Screen by mutableStateOf(Screen.Home)
        private set
    var home: HomeState by mutableStateOf(HomeState())
        private set
    var saving: Boolean by mutableStateOf(false)
        private set

    /** Mensajes de un solo uso para el Snackbar. */
    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()

    /** Lo enviado a WhatsApp, a la espera de su respuesta (RN-8 confirma esta versión). */
    private var pendingRequest: AddPackRequest? = null

    /** RN-9: se llama al volver a la app. Consulta a WhatsApp en segundo plano. */
    fun refresh() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            home = withContext(Dispatchers.IO) {
                val index = repository.load()
                HomeState(
                    loading = false,
                    packs = packStatuses(index) { WhatsApp.isWhitelisted(context, it) },
                    canAddSticker = repository.canAddSticker(),
                )
            }
        }
    }

    // --- Crear sticker ---

    /** AC-8: con el máximo alcanzado, ni se abre el selector. */
    fun tryStartNewSticker(openPicker: () -> Unit) {
        if (home.canAddSticker) {
            openPicker()
        } else {
            message(
                "Llegaste al máximo: ${PackRules.MAX_PACKS} packs de ${PackRules.MAX_STICKERS} stickers. " +
                    "Borrá alguno para hacer lugar.",
            )
        }
    }

    fun startCrop(uri: Uri) {
        screen = Screen.Crop(photo = null)
        viewModelScope.launch {
            val context = getApplication<Application>()
            val photo = withContext(Dispatchers.IO) {
                runCatching { StickerImages.decode(ImageDecoder.createSource(context.contentResolver, uri)) }
                    .onFailure { Log.w(TAG, "No se pudo abrir la foto $uri", it) }
                    .getOrNull()
            }
            if (screen !is Screen.Crop) return@launch // se canceló mientras cargaba
            if (photo == null) {
                screen = Screen.Home
                message("No se pudo abrir esa foto.")
            } else {
                screen = Screen.Crop(photo)
            }
        }
    }

    /** AC-5. */
    fun cancelCrop() {
        if (!saving) screen = Screen.Home
    }

    /** AC-2 a AC-4: genera el WebP con lo que está dentro del cuadro y lo suma al pack (RN-3). */
    fun saveSticker(photo: Bitmap, crop: CropTransform, frameSize: Float) {
        if (saving) return
        saving = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                runCatching {
                    val sticker = StickerImages.render(photo, CropMath.toOutput(crop, frameSize))
                    repository.addSticker(StickerImages.encodeWebp(sticker))
                }
            }
            saving = false
            result
                .onSuccess {
                    when (it) {
                        is AddResult.Added -> {
                            screen = Screen.Home
                            message("Sticker guardado en ${it.pack.name}.")
                        }
                        AddResult.LimitReached -> {
                            screen = Screen.Home
                            message("Llegaste al máximo de packs.")
                        }
                    }
                }
                .onFailure {
                    Log.e(TAG, "No se pudo guardar el sticker", it)
                    message("No se pudo guardar el sticker.")
                }
            refresh()
        }
    }

    // --- Borrar ---

    fun deleteSticker(packId: String, file: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { repository.deleteSticker(packId, file) }
            when (result) {
                is DeleteResult.Deleted -> message("Sticker borrado.")
                DeleteResult.BelowMinimum -> message(
                    "Este pack ya está en WhatsApp: tiene que quedar con al menos ${PackRules.MIN_STICKERS} stickers.",
                )
                DeleteResult.NotFound -> Unit
            }
            refresh()
        }
    }

    // --- WhatsApp ---

    /** Devuelve el pedido a lanzar, o null si no se puede (y avisa por qué). */
    fun prepareWhatsAppRequest(status: PackStatus): AddPackRequest? {
        val context = getApplication<Application>()
        if (!WhatsApp.isInstalled(context)) {
            message("WhatsApp no está instalado.")
            return null
        }
        val pack = status.pack
        if (!pack.canSendToWhatsApp) return null
        return AddPackRequest(pack.identifier, pack.name, pack.imageDataVersion).also { pendingRequest = it }
    }

    fun onWhatsAppLaunchFailed() {
        pendingRequest = null
        message("No se pudo abrir WhatsApp.")
    }

    /** RN-8: solo una respuesta afirmativa confirma la versión enviada. */
    fun onWhatsAppResult(outcome: AddPackOutcome) {
        val request = pendingRequest
        pendingRequest = null
        viewModelScope.launch {
            if (request != null && outcome.confirms) {
                withContext(Dispatchers.IO) { repository.confirmAdded(request.packId, request.version) }
            }
            if (outcome is AddPackOutcome.ValidationError) {
                Log.e(TAG, "WhatsApp rechazó el pack: ${outcome.message}")
                message("WhatsApp rechazó el pack: ${outcome.message}")
            }
            refresh()
        }
    }

    private fun message(text: String) {
        messageChannel.trySend(text)
    }

    private companion object {
        const val TAG = "MainViewModel"
    }
}
