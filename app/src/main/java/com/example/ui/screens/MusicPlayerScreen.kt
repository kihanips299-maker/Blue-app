package com.example.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode as AnimRepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SpeakerGroup
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.MusicLibraryFilter
import com.example.model.PartyTrack
import com.example.model.PartyUiState
import com.example.model.RepeatMode
import com.example.model.TrackSourceType
import com.example.ui.components.PartySpectrumCanvas
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.PartyMagenta
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.SurfaceHighest
import com.example.ui.theme.SyncMint
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarmAmber
import java.util.Locale

@Composable
fun MusicPlayerScreen(
    state: PartyUiState,
    albumArtBytes: ByteArray?,
    onTogglePlayPause: () -> Unit,
    onPreviousTrack: () -> Unit,
    onNextTrack: () -> Unit,
    onSeekToMs: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onSelectTrack: (PartyTrack) -> Unit,
    onAddLocalMusic: () -> Unit,
    onRemoveLocalTrack: (String) -> Unit,
    onFilterChange: (MusicLibraryFilter) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onOpenStudioEq: () -> Unit,
    onOpenSpeakers: () -> Unit,
    modifier: Modifier = Modifier
) {
    val filteredTracks = remember(state.playlist, state.libraryFilter, state.searchQuery) {
        state.playlist.filter { track ->
            val matchesFilter = when (state.libraryFilter) {
                MusicLibraryFilter.ALL -> true
                MusicLibraryFilter.LOCAL_MUSIC -> track.sourceType == TrackSourceType.LOCAL_FILE
                MusicLibraryFilter.PARTY_GROOVES -> track.sourceType == TrackSourceType.SYNTH_GROOVE
            }
            val query = state.searchQuery.trim()
            val matchesQuery = query.isEmpty() ||
                track.title.contains(query, ignoreCase = true) ||
                track.artist.contains(query, ignoreCase = true) ||
                track.album.contains(query, ignoreCase = true)
            matchesFilter && matchesQuery
        }
    }

    val activeSpeakersCount = state.outputDevices.count { it.isConnected && it.isEnabledInParty }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Full Now Playing Music Player Deck
        item {
            NowPlayingPlayerCard(
                state = state,
                albumArtBytes = albumArtBytes,
                activeSpeakersCount = activeSpeakersCount,
                onTogglePlayPause = onTogglePlayPause,
                onPreviousTrack = onPreviousTrack,
                onNextTrack = onNextTrack,
                onSeekToMs = onSeekToMs,
                onToggleShuffle = onToggleShuffle,
                onCycleRepeatMode = onCycleRepeatMode,
                onOpenStudioEq = onOpenStudioEq,
                onOpenSpeakers = onOpenSpeakers
            )
        }

        // 2. Music Library & Queue Controls Header
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "MUSIC LIBRARY & QUEUE",
                            style = MaterialTheme.typography.labelMedium,
                            color = ElectricCyan
                        )
                        Text(
                            text = "${state.playlist.size} tracks ready",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                    }

                    Button(
                        onClick = onAddLocalMusic,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCyan,
                            contentColor = ObsidianBg
                        ),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("add_music_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_pick_audio))
                    }
                }

                // Search bar
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Search tracks, artists, or albums", color = TextMuted) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search library",
                            tint = TextSecondary
                        )
                    },
                    trailingIcon = {
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear search",
                                    tint = TextSecondary
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = BorderSubtle,
                        focusedContainerColor = SurfaceDark,
                        unfocusedContainerColor = SurfaceDark,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("music_search_input")
                )

                // Filter chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(MusicLibraryFilter.entries) { filter ->
                        val selected = state.libraryFilter == filter
                        val count = when (filter) {
                            MusicLibraryFilter.ALL -> state.playlist.size
                            MusicLibraryFilter.LOCAL_MUSIC -> state.playlist.count { it.sourceType == TrackSourceType.LOCAL_FILE }
                            MusicLibraryFilter.PARTY_GROOVES -> state.playlist.count { it.sourceType == TrackSourceType.SYNTH_GROOVE }
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (selected) ElectricCyan.copy(alpha = 0.18f) else SurfaceDark,
                            border = BorderStroke(1.dp, if (selected) ElectricCyan else BorderSubtle),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 40.dp)
                                .clip(RoundedCornerShape(50))
                                .clickable { onFilterChange(filter) }
                                .testTag("music_filter_${filter.name.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "${filter.label} ($count)",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) ElectricCyan else TextPrimary
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Empty state when no matching tracks or My Music is empty
        if (filteredTracks.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = SurfaceDark,
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LibraryMusic,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = if (state.libraryFilter == MusicLibraryFilter.LOCAL_MUSIC && state.searchQuery.isBlank()) {
                                "No device songs added yet"
                            } else {
                                "No matching tracks found"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "Pick MP3, AAC, WAV, or FLAC songs from your device to stream them in sync across all connected Bluetooth speakers.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        OutlinedButton(
                            onClick = onAddLocalMusic,
                            border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.6f)),
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = ElectricCyan)
                            Spacer(Modifier.width(6.dp))
                            Text("Browse Device Audio", color = ElectricCyan)
                        }
                    }
                }
            }
        } else {
            // 4. Queue Track Rows
            items(filteredTracks, key = { it.id }) { track ->
                val isCurrent = track.id == state.currentTrack.id && !state.calibrationPulseActive
                QueueTrackRow(
                    track = track,
                    isCurrent = isCurrent,
                    isPlaying = isCurrent && state.isPlaying,
                    onClick = { onSelectTrack(track) },
                    onRemove = if (track.sourceType == TrackSourceType.LOCAL_FILE) {
                        { onRemoveLocalTrack(track.id) }
                    } else null
                )
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun NowPlayingPlayerCard(
    state: PartyUiState,
    albumArtBytes: ByteArray?,
    activeSpeakersCount: Int,
    onTogglePlayPause: () -> Unit,
    onPreviousTrack: () -> Unit,
    onNextTrack: () -> Unit,
    onSeekToMs: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onOpenStudioEq: () -> Unit,
    onOpenSpeakers: () -> Unit
) {
    val albumBitmap = remember(albumArtBytes) {
        albumArtBytes?.let { bytes ->
            runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull()
        }
    }

    val spinTransition = rememberInfiniteTransition(label = "vinyl_spin")
    val spinAngle by spinTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = AnimRepeatMode.Restart
        ),
        label = "vinyl_angle"
    )

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = SurfaceDark,
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("now_playing_card")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            ElectricCyan.copy(alpha = 0.08f),
                            SurfaceDark,
                            PartyMagenta.copy(alpha = 0.06f)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Top quick status pills: Speakers & Equalizer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (activeSpeakersCount >= 2) SyncMint.copy(alpha = 0.14f) else SurfaceElevated,
                        border = BorderStroke(
                            1.dp,
                            if (activeSpeakersCount >= 2) SyncMint.copy(alpha = 0.45f) else BorderSubtle
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = onOpenSpeakers)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SpeakerGroup,
                                contentDescription = null,
                                tint = if (activeSpeakersCount >= 2) SyncMint else ElectricCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "$activeSpeakersCount speaker${if (activeSpeakersCount == 1) "" else "s"} synced",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (activeSpeakersCount >= 2) SyncMint else TextPrimary
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = SurfaceElevated,
                        border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = onOpenStudioEq)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Equalizer,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "EQ: ${state.equalizerPreset.title}",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan
                            )
                        }
                    }
                }

                // Artwork + Track Info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        ElectricCyan.copy(alpha = 0.25f),
                                        PartyMagenta.copy(alpha = 0.25f)
                                    )
                                )
                            )
                            .border(1.dp, ElectricCyan.copy(alpha = 0.45f), RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (albumBitmap != null) {
                            Image(
                                bitmap = albumBitmap,
                                contentDescription = "Album art for ${state.currentTrack.title}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = if (state.currentTrack.sourceType == TrackSourceType.LOCAL_FILE) {
                                    Icons.Default.Album
                                } else {
                                    Icons.Default.GraphicEq
                                },
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier
                                    .size(44.dp)
                                    .rotate(if (state.isPlaying && state.currentTrack.sourceType == TrackSourceType.LOCAL_FILE) spinAngle else 0f)
                            )
                        }
                        if (state.isLoadingTrack) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(ObsidianBg.copy(alpha = 0.65f)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = ElectricCyan,
                                    modifier = Modifier.size(26.dp),
                                    strokeWidth = 2.5.dp
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (state.calibrationPulseActive) "SYNC CALIBRATION" else "NOW PLAYING",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (state.calibrationPulseActive) WarmAmber else PartyMagenta,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = state.currentTrack.title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
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
                        Text(
                            text = state.currentTrack.album,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Spectrum Visualizer
                PartySpectrumCanvas(
                    bands = state.diagnostics.spectrumBands,
                    isPlaying = state.isPlaying,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                )

                // Seekbar & Time Readout
                val isLocalSeekable = state.currentTrack.sourceType == TrackSourceType.LOCAL_FILE &&
                    state.currentTrack.durationMs > 0L
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Slider(
                        value = if (isLocalSeekable) {
                            (state.positionMs.toFloat() / state.currentTrack.durationMs.toFloat()).coerceIn(0f, 1f)
                        } else {
                            ((state.positionMs % 16000L).toFloat() / 16000f).coerceIn(0f, 1f)
                        },
                        onValueChange = { frac ->
                            if (isLocalSeekable) {
                                onSeekToMs((frac * state.currentTrack.durationMs).toLong())
                            }
                        },
                        enabled = isLocalSeekable,
                        colors = SliderDefaults.colors(
                            thumbColor = ElectricCyan,
                            activeTrackColor = ElectricCyan,
                            inactiveTrackColor = SurfaceHighest,
                            disabledThumbColor = ElectricCyan.copy(alpha = 0.65f),
                            disabledActiveTrackColor = ElectricCyan.copy(alpha = 0.55f),
                            disabledInactiveTrackColor = SurfaceHighest
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("player_seek_slider")
                            .semantics { contentDescription = "Track playback position" }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatPlayerTime(state.positionMs),
                            fontFamily = MonoFontFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        Text(
                            text = if (isLocalSeekable) {
                                formatPlayerTime(state.currentTrack.durationMs)
                            } else {
                                "LOOP • ${state.currentTrack.bpm} BPM"
                            },
                            fontFamily = MonoFontFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }

                // Main Player Transport Row: Shuffle | Prev | Play/Pause | Next | Repeat
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onToggleShuffle,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("player_shuffle_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = if (state.isShuffleEnabled) "Disable shuffle" else "Enable shuffle",
                            tint = if (state.isShuffleEnabled) ElectricCyan else TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    IconButton(
                        onClick = onPreviousTrack,
                        modifier = Modifier
                            .size(52.dp)
                            .testTag("player_prev_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous song",
                            tint = TextPrimary,
                            modifier = Modifier.size(32.dp)
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
                            .size(66.dp)
                            .testTag("player_play_pause_button")
                    ) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (state.isPlaying) "Pause music" else "Play music",
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    IconButton(
                        onClick = onNextTrack,
                        modifier = Modifier
                            .size(52.dp)
                            .testTag("player_next_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next song",
                            tint = TextPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    IconButton(
                        onClick = onCycleRepeatMode,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("player_repeat_button")
                    ) {
                        val repeatActive = state.repeatMode != RepeatMode.OFF
                        Icon(
                            imageVector = if (state.repeatMode == RepeatMode.REPEAT_ONE) {
                                Icons.Default.RepeatOne
                            } else {
                                Icons.Default.Repeat
                            },
                            contentDescription = state.repeatMode.label,
                            tint = if (repeatActive) ElectricCyan else TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueTrackRow(
    track: PartyTrack,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onRemove: (() -> Unit)?
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isCurrent) ElectricCyan.copy(alpha = 0.12f) else SurfaceDark,
        border = BorderStroke(1.dp, if (isCurrent) ElectricCyan.copy(alpha = 0.7f) else BorderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("queue_track_${track.id}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isCurrent) ElectricCyan.copy(alpha = 0.2f) else SurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        isPlaying -> Icons.Default.GraphicEq
                        track.sourceType == TrackSourceType.LOCAL_FILE -> Icons.Default.AudioFile
                        else -> Icons.Default.PlayArrow
                    },
                    contentDescription = null,
                    tint = if (isCurrent) ElectricCyan else TextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isCurrent) ElectricCyan else TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = if (track.durationMs > 0L) formatPlayerTime(track.durationMs) else "${track.bpm} BPM",
                fontFamily = MonoFontFamily,
                style = MaterialTheme.typography.labelSmall,
                color = if (isCurrent) ElectricCyan else TextMuted
            )

            if (onRemove != null) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Remove ${track.title} from queue",
                        tint = TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MiniMusicPlayerBar(
    state: PartyUiState,
    onTogglePlayPause: () -> Unit,
    onNextTrack: () -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onOpenPlayer)
            .testTag("mini_music_player_bar"),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceElevated,
        border = BorderStroke(
            1.dp,
            if (state.isPlaying) ElectricCyan.copy(alpha = 0.55f) else BorderSubtle
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ElectricCyan.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.currentTrack.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${state.currentTrack.artist} • EQ ${state.equalizerPreset.title}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onTogglePlayPause,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("mini_player_play_pause")
            ) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    tint = ElectricCyan,
                    modifier = Modifier.size(24.dp)
                )
            }

            IconButton(
                onClick = onNextTrack,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("mini_player_next")
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next track",
                    tint = TextPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

private fun formatPlayerTime(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
