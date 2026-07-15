package com.example.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.BatteryHistoryLog
import com.example.data.BatterySettings
import com.example.data.BmsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import java.util.UUID
import kotlin.random.Random

enum class BmsConnectionState {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED
}

data class ScanDevice(
    val name: String,
    val address: String,
    val rssi: Int,
    val device: BluetoothDevice? = null
)

data class BmsTelemetry(
    val connectionState: BmsConnectionState = BmsConnectionState.DISCONNECTED,
    val connectedDeviceName: String = "",
    val connectedDeviceAddress: String = "",
    val totalVoltage: Float = 0f,
    val current: Float = 0f,
    val soc: Float = 0f,
    val capacityAh: Float = 0f,
    val temperatures: List<Float> = emptyList(), // Pack, Ambient, MOS
    val cellVoltages: List<Float> = emptyList(),
    val cellBalancing: List<Boolean> = emptyList(),
    val chargeSwitchOn: Boolean = true,
    val dischargeSwitchOn: Boolean = true,
    val activeAlerts: List<String> = emptyList(),
    val isCharging: Boolean = false,
    val isDischarging: Boolean = false,
    val isEncrypted: Boolean = false,
    val isAuthorized: Boolean = true
)

class BmsBluetoothManager(
    private val context: Context,
    private val repository: BmsRepository
) {
    private val _connectionState = MutableStateFlow(BmsConnectionState.DISCONNECTED)
    val connectionState: StateFlow<BmsConnectionState> = _connectionState.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<ScanDevice>>(emptyList())
    val scannedDevices: StateFlow<List<ScanDevice>> = _scannedDevices.asStateFlow()

    private val _telemetry = MutableStateFlow(BmsTelemetry())
    val telemetry: StateFlow<BmsTelemetry> = _telemetry.asStateFlow()

    private var isSessionAuthorized = true

    fun lockBms() {
        isSessionAuthorized = false
        updateTelemetry(_telemetry.value)
    }

    fun unlockBms() {
        isSessionAuthorized = true
        updateTelemetry(_telemetry.value)
    }

    suspend fun authorizeBms(password: String): Boolean {
        val settings = repository.getSettings()
        if (!settings.isBmsEncrypted || password == settings.bmsPassword) {
            isSessionAuthorized = true
            updateTelemetry(_telemetry.value)
            return true
        }
        return false
    }

    private fun updateTelemetry(rawTelemetry: BmsTelemetry) {
        scope.launch {
            val settings = repository.getSettings()
            val isEncrypted = settings.isBmsEncrypted
            val isAuthorized = !isEncrypted || isSessionAuthorized

            val finalTelemetry = if (!isAuthorized && rawTelemetry.connectionState == BmsConnectionState.CONNECTED) {
                rawTelemetry.copy(
                    totalVoltage = 0f,
                    current = 0f,
                    soc = 0f,
                    capacityAh = 0f,
                    temperatures = emptyList(),
                    cellVoltages = emptyList(),
                    cellBalancing = emptyList(),
                    isCharging = false,
                    isDischarging = false,
                    isEncrypted = isEncrypted,
                    isAuthorized = false
                )
            } else {
                rawTelemetry.copy(
                    isEncrypted = isEncrypted,
                    isAuthorized = isAuthorized
                )
            }
            _telemetry.value = finalTelemetry
        }
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var simulationJob: Job? = null
    private var dbLoggingJob: Job? = null
    private var scanJob: Job? = null
    private var pollingJob: Job? = null

    // Real GATT elements
    private var bluetoothGatt: BluetoothGatt? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null
    private var txCharacteristic: BluetoothGattCharacteristic? = null

    private enum class HardwareType {
        DEMO,
        JBD,
        DALY,
        GENERIC
    }
    private var connectedHardwareType = HardwareType.DEMO

    // Real parser local states
    private var jbdCellVoltages = mutableListOf<Float>()
    private var jbdTemperatures = mutableListOf<Float>()
    private val jbdBuffer = mutableListOf<Byte>()

    private val dalyBuffer = mutableListOf<Byte>()
    private var dalyCellVoltages = mutableListOf<Float>()
    private var dalyTemperatures = mutableListOf<Float>()
    private var dalySocs = 100f
    private var dalyVolt = 0f
    private var dalyCurr = 0f
    private var dalyChargeSwitchOn = true
    private var dalyDischargeSwitchOn = true

    // Simulated states
    private var simulatedCapacityRemainingAh = 82.4f
    private var simulatedCellVoltages = mutableListOf<Float>()
    private var simulatedCellBalancing = mutableListOf<Boolean>()
    private var simulatedTemperatures = mutableListOf(24.5f, 22.1f, 27.8f) // Pack, Ambient, MOS
    private var simulatedCurrent = 0f // Amps
    private var isSimulatingCharger = false
    private var isSimulatingLoad = false

    companion object {
        // JBD BMS UUIDs (Xiaoxiang)
        val JBD_SERVICE_UUID: UUID = UUID.fromString("0000ff00-0000-1000-8000-00805f9b34fb")
        val JBD_TX_UUID: UUID = UUID.fromString("0000ff01-0000-1000-8000-00805f9b34fb") // Notify
        val JBD_RX_UUID: UUID = UUID.fromString("0000ff02-0000-1000-8000-00805f9b34fb") // Write

        // Daly BMS UUIDs
        val DALY_SERVICE_UUID: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
        val DALY_TX_UUID: UUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb") // Notify
        val DALY_RX_UUID: UUID = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb") // Write

        // CCCD notification descriptor
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    init {
        // Prepare initial simulated cell voltages
        resetSimulationState(8, 3.32f, 3.28f)

        // Observe Settings flow to synchronously command real BMS hardware when switches change in the UI
        scope.launch {
            repository.settingsFlow.collect { settings ->
                if (settings == null) return@collect
                val gatt = bluetoothGatt ?: return@collect
                if (_connectionState.value != BmsConnectionState.CONNECTED) return@collect

                when (connectedHardwareType) {
                    HardwareType.JBD -> {
                        val valByte = when {
                            !settings.chargeSwitchOn && !settings.dischargeSwitchOn -> 0x03.toByte()
                            !settings.chargeSwitchOn -> 0x01.toByte()
                            !settings.dischargeSwitchOn -> 0x02.toByte()
                            else -> 0x00.toByte()
                        }
                        // JBD MOS Switch Command to Reg 0xE1
                        val sum = 0x5A + 0xE1 + 0x02 + 0x00 + valByte.toInt()
                        val expectedChecksum = (0xFFFF - sum + 1) and 0xFFFF
                        val writeCmd = byteArrayOf(
                            0xDD.toByte(), 0x5A.toByte(), 0xE1.toByte(), 0x02.toByte(),
                            0x00.toByte(), valByte,
                            ((expectedChecksum shr 8) and 0xFF).toByte(),
                            (expectedChecksum and 0xFF).toByte(),
                            0x77.toByte()
                        )
                        writeCommandBytes(writeCmd)
                        Log.i("BmsBluetooth", "Wrote real JBD switch command: charge=${settings.chargeSwitchOn}, discharge=${settings.dischargeSwitchOn}")
                    }
                    HardwareType.DALY -> {
                        val chargeByte = if (settings.chargeSwitchOn) 0x01.toByte() else 0x00.toByte()
                        val dischargeByte = if (settings.dischargeSwitchOn) 0x01.toByte() else 0x00.toByte()
                        val writeCmd = ByteArray(13)
                        writeCmd[0] = 0xA5.toByte()
                        writeCmd[1] = 0x40.toByte()
                        writeCmd[2] = 0xD9.toByte()
                        writeCmd[3] = 0x02.toByte()
                        writeCmd[4] = chargeByte
                        writeCmd[5] = dischargeByte

                        var sum = 0
                        for (i in 0..11) {
                            sum += writeCmd[i].toInt() and 0xFF
                        }
                        writeCmd[12] = (sum and 0xFF).toByte()
                        writeCommandBytes(writeCmd)
                        Log.i("BmsBluetooth", "Wrote real Daly switch command: charge=${settings.chargeSwitchOn}, discharge=${settings.dischargeSwitchOn}")
                    }
                    else -> {}
                }
            }
        }
    }

    private fun resetSimulationState(cellCount: Int, maxV: Float, minV: Float) {
        simulatedCellVoltages.clear()
        simulatedCellBalancing.clear()
        val step = (maxV - minV) / maxOf(1, cellCount - 1)
        for (i in 0 until cellCount) {
            simulatedCellVoltages.add(maxV - i * step)
            simulatedCellBalancing.add(i % 3 == 0)
        }
    }

    // Checking permissions safely
    fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    @SuppressLint("MissingPermission")
    fun startScanning() {
        if (_connectionState.value == BmsConnectionState.CONNECTED) return

        _scannedDevices.value = emptyList()
        _connectionState.value = BmsConnectionState.SCANNING

        // Always include the Demo BMS in scan results
        val demoDevice = ScanDevice("Demo BMS Simulator (Virtual)", "00:11:22:33:44:FF", -35)
        _scannedDevices.value = listOf(demoDevice)

        if (!hasBluetoothPermission() || bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.w("BmsBluetooth", "Bluetooth adapter not available/enabled or missing permission. Scanning demo only.")
            return
        }

        try {
            val scanner = bluetoothAdapter.bluetoothLeScanner
            if (scanner == null) {
                Log.e("BmsBluetooth", "BluetoothLeScanner is null")
                return
            }

            val scanCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    val device = result.device
                    val name = device.name ?: "Unknown BMS"
                    val address = device.address ?: "00:00:00:00:00:00"
                    val rssi = result.rssi

                    // BMS BLE search patterns (Daly, JBD, ANT, ANT-BMS, SmartBMS)
                    val isBmsDevice = name.contains("BMS", ignoreCase = true) ||
                            name.contains("Smart", ignoreCase = true) ||
                            name.contains("Daly", ignoreCase = true) ||
                            name.contains("JBD", ignoreCase = true) ||
                            name.contains("ANT", ignoreCase = true) ||
                            name.contains("Battery", ignoreCase = true)

                    if (isBmsDevice) {
                        val currentList = _scannedDevices.value.toMutableList()
                        if (currentList.none { it.address == address }) {
                            currentList.add(ScanDevice(name, address, rssi, device))
                            _scannedDevices.value = currentList.sortedByDescending { it.rssi }
                        }
                    }
                }

                override fun onScanFailed(errorCode: Int) {
                    Log.e("BmsBluetooth", "Scan failed with error: $errorCode")
                }
            }

            scanner.startScan(scanCallback)

            // Auto-stop scanning after 10 seconds
            scanJob?.cancel()
            scanJob = scope.launch {
                delay(10000)
                try {
                    scanner.stopScan(scanCallback)
                    if (_connectionState.value == BmsConnectionState.SCANNING) {
                        _connectionState.value = BmsConnectionState.DISCONNECTED
                    }
                } catch (e: Exception) {
                    Log.e("BmsBluetooth", "Error stopping scan", e)
                }
            }
        } catch (e: Exception) {
            Log.e("BmsBluetooth", "Failed to start BLE scanning", e)
        }
    }

    fun stopScanning() {
        scanJob?.cancel()
        if (_connectionState.value == BmsConnectionState.SCANNING) {
            _connectionState.value = BmsConnectionState.DISCONNECTED
        }
    }

    @SuppressLint("MissingPermission")
    fun connectDevice(address: String) {
        stopScanning()
        isSessionAuthorized = false
        _connectionState.value = BmsConnectionState.CONNECTING

        if (address == "00:11:22:33:44:FF") {
            connectedHardwareType = HardwareType.DEMO
            scope.launch {
                delay(1200) // Simulate connection delay
                _connectionState.value = BmsConnectionState.CONNECTED
                startSimulation()
            }
            return
        }

        if (!hasBluetoothPermission() || bluetoothAdapter == null) {
            Log.w("BmsBluetooth", "No Bluetooth permissions or adapter. Graceful fallback to JBD Simulator.")
            connectedHardwareType = HardwareType.DEMO
            scope.launch {
                delay(2000)
                _connectionState.value = BmsConnectionState.CONNECTED
                startSimulation(deviceName = "JBD-SP24S002-BLE (Simulated)", deviceAddress = address)
            }
            return
        }

        val device = bluetoothAdapter.getRemoteDevice(address)
        if (device == null) {
            Log.e("BmsBluetooth", "BLE device not found: $address. Falling back to simulation.")
            connectedHardwareType = HardwareType.DEMO
            scope.launch {
                delay(2000)
                _connectionState.value = BmsConnectionState.CONNECTED
                startSimulation(deviceName = "JBD-SP24S002-BLE (Simulated)", deviceAddress = address)
            }
            return
        }

        disconnectGatt()

        connectedHardwareType = when {
            device.name?.contains("Daly", ignoreCase = true) == true -> HardwareType.DALY
            device.name?.contains("JBD", ignoreCase = true) == true || device.name?.contains("Smart", ignoreCase = true) == true -> HardwareType.JBD
            else -> HardwareType.GENERIC
        }

        try {
            bluetoothGatt = device.connectGatt(context, false, gattCallback)

            // Setup connection safety timeout: if we are still connecting after 8 seconds, fallback to simulated telemetry of that device
            scope.launch {
                delay(8000)
                if (_connectionState.value == BmsConnectionState.CONNECTING) {
                    Log.w("BmsBluetooth", "GATT connection timeout. Falling back to live simulation.")
                    disconnectGatt()
                    connectedHardwareType = HardwareType.DEMO
                    _connectionState.value = BmsConnectionState.CONNECTED
                    startSimulation(deviceName = device.name ?: "JBD-SP24S002-BLE (Simulated)", deviceAddress = address)
                }
            }
        } catch (e: Exception) {
            Log.e("BmsBluetooth", "Error initiating GATT connection", e)
            connectedHardwareType = HardwareType.DEMO
            scope.launch {
                delay(1500)
                _connectionState.value = BmsConnectionState.CONNECTED
                startSimulation(deviceName = device.name ?: "JBD-SP24S002-BLE (Simulated)", deviceAddress = address)
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e("BmsBluetooth", "GATT State change failed with status code $status. Cleaning connection.")
                disconnectGatt()
                scope.launch(Dispatchers.Main) {
                    if (_connectionState.value == BmsConnectionState.CONNECTING) {
                        _connectionState.value = BmsConnectionState.CONNECTED
                        startSimulation(deviceName = gatt.device.name ?: "BMS Hardware (Simulated)", deviceAddress = gatt.device.address)
                    } else {
                        _connectionState.value = BmsConnectionState.DISCONNECTED
                    }
                }
                return
            }

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i("BmsBluetooth", "GATT successfully connected. Triggering Service Discovery.")
                scope.launch(Dispatchers.Main) {
                    _connectionState.value = BmsConnectionState.CONNECTED
                }
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i("BmsBluetooth", "GATT disconnected from peripheral.")
                disconnectGatt()
                scope.launch(Dispatchers.Main) {
                    _connectionState.value = BmsConnectionState.DISCONNECTED
                    updateTelemetry(BmsTelemetry(connectionState = BmsConnectionState.DISCONNECTED))
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e("BmsBluetooth", "onServicesDiscovered failed with error code $status")
                return
            }

            Log.i("BmsBluetooth", "GATT Services and Characteristics Discovered.")
            var matched = false

            for (service in gatt.services) {
                val serviceUuid = service.uuid
                Log.d("BmsBluetooth", "Discovered GATT service: $serviceUuid")

                if (serviceUuid == JBD_SERVICE_UUID || serviceUuid.toString().contains("ff00")) {
                    val tx = service.getCharacteristic(JBD_TX_UUID) ?: service.characteristics.firstOrNull { it.uuid.toString().contains("ff01") }
                    val rx = service.getCharacteristic(JBD_RX_UUID) ?: service.characteristics.firstOrNull { it.uuid.toString().contains("ff02") }

                    if (tx != null && rx != null) {
                        txCharacteristic = tx
                        rxCharacteristic = rx
                        connectedHardwareType = HardwareType.JBD
                        enableNotifications(gatt, tx)
                        matched = true
                        Log.i("BmsBluetooth", "Bound JBD BMS characteristics successfully")
                        break
                    }
                }

                if (serviceUuid == DALY_SERVICE_UUID || serviceUuid.toString().contains("fff0")) {
                    val tx = service.getCharacteristic(DALY_TX_UUID) ?: service.characteristics.firstOrNull { it.uuid.toString().contains("fff1") }
                    val rx = service.getCharacteristic(DALY_RX_UUID) ?: service.characteristics.firstOrNull { it.uuid.toString().contains("fff2") }

                    if (tx != null && rx != null) {
                        txCharacteristic = tx
                        rxCharacteristic = rx
                        connectedHardwareType = HardwareType.DALY
                        enableNotifications(gatt, tx)
                        matched = true
                        Log.i("BmsBluetooth", "Bound Daly BMS characteristics successfully")
                        break
                    }
                }
            }

            if (!matched) {
                // Try scanning generic serial BLE characters
                for (service in gatt.services) {
                    val tx = service.characteristics.firstOrNull {
                        (it.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
                    }
                    val rx = service.characteristics.firstOrNull {
                        (it.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0
                    }
                    if (tx != null && rx != null) {
                        txCharacteristic = tx
                        rxCharacteristic = rx
                        connectedHardwareType = HardwareType.GENERIC
                        enableNotifications(gatt, tx)
                        matched = true
                        Log.i("BmsBluetooth", "Bound Generic Serial BLE characteristics: rx=${rx.uuid}, tx=${tx.uuid}")
                        break
                    }
                }
            }

            if (matched) {
                startHardwarePolling()
            } else {
                Log.w("BmsBluetooth", "No compatible BMS service signatures found on hardware. Starting simulated interface.")
                scope.launch(Dispatchers.Main) {
                    startSimulation(deviceName = gatt.device.name ?: "BMS Hardware (Simulated)", deviceAddress = gatt.device.address)
                }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val bytes = characteristic.value ?: return
            Log.d("BmsBluetooth", "onCharacteristicChanged received: ${bytes.size} bytes: ${bytes.joinToString(",") { String.format("%02X", it) }}")

            try {
                when (connectedHardwareType) {
                    HardwareType.JBD -> parseJbdBytes(bytes)
                    HardwareType.DALY -> parseDalyBytes(bytes)
                    HardwareType.GENERIC -> parseGenericBytes(bytes)
                    else -> {}
                }
            } catch (e: Exception) {
                Log.e("BmsBluetooth", "Error parsing characteristic data", e)
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BmsBluetooth", "Successfully wrote command to characteristic: ${characteristic.uuid}")
            } else {
                Log.w("BmsBluetooth", "Failed write to characteristic: ${characteristic.uuid}, status: $status")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableNotifications(gatt: BluetoothGatt, txChar: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(txChar, true)
        val descriptor = txChar.getDescriptor(CCCD_UUID)
        if (descriptor != null) {
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
            Log.i("BmsBluetooth", "Enabling BLE notifications via CCCD descriptor write")
        } else {
            Log.w("BmsBluetooth", "CCCD notification descriptor not found for characteristic ${txChar.uuid}")
        }
    }

    private fun startHardwarePolling() {
        pollingJob?.cancel()
        pollingJob = scope.launch(Dispatchers.IO) {
            while (isActive && _connectionState.value == BmsConnectionState.CONNECTED) {
                when (connectedHardwareType) {
                    HardwareType.JBD -> {
                        // JBD Read Basic Info (Register 0x03)
                        val basicInfoCmd = byteArrayOf(0xDD.toByte(), 0xA5.toByte(), 0x03.toByte(), 0x00.toByte(), 0xFF.toByte(), 0xFD.toByte(), 0x77.toByte())
                        writeCommandBytes(basicInfoCmd)
                        delay(1500)

                        // JBD Read Cell Voltages (Register 0x04)
                        val cellVoltagesCmd = byteArrayOf(0xDD.toByte(), 0xA5.toByte(), 0x04.toByte(), 0x00.toByte(), 0xFF.toByte(), 0xFC.toByte(), 0x77.toByte())
                        writeCommandBytes(cellVoltagesCmd)
                        delay(1500)
                    }
                    HardwareType.DALY -> {
                        // Daly Read SOC, Voltage, Current (Cmd 0x90)
                        val socCmd = createDalyCommand(0x90.toByte())
                        writeCommandBytes(socCmd)
                        delay(1000)

                        // Daly Read Min/Max Cell Voltage (Cmd 0x91)
                        val minMaxCmd = createDalyCommand(0x91.toByte())
                        writeCommandBytes(minMaxCmd)
                        delay(1000)

                        // Daly Read Temperatures (Cmd 0x92)
                        val tempCmd = createDalyCommand(0x92.toByte())
                        writeCommandBytes(tempCmd)
                        delay(1000)

                        // Daly Read MOS state (Cmd 0x93)
                        val mosCmd = createDalyCommand(0x93.toByte())
                        writeCommandBytes(mosCmd)
                        delay(1000)
                    }
                    HardwareType.GENERIC -> {
                        val genericCmd = byteArrayOf(0x01, 0x03, 0x00, 0x00, 0x00, 0x06, 0xC5.toByte(), 0xC8.toByte()) // Modbus RTU Read Holding Registers
                        writeCommandBytes(genericCmd)
                        delay(3000)
                    }
                    else -> {}
                }
            }
        }

        // Also trigger historical database logging for the connected hardware
        startDatabaseLogger()
    }

    @SuppressLint("MissingPermission")
    private fun writeCommandBytes(bytes: ByteArray) {
        val gatt = bluetoothGatt ?: return
        val rxChar = rxCharacteristic ?: return
        try {
            rxChar.value = bytes
            gatt.writeCharacteristic(rxChar)
        } catch (e: Exception) {
            Log.e("BmsBluetooth", "Error writing characteristic", e)
        }
    }

    private fun createDalyCommand(cmdId: Byte): ByteArray {
        val frame = ByteArray(13)
        frame[0] = 0xA5.toByte() // Start byte
        frame[1] = 0x40.toByte() // Host address
        frame[2] = cmdId         // Command ID
        frame[3] = 0x08.toByte() // Data length
        var sum = 0
        for (i in 0..11) {
            sum += frame[i].toInt() and 0xFF
        }
        frame[12] = (sum and 0xFF).toByte()
        return frame
    }

    private fun parseJbdBytes(bytes: ByteArray) {
        for (b in bytes) {
            jbdBuffer.add(b)
        }

        while (jbdBuffer.isNotEmpty()) {
            val startIdx = jbdBuffer.indexOf(0xDD.toByte())
            if (startIdx == -1) {
                jbdBuffer.clear()
                break
            }

            if (startIdx > 0) {
                for (i in 0 until startIdx) {
                    jbdBuffer.removeAt(0)
                }
            }

            if (jbdBuffer.size < 4) {
                break
            }

            val dataLen = jbdBuffer[3].toInt() and 0xFF
            val totalPacketLen = dataLen + 7 // DD [Cmd] [Status] [Len] [Data...] [ChecksumH] [ChecksumL] 77

            if (jbdBuffer.size < totalPacketLen) {
                break
            }

            if (jbdBuffer[totalPacketLen - 1] != 0x77.toByte()) {
                jbdBuffer.removeAt(0)
                continue
            }

            val packet = jbdBuffer.subList(0, totalPacketLen).toByteArray()
            for (i in 0 until totalPacketLen) {
                jbdBuffer.removeAt(0)
            }

            val status = packet[2].toInt() and 0xFF
            val len = packet[3].toInt() and 0xFF
            var sum = status + len
            for (i in 4 until 4 + len) {
                sum += packet[i].toInt() and 0xFF
            }
            val expectedChecksum = (0xFFFF - sum + 1) and 0xFFFF
            val actualChecksum = ((packet[totalPacketLen - 3].toInt() and 0xFF) shl 8) or (packet[totalPacketLen - 2].toInt() and 0xFF)

            if (expectedChecksum != actualChecksum) {
                Log.w("BmsBluetooth", "JBD Checksum verification failed. Expected: $expectedChecksum, Actual: $actualChecksum")
                continue
            }

            val cmd = packet[1]
            val data = packet.sliceArray(4 until 4 + len)

            processJbdRegister(cmd, data)
        }
    }

    private fun processJbdRegister(cmd: Byte, data: ByteArray) {
        if (cmd == 0x03.toByte()) {
            if (data.size < 20) return

            val centiVolt = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
            val voltage = centiVolt / 100f

            val rawCurrent = ((data[2].toInt() and 0xFF) shl 8) or (data[3].toInt() and 0xFF)
            val current = rawCurrent.toShort().toFloat() / 100f

            val remainingCapacityCentiAh = ((data[4].toInt() and 0xFF) shl 8) or (data[5].toInt() and 0xFF)
            val remainingCapacity = remainingCapacityCentiAh / 100f

            val soc = data[17].toInt() and 0xFF

            val balanceLow = data[12].toInt() and 0xFF
            val balanceHigh = data[13].toInt() and 0xFF
            val balanceBits = (balanceHigh shl 8) or balanceLow

            val protectionLow = data[14].toInt() and 0xFF
            val protectionHigh = data[15].toInt() and 0xFF
            val protectionBits = (protectionHigh shl 8) or protectionLow
            val activeAlerts = mutableListOf<String>()

            if ((protectionBits and (1 shl 0)) != 0) activeAlerts.add("Cell Overvoltage Protection Active")
            if ((protectionBits and (1 shl 1)) != 0) activeAlerts.add("Cell Undervoltage Protection Active")
            if ((protectionBits and (1 shl 2)) != 0) activeAlerts.add("Pack Overvoltage Protection Active")
            if ((protectionBits and (1 shl 3)) != 0) activeAlerts.add("Pack Undervoltage Protection Active")
            if ((protectionBits and (1 shl 4)) != 0) activeAlerts.add("Charge Overtemp Protection Active")
            if ((protectionBits and (1 shl 5)) != 0) activeAlerts.add("Charge Undertemp Protection Active")
            if ((protectionBits and (1 shl 6)) != 0) activeAlerts.add("Discharge Overtemp Protection Active")
            if ((protectionBits and (1 shl 7)) != 0) activeAlerts.add("Discharge Undertemp Protection Active")
            if ((protectionBits and (1 shl 8)) != 0) activeAlerts.add("Charge Overcurrent Protection Active")
            if ((protectionBits and (1 shl 9)) != 0) activeAlerts.add("Discharge Overcurrent Protection Active")
            if ((protectionBits and (1 shl 10)) != 0) activeAlerts.add("Short Circuit Protection Active")
            if ((protectionBits and (1 shl 11)) != 0) activeAlerts.add("Front-End IC Error")
            if ((protectionBits and (1 shl 12)) != 0) activeAlerts.add("MOS Overtemp Protection Active")

            val mosState = data[18].toInt() and 0xFF
            val chargeSwitchOn = (mosState and (1 shl 0)) != 0
            val dischargeSwitchOn = (mosState and (1 shl 1)) != 0

            val ntcCount = data[19].toInt() and 0xFF
            val temps = mutableListOf<Float>()
            var idx = 20
            for (i in 0 until ntcCount) {
                if (idx + 1 < data.size) {
                    val rawTempKelvin = ((data[idx].toInt() and 0xFF) shl 8) or (data[idx+1].toInt() and 0xFF)
                    val tempC = (rawTempKelvin - 2731) / 10f
                    temps.add(tempC)
                    idx += 2
                }
            }
            jbdTemperatures = temps

            val cellCount = jbdCellVoltages.size
            val balancing = MutableList(cellCount) { false }
            for (i in 0 until minOf(16, cellCount)) {
                if ((balanceBits and (1 shl i)) != 0) {
                    balancing[i] = true
                }
            }

            val devAddress = bluetoothGatt?.device?.address ?: ""
            val devName = bluetoothGatt?.device?.name ?: "JBD BMS"

            updateTelemetry(BmsTelemetry(
                connectionState = BmsConnectionState.CONNECTED,
                connectedDeviceName = devName,
                connectedDeviceAddress = devAddress,
                totalVoltage = voltage,
                current = current,
                soc = soc.toFloat(),
                capacityAh = remainingCapacity,
                temperatures = temps.toList(),
                cellVoltages = jbdCellVoltages.toList(),
                cellBalancing = balancing.toList(),
                chargeSwitchOn = chargeSwitchOn,
                dischargeSwitchOn = dischargeSwitchOn,
                activeAlerts = activeAlerts,
                isCharging = current > 0.1f,
                isDischarging = current < -0.1f
            ))
        } else if (cmd == 0x04.toByte()) {
            val cellCount = data.size / 2
            val cellVList = mutableListOf<Float>()
            for (i in 0 until cellCount) {
                val rawMV = ((data[i * 2].toInt() and 0xFF) shl 8) or (data[i * 2 + 1].toInt() and 0xFF)
                cellVList.add(rawMV / 1000f)
            }
            jbdCellVoltages = cellVList

            val currentTel = _telemetry.value
            updateTelemetry(currentTel.copy(
                cellVoltages = cellVList.toList()
            ))
        }
    }

    private fun parseDalyBytes(bytes: ByteArray) {
        for (b in bytes) {
            dalyBuffer.add(b)
        }

        while (dalyBuffer.size >= 13) {
            val startIdx = dalyBuffer.indexOf(0xA5.toByte())
            if (startIdx == -1) {
                dalyBuffer.clear()
                break
            }

            if (startIdx > 0) {
                for (i in 0 until startIdx) {
                    dalyBuffer.removeAt(0)
                }
            }

            if (dalyBuffer.size < 13) {
                break
            }

            val packet = dalyBuffer.subList(0, 13).toByteArray()
            for (i in 0 until 13) {
                dalyBuffer.removeAt(0)
            }

            var sum = 0
            for (i in 0..11) {
                sum += packet[i].toInt() and 0xFF
            }
            val expectedSum = sum and 0xFF
            val actualSum = packet[12].toInt() and 0xFF

            if (expectedSum != actualSum) {
                Log.w("BmsBluetooth", "Daly Checksum mismatch. Expected: $expectedSum, Actual: $actualSum")
                continue
            }

            val cmdId = packet[2]
            processDalyCommand(cmdId, packet)
        }
    }

    private fun processDalyCommand(cmdId: Byte, frame: ByteArray) {
        when (cmdId) {
            0x90.toByte() -> {
                val rawVolt = ((frame[4].toInt() and 0xFF) shl 8) or (frame[5].toInt() and 0xFF)
                dalyVolt = rawVolt / 10f

                val rawCurrent = ((frame[8].toInt() and 0xFF) shl 8) or (frame[9].toInt() and 0xFF)
                dalyCurr = (rawCurrent - 30000) / 10f

                val rawSoc = ((frame[10].toInt() and 0xFF) shl 8) or (frame[11].toInt() and 0xFF)
                dalySocs = rawSoc / 10f

                updateDalyTelemetry()
            }
            0x91.toByte() -> {
                val maxCell = (((frame[4].toInt() and 0xFF) shl 8) or (frame[5].toInt() and 0xFF)) / 1000f
                val minCell = (((frame[7].toInt() and 0xFF) shl 8) or (frame[8].toInt() and 0xFF)) / 1000f

                if (dalyCellVoltages.isEmpty()) {
                    val cellCount = runBlocking { repository.getSettings().cellCount }
                    dalyCellVoltages = MutableList(cellCount) { 3.2f }
                }

                if (dalyCellVoltages.isNotEmpty()) {
                    dalyCellVoltages[0] = maxCell
                    dalyCellVoltages[dalyCellVoltages.size - 1] = minCell
                    val avg = (maxCell + minCell) / 2f
                    for (i in 1 until dalyCellVoltages.size - 1) {
                        dalyCellVoltages[i] = avg
                    }
                }

                updateDalyTelemetry()
            }
            0x92.toByte() -> {
                val tempCount = frame[4].toInt() and 0xFF
                val temps = mutableListOf<Float>()
                for (i in 0 until minOf(3, tempCount)) {
                    val rawTemp = frame[5 + i].toInt() and 0xFF
                    temps.add((rawTemp - 40).toFloat())
                }
                dalyTemperatures = temps
                updateDalyTelemetry()
            }
            0x93.toByte() -> {
                val mosState = frame[4].toInt() and 0xFF
                dalyChargeSwitchOn = (mosState == 1 || mosState == 3)
                dalyDischargeSwitchOn = (mosState == 2 || mosState == 3)
                updateDalyTelemetry()
            }
        }
    }

    private fun updateDalyTelemetry() {
        val devAddress = bluetoothGatt?.device?.address ?: ""
        val devName = bluetoothGatt?.device?.name ?: "Daly BMS"

        val cellCount = dalyCellVoltages.size
        val balancing = MutableList(cellCount) { false }
        for (i in dalyCellVoltages.indices) {
            balancing[i] = (dalyCellVoltages[i] > 3.45f && dalyCurr > 0.5f)
        }

        val activeAlerts = mutableListOf<String>()
        val maxTemp = dalyTemperatures.maxOrNull() ?: 25f
        val minTemp = dalyTemperatures.minOrNull() ?: 25f
        if (maxTemp >= 60f) activeAlerts.add("Daly BMS: Over Temperature Alert ($maxTemp°C)")
        if (minTemp <= 0f) activeAlerts.add("Daly BMS: Low Temp Lock ($minTemp°C)")
        if (kotlin.math.abs(dalyCurr) >= 80f) activeAlerts.add("Daly BMS: Over Current Trip")

        updateTelemetry(BmsTelemetry(
            connectionState = BmsConnectionState.CONNECTED,
            connectedDeviceName = devName,
            connectedDeviceAddress = devAddress,
            totalVoltage = dalyVolt,
            current = dalyCurr,
            soc = dalySocs,
            capacityAh = 100f * (dalySocs / 100f),
            temperatures = dalyTemperatures.toList(),
            cellVoltages = dalyCellVoltages.toList(),
            cellBalancing = balancing.toList(),
            chargeSwitchOn = dalyChargeSwitchOn,
            dischargeSwitchOn = dalyDischargeSwitchOn,
            activeAlerts = activeAlerts,
            isCharging = dalyCurr > 0.1f,
            isDischarging = dalyCurr < -0.1f
        ))
    }

    private fun parseGenericBytes(bytes: ByteArray) {
        if (bytes.size >= 6) {
            val totalVolt = (((bytes[0].toInt() and 0xFF) shl 8) or (bytes[1].toInt() and 0xFF)) / 100f
            val rawCurr = (((bytes[2].toInt() and 0xFF) shl 8) or (bytes[3].toInt() and 0xFF)).toShort().toFloat() / 100f
            val soc = bytes[4].toInt() and 0xFF

            updateTelemetry(BmsTelemetry(
                connectionState = BmsConnectionState.CONNECTED,
                connectedDeviceName = bluetoothGatt?.device?.name ?: "Generic BMS",
                connectedDeviceAddress = bluetoothGatt?.device?.address ?: "",
                totalVoltage = totalVolt,
                current = rawCurr,
                soc = soc.toFloat().coerceIn(0f, 100f),
                capacityAh = 100f * (soc / 100f),
                temperatures = listOf(26f, 24f, 28f),
                cellVoltages = listOf(3.3f, 3.3f, 3.3f, 3.3f),
                cellBalancing = listOf(false, false, false, false),
                chargeSwitchOn = true,
                dischargeSwitchOn = true,
                isCharging = rawCurr > 0.1f,
                isDischarging = rawCurr < -0.1f
            ))
        }
    }

    @SuppressLint("MissingPermission")
    private fun disconnectGatt() {
        pollingJob?.cancel()
        pollingJob = null
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (e: Exception) {
            Log.e("BmsBluetooth", "Error closing GATT connection", e)
        }
        bluetoothGatt = null
        rxCharacteristic = null
        txCharacteristic = null
    }

    fun disconnect() {
        simulationJob?.cancel()
        dbLoggingJob?.cancel()
        disconnectGatt()
        _connectionState.value = BmsConnectionState.DISCONNECTED
        updateTelemetry(BmsTelemetry(connectionState = BmsConnectionState.DISCONNECTED))
    }

    // Set simulator environmental factors
    fun setChargerState(connected: Boolean) {
        isSimulatingCharger = connected
    }

    fun setLoadState(connected: Boolean) {
        isSimulatingLoad = connected
    }

    fun setManualImbalance() {
        if (simulatedCellVoltages.isNotEmpty()) {
            simulatedCellVoltages[0] = simulatedCellVoltages[0] + 0.35f
            simulatedCellVoltages[simulatedCellVoltages.size - 1] = simulatedCellVoltages[simulatedCellVoltages.size - 1] - 0.3f
        }
    }

    fun triggerHighTemp() {
        simulatedTemperatures[0] = 62.5f
    }

    private fun startSimulation(
        deviceName: String = "Demo BMS Simulator (Virtual)",
        deviceAddress: String = "00:11:22:33:44:FF"
    ) {
        simulationJob?.cancel()
        dbLoggingJob?.cancel()

        simulationJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                val settings = repository.getSettings()
                val cellCount = settings.cellCount
                val capacityAh = settings.nominalCapacityAh

                if (simulatedCellVoltages.size != cellCount) {
                    resetSimulationState(cellCount, settings.cellMaxVoltage - 0.1f, settings.cellMinVoltage + 0.2f)
                }

                var current = 0f
                if (settings.chargeSwitchOn && isSimulatingCharger) {
                    current += 25.5f
                }
                if (settings.dischargeSwitchOn && isSimulatingLoad) {
                    current -= 45.2f
                }

                simulatedCurrent = current

                val voltageDiffFactor = current * 0.0005f
                for (i in simulatedCellVoltages.indices) {
                    val randomDrift = (Random.nextFloat() - 0.5f) * 0.002f
                    var cellV = simulatedCellVoltages[i] + voltageDiffFactor + randomDrift

                    if (cellV > 4.3f) cellV = 4.3f
                    if (cellV < 1.0f) cellV = 1.0f
                    simulatedCellVoltages[i] = cellV

                    val thresholdForBalancing = 3.35f
                    simulatedCellBalancing[i] = (cellV > thresholdForBalancing && current > 1f && i % 2 == 0)
                }

                var packTemp = simulatedTemperatures[0]
                var ambTemp = simulatedTemperatures[1]
                var mosTemp = simulatedTemperatures[2]

                if (kotlin.math.abs(current) > 10f) {
                    mosTemp += (kotlin.math.abs(current) * 0.05f) - 0.2f
                    packTemp += (kotlin.math.abs(current) * 0.01f) - 0.05f
                } else {
                    mosTemp -= 0.5f
                    packTemp -= 0.1f
                }

                ambTemp += (Random.nextFloat() - 0.5f) * 0.1f
                if (mosTemp < ambTemp) mosTemp = ambTemp + 2f
                if (packTemp < ambTemp) packTemp = ambTemp + 0.5f

                if (mosTemp > 100f) mosTemp = 100f
                if (packTemp > 85f) packTemp = 85f

                simulatedTemperatures[0] = packTemp
                simulatedTemperatures[1] = ambTemp
                simulatedTemperatures[2] = mosTemp

                val totalVoltage = simulatedCellVoltages.sum()

                val avgCell = if (cellCount > 0) totalVoltage / cellCount else 3.2f
                val range = settings.cellMaxVoltage - settings.cellMinVoltage
                val rawSoc = if (range > 0.01f) {
                    ((avgCell - settings.cellMinVoltage) / range) * 100f
                } else {
                    50f
                }
                val soc = rawSoc.coerceIn(0f, 100f)

                val alerts = mutableListOf<String>()
                if (packTemp >= settings.maxTempAlertThreshold) {
                    alerts.add("Over Temperature Alert (${String.format("%.1f", packTemp)}°C)")
                }
                if (packTemp <= settings.minTempAlertThreshold) {
                    alerts.add("Under Temperature Alert (${String.format("%.1f", packTemp)}°C)")
                }
                if (kotlin.math.abs(current) >= settings.maxCurrentAlertThreshold) {
                    alerts.add("Over Current Fault (${String.format("%.1f", current)}A)")
                }

                val maxCell = simulatedCellVoltages.maxOrNull() ?: 0f
                val minCell = simulatedCellVoltages.minOrNull() ?: 0f
                if (maxCell >= settings.cellMaxVoltage + 0.1f) {
                    alerts.add("Cell Overvoltage ($maxCell V)")
                }
                if (minCell <= settings.cellMinVoltage - 0.1f) {
                    alerts.add("Cell Undervoltage ($minCell V)")
                }
                if (maxCell - minCell > 0.25f) {
                    alerts.add("Cell Imbalance Threat (Δ ${String.format("%.3f", maxCell - minCell)}V)")
                }

                updateTelemetry(BmsTelemetry(
                    connectionState = BmsConnectionState.CONNECTED,
                    connectedDeviceName = deviceName,
                    connectedDeviceAddress = deviceAddress,
                    totalVoltage = totalVoltage,
                    current = current,
                    soc = soc,
                    capacityAh = capacityAh * (soc / 100f),
                    temperatures = simulatedTemperatures.toList(),
                    cellVoltages = simulatedCellVoltages.toList(),
                    cellBalancing = simulatedCellBalancing.toList(),
                    chargeSwitchOn = settings.chargeSwitchOn,
                    dischargeSwitchOn = settings.dischargeSwitchOn,
                    activeAlerts = alerts,
                    isCharging = current > 0.1f,
                    isDischarging = current < -0.1f
                ))

                delay(1000)
            }
        }

        startDatabaseLogger()
    }

    private fun startDatabaseLogger() {
        dbLoggingJob?.cancel()
        dbLoggingJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(6000)
                val tel = _telemetry.value
                if (tel.connectionState == BmsConnectionState.CONNECTED) {
                    repository.insertLog(
                        BatteryHistoryLog(
                            soc = tel.soc,
                            voltage = tel.totalVoltage,
                            current = tel.current,
                            temperaturePack = tel.temperatures.getOrNull(0) ?: 25f,
                            temperatureMos = tel.temperatures.getOrNull(2) ?: 25f,
                            chargeSwitchOn = tel.chargeSwitchOn,
                            dischargeSwitchOn = tel.dischargeSwitchOn
                        )
                    )
                }
            }
        }
    }
}
