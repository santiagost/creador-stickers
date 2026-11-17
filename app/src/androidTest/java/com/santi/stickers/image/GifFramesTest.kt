package com.santi.stickers.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.random.Random

/**
 * Spec 005: AC-7 y AC-8. GIFs de prueba generados con ffmpeg:
 * - prueba.gif: 3 s del video de prueba, 240x135, 10 fps (30 cuadros de 100 ms).
 * - transparente.gif: 200x200, 20 cuadros de 100 ms, círculo que se mueve sobre fondo transparente.
 */
@RunWith(AndroidJUnit4::class)
class GifFramesTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun info_delGifDePrueba() {
        val info = GifFrames.info(context, gifUri)
        assertEquals(240, info.width)
        assertEquals(135, info.height)
        assertEquals(240, info.workWidth)
        assertEquals(30, info.timeline.frameCount)
        assertEquals(3000, info.durationMs)
    }

    @Test
    fun tramoEntero_da45CuadrosArgbDelTamanoDeTrabajo() {
        val info = GifFrames.info(context, gifUri)
        val sizes = mutableSetOf<String>()
        var count = 0
        val t0 = SystemClock.elapsedRealtime()
        GifFrames.frames(context, gifUri, info, ClipRange(0, 3000)) { _, f ->
            sizes += "${f.width}x${f.height}/${f.config}"
            count++
        }
        Log.i(TAG, "GIF: 45 cuadros en ${SystemClock.elapsedRealtime() - t0} ms")
        assertEquals(45, count)
        assertEquals(setOf("240x135/ARGB_8888"), sizes)
    }

    @Test
    fun frameAt_daElCuadroDeEseMomento() {
        val info = GifFrames.info(context, gifUri)
        val atStart = GifFrames.frameAt(context, gifUri, info, 0)
        val atMiddle = GifFrames.frameAt(context, gifUri, info, 1550)
        var expected: Bitmap? = null
        // A 15 fps, el cuadro 23 del tramo cae en 1533 ms → cuadro 15 del GIF, el mismo que en 1550 ms.
        GifFrames.frames(context, gifUri, info, ClipRange(0, 3000)) { i, f -> if (i == 23) expected = f.copy(Bitmap.Config.ARGB_8888, false) }
        assertTrue("el cuadro del medio tiene que ser distinto del primero", !atStart.sameAs(atMiddle))
        assertTrue("frameAt(1550) coincide con el cuadro 15", expected!!.sameAs(atMiddle))
    }

    @Test
    fun ac7_gifDePrueba_daUnWebpAnimadoValido() {
        val info = GifFrames.info(context, gifUri)
        val result = encodeGif(gifUri, info, ClipRange(500, 2000))
        assertTrue("se esperaba Ok y vino $result", result is AnimatedResult.Ok)
        val anim = assertValidAnimated((result as AnimatedResult.Ok).bytes)
        // 30 cuadros a 15 fps, pero el GIF es de 10 fps: el encoder une los cuadros repetidos seguidos
        // (minimizeSize), así que quedan los 20 cuadros reales del GIF con la misma duración total.
        assertEquals(20, anim.frameDurations.size)
        assertEquals(30 * 66, anim.totalMs)
    }

    @Test
    fun ac7_d5_gifTransparente_conservaLaTransparencia() {
        val info = GifFrames.info(context, transparentUri)
        assertEquals(2000, info.durationMs)
        val first = GifFrames.frameAt(context, transparentUri, info, 0)
        assertEquals("esquina transparente en el GIF", 0, Color.alpha(first.getPixel(2, 2)))
        assertEquals("centro opaco en el GIF", 255, Color.alpha(first.getPixel(100, 100)))

        val result = encodeGif(transparentUri, info, ClipRange(0, 2000))
        assertTrue("se esperaba Ok y vino $result", result is AnimatedResult.Ok)
        val bytes = (result as AnimatedResult.Ok).bytes
        assertValidAnimated(bytes)
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertTrue("el sticker tiene alfa", decoded.hasAlpha())
        assertTrue("esquina transparente en el sticker", Color.alpha(decoded.getPixel(5, 5)) < 10)
        assertTrue("centro opaco en el sticker", Color.alpha(decoded.getPixel(256, 256)) > 245)
    }

    @Test
    fun ac8_gifRoto_daErrorSinRomperLaApp() {
        val truncated = copyBytes("roto.gif", File(context.cacheDir, "gif-prueba.gif").readBytes().copyOf(2000))
        val notGif = copyBytes("nogif.gif", Random(1).nextBytes(5000))
        for (uri in listOf(truncated, notGif)) {
            val outcome = runCatching {
                val info = GifFrames.info(context, uri)
                GifFrames.frames(context, uri, info, ClipRange(0, minOf(2000, info.durationMs))) { _, _ -> }
            }
            Log.i(TAG, "GIF roto $uri → $outcome")
            assertTrue("se esperaba un error con $uri y vino $outcome", outcome.isFailure)
        }
    }

    @Test
    fun ac8_d6_gifDeMasDe20MB_seRechaza() {
        val big = File(context.cacheDir, "grande.gif")
        big.outputStream().use { out ->
            out.write("GIF89a".toByteArray())
            val chunk = ByteArray(1024 * 1024)
            repeat(20) { out.write(chunk) }
        }
        try {
            GifFrames.info(context, Uri.fromFile(big))
            fail("se esperaba GifTooLargeException")
        } catch (_: GifTooLargeException) {
        } finally {
            big.delete()
        }
    }

    // --- helpers ---

    private fun encodeGif(uri: Uri, info: GifInfo, range: ClipRange): AnimatedResult {
        val frame = info.workWidth.toFloat()
        val out = CropMath.toOutput(CropMath.cover(info.workWidth, info.workHeight, frame), frame)
        val rendered = mutableListOf<Bitmap>()
        GifFrames.frames(context, uri, info, range) { _, f -> rendered += StickerImages.render(f, out) }
        return AnimatedWebp.encode(context, rendered, sourceFps = TrimMath.fpsFor(range.lengthMs)).also { r ->
            Log.i(TAG, "GIF → sticker: $r")
            rendered.forEach { it.recycle() }
        }
    }

    private fun assertValidAnimated(bytes: ByteArray): WebpAnimInfo {
        assertTrue("pesa ${bytes.size}", bytes.size <= AnimatedWebp.MAX_BYTES)
        val info = WebpAnimInfo.parse(bytes)
        assertEquals(512, info.canvasWidth)
        assertEquals(512, info.canvasHeight)
        assertEquals("repetición infinita", 0, info.loopCount)
        assertTrue("cuadros ${info.frameDurations.size}", info.frameDurations.size >= 2)
        assertTrue("cada cuadro ≥ 8 ms", info.frameDurations.all { it >= 8 })
        assertTrue("total ${info.totalMs} ms", info.totalMs <= 10_000)
        return info
    }

    private fun copyBytes(name: String, bytes: ByteArray): Uri {
        val file = File(context.cacheDir, name)
        file.writeBytes(bytes)
        return Uri.fromFile(file)
    }

    companion object {
        private const val TAG = "GifFramesTest"
        private lateinit var gifUri: Uri
        private lateinit var transparentUri: Uri

        private fun copyAsset(name: String, target: String): Uri {
            val inst = InstrumentationRegistry.getInstrumentation()
            val file = File(inst.targetContext.cacheDir, target)
            inst.context.assets.open("gif/$name").use { input -> file.outputStream().use { input.copyTo(it) } }
            return Uri.fromFile(file)
        }

        @JvmStatic
        @BeforeClass
        fun copyGifs() {
            gifUri = copyAsset("prueba.gif", "gif-prueba.gif")
            transparentUri = copyAsset("transparente.gif", "gif-transparente.gif")
        }
    }
}
