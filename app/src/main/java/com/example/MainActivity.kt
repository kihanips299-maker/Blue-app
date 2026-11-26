package com.example

import android.Manifest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.SpeakerGroup
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.BluetoothSearching
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.SpeakerGroup
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.audio.DeviceMusicScanner
import com.example.ui.AmpSyncViewModel
import com.example.ui.components.SyncStatusPill
import com.example.ui.screens.BluetoothDiscoveryScreen
import com.example.ui.screens.MiniMusicPlayerBar
import com.example.ui.screens.MusicPlayerScreen
import com.example.ui.screens.PartyDeckScreen
import com.example.ui.screens.SpeakersSyncScreen
import com.example.ui.screens.StudioPresetsScreen
import com.example.ui.theme.AmpSyncTheme
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

const val ROUTE_PARTY_DECK = "party_deck"
const val ROUTE_MUSIC_PLAYER = "music_player"
const val ROUTE_SPEAKERS_SYNC = "speakers_sync"
const val ROUTE_DISCOVERY = "bluetooth_discovery"
const val ROUTE_STUDIO_PRESETS = "studio_presets"

private data class NavDestinationItem(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val navDestinations = listOf(
    NavDestinationItem(
        route = ROUTE_PARTY_DECK,
        labelRes = R.string.nav_party_deck,
        selectedIcon = Icons.Filled.GraphicEq,
        unselectedIcon = Icons.Outlined.GraphicEq,
        testTag = "nav_party_deck"
    ),
    NavDestinationItem(
        route = ROUTE_MUSIC_PLAYER,
        labelRes = R.string.nav_player,
        selectedIcon = Icons.Filled.LibraryMusic,
        unselectedIcon = Icons.Outlined.LibraryMusic,
        testTag = "nav_player"
    ),
    NavDestinationItem(
        route = ROUTE_SPEAKERS_SYNC,
        labelRes = R.string.nav_speakers,
        selectedIcon = Icons.Filled.SpeakerGroup,
        unselectedIcon = Icons.Outlined.SpeakerGroup,
        testTag = "nav_speakers"
    ),
    NavDestinationItem(
        route = ROUTE_DISCOVERY,
        labelRes = R.string.nav_discover,
        selectedIcon = Icons.Filled.BluetoothSearching,
        unselectedIcon = Icons.Outlined.BluetoothSearching,
        testTag = "nav_discover"
    ),
    NavDestinationItem(
        route = ROUTE_STUDIO_PRESETS,
        labelRes = R.string.nav_presets,
        selectedIcon = Icons.Filled.Tune,
        unselectedIcon = Icons.Outlined.Tune,
        testTag = "nav_studio"
    )
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(ObsidianBg.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(SurfaceDark.toArgb())
        )
        volumeControlStream = AudioManager.STREAM_MUSIC
        setContent {
            AmpSyncTheme {
                AmpSyncApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        volumeControlStream = AudioManager.STREAM_MUSIC
    }
}

@Composable
fun AmpSyncApp(viewModel: AmpSyncViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val albumArtBytes by viewModel.currentAlbumArt.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentRoute = currentDestination?.route

    val btPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.onBluetoothPermissionsUpdated()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* playback works even if notifications are declined */ }

    val musicPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onMusicPermissionResult(granted)
    }

    var askedMusicPermissionOnPlayerTab by rememberSaveable { mutableStateOf(false) }

    val requestMusicLibraryPermission: () -> Unit = {
        musicPermissionLauncher.launch(DeviceMusicScanner.requiredAudioPermission())
    }

    LaunchedEffect(currentRoute, state.hasMusicPermission) {
        if (currentRoute == ROUTE_MUSIC_PLAYER && !state.hasMusicPermission && !askedMusicPermissionOnPlayerTab) {
            askedMusicPermissionOnPlayerTab = true
            requestMusicLibraryPermission()
        }
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.loadLocalAudioUris(uris)
        }
    }

    val requestBluetoothPermissions: () -> Unit = {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        btPermissionLauncher.launch(permissions)
    }

    val handlePlayPause: () -> Unit = {
        if (!state.isPlaying && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        viewModel.togglePlayPause()
    }

    val navigateToRoute: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = ObsidianBg,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                AmpSyncTopHeader(
                    activeSinkCount = state.outputDevices.count { it.isConnected && it.isEnabledInParty },
                    isPlaying = state.isPlaying,
                    maxDriftMs = state.diagnostics.maxSyncDriftMs,
                    statusBanner = state.statusBanner,
                    onDismissBanner = viewModel::dismissBanner,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
                )
            },
            bottomBar = {
                val showMiniPlayer = currentRoute != ROUTE_MUSIC_PLAYER && currentRoute != ROUTE_PARTY_DECK
                Column(modifier = Modifier.fillMaxWidth()) {
                    AnimatedVisibility(visible = showMiniPlayer) {
                        MiniMusicPlayerBar(
                            state = state,
                            onTogglePlayPause = handlePlayPause,
                            onNextTrack = viewModel::nextTrack,
                            onOpenPlayer = { navigateToRoute(ROUTE_MUSIC_PLAYER) },
                            modifier = if (useRail) {
                                Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                            } else {
                                Modifier
                            }
                        )
                    }
                    if (!useRail) {
                        NavigationBar(
                            containerColor = SurfaceDark,
                            contentColor = TextPrimary,
                            windowInsets = WindowInsets.navigationBars
                        ) {
                            navDestinations.forEach { dest ->
                                val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                                val label = stringResource(dest.labelRes)
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateToRoute(dest.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = label
                                        )
                                    },
                                    label = { Text(label, maxLines = 1) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = ObsidianBg,
                                        selectedTextColor = ElectricCyan,
                                        indicatorColor = ElectricCyan,
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary
                                    ),
                                    modifier = Modifier.testTag(dest.testTag)
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = SurfaceDark,
                        contentColor = TextPrimary,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Spacer(Modifier.size(12.dp))
                        navDestinations.forEach { dest ->
                            val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                            val label = stringResource(dest.labelRes)
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToRoute(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = ObsidianBg,
                                    selectedTextColor = ElectricCyan,
                                    indicatorColor = ElectricCyan,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_PARTY_DECK,
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 720.dp)
                    ) {
                        composable(ROUTE_PARTY_DECK) {
                            PartyDeckScreen(
                                state = state,
                                onTogglePlayPause = handlePlayPause,
                                onPreviousTrack = viewModel::previousTrack,
                                onNextTrack = viewModel::nextTrack,
                                onSelectTrack = viewModel::selectTrack,
                                onSeekToMs = viewModel::seekToMs,
                                onPickLocalAudio = { audioPickerLauncher.launch(arrayOf("audio/*")) },
                                onToggleCalibrationPulse = viewModel::toggleCalibrationPulse,
                                onMasterVolumeChange = viewModel::setMasterVolume,
                                onToggleMasterMute = viewModel::toggleMasterMute,
                                onDeviceVolumeChange = viewModel::setDeviceVolume,
                                onToggleDeviceMute = viewModel::toggleDeviceMute,
                                onToggleDeviceSolo = viewModel::toggleDeviceSolo,
                                onDeviceZoneChange = viewModel::setDeviceRoomZone,
                                onSelectEqualizerPreset = viewModel::setEqualizerPreset,
                                onNavigateToSpeakers = { navigateToRoute(ROUTE_SPEAKERS_SYNC) },
                                onNavigateToStudio = { navigateToRoute(ROUTE_STUDIO_PRESETS) }
                            )
                        }

                        composable(ROUTE_MUSIC_PLAYER) {
                            MusicPlayerScreen(
                                state = state,
                                albumArtBytes = albumArtBytes,
                                onTogglePlayPause = handlePlayPause,
                                onPreviousTrack = viewModel::previousTrack,
                                onNextTrack = viewModel::nextTrack,
                                onSeekToMs = viewModel::seekToMs,
                                onToggleShuffle = viewModel::toggleShuffle,
                                onCycleRepeatMode = viewModel::cycleRepeatMode,
                                onSelectTrack = { track -> viewModel.selectTrack(track, autoPlay = true) },
                                onRequestMusicPermission = requestMusicLibraryPermission,
                                onRescanDeviceMusic = { viewModel.scanDeviceMusic(showResultBanner = true) },
                                onAddLocalMusic = { audioPickerLauncher.launch(arrayOf("audio/*")) },
                                onRemoveLocalTrack = viewModel::removeImportedTrack,
                                onFilterChange = viewModel::setLibraryFilter,
                                onSearchQueryChange = viewModel::setSearchQuery,
                                onOpenStudioEq = { navigateToRoute(ROUTE_STUDIO_PRESETS) },
                                onOpenSpeakers = { navigateToRoute(ROUTE_SPEAKERS_SYNC) }
                            )
                        }

                        composable(ROUTE_SPEAKERS_SYNC) {
                            SpeakersSyncScreen(
                                state = state,
                                onRequestBluetoothPermissions = requestBluetoothPermissions,
                                onToggleScan = {
                                    navigateToRoute(ROUTE_DISCOVERY)
                                    if (!state.bluetoothCapabilities.hasBluetoothPermissions) {
                                        requestBluetoothPermissions()
                                    } else {
                                        viewModel.startBluetoothScanIfIdle()
                                    }
                                },
                                onOpenBluetoothSettings = viewModel::openBluetoothSettings,
                                onRefreshDevices = viewModel::refreshDevices,
                                onPairDevice = viewModel::pairDiscoveredDevice,
                                onToggleDeviceInParty = viewModel::toggleDeviceInParty,
                                onMasterVolumeChange = viewModel::setMasterVolume,
                                onToggleMasterMute = viewModel::toggleMasterMute,
                                onDeviceVolumeChange = viewModel::setDeviceVolume,
                                onToggleDeviceMute = viewModel::toggleDeviceMute,
                                onLatencyOffsetChange = viewModel::setDeviceLatencyOffset,
                                onZoneChange = viewModel::setDeviceRoomZone,
                                onToggleCalibrationPulse = viewModel::toggleCalibrationPulse
                            )
                        }

                        composable(ROUTE_DISCOVERY) {
                            BluetoothDiscoveryScreen(
                                state = state,
                                onRequestBluetoothPermissions = requestBluetoothPermissions,
                                onToggleScan = {
                                    if (!state.bluetoothCapabilities.hasBluetoothPermissions) {
                                        requestBluetoothPermissions()
                                    } else {
                                        viewModel.toggleBluetoothScan()
                                    }
                                },
                                onRefresh = viewModel::refreshDevices,
                                onOpenBluetoothSettings = viewModel::openBluetoothSettings,
                                onSetAudioOnlyFilter = viewModel::setDiscoveryAudioOnlyFilter,
                                onPairDevice = viewModel::pairDiscoveredDevice,
                                onMasterVolumeChange = viewModel::setMasterVolume,
                                onToggleMasterMute = viewModel::toggleMasterMute,
                                onSpeakerVolumeChange = viewModel::setDiscoveredSpeakerVolume,
                                onToggleSpeakerMute = viewModel::toggleDiscoveredSpeakerMute,
                                onNavigateToSpeakers = { navigateToRoute(ROUTE_SPEAKERS_SYNC) }
                            )
                        }

                        composable(ROUTE_STUDIO_PRESETS) {
                            StudioPresetsScreen(
                                state = state,
                                onSelectEqualizerPreset = viewModel::setEqualizerPreset,
                                onToggleLowLatencyMode = viewModel::toggleLowLatencyMode,
                                onToggleAutoPauseOnDisconnect = viewModel::toggleAutoPauseOnDisconnect,
                                onApplyPreset = viewModel::applyPreset,
                                onSavePreset = viewModel::saveCurrentAsPreset,
                                onDeletePreset = viewModel::deletePreset
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AmpSyncTopHeader(
    activeSinkCount: Int,
    isPlaying: Boolean,
    maxDriftMs: Float,
    statusBanner: String?,
    onDismissBanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ObsidianBg)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ElectricCyan.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.Filled.GraphicEq,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier
                            .padding(6.dp)
                            .size(20.dp)
                    )
                }
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            SyncStatusPill(
                activeSinkCount = activeSinkCount,
                isPlaying = isPlaying,
                maxDriftMs = maxDriftMs
            )
        }

        AnimatedVisibility(visible = !statusBanner.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SurfaceElevated,
                border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = statusBanner.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onDismissBanner,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss notification",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
