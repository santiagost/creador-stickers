package com.santi.stickers.image

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri

/** Fuente de cuadros de un sticker animado: video o GIF (spec 005, sección 10). Pesado: fuera del hilo principal. */
sealed interface AnimationSource {
    val uri: Uri
    val durationMs: Long
    val workWidth: Int
    val workHeight: Int

    /** Cuadro en [timeMs]. [exact] solo cambia algo en videos (ver [VideoFrames.frameAt]). */
    fun frameAt(context: Context, timeMs: Long, exact: Boolean): Bitmap

    fun frames(context: Context, range: ClipRange, onProgress: (Float) -> Unit = {}, onFrame: (index: Int, frame: Bitmap) -> Unit): Int
}

data class VideoSource(override val uri: Uri, val info: VideoInfo) : AnimationSource {
    override val durationMs get() = info.durationMs
    override val workWidth get() = info.workWidth
    override val workHeight get() = info.workHeight
    override fun frameAt(context: Context, timeMs: Long, exact: Boolean) = VideoFrames.frameAt(context, uri, info, timeMs, exact)
    override fun frames(context: Context, range: ClipRange, onProgress: (Float) -> Unit, onFrame: (Int, Bitmap) -> Unit) =
        VideoFrames.frames(context, uri, info, range, onProgress, onFrame)
}

data class GifSource(override val uri: Uri, val info: GifInfo) : AnimationSource {
    override val durationMs get() = info.durationMs
    override val workWidth get() = info.workWidth
    override val workHeight get() = info.workHeight
    override fun frameAt(context: Context, timeMs: Long, exact: Boolean) = GifFrames.frameAt(context, uri, info, timeMs)
    override fun frames(context: Context, range: ClipRange, onProgress: (Float) -> Unit, onFrame: (Int, Bitmap) -> Unit) =
        GifFrames.frames(context, uri, info, range, onProgress, onFrame)
}
