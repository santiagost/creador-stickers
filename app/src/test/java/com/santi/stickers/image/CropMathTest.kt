package com.santi.stickers.image

import org.junit.Assert.assertEquals
import org.junit.Test

/** Spec 001: AC-1 (encuadre inicial) y AC-2 (lo que está en el cuadro es lo que se guarda), parte de cálculo. */
class CropMathTest {

    private val eps = 1e-3f

    private fun assertTransform(expected: CropTransform, actual: CropTransform) {
        assertEquals("scale", expected.scale, actual.scale, eps)
        assertEquals("offsetX", expected.offsetX, actual.offsetX, eps)
        assertEquals("offsetY", expected.offsetY, actual.offsetY, eps)
    }

    @Test
    fun `AC-1 una foto horizontal cubre el cuadro y queda centrada`() {
        // 4000x3000 en un cuadro de 1000: el lado corto (3000) ocupa el cuadro.
        val t = CropMath.cover(imageWidth = 4000, imageHeight = 3000, frameSize = 1000f)
        assertTransform(CropTransform(scale = 1000f / 3000f, offsetX = -166.6667f, offsetY = 0f), t)
    }

    @Test
    fun `AC-1 una foto vertical cubre el cuadro y queda centrada`() {
        val t = CropMath.cover(imageWidth = 1500, imageHeight = 3000, frameSize = 600f)
        assertTransform(CropTransform(scale = 0.4f, offsetX = 0f, offsetY = -300f), t)
    }

    @Test
    fun `AC-1 una foto cuadrada coincide exactamente con el cuadro`() {
        val t = CropMath.cover(imageWidth = 800, imageHeight = 800, frameSize = 400f)
        assertTransform(CropTransform(scale = 0.5f, offsetX = 0f, offsetY = 0f), t)
    }

    @Test
    fun `AC-2 pasar a la salida de 512 escala posicion y tamano en la misma proporcion`() {
        val inFrame = CropTransform(scale = 0.5f, offsetX = -100f, offsetY = 50f)
        val out = CropMath.toOutput(inFrame, frameSize = 1024f, outputSize = 512)
        assertTransform(CropTransform(scale = 0.25f, offsetX = -50f, offsetY = 25f), out)
    }

    @Test
    fun `AC-2 un punto de la foto cae en el mismo lugar relativo del cuadro y de la salida`() {
        val inFrame = CropTransform(scale = 2f, offsetX = -300f, offsetY = -120f)
        val frame = 900f
        val out = CropMath.toOutput(inFrame, frame, outputSize = 512)
        val (px, py) = 210f to 180f // punto en píxeles de la foto
        val relFrameX = (px * inFrame.scale + inFrame.offsetX) / frame
        val relFrameY = (py * inFrame.scale + inFrame.offsetY) / frame
        val relOutX = (px * out.scale + out.offsetX) / 512f
        val relOutY = (py * out.scale + out.offsetY) / 512f
        assertEquals(relFrameX, relOutX, eps)
        assertEquals(relFrameY, relOutY, eps)
    }

    @Test
    fun `reduccion en pasos de a mitad cuando la escala es menor a un medio`() {
        assertEquals(0, CropMath.halvingSteps(1f))
        assertEquals(0, CropMath.halvingSteps(0.5f))
        assertEquals(1, CropMath.halvingSteps(0.3f))
        assertEquals(2, CropMath.halvingSteps(0.2f))
        assertEquals(3, CropMath.halvingSteps(0.1f))
    }
}
