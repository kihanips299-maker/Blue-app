package com.example.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.model.BluetoothCapabilities
import com.example.model.BluetoothConnectionStatus
import com.example.model.DiscoveredBluetoothDevice
import com.example.model.SpeakerTransportType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PairedBluetoothSpeaker(
    val address: String,
    val name: String,
    val isConnected: Boolean,
    val isLeAudio: Boolean,
    val batteryPercent: Int? = null
)

class BluetoothAudioMonitor(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _systemOutputDevices = MutableStateFlow<List<AudioDeviceInfo>>(emptyList())
    val systemOutputDevices: StateFlow<List<AudioDeviceInfo>> = _systemOutputDevices.asStateFlow()

    private val _pairedSpeakers = MutableStateFlow<List<PairedBluetoothSpeaker>>(emptyList())
    val pairedSpeakers: StateFlow<List<PairedBluetoothSpeaker>> = _pairedSpeakers.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredBluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredBluetoothDevice>> = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _capabilities = MutableStateFlow(BluetoothCapabilities())
    val capabilities: StateFlow<BluetoothCapabilities> = _capabilities.asStateFlow()

    private var a2dpProxy: BluetoothA2dp? = null
    private var leAudioProxy: BluetoothProfile? = null
    private var bleScanner: BluetoothLeScanner? = null
    private var isBleScanning = false
    private var isStarted = false

    private val discoveredMap = linkedMapOf<String, DiscoveredBluetoothDevice>()
    private val connectedAddresses = mutableSetOf<String>()
    private val leConnectedAddresses = mutableSetOf<String>()
    private val batteryLevelsByAddress = java.util.concurrent.ConcurrentHashMap<String, Int>()

    private val scanTimeoutRunnable = Runnable {
        stopScan()
    }

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            refreshAll()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            refreshAll()
        }
    }

    private val bleScanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            val device = result?.device ?: return
            if (!hasConnectPermission()) return
            val name = result.scanRecord?.deviceName ?: device.name
            if (name.isNullOrBlank()) return
            val isLeAudioHint = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                (result.scanRecord?.serviceUuids?.any {
                    it.uuid.toString().startsWith("0000184e", ignoreCase = true) || // ASCS
                        it.uuid.toString().startsWith("00001853", ignoreCase = true) || // Common Audio
                        it.uuid.toString().startsWith("0000184f", ignoreCase = true) // BAA
                } == true)
            upsertDiscoveredDevice(
                device = device,
                overrideName = name,
                rssi = result.rssi,
                transportHint = if (isLeAudioHint) "LE Audio" else "BLE"
            )
        }

        override fun onScanFailed(errorCode: Int) {
            isBleScanning = false
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED,
                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
                BluetoothDevice.ACTION_ACL_CONNECTED,
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    refreshAll()
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device = extractBluetoothDevice(intent)
                    device?.let { upsertDiscoveredDevice(it, overrideName = null, rssi = null, transportHint = "Classic BT") }
                    refreshAll()
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    _isScanning.value = true
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    if (!isBleScanning) {
                        _isScanning.value = false
                    }
                }
                BluetoothDevice.ACTION_FOUND -> {
                    if (!hasScanPermission()) return
                    val device = extractBluetoothDevice(intent) ?: return
                    val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)
                        .takeIf { it != Short.MIN_VALUE }?.toInt()
                    upsertDiscoveredDevice(
                        device = device,
                        overrideName = null,
                        rssi = rssi,
                        transportHint = "Classic BT"
                    )
                }
                ACTION_BATTERY_LEVEL_CHANGED -> {
                    val device = extractBluetoothDevice(intent) ?: return
                    val addr = device.address ?: return
                    val level = intent.getIntExtra(EXTRA_BATTERY_LEVEL, -1)
                    if (level in 0..100) {
                        batteryLevelsByAddress[addr] = level
                    } else {
                        batteryLevelsByAddress.remove(addr)
                    }
                    refreshAll()
                }
            }
        }
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            when (profile) {
                BluetoothProfile.A2DP -> a2dpProxy = proxy as? BluetoothA2dp
                else -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        profile == BluetoothProfile.LE_AUDIO
                    ) {
                        leAudioProxy = proxy
                    }
                }
            }
            refreshAll()
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.A2DP) a2dpProxy = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                profile == BluetoothProfile.LE_AUDIO
            ) {
                leAudioProxy = null
            }
            refreshAll()
        }
    }

    fun start() {
        if (isStarted) {
            refreshAll()
            return
        }
        isStarted = true
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, mainHandler)

        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(ACTION_BATTERY_LEVEL_CHANGED)
        }
        ContextCompat.registerReceiver(
            context,
            bluetoothReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )

        attachProfileProxies()
        refreshAll()
    }

    fun stop() {
        if (!isStarted) return
        isStarted = false
        stopScan()
        runCatching { audioManager.unregisterAudioDeviceCallback(audioDeviceCallback) }
        runCatching { context.unregisterReceiver(bluetoothReceiver) }
        a2dpProxy?.let { bluetoothAdapter?.closeProfileProxy(BluetoothProfile.A2DP, it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            leAudioProxy?.let { bluetoothAdapter?.closeProfileProxy(BluetoothProfile.LE_AUDIO, it) }
        }
        a2dpProxy = null
        leAudioProxy = null
    }

    fun attachProfileProxies() {
        val adapter = bluetoothAdapter ?: return
        if (!hasConnectPermission()) return
        runCatching {
            if (a2dpProxy == null) {
                adapter.getProfileProxy(context, profileListener, BluetoothProfile.A2DP)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && leAudioProxy == null) {
                adapter.getProfileProxy(context, profileListener, BluetoothProfile.LE_AUDIO)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun refreshAll() {
        val rawOutputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { isSupportedPlaybackSink(it) }
            .distinctBy { "${it.type}_${it.productName}_${it.id}" }

        _systemOutputDevices.value = rawOutputs

        val hasConnect = hasConnectPermission()
        val adapter = bluetoothAdapter

        connectedAddresses.clear()
        leConnectedAddresses.clear()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            rawOutputs.forEach { info ->
                val addr = info.address
                if (!addr.isNullOrBlank()) {
                    connectedAddresses.add(addr)
                }
            }
        }

        val paired = if (adapter != null && hasConnect) {
            runCatching {
                a2dpProxy?.connectedDevices?.mapNotNull { it.address }?.let {
                    connectedAddresses.addAll(it)
                }
            }
            runCatching {
                leAudioProxy?.connectedDevices?.mapNotNull { it.address }?.let {
                    leConnectedAddresses.addAll(it)
                    connectedAddresses.addAll(it)
                }
            }

            runCatching {
                adapter.bondedDevices.orEmpty()
                    .filter { isAudioBluetoothDevice(it) }
                    .map { dev ->
                        val addr = dev.address ?: dev.name.orEmpty()
                        val isLe = addr in leConnectedAddresses
                        val isConnected = addr in connectedAddresses
                        val battery = readBatteryLevelSafely(dev) ?: batteryLevelsByAddress[addr]
                        if (battery != null && addr.isNotBlank()) {
                            batteryLevelsByAddress[addr] = battery
                        }
                        PairedBluetoothSpeaker(
                            address = addr,
                            name = dev.name ?: "Bluetooth Speaker",
                            isConnected = isConnected,
                            isLeAudio = isLe,
                            batteryPercent = if (isConnected) battery else null
                        )
                    }
                    .sortedWith(compareByDescending<PairedBluetoothSpeaker> { it.isConnected }.thenBy { it.name })
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        _pairedSpeakers.value = paired

        // Sync bonded/connected devices into discovery list so users always see current connection status
        if (adapter != null && hasConnect) {
            runCatching {
                adapter.bondedDevices.orEmpty().forEach { dev ->
                    if (isAudioBluetoothDevice(dev)) {
                        val addr = dev.address ?: return@forEach
                        val existing = discoveredMap[addr]
                        val status = resolveConnectionStatus(addr, dev.bondState)
                        val transport = when {
                            addr in leConnectedAddresses -> "LE Audio"
                            existing != null -> existing.transportHint
                            else -> "Classic BT"
                        }
                        val battery = if (status == BluetoothConnectionStatus.CONNECTED) {
                            readBatteryLevelSafely(dev) ?: batteryLevelsByAddress[addr]
                        } else {
                            null
                        }
                        discoveredMap[addr] = DiscoveredBluetoothDevice(
                            name = dev.name?.takeIf { it.isNotBlank() } ?: existing?.name ?: "Bluetooth Speaker",
                            address = addr,
                            rssi = existing?.rssi,
                            isAudioDevice = true,
                            bondState = dev.bondState,
                            connectionStatus = status,
                            transportHint = transport,
                            lastSeenMs = existing?.lastSeenMs ?: System.currentTimeMillis(),
                            batteryPercent = battery
                        )
                    }
                }
            }
        }
        publishDiscoveredList()

        val leAudioSupported = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && adapter != null) {
            runCatching { adapter.isLeAudioSupported == BluetoothStatusCodes.FEATURE_SUPPORTED }.getOrDefault(false)
        } else {
            false
        }

        val auracastSupported = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && adapter != null) {
            runCatching {
                adapter.isLeAudioBroadcastSourceSupported == BluetoothStatusCodes.FEATURE_SUPPORTED
            }.getOrDefault(false)
        } else {
            false
        }

        val activeA2dp = rawOutputs.count { mapTransportType(it) == SpeakerTransportType.BLUETOOTH_A2DP }
        val activeLe = rawOutputs.count {
            val t = mapTransportType(it)
            t == SpeakerTransportType.BLUETOOTH_LE_AUDIO || t == SpeakerTransportType.BLUETOOTH_LE_BROADCAST
        }

        _capabilities.value = BluetoothCapabilities(
            bluetoothEnabled = adapter?.isEnabled == true,
            hasBluetoothPermissions = hasConnect && hasScanPermission(),
            leAudioSupported = leAudioSupported,
            auracastBroadcastSupported = auracastSupported,
            activeA2dpCount = activeA2dp,
            activeLeAudioCount = activeLe
        )
    }

    @SuppressLint("MissingPermission")
    fun startScan(): Boolean {
        val adapter = bluetoothAdapter ?: return false
        if (!adapter.isEnabled || !hasScanPermission()) return false

        mainHandler.removeCallbacks(scanTimeoutRunnable)
        // Clear only non-bonded stale entries while keeping live paired/connected status fresh
        val keepBonded = discoveredMap.filterValues { it.bondState == BluetoothDevice.BOND_BONDED }
        discoveredMap.clear()
        discoveredMap.putAll(keepBonded)
        refreshAll()

        val classicStarted = runCatching {
            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }
            adapter.startDiscovery()
        }.getOrDefault(false)

        val bleStarted = runCatching {
            val scanner = adapter.bluetoothLeScanner
            bleScanner = scanner
            if (scanner != null) {
                val settings = ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build()
                scanner.startScan(null, settings, bleScanCallback)
                isBleScanning = true
                true
            } else {
                false
            }
        }.getOrDefault(false)

        val active = classicStarted || bleStarted
        _isScanning.value = active
        if (active) {
            mainHandler.postDelayed(scanTimeoutRunnable, 15_000L)
        }
        return active
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        mainHandler.removeCallbacks(scanTimeoutRunnable)
        val adapter = bluetoothAdapter
        if (adapter != null && hasScanPermission()) {
            runCatching {
                if (adapter.isDiscovering) {
                    adapter.cancelDiscovery()
                }
            }
            if (isBleScanning) {
                runCatching { bleScanner?.stopScan(bleScanCallback) }
                isBleScanning = false
            }
        }
        _isScanning.value = false
    }

    @SuppressLint("MissingPermission")
    fun initiateBond(address: String): Boolean {
        val adapter = bluetoothAdapter ?: return false
        if (!hasConnectPermission()) return false
        stopScan()
        return runCatching {
            val device = adapter.getRemoteDevice(address)
            if (device.bondState == BluetoothDevice.BOND_NONE) {
                device.createBond()
            } else {
                true
            }
        }.getOrDefault(false)
    }

    fun openBluetoothSettings() {
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    @SuppressLint("MissingPermission")
    private fun upsertDiscoveredDevice(
        device: BluetoothDevice,
        overrideName: String?,
        rssi: Int?,
        transportHint: String
    ) {
        val name = (overrideName ?: device.name)?.takeIf { it.isNotBlank() } ?: return
        val address = device.address ?: return
        val existing = discoveredMap[address]
        val isAudio = isAudioBluetoothDevice(device) || transportHint == "LE Audio"
        val bondState = runCatching { device.bondState }.getOrDefault(BluetoothDevice.BOND_NONE)
        val status = resolveConnectionStatus(address, bondState)
        val resolvedTransport = when {
            address in leConnectedAddresses -> "LE Audio"
            existing?.transportHint == "Classic BT" && transportHint == "BLE" -> "Dual-Mode BT"
            else -> transportHint
        }

        val battery = if (status == BluetoothConnectionStatus.CONNECTED) {
            readBatteryLevelSafely(device) ?: batteryLevelsByAddress[address]
        } else {
            null
        }

        discoveredMap[address] = DiscoveredBluetoothDevice(
            name = name,
            address = address,
            rssi = rssi ?: existing?.rssi,
            isAudioDevice = isAudio,
            bondState = bondState,
            connectionStatus = status,
            transportHint = resolvedTransport,
            lastSeenMs = System.currentTimeMillis(),
            batteryPercent = battery
        )
        publishDiscoveredList()
    }

    private fun readBatteryLevelSafely(device: BluetoothDevice): Int? {
        return runCatching {
            val method = BluetoothDevice::class.java.getMethod("getBatteryLevel")
            val level = method.invoke(device) as? Int ?: -1
            level.takeIf { it in 0..100 }
        }.getOrNull()
    }

    private fun resolveConnectionStatus(address: String, bondState: Int): BluetoothConnectionStatus {
        return when {
            address in connectedAddresses -> BluetoothConnectionStatus.CONNECTED
            bondState == BluetoothDevice.BOND_BONDING -> BluetoothConnectionStatus.PAIRING
            bondState == BluetoothDevice.BOND_BONDED -> BluetoothConnectionStatus.PAIRED
            else -> BluetoothConnectionStatus.AVAILABLE
        }
    }

    private fun publishDiscoveredList() {
        _discoveredDevices.value = discoveredMap.values.sortedWith(
            compareBy<DiscoveredBluetoothDevice> {
                when (it.connectionStatus) {
                    BluetoothConnectionStatus.CONNECTED -> 0
                    BluetoothConnectionStatus.CONNECTING -> 1
                    BluetoothConnectionStatus.PAIRING -> 2
                    BluetoothConnectionStatus.PAIRED -> 3
                    BluetoothConnectionStatus.AVAILABLE -> 4
                }
            }
                .thenByDescending { it.isAudioDevice }
                .thenByDescending { it.rssi ?: -120 }
                .thenBy { it.name }
        )
    }

    private fun extractBluetoothDevice(intent: Intent): BluetoothDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
    }

    @SuppressLint("MissingPermission")
    private fun isAudioBluetoothDevice(device: BluetoothDevice): Boolean {
        val btClass = runCatching { device.bluetoothClass }.getOrNull() ?: return true
        val major = btClass.majorDeviceClass
        return major == BluetoothClass.Device.Major.AUDIO_VIDEO ||
            major == BluetoothClass.Device.Major.UNCATEGORIZED
    }

    fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    companion object {
        fun isSupportedPlaybackSink(info: AudioDeviceInfo): Boolean {
            if (!info.isSink) return false
            return when (info.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_USB_DEVICE,
                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> true
                else -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        (info.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                            info.type == AudioDeviceInfo.TYPE_BLE_SPEAKER)
                    ) {
                        true
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        info.type == AudioDeviceInfo.TYPE_BLE_BROADCAST
                    ) {
                        true
                    } else {
                        false
                    }
                }
            }
        }

        fun mapTransportType(info: AudioDeviceInfo): SpeakerTransportType {
            return when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    info.type == AudioDeviceInfo.TYPE_BLE_BROADCAST -> SpeakerTransportType.BLUETOOTH_LE_BROADCAST
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    (info.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                        info.type == AudioDeviceInfo.TYPE_BLE_HEADSET) -> SpeakerTransportType.BLUETOOTH_LE_AUDIO
                info.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> SpeakerTransportType.BLUETOOTH_A2DP
                info.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                    info.type == AudioDeviceInfo.TYPE_USB_HEADSET -> SpeakerTransportType.USB_AUDIO
                info.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    info.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> SpeakerTransportType.WIRED_AUDIO
                else -> SpeakerTransportType.BUILT_IN_SPEAKER
            }
        }

        fun buildStableDeviceId(info: AudioDeviceInfo): String {
            val addr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.address.takeIf { !it.isNullOrBlank() }
            } else null
            return if (addr != null) {
                "bt_${addr}"
            } else {
                val product = info.productName?.toString()?.trim()?.ifEmpty { "Speaker" } ?: "Speaker"
                "sink_${info.type}_${product.lowercase().replace(Regex("[^a-z0-9]+"), "_")}"
            }
        }

        private const val ACTION_BATTERY_LEVEL_CHANGED = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
        private const val EXTRA_BATTERY_LEVEL = "android.bluetooth.device.extra.BATTERY_LEVEL"
    }
}
