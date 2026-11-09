package com.santi.stickers.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 002: RN-5 (encuadre automático sobre el sujeto) y RN-8 a RN-11 (imán); AC-8 a AC-12, parte de cálculo. */
class MagnetAndFitTest {

    private val eps = 1e-3f
    private val frame = 500f
    private val snap = 24f

    private fun assertTransform(expected: CropTransform, actual: CropTransform) {
        assertEquals("scale", expected.scale, actual.scale, eps)
        assertEquals("offsetX", expected.offsetX, actual.offsetX, eps)
        assertEquals("offsetY", expected.offsetY, actual.offsetY, eps)
    }

    // --- RN-5: encuadre automático ---

    @Test
    fun `RN-5 la caja del sujeto entra entera y centrada con 5 por ciento de margen`() {
        // Foto 1000x800, sujeto en (200,100)-(600,500): 400x400.
        val t = CropMath.fitBox(CropMath.Box(200f, 100f, 600f, 500f), imageWidth = 1000, imageHeight = 800, frameSize = frame)
        assertTransform(CropTransform(scale = 450f / 400f, offsetX = -200f, offsetY = -87.5f), t)
    }

    @Test
    fun `RN-5 un sujeto alargado entra por su lado largo`() {
        val t = CropMath.fitBox(CropMath.Box(0f, 0f, 100f, 900f), imageWidth = 1000, imageHeight = 1000, frameSize = frame)
        assertEquals(450f / 900f, t.scale, eps)
        assertEquals(frame / 2, (0f + 900f) / 2 * t.scale + t.offsetY, eps) // centrada en vertical
    }

    @Test
    fun `RN-5 un sujeto diminuto no supera el zoom maximo`() {
        val t = CropMath.fitBox(CropMath.Box(500f, 500f, 502f, 502f), imageWidth = 1000, imageHeight = 1000, frameSize = frame)
        assertEquals(CropMath.zoomLimits(1000, 1000, frame).max, t.scale, eps)
    }

    @Test
    fun `RN-5 respeta la misma regla que los gestos el centro de la foto queda dentro del cuadro`() {
        // Sujeto en una esquina de una foto grande: centrarlo empujaría el centro de la foto fuera del cuadro.
        val t = CropMath.fitBox(CropMath.Box(0f, 0f, 100f, 100f), imageWidth = 2000, imageHeight = 2000, frameSize = frame)
        val centerX = t.offsetX + 2000 * t.scale / 2
        val centerY = t.offsetY + 2000 * t.scale / 2
        assertTrue(centerX in 0f..frame)
        assertTrue(centerY in 0f..frame)
    }

    // --- RN-8: imán de escala ---

    private val tallW = 1000
    private val tallH = 2000
    private val widthFit = frame / tallW // 0.5

    private fun magnet(raw: CropTransform, w: Int = tallW, h: Int = tallH) = CropMath.magnet(raw, w, h, frame, snap)

    @Test
    fun `AC-10 cerca de ancho justo la escala se pega y el centro de la foto no se mueve`() {
        val raw = CropTransform(scale = widthFit * 1.04f, offsetX = -10f, offsetY = -300f)
        val centerY = raw.offsetY + tallH * raw.scale / 2

        val t = magnet(raw)

        assertEquals(widthFit, t.scale, eps)
        assertEquals(centerY, t.offsetY + tallH * t.scale / 2, eps)
        // En x, al quedar a ancho justo, además se pega al borde (RN-9).
        assertEquals(0f, t.offsetX, eps)
    }

    @Test
    fun `AC-10 lejos de ancho y alto justo la escala queda libre`() {
        val raw = CropTransform(scale = widthFit * 1.10f, offsetX = -50f, offsetY = -300f)
        assertEquals(raw.scale, magnet(raw).scale, eps)
    }

    @Test
    fun `AC-10 tambien se pega a alto justo`() {
        val heightFit = frame / tallH // 0.25: la foto entera entra de alto
        val raw = CropTransform(scale = heightFit * 0.95f, offsetX = 120f, offsetY = 10f)
        assertEquals(heightFit, magnet(raw).scale, eps)
    }

    // --- RN-9 y RN-10: imán de posición ---

    @Test
    fun `AC-8 foto vertical a ancho justo con desvio lateral chico no se corre de costado`() {
        for (dx in listOf(-23f, -10f, 0f, 15f, 23f)) {
            val t = magnet(CropTransform(widthFit, offsetX = dx, offsetY = -300f))
            assertEquals("desvío $dx", 0f, t.offsetX, eps)
            assertEquals("el eje largo queda libre", -300f, t.offsetY, eps)
        }
    }

    @Test
    fun `AC-9 con desvio lateral mayor al umbral se despega`() {
        val t = magnet(CropTransform(widthFit, offsetX = 30f, offsetY = -300f))
        assertEquals(30f, t.offsetX, eps)
    }

    @Test
    fun `AC-11 foto horizontal a alto justo con los ejes invertidos`() {
        val heightFit = frame / 1000f
        val pegada = CropMath.magnet(CropTransform(heightFit, offsetX = -300f, offsetY = 15f), 2000, 1000, frame, snap)
        assertEquals(0f, pegada.offsetY, eps)
        assertEquals(-300f, pegada.offsetX, eps)
        val suelta = CropMath.magnet(CropTransform(heightFit, offsetX = -300f, offsetY = -30f), 2000, 1000, frame, snap)
        assertEquals(-30f, suelta.offsetY, eps)
    }

    @Test
    fun `RN-9 en el eje largo el borde de la foto se pega al borde del cuadro`() {
        // Alto mostrado 1000 en un cuadro de 500: el borde inferior coincide con offsetY = -500.
        val t = magnet(CropTransform(widthFit, offsetX = 0f, offsetY = -490f))
        assertEquals(-500f, t.offsetY, eps)
        val arriba = magnet(CropTransform(widthFit, offsetX = 0f, offsetY = -12f))
        assertEquals(0f, arriba.offsetY, eps)
    }

    @Test
    fun `RN-9 si la foto es mas chica que el cuadro solo se pega al centro, no a los bordes de adentro`() {
        // Escala 0.15 (lejos de ancho y alto justo): 150x300 dentro de 500.
        val centro = magnet(CropTransform(0.15f, offsetX = 180f, offsetY = 110f))
        assertEquals((frame - 150f) / 2, centro.offsetX, eps)
        assertEquals((frame - 300f) / 2, centro.offsetY, eps)
        val cercaDelBorde = magnet(CropTransform(0.15f, offsetX = 10f, offsetY = 190f))
        assertEquals("no se pega al borde izquierdo", 10f, cercaDelBorde.offsetX, eps)
        assertEquals("no se pega al borde inferior", 190f, cercaDelBorde.offsetY, eps)
    }

    @Test
    fun `RN-11 aplicar el iman dos veces da lo mismo`() {
        val raw = CropTransform(widthFit * 1.03f, offsetX = 17f, offsetY = -488f)
        val once = magnet(raw)
        assertTransform(once, magnet(once))
    }
}
