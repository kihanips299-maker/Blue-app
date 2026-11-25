package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.audio.EqualizerDspProcessor
import com.example.model.EqualizerPreset
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
import kotlin.math.roundToInt

@Composable
fun EqualizerQuickStrip(
    activePreset: EqualizerPreset,
    onSelectPreset: (EqualizerPreset) -> Unit,
    onOpenStudioEq: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("equalizer_quick_strip"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                    imageVector = Icons.Default.Equalizer,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "EQUALIZER PRESETS",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }

            Surface(
                shape = RoundedCornerShape(50),
                color = presetAccentColor(activePreset).copy(alpha = 0.14f),
                border = BorderStroke(1.dp, presetAccentColor(activePreset).copy(alpha = 0.45f)),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onOpenStudioEq)
                    .testTag("eq_active_badge_button")
            ) {
                Text(
                    text = "${activePreset.title} • EQ Curve",
                    style = MaterialTheme.typography.labelSmall,
                    color = presetAccentColor(activePreset),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(end = 4.dp)
        ) {
            items(EqualizerPreset.entries, key = { it.id }) { preset ->
                val selected = preset == activePreset
                val accent = presetAccentColor(preset)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected) accent.copy(alpha = 0.18f) else SurfaceDark,
                    border = BorderStroke(1.dp, if (selected) accent else BorderSubtle),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelectPreset(preset) }
                        .testTag("eq_quick_chip_${preset.id}")
                        .semantics {
                            contentDescription = "${preset.title} equalizer preset${if (selected) ", active" else ""}"
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = presetIcon(preset),
                            contentDescription = null,
                            tint = if (selected) accent else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = preset.title,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) accent else TextPrimary,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EqualizerConsoleCard(
    activePreset: EqualizerPreset,
    onSelectPreset: (EqualizerPreset) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeAccent by animateColorAsState(
        targetValue = presetAccentColor(activePreset),
        label = "eq_accent"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("equalizer_console_card"),
        shape = RoundedCornerShape(22.dp),
        color = SurfaceDark,
        border = BorderStroke(1.dp, activeAccent.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Equalizer,
                            contentDescription = null,
                            tint = activeAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "5-BAND DSP EQUALIZER",
                            style = MaterialTheme.typography.labelMedium,
                            color = activeAccent
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${activePreset.title} Mode",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Text(
                        text = activePreset.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = activeAccent.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, activeAccent.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "ALL SPEAKERS",
                        style = MaterialTheme.typography.labelSmall,
                        color = activeAccent,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            // Live Frequency Response Curve Canvas
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = ObsidianBg,
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    EqualizerResponseCanvas(
                        preset = activePreset,
                        accentColor = activeAccent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(104.dp)
                    )

                    // 5-Band Gain Readouts
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        EqualizerPreset.BAND_LABELS.forEachIndexed { idx, freqLabel ->
                            val gainDb = activePreset.bandGainsDb.getOrElse(idx) { 0f }
                            val animatedGain by animateFloatAsState(
                                targetValue = gainDb,
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                label = "band_gain_$idx"
                            )
                            val roundedDb = animatedGain.roundToInt()
                            val gainText = when {
                                roundedDb > 0 -> "+${roundedDb}dB"
                                else -> "${roundedDb}dB"
                            }
                            val gainColor = when {
                                roundedDb > 0 -> activeAccent
                                roundedDb < 0 -> PartyMagenta
                                else -> TextSecondary
                            }

                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                color = SurfaceElevated,
                                border = BorderStroke(1.dp, BorderSubtle)
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = gainText,
                                        fontFamily = MonoFontFamily,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = gainColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = freqLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2-Column Preset Toggle Grid
            val rows = remember { EqualizerPreset.entries.chunked(2) }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rows.forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        pair.forEach { preset ->
                            EqualizerPresetOptionCard(
                                preset = preset,
                                isSelected = preset == activePreset,
                                onClick = { onSelectPreset(preset) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (pair.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EqualizerPresetOptionCard(
    preset: EqualizerPreset,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = presetAccentColor(preset)
    Surface(
        modifier = modifier
            .defaultMinSize(minHeight = 64.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("eq_preset_card_${preset.id}")
            .semantics {
                contentDescription = "${preset.title} equalizer mode: ${preset.subtitle}"
            },
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) accent.copy(alpha = 0.16f) else SurfaceElevated,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) accent else BorderSubtle
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) accent.copy(alpha = 0.22f) else SurfaceHighest),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = presetIcon(preset),
                    contentDescription = null,
                    tint = if (isSelected) accent else TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = preset.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isSelected) accent else TextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = preset.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun EqualizerResponseCanvas(
    preset: EqualizerPreset,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val targetCurve = remember(preset) {
        EqualizerDspProcessor.computeFrequencyResponseDb(preset, pointCount = 36)
    }

    val animatedCurve = targetCurve.mapIndexed { idx, targetDb ->
        val animated by animateFloatAsState(
            targetValue = targetDb,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "eq_curve_$idx"
        )
        animated
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val padY = 10.dp.toPx()
        val usableH = (h - padY * 2f).coerceAtLeast(10f)
        val zeroY = padY + usableH * 0.5f

        fun dbToY(db: Float): Float {
            val norm = (db.coerceIn(-12f, 12f) / 12f) // -1..+1
            return zeroY - norm * (usableH * 0.5f)
        }

        // +6dB, 0dB, -6dB reference lines
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
        drawLine(
            color = BorderSubtle.copy(alpha = 0.55f),
            start = Offset(0f, dbToY(6f)),
            end = Offset(w, dbToY(6f)),
            strokeWidth = 1.dp.toPx(),
            pathEffect = dashEffect
        )
        drawLine(
            color = TextSecondary.copy(alpha = 0.35f),
            start = Offset(0f, zeroY),
            end = Offset(w, zeroY),
            strokeWidth = 1.2.dp.toPx()
        )
        drawLine(
            color = BorderSubtle.copy(alpha = 0.55f),
            start = Offset(0f, dbToY(-6f)),
            end = Offset(w, dbToY(-6f)),
            strokeWidth = 1.dp.toPx(),
            pathEffect = dashEffect
        )

        val count = animatedCurve.size
        if (count < 2) return@Canvas

        val strokePath = Path()
        val fillPath = Path()
        val stepX = w / (count - 1).toFloat()

        animatedCurve.forEachIndexed { idx, db ->
            val x = idx * stepX
            val y = dbToY(db)
            if (idx == 0) {
                strokePath.moveTo(x, y)
                fillPath.moveTo(x, zeroY)
                fillPath.lineTo(x, y)
            } else {
                val prevX = (idx - 1) * stepX
                val prevY = dbToY(animatedCurve[idx - 1])
                val midX = (prevX + x) * 0.5f
                strokePath.cubicTo(midX, prevY, midX, y, x, y)
                fillPath.cubicTo(midX, prevY, midX, y, x, y)
            }
            if (idx == count - 1) {
                fillPath.lineTo(x, zeroY)
                fillPath.close()
            }
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    accentColor.copy(alpha = 0.28f),
                    accentColor.copy(alpha = 0.04f)
                )
            )
        )

        drawPath(
            path = strokePath,
            brush = Brush.horizontalGradient(
                colors = listOf(ElectricCyan, accentColor, PartyMagenta)
            ),
            style = Stroke(
                width = 2.6.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // Draw 5 glowing control node dots at approximate log-spaced positions
        val bandPositions = listOf(0.11f, 0.31f, 0.53f, 0.75f, 0.94f)
        bandPositions.forEachIndexed { i, xFrac ->
            val sampleIdx = (xFrac * (count - 1)).roundToInt().coerceIn(0, count - 1)
            val cx = w * xFrac
            val cy = dbToY(animatedCurve[sampleIdx])
            drawCircle(
                color = accentColor.copy(alpha = 0.28f),
                radius = 7.dp.toPx(),
                center = Offset(cx, cy)
            )
            drawCircle(
                color = accentColor,
                radius = 3.5.dp.toPx(),
                center = Offset(cx, cy)
            )
        }
    }
}

private fun presetIcon(preset: EqualizerPreset): ImageVector {
    return when (preset) {
        EqualizerPreset.FLAT -> Icons.Default.GraphicEq
        EqualizerPreset.PARTY -> Icons.Default.Bolt
        EqualizerPreset.ACOUSTIC -> Icons.Default.MusicNote
        EqualizerPreset.BASS_BOOST -> Icons.Default.Speaker
        EqualizerPreset.VOCAL -> Icons.Default.RecordVoiceOver
        EqualizerPreset.OUTDOOR -> Icons.Default.WbSunny
    }
}

private fun presetAccentColor(preset: EqualizerPreset): Color {
    return when (preset) {
        EqualizerPreset.FLAT -> ElectricCyan
        EqualizerPreset.PARTY -> PartyMagenta
        EqualizerPreset.ACOUSTIC -> SyncMint
        EqualizerPreset.BASS_BOOST -> WarmAmber
        EqualizerPreset.VOCAL -> ElectricCyan
        EqualizerPreset.OUTDOOR -> SyncMint
    }
}
