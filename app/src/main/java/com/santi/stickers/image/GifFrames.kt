package com.santi.stickers.image

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.bumptech.glide.gifdecoder.GifDecoder
import com.bumptech.glide.gifdecoder.GifHeaderParser
import com.bumptech.glide.gifdecoder.StandardGifDecoder
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Datos de un GIF. [workWidth]/[workHeight]: tamaño de trabajo, como en [VideoInfo] (lado largo ≤ 1024). */
data class GifInfo(val width: Int, val height: Int, val timeline: GifTimeline) {
    val durationMs get() = timeline.durationMs
    private val workScale get() = min(1f, VideoFrames.WORK_MAX_SIDE.toFloat() / max(width, height))
    val workWidth get() = max(1, (width * workScale).roundToInt())
    val workHeight get() = max(1, (height * workScale).roundToInt())
}

/** D6: el GIF pesa más de 20 MB. */
class GifTooLargeException : Exception("El GIF pesa más de ${GifFrames.MAX_FILE_BYTES / (1024 * 1024)} MB")

/**
 * Lectura de cuadros de un GIF (spec 005, D3) con el lector de Glide. Como [VideoFrames], entrega cuadros
 * en ARGB_8888 al tamaño de trabajo, conservando la transparencia (D5). Pesado: llamar fuera del hilo principal.
 */
object GifFrames {
    const val MAX_FILE_BYTES = 20L * 1024 * 1024 // D6

    fun info(context: Context, uri: Uri): GifInfo {
        val header = GifHeaderParser().setData(read(context, uri)).parseHeader()
        require(header.status == GifDecoder.STATUS_OK && header.numFrames > 0) { "No es un GIF válido (estado ${header.status})" }
        val decoder = StandardGifDecoder(SimpleBitmapProvider)
        decoder.setData(header, ByteArray(0)) // solo para leer las duraciones; no decodifica cuadros
        val delays = (0 until header.numFrames).map { decoder.getDelay(it) }
        decoder.clear()
        return GifInfo(header.width, header.height, GifTimeline(delays))
    }

    /** Cuadro que se ve en [timeMs]. Un GIF se decodifica en orden, así que recorre desde el principio. */
    fun frameAt(context: Context, uri: Uri, info: GifInfo, timeMs: Long): Bitmap {
        var result: Bitmap? = null
        decode(context, uri, info, lastIndex = info.timeline.indexAt(timeMs)) { _, frame ->
            result?.recycle()
            result = frame
        }
        return result ?: error("No se pudo leer el cuadro en $timeMs ms")
    }

    /**
     * Recorre [range] a [TrimMath.fpsFor] (RN-3) sobre la línea de tiempo real del GIF (RN-4) y entrega cada
     * cuadro en [onFrame] (se libera después de la llamada). Un cuadro largo del GIF se repite las veces necesarias.
     */
    fun frames(
        context: Context,
        uri: Uri,
        info: GifInfo,
        range: ClipRange,
        onProgress: (Float) -> Unit = {},
        onFrame: (index: Int, frame: Bitmap) -> Unit,
    ): Int {
        require(range.startMs >= 0 && range.endMs <= info.durationMs) { "El tramo $range sale del GIF" }
        val fps = TrimMath.fpsFor(range.lengthMs)
        val count = TrimMath.frameCount(range.lengthMs)
        require(count >= 2) { "El tramo es demasiado corto" } // RN-2
        val wanted = (0 until count).map { k -> info.timeline.indexAt(range.startMs + k * 1000L / fps) }
        var delivered = 0
        decode(context, uri, info, lastIndex = wanted.last()) { index, frame ->
            repeat(wanted.count { it == index }) {
                onFrame(delivered++, frame)
                onProgress(delivered / count.toFloat())
            }
            frame.recycle()
        }
        return delivered
    }

    /** Decodifica en orden los cuadros 0..[lastIndex] y entrega cada uno normalizado (el que lo recibe lo libera). */
    private fun decode(context: Context, uri: Uri, info: GifInfo, lastIndex: Int, onFrame: (Int, Bitmap) -> Unit) {
        val bytes = read(context, uri)
        val header = GifHeaderParser().setData(bytes).parseHeader()
        require(header.status == GifDecoder.STATUS_OK) { "No es un GIF válido (estado ${header.status})" }
        val decoder = StandardGifDecoder(SimpleBitmapProvider)
        try {
            decoder.setDefaultBitmapConfig(Bitmap.Config.ARGB_8888)
            decoder.setData(header, bytes)
            for (i in 0..min(lastIndex, header.numFrames - 1)) {
                decoder.advance()
                val raw = decoder.nextFrame ?: error("No se pudo leer el cuadro $i del GIF (estado ${decoder.status})")
                onFrame(i, normalize(raw, info))
            }
        } finally {
            decoder.clear()
        }
    }

    /** Al tamaño de trabajo y en ARGB_8888 (conserva el alfa, D5). */
    private fun normalize(raw: Bitmap, info: GifInfo): Bitmap {
        var b = raw
        if (b.width != info.workWidth || b.height != info.workHeight) {
            b = Bitmap.createScaledBitmap(raw, info.workWidth, info.workHeight, true)
            if (b !== raw) raw.recycle()
        }
        if (b.config != Bitmap.Config.ARGB_8888) {
            val copy = b.copy(Bitmap.Config.ARGB_8888, false)
            b.recycle()
            b = copy
        }
        return b
    }

    /** Lee el archivo entero; más de [MAX_FILE_BYTES] es [GifTooLargeException] (D6, AC-8). */
    private fun read(context: Context, uri: Uri): ByteArray {
        val input = context.contentResolver.openInputStream(uri) ?: error("No se pudo abrir $uri")
        return input.use {
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = it.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
                if (out.size() > MAX_FILE_BYTES) throw GifTooLargeException()
            }
            out.toByteArray()
        }
    }

    /**
     * Cada cuadro en un Bitmap nuevo: quien lo recibe es el dueño. El lector no reutiliza nada de lo que entrega
     * (verificado en el bytecode de StandardGifDecoder 5.0.9: getNextFrame pide un Bitmap nuevo en cada llamada y
     * guarda aparte el que necesita para el cuadro siguiente).
     */
    private object SimpleBitmapProvider : GifDecoder.BitmapProvider {
        override fun obtain(width: Int, height: Int, config: Bitmap.Config): Bitmap = Bitmap.createBitmap(width, height, config)
        override fun release(bitmap: Bitmap) = Unit
        override fun obtainByteArray(size: Int) = ByteArray(size)
        override fun release(bytes: ByteArray) = Unit
        override fun obtainIntArray(size: Int) = IntArray(size)
        override fun release(array: IntArray) = Unit
    }
}
