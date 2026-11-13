package com.santi.stickers.image

import org.junit.Assert.assertEquals
import org.junit.Test

/** Spec 005, RN-4: línea de tiempo de un GIF a partir de la duración de cada cuadro. */
class GifTimelineTest {

    @Test
    fun `RN-4 la duracion es la suma de los cuadros`() {
        assertEquals(350, GifTimeline(listOf(100, 200, 50)).durationMs)
    }

    @Test
    fun `RN-4 un cuadro de 0 ms se toma como 100 ms`() {
        val t = GifTimeline(listOf(0, 40, -5))
        assertEquals(listOf(100, 40, 100), t.delaysMs)
        assertEquals(240, t.durationMs)
    }

    @Test
    fun `cuadro que se ve en cada momento`() {
        val t = GifTimeline(listOf(100, 200, 50))
        assertEquals(0, t.indexAt(0))
        assertEquals(0, t.indexAt(99))
        assertEquals(1, t.indexAt(100))
        assertEquals(1, t.indexAt(299))
        assertEquals(2, t.indexAt(300))
        assertEquals(2, t.indexAt(349))
        // Fuera de la línea de tiempo: el primero o el último.
        assertEquals(0, t.indexAt(-10))
        assertEquals(2, t.indexAt(10_000))
    }
}
