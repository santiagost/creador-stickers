package com.santi.stickers.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.santi.stickers.image.CropMath
import com.santi.stickers.image.CropTransform
import kotlin.math.min

/** Pantalla de encuadre (spec 001, sección 11.2; D4). */
@Composable
fun CropScreen(
    photo: Bitmap?,
    saving: Boolean,
    onCancel: () -> Unit,
    onSave: (photo: Bitmap, crop: CropTransform, frameSize: Float) -> Unit,
) {
    BackHandler(onBack = onCancel) // AC-5

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding(),
    ) {
        Text(
            "Mové y pellizcá la foto para encuadrarla",
            color = Color.White,
            modifier = Modifier.padding(16.dp),
        )
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (photo == null) {
                CircularProgressIndicator()
            } else {
                val density = LocalDensity.current
                val widthPx = with(density) { maxWidth.toPx() }
                val heightPx = with(density) { maxHeight.toPx() }
                val margin = with(density) { 24.dp.toPx() }
                val frame = min(widthPx, heightPx) - 2 * margin
                CropArea(photo, widthPx, heightPx, frame, saving, onCancel, onSave)
            }
        }
    }
}

@Composable
private fun CropArea(
    photo: Bitmap,
    widthPx: Float,
    heightPx: Float,
    frame: Float,
    saving: Boolean,
    onCancel: () -> Unit,
    onSave: (Bitmap, CropTransform, Float) -> Unit,
) {
    val image = remember(photo) { photo.asImageBitmap() }
    val frameLeft = (widthPx - frame) / 2f
    val frameTop = (heightPx - frame) / 2f
    // AC-1: arranca cubriendo el cuadro, centrada.
    var crop by remember(photo, frame) { mutableStateOf(CropMath.cover(photo.width, photo.height, frame)) }
    val checkerSize = with(LocalDensity.current) { 12.dp.toPx() }

    Box(Modifier.fillMaxSize()) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(photo, frame) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        crop = CropMath.applyGesture(
                            crop, photo.width, photo.height, frame,
                            centroidX = centroid.x - frameLeft,
                            centroidY = centroid.y - frameTop,
                            panX = pan.x,
                            panY = pan.y,
                            zoom = zoom,
                        )
                    }
                },
        ) {
            val frameOrigin = Offset(frameLeft, frameTop)
            val frameSize = Size(frame, frame)
            // Fondo a cuadros dentro del cuadro: así se ve qué va a quedar transparente.
            clipRect(frameLeft, frameTop, frameLeft + frame, frameTop + frame) {
                val cells = (frame / checkerSize).toInt() + 1
                for (i in 0 until cells) for (j in 0 until cells) {
                    drawRect(
                        if ((i + j) % 2 == 0) Color(0xFFE0E0E0) else Color.White,
                        topLeft = Offset(frameLeft + i * checkerSize, frameTop + j * checkerSize),
                        size = Size(checkerSize, checkerSize),
                    )
                }
            }
            withTransform({
                translate(frameLeft + crop.offsetX, frameTop + crop.offsetY)
                scale(crop.scale, crop.scale, pivot = Offset.Zero)
            }) {
                drawImage(image)
            }
            // Oscurece lo que queda fuera del cuadro.
            val dim = Color.Black.copy(alpha = 0.65f)
            drawRect(dim, Offset.Zero, Size(size.width, frameTop))
            drawRect(dim, Offset(0f, frameTop + frame), Size(size.width, size.height - frameTop - frame))
            drawRect(dim, Offset(0f, frameTop), Size(frameLeft, frame))
            drawRect(dim, Offset(frameLeft + frame, frameTop), Size(size.width - frameLeft - frame, frame))
            drawRect(Color.White, frameOrigin, frameSize, style = Stroke(width = 2.dp.toPx()))
        }

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onCancel, enabled = !saving, modifier = Modifier.weight(1f)) {
                Text("Cancelar", color = Color.White)
            }
            Button(
                onClick = { onSave(photo, crop, frame) },
                enabled = !saving,
                modifier = Modifier.weight(1f),
            ) { Text(if (saving) "Guardando…" else "Guardar") }
        }
    }
}
