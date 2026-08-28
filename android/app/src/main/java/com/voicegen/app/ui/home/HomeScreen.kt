package com.voicegen.app.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.voicegen.app.data.remote.VoiceProfileDto
import java.io.BufferedReader
import java.io.InputStreamReader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onTextChanged: (String) -> Unit,
    onImportText: (String) -> Unit,
    onSpeedChanged: (Float) -> Unit,
    onFormatChanged: (String) -> Unit,
    onLanguageChanged: (String) -> Unit,
    onGenerate: () -> Unit,
    onDismissError: () -> Unit,
    onChangeVoice: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenVoiceLibrary: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val text = BufferedReader(InputStreamReader(input)).readText()
                    onImportText(text)
                }
            } catch (e: Exception) {
                // Import failure is non-fatal — the user can still type/paste text.
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Voice Generator") },
                actions = {
                    IconButton(onClick = onOpenVoiceLibrary) { Icon(Icons.Default.Person, contentDescription = "Voices") }
                    IconButton(onClick = onOpenLibrary) { Icon(Icons.Default.LibraryMusic, contentDescription = "Library") }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            Text("VOICE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Card {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        uiState.selectedVoice?.name ?: "No voice selected",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onChangeVoice) { Text("Change") }
                }
            }

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("TEXT", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                TextButton(onClick = { filePicker.launch("text/plain") }) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Import Text File")
                }
            }

            OutlinedTextField(
                value = uiState.text,
                onValueChange = onTextChanged,
                modifier = Modifier.fillMaxWidth().weight(1f),
                placeholder = { Text("Type or paste text here. There's no artificial length limit — even very large documents are supported.") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
            )

            Spacer(Modifier.height(12.dp))
            Text(
                "${uiState.text.length} characters",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))
            GenerationSettingsRow(
                language = uiState.language,
                speed = uiState.speed,
                outputFormat = uiState.outputFormat,
                onLanguageChanged = onLanguageChanged,
                onSpeedChanged = onSpeedChanged,
                onFormatChanged = onFormatChanged,
            )

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onGenerate,
                enabled = !uiState.isSubmitting && uiState.selectedVoice != null && uiState.text.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                if (uiState.isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("Starting…")
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("GENERATE SPEECH", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    uiState.errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = onDismissError,
            title = { Text("Couldn't start generation") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = onDismissError) { Text("OK") } },
        )
    }
}

@Composable
private fun GenerationSettingsRow(
    language: String,
    speed: Float,
    outputFormat: String,
    onLanguageChanged: (String) -> Unit,
    onSpeedChanged: (Float) -> Unit,
    onFormatChanged: (String) -> Unit,
) {
    Column {
        Text("Generation Settings", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Autorenew, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Speed: ${"%.1f".format(speed)}x", modifier = Modifier.width(100.dp))
            Slider(
                value = speed,
                onValueChange = onSpeedChanged,
                valueRange = 0.5f..2.0f,
                modifier = Modifier.weight(1f),
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Language", modifier = Modifier.width(100.dp))
            LanguageDropdown(language, onLanguageChanged)
            Spacer(Modifier.width(16.dp))
            Text("Output", modifier = Modifier.width(60.dp))
            FormatToggle(outputFormat, onFormatChanged)
        }
    }
}

@Composable
private fun LanguageDropdown(current: String, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val languages = listOf("en" to "English (Auto)", "es" to "Spanish", "fr" to "French", "de" to "German", "it" to "Italian", "pt" to "Portuguese")
    Box {
        AssistChip(onClick = { expanded = true }, label = { Text(languages.firstOrNull { it.first == current }?.second ?: current) })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            languages.forEach { (code, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { onChange(code); expanded = false })
            }
        }
    }
}

@Composable
private fun FormatToggle(current: String, onChange: (String) -> Unit) {
    Row {
        listOf("mp3", "wav").forEach { format ->
            FilterChip(
                selected = current == format,
                onClick = { onChange(format) },
                label = { Text(format.uppercase()) },
                modifier = Modifier.padding(end = 6.dp),
            )
        }
    }
}
