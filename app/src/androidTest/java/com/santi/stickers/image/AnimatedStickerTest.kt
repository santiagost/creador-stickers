package com.santi.stickers.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs
import kotlin.random.Random

/**
 * Spec 003: AC-3, AC-6 y AC-7, con el video de prueba de Santi (D13) y cuadros sintéticos.
 * El video es H.264 1280x720, 60 fps, 4,2 s.
 */
@RunWith(AndroidJUnit4::class)
class AnimatedStickerTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    // --- VideoFrames ---

    @Test
    fun info_delVideoDePrueba() {
        val info = VideoFrames.info(context, videoUri)
        assertTrue("duración ${info.durationMs}", abs(info.durationMs - 4155) <= 60)
        assertEquals(1280, info.width)
        assertEquals(720, info.height)
        assertEquals(1024, info.workWidth)
        assertEquals(576, info.workHeight)
        assertEquals(info.durationMs - VideoFrames.CLIP_MS, VideoFrames.maxStart(info))
    }

    @Test
    fun cuadroDelTramo_tieneElTamanoDeTrabajoYFormatoArgb() {
        val info = VideoFrames.info(context, videoUri)
        val frame = VideoFrames.frameAt(context, videoUri, info, timeMs = 1500, exact = true)
        assertEquals(1024, frame.width)
        assertEquals(576, frame.height)
        assertEquals(Bitmap.Config.ARGB_8888, frame.config)
    }

    @Test
    fun tramoDe3s_da45CuadrosDelMismoTamano() {
        val info = VideoFrames.info(context, videoUri)
        val sizes = mutableSetOf<String>()
        var count = 0
        val t0 = SystemClock.elapsedRealtime()
        VideoFrames.frames(context, videoUri, info, startMs = 0) { _, f ->
            sizes += "${f.width}x${f.height}/${f.config}"
            count++
        }
        Log.i(TAG, "45 cuadros en ${SystemClock.elapsedRealtime() - t0} ms")
        assertEquals(45, count)
        assertEquals(setOf("1024x576/ARGB_8888"), sizes)
    }

    @Test
    fun ac3_siQuedaMenosDe3s_seUsaHastaElFinal() {
        val info = VideoFrames.info(context, videoUri)
        var count = 0
        VideoFrames.frames(context, videoUri, info, startMs = 3000) { _, _ -> count++ }
        val expected = ((info.durationMs - 3000) * VideoFrames.FPS / 1000).toInt()
        assertEquals(expected, count)
    }

    // --- AnimatedWebp ---

    @Test
    fun ac6_videoDePrueba_daUnWebpAnimadoValido() {
        val info = VideoFrames.info(context, videoUri)
        val frame = info.workWidth.toFloat()
        val crop = CropMath.cover(info.workWidth, info.workHeight, frame)
        val first = StickerImages.render(VideoFrames.frameAt(context, videoUri, info, 500, exact = true), CropMath.toOutput(crop, frame))
        val t0 = SystemClock.elapsedRealtime()

        val result = AnimatedWebp.fromVideo(context, videoUri, info, startMs = 500, crop = crop, frameSize = frame)

        Log.i(TAG, "video → sticker: $result en ${SystemClock.elapsedRealtime() - t0} ms")
        assertTrue("se esperaba Ok y vino $result", result is AnimatedResult.Ok)
        val bytes = (result as AnimatedResult.Ok).bytes
        assertValidAnimated(bytes)
        // El primer cuadro del sticker es el primer cuadro del tramo encuadrado.
        val decodedFirst = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertSimilar(first, decodedFirst)
    }

    @Test
    fun ac6_cuadrosSinteticos_entranAlPrimerIntento() {
        val frames = (0 until 45).map { moving(it) }
        val result = AnimatedWebp.encode(context, frames)
        assertTrue("$result", result is AnimatedResult.Ok)
        result as AnimatedResult.Ok
        assertEquals(15, result.fps)
        val info = assertValidAnimated(result.bytes)
        assertEquals(45, info.frameDurations.size)
        assertEquals(listOf(66), info.frameDurations.distinct())
    }

    @Test
    fun rn4_siElPrimerIntentoNoEntra_elSiguienteSeMideDeNuevo() {
        // Bug de la prueba de P3: todos los intentos medían el archivo del primero.
        val frames = (0 until 45).map { moving(it) }
        val first = AnimatedWebp.encode(context, frames, maxBytes = Int.MAX_VALUE, ladder = listOf(15 to 70f)) as AnimatedResult.Ok

        val result = AnimatedWebp.encode(context, frames, maxBytes = first.bytes.size - 1, ladder = listOf(15 to 70f, 8 to 25f))

        assertTrue("se esperaba Ok con el segundo escalón y vino $result", result is AnimatedResult.Ok)
        result as AnimatedResult.Ok
        assertEquals(8, result.fps)
        assertTrue(result.bytes.size < first.bytes.size)
        assertEquals(24, WebpAnimInfo.parse(result.bytes).frameDurations.size)
    }

    @Test
    fun ac7_siNoEntraEn500KB_devuelveTooBig() {
        val frames = (0 until 45).map { noise(it) }
        assertEquals(AnimatedResult.TooBig, AnimatedWebp.encode(context, frames))
    }

    // --- helpers ---

    private fun assertValidAnimated(bytes: ByteArray): WebpAnimInfo {
        assertTrue("pesa ${bytes.size}", bytes.size <= AnimatedWebp.MAX_BYTES)
        val info = WebpAnimInfo.parse(bytes)
        assertEquals(512, info.canvasWidth)
        assertEquals(512, info.canvasHeight)
        assertEquals("repetición infinita", 0, info.loopCount)
        assertTrue("cuadros ${info.frameDurations.size}", info.frameDurations.size >= 2)
        assertTrue("cada cuadro ≥ 8 ms", info.frameDurations.all { it >= 8 })
        assertEquals("todos los cuadros duran lo mismo", 1, info.frameDurations.distinct().size)
        assertTrue("total ${info.totalMs} ms", info.totalMs <= 10_000)
        return info
    }

    private fun assertSimilar(a: Bitmap, b: Bitmap) {
        var diff = 0L
        var n = 0
        for (y in 16 until 512 step 32) for (x in 16 until 512 step 32) {
            val p = a.getPixel(x, y)
            val q = b.getPixel(x, y)
            diff += abs(Color.red(p) - Color.red(q)) + abs(Color.green(p) - Color.green(q)) + abs(Color.blue(p) - Color.blue(q))
            n++
        }
        val avg = diff / (n * 3f)
        assertTrue("diferencia media por canal $avg", avg < 25)
    }

    /** Cuadro fácil de comprimir: un círculo que se mueve sobre un degradé. */
    private fun moving(i: Int): Bitmap {
        val b = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(Color.rgb(30, 60 + i, 120))
        c.drawCircle(60f + i * 9, 256f, 60f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.YELLOW })
        return b
    }

    /** Peor caso: ruido distinto en cada cuadro, no entra en 500 KB con ninguna combinación. */
    private fun noise(i: Int): Bitmap {
        val rnd = Random(i)
        val b = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val row = IntArray(512)
        for (y in 0 until 512) {
            for (x in 0 until 512) row[x] = 0xFF000000.toInt() or rnd.nextInt(0x1000000)
            b.setPixels(row, 0, 512, 0, y, 512, 1)
        }
        return b
    }

    companion object {
        private const val TAG = "AnimatedStickerTest"
        private lateinit var videoUri: Uri

        @JvmStatic
        @BeforeClass
        fun copyVideo() {
            val inst = InstrumentationRegistry.getInstrumentation()
            val file = File(inst.targetContext.cacheDir, "prueba.mp4")
            inst.context.assets.open("video/prueba.mp4").use { input -> file.outputStream().use { input.copyTo(it) } }
            videoUri = Uri.fromFile(file)
        }
    }
}
