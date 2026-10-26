package com.doorbell.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.doorbell.app.model.DetectionEvent
import com.doorbell.app.model.EventType
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventHistoryScreen() {
    var events by remember { mutableStateOf<List<DetectionEvent>>(emptyList()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Event History") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        if (events.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No events yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(events) { event ->
                    EventCard(event = event)
                }
            }
        }
    }
}

@Composable
fun EventCard(event: DetectionEvent) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (event.type) {
                    EventType.PERSON_DETECTED -> Icons.Default.Person
                    EventType.LOW_BATTERY -> Icons.Default.Warning
                    EventType.STORAGE_LOW -> Icons.Default.Warning
                    EventType.DEVICE_OFFLINE -> Icons.Default.Warning
                },
                contentDescription = null,
                tint = when (event.type) {
                    EventType.PERSON_DETECTED -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.error
                },
                modifier = Modifier.size(40.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (event.type) {
                        EventType.PERSON_DETECTED -> "Person Detected"
                        EventType.LOW_BATTERY -> "Low Battery"
                        EventType.STORAGE_LOW -> "Storage Low"
                        EventType.DEVICE_OFFLINE -> "Device Offline"
                    },
                    style = MaterialTheme.typography.titleSmall
                )

                Text(
                    text = dateFormat.format(Date(event.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )

                if (event.type == EventType.PERSON_DETECTED && event.confidence > 0) {
                    Text(
                        text = "Confidence: ${String.format("%.0f", event.confidence * 100)}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}
