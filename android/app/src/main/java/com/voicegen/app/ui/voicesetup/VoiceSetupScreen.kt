package com.voicegen.app.ui.voicesetup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Spec section 6: the screen shown on first launch, before the user ever
 * sees the text-generation home screen. Two paths in: upload a file, or
 * record directly (handled on RecordVoiceScreen).
 */
@Composable
fun VoiceSetupScreen(
    uiState: VoiceSetupUiState,
    onUploadFile: (Uri, String) -> Unit,
    onRecordVoiceClick: () -> Unit,
    onDismissError: () -> Unit,
) {
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var showNameDialog by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            pendingUri = uri
            showNameDialog = true
        }
    }

    Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Set Up Your Voice",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Upload or record a voice sample. This voice will be used to generate your speech.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(40.dp))

            Button(
                onClick = { filePicker.launch("audio/*") },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = !uiState.isUploading,
            ) {
                Icon(Icons.Default.UploadFile, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Upload Voice Sample")
            }

            Spacer(Modifier.height(16.dp))

            OutlinedButton(
                onClick = onRecordVoiceClick,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = !uiState.isUploading,
            ) {
                Icon(Icons.Default.Mic, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Record Voice")
            }

            if (uiState.isUploading) {
                Spacer(Modifier.height(24.dp))
                CircularProgressIndicator()
                Spacer(Modifier.height(8.dp))
                Text("Uploading and validating your voice sample…", style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(32.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Only upload or record a voice that you own or have permission to use. " +
                        "Voice cloning should only be used responsibly and with appropriate authorization.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    if (showNameDialog && pendingUri != null) {
        var name by remember { mutableStateOf("My Voice") }
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text("Name this voice") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Voice name") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onUploadFile(pendingUri!!, name.ifBlank { "My Voice" })
                    showNameDialog = false
                }) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) { Text("Cancel") }
            },
        )
    }

    uiState.errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = onDismissError,
            title = { Text("Couldn't use that voice sample") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = onDismissError) { Text("OK") } },
        )
    }
}
