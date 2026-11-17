package com.santi.stickers.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Spec 007, sección 11 y RN-5: "Bajando video… N %" con "Cancelar" (AC-4). */
@Composable
fun DownloadScreen(state: Screen.Downloading, onCancel: () -> Unit) {
    BackHandler(onBack = onCancel)
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (state.progress <= 0f) {
            Text("Buscando el video en X…", color = Color.White)
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            Text("Bajando video… ${(state.progress * 100).toInt()} %", color = Color.White)
            LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
        }
        OutlinedButton(onClick = onCancel) { Text("Cancelar", color = Color.White) }
    }
}
