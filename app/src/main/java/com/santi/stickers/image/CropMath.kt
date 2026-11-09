package com.santi.stickers.image

import kotlin.math.abs
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
        val offsetX = centroidX - (centroidX - t.offsetX) * k + panX
        val offsetY = centroidY - (centroidY - t.offsetY) * k + panY
        return keepCenterInFrame(CropTransform(scale, offsetX, offsetY), imageWidth, imageHeight, frameSize)
    }

    /** Regla común a gestos y encuadre automático: el centro de la foto no sale del cuadro. */
    private fun keepCenterInFrame(t: CropTransform, imageWidth: Int, imageHeight: Int, frameSize: Float): CropTransform {
        val halfW = imageWidth * t.scale / 2f
        val halfH = imageHeight * t.scale / 2f
        return t.copy(
            offsetX = (t.offsetX + halfW).coerceIn(0f, frameSize) - halfW,
            offsetY = (t.offsetY + halfH).coerceIn(0f, frameSize) - halfH,
        )
    }

    /** Caja en píxeles de la foto (por ejemplo, la del sujeto que devuelve quitar fondo). */
    data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width get() = right - left
        val height get() = bottom - top
    }

    /** Spec 002, RN-5: la caja entra entera en el cuadro, centrada, con [margin] de margen por lado. */
    fun fitBox(box: Box, imageWidth: Int, imageHeight: Int, frameSize: Float, margin: Float = 0.05f): CropTransform {
        val limits = zoomLimits(imageWidth, imageHeight, frameSize)
        val scale = (frameSize * (1 - 2 * margin) / max(box.width, box.height)).coerceIn(limits.min, limits.max)
        val t = CropTransform(
            scale = scale,
            offsetX = frameSize / 2 - (box.left + box.right) / 2 * scale,
            offsetY = frameSize / 2 - (box.top + box.bottom) / 2 * scale,
        )
        return keepCenterInFrame(t, imageWidth, imageHeight, frameSize)
    }

    /**
     * Spec 002, RN-8 a RN-11: devuelve lo que se ve a partir de la posición "real" del dedo ([raw]).
     * - Escala: a menos de [scaleTolerance] de ancho justo o alto justo, se pega (sin mover el centro de la foto).
     * - Posición, por eje: a menos de [snapDistance] (en píxeles del cuadro) se pega a un borde alineado si la foto
     *   llena el cuadro en ese eje, o al centro si es más chica.
     * Como [raw] se sigue acumulando aparte, la foto se despega recién al pasar el umbral (imán "duro").
     */
    fun magnet(
        raw: CropTransform,
        imageWidth: Int,
        imageHeight: Int,
        frameSize: Float,
        snapDistance: Float,
        scaleTolerance: Float = 0.06f,
    ): CropTransform {
        val fits = listOf(frameSize / imageWidth, frameSize / imageHeight)
        val target = fits.minBy { abs(raw.scale / it - 1f) }
        val scale = if (abs(raw.scale / target - 1f) <= scaleTolerance) target else raw.scale

        // Cambiar la escala sin mover el centro de la foto.
        val centerX = raw.offsetX + imageWidth * raw.scale / 2f
        val centerY = raw.offsetY + imageHeight * raw.scale / 2f
        val offsetX = centerX - imageWidth * scale / 2f
        val offsetY = centerY - imageHeight * scale / 2f

        return CropTransform(
            scale = scale,
            offsetX = snapAxis(offsetX, imageWidth * scale, frameSize, snapDistance),
            offsetY = snapAxis(offsetY, imageHeight * scale, frameSize, snapDistance),
        )
    }

    private fun snapAxis(offset: Float, size: Float, frameSize: Float, snapDistance: Float): Float {
        // Si la foto llena el cuadro en este eje, se pega a los bordes (que no quede hueco).
        // Si es más chica, solo al centro: pegarse a los bordes de adentro se sentía raro (prueba de Santi).
        val candidates = if (size >= frameSize) listOf(0f, frameSize - size) else listOf((frameSize - size) / 2f)
        val nearest = candidates.minBy { abs(it - offset) }
        return if (abs(nearest - offset) <= snapDistance) nearest else offset
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
