package com.doorbell.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.doorbell.app.network.WebSocketClient
import com.doorbell.app.service.DoorbellService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoorbellApp(
    onStartService: () -> Unit,
    onStopService: () -> Unit
) {
    val navController = rememberNavController()
    val context = LocalContext.current

    var serviceRunning by remember { mutableStateOf(false) }
    val connectionState by DoorbellService.connectionStateState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Smart Doorbell") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "preview",
            modifier = Modifier.padding(padding)
        ) {
            composable("preview") {
                PreviewScreen(
                    serviceRunning = serviceRunning,
                    connectionState = connectionState,
                    onStartService = {
                        onStartService()
                        serviceRunning = true
                    },
                    onStopService = {
                        onStopService()
                        serviceRunning = false
                    },
                    onNavigateToSettings = { navController.navigate("settings") },
                    onNavigateToEvents = { navController.navigate("events") }
                )
            }

            composable("settings") {
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable("events") {
                EventHistoryScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}

@Composable
fun PreviewScreen(
    serviceRunning: Boolean,
    connectionState: WebSocketClient.ConnectionState,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToEvents: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                CameraPreview(modifier = Modifier.fillMaxSize())

                Column(
                    modifier = Modifier
                        .align(androidx.compose.ui.Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    ConnectionStatusBadge(connectionState)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            OutlinedButton(
                onClick = if (serviceRunning) onStopService else onStartService
            ) {
                Icon(
                    imageVector = if (serviceRunning) Icons.Default.Close else Icons.Default.PlayArrow,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (serviceRunning) "Stop" else "Start")
            }

            OutlinedButton(onClick = onNavigateToSettings) {
                Icon(Icons.Default.Settings, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Settings")
            }

            OutlinedButton(onClick = onNavigateToEvents) {
                Icon(Icons.Default.List, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Events")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        StatusCard(serviceRunning = serviceRunning, connectionState = connectionState)
    }
}

@Composable
fun CameraPreview(modifier: Modifier = Modifier) {
    val bitmap by DoorbellService.previewBitmap.collectAsState()

    bitmap?.let { bmp ->
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "Camera preview",
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } ?: Box(modifier = modifier)
}

@Composable
fun ConnectionStatusBadge(state: WebSocketClient.ConnectionState) {
    val (color, text) = when (state) {
        WebSocketClient.ConnectionState.CONNECTED -> MaterialTheme.colorScheme.primary to "Connected"
        WebSocketClient.ConnectionState.CONNECTING -> MaterialTheme.colorScheme.tertiary to "Connecting"
        WebSocketClient.ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.outline to "Disconnected"
        WebSocketClient.ConnectionState.ERROR -> MaterialTheme.colorScheme.error to "Error"
    }

    Surface(
        color = color.copy(alpha = 0.9f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
fun StatusCard(serviceRunning: Boolean, connectionState: WebSocketClient.ConnectionState) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Status",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Service")
                Text(
                    text = if (serviceRunning) "Running" else "Stopped",
                    color = if (serviceRunning) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline
                )
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Connection")
                Text(
                    text = connectionState.name.lowercase().replaceFirstChar { it.uppercase() },
                    color = when (connectionState) {
                        WebSocketClient.ConnectionState.CONNECTED -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.outline
                    }
                )
            }
        }
    }
}
