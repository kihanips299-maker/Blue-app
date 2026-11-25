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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.EqualizerPreset
import com.example.model.PartyPreset
import com.example.model.PartyUiState
import com.example.ui.components.EqualizerConsoleCard
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.PartyMagenta
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.SyncMint
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun StudioPresetsScreen(
    state: PartyUiState,
    onSelectEqualizerPreset: (EqualizerPreset) -> Unit,
    onToggleLowLatencyMode: () -> Unit,
    onToggleAutoPauseOnDisconnect: () -> Unit,
    onApplyPreset: (PartyPreset) -> Unit,
    onSavePreset: (String) -> Unit,
    onDeletePreset: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showSaveDialog by remember { mutableStateOf(false) }
    var presetNameInput by remember { mutableStateOf("") }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            containerColor = SurfaceElevated,
            title = { Text("Save Room Preset", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Saves master volume, low-latency mode, and every speaker's room volume & ms delay offset.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = presetNameInput,
                        onValueChange = { presetNameInput = it },
                        label = { Text("Preset name (e.g. Friday Rooftop)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("preset_name_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (presetNameInput.isNotBlank()) {
                            onSavePreset(presetNameInput)
                            presetNameInput = ""
                            showSaveDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = ObsidianBg
                    ),
                    modifier = Modifier.testTag("confirm_save_preset_button")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 0. 5-Band DSP Equalizer Console
        item {
            EqualizerConsoleCard(
                activePreset = state.equalizerPreset,
                onSelectPreset = onSelectEqualizerPreset
            )
        }

        // 1. Saved Room Presets Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "PARTY ROOM PRESETS",
                        style = MaterialTheme.typography.labelMedium,
                        color = ElectricCyan
                    )
                    Text(
                        text = "One-tap scene recall",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                }
                Button(
                    onClick = { showSaveDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = ObsidianBg
                    ),
                    modifier = Modifier.testTag("save_preset_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_save_preset))
                }
            }
        }

        // 2. Presets List
        items(state.presets, key = { it.id }) { preset ->
            val isActive = preset.id == state.activePresetId
            val isBuiltIn = preset.id.startsWith("preset_house_party") ||
                preset.id.startsWith("preset_chill_lounge") ||
                preset.id.startsWith("preset_tight_sync")

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = SurfaceDark,
                border = BorderStroke(1.dp, if (isActive) ElectricCyan else BorderSubtle),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onApplyPreset(preset) }
                    .testTag("preset_card_${preset.id}")
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isActive) ElectricCyan.copy(alpha = 0.2f) else SurfaceElevated,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = if (isActive) Icons.Default.Check else Icons.Default.Bolt,
                                contentDescription = null,
                                tint = if (isActive) ElectricCyan else PartyMagenta,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                        Column {
                            Text(
                                text = preset.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            val eqLabel = "${preset.equalizerPreset.title} EQ"
                            val countLabel = if (preset.deviceConfigs.isEmpty()) {
                                "Master ${(preset.masterVolume * 100).roundToInt()}% • $eqLabel"
                            } else {
                                "Master ${(preset.masterVolume * 100).roundToInt()}% • $eqLabel • ${preset.deviceConfigs.size} speakers"
                            }
                            Text(
                                text = countLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }

                    if (!isBuiltIn) {
                        IconButton(
                            onClick = { onDeletePreset(preset.id) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete preset ${preset.name}",
                                tint = TextMuted
                            )
                        }
                    }
                }
            }
        }

        // 3. Engine Settings & Low Latency Controls
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = SurfaceDark,
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = ElectricCyan
                        )
                        Text(
                            text = "LOW-LATENCY ENGINE SETTINGS",
                            style = MaterialTheme.typography.labelMedium,
                            color = ElectricCyan
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Low-Latency AudioTrack Mode",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary
                            )
                            Text(
                                text = "Uses PERFORMANCE_MODE_LOW_LATENCY with minimal hardware buffer frames.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = state.lowLatencyMode,
                            onCheckedChange = { onToggleLowLatencyMode() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ObsidianBg,
                                checkedTrackColor = ElectricCyan
                            ),
                            modifier = Modifier.testTag("low_latency_switch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-Pause on Speaker Drop",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary
                            )
                            Text(
                                text = "Prevents sudden phone-speaker blasting if a Bluetooth speaker disconnects.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = state.autoPauseOnDisconnect,
                            onCheckedChange = { onToggleAutoPauseOnDisconnect() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ObsidianBg,
                                checkedTrackColor = SyncMint
                            ),
                            modifier = Modifier.testTag("auto_pause_switch")
                        )
                    }
                }
            }
        }

        // 4. Live Engine Telemetry Readout
        item {
            val diag = state.diagnostics
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = SurfaceElevated,
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "REAL-TIME SYNC TELEMETRY",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TelemetryStatBox(
                            label = "SAMPLE RATE",
                            value = "${diag.sampleRateHz} Hz",
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryStatBox(
                            label = "BLOCK SIZE",
                            value = "${diag.bufferFrames} smp",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TelemetryStatBox(
                            label = "PERF MODE",
                            value = diag.performanceModeLabel,
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryStatBox(
                            label = "SYNC DRIFT",
                            value = String.format(Locale.US, "±%.1f ms", diag.maxSyncDriftMs),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun TelemetryStatBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = SurfaceDark,
        border = BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
            Text(
                text = value,
                fontFamily = MonoFontFamily,
                style = MaterialTheme.typography.titleSmall,
                color = ElectricCyan,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
