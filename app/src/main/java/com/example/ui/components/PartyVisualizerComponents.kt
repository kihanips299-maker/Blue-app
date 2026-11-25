package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Battery1Bar
import androidx.compose.material.icons.filled.Battery2Bar
import androidx.compose.material.icons.filled.Battery3Bar
import androidx.compose.material.icons.filled.Battery4Bar
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.Battery6Bar
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.BatteryUnknown
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.AudioOutputDevice
import com.example.model.RoomZone
import com.example.model.SpeakerTransportType
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
fun PartySpectrumCanvas(
    bands: List<Float>,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val animatedBands = bands.mapIndexed { idx, value ->
        val animated by animateFloatAsState(
            targetValue = if (isPlaying) value.coerceIn(0.06f, 1f) else 0.08f + (idx % 3) * 0.03f,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "band_$idx"
        )
        animated
    }

    Canvas(modifier = modifier) {
        val count = animatedBands.size.coerceAtLeast(1)
        val gap = size.width * 0.018f
        val barWidth = ((size.width - gap * (count - 1)) / count).coerceAtLeast(4f)

        for (i in 0 until count) {
            val fraction = animatedBands[i]
            val barHeight = (size.height * fraction).coerceAtLeast(6f)
            val x = i * (barWidth + gap)
            val y = size.height - barHeight
            val ratio = i.toFloat() / (count - 1).coerceAtLeast(1)
            val topColor = lerpColor(ElectricCyan, PartyMagenta, ratio)
            val bottomColor = topColor.copy(alpha = 0.25f)

            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(topColor, bottomColor),
                    startY = y,
                    endY = size.height
                ),
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

@Composable
fun SignalMeterBar(
    level: Float,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val animatedLevel by animateFloatAsState(
        targetValue = level.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "vu_level"
    )
    Canvas(modifier = modifier.height(6.dp)) {
        val segments = 14
        val gap = 3.dp.toPx()
        val segWidth = ((size.width - gap * (segments - 1)) / segments).coerceAtLeast(2f)
        val litCount = (animatedLevel * segments).roundToInt()

        for (i in 0 until segments) {
            val x = i * (segWidth + gap)
            val segColor = when {
                i >= litCount -> SurfaceHighest
                i >= segments - 2 -> PartyMagenta
                i >= segments - 4 -> WarmAmber
                else -> accentColor
            }
            drawRoundRect(
                color = segColor,
                topLeft = Offset(x, 0f),
                size = Size(segWidth, size.height),
                cornerRadius = CornerRadius(3f, 3f)
            )
        }
    }
}

@Composable
fun SyncStatusPill(
    activeSinkCount: Int,
    isPlaying: Boolean,
    maxDriftMs: Float,
    modifier: Modifier = Modifier
) {
    val statusColor by animateColorAsState(
        targetValue = when {
            activeSinkCount >= 2 && isPlaying -> SyncMint
            activeSinkCount >= 1 && isPlaying -> ElectricCyan
            activeSinkCount >= 2 -> ElectricCyan
            else -> TextSecondary
        },
        label = "status_color"
    )

    val label = when {
        activeSinkCount >= 2 && isPlaying -> "SYNC LOCKED • $activeSinkCount SINKS"
        activeSinkCount == 1 && isPlaying -> "LIVE • 1 SINK"
        activeSinkCount >= 2 -> "READY • $activeSinkCount SINKS"
        activeSinkCount == 1 -> "1 SINK READY"
        else -> "NO SINKS"
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = statusColor.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = statusColor,
                fontWeight = FontWeight.Bold
            )
            if (isPlaying && activeSinkCount >= 2) {
                Text(
                    text = "±${maxDriftMs.roundToInt()}ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
fun SpeakerMixerCard(
    device: AudioOutputDevice,
    masterVolume: Float,
    isMasterMuted: Boolean,
    onVolumeChange: (Float) -> Unit,
    onToggleMute: () -> Unit,
    onToggleSolo: () -> Unit,
    onZoneChange: (RoomZone) -> Unit,
    onOpenCalibration: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = SpeakerAccentColors[device.accentIndex % SpeakerAccentColors.size]
    var showZoneMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("speaker_mixer_card_${device.id}"),
        shape = RoundedCornerShape(20.dp),
        color = SurfaceDark,
        border = BorderStroke(
            width = 1.dp,
            color = if (device.isSolo) accent else BorderSubtle
        )
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
                            .background(accent.copy(alpha = 0.16f))
                            .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = transportIcon(device.transportType),
                            contentDescription = "${device.name} ${device.transportType.label}",
                            tint = accent,
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
                            Box {
                                Text(
                                    text = device.roomZone.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accent,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(accent.copy(alpha = 0.12f))
                                        .clickable { showZoneMenu = true }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                                DropdownMenu(
                                    expanded = showZoneMenu,
                                    onDismissRequest = { showZoneMenu = false }
                                ) {
                                    RoomZone.entries.forEach { zone ->
                                        DropdownMenuItem(
                                            text = { Text(zone.displayName) },
                                            onClick = {
                                                onZoneChange(zone)
                                                showZoneMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                            Text(
                                text = device.transportType.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                            val isBt = device.transportType == SpeakerTransportType.BLUETOOTH_A2DP ||
                                device.transportType == SpeakerTransportType.BLUETOOTH_LE_AUDIO ||
                                device.transportType == SpeakerTransportType.BLUETOOTH_LE_BROADCAST
                            if (device.isConnected && isBt) {
                                SpeakerBatteryBadge(
                                    batteryPercent = device.batteryPercent,
                                    testTag = "battery_badge_${device.id}"
                                )
                            }
                        }
                    }
                }

                // Latency Offset Pill (opens calibration)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SurfaceElevated,
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier
                        .clickable { onOpenCalibration() }
                        .testTag("latency_pill_${device.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Calibrate speaker latency",
                            tint = ElectricCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        val sign = if (device.latencyOffsetMs > 0) "+" else ""
                        Text(
                            text = "${sign}${device.latencyOffsetMs}ms",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary
                        )
                    }
                }
            }

            // Independent Speaker Volume Control Component
            SpeakerVolumeControl(
                deviceName = device.name,
                volume = device.volume,
                isMuted = device.isMuted,
                masterVolume = masterVolume,
                isMasterMuted = isMasterMuted,
                isSolo = device.isSolo,
                showSoloButton = true,
                rmsLevel = device.rmsLevel,
                accentColor = accent,
                label = "CHANNEL GAIN",
                testTagPrefix = device.id,
                onVolumeChange = onVolumeChange,
                onToggleMute = onToggleMute,
                onToggleSolo = onToggleSolo
            )
        }
    }
}

@Composable
fun MasterVolumeConsole(
    masterVolume: Float,
    isMasterMuted: Boolean,
    onVolumeChange: (Float) -> Unit,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier,
    connectedSpeakers: List<AudioOutputDevice> = emptyList(),
    masterRms: Float = 0f
) {
    val activeConnected = connectedSpeakers.filter { it.isConnected && it.isEnabledInParty }
    val btCount = activeConnected.count {
        it.transportType == SpeakerTransportType.BLUETOOTH_A2DP ||
            it.transportType == SpeakerTransportType.BLUETOOTH_LE_AUDIO ||
            it.transportType == SpeakerTransportType.BLUETOOTH_LE_BROADCAST
    }
    val pct = if (isMasterMuted) 0 else (masterVolume * 100).roundToInt()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("master_volume_console"),
        shape = RoundedCornerShape(20.dp),
        color = SurfaceElevated,
        border = BorderStroke(
            1.dp,
            if (isMasterMuted) PartyMagenta.copy(alpha = 0.55f) else ElectricCyan.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onToggleMute)
                        .testTag("master_mute_button"),
                    shape = RoundedCornerShape(14.dp),
                    color = if (isMasterMuted) PartyMagenta.copy(alpha = 0.22f) else ElectricCyan.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, if (isMasterMuted) PartyMagenta else ElectricCyan.copy(alpha = 0.5f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isMasterMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = if (isMasterMuted) "Unmute master volume" else "Mute master volume",
                            tint = if (isMasterMuted) PartyMagenta else ElectricCyan
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "MASTER VOLUME",
                                style = MaterialTheme.typography.labelLarge,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            val scopeLabel = when {
                                btCount > 0 -> "Global gain • Scales $btCount Bluetooth speaker${if (btCount == 1) "" else "s"}"
                                activeConnected.isNotEmpty() -> "Global gain • Scales ${activeConnected.size} active output${if (activeConnected.size == 1) "" else "s"}"
                                else -> "Global gain • Proportional multi-speaker control"
                            }
                            Text(
                                text = scopeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                        Text(
                            text = if (isMasterMuted) "MUTED" else "$pct%",
                            fontFamily = MonoFontFamily,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isMasterMuted) PartyMagenta else ElectricCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            SignalMeterBar(
                level = if (isMasterMuted) 0f else masterRms,
                accentColor = ElectricCyan,
                modifier = Modifier.fillMaxWidth()
            )

            Slider(
                value = if (isMasterMuted) 0f else masterVolume,
                onValueChange = onVolumeChange,
                valueRange = 0f..1f,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("master_volume_slider")
                    .semantics { contentDescription = "Master Volume global proportional gain slider" },
                colors = SliderDefaults.colors(
                    thumbColor = if (isMasterMuted) PartyMagenta else ElectricCyan,
                    activeTrackColor = if (isMasterMuted) PartyMagenta else ElectricCyan,
                    inactiveTrackColor = ObsidianBg
                )
            )

            // Live proportional output preview for each active connected speaker
            if (activeConnected.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    activeConnected.take(3).forEach { spk ->
                        val accent = SpeakerAccentColors[spk.accentIndex % SpeakerAccentColors.size]
                        val effectivePct = if (isMasterMuted || spk.isMuted) {
                            0
                        } else {
                            (masterVolume * spk.volume * 100f).roundToInt()
                        }
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            color = SurfaceDark,
                            border = BorderStroke(1.dp, BorderSubtle)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = spk.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "$effectivePct%",
                                    fontFamily = MonoFontFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (effectivePct == 0) PartyMagenta else accent,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun transportIcon(type: SpeakerTransportType): ImageVector {
    return when (type) {
        SpeakerTransportType.BLUETOOTH_A2DP,
        SpeakerTransportType.BLUETOOTH_LE_AUDIO,
        SpeakerTransportType.BLUETOOTH_LE_BROADCAST -> Icons.Default.BluetoothAudio
        SpeakerTransportType.WIRED_AUDIO -> Icons.Default.Headphones
        SpeakerTransportType.USB_AUDIO -> Icons.Default.Usb
        SpeakerTransportType.BUILT_IN_SPEAKER -> Icons.Default.Speaker
    }
}

@Composable
fun SpeakerBatteryBadge(
    batteryPercent: Int?,
    modifier: Modifier = Modifier,
    testTag: String = "speaker_battery_badge"
) {
    val pct = batteryPercent?.coerceIn(0, 100)
    val badgeColor = when {
        pct == null -> TextSecondary
        pct <= 19 -> com.example.ui.theme.CoralError
        pct <= 49 -> WarmAmber
        else -> SyncMint
    }
    val batteryIcon = when {
        pct == null -> Icons.Default.BatteryUnknown
        pct <= 15 -> Icons.Default.BatteryAlert
        pct <= 28 -> Icons.Default.Battery1Bar
        pct <= 42 -> Icons.Default.Battery2Bar
        pct <= 56 -> Icons.Default.Battery3Bar
        pct <= 70 -> Icons.Default.Battery4Bar
        pct <= 84 -> Icons.Default.Battery5Bar
        pct <= 95 -> Icons.Default.Battery6Bar
        else -> Icons.Default.BatteryFull
    }
    val label = pct?.let { "$it%" } ?: "--%"
    val desc = pct?.let { "Speaker battery $it percent" } ?: "Speaker battery level unavailable"

    Surface(
        modifier = modifier
            .testTag(testTag)
            .semantics { contentDescription = desc },
        shape = RoundedCornerShape(8.dp),
        color = badgeColor.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = batteryIcon,
                contentDescription = null,
                tint = badgeColor,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = label,
                fontFamily = MonoFontFamily,
                style = MaterialTheme.typography.labelSmall,
                color = badgeColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun lerpColor(start: Color, stop: Color, fraction: Float): Color {
    val t = fraction.coerceIn(0f, 1f)
    return Color(
        red = start.red + (stop.red - start.red) * t,
        green = start.green + (stop.green - start.green) * t,
        blue = start.blue + (stop.blue - start.blue) * t,
        alpha = 1f
    )
}
