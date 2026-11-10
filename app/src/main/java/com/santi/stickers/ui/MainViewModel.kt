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
import com.santi.stickers.image.AnimatedResult
import com.santi.stickers.image.AnimatedWebp
import com.santi.stickers.image.BackgroundRemover
import com.santi.stickers.image.CropMath
import com.santi.stickers.image.CropTransform
import com.santi.stickers.image.MlKitBackgroundRemover
import com.santi.stickers.image.RemovalResult
import com.santi.stickers.image.StickerImages
import com.santi.stickers.image.VideoFrames
import com.santi.stickers.image.VideoInfo
import com.santi.stickers.whatsapp.AddPackOutcome
import com.santi.stickers.whatsapp.AddPackRequest
import com.santi.stickers.whatsapp.WhatsApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Pantalla actual: sin librería de navegación (D1). */
sealed interface Screen {
    data object Home : Screen

    /**
     * [photo] es null mientras se decodifica. Spec 002: [removal] guarda el recorte ya calculado (D9),
     * [showSubject] dice si "Quitar fondo" está prendido y [removing] si ML Kit está trabajando.
     */
    data class Crop(
        val photo: Bitmap?,
        val removal: RemovalResult.Removed? = null,
        val showSubject: Boolean = false,
        val removing: Boolean = false,
        /** Spec 003: si viene de un video, [photo] es el primer cuadro del tramo (D7). */
        val video: VideoClip? = null,
        /** Spec 003: progreso de "Generando sticker…" (0 a 1), o null si no está generando. */
        val generating: Float? = null,
    ) : Screen {
        /** Lo que se ve y lo que se guarda (RN-7). */
        val shown: Bitmap? get() = if (showSubject) removal?.subject else photo
    }

    /** Spec 003: elegir desde dónde arranca el tramo. [preview] es null mientras carga el primer cuadro. */
    data class Trim(val uri: Uri, val info: VideoInfo, val startMs: Long, val preview: Bitmap?) : Screen
}

/** Spec 003: el tramo elegido de un video. */
data class VideoClip(val uri: Uri, val info: VideoInfo, val startMs: Long)

