package com.santi.stickers.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aureusapps.android.webpandroid.encoder.WebPAnimEncoder
import com.aureusapps.android.webpandroid.encoder.WebPAnimEncoderOptions
import com.aureusapps.android.webpandroid.encoder.WebPConfig
import com.aureusapps.android.webpandroid.encoder.WebPMuxAnimParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Spec 003: D9 y AC-11, el ícono de un pack animado sale de su primer cuadro. */
@RunWith(AndroidJUnit4::class)
class AnimatedTrayTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun solid(color: Int) = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    @Test
    fun iconoDeUnWebpAnimado_esElPrimerCuadroEnPng96() {
        val file = File(context.cacheDir, "anim-tray-test.webp").apply { delete() }
        WebPAnimEncoder(context, 512, 512, WebPAnimEncoderOptions(animParams = WebPMuxAnimParams(loopCount = 0)))
            .apply {
                configure(WebPConfig(lossless = WebPConfig.COMPRESSION_LOSSY, quality = 80f))
                addFrame(0, solid(Color.RED))
                addFrame(100, solid(Color.BLUE))
                assemble(200, Uri.fromFile(file))
                release()
            }

        val png = StickerImages.trayPng(file)

        assertTrue("pesa ${png.size}", png.size <= StickerImages.MAX_TRAY_BYTES)
        val icon = BitmapFactory.decodeByteArray(png, 0, png.size)
        assertEquals(96, icon.width)
        assertEquals(96, icon.height)
        val center = icon.getPixel(48, 48)
        assertTrue("el ícono tiene que ser el primer cuadro (rojo), vino ${Integer.toHexString(center)}", Color.red(center) > 200 && Color.blue(center) < 60)
    }
}
