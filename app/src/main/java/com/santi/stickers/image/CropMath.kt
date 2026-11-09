package com.santi.stickers.image

import kotlin.math.max
import kotlin.math.min

/**
 * Cómo se ubica la foto: un píxel (x, y) de la foto cae en (x * scale + offsetX, y * scale + offsetY).
 * Las coordenadas son las del cuadro de encuadre en pantalla, o las de la salida de 512 después de [CropMath.toOutput].
 */
data class CropTransform(val scale: Float, val offsetX: Float, val offsetY: Float)

/** Cálculos del encuadre, sin Android, para probarlos en la PC. */
object CropMath {
    const val STICKER_SIZE = 512

    /** AC-1: la foto cubre todo el cuadro (el lado corto ocupa el cuadro) y queda centrada. */
    fun cover(imageWidth: Int, imageHeight: Int, frameSize: Float): CropTransform {
        val scale = frameSize / min(imageWidth, imageHeight)
        return CropTransform(
            scale = scale,
            offsetX = (frameSize - imageWidth * scale) / 2f,
            offsetY = (frameSize - imageHeight * scale) / 2f,
        )
    }

    /** AC-2: pasa un encuadre en un cuadro de [frameSize] a la salida de [outputSize] (misma proporción). */
    fun toOutput(t: CropTransform, frameSize: Float, outputSize: Int = STICKER_SIZE): CropTransform {
        val k = outputSize / frameSize
        return CropTransform(t.scale * k, t.offsetX * k, t.offsetY * k)
    }

    data class ZoomLimits(val min: Float, val max: Float)

    /**
     * Mínimo: la foto entera ocupa la mitad del cuadro (deja margen transparente, D4).
     * Máximo: 8 veces el encuadre inicial.
     */
    fun zoomLimits(imageWidth: Int, imageHeight: Int, frameSize: Float) = ZoomLimits(
        min = 0.5f * frameSize / max(imageWidth, imageHeight),
        max = 8f * frameSize / min(imageWidth, imageHeight),
    )

    /**
     * Aplica un gesto de pellizco/arrastre. [centroidX]/[centroidY] es el punto entre los dedos, en coordenadas
     * del cuadro: el punto de la foto que está ahí no se mueve al hacer zoom. El centro de la foto no puede
     * salir del cuadro, para no perderla de vista.
     */
    fun applyGesture(
        t: CropTransform,
        imageWidth: Int,
        imageHeight: Int,
        frameSize: Float,
        centroidX: Float,
        centroidY: Float,
        panX: Float,
        panY: Float,
        zoom: Float,
    ): CropTransform {
        val limits = zoomLimits(imageWidth, imageHeight, frameSize)
        val scale = (t.scale * zoom).coerceIn(limits.min, limits.max)
        val k = scale / t.scale
        val halfW = imageWidth * scale / 2f
        val halfH = imageHeight * scale / 2f
        val offsetX = centroidX - (centroidX - t.offsetX) * k + panX
        val offsetY = centroidY - (centroidY - t.offsetY) * k + panY
        return CropTransform(
            scale = scale,
            offsetX = (offsetX + halfW).coerceIn(0f, frameSize) - halfW,
            offsetY = (offsetY + halfH).coerceIn(0f, frameSize) - halfH,
        )
    }

    /**
     * Cuántas veces conviene reducir a la mitad al final para que ningún paso achique más de 2x
     * (un filtro bilineal que achica mucho de golpe deja dientes y moiré).
     */
    fun halvingSteps(scale: Float): Int {
        var steps = 0
        var s = scale
        while (s < 0.5f) {
            s *= 2f
            steps++
        }
        return steps
    }
}
