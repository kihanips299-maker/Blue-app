package com.example.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsBluetooth
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AudioOutputDevice
import com.example.model.BluetoothConnectionStatus
import com.example.model.DiscoveredBluetoothDevice
import com.example.model.PartyUiState
import com.example.model.SignalStrengthLevel
import com.example.ui.components.MasterVolumeConsole
import com.example.ui.components.SpeakerBatteryBadge
import com.example.ui.components.SpeakerVolumeControl
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CoralError
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.PartyMagenta
import com.example.ui.theme.SpeakerAccentColors
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.SurfaceHighest
import com.example.ui.theme.SyncMint
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarmAmber

@Composable
fun BluetoothDiscoveryScreen(
    state: PartyUiState,
    onRequestBluetoothPermissions: () -> Unit,
    onToggleScan: () -> Unit,
    onRefresh: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onSetAudioOnlyFilter: (Boolean) -> Unit,
    onPairDevice: (String) -> Unit,
    onMasterVolumeChange: (Float) -> Unit,
    onToggleMasterMute: () -> Unit,
    onSpeakerVolumeChange: (address: String, name: String, volume: Float) -> Unit,
    onToggleSpeakerMute: (address: String, name: String) -> Unit,
    onNavigateToSpeakers: () -> Unit,
    modifier: Modifier = Modifier
) {
    val caps = state.bluetoothCapabilities
    val filteredDevices = if (state.discoveryAudioOnlyFilter) {
        state.discoveredDevices.filter { it.isAudioDevice }
    } else {
        state.discoveredDevices
    }

    val connectedCount = filteredDevices.count { it.connectionStatus == BluetoothConnectionStatus.CONNECTED }
    val pairedCount = filteredDevices.count { it.connectionStatus == BluetoothConnectionStatus.PAIRED }
    val availableCount = filteredDevices.count { it.connectionStatus == BluetoothConnectionStatus.AVAILABLE }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("bluetooth_discovery_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Permission prompt if Bluetooth permissions are missing
        if (!caps.hasBluetoothPermissions) {
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = WarmAmber.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, WarmAmber.copy(alpha = 0.55f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bluetooth,
                                contentDescription = null,
                                tint = WarmAmber
                            )
                            Text(
                                text = "Bluetooth Scan Permission Required",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                        }
                        Text(
                            text = stringResource(R.string.bt_permission_rationale),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Button(
                            onClick = onRequestBluetoothPermissions,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = WarmAmber,
                                contentColor = ObsidianBg
                            ),
                            modifier = Modifier.testTag("discovery_grant_bt_button")
                        ) {
                            Text(stringResource(R.string.action_grant_bt))
                        }
                    }
                }
            }
        }

        // 2. Radar Scan Header Card
        item {
            DiscoveryRadarHeaderCard(
                bluetoothEnabled = caps.bluetoothEnabled,
                isScanning = state.isScanning,
                connectedCount = connectedCount,
                pairedCount = pairedCount,
                availableCount = availableCount,
                onToggleScan = onToggleScan,
                onRefresh = onRefresh,
                onOpenBluetoothSettings = onOpenBluetoothSettings
            )
        }

        // 3. Global Master Volume Control
        if (connectedCount > 0 || state.outputDevices.any { it.isConnected }) {
            item {
                MasterVolumeConsole(
                    masterVolume = state.masterVolume,
                    isMasterMuted = state.isMasterMuted,
                    connectedSpeakers = state.outputDevices,
                    masterRms = state.diagnostics.masterRms,
                    onVolumeChange = onMasterVolumeChange,
                    onToggleMute = onToggleMasterMute
                )
            }
        }

        // 4. Filter Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.discoveryAudioOnlyFilter,
                        onClick = { onSetAudioOnlyFilter(true) },
                        label = { Text("Audio Speakers") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Speaker,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricCyan.copy(alpha = 0.2f),
                            selectedLabelColor = ElectricCyan,
                            selectedLeadingIconColor = ElectricCyan,
                            containerColor = SurfaceDark,
                            labelColor = TextSecondary
                        ),
                        modifier = Modifier.testTag("filter_audio_only_chip")
                    )
                    FilterChip(
                        selected = !state.discoveryAudioOnlyFilter,
                        onClick = { onSetAudioOnlyFilter(false) },
                        label = { Text("All Bluetooth") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Bluetooth,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricCyan.copy(alpha = 0.2f),
                            selectedLabelColor = ElectricCyan,
                            selectedLeadingIconColor = ElectricCyan,
                            containerColor = SurfaceDark,
                            labelColor = TextSecondary
                        ),
                        modifier = Modifier.testTag("filter_all_bt_chip")
                    )
                }

                Text(
                    text = "${filteredDevices.size} found",
                    fontFamily = MonoFontFamily,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }
        }

        // 5. Empty State or Discovered Speaker List
        if (filteredDevices.isEmpty()) {
            item {
                DiscoveryEmptyStateCard(
                    isScanning = state.isScanning,
                    bluetoothEnabled = caps.bluetoothEnabled,
                    onToggleScan = onToggleScan,
                    onOpenBluetoothSettings = onOpenBluetoothSettings
                )
            }
        } else {
            items(filteredDevices, key = { it.address }) { device ->
                val linkedOutput = state.outputDevices.find { out ->
                    out.address.equals(device.address, ignoreCase = true) ||
                        out.id == "bt_${device.address}" ||
                        (out.isConnected && out.name.equals(device.name, ignoreCase = true))
                }
                DiscoveredSpeakerCard(
                    device = device,
                    linkedOutput = linkedOutput,
                    masterVolume = state.masterVolume,
                    isMasterMuted = state.isMasterMuted,
                    onPair = { onPairDevice(device.address) },
                    onConnect = onOpenBluetoothSettings,
                    onVolumeChange = { vol -> onSpeakerVolumeChange(device.address, device.name, vol) },
                    onToggleMute = { onToggleSpeakerMute(device.address, device.name) },
                    onManageSynced = onNavigateToSpeakers
                )
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun DiscoveryRadarHeaderCard(
    bluetoothEnabled: Boolean,
    isScanning: Boolean,
    connectedCount: Int,
    pairedCount: Int,
    availableCount: Int,
    onToggleScan: () -> Unit,
    onRefresh: () -> Unit,
    onOpenBluetoothSettings: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = SurfaceDark,
        border = BorderStroke(1.dp, if (isScanning) ElectricCyan.copy(alpha = 0.55f) else BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    ScanningRadarBadge(
                        isScanning = isScanning,
                        bluetoothEnabled = bluetoothEnabled
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when {
                                !bluetoothEnabled -> "BLUETOOTH OFF"
                                isScanning -> "SCANNING NEARBY..."
                                else -> "SPEAKER RADAR"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = when {
                                !bluetoothEnabled -> CoralError
                                isScanning -> ElectricCyan
                                else -> TextSecondary
                            }
                        )
                        Text(
                            text = when {
                                !bluetoothEnabled -> "Turn on Bluetooth"
                                isScanning -> "Classic BT & LE Audio"
                                else -> "Discover party speakers"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceElevated,
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onRefresh)
                        .testTag("discovery_refresh_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Bluetooth devices",
                            tint = ElectricCyan
                        )
                    }
                }
            }

            // Summary Counters Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DiscoveryCountPill("Connected", connectedCount, SyncMint, Modifier.weight(1f))
                DiscoveryCountPill("Paired", pairedCount, ElectricCyan, Modifier.weight(1f))
                DiscoveryCountPill("Available", availableCount, PartyMagenta, Modifier.weight(1f))
            }

            // Scan & Settings Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onToggleScan,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isScanning) PartyMagenta else ElectricCyan,
                        contentColor = ObsidianBg
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("discovery_scan_toggle_button")
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = ObsidianBg
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Stop Scan")
                    } else {
                        Icon(
                            imageVector = Icons.Default.BluetoothSearching,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Scan for Speakers")
                    }
                }

                OutlinedButton(
                    onClick = onOpenBluetoothSettings,
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("discovery_bt_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SettingsBluetooth,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("BT Settings", color = TextPrimary)
                }
            }
        }
    }
}

