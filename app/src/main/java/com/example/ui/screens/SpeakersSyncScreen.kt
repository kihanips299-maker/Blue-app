package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SettingsBluetooth
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AudioOutputDevice
import com.example.model.DiscoveredBluetoothDevice
import com.example.model.PartyUiState
import com.example.model.RoomZone
import com.example.model.SpeakerTransportType
import com.example.ui.components.MasterVolumeConsole
import com.example.ui.components.SpeakerBatteryBadge
import com.example.ui.components.SpeakerVolumeControl
import com.example.ui.components.transportIcon
import com.example.ui.theme.BorderSubtle
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
import kotlin.math.roundToInt

@Composable
fun SpeakersSyncScreen(
    state: PartyUiState,
    onRequestBluetoothPermissions: () -> Unit,
    onToggleScan: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onRefreshDevices: () -> Unit,
    onPairDevice: (String) -> Unit,
    onToggleDeviceInParty: (String) -> Unit,
    onMasterVolumeChange: (Float) -> Unit,
    onToggleMasterMute: () -> Unit,
    onDeviceVolumeChange: (String, Float) -> Unit,
    onToggleDeviceMute: (String) -> Unit,
    onLatencyOffsetChange: (String, Int) -> Unit,
    onZoneChange: (String, RoomZone) -> Unit,
    onToggleCalibrationPulse: () -> Unit,
    modifier: Modifier = Modifier
) {
    val caps = state.bluetoothCapabilities

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Bluetooth Permission Banner if missing
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
                                text = "Bluetooth Permission Needed",
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
                            modifier = Modifier.testTag("grant_bluetooth_button")
                        ) {
                            Text(stringResource(R.string.action_grant_bt))
                        }
                    }
                }
            }
        }

        // 2. Hardware Capability & Action Header
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = SurfaceDark,
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "MULTI-SPEAKER ROUTING",
                                style = MaterialTheme.typography.labelMedium,
                                color = ElectricCyan
                            )
                            Text(
                                text = "Pair & calibrate speakers",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SurfaceElevated,
                            border = BorderStroke(1.dp, BorderSubtle),
                            modifier = Modifier
                                .clickable(onClick = onRefreshDevices)
                                .testTag("refresh_devices_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh audio outputs",
                                tint = ElectricCyan,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CapabilityChip(
                            label = if (caps.leAudioSupported) "LE Audio Ready" else "Classic A2DP",
                            active = caps.leAudioSupported,
                            modifier = Modifier.weight(1f)
                        )
                        CapabilityChip(
                            label = if (caps.auracastBroadcastSupported) "Auracast TX" else "Dual-Stream Engine",
                            active = caps.auracastBroadcastSupported,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onToggleScan,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (state.isScanning) PartyMagenta else ElectricCyan,
                                contentColor = ObsidianBg
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("scan_speakers_button")
                        ) {
                            if (state.isScanning) {
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
                                Text(stringResource(R.string.action_scan_speakers))
                            }
                        }

                        OutlinedButton(
                            onClick = onOpenBluetoothSettings,
                            border = BorderStroke(1.dp, BorderSubtle),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("open_bt_settings_button")
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

        // 3. Calibration Pulse Helper Strip
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SurfaceElevated,
                border = BorderStroke(
                    1.dp,
                    if (state.calibrationPulseActive) WarmAmber else BorderSubtle
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Acoustic Delay Calibration",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        Text(
                            text = "Play the precision click pulse and nudge ±ms until speakers lock.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Button(
                        onClick = onToggleCalibrationPulse,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.calibrationPulseActive) WarmAmber else SurfaceHighest,
                            contentColor = if (state.calibrationPulseActive) ObsidianBg else TextPrimary
                        ),
                        modifier = Modifier.testTag("speakers_calibration_pulse_btn")
                    ) {
                        Text(if (state.calibrationPulseActive) "Stop Pulse" else "Test Pulse")
                    }
                }
            }
        }

        // 3.5 Global Master Volume Control
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

        // 4. Connected & Paired Speakers
        item {
            Text(
                text = "CONNECTED & PAIRED OUTPUTS (${state.outputDevices.size})",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
        }

        if (state.outputDevices.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = SurfaceDark,
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bluetooth,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "No Audio Outputs Detected",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "Tap Refresh or connect Bluetooth speakers in Android Settings.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }
        } else {
            items(state.outputDevices, key = { it.id }) { device ->
                SpeakerCalibrationCard(
                    device = device,
                    masterVolume = state.masterVolume,
                    isMasterMuted = state.isMasterMuted,
                    onToggleEnabled = { onToggleDeviceInParty(device.id) },
                    onVolumeChange = { vol -> onDeviceVolumeChange(device.id, vol) },
                    onToggleMute = { onToggleDeviceMute(device.id) },
                    onOffsetChange = { offset -> onLatencyOffsetChange(device.id, offset) },
                    onZoneChange = { zone -> onZoneChange(device.id, zone) },
                    onOpenBluetoothSettings = onOpenBluetoothSettings
                )
            }
        }

        // 5. Discovered Nearby Bluetooth Speakers
        if (state.discoveredDevices.isNotEmpty() || state.isScanning) {
            item {
                Text(
                    text = "NEARBY BLUETOOTH DEVICES",
                    style = MaterialTheme.typography.labelMedium,
                    color = ElectricCyan
                )
            }
            items(state.discoveredDevices, key = { it.address }) { bt ->
                DiscoveredDeviceRow(
                    device = bt,
                    onPair = { onPairDevice(bt.address) }
                )
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun SpeakerCalibrationCard(
    device: AudioOutputDevice,
    masterVolume: Float,
    isMasterMuted: Boolean,
    onToggleEnabled: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onToggleMute: () -> Unit,
    onOffsetChange: (Int) -> Unit,
    onZoneChange: (RoomZone) -> Unit,
    onOpenBluetoothSettings: () -> Unit
) {
    val accent = SpeakerAccentColors[device.accentIndex % SpeakerAccentColors.size]

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = SurfaceDark,
        border = BorderStroke(
            1.dp,
            if (device.isConnected && device.isEnabledInParty) accent.copy(alpha = 0.65f) else BorderSubtle
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("speaker_config_card_${device.id}")
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
                    Icon(
                        imageVector = transportIcon(device.transportType),
                        contentDescription = null,
                        tint = if (device.isConnected) accent else TextMuted,
                        modifier = Modifier.size(26.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = device.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val connLabel = if (device.isConnected) {
                            "CONNECTED • ${device.transportType.label}"
                        } else {
                            "PAIRED • OFFLINE"
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = connLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (device.isConnected) SyncMint else TextMuted
                            )
                            val isBt = device.transportType == SpeakerTransportType.BLUETOOTH_A2DP ||
                                device.transportType == SpeakerTransportType.BLUETOOTH_LE_AUDIO ||
                                device.transportType == SpeakerTransportType.BLUETOOTH_LE_BROADCAST
                            if (device.isConnected && isBt) {
                                SpeakerBatteryBadge(
                                    batteryPercent = device.batteryPercent,
                                    testTag = "sync_battery_badge_${device.id}"
                                )
                            }
                        }
                    }
                }

                if (device.isConnected) {
                    Switch(
                        checked = device.isEnabledInParty,
                        onCheckedChange = { onToggleEnabled() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ObsidianBg,
                            checkedTrackColor = accent
                        ),
                        modifier = Modifier.testTag("enable_speaker_switch_${device.id}")
                    )
                } else {
                    OutlinedButton(
                        onClick = onOpenBluetoothSettings,
                        border = BorderStroke(1.dp, BorderSubtle)
                    ) {
                        Text("Connect", color = ElectricCyan)
                    }
                }
            }

            if (device.isConnected) {
                // Room Zone Quick Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RoomZone.entries.take(4).forEach { zone ->
                        val selected = device.roomZone == zone
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) accent.copy(alpha = 0.2f) else SurfaceElevated,
                            border = BorderStroke(1.dp, if (selected) accent else BorderSubtle),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onZoneChange(zone) }
                        ) {
                            Text(
                                text = zone.displayName.substringBefore(" "),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) accent else TextSecondary,
                                maxLines = 1,
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp)
                            )
                        }
                    }
                }

                SpeakerVolumeControl(
                    deviceName = device.name,
                    volume = device.volume,
                    isMuted = device.isMuted,
                    masterVolume = masterVolume,
                    isMasterMuted = isMasterMuted,
                    rmsLevel = device.rmsLevel,
                    accentColor = accent,
                    label = "INDEPENDENT SPEAKER VOLUME",
                    testTagPrefix = "sync_${device.id}",
                    onVolumeChange = onVolumeChange,
                    onToggleMute = onToggleMute
                )

                // Latency Offset Calibration Slider + Precision Steppers
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "SYNC PHASE OFFSET",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary
                            )
                        }
                        val sign = if (device.latencyOffsetMs > 0) "+" else ""
                        Text(
                            text = "${sign}${device.latencyOffsetMs} ms",
                            fontFamily = MonoFontFamily,
                            style = MaterialTheme.typography.labelLarge,
                            color = ElectricCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Slider(
                        value = device.latencyOffsetMs.toFloat(),
                        onValueChange = { onOffsetChange(it.roundToInt()) },
                        valueRange = -150f..150f,
                        colors = SliderDefaults.colors(
                            thumbColor = ElectricCyan,
                            activeTrackColor = ElectricCyan,
                            inactiveTrackColor = SurfaceHighest
                        ),
                        modifier = Modifier.testTag("latency_slider_${device.id}")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OffsetStepButton("-10ms", { onOffsetChange(device.latencyOffsetMs - 10) }, Modifier.weight(1f))
                        OffsetStepButton("-1ms", { onOffsetChange(device.latencyOffsetMs - 1) }, Modifier.weight(1f))
                        OffsetStepButton("0ms", { onOffsetChange(0) }, Modifier.weight(1f))
                        OffsetStepButton("+1ms", { onOffsetChange(device.latencyOffsetMs + 1) }, Modifier.weight(1f))
                        OffsetStepButton("+10ms", { onOffsetChange(device.latencyOffsetMs + 10) }, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun OffsetStepButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = SurfaceElevated,
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                fontFamily = MonoFontFamily,
                style = MaterialTheme.typography.labelSmall,
                color = TextPrimary
            )
        }
    }
}

@Composable
private fun CapabilityChip(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (active) SyncMint.copy(alpha = 0.14f) else SurfaceElevated,
        border = BorderStroke(1.dp, if (active) SyncMint.copy(alpha = 0.5f) else BorderSubtle)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = if (active) SyncMint else ElectricCyan,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DiscoveredDeviceRow(
    device: DiscoveredBluetoothDevice,
    onPair: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SurfaceDark,
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val rssiText = device.rssi?.let { "${it} dBm" } ?: "Nearby"
                Text(
                    text = "${device.address} • $rssiText",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
            Button(
                onClick = onPair,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricCyan,
                    contentColor = ObsidianBg
                )
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Pair")
            }
        }
    }
}
