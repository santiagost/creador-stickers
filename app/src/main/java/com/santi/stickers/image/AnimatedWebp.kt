package com.santi.stickers.image

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.aureusapps.android.webpandroid.encoder.WebPAnimEncoder
import com.aureusapps.android.webpandroid.encoder.WebPAnimEncoderOptions
import com.aureusapps.android.webpandroid.encoder.WebPConfig
import com.aureusapps.android.webpandroid.encoder.WebPMuxAnimParams
import com.aureusapps.android.webpandroid.encoder.WebPPreset
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt

sealed interface AnimatedResult {
    data class Ok(val bytes: ByteArray, val fps: Int, val quality: Float) : AnimatedResult {
        override fun toString() = "Ok(${bytes.size / 1024} KB, $fps fps, calidad $quality)"
    }

    /** RN-5: no entra en 500 KB con ninguna combinación. */
    data object TooBig : AnimatedResult
}

/** WebP animado para WhatsApp con webp-android (spec 003, D4 y D6). Pesado: llamar fuera del hilo principal. */
object AnimatedWebp {
    const val MAX_BYTES = 500 * 1024
    const val SIZE = CropMath.STICKER_SIZE
    private const val TAG = "AnimatedWebp"

    /** D6: cuadros por segundo y calidad, de mejor a peor. */
    val LADDER = listOf(15 to 70f, 12 to 60f, 10 to 50f, 10 to 40f, 8 to 35f, 8 to 25f)

    /**
     * Arma el sticker con [frames] a [VideoFrames.FPS] (ya a 512x512, ARGB_8888), bajando cuadros por segundo
     * y calidad hasta entrar en [MAX_BYTES] (RN-4, RN-5). Repetición infinita y cuadros de igual duración.
     */
    fun encode(
        context: Context,
        frames: List<Bitmap>,
        maxBytes: Int = MAX_BYTES,
        ladder: List<Pair<Int, Float>> = LADDER,
        onProgress: (Float) -> Unit = {},
    ): AnimatedResult {
        require(frames.size >= 2) { "Hacen falta al menos 2 cuadros" }
        val clipMs = frames.size * 1000L / VideoFrames.FPS
        val out = File(context.cacheDir, "anim-${System.nanoTime()}.webp")
        try {
            for ((fps, quality) in ladder) {
                // webp-android no pisa un archivo que ya existe: sin esto, todos los intentos medían el primero.
                out.delete()
                val frameMs = 1000 / fps // 66, 83, 100 o 125 ms: siempre ≥ 8 ms
                val n = (clipMs * fps / 1000).toInt().coerceAtLeast(2)
                val picked = (0 until n).map { k -> frames[min(frames.size - 1, (k * VideoFrames.FPS / fps.toFloat()).roundToInt())] }
                val encoder = WebPAnimEncoder(
                    context, SIZE, SIZE,
                    WebPAnimEncoderOptions(minimizeSize = true, animParams = WebPMuxAnimParams(backgroundColor = 0, loopCount = 0)),
                )
                try {
                    encoder.configure(WebPConfig(lossless = WebPConfig.COMPRESSION_LOSSY, quality = quality, method = 4), WebPPreset.WEBP_PRESET_PICTURE)
                    picked.forEachIndexed { k, frame ->
                        encoder.addFrame((k * frameMs).toLong(), frame)
                        onProgress((k + 1f) / n) // si hay que reintentar, el progreso vuelve a empezar
                    }
                    encoder.assemble((n * frameMs).toLong(), Uri.fromFile(out))
                } finally {
                    encoder.release()
                }
                Log.i(TAG, "intento $fps fps, calidad $quality: ${out.length() / 1024} KB, $n cuadros")
                if (out.length() in 1..maxBytes.toLong()) return AnimatedResult.Ok(out.readBytes(), fps, quality)
            }
            return AnimatedResult.TooBig
        } finally {
            out.delete()
        }
    }

    /**
     * Video → sticker: lee el tramo, encuadra cada cuadro con [crop] (calculado en un cuadro de [frameSize]
     * sobre el tamaño de trabajo, D7) y lo codifica. El progreso va de 0 a 0,4 leyendo y de 0,4 a 1 codificando.
     */
    fun fromVideo(
        context: Context,
        uri: Uri,
        info: VideoInfo,
        startMs: Long,
        crop: CropTransform,
        frameSize: Float,
        onProgress: (Float) -> Unit = {},
    ): AnimatedResult {
        val out = CropMath.toOutput(crop, frameSize)
        val rendered = ArrayList<Bitmap>(VideoFrames.frameCount(info, startMs))
        try {
            VideoFrames.frames(context, uri, info, startMs, onProgress = { onProgress(it * 0.4f) }) { _, frame ->
                rendered += StickerImages.render(frame, out)
            }
            return encode(context, rendered) { onProgress(0.4f + it * 0.6f) }
        } finally {
            rendered.forEach { it.recycle() }
        }
    }
}