@Composable
private fun ScanningRadarBadge(
    isScanning: Boolean,
    bluetoothEnabled: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar_pulse"
    )

    Box(
        modifier = Modifier.size(56.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = size.minDimension / 2f
            if (isScanning) {
                drawCircle(
                    color = ElectricCyan.copy(alpha = (1f - pulse) * 0.6f),
                    radius = maxRadius * (0.45f + 0.55f * pulse),
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
                val secondPulse = (pulse + 0.5f) % 1f
                drawCircle(
                    color = PartyMagenta.copy(alpha = (1f - secondPulse) * 0.45f),
                    radius = maxRadius * (0.45f + 0.55f * secondPulse),
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
            drawCircle(
                color = if (bluetoothEnabled) ElectricCyan.copy(alpha = 0.16f) else CoralError.copy(alpha = 0.16f),
                radius = maxRadius * 0.68f,
                center = center
            )
        }

        Icon(
            imageVector = when {
                !bluetoothEnabled -> Icons.Default.BluetoothDisabled
                isScanning -> Icons.Default.BluetoothSearching
                else -> Icons.Default.BluetoothAudio
            },
            contentDescription = null,
            tint = if (bluetoothEnabled) ElectricCyan else CoralError,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun DiscoveryCountPill(
    label: String,
    count: Int,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = SurfaceElevated,
        border = BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$count",
                fontFamily = MonoFontFamily,
                style = MaterialTheme.typography.titleMedium,
                color = accent,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun DiscoveredSpeakerCard(
    device: DiscoveredBluetoothDevice,
    linkedOutput: AudioOutputDevice?,
    masterVolume: Float,
    isMasterMuted: Boolean,
    onPair: () -> Unit,
    onConnect: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onToggleMute: () -> Unit,
    onManageSynced: () -> Unit
) {
    val statusColor = when (device.connectionStatus) {
        BluetoothConnectionStatus.CONNECTED -> SyncMint
        BluetoothConnectionStatus.CONNECTING,
        BluetoothConnectionStatus.PAIRING -> WarmAmber
        BluetoothConnectionStatus.PAIRED -> ElectricCyan
        BluetoothConnectionStatus.AVAILABLE -> PartyMagenta
    }

    val signalColor = when (device.signalLevel) {
        SignalStrengthLevel.EXCELLENT -> SyncMint
        SignalStrengthLevel.GOOD -> ElectricCyan
        SignalStrengthLevel.FAIR -> WarmAmber
        SignalStrengthLevel.WEAK -> PartyMagenta
        SignalStrengthLevel.UNKNOWN -> if (device.connectionStatus == BluetoothConnectionStatus.CONNECTED) SyncMint else TextMuted
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = SurfaceDark,
        border = BorderStroke(
            width = 1.dp,
            color = if (device.connectionStatus == BluetoothConnectionStatus.CONNECTED) {
                SyncMint.copy(alpha = 0.55f)
            } else {
                BorderSubtle
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("discovered_device_card_${device.address}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(statusColor.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (device.connectionStatus == BluetoothConnectionStatus.CONNECTED) {
                                Icons.Default.BluetoothConnected
                            } else {
                                Icons.Default.BluetoothAudio
                            },
                            contentDescription = "${device.name} ${device.connectionStatus.label}",
                            tint = statusColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = device.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = device.transportHint,
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan
                            )
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                            Text(
                                text = device.address,
                                fontFamily = MonoFontFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Connection Status & Battery Badges
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = statusColor.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.45f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                            Text(
                                text = device.connectionStatus.label.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = statusColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (device.connectionStatus == BluetoothConnectionStatus.CONNECTED) {
                        SpeakerBatteryBadge(
                            batteryPercent = device.batteryPercent ?: linkedOutput?.batteryPercent,
                            testTag = "discovery_battery_badge_${device.address}"
                        )
                    }
                }
            }

            // Bottom Strip: Signal Strength Indicator + Action Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.semantics {
                        val desc = device.rssi?.let { "Signal strength $it dBm, ${device.signalLevel.label}" }
                            ?: "Signal status ${device.signalLevel.label}"
                        contentDescription = desc
                    }
                ) {
                    SignalStrengthBars(
                        activeBars = if (device.rssi != null) {
                            device.signalLevel.bars
                        } else if (device.connectionStatus == BluetoothConnectionStatus.CONNECTED) {
                            4
                        } else {
                            0
                        },
                        activeColor = signalColor
                    )

                    Column {
                        Text(
                            text = device.rssi?.let { "$it dBm" }
                                ?: if (device.connectionStatus == BluetoothConnectionStatus.CONNECTED) "Active Link" else "RSSI Idle",
                            fontFamily = MonoFontFamily,
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = if (device.rssi != null) {
                                "${device.signalLevel.label} Signal"
                            } else if (device.connectionStatus == BluetoothConnectionStatus.CONNECTED) {
                                "Streaming Ready"
                            } else {
                                "Scan to measure"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }

                when (device.connectionStatus) {
                    BluetoothConnectionStatus.CONNECTED -> {
                        OutlinedButton(
                            onClick = onManageSynced,
                            border = BorderStroke(1.dp, SyncMint.copy(alpha = 0.6f)),
                            modifier = Modifier.testTag("manage_connected_btn_${device.address}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = SyncMint,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Configure", color = SyncMint)
                        }
                    }
                    BluetoothConnectionStatus.PAIRING,
                    BluetoothConnectionStatus.CONNECTING -> {
                        OutlinedButton(
                            onClick = {},
                            enabled = false,
                            border = BorderStroke(1.dp, WarmAmber.copy(alpha = 0.5f))
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = WarmAmber
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(device.connectionStatus.label, color = WarmAmber)
                        }
                    }
                    BluetoothConnectionStatus.PAIRED -> {
                        Button(
                            onClick = onConnect,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricCyan,
                                contentColor = ObsidianBg
                            ),
                            modifier = Modifier.testTag("connect_bt_btn_${device.address}")
                        ) {
                            Text("Connect")
                        }
                    }
                    BluetoothConnectionStatus.AVAILABLE -> {
                        Button(
                            onClick = onPair,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PartyMagenta,
                                contentColor = TextPrimary
                            ),
                            modifier = Modifier.testTag("pair_bt_btn_${device.address}")
                        ) {
                            Text("Pair")
                        }
                    }
                }
            }

            // Independent volume slider control for connected speakers
            if (device.connectionStatus == BluetoothConnectionStatus.CONNECTED) {
                val accent = linkedOutput?.let {
                    SpeakerAccentColors[it.accentIndex % SpeakerAccentColors.size]
                } ?: SyncMint
                SpeakerVolumeControl(
                    deviceName = device.name,
                    volume = linkedOutput?.volume ?: 0.85f,
                    isMuted = linkedOutput?.isMuted ?: false,
                    masterVolume = masterVolume,
                    isMasterMuted = isMasterMuted,
                    rmsLevel = linkedOutput?.rmsLevel ?: 0f,
                    accentColor = accent,
                    label = "INDEPENDENT SPEAKER VOLUME",
                    testTagPrefix = device.address,
                    onVolumeChange = onVolumeChange,
                    onToggleMute = onToggleMute
                )
            }
        }
    }
}

@Composable
fun SignalStrengthBars(
    activeBars: Int,
    activeColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(width = 28.dp, height = 22.dp)) {
        val totalBars = 4
        val gap = 3.dp.toPx()
        val barWidth = ((size.width - gap * (totalBars - 1)) / totalBars).coerceAtLeast(3f)

        for (i in 0 until totalBars) {
            val heightFraction = (i + 1) / totalBars.toFloat()
            val barHeight = size.height * (0.28f + 0.72f * heightFraction)
            val x = i * (barWidth + gap)
            val y = size.height - barHeight
            val isLit = i < activeBars

            drawRoundRect(
                color = if (isLit) activeColor else SurfaceHighest,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )
        }
    }
}

@Composable
private fun DiscoveryEmptyStateCard(
    isScanning: Boolean,
    bluetoothEnabled: Boolean,
    onToggleScan: () -> Unit,
    onOpenBluetoothSettings: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = SurfaceDark,
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (isScanning) Icons.Default.BluetoothSearching else Icons.Default.BluetoothAudio,
                contentDescription = null,
                tint = if (isScanning) ElectricCyan else TextMuted,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = when {
                    !bluetoothEnabled -> "Bluetooth Is Turned Off"
                    isScanning -> "Listening for Nearby Speakers..."
                    else -> "No Speakers Found Yet"
                },
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Text(
                text = when {
                    !bluetoothEnabled -> "Enable Bluetooth in system settings, put your speakers in pairing mode, and scan again."
                    isScanning -> "Put your Bluetooth speakers into pairing mode so their signal strength and name appear here."
                    else -> "Tap Scan for Speakers to search nearby Classic Bluetooth and LE Audio speakers with live RSSI."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onToggleScan,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = ObsidianBg
                    )
                ) {
                    Text(if (isScanning) "Stop Scan" else "Start Scan")
                }
                OutlinedButton(
                    onClick = onOpenBluetoothSettings,
                    border = BorderStroke(1.dp, BorderSubtle)
                ) {
                    Text("BT Settings", color = TextPrimary)
                }
            }
        }
    }
}
