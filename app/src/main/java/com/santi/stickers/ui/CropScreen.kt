package com.santi.stickers.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.santi.stickers.image.CropMath
import com.santi.stickers.image.CropTransform
import kotlin.math.min

/** Pantalla de encuadre (spec 001, 11.2; spec 002, sección 11). */
@Composable
fun CropScreen(
    state: Screen.Crop,
    saving: Boolean,
    snackbar: SnackbarHostState,
    onToggleBackground: () -> Unit,
    onCancel: () -> Unit,
    onSave: (photo: Bitmap, crop: CropTransform, frameSize: Float) -> Unit,
) {
    BackHandler(onBack = onCancel) // AC-5 (spec 001), AC-7 (spec 002)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Text(
                "Mové y pellizcá la foto para encuadrarla",
                color = Color.White,
                modifier = Modifier.padding(16.dp),
            )
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val photo = state.photo
                if (photo == null) {
                    CircularProgressIndicator()
                } else {
                    val density = LocalDensity.current
                    val widthPx = with(density) { maxWidth.toPx() }
                    val heightPx = with(density) { maxHeight.toPx() }
                    val margin = with(density) { 24.dp.toPx() }
                    val frame = min(widthPx, heightPx) - 2 * margin
                    CropArea(state, photo, widthPx, heightPx, frame, saving, onToggleBackground, onCancel, onSave)
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.TopCenter).systemBarsPadding())
    }
}

@Composable
private fun CropArea(
    state: Screen.Crop,
    photo: Bitmap,
    widthPx: Float,
    heightPx: Float,
    frame: Float,
    saving: Boolean,
    onToggleBackground: () -> Unit,
    onCancel: () -> Unit,
    onSave: (Bitmap, CropTransform, Float) -> Unit,
) {
    val shown = state.shown ?: photo
    val image = remember(shown) { shown.asImageBitmap() }
    val frameLeft = (widthPx - frame) / 2f
    val frameTop = (heightPx - frame) / 2f
    val snapPx = with(LocalDensity.current) { MAGNET_DISTANCE.toPx() }
    val checkerSize = with(LocalDensity.current) { 12.dp.toPx() }

    // Posición "real" del dedo; lo que se ve es el imán aplicado (spec 002, RN-8 a RN-11).
    // Depende de la foto original, no de la mostrada: apagar "Quitar fondo" conserva el encuadre (D10).
    var raw by remember(photo, frame) { mutableStateOf(CropMath.cover(photo.width, photo.height, frame)) }
    val crop = CropMath.magnet(raw, photo.width, photo.height, frame, snapPx)

    // RN-5: al prender "Quitar fondo", el encuadre se ajusta al sujeto.
    LaunchedEffect(state.showSubject, state.removal) {
        val removal = state.removal
        if (state.showSubject && removal != null) {
            raw = CropMath.fitBox(removal.bounds, photo.width, photo.height, frame)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(photo, frame) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val centroid = event.calculateCentroid(useCurrent = false)
                            if ((zoom != 1f || pan != Offset.Zero) && centroid != Offset.Unspecified) {
                                raw = CropMath.applyGesture(
                                    raw, photo.width, photo.height, frame,
                                    centroidX = centroid.x - frameLeft,
                                    centroidY = centroid.y - frameTop,
                                    panX = pan.x,
                                    panY = pan.y,
                                    zoom = zoom,
                                )
                            }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        } while (event.changes.any { it.pressed })
                        // Al soltar, lo que se ve pasa a ser la posición real: el próximo gesto arranca pegado.
                        raw = CropMath.magnet(raw, photo.width, photo.height, frame, snapPx)
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

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = state.showSubject,
                onClick = onToggleBackground,
                enabled = !state.removing && !saving,
                label = {
                    Text(
                        when {
                            state.removing -> "Quitando fondo…"
                            state.showSubject -> "Fondo quitado"
                            else -> "Quitar fondo"
                        },
                        color = Color.White,
                    )
                },
                leadingIcon = if (state.removing) {
                    { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White) }
                } else {
                    null
                },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onCancel, enabled = !saving, modifier = Modifier.weight(1f)) {
                    Text("Cancelar", color = Color.White)
                }
                Button(
                    onClick = { onSave(shown, crop, frame) }, // RN-7 y RN-11: se guarda lo que se ve
                    enabled = !saving && !state.removing, // AC-7
                    modifier = Modifier.weight(1f),
                ) { Text(if (saving) "Guardando…" else "Guardar") }
            }
        }
    }
}

/** Spec 002, D5: distancia del imán de posición (umbral inicial, a ajustar probando). */
private val MAGNET_DISTANCE = 24.dp
