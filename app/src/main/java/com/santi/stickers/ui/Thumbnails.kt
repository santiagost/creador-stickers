package com.santi.stickers.ui

import android.graphics.ImageDecoder
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Miniaturas de los stickers. Los nombres de archivo nunca se reutilizan (RN-14),
 * así que la ruta alcanza como clave de la caché.
 */
private object ThumbCache {
    const val SIZE_PX = 160
    val cache = LruCache<String, ImageBitmap>(200) // ~100 KB cada una
}

@Composable
fun StickerThumbnail(file: File, contentDescription: String?, modifier: Modifier = Modifier) {
    val bitmap by produceState(initialValue = ThumbCache.cache.get(file.path), file.path) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, _, _ ->
                        decoder.setTargetSize(ThumbCache.SIZE_PX, ThumbCache.SIZE_PX)
                    }.asImageBitmap()
                }.getOrNull()
            }?.also { ThumbCache.cache.put(file.path, it) }
        }
    }
    bitmap?.let { Image(it, contentDescription, modifier, contentScale = ContentScale.Fit) }
}
