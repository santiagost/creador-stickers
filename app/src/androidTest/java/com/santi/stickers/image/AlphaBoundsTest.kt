package com.santi.stickers.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Spec 002: caja del sujeto a partir del canal alfa (base de RN-3 y RN-5). */
@RunWith(AndroidJUnit4::class)
class AlphaBoundsTest {

    @Test
    fun cajaDeLosPixelesNoTransparentes() {
        val b = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        Canvas(b).drawRect(30f, 10f, 80f, 60f, Paint().apply { color = Color.RED })
        assertEquals(CropMath.Box(30f, 10f, 80f, 60f), StickerImages.alphaBounds(b))
    }

    @Test
    fun todoTransparenteNoTieneCaja() {
        assertNull(StickerImages.alphaBounds(Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)))
    }
}
