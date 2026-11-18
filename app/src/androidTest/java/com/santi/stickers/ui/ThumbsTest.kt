package com.santi.stickers.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.santi.stickers.image.AnimatedResult
import com.santi.stickers.image.AnimatedWebp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Spec 009, D6, RN-6: los WebP animados que arma la app se decodifican animados; los quietos, quietos. */
@RunWith(AndroidJUnit4::class)
class ThumbsTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun solid(color: Int) = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    @Test
    fun webpAnimadoDeLaApp_seDecodificaAnimado() {
        val frames = List(10) { solid(if (it % 2 == 0) Color.RED else Color.BLUE) }
        val result = AnimatedWebp.encode(context, frames) as AnimatedResult.Ok
        val file = File(context.cacheDir, "thumb-anim-test.webp").apply { writeBytes(result.bytes) }

        val thumb = Thumbs.decode(file)

        assertTrue("vino $thumb", thumb is Thumb.Animated)
        val drawable = (thumb as Thumb.Animated).drawable
        assertEquals(Thumbs.SIZE_PX, drawable.intrinsicWidth)
        file.delete()
    }

    @Test
    fun webpQuieto_seDecodificaQuieto() {
        val file = File(context.cacheDir, "thumb-still-test.webp").apply {
            outputStream().use { solid(Color.GREEN).compress(Bitmap.CompressFormat.WEBP_LOSSY, 80, it) }
        }

        val thumb = Thumbs.decode(file)

        assertTrue("vino $thumb", thumb is Thumb.Still)
        assertEquals(Thumbs.SIZE_PX, (thumb as Thumb.Still).bitmap.width)
        file.delete()
    }

    @Test
    fun archivoRoto_daNull() {
        val file = File(context.cacheDir, "thumb-broken-test.webp").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        assertNull(Thumbs.decode(file))
        file.delete()
    }
}
