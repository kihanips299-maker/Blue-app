package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.audio.MultiSinkAudioEngine
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.PartyMagenta
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.SurfaceHighest
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarmAmber
import kotlin.math.log10
import kotlin.math.roundToInt

/**
 * Reusable independent volume control UI component for a connected speaker.
 * Displays both the speaker's relative fader level and the effective output
 * scaled proportionally by the global Master Volume slider.
 */
@Composable
fun SpeakerVolumeControl(
    deviceName: String,
    volume: Float,
    isMuted: Boolean,
    accentColor: Color,
    onVolumeChange: (Float) -> Unit,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier,
    masterVolume: Float = 1f,
    isMasterMuted: Boolean = false,
    isSolo: Boolean = false,
    showSoloButton: Boolean = false,
    onToggleSolo: () -> Unit = {},
    rmsLevel: Float = 0f,
    showMeter: Boolean = true,
    label: String = "SPEAKER LEVEL",
    testTagPrefix: String = deviceName
) {
    val displayedVolume = if (isMuted) 0f else volume.coerceIn(0f, 1f)
    val pct = (displayedVolume * 100f).roundToInt()
    val effectiveLinear = MultiSinkAudioEngine.computeProportionalLinearLevel(
        masterVolume = masterVolume,
        speakerVolume = volume,
        isMasterMuted = isMasterMuted,
        isSpeakerMuted = isMuted
    )
    val effectivePct = (effectiveLinear * 100f).roundToInt()

    val dbLabel = when {
        isMasterMuted -> "MASTER MUTED"
        isMuted || effectivePct == 0 -> "MUTED"
        masterVolume < 0.99f -> "OUT $effectivePct%"
        else -> {
            val db = 20.0 * log10((effectiveLinear * effectiveLinear).toDouble().coerceAtLeast(0.0001))
            String.format(java.util.Locale.US, "%.1f dB", db)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("speaker_volume_control_$testTagPrefix"),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceElevated,
        border = BorderStroke(1.dp, if (isSolo) WarmAmber.copy(alpha = 0.7f) else BorderSubtle)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = dbLabel,
                        fontFamily = MonoFontFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            isMasterMuted || isMuted -> PartyMagenta
                            masterVolume < 0.99f -> ElectricCyan
                            else -> TextMuted
                        }
                    )
                    Text(
                        text = "$pct%",
                        fontFamily = MonoFontFamily,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isMuted) PartyMagenta else accentColor,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(38.dp)
                    )
                }
            }

            if (showMeter) {
                SignalMeterBar(
                    level = if (isMuted || isMasterMuted) 0f else rmsLevel,
                    accentColor = accentColor,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onToggleMute)
                        .testTag("mute_btn_$testTagPrefix")
                        .semantics {
                            contentDescription = if (isMuted) "Unmute $deviceName" else "Mute $deviceName"
                        },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isMuted) PartyMagenta.copy(alpha = 0.22f) else SurfaceDark,
                    border = BorderStroke(1.dp, if (isMuted) PartyMagenta else BorderSubtle)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isMuted) {
                                Icons.AutoMirrored.Filled.VolumeOff
                            } else {
                                Icons.AutoMirrored.Filled.VolumeUp
                            },
                            contentDescription = null,
                            tint = if (isMuted) PartyMagenta else accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                if (showSoloButton) {
                    Surface(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onToggleSolo)
                            .testTag("solo_btn_$testTagPrefix")
                            .semantics {
                                contentDescription = if (isSolo) "Disable solo on $deviceName" else "Solo $deviceName"
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSolo) WarmAmber.copy(alpha = 0.22f) else SurfaceDark,
                        border = BorderStroke(1.dp, if (isSolo) WarmAmber else BorderSubtle)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "S",
                                fontFamily = MonoFontFamily,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (isSolo) WarmAmber else TextSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Slider(
                    value = displayedVolume,
                    onValueChange = onVolumeChange,
                    valueRange = 0f..1f,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("volume_slider_$testTagPrefix")
                        .semantics { contentDescription = "$deviceName volume slider" },
                    colors = SliderDefaults.colors(
                        thumbColor = if (isMuted) TextMuted else accentColor,
                        activeTrackColor = if (isMuted) TextMuted else accentColor,
                        inactiveTrackColor = SurfaceHighest
                    )
                )
            }
        }
    }
}
