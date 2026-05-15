package com.doorbell.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val config by viewModel.config.collectAsState()

    var sensitivity by remember(config) { mutableFloatStateOf(config.sensitivity) }
    var minDuration by remember(config) { mutableIntStateOf(config.minDuration) }
    var cooldown by remember(config) { mutableIntStateOf(config.cooldown) }
    var motionSensitivity by remember(config) { mutableFloatStateOf(config.motionSensitivity) }
    var motionPixelThreshold by remember(config) { mutableIntStateOf(config.motionPixelThreshold) }
    var zoomRatio by remember(config) { mutableFloatStateOf(config.zoomRatio) }
    var recordDuration by remember(config) { mutableIntStateOf(config.recordDuration) }
    var retentionDays by remember(config) { mutableIntStateOf(config.retentionDays) }
    var screenTimeout by remember(config) { mutableIntStateOf(config.screenTimeout) }
    var pcServerAddress by remember(config) { mutableStateOf(config.pcServerAddress) }
    var pcServerPort by remember(config) { mutableIntStateOf(config.pcServerPort) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Detection Settings",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text("Sensitivity: ${String.format("%.1f", sensitivity)}")
            Slider(
                value = sensitivity,
                onValueChange = { sensitivity = it },
                valueRange = 0.3f..0.95f,
                steps = 12
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text("Min Duration: ${minDuration}s")
            Slider(
                value = minDuration.toFloat(),
                onValueChange = { minDuration = it.toInt() },
                valueRange = 1f..10f,
                steps = 8
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text("Cooldown: ${cooldown}s")
            Slider(
                value = cooldown.toFloat(),
                onValueChange = { cooldown = it.toInt() },
                valueRange = 10f..300f,
                steps = 28
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text("Motion Sensitivity: ${String.format("%.0f", motionSensitivity * 100)}%")
            Slider(
                value = motionSensitivity,
                onValueChange = { motionSensitivity = it },
                valueRange = 0.05f..0.50f,
                steps = 8
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text("Motion Pixel Threshold: ${motionPixelThreshold}")
            Slider(
                value = motionPixelThreshold.toFloat(),
                onValueChange = { motionPixelThreshold = it.toInt() },
                valueRange = 10f..100f,
                steps = 17
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text("Zoom Ratio: ${String.format("%.1f", zoomRatio)}x")
            Slider(
                value = zoomRatio,
                onValueChange = { zoomRatio = it },
                valueRange = 1f..10f,
                steps = 17
            )

            Divider(modifier = Modifier.padding(vertical = 16.dp))

            Text(
                text = "Recording Settings",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text("Record Duration: ${recordDuration}s")
            Slider(
                value = recordDuration.toFloat(),
                onValueChange = { recordDuration = it.toInt() },
                valueRange = 5f..30f,
                steps = 24
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text("Retention: ${retentionDays} days")
            Slider(
                value = retentionDays.toFloat(),
                onValueChange = { retentionDays = it.toInt() },
                valueRange = 1f..90f,
                steps = 88
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text("Screen Timeout: ${screenTimeout}s")
            Slider(
                value = screenTimeout.toFloat(),
                onValueChange = { screenTimeout = it.toInt() },
                valueRange = 10f..120f,
                steps = 21
            )

            Divider(modifier = Modifier.padding(vertical = 16.dp))

            Text(
                text = "PC Server Settings",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = pcServerAddress,
                onValueChange = { pcServerAddress = it },
                label = { Text("PC Server Address") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = pcServerPort.toString(),
                onValueChange = { pcServerPort = it.toIntOrNull() ?: 8080 },
                label = { Text("PC Server Port") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    viewModel.updateConfig(
                        config.copy(
                            sensitivity = sensitivity,
                            minDuration = minDuration,
                            cooldown = cooldown,
                            motionSensitivity = motionSensitivity,
                            motionPixelThreshold = motionPixelThreshold,
                            zoomRatio = zoomRatio,
                            recordDuration = recordDuration,
                            retentionDays = retentionDays,
                            screenTimeout = screenTimeout,
                            pcServerAddress = pcServerAddress,
                            pcServerPort = pcServerPort
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Settings")
            }
        }
    }
}
