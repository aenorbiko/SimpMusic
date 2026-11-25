package com.maxrave.simpmusic.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.maxrave.simpmusic.viewModel.SharedViewModel

/** Read-only diagnostic for newly generated recommendations in this process. */
@Composable
internal fun EnhancedDiagnosticStar(
    videoId: String,
    color: Color,
    style: TextStyle,
    sharedViewModel: SharedViewModel,
) {
    val diagnostics by sharedViewModel.enhancedRadioDiagnostics.collectAsState()
    val details = diagnostics[videoId]
    var open by remember(videoId) { mutableStateOf(false) }
    Text("✦", color = color, style = style, modifier = Modifier.clickable { open = true })
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("Enhanced recommendation") },
            text = {
                Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                    if (details == null) {
                        Text("Radio diagnostics unavailable for this restored track. Start a new Enhanced queue to capture them.")
                    } else {
                        Text("A: ${details.songA}")
                        Text("B: ${details.songB}")
                        Text("Radio A: ${details.receivedA} received / ${details.eligibleA} eligible")
                        Text("Radio B: ${details.receivedB} received / ${details.eligibleB} eligible")
                        Text("Common eligible songs: ${details.commonSongs.size}")
                        Text("Common with old top 30: ${details.oldTop30Matches}")
                        Text("Source: ${details.source}")
                        Text("Selected: ${details.selectedSong}")
                        Text("Eligible rank A: ${details.selectedRankA ?: "—"} / B: ${details.selectedRankB ?: "—"}")
                        Text("Common songs (eligible ranks):")
                        if (details.commonSongs.isEmpty()) Text("None")
                        details.commonSongs.forEach { Text(it) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Close") } },
        )
    }
}