data class HomeState(
    val loading: Boolean = true,
    val packs: List<PackStatus> = emptyList(),
    val canAddStatic: Boolean = true,
    val canAddAnimated: Boolean = true,
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

    private var refreshJob: Job? = null
    private var cropJob: Job? = null
    private var removeJob: Job? = null
    private val backgroundRemover: BackgroundRemover = MlKitBackgroundRemover()

    override fun onCleared() {
        backgroundRemover.close()
    }

    /**
     * RN-9: se llama al volver a la app y después de cada cambio. Consulta a WhatsApp en segundo plano.
     * Cancela la consulta anterior para que una respuesta vieja no pise a una nueva.
     */
    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val context = getApplication<Application>()
            home = withContext(Dispatchers.IO) {
                val index = repository.load()
                HomeState(
                    loading = false,
                    packs = packStatuses(index) { WhatsApp.isWhitelisted(context, it) },
                    canAddStatic = repository.canAddSticker(animated = false),
                    canAddAnimated = repository.canAddSticker(animated = true),
                )
            }
        }
    }

    // --- Crear sticker ---

    /** AC-8 (spec 001), AC-9 (spec 003): con el máximo alcanzado, ni se abre el selector. */
    fun tryStartNewSticker(animated: Boolean, openPicker: () -> Unit) {
        if (if (animated) home.canAddAnimated else home.canAddStatic) {
            openPicker()
        } else {
            message(
                "Llegaste al máximo: ${PackRules.MAX_PACKS} packs de ${PackRules.MAX_STICKERS} stickers. " +
                    "Borrá alguno para hacer lugar.",
            )
        }
    }

    // --- Video (spec 003) ---

    private var trimPreviewJob: Job? = null
    private var generateJob: Job? = null

    /** AC-1/AC-2: abre la pantalla de tramo con el cuadro del principio. */
    fun startVideo(uri: Uri) {
        cropJob?.cancel()
        cropJob = viewModelScope.launch {
            val context = getApplication<Application>()
            val info = withContext(Dispatchers.IO) {
                runCatching { VideoFrames.info(context, uri) }
                    .onFailure { Log.w(TAG, "No se pudo abrir el video $uri", it) }
                    .getOrNull()
            }
            when {
                info == null -> message("No se pudo abrir ese video.")
                VideoFrames.frameCount(info, 0) < 2 -> message("Ese video es demasiado corto.") // RN-2
                else -> {
                    Log.i(TAG, "video $info")
                    screen = Screen.Trim(uri, info, startMs = 0, preview = null)
                    loadTrimPreview(exact = true)
                }
            }
        }
    }

    /** AC-2: al mover la barra se muestra el cuadro clave más cercano (rápido); al soltar, el exacto. */
    fun setTrimStart(startMs: Long, exact: Boolean) {
        val trim = screen as? Screen.Trim ?: return
        screen = trim.copy(startMs = startMs.coerceIn(0, VideoFrames.maxStart(trim.info)))
        loadTrimPreview(exact)
    }

    private fun loadTrimPreview(exact: Boolean) {
        val trim = screen as? Screen.Trim ?: return
        trimPreviewJob?.cancel()
        trimPreviewJob = viewModelScope.launch {
            val context = getApplication<Application>()
            val frame = withContext(Dispatchers.IO) {
                runCatching { VideoFrames.frameAt(context, trim.uri, trim.info, trim.startMs, exact) }.getOrNull()
            } ?: return@launch
            val current = screen as? Screen.Trim ?: return@launch
            if (current.uri == trim.uri && current.startMs == trim.startMs) screen = current.copy(preview = frame)
        }
    }

    /** Pasa al encuadre con el cuadro exacto del inicio del tramo (D7). */
    fun confirmTrim() {
        val trim = screen as? Screen.Trim ?: return
        trimPreviewJob?.cancel()
        val clip = VideoClip(trim.uri, trim.info, trim.startMs)
        screen = Screen.Crop(photo = null, video = clip)
        cropJob = viewModelScope.launch {
            val context = getApplication<Application>()
            val first = withContext(Dispatchers.IO) {
                runCatching { VideoFrames.frameAt(context, clip.uri, clip.info, clip.startMs, exact = true) }.getOrNull()
            }
            if ((screen as? Screen.Crop)?.video != clip) return@launch
            if (first == null) {
                screen = Screen.Home
                message("No se pudo leer el video.")
            } else {
                screen = Screen.Crop(photo = first, video = clip)
            }
        }
    }

    /** AC-4. */
    fun cancelTrim() {
        trimPreviewJob?.cancel()
        cropJob?.cancel()
        screen = Screen.Home
    }

    /** AC-5 a AC-7: genera el sticker animado con progreso y lo suma al último pack animado (RN-6). */
    fun saveAnimated(crop: CropTransform, frameSize: Float) {
        val state = screen as? Screen.Crop ?: return
        val clip = state.video ?: return
        if (state.generating != null) return
        screen = state.copy(generating = 0f)
        generateJob = viewModelScope.launch {
            val context = getApplication<Application>()
            val result = withContext(Dispatchers.Default) {
                runCatching {
                    AnimatedWebp.fromVideo(context, clip.uri, clip.info, clip.startMs, crop, frameSize) { p ->
                        ensureActive() // "Cancelar" corta en el próximo cuadro
                        viewModelScope.launch {
                            (screen as? Screen.Crop)?.takeIf { it.video == clip && it.generating != null }
                                ?.let { screen = it.copy(generating = p) }
                        }
                    }
                }
            }
            val current = screen as? Screen.Crop
            if (current?.video != clip) return@launch // se canceló
            result
                .onSuccess {
                    when (it) {
                        is AnimatedResult.Ok -> {
                            when (val added = withContext(Dispatchers.IO) { repository.addSticker(it.bytes, animated = true) }) {
                                is AddResult.Added -> {
                                    screen = Screen.Home
                                    message("Sticker animado guardado en ${added.pack.name}.")
                                }
                                AddResult.LimitReached -> {
                                    screen = Screen.Home
                                    message("Llegaste al máximo de packs.")
                                }
                            }
                        }
                        AnimatedResult.TooBig -> {
                            screen = current.copy(generating = null)
                            message("No entra en el tamaño de WhatsApp. Probá con otro tramo.") // RN-5
                        }
                    }
                }
                .onFailure {
                    Log.e(TAG, "No se pudo generar el sticker animado", it)
                    screen = current.copy(generating = null)
                    message("No se pudo generar el sticker.")
                }
            refresh()
        }
    }


    fun startCrop(uri: Uri) {
        cropJob?.cancel() // si quedaba otra foto cargando, que no aparezca después
        removeJob?.cancel()
        screen = Screen.Crop(photo = null) // RN-1 (spec 002): "Quitar fondo" arranca apagado
        cropJob = viewModelScope.launch {
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

    /** AC-5 (spec 001); AC-7 (spec 002) y AC-5 (spec 003): también corta quitar fondo o la generación en curso. */
    fun cancelCrop() {
        if (saving) return
        cropJob?.cancel()
        removeJob?.cancel()
        generateJob?.cancel()
        screen = Screen.Home
    }

    /** Spec 002, RN-1 a RN-6: prende o apaga "Quitar fondo". */
    fun toggleBackground() {
        val crop = screen as? Screen.Crop ?: return
        val photo = crop.photo ?: return
        when {
            crop.removing -> Unit
            crop.showSubject -> screen = crop.copy(showSubject = false) // RN-6
            crop.removal != null -> screen = crop.copy(showSubject = true) // D9: sin reprocesar
            else -> {
                screen = crop.copy(removing = true)
                removeJob = viewModelScope.launch {
                    val result = backgroundRemover.remove(photo)
                    val current = screen as? Screen.Crop
                    if (current?.photo !== photo) return@launch // se eligió otra foto o se salió
                    screen = when (result) {
                        is RemovalResult.Removed -> current.copy(removal = result, showSubject = true, removing = false)
                        RemovalResult.NoSubject -> {
                            message("No encontré nada para recortar en esta foto.") // RN-3
                            current.copy(removing = false)
                        }
                        is RemovalResult.Unavailable -> {
                            message("No se pudo quitar el fondo ahora. La foto se puede usar igual.") // RN-4
                            current.copy(removing = false)
                        }
                    }
                }
            }
        }
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
        // Se relee del disco (index.json es chico): la pantalla puede mostrar una versión anterior
        // si se acaba de guardar o borrar un sticker, y RN-8 tiene que confirmar la versión real.
        val pack = repository.load().packs.firstOrNull { it.identifier == status.pack.identifier } ?: return null
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
