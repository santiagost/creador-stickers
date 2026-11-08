package com.santi.stickers.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.random.Random

/** Spec 001: AC-2, AC-3, AC-4 y el ícono de RN-11/AC-19, en Android real. */
@RunWith(AndroidJUnit4::class)
class StickerImagesTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** Foto "difícil": ruido aleatorio, el peor caso para comprimir. */
    private fun noise(width: Int, height: Int): Bitmap {
        val rnd = Random(42)
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val row = IntArray(width)
        for (y in 0 until height) { // fila por fila para no duplicar la memoria de una foto de 12 MP
            for (x in 0 until width) row[x] = 0xFF000000.toInt() or rnd.nextInt(0x1000000)
            bmp.setPixels(row, 0, width, 0, y, width, 1)
        }
        return bmp
    }

    /** Cuatro cuadrantes: rojo, verde / azul, amarillo. */
    private fun quadrants(size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val h = size / 2f
        val p = Paint()
        p.color = Color.RED; c.drawRect(0f, 0f, h, h, p)
        p.color = Color.GREEN; c.drawRect(h, 0f, size.toFloat(), h, p)
        p.color = Color.BLUE; c.drawRect(0f, h, h, size.toFloat(), p)
        p.color = Color.YELLOW; c.drawRect(h, h, size.toFloat(), size.toFloat(), p)
        return bmp
    }

    private fun writeJpeg(bmp: Bitmap): File =
        File(context.cacheDir, "test-${System.nanoTime()}.jpg").apply {
            outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        }

    private fun decodeBytes(bytes: ByteArray): Bitmap =
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

    private fun isWebp(bytes: ByteArray) =
        String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP"

    private fun assertColorNear(expected: Int, actual: Int) {
        val tol = 40
        assertTrue(
            "esperado ${Integer.toHexString(expected)}, vino ${Integer.toHexString(actual)}",
            Math.abs(Color.red(expected) - Color.red(actual)) <= tol &&
                Math.abs(Color.green(expected) - Color.green(actual)) <= tol &&
                Math.abs(Color.blue(expected) - Color.blue(actual)) <= tol &&
                Color.alpha(actual) >= 250,
        )
    }

    @Test
    fun decode_reduceFotosGrandesAunLadoMaximoYQuedaEnSoftware() {
        val file = writeJpeg(noise(4000, 3000))
        val bmp = StickerImages.decode(ImageDecoder.createSource(file))
        assertEquals(StickerImages.DECODE_MAX_SIDE, maxOf(bmp.width, bmp.height))
        assertEquals(1536, bmp.height)
        assertEquals(Bitmap.Config.ARGB_8888, bmp.config)
    }

    @Test
    fun ac4_fotoDe12MPConMuchoDetalle_daWebp512DeHasta100KB() {
        val photo = StickerImages.decode(ImageDecoder.createSource(writeJpeg(noise(4000, 3000))))
        val crop = CropMath.toOutput(CropMath.cover(photo.width, photo.height, 1000f), 1000f)

        val bytes = StickerImages.encodeWebp(StickerImages.render(photo, crop))

        assertTrue("pesa ${bytes.size} bytes", bytes.size <= StickerImages.MAX_STICKER_BYTES)
        assertTrue(isWebp(bytes))
        val decoded = decodeBytes(bytes)
        assertEquals(512, decoded.width)
        assertEquals(512, decoded.height)
    }

    @Test
    fun ac2_loQueEstaEnElCuadroEsLoQueSeGuarda() {
        val photo = quadrants(1000)
        // Cuadro de 500 px mostrando solo el cuadrante superior izquierdo (rojo) a escala 1.
        val onlyRed = CropMath.toOutput(CropTransform(scale = 1f, offsetX = 0f, offsetY = 0f), frameSize = 500f)
        val red = decodeBytes(StickerImages.encodeWebp(StickerImages.render(photo, onlyRed)))
        for ((x, y) in listOf(10 to 10, 256 to 256, 500 to 500, 10 to 500)) assertColorNear(Color.RED, red.getPixel(x, y))

        // Encuadre que cubre toda la foto: cada cuadrante en su lugar.
        val all = CropMath.toOutput(CropMath.cover(1000, 1000, 800f), 800f)
        val full = decodeBytes(StickerImages.encodeWebp(StickerImages.render(photo, all)))
        assertColorNear(Color.RED, full.getPixel(128, 128))
        assertColorNear(Color.GREEN, full.getPixel(384, 128))
        assertColorNear(Color.BLUE, full.getPixel(128, 384))
        assertColorNear(Color.YELLOW, full.getPixel(384, 384))
    }

    @Test
    fun ac3_alAlejarLasZonasSinFotoQuedanTransparentes() {
        val photo = quadrants(1000)
        // Foto a la mitad del cuadro, centrada: margen de 1/4 a cada lado.
        val zoomedOut = CropMath.toOutput(CropTransform(scale = 0.4f, offsetX = 200f, offsetY = 200f), frameSize = 800f)

        val decoded = decodeBytes(StickerImages.encodeWebp(StickerImages.render(photo, zoomedOut)))

        for ((x, y) in listOf(0 to 0, 511 to 0, 0 to 511, 511 to 511, 60 to 256, 256 to 60)) {
            assertEquals("alfa en ($x,$y)", 0, Color.alpha(decoded.getPixel(x, y)))
        }
        assertColorNear(Color.RED, decoded.getPixel(180, 180))
        assertColorNear(Color.YELLOW, decoded.getPixel(330, 330))
    }

    @Test
    fun trayPng_esPng96x96DeHasta50KB() {
        val photo = StickerImages.decode(ImageDecoder.createSource(writeJpeg(noise(2000, 2000))))
        val crop = CropMath.toOutput(CropMath.cover(photo.width, photo.height, 1000f), 1000f)
        val sticker = File(context.cacheDir, "s1.webp").apply {
            writeBytes(StickerImages.encodeWebp(StickerImages.render(photo, crop)))
        }

        val png = StickerImages.trayPng(sticker)

        assertTrue("pesa ${png.size} bytes", png.size <= StickerImages.MAX_TRAY_BYTES)
        assertEquals(0x89.toByte(), png[0])
        assertEquals("PNG", String(png, 1, 3, Charsets.US_ASCII))
        val decoded = decodeBytes(png)
        assertEquals(96, decoded.width)
        assertEquals(96, decoded.height)
    }
}
