package com.santi.stickers.image

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
