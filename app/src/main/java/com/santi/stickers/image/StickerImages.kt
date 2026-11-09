package com.santi.stickers.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Genera las imágenes que pide WhatsApp: sticker WebP 512x512 ≤ 100 KB (RN-1) con transparencia
 * fuera de la foto (RN-2), e ícono PNG 96x96 ≤ 50 KB (RN-11). Son operaciones pesadas:
 * llamarlas fuera del hilo principal.
 */
object StickerImages {
    const val DECODE_MAX_SIDE = 2048
    const val MAX_STICKER_BYTES = 100 * 1024
    const val MAX_TRAY_BYTES = 50 * 1024
    const val TRAY_SIZE = 96

    /** Tope de reducciones finales: el lienzo intermedio queda en 2048x2048 como máximo (16 MB). */
    private const val MAX_HALVINGS = 2

    /**
     * Decodifica la foto en memoria normal (no HARDWARE, para poder dibujarla) y con el lado largo
     * en [maxSide] como máximo, para no cargar 12 MP enteros.
     */
    fun decode(source: ImageDecoder.Source, maxSide: Int = DECODE_MAX_SIDE): Bitmap =
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val w = info.size.width
            val h = info.size.height
            val longSide = max(w, h)
            if (longSide > maxSide) {
                val f = maxSide.toFloat() / longSide
                decoder.setTargetSize(max(1, (w * f).roundToInt()), max(1, (h * f).roundToInt()))
            }
        }

    /**
     * Dibuja [photo] con el encuadre [crop] (ya en coordenadas de salida, ver [CropMath.toOutput])
     * sobre un lienzo transparente de [size]x[size]. Si hay que achicar mucho, dibuja más grande
     * y después reduce a la mitad en pasos, para que no aparezcan dientes.
     */
    fun render(photo: Bitmap, crop: CropTransform, size: Int = CropMath.STICKER_SIZE): Bitmap {
        val steps = min(CropMath.halvingSteps(crop.scale), MAX_HALVINGS)
        val factor = 1 shl steps
        var canvasBitmap = Bitmap.createBitmap(size * factor, size * factor, Bitmap.Config.ARGB_8888)
        val matrix = Matrix().apply {
            setScale(crop.scale * factor, crop.scale * factor)
            postTranslate(crop.offsetX * factor, crop.offsetY * factor)
        }
        Canvas(canvasBitmap).drawBitmap(photo, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        repeat(steps) {
            val half = Bitmap.createScaledBitmap(canvasBitmap, canvasBitmap.width / 2, canvasBitmap.height / 2, true)
            canvasBitmap.recycle()
            canvasBitmap = half
        }
        return canvasBitmap
    }

    /** RN-1: WebP con pérdida (conserva el canal alfa), bajando la calidad hasta entrar en [maxBytes]. */
    fun encodeWebp(bitmap: Bitmap, maxBytes: Int = MAX_STICKER_BYTES): ByteArray {
        for (quality in 90 downTo 10 step 10) {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, quality, out)
            if (out.size() <= maxBytes) return out.toByteArray()
        }
        error("No se pudo bajar el sticker a $maxBytes bytes")
    }

    /**
     * Caja de los píxeles no transparentes (derecha e inferior exclusivas), o null si todo es transparente.
     * Spec 002: detecta "sin sujeto" (RN-3) y da la caja para el encuadre automático (RN-5).
     */
    fun alphaBounds(bitmap: Bitmap): CropMath.Box? {
        val w = bitmap.width
        val row = IntArray(w)
        var minX = w
        var minY = -1
        var maxX = -1
        var maxY = -1
        for (y in 0 until bitmap.height) {
            bitmap.getPixels(row, 0, w, 0, y, w, 1)
            for (x in 0 until w) {
                if (row[x] ushr 24 != 0) {
                    if (minY < 0) minY = y
                    maxY = y
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                }
            }
        }
        if (maxX < 0) return null
        return CropMath.Box(minX.toFloat(), minY.toFloat(), (maxX + 1).toFloat(), (maxY + 1).toFloat())
    }

    /** RN-11: ícono de bandeja a partir del archivo de un sticker. Firma compatible con PackRepository. */
    fun trayPng(stickerFile: File): ByteArray {
        val bmp = ImageDecoder.decodeBitmap(ImageDecoder.createSource(stickerFile)) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetSize(TRAY_SIZE, TRAY_SIZE)
        }
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        check(out.size() <= MAX_TRAY_BYTES) { "El ícono pesa ${out.size()} bytes" }
        return out.toByteArray()
    }
}
