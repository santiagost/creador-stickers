package com.santi.stickers.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec 002: AC-2, AC-4, AC-5 y AC-14 (parte técnica), con ML Kit real en el teléfono y las fotos de Santi (D12).
 * La app no tiene permisos de red (D13): si estos tests pasan, ML Kit funciona sin red.
 */
@RunWith(AndroidJUnit4::class)
class BackgroundRemoverTest {

    private val testAssets = InstrumentationRegistry.getInstrumentation().context.assets

    private fun photo(name: String): Bitmap = StickerImages.decode(ImageDecoder.createSource(testAssets, "fondo/$name"))

    private fun remove(name: String): RemovalResult {
        val p = photo(name)
        val t0 = SystemClock.elapsedRealtime()
        val result = runBlocking { remover.remove(p) }
        Log.i(TAG, "$name ${p.width}x${p.height}: $result en ${SystemClock.elapsedRealtime() - t0} ms")
        return result
    }

    private fun transparentFraction(b: Bitmap): Float {
        val row = IntArray(b.width)
        var transparent = 0L
        for (y in 0 until b.height) {
            b.getPixels(row, 0, b.width, 0, y, b.width, 1)
            row.forEach { if (Color.alpha(it) == 0) transparent++ }
        }
        return transparent.toFloat() / (b.width.toLong() * b.height)
    }

    @Test
    fun ac2_objetoClaro_quitaElFondoYDevuelveLaCajaDelSujeto() {
        val p = photo("Objeto claro.jpg")
        val result = remove("Objeto claro.jpg")

        assertTrue("se esperaba Removed y vino $result", result is RemovalResult.Removed)
        result as RemovalResult.Removed
        assertEquals(p.width, result.subject.width)
        assertEquals(p.height, result.subject.height)
        val transparent = transparentFraction(result.subject)
        assertTrue("transparente $transparent", transparent in 0.05f..0.95f)
        with(result.bounds) {
            assertTrue("caja $this", left >= 0 && top >= 0 && right <= p.width && bottom <= p.height && width > 0 && height > 0)
        }
    }

    @Test
    fun ac4_stickerSinFondo_esWebp512DeHasta100KBConLasEsquinasTransparentes() {
        val result = remove("Objeto claro.jpg") as RemovalResult.Removed
        val frame = 1000f
        val crop = CropMath.fitBox(result.bounds, result.subject.width, result.subject.height, frame)

        val bytes = StickerImages.encodeWebp(StickerImages.render(result.subject, CropMath.toOutput(crop, frame)))

        assertTrue("pesa ${bytes.size}", bytes.size <= StickerImages.MAX_STICKER_BYTES)
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertEquals(512, decoded.width)
        assertEquals(512, decoded.height)
        for ((x, y) in listOf(2 to 2, 509 to 2, 2 to 509, 509 to 509)) {
            assertEquals("alfa en ($x,$y)", 0, Color.alpha(decoded.getPixel(x, y)))
        }
    }

    @Test
    fun ac5_imagenPlana_noEncuentraSujeto() {
        assertEquals(RemovalResult.NoSubject, remove("Imagen plana.jpg"))
    }

    @Test
    fun variosObjetos_devuelveUnRecorte() {
        assertTrue(remove("varios objetos.jpeg") is RemovalResult.Removed)
    }

    @Test
    fun repetirLaMismaFoto_daLaMismaCaja() {
        val a = remove("Objeto claro.jpg") as RemovalResult.Removed
        val b = remove("Objeto claro.jpg") as RemovalResult.Removed
        assertEquals(a.bounds, b.bounds)
    }

    companion object {
        private const val TAG = "BackgroundRemoverTest"
        private val remover by lazy { MlKitBackgroundRemover() }

        @JvmStatic
        @AfterClass
        fun closeRemover() = remover.close()
    }
}
