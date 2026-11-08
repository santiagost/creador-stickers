package com.santi.stickers.data

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.santi.stickers.image.StickerImages
import java.io.File

/**
 * Única instancia del repositorio de packs, compartida por la UI y el ContentProvider
 * (así comparten también el lock). Los packs viven en `filesDir/packs`.
 */
object Packs {
    @Volatile
    private var instance: PackRepository? = null

    fun repository(context: Context): PackRepository =
        instance ?: synchronized(this) {
            instance ?: PackRepository(File(context.applicationContext.filesDir, "packs"), StickerImages::trayPng)
                .also { instance = it }
        }

    /** Los tests instrumentados usan un repositorio aparte para no tocar los stickers reales. null restaura el normal. */
    @VisibleForTesting
    fun useForTests(repository: PackRepository?) {
        instance = repository
    }
}
