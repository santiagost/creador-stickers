package com.santi.stickers.ui

import android.app.Application
import android.content.Context
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
import com.santi.stickers.data.CreatePackResult
import com.santi.stickers.data.DeletePackResult
import com.santi.stickers.data.DeleteResult
import com.santi.stickers.data.PackRules
import com.santi.stickers.data.PackStatus
import com.santi.stickers.data.Packs
import com.santi.stickers.data.RenameResult
import com.santi.stickers.data.packStatuses
import com.santi.stickers.image.AnimatedResult
import com.santi.stickers.image.AnimationSource
import com.santi.stickers.image.AnimatedWebp
import com.santi.stickers.image.BackgroundRemover
import com.santi.stickers.image.ClipRange
import com.santi.stickers.image.CropMath
import com.santi.stickers.image.CropTransform
import com.santi.stickers.image.GifFrames
import com.santi.stickers.image.GifSource
import com.santi.stickers.image.GifTooLargeException
import com.santi.stickers.image.MlKitBackgroundRemover
import com.santi.stickers.image.RemovalResult
import com.santi.stickers.image.StickerImages
import com.santi.stickers.image.TrimMath
import com.santi.stickers.image.VideoFrames
import com.santi.stickers.image.VideoSource
import com.santi.stickers.share.SharedKind
import com.santi.stickers.share.sharedKind
import com.santi.stickers.x.XDownload
import com.santi.stickers.x.XDownloader
import com.santi.stickers.x.XLinks
import java.io.File
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

    /** Spec 004: pantalla de un pack. */
    data class Pack(val packId: String) : Screen

    /** Spec 007: bajando el video de un tweet ([progress] de 0 a 1). */
    data class Downloading(val progress: Float) : Screen

    /** Spec 006: algo compartido desde otra app, esperando que se elija el pack. */
    data class ChoosePack(val uri: Uri, val kind: SharedKind) : Screen

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

    /** Spec 003/005: elegir el tramo. [preview] es el cuadro donde arranca; null mientras carga. */
    data class Trim(val source: AnimationSource, val range: ClipRange, val preview: Bitmap?) : Screen
}

/** Spec 003: el tramo elegido de un video. */
data class VideoClip(val source: AnimationSource, val range: ClipRange)

