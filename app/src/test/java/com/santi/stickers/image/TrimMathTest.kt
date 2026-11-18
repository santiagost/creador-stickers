package com.santi.stickers.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 005: caja del tramo (RN-1, RN-2) y cuadros por segundo (RN-3, D4). */
class TrimMathTest {

    private fun r(start: Long, length: Long) = ClipRange(start, length)

    // --- Caja inicial (D1, RN-2, AC-2, AC-4) ---

    @Test
    fun `AC-2 con un video largo la caja arranca en 0 a 5 s`() {
        assertEquals(r(0, 5000), TrimMath.initial(30_000))
    }

    @Test
    fun `AC-4 con un video de entre 2 y 5 s la caja lo cubre entero`() {
        assertEquals(r(0, 3000), TrimMath.initial(3000))
        assertEquals(r(0, 2000), TrimMath.initial(2000))
    }

    @Test
    fun `AC-4 con un video de menos de 2 s se usa entero`() {
        assertEquals(r(0, 1000), TrimMath.initial(1000))
    }

    // --- Mover (RN-1, AC-2) ---

    @Test
    fun `AC-2 mover no cambia el largo`() {
        assertEquals(r(4000, 5000), TrimMath.move(r(0, 5000), 4000, 30_000))
        assertEquals(r(1500, 5000), TrimMath.move(r(4000, 5000), -2500, 30_000))
    }

    @Test
    fun `AC-2 la caja no sale del principio ni del final`() {
        assertEquals(r(0, 5000), TrimMath.move(r(1000, 5000), -3000, 30_000))
        assertEquals(r(25_000, 5000), TrimMath.move(r(20_000, 5000), 9000, 30_000))
    }

    @Test
    fun `si la caja cubre todo el video no se mueve`() {
        assertEquals(r(0, 3000), TrimMath.move(r(0, 3000), 500, 3000))
    }

    // --- Achicar y agrandar (RN-1, AC-3) ---

    @Test
    fun `AC-3 tirar del borde derecho cambia el largo entre 2 y 10 s`() {
        assertEquals(r(0, 7000), TrimMath.resizeEnd(r(0, 5000), 2000, 30_000))
        assertEquals(r(0, 10_000), TrimMath.resizeEnd(r(0, 5000), 9000, 30_000))
        assertEquals(r(0, 2000), TrimMath.resizeEnd(r(0, 5000), -4500, 30_000))
    }

    @Test
    fun `AC-3 el borde derecho no pasa del final del video`() {
        assertEquals(r(20_000, 4000), TrimMath.resizeEnd(r(20_000, 3000), 5000, 24_000))
    }

    @Test
    fun `AC-3 tirar del borde izquierdo mueve el inicio y deja fijo el final`() {
        assertEquals(r(8000, 7000), TrimMath.resizeStart(r(10_000, 5000), -2000, 30_000))
        assertEquals(r(5000, 10_000), TrimMath.resizeStart(r(10_000, 5000), -9000, 30_000))
        assertEquals(r(13_000, 2000), TrimMath.resizeStart(r(10_000, 5000), 4000, 30_000))
    }

    @Test
    fun `AC-3 el borde izquierdo no pasa del principio del video`() {
        assertEquals(r(0, 6000), TrimMath.resizeStart(r(1000, 5000), -3000, 30_000))
    }

    @Test
    fun `un video de menos de 2 s no se puede achicar ni agrandar`() {
        assertEquals(r(0, 1500), TrimMath.resizeEnd(r(0, 1500), -500, 1500))
        assertEquals(r(0, 1500), TrimMath.resizeStart(r(0, 1500), 500, 1500))
    }

    // --- Cuadros por segundo (RN-3, D4) ---

    @Test
    fun `RN-3 hasta 5 s van a 15 fps`() {
        assertEquals(15, TrimMath.fpsFor(2000))
        assertEquals(15, TrimMath.fpsFor(5000))
    }

    @Test
    fun `RN-3 los tramos largos bajan los fps y nunca pasan de 75 cuadros`() {
        assertEquals(7, TrimMath.fpsFor(10_000))
        assertEquals(12, TrimMath.fpsFor(6000))
        for (ms in 2000L..10_000L step 100) {
            val frames = TrimMath.frameCount(ms)
            assertTrue("$ms ms → $frames cuadros", frames in 2..TrimMath.MAX_FRAMES)
        }
    }

    @Test
    fun `cantidad de cuadros del tramo`() {
        assertEquals(75, TrimMath.frameCount(5000))
        assertEquals(70, TrimMath.frameCount(10_000))
        assertEquals(15, TrimMath.frameCount(1000))
        assertEquals(1, TrimMath.frameCount(100)) // menos de 2: el ViewModel lo rechaza (RN-2)
    }

    // --- Línea dentro de la caja (spec 009) ---

    @Test
    fun `009 RN-2 la linea esta en inicio mas lugar por largo`() {
        assertEquals(10_000L, TrimMath.cursorMs(r(10_000, 4000), 0f)) // D3: al principio
        assertEquals(12_000L, TrimMath.cursorMs(r(10_000, 4000), 0.5f))
        assertEquals(14_000L, TrimMath.cursorMs(r(10_000, 4000), 1f))
    }

    @Test
    fun `009 RN-1 arrastrar la linea la mueve y no sale de la caja`() {
        assertEquals(0.25f, TrimMath.dragCursor(r(10_000, 4000), 0f, 1000), 1e-6f)
        assertEquals(0.75f, TrimMath.dragCursor(r(10_000, 4000), 1f, -1000), 1e-6f)
        assertEquals(1f, TrimMath.dragCursor(r(10_000, 4000), 0.5f, 60_000), 0f)
        assertEquals(0f, TrimMath.dragCursor(r(10_000, 4000), 0.5f, -60_000), 0f)
    }

    @Test
    fun `009 RN-3 mover la caja o cambiar su largo mantiene el lugar relativo`() {
        val cursor = 0.5f
        val moved = TrimMath.move(r(10_000, 4000), 3000, 30_000)
        assertEquals(15_000L, TrimMath.cursorMs(moved, cursor))
        val longer = TrimMath.resizeEnd(r(10_000, 4000), 4000, 30_000)
        assertEquals(14_000L, TrimMath.cursorMs(longer, cursor))
    }

    @Test
    fun `009 RN-1 un lugar fuera de rango se acota`() {
        assertEquals(14_000L, TrimMath.cursorMs(r(10_000, 4000), 1.5f))
        assertEquals(10_000L, TrimMath.cursorMs(r(10_000, 4000), -1f))
    }
}
