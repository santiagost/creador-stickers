package com.santi.stickers.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.santi.stickers.image.ClipRange
import com.santi.stickers.image.TrimMath
import java.util.Locale
import kotlin.math.roundToLong

/** Spec 005, sección 11: el cuadro de inicio arriba y la caja del tramo sobre la línea de tiempo. */
@Composable
fun TrimScreen(
    state: Screen.Trim,
    onRangeChange: (range: ClipRange, exact: Boolean) -> Unit,
    onCancel: () -> Unit,
    onNext: () -> Unit,
) {
    BackHandler(onBack = onCancel) // AC-4 (spec 003)
    val durationMs = state.source.durationMs

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Elegí el tramo", color = Color.White)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val preview = state.preview
            if (preview == null) {
                CircularProgressIndicator()
            } else {
                val image = remember(preview) { preview.asImageBitmap() }
                Image(image, contentDescription = "Cuadro donde arranca el tramo", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
        }
        if (durationMs < TrimMath.MIN_MS) {
            // RN-2, AC-4: muy corto, se usa entero.
            Text("Dura ${seconds(durationMs)} s: se usa entero.", color = Color.White)
        } else {
            TrimBar(state.range, durationMs, onRangeChange)
            // AC-5
            Text("Tramo de ${seconds(state.range.lengthMs)} s desde ${seconds(state.range.startMs)} s", color = Color.White)
            Text(
                "Arrastrá la caja para moverla; tirá de los bordes para cambiar el largo (2 a 10 s).",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancelar", color = Color.White) }
            Button(onClick = onNext, enabled = state.preview != null, modifier = Modifier.weight(1f)) { Text("Siguiente") }
        }
    }
}

private enum class DragMode { MOVE, START, END }

/**
 * La línea de tiempo con la caja (RN-1, AC-2, AC-3). Arrastrar adentro la mueve; arrastrar cerca de un borde
 * cambia el largo. La cuenta la hace [TrimMath] sobre la caja del principio del gesto, para no acumular redondeos.
 */
@Composable
private fun TrimBar(range: ClipRange, durationMs: Long, onRangeChange: (ClipRange, Boolean) -> Unit) {
    val currentRange by rememberUpdatedState(range)
    val onChange by rememberUpdatedState(onRangeChange)
    val handle = 14.dp
    val grab = with(LocalDensity.current) { 28.dp.toPx() } // zona para agarrar un borde, más ancha que la manija

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.15f)),
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val msPerPx = durationMs / widthPx
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(durationMs, widthPx) {
                    var mode: DragMode? = null
                    var origin = currentRange
                    var dragPx = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { pos ->
                            origin = currentRange
                            dragPx = 0f
                            val startPx = origin.startMs / msPerPx
                            val endPx = origin.endMs / msPerPx
                            mode = when {
                                // Si la caja es angosta, el borde más cercano gana.
                                pos.x in (startPx - grab)..(startPx + grab) && pos.x <= (startPx + endPx) / 2 -> DragMode.START
                                pos.x in (endPx - grab)..(endPx + grab) -> DragMode.END
                                pos.x in startPx..endPx -> DragMode.MOVE
                                else -> null
                            }
                        },
                        onDragEnd = { if (mode != null) onChange(currentRange, true) },
                        onDragCancel = { if (mode != null) onChange(currentRange, true) },
                    ) { change, delta ->
                        val m = mode ?: return@detectHorizontalDragGestures
                        change.consume()
                        dragPx += delta
                        val deltaMs = (dragPx * msPerPx).roundToLong()
                        val next = when (m) {
                            DragMode.MOVE -> TrimMath.move(origin, deltaMs, durationMs)
                            DragMode.START -> TrimMath.resizeStart(origin, deltaMs, durationMs)
                            DragMode.END -> TrimMath.resizeEnd(origin, deltaMs, durationMs)
                        }
                        if (next != currentRange) onChange(next, false)
                    }
                },
        ) {
            val density = LocalDensity.current
            val startDp = with(density) { (range.startMs / msPerPx).toDp() }
            val widthDp = with(density) { (range.lengthMs / msPerPx).toDp() }
            val color = MaterialTheme.colorScheme.primary
            Box(
                Modifier
                    .offset(x = startDp)
                    .width(widthDp)
                    .fillMaxHeight()
                    .background(color.copy(alpha = 0.3f))
                    .border(3.dp, color, RoundedCornerShape(8.dp)),
            ) {
                Box(Modifier.align(Alignment.CenterStart).width(handle).fillMaxHeight().background(color, RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)))
                Box(Modifier.align(Alignment.CenterEnd).width(handle).fillMaxHeight().background(color, RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)))
            }
        }
    }
}

private fun seconds(ms: Long) = String.format(Locale.forLanguageTag("es-AR"), "%.1f", ms / 1000f)