data class HomeState(
    val loading: Boolean = true,
    val packs: List<PackStatus> = emptyList(),
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

    init {
        // Spec 007, D6: videos de X que hayan quedado de otra vez (más de una hora, para no tocar los de otra pantalla abierta).
        viewModelScope.launch(Dispatchers.IO) { XDownloader.clearCache(getApplication(), olderThanMs = 60 * 60 * 1000L) }
    }

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
                )
            }
        }
    }

    // --- Packs (spec 004) ---

    /** Pack al que va el sticker que se está creando (RN-2). */
    private var targetPackId: String? = null

    fun suggestedName(animated: Boolean): String = repository.suggestedName(animated)

    /** AC-2, AC-3. */
    fun createPack(name: String, animated: Boolean) {
        viewModelScope.launch {
            when (val r = withContext(Dispatchers.IO) { repository.createPack(name, animated) }) {
                is CreatePackResult.Created -> screen = Screen.Pack(r.pack.identifier)
                CreatePackResult.LimitReached -> message("Llegaste al máximo de ${PackRules.MAX_PACKS} packs.")
                CreatePackResult.InvalidName -> message("El nombre no puede estar vacío ni pasar de ${PackRules.MAX_NAME_LENGTH} letras.")
            }
            refresh()
        }
    }

    /** Spec 008, RN-1, RN-2. */
    fun deletePack(packId: String) {
        viewModelScope.launch {
            when (val r = withContext(Dispatchers.IO) { repository.deletePack(packId) }) {
                is DeletePackResult.Deleted -> {
                    screen = Screen.Home
                    message("Pack \"${r.pack.name}\" borrado.")
                }
                DeletePackResult.NotFound -> screen = Screen.Home
            }
            refresh()
        }
    }

    /** Spec 008, D3, AC-4, AC-5. */
    fun renamePack(packId: String, name: String) {
        viewModelScope.launch {
            when (withContext(Dispatchers.IO) { repository.renamePack(packId, name) }) {
                is RenameResult.Renamed -> refresh()
                RenameResult.InvalidName -> message("El nombre no puede estar vacío ni pasar de ${PackRules.MAX_NAME_LENGTH} letras.")
                RenameResult.Unchanged, RenameResult.NotFound -> Unit
            }
        }
    }

    fun openPack(packId: String) {
        screen = Screen.Pack(packId)
    }

    fun closePack() {
        screen = Screen.Home
    }

    /** Vuelve al pack desde el que se estaba creando un sticker (o a Inicio si no hay). */
    private fun backToPack() {
        screen = targetPackId?.let { Screen.Pack(it) } ?: Screen.Home
    }

    // --- Crear sticker ---

    // --- Compartido desde otra app (spec 006) ---

    /** RN-1, AC-6, AC-8: abre "¿A qué pack?". La Activity lo llama una sola vez por cada envío (RN-5). */
    fun onShared(uri: Uri?, mime: String?, more: Boolean) {
        val kind = sharedKind(mime)
        when {
            uri == null -> message("No se pudo abrir lo que compartiste.")
            kind == null -> message("Ese archivo no sirve para stickers.")
            else -> {
                if (more) message("Se usó solo el primer archivo.") // D4
                Log.i(TAG, "compartido $kind $mime")
                screen = Screen.ChoosePack(uri, kind)
            }
        }
    }

    /** AC-3, AC-5: sigue con el encuadre o el tramo, como desde "+ Nuevo sticker". */
    fun chooseSharedPack(packId: String) {
        val shared = screen as? Screen.ChoosePack ?: return
        val pack = repository.load().packs.firstOrNull { it.identifier == packId } ?: return
        if (pack.isFull || pack.animated != shared.kind.animated) return // la pantalla no deja elegirlos
        targetPackId = packId
        when (shared.kind) {
            SharedKind.PHOTO -> startCrop(shared.uri)
            SharedKind.VIDEO -> startVideo(shared.uri)
            SharedKind.GIF -> startGif(shared.uri)
        }
    }

    /** D3, AC-4, RN-4: crea el pack con el tipo ya elegido y sigue con ese pack. */
    fun createPackForShared(name: String) {
        val shared = screen as? Screen.ChoosePack ?: return
        viewModelScope.launch {
            when (val r = withContext(Dispatchers.IO) { repository.createPack(name, shared.kind.animated) }) {
                is CreatePackResult.Created -> {
                    refresh()
                    chooseSharedPack(r.pack.identifier)
                }
                CreatePackResult.LimitReached -> message("Llegaste al máximo de ${PackRules.MAX_PACKS} packs.")
                CreatePackResult.InvalidName -> message("El nombre no puede estar vacío ni pasar de ${PackRules.MAX_NAME_LENGTH} letras.")
            }
        }
    }

    fun cancelShared() {
        discardDownload()
        screen = Screen.Home
    }

    // --- Links de X (spec 007) ---

    private var downloadJob: Job? = null
    private var downloadReturn: Screen = Screen.Home

    /** D6: el video bajado, para borrarlo al terminar o cancelar. */
    private var downloadedFile: File? = null

    /** D3, RN-1, RN-2: texto compartido (desde X). Va a "¿A qué pack?" después de bajar. */
    fun onSharedText(text: String?) {
        val id = text?.let(XLinks::statusId) ?: return message("Eso no es un link de X.")
        targetPackId = null
        download(id, intoPack = false)
    }

    /** D3, RN-7: "Pegar link de X" desde un pack animado ([tryStartNewSticker] ya eligió el pack). */
    fun onPastedLink(text: String?) {
        val id = text?.let(XLinks::statusId) ?: return message("No copiaste un link de X.")
        download(id, intoPack = true)
    }

    /** RN-5, RN-6, AC-4. */
    private fun download(statusId: String, intoPack: Boolean) {
        discardDownload()
        downloadJob?.cancel()
        downloadReturn = screen
        screen = Screen.Downloading(0f)
        downloadJob = viewModelScope.launch {
            val context = getApplication<Application>()
            val result = XDownloader.download(context, statusId) { p ->
                viewModelScope.launch { (screen as? Screen.Downloading)?.let { screen = it.copy(progress = p) } }
            }
            if (screen !is Screen.Downloading) {
                (result as? XDownload.Ok)?.file?.delete() // se canceló justo al terminar
                return@launch
            }
            fun fail(text: String) {
                screen = downloadReturn
                message(text)
            }
            when (result) {
                is XDownload.Ok -> {
                    Log.i(TAG, "bajado ${result.file.length() / 1024} KB, gif=${result.isGif}")
                    downloadedFile = result.file
                    if (result.more) message("El tweet tiene varios videos: se usó el primero.") // D4
                    val uri = Uri.fromFile(result.file)
                    if (intoPack) {
                        backToPack()
                        startVideo(uri)
                    } else {
                        screen = Screen.ChoosePack(uri, SharedKind.VIDEO)
                    }
                }
                XDownload.NoConnection -> fail("No hay conexión.")
                XDownload.Unreadable -> fail("No se pudo leer ese tweet. Puede ser privado o haberse borrado.")
                XDownload.NoVideo -> fail("Ese tweet no tiene video ni GIF.")
                XDownload.TooBig -> fail("Ese video pesa más de ${XDownloader.MAX_BYTES / (1024 * 1024)} MB.") // D5
            }
        }
    }

    /** AC-4. */
    fun cancelDownload() {
        downloadJob?.cancel()
        if (screen is Screen.Downloading) screen = downloadReturn
    }

    private fun discardDownload() {
        downloadedFile?.delete()
        downloadedFile = null
    }

    /** AC-5 y AC-6 (spec 004): con el pack lleno no se abre el selector. */
    fun tryStartNewSticker(packId: String, openPicker: (animated: Boolean) -> Unit) {
        val pack = repository.load().packs.firstOrNull { it.identifier == packId } ?: return
        if (pack.isFull) {
            message("Este pack ya tiene ${PackRules.MAX_STICKERS} stickers. Creá otro pack.")
            return
        }
        targetPackId = packId
        openPicker(pack.animated)
    }

    // --- Video (spec 003) ---

    private var trimPreviewJob: Job? = null
    private var generateJob: Job? = null

    /** AC-1/AC-2 (spec 003): abre la pantalla de tramo con el cuadro del principio. */
    fun startVideo(uri: Uri) = startAnimation(uri, what = "ese video") { VideoSource(uri, VideoFrames.info(it, uri)) }

    /** Spec 005, AC-7 y AC-8: lo mismo desde un GIF. */
    fun startGif(uri: Uri) = startAnimation(uri, what = "ese GIF") { GifSource(uri, GifFrames.info(it, uri)) }

    private fun startAnimation(uri: Uri, what: String, open: (Context) -> AnimationSource) {
        cropJob?.cancel()
        cropJob = viewModelScope.launch {
            val context = getApplication<Application>()
            val source = withContext(Dispatchers.IO) {
                runCatching { open(context) }
                    .onFailure { Log.w(TAG, "No se pudo abrir $uri", it) }
            }
            val opened = source.getOrNull()
            when {
                source.exceptionOrNull() is GifTooLargeException -> message("Ese GIF pesa más de 20 MB.") // D6
                opened == null -> message("No se pudo abrir $what.") // AC-8
                opened is GifSource && opened.info.timeline.frameCount < 2 -> message("Ese GIF no es animado.")
                TrimMath.frameCount(TrimMath.initial(opened.durationMs).lengthMs) < 2 -> message("Es demasiado corto.") // RN-2
                else -> {
                    Log.i(TAG, "animación $opened")
                    screen = Screen.Trim(opened, TrimMath.initial(opened.durationMs), preview = null)
                    loadTrimPreview(exact = true)
                }
            }
        }
    }

    /**
     * Spec 005, AC-2 a AC-5: la pantalla calcula la caja con [TrimMath]. Mientras se arrastra se muestra un cuadro
     * rápido; al soltar, el exacto.
     */
    fun setTrimRange(range: ClipRange, exact: Boolean) {
        val trim = screen as? Screen.Trim ?: return
        screen = trim.copy(range = range)
        if (exact || range.startMs != trim.range.startMs) loadTrimPreview(exact)
    }

    /** Los cuadros de muestra se leen de a uno; si llega otro pedido, los que esperaban se descartan. */
    private val previewDispatcher = Dispatchers.IO.limitedParallelism(1)

    private fun loadTrimPreview(exact: Boolean) {
        val trim = screen as? Screen.Trim ?: return
        trimPreviewJob?.cancel()
        trimPreviewJob = viewModelScope.launch {
            val context = getApplication<Application>()
            val frame = withContext(previewDispatcher) {
                runCatching { trim.source.frameAt(context, trim.range.startMs, exact) }.getOrNull()
            } ?: return@launch
            val current = screen as? Screen.Trim ?: return@launch
            if (current.source == trim.source && current.range.startMs == trim.range.startMs) screen = current.copy(preview = frame)
        }
    }

    /** Pasa al encuadre con el cuadro exacto del inicio del tramo (D7). */
    fun confirmTrim() {
        val trim = screen as? Screen.Trim ?: return
        trimPreviewJob?.cancel()
        val clip = VideoClip(trim.source, trim.range)
        screen = Screen.Crop(photo = null, video = clip)
        cropJob = viewModelScope.launch {
            val context = getApplication<Application>()
            val first = withContext(Dispatchers.IO) {
                runCatching { clip.source.frameAt(context, clip.range.startMs, exact = true) }.getOrNull()
            }
            if ((screen as? Screen.Crop)?.video != clip) return@launch
            if (first == null) {
                backToPack()
                message("No se pudo leer el archivo.")
            } else {
                screen = Screen.Crop(photo = first, video = clip)
            }
        }
    }

    /** AC-4. */
    fun cancelTrim() {
        discardDownload()
        trimPreviewJob?.cancel()
        cropJob?.cancel()
        backToPack()
    }

    /** AC-5 a AC-7 (spec 003): genera el sticker animado con progreso y lo suma al pack elegido (spec 004, RN-2). */
    fun saveAnimated(crop: CropTransform, frameSize: Float) {
        val state = screen as? Screen.Crop ?: return
        val clip = state.video ?: return
        if (state.generating != null) return
        screen = state.copy(generating = 0f)
        generateJob = viewModelScope.launch {
            val context = getApplication<Application>()
            val result = withContext(Dispatchers.Default) {
                runCatching {
                    AnimatedWebp.fromSource(context, clip.source, clip.range, crop, frameSize) { p ->
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
                            onAdded(withContext(Dispatchers.IO) { addToTarget(it.bytes) })
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
                backToPack()
                message("No se pudo abrir esa foto.")
            } else {
                screen = Screen.Crop(photo)
            }
        }
    }

    /** AC-5 (spec 001); AC-7 (spec 002) y AC-5 (spec 003): también corta quitar fondo o la generación en curso. */
    fun cancelCrop() {
        if (!saving) discardDownload()
        if (saving) return
        cropJob?.cancel()
        removeJob?.cancel()
        generateJob?.cancel()
        backToPack()
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
                    addToTarget(StickerImages.encodeWebp(sticker))
                }
            }
            saving = false
            result
                .onSuccess { onAdded(it) }
                .onFailure {
                    Log.e(TAG, "No se pudo guardar el sticker", it)
                    message("No se pudo guardar el sticker.")
                }
            refresh()
        }
    }

    /** Spec 004, RN-2: el sticker va al pack desde el que se empezó. */
    private fun addToTarget(webp: ByteArray): AddResult =
        targetPackId?.let { repository.addSticker(it, webp) } ?: AddResult.NotFound

    /** Después de guardar se vuelve a la pantalla del pack (AC-5). */
    private fun onAdded(result: AddResult) {
        discardDownload()
        when (result) {
            is AddResult.Added -> {
                screen = Screen.Pack(result.pack.identifier)
                message("Sticker guardado en ${result.pack.name}.")
            }
            AddResult.PackFull -> {
                backToPack()
                message("Este pack ya tiene ${PackRules.MAX_STICKERS} stickers.")
            }
            AddResult.NotFound -> {
                screen = Screen.Home
                message("No se encontró el pack.")
            }
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
