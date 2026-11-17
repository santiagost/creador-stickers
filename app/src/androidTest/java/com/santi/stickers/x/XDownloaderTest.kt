package com.santi.stickers.x

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.santi.stickers.image.VideoFrames
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Spec 007, P2. **Necesita internet** y depende de X: habla con X de verdad (D1), así que puede fallar
 * si X cambia la dirección de los tweets incrustados o se borran los tweets.
 * - 2094024294988701975: NASA, despegue del telescopio Roman (video de 33 s).
 * - 2122576566987489513: un tweet con "GIF" que pasó Santi.
 */
@RunWith(AndroidJUnit4::class)
class XDownloaderTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun video_seBajaA720pYSeLeeComoVideo() = runBlocking {
        var last = 0f
        val r = XDownloader.download(context, "2094024294988701975") { last = it }
        assertTrue("se esperaba Ok y vino $r", r is XDownload.Ok)
        r as XDownload.Ok
        try {
            assertFalse(r.isGif)
            assertEquals(1f, last, 0.001f)
            val info = VideoFrames.info(context, Uri.fromFile(r.file))
            assertEquals(1280, info.width)
            assertEquals(720, info.height)
            assertTrue("duración ${info.durationMs}", info.durationMs in 30_000..36_000)
        } finally {
            r.file.delete()
        }
    }

    @Test
    fun gif_seBajaComoVideo() = runBlocking {
        val r = XDownloader.download(context, "2122576566987489513")
        assertTrue("se esperaba Ok y vino $r", r is XDownload.Ok)
        r as XDownload.Ok
        try {
            assertTrue(r.isGif)
            // Sin el arreglo de Mp4Fix, Android no ve ninguna pista en este archivo (verificado en el S20 FE).
            val info = VideoFrames.info(context, Uri.fromFile(r.file))
            assertEquals(200, info.width)
            assertTrue("duración ${info.durationMs}", info.durationMs in 3000..3200)
        } finally {
            r.file.delete()
        }
    }

    @Test
    fun ac8_masGrandeQueElLimite_noSeBaja() = runBlocking {
        val before = cached()
        assertEquals(XDownload.TooBig, XDownloader.download(context, "2094024294988701975", maxBytes = 100_000))
        assertEquals("no queda nada en la caché", before, cached())
    }

    @Test
    fun ac6_tweetQueNoExiste() = runBlocking {
        assertEquals(XDownload.Unreadable, XDownloader.download(context, "1"))
    }

    @Test
    fun ac5_tweetSinVideo() = runBlocking {
        assertEquals(XDownload.NoVideo, XDownloader.download(context, "20"))
    }

    @Test
    fun d6_limpiarLaCache() {
        File(context.cacheDir, "x-123-1.mp4").writeText("viejo")
        File(context.cacheDir, "otro.mp4").writeText("no es de X")
        XDownloader.clearCache(context)
        assertFalse(File(context.cacheDir, "x-123-1.mp4").exists())
        assertTrue(File(context.cacheDir, "otro.mp4").exists())
        File(context.cacheDir, "otro.mp4").delete()
    }

    private fun cached() = context.cacheDir.list()!!.filter { it.startsWith("x-") }.toSet()
}
