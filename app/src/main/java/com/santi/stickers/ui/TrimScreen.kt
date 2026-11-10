package com.santi.stickers.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.santi.stickers.image.VideoFrames
import java.util.Locale

/** Spec 003, sección 11: elegir desde dónde arranca el tramo de 3 s. */
@Composable
fun TrimScreen(
    state: Screen.Trim,
    onStartChange: (startMs: Long, exact: Boolean) -> Unit,
    onCancel: () -> Unit,
    onNext: () -> Unit,
) {
    BackHandler(onBack = onCancel) // AC-4
    val maxStart = VideoFrames.maxStart(state.info)
    val clipMs = VideoFrames.clipLength(state.info, state.startMs)

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Elegí desde dónde arranca", color = Color.White)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val preview = state.preview
            if (preview == null) {
                CircularProgressIndicator()
            } else {
                val image = remember(preview) { preview.asImageBitmap() }
                Image(image, contentDescription = "Cuadro donde arranca el tramo", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
        }
        if (maxStart > 0) {
            Slider(
                value = state.startMs.toFloat(),
                onValueChange = { onStartChange(it.toLong(), false) },
                onValueChangeFinished = { onStartChange(state.startMs, true) },
                valueRange = 0f..maxStart.toFloat(),
            )
            Text("Tramo de ${seconds(clipMs)} s desde ${seconds(state.startMs)} s", color = Color.White)
        } else {
            // AC-3: video más corto que el tramo.
            Text("El video dura ${seconds(state.info.durationMs)} s: se usa entero.", color = Color.White)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancelar", color = Color.White) }
            Button(onClick = onNext, enabled = state.preview != null, modifier = Modifier.weight(1f)) { Text("Siguiente") }
        }
    }
}

private fun seconds(ms: Long) = String.format(Locale.forLanguageTag("es-AR"), "%.1f", ms / 1000f)
