package com.santi.stickers.ui

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import android.widget.ImageView
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Lo que se muestra de un sticker: una imagen quieta o un WebP animado (spec 009, D6). */
sealed interface Thumb {
    data class Still(val bitmap: ImageBitmap) : Thumb
    class Animated(val drawable: AnimatedImageDrawable) : Thumb
}

/**
 * Miniaturas de los stickers. Los nombres de archivo nunca se reutilizan (RN-14),
 * así que la ruta alcanza como clave de la caché.
 */
object Thumbs {
    const val SIZE_PX = 160

    // Solo las quietas: un AnimatedImageDrawable no se puede mostrar en dos lugares a la vez.
    private val stills = LruCache<String, ImageBitmap>(200) // ~100 KB cada una

    fun cached(file: File): Thumb? = stills.get(file.path)?.let { Thumb.Still(it) }

    /**
     * Spec 009, D4, RN-6: decodifica a [SIZE_PX]. Un WebP animado vuelve como [Thumb.Animated], sin arrancar;
     * si algo falla, null. Pesado: llamar fuera del hilo principal.
     */
    fun decode(file: File): Thumb? = runCatching {
        val drawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(file)) { decoder, _, _ ->
            decoder.setTargetSize(SIZE_PX, SIZE_PX)
        }
        when (drawable) {
            is AnimatedImageDrawable -> Thumb.Animated(drawable)
            is BitmapDrawable -> Thumb.Still(drawable.bitmap.asImageBitmap()).also { stills.put(file.path, it.bitmap) }
            else -> null
        }
    }.getOrNull()
}

@Composable
fun StickerThumbnail(file: File, contentDescription: String?, modifier: Modifier = Modifier) {
    val thumb by produceState(initialValue = Thumbs.cached(file), file.path) {
        if (value == null) value = withContext(Dispatchers.IO) { Thumbs.decode(file) }
    }
    when (val t = thumb) {
        is Thumb.Still -> Image(t.bitmap, contentDescription, modifier, contentScale = ContentScale.Fit)
        is Thumb.Animated -> AnimatedThumbnail(t.drawable, contentDescription, modifier)
        null -> Unit
    }
}

/** Spec 009, D4, D6: se mueve siempre mientras está en pantalla; se para al salir de la composición. */
@Composable
private fun AnimatedThumbnail(drawable: AnimatedImageDrawable, contentDescription: String?, modifier: Modifier) {
    AndroidView(
        factory = { context ->
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                this.contentDescription = contentDescription
            }
        },
        update = { view ->
            if (view.drawable !== drawable) view.setImageDrawable(drawable)
            drawable.start()
        },
        modifier = modifier,
    )
    DisposableEffect(drawable) { onDispose { drawable.stop() } }
}
