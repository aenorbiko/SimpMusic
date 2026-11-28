package com.maxrave.simpmusic.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
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
import com.maxrave.simpmusic.utils.EnhancedBuildIdentity
import com.maxrave.simpmusic.expect.copyToClipboard

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
                        SelectionContainer { Text(EnhancedBuildIdentity.reportCaption + "\n\n" + details.asText()) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Close") } },
            dismissButton = {
                if (details != null) {
                    Column {
                        TextButton(onClick = { copyToClipboard("Enhanced recommendation", EnhancedBuildIdentity.reportCaption + "\n\n" + details.asText()) }) { Text("Copy") }
                        TextButton(onClick = {
                            copyToClipboard("Enhanced radio diagnostics", EnhancedBuildIdentity.reportCaption + "\n\n" + diagnostics.values.joinToString("\n\n---\n\n") { it.asText() })
                        }) { Text("Copy all") }
                    }
                }
            },
        )
    }
}
