package com.voicegen.app.ui.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    uiState: LibraryUiState,
    onBack: () -> Unit,
    onPlay: (jobId: String, outputFormat: String) -> Unit,
    onShare: (jobId: String, outputFormat: String) -> Unit,
    onDelete: (jobId: String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Library") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } },
            )
        }
    ) { padding ->
        if (uiState.items.isEmpty() && !uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No generations yet.", style = MaterialTheme.typography.bodyLarge)
            }
            return@Scaffold
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp)) {
            items(uiState.items, key = { it.record.jobId }) { item ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            item.record.textPreview.ifBlank { "(untitled)" },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            DateFormat.getDateTimeInstance().format(Date(item.record.createdAtMillis)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Voice: ${item.record.voiceName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        val status = item.status?.status ?: "unknown"
                        Spacer(Modifier.height(6.dp))
                        StatusBadge(status)

                        if (status == "failed" && item.status?.errorMessage != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                item.status.errorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }

                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (status == "completed") {
                                TextButton(onClick = { onPlay(item.record.jobId, item.record.outputFormat) }) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Play")
                                }
                                TextButton(onClick = { onShare(item.record.jobId, item.record.outputFormat) }) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Share")
                                }
                            }
                            TextButton(onClick = { onDelete(item.record.jobId) }) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Delete")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val (label, color) = when (status) {
        "completed" -> "Completed" to MaterialTheme.colorScheme.primary
        "processing" -> "Processing…" to MaterialTheme.colorScheme.secondary
        "queued" -> "Queued" to MaterialTheme.colorScheme.secondary
        "failed" -> "Failed" to MaterialTheme.colorScheme.error
        "cancelled" -> "Cancelled" to MaterialTheme.colorScheme.onSurfaceVariant
        else -> "Unknown" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    AssistChip(onClick = {}, label = { Text(label, fontWeight = FontWeight.Medium) }, colors = AssistChipDefaults.assistChipColors(labelColor = color))
}
