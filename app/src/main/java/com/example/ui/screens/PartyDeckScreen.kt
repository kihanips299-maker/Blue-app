package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SpeakerGroup
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.EqualizerPreset
import com.example.model.PartyTrack
import com.example.model.PartyUiState
import com.example.model.RoomZone
import com.example.model.TrackSourceType
import com.example.ui.components.EqualizerQuickStrip
import com.example.ui.components.MasterVolumeConsole
import com.example.ui.components.PartySpectrumCanvas
import com.example.ui.components.SpeakerMixerCard
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.PartyMagenta
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.SurfaceHighest
import com.example.ui.theme.SyncMint
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarmAmber
import java.util.Locale

@Composable
fun PartyDeckScreen(
    state: PartyUiState,
    onTogglePlayPause: () -> Unit,
    onPreviousTrack: () -> Unit,
    onNextTrack: () -> Unit,
    onSelectTrack: (PartyTrack) -> Unit,
    onSeekToMs: (Long) -> Unit,
    onPickLocalAudio: () -> Unit,
    onToggleCalibrationPulse: () -> Unit,
    onMasterVolumeChange: (Float) -> Unit,
    onToggleMasterMute: () -> Unit,
    onDeviceVolumeChange: (String, Float) -> Unit,
    onToggleDeviceMute: (String) -> Unit,
    onToggleDeviceSolo: (String) -> Unit,
    onDeviceZoneChange: (String, RoomZone) -> Unit,
    onSelectEqualizerPreset: (EqualizerPreset) -> Unit,
    onNavigateToSpeakers: () -> Unit,
    onNavigateToStudio: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeEnabledSpeakers = state.outputDevices.filter { it.isConnected && it.isEnabledInParty }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Hero Soundstage Deck Card
        item {
            SoundstageHeroCard(
                state = state,
                onTogglePlayPause = onTogglePlayPause,
                onPreviousTrack = onPreviousTrack,
                onNextTrack = onNextTrack,
                onSeekToMs = onSeekToMs,
                onPickLocalAudio = onPickLocalAudio,
                onToggleCalibrationPulse = onToggleCalibrationPulse
            )
        }

        // 2. Quick Groove & Track Switcher Pills
        if (state.playlist.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "AUDIO SOURCE & GROOVES",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.playlist, key = { it.id }) { track ->
                            val selected = track.id == state.currentTrack.id && !state.calibrationPulseActive
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (selected) ElectricCyan.copy(alpha = 0.18f) else SurfaceDark,
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) ElectricCyan else BorderSubtle
                                ),
                                modifier = Modifier
                                    .clickable { onSelectTrack(track) }
                                    .testTag("track_chip_${track.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (track.sourceType == TrackSourceType.LOCAL_FILE) {
                                            Icons.Default.AudioFile
                                        } else {
                                            Icons.Default.GraphicEq
                                        },
                                        contentDescription = null,
                                        tint = if (selected) ElectricCyan else TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = track.title,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (selected) ElectricCyan else TextPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Master Volume Global Proportional Gain Console
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

        // 3b. Equalizer Presets Quick Toggle Strip
        item {
            EqualizerQuickStrip(
                activePreset = state.equalizerPreset,
                onSelectPreset = onSelectEqualizerPreset,
                onOpenStudioEq = onNavigateToStudio
            )
        }

        // 4. Multi-Room Mixer Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ACTIVE ROOM MIXER",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    Text(
                        text = "${activeEnabledSpeakers.size} synchronized output${if (activeEnabledSpeakers.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (activeEnabledSpeakers.size >= 2) SyncMint else TextSecondary
                    )
                }
                OutlinedButton(
                    onClick = onNavigateToSpeakers,
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.testTag("manage_speakers_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SpeakerGroup,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Speakers & Sync", color = TextPrimary)
                }
            }
        }

        // 5. Multi-Speaker Tip Card when fewer than 2 outputs are active
        if (activeEnabledSpeakers.size < 2) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = ElectricCyan.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BluetoothSearching,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(28.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (activeEnabledSpeakers.isEmpty()) {
                                    "No Speakers Active in Party Group"
                                } else {
                                    "Add a 2nd Speaker for Multi-Room Sync"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary
                            )
                            Text(
                                text = "Pair two Bluetooth speakers (A2DP or LE Audio) in Speakers to stream synchronized low-latency audio with independent room volume.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }

        // 6. Per-Speaker Mixer Channels
        items(activeEnabledSpeakers, key = { it.id }) { device ->
            SpeakerMixerCard(
                device = device,
                masterVolume = state.masterVolume,
                isMasterMuted = state.isMasterMuted,
                onVolumeChange = { vol -> onDeviceVolumeChange(device.id, vol) },
                onToggleMute = { onToggleDeviceMute(device.id) },
                onToggleSolo = { onToggleDeviceSolo(device.id) },
                onZoneChange = { zone -> onDeviceZoneChange(device.id, zone) },
                onOpenCalibration = onNavigateToSpeakers
            )
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun SoundstageHeroCard(
    state: PartyUiState,
    onTogglePlayPause: () -> Unit,
    onPreviousTrack: () -> Unit,
    onNextTrack: () -> Unit,
    onSeekToMs: (Long) -> Unit,
    onPickLocalAudio: () -> Unit,
    onToggleCalibrationPulse: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = SurfaceDark,
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(id = R.drawable.img_party_stage_1795605377710),
                contentDescription = "Party soundstage waveform backdrop",
                contentScale = ContentScale.Crop,
                alpha = 0.42f,
                modifier = Modifier.matchParentSize()
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                ObsidianBg.copy(alpha = 0.45f),
                                ObsidianBg.copy(alpha = 0.92f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (state.calibrationPulseActive) {
                            WarmAmber.copy(alpha = 0.22f)
                        } else {
                            PartyMagenta.copy(alpha = 0.2f)
                        },
                        border = BorderStroke(
                            1.dp,
                            if (state.calibrationPulseActive) WarmAmber else PartyMagenta.copy(alpha = 0.6f)
                        )
                    ) {
                        Text(
                            text = if (state.calibrationPulseActive) {
                                "SYNC PULSE MODE"
                            } else if (state.currentTrack.sourceType == TrackSourceType.LOCAL_FILE) {
                                "LOCAL FILE • 44.1kHz"
                            } else {
                                "LOW-LATENCY SYNTH • ${state.currentTrack.bpm} BPM"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (state.calibrationPulseActive) WarmAmber else PartyMagenta,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    Text(
                        text = formatDuration(state.positionMs),
                        fontFamily = MonoFontFamily,
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = state.currentTrack.title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = state.currentTrack.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Real-time audio spectrum Canvas
                PartySpectrumCanvas(
                    bands = state.diagnostics.spectrumBands,
                    isPlaying = state.isPlaying,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                )

                // Seek bar for local audio files
                if (state.currentTrack.sourceType == TrackSourceType.LOCAL_FILE && state.currentTrack.durationMs > 0L) {
                    Column {
                        Slider(
                            value = (state.positionMs.toFloat() / state.currentTrack.durationMs.toFloat()).coerceIn(0f, 1f),
                            onValueChange = { frac ->
                                onSeekToMs((frac * state.currentTrack.durationMs).toLong())
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = ElectricCyan,
                                activeTrackColor = ElectricCyan,
                                inactiveTrackColor = SurfaceHighest
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatDuration(state.positionMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                            Text(
                                text = formatDuration(state.currentTrack.durationMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }
                }

                // Transport Controls Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = onPickLocalAudio,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = SurfaceElevated,
                            contentColor = TextPrimary
                        ),
                        modifier = Modifier.testTag("load_track_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AudioFile,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Load Audio")
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = onPreviousTrack,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("prev_track_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Previous track",
                                tint = TextPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Button(
                            onClick = onTogglePlayPause,
                            shape = CircleShape,
                            contentPadding = PaddingValues(0.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (state.isPlaying) PartyMagenta else ElectricCyan,
                                contentColor = ObsidianBg
                            ),
                            modifier = Modifier
                                .size(60.dp)
                                .testTag("play_pause_button")
                        ) {
                            Icon(
                                imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (state.isPlaying) "Pause party playback" else "Start party playback",
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        IconButton(
                            onClick = onNextTrack,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("next_track_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next track",
                                tint = TextPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (state.calibrationPulseActive) {
                            WarmAmber.copy(alpha = 0.24f)
                        } else {
                            SurfaceElevated
                        },
                        border = BorderStroke(
                            1.dp,
                            if (state.calibrationPulseActive) WarmAmber else BorderSubtle
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(onClick = onToggleCalibrationPulse)
                            .testTag("calibration_pulse_button")
                    ) {
                        Text(
                            text = if (state.calibrationPulseActive) "Stop Pulse" else "Sync Pulse",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (state.calibrationPulseActive) WarmAmber else TextPrimary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
