package com.voicegen.app.ui.voices

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.voicegen.app.data.remote.VoiceProfileDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceLibraryScreen(
    uiState: VoiceLibraryUiState,
    onBack: () -> Unit,
    onAddVoice: () -> Unit,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismissError: () -> Unit,
) {
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Voice Profiles") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } },
                actions = {
                    TextButton(onClick = onAddVoice) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Add Voice")
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.voices.isEmpty() && !uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No voice profiles yet.", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onAddVoice) { Text("Add Voice") }
                }
            }
            return@Scaffold
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp)) {
            items(uiState.voices, key = { it.id }) { voice: VoiceProfileDto ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val isSelected = voice.id == uiState.selectedVoiceId
                        Icon(
                            if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = if (isSelected) "Selected" else "Not selected",
                            tint = if (isSelected) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable_select { onSelect(voice.id) },
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(voice.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${voice.durationSeconds.toInt()}s sample",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { pendingDeleteId = voice.id }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                }
            }
        }
    }

    pendingDeleteId?.let { id ->
        val name = uiState.voices.firstOrNull { it.id == id }?.name ?: "this voice"
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Delete voice profile?") },
            text = { Text("This will permanently delete \"$name\" and its reference sample. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(id)
                    pendingDeleteId = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteId = null }) { Text("Cancel") } },
        )
    }

    uiState.errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = onDismissError,
            title = { Text("Couldn't load voices") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = onDismissError) { Text("OK") } },
        )
    }
}

// Small helper so the selection-state icon is tappable without pulling in
// a whole separate clickable row component for this one case.
private fun Modifier.clickable_select(onClick: () -> Unit): Modifier =
    this.then(androidx.compose.foundation.clickable(onClick = onClick))
