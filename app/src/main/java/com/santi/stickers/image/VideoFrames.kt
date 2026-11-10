package com.santi.stickers.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Datos de un video. [width]/[height] son como se ve (rotación aplicada);
 * [workWidth]/[workHeight] es el tamaño con el que se trabaja (lado largo ≤ [VideoFrames.WORK_MAX_SIDE]).
 */
data class VideoInfo(val durationMs: Long, val width: Int, val height: Int, val rotation: Int, val frameCount: Int?) {
    private val workScale get() = min(1f, VideoFrames.WORK_MAX_SIDE.toFloat() / max(width, height))
    val workWidth get() = max(1, (width * workScale).roundToInt())
    val workHeight get() = max(1, (height * workScale).roundToInt())
}

/**
 * Lectura de cuadros de un video (spec 003, D5). Todo lo que devuelve está en ARGB_8888, en memoria normal
 * y al tamaño de trabajo, para que el encuadre calculado sobre un cuadro valga para todos.
 * Son operaciones pesadas: llamarlas fuera del hilo principal.
 */
object VideoFrames {
    const val FPS = 15
    const val CLIP_MS = 3000L // spec 003, D2
    const val WORK_MAX_SIDE = 1024

    /** Memoria máxima para un bloque de cuadros a resolución original (getFramesAtIndex). */
    private const val CHUNK_BYTES = 64L * 1024 * 1024

    private fun <T> retriever(context: Context, uri: Uri, block: (MediaMetadataRetriever) -> T): T =
        MediaMetadataRetriever().use { r ->
            r.setDataSource(context, uri)
            block(r)
        }

    fun info(context: Context, uri: Uri): VideoInfo = retriever(context, uri) { r -> readInfo(r) }

    private fun readInfo(r: MediaMetadataRetriever): VideoInfo {
        fun meta(key: Int) = r.extractMetadata(key)
        val rotation = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        var w = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: error("El video no tiene ancho")
        var h = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: error("El video no tiene alto")
        if (rotation == 90 || rotation == 270) w = h.also { h = w }
        return VideoInfo(
            durationMs = meta(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: error("El video no tiene duración"),
            width = w,
            height = h,
            rotation = rotation,
            frameCount = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toIntOrNull(),
        )
    }

    /** RN-2: el tramo no puede arrancar después de esto (si el video es corto, 0). */
    fun maxStart(info: VideoInfo): Long = (info.durationMs - CLIP_MS).coerceAtLeast(0)

    /** RN-2: largo real del tramo que arranca en [startMs]. */
    fun clipLength(info: VideoInfo, startMs: Long): Long = min(CLIP_MS, info.durationMs - startMs).coerceAtLeast(0)

    /** Cantidad de cuadros que va a tener el sticker (a [FPS]). */
    fun frameCount(info: VideoInfo, startMs: Long): Int = (clipLength(info, startMs) * FPS / 1000).toInt()

    /** Cuadro en [timeMs]. [exact] = el cuadro justo (más lento); si no, el cuadro clave más cercano (rápido, para la barra). */
    fun frameAt(context: Context, uri: Uri, info: VideoInfo, timeMs: Long, exact: Boolean): Bitmap = retriever(context, uri) { r ->
        val option = if (exact) MediaMetadataRetriever.OPTION_CLOSEST else MediaMetadataRetriever.OPTION_CLOSEST_SYNC
        val frame = r.getScaledFrameAtTime(timeMs * 1000, option, info.workWidth, info.workHeight)
            ?: error("No se pudo leer el cuadro en $timeMs ms")
        normalize(frame, info)
    }

    /**
     * Recorre los cuadros del tramo a [FPS] y entrega cada uno en [onFrame] (se libera después de la llamada).
     * Primero por índice, que es mucho más rápido; si el video no lo permite, por tiempo.
     */
    fun frames(
        context: Context,
        uri: Uri,
        info: VideoInfo,
        startMs: Long,
        onProgress: (Float) -> Unit = {},
        onFrame: (index: Int, frame: Bitmap) -> Unit,
    ): Int = retriever(context, uri) { r ->
        val count = frameCount(info, startMs)
        require(count >= 2) { "El tramo es demasiado corto" } // RN-2: WhatsApp pide 2 cuadros o más
        val byIndex = info.frameCount?.takeIf { it > 1 }?.let { total ->
            runCatching { framesByIndex(r, info, total, startMs, count, onProgress, onFrame) }.getOrNull()
        }
        byIndex ?: framesByTime(r, info, startMs, count, onProgress, onFrame)
    }

    private fun framesByIndex(
        r: MediaMetadataRetriever,
        info: VideoInfo,
        total: Int,
        startMs: Long,
        count: Int,
        onProgress: (Float) -> Unit,
        onFrame: (Int, Bitmap) -> Unit,
    ): Int {
        val nativeFps = total * 1000.0 / info.durationMs
        val wanted = (0 until count).map { i -> ((startMs + i * 1000.0 / FPS) * nativeFps / 1000).roundToInt().coerceIn(0, total - 1) }
        // Cuántos cuadros a resolución original caben en memoria por bloque.
        val frameBytes = info.width.toLong() * info.height * 4
        val chunk = (CHUNK_BYTES / frameBytes).toInt().coerceIn(1, 8)
        var delivered = 0
        var idx = wanted.first()
        while (idx <= wanted.last()) {
            val n = min(chunk, wanted.last() - idx + 1)
            r.getFramesAtIndex(idx, n).forEachIndexed { j, raw ->
                val frameIndex = idx + j
                val times = wanted.count { it == frameIndex } // un video de menos de 15 fps repite cuadros
                if (times > 0) {
                    val frame = normalize(raw, info)
                    repeat(times) {
                        onFrame(delivered++, frame)
                        onProgress(delivered / count.toFloat())
                    }
                    if (frame !== raw) frame.recycle()
                }
                raw.recycle()
            }
            idx += n
        }
        return delivered
    }

    private fun framesByTime(
        r: MediaMetadataRetriever,
        info: VideoInfo,
        startMs: Long,
        count: Int,
        onProgress: (Float) -> Unit,
        onFrame: (Int, Bitmap) -> Unit,
    ): Int {
        for (i in 0 until count) {
            val tUs = (startMs * 1000) + i * 1_000_000L / FPS
            val raw = r.getScaledFrameAtTime(tUs, MediaMetadataRetriever.OPTION_CLOSEST, info.workWidth, info.workHeight)
                ?: error("No se pudo leer el cuadro $i")
            val frame = normalize(raw, info)
            onFrame(i, frame)
            onProgress((i + 1) / count.toFloat())
            frame.recycle()
            if (raw !== frame) raw.recycle()
        }
        return count
    }

    /**
     * Deja el cuadro como se ve, al tamaño de trabajo, en ARGB_8888 y memoria normal (D5).
     * Si viene sin rotar (orientación distinta de la esperada), lo rota según la metadata. No verificado con
     * videos rotados reales: los de prueba tienen rotación 0.
     */
    private fun normalize(raw: Bitmap, info: VideoInfo): Bitmap {
        var b = raw
        val expectLandscape = info.width > info.height
        val isLandscape = b.width > b.height
        if (info.rotation != 0 && info.width != info.height && expectLandscape != isLandscape) {
            b = Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(info.rotation.toFloat()) }, true)
        }
        if (b.width != info.workWidth || b.height != info.workHeight) {
            b = Bitmap.createScaledBitmap(b, info.workWidth, info.workHeight, true)
        }
        if (b.config != Bitmap.Config.ARGB_8888) {
            b = b.copy(Bitmap.Config.ARGB_8888, false)
        }
        return b
    }
}
