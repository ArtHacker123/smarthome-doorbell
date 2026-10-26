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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
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

    var serviceRunning by remember { mutableStateOf(false) }
    val connectionState by DoorbellService.connectionStateState.collectAsStateWithLifecycle()

    val items = listOf(
        BottomNavItem("preview", "Home", Icons.Default.Home),
        BottomNavItem("events", "Events", Icons.Default.List),
        BottomNavItem("settings", "Settings", Icons.Default.Settings)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Smart Doorbell") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        bottomBar = {
            NavigationBar {
                val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
                items.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        selected = currentRoute == item.route,
                        onClick = {
                            if (currentRoute != item.route) {
                                navController.navigate(item.route) {
                                    popUpTo("preview") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            }
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
                    onTestDetection = {
                        DoorbellService.currentInstance?.testSimulateDetection()
                    }
                )
            }

            composable("settings") {
                SettingsScreen(
                    onSave = {
                        navController.navigate("preview") {
                            popUpTo("preview") { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable("events") {
                EventHistoryScreen()
            }
        }
    }
}

data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@Composable
fun PreviewScreen(
    serviceRunning: Boolean,
    connectionState: WebSocketClient.ConnectionState,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onTestDetection: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f),
            shape = MaterialTheme.shapes.large,
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                CameraPreview(modifier = Modifier.fillMaxSize())

                Row(
                    modifier = Modifier
                        .align(androidx.compose.ui.Alignment.TopStart)
                        .padding(12.dp)
                ) {
                    ConnectionStatusBadge(connectionState)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = if (serviceRunning) onStopService else onStartService,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (serviceRunning) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = if (serviceRunning) Icons.Default.Close else Icons.Default.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (serviceRunning) "Stop Monitoring" else "Start Monitoring")
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(top = 12.dp)
                ) {
                    if (serviceRunning) {
                        OutlinedButton(
                            onClick = onTestDetection,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = false
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Simulate Detection")
                        }
                    }
                }
            }
        }
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
        color = color.copy(alpha = 0.95f),
        shape = MaterialTheme.shapes.small,
        shadowElevation = 2.dp
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
fun StatusCard(serviceRunning: Boolean, connectionState: WebSocketClient.ConnectionState) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "System Status",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text("Monitoring Service")
            Surface(
                color = if (serviceRunning) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = if (serviceRunning) "Active" else "Inactive",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (serviceRunning) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text("Server Connection")
            Surface(
                color = when (connectionState) {
                    WebSocketClient.ConnectionState.CONNECTED -> MaterialTheme.colorScheme.primaryContainer
                    WebSocketClient.ConnectionState.CONNECTING -> MaterialTheme.colorScheme.tertiaryContainer
                    WebSocketClient.ConnectionState.ERROR -> MaterialTheme.colorScheme.errorContainer
                    WebSocketClient.ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.surfaceVariant
                },
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = connectionState.name.lowercase().replaceFirstChar { it.uppercase() },
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = when (connectionState) {
                        WebSocketClient.ConnectionState.CONNECTED -> MaterialTheme.colorScheme.onPrimaryContainer
                        WebSocketClient.ConnectionState.CONNECTING -> MaterialTheme.colorScheme.onTertiaryContainer
                        WebSocketClient.ConnectionState.ERROR -> MaterialTheme.colorScheme.onErrorContainer
                        WebSocketClient.ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}
