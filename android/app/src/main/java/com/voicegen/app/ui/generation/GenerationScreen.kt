package com.voicegen.app.ui.generation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun GenerationScreen(
    uiState: GenerationUiState,
    onCancel: () -> Unit,
    onDone: () -> Unit,
    onPlay: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (uiState.status) {
            "completed" -> {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(12.dp))
                Text("Generation Complete", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onPlay) { Text("Play") }
                    OutlinedButton(onClick = onSave) { Text("Save") }
                    OutlinedButton(onClick = onShare) { Text("Share") }
                }
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onDone) { Text("Back to Home") }
            }

            "failed" -> {
                Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(12.dp))
                Text("Generation Failed", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    uiState.errorMessage ?: "Something went wrong during generation.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                TextButton(onClick = onDone) { Text("Back to Home") }
            }

            "cancelled" -> {
                Text("Generation Cancelled", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onDone) { Text("Back to Home") }
            }

            else -> {
                Text("Generating Speech", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                if (uiState.totalChunks > 0) {
                    Text("Chunk ${uiState.currentChunk} / ${uiState.totalChunks}", style = MaterialTheme.typography.titleMedium)
                } else {
                    Text("Queued…", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(8.dp))
                Text("${uiState.progress}%", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { uiState.progress / 100f },
                    modifier = Modifier.fillMaxWidth(0.8f).height(10.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text("Elapsed: ${formatSeconds(uiState.elapsedSeconds)}", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(24.dp))
                OutlinedButton(onClick = onCancel) { Text("Cancel") }
            }
        }
    }
}

private fun formatSeconds(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
