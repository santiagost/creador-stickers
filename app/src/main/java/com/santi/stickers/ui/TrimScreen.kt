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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
    onCursorChange: (cursor: Float, exact: Boolean) -> Unit,
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
                Image(image, contentDescription = "Cuadro en la línea del tramo", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
        }
        if (durationMs < TrimMath.MIN_MS) {
            // RN-2, AC-4: muy corto, se usa entero.
            Text("Dura ${seconds(durationMs)} s: se usa entero.", color = Color.White)
        } else {
            TrimBar(state.range, state.cursor, durationMs, onRangeChange, onCursorChange)
            // AC-5 (spec 005); spec 009, sección 11
            val viewing = if (state.cursor > 0f) " · viendo ${seconds(state.cursorMs)} s" else ""
            Text("Tramo de ${seconds(state.range.lengthMs)} s desde ${seconds(state.range.startMs)} s$viewing", color = Color.White)
            Text(
                "Arrastrá la caja para moverla; tirá de los bordes para cambiar el largo (2 a 10 s). Con la perilla de abajo recorrés el tramo.",
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
 * Spec 009: la línea cruza la caja y se mueve con la perilla de abajo (D1), en su propia franja.
 */
@Composable
private fun TrimBar(
    range: ClipRange,
    cursor: Float,
    durationMs: Long,
    onRangeChange: (ClipRange, Boolean) -> Unit,
    onCursorChange: (Float, Boolean) -> Unit,
) {
    val currentRange by rememberUpdatedState(range)
    val currentCursor by rememberUpdatedState(cursor)
    val onChange by rememberUpdatedState(onRangeChange)
    val onCursor by rememberUpdatedState(onCursorChange)
    val handle = 14.dp
    val grab = with(LocalDensity.current) { 28.dp.toPx() } // zona para agarrar un borde, más ancha que la manija
    val knob = 24.dp

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val widthPx = constraints.maxWidth.toFloat()
        val msPerPx = durationMs / widthPx
        val density = LocalDensity.current
        val lineDp = with(density) { (TrimMath.cursorMs(range, cursor) / msPerPx).toDp() }
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.15f))
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
                // Spec 009: la línea, sobre la caja.
                Box(Modifier.offset(x = lineDp - 1.5.dp).width(3.dp).fillMaxHeight().background(Color.White))
            }
            // Spec 009, D1, AC-5: franja de la perilla. Arrastrar en cualquier lugar de la franja mueve la línea.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .pointerInput(durationMs, widthPx) {
                        var origin = 0f
                        var originRange = currentRange
                        var dragPx = 0f
                        detectHorizontalDragGestures(
                            onDragStart = {
                                origin = currentCursor
                                originRange = currentRange
                                dragPx = 0f
                            },
                            onDragEnd = { onCursor(currentCursor, true) },
                            onDragCancel = { onCursor(currentCursor, true) },
                        ) { change, delta ->
                            change.consume()
                            dragPx += delta
                            val next = TrimMath.dragCursor(originRange, origin, (dragPx * msPerPx).roundToLong())
                            if (next != currentCursor) onCursor(next, false)
                        }
                    },
            ) {
                Box(Modifier.offset(x = lineDp - 1.5.dp).width(3.dp).height(10.dp).background(Color.White))
                Box(
                    Modifier
                        .offset(x = lineDp - knob / 2, y = 8.dp)
                        .size(knob)
                        .background(Color.White, CircleShape),
                )
            }
        }
    }
}

private fun seconds(ms: Long) = String.format(Locale.forLanguageTag("es-AR"), "%.1f", ms / 1000f)
