package com.voicegen.app.ui.record

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

@Composable
fun RecordVoiceScreen(
    uiState: RecordVoiceUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onPlay: () -> Unit,
    onStopPlayback: () -> Unit,
    onRecordAgain: () -> Unit,
    onUseRecording: (String) -> Unit,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
    }

    var showNameDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (!hasPermission) {
            Text("Microphone permission is needed to record your voice.", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                Text("Grant Microphone Permission")
            }
            return@Column
        }

        when (uiState.phase) {
            RecordingPhase.IDLE -> {
                Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(24.dp))
                Text("Ready to record", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(24.dp))
                Button(onClick = onStart, modifier = Modifier.height(56.dp)) {
                    Icon(Icons.Default.Mic, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Record")
                }
            }

            RecordingPhase.RECORDING -> {
                Text("Recording", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(formatSeconds(uiState.elapsedSeconds), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onStop,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Stop")
                }
            }

            RecordingPhase.RECORDED, RecordingPhase.PLAYING -> {
                Text("Recording complete", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(formatSeconds(uiState.elapsedSeconds), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(24.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (uiState.phase == RecordingPhase.PLAYING) {
                        OutlinedButton(onClick = onStopPlayback) {
                            Icon(Icons.Default.Stop, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Stop")
                        }
                    } else {
                        OutlinedButton(onClick = onPlay) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Play")
                        }
                    }
                    OutlinedButton(onClick = onRecordAgain) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Record Again")
                    }
                }

                Spacer(Modifier.height(24.dp))
                Button(onClick = { showNameDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Use This Voice")
                }
            }
        }
    }

    if (showNameDialog) {
        var name by remember { mutableStateOf("My Voice") }
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text("Name this voice") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Voice name") })
            },
            confirmButton = {
                TextButton(onClick = {
                    onUseRecording(name.ifBlank { "My Voice" })
                    showNameDialog = false
                }) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = { showNameDialog = false }) { Text("Cancel") } },
        )
    }
}

private fun formatSeconds(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
