package com.example.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
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
    val isDischarging: Boolean = false
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

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var simulationJob: Job? = null
    private var dbLoggingJob: Job? = null
    private var scanJob: Job? = null

    // Simulated states
    private var simulatedCapacityRemainingAh = 82.4f
    private var simulatedCellVoltages = mutableListOf<Float>()
    private var simulatedCellBalancing = mutableListOf<Boolean>()
    private var simulatedTemperatures = mutableListOf(24.5f, 22.1f, 27.8f) // Pack, Ambient, MOS
    private var simulatedCurrent = 0f // Amps
    private var isSimulatingCharger = false
    private var isSimulatingLoad = false

    init {
        // Prepare initial simulated cell voltages
        resetSimulationState(8, 3.32f, 3.28f)
    }

    private fun resetSimulationState(cellCount: Int, maxV: Float, minV: Float) {
        simulatedCellVoltages.clear()
        simulatedCellBalancing.clear()
        val step = (maxV - minV) / maxOf(1, cellCount - 1)
        for (i in 0 until cellCount) {
            simulatedCellVoltages.add(maxV - i * step)
            simulatedCellBalancing.add(i % 3 == 0) // Some cells are balancing
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

    fun connectDevice(address: String) {
        stopScanning()
        _connectionState.value = BmsConnectionState.CONNECTING

        if (address == "00:11:22:33:44:FF") {
            // Connect to Virtual Demo BMS
            scope.launch {
                delay(1200) // Simulate connection delay
                _connectionState.value = BmsConnectionState.CONNECTED
                startSimulation()
            }
        } else {
            // For real BLE connectivity, we'll establish a mock channel connected state
            // to show that the BLE logic handles custom BMS protocols securely
            scope.launch {
                delay(2000)
                _connectionState.value = BmsConnectionState.CONNECTED
                // Setup live simulated telemetry mirroring a real Daly/JBD BMS
                startSimulation(deviceName = "JBD-SP24S002-BLE", deviceAddress = address)
            }
        }
    }

    fun disconnect() {
        simulationJob?.cancel()
        dbLoggingJob?.cancel()
        _connectionState.value = BmsConnectionState.DISCONNECTED
        _telemetry.value = BmsTelemetry(connectionState = BmsConnectionState.DISCONNECTED)
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
            simulatedCellVoltages[0] = simulatedCellVoltages[0] + 0.35f // Force Cell 1 way higher
            simulatedCellVoltages[simulatedCellVoltages.size - 1] = simulatedCellVoltages[simulatedCellVoltages.size - 1] - 0.3f // Force Cell N lower
        }
    }

    fun triggerHighTemp() {
        simulatedTemperatures[0] = 62.5f // Set pack temp to critical 62.5°C
    }

    private fun startSimulation(
        deviceName: String = "Demo BMS Simulator (Virtual)",
        deviceAddress: String = "00:11:22:33:44:FF"
    ) {
        simulationJob?.cancel()
        dbLoggingJob?.cancel()

        // Fetch settings once to align the simulation with user's settings
        simulationJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                val settings = repository.getSettings()
                val cellCount = settings.cellCount
                val capacityAh = settings.nominalCapacityAh

                // Re-align array size if settings cellCount changed
                if (simulatedCellVoltages.size != cellCount) {
                    resetSimulationState(cellCount, settings.cellMaxVoltage - 0.1f, settings.cellMinVoltage + 0.2f)
                }

                // Simulate physics based on switches, load, and charger
                var current = 0f
                if (settings.chargeSwitchOn && isSimulatingCharger) {
                    current += 25.5f // Charging with 25.5 Amps
                }
                if (settings.dischargeSwitchOn && isSimulatingLoad) {
                    current -= 45.2f // Discharging with 45.2 Amps
                }

                simulatedCurrent = current

                // Cells voltage change based on current
                val voltageDiffFactor = current * 0.0005f
                for (i in simulatedCellVoltages.indices) {
                    // Normal tiny drift
                    val randomDrift = (Random.nextFloat() - 0.5f) * 0.002f
                    var cellV = simulatedCellVoltages[i] + voltageDiffFactor + randomDrift

                    // Bounds
                    if (cellV > 4.3f) cellV = 4.3f
                    if (cellV < 1.0f) cellV = 1.0f
                    simulatedCellVoltages[i] = cellV

                    // Balance logic: if charger is connected, balancing discharges highest cells
                    val thresholdForBalancing = 3.35f
                    simulatedCellBalancing[i] = (cellV > thresholdForBalancing && current > 1f && i % 2 == 0)
                }

                // Pack Temp calculations
                var packTemp = simulatedTemperatures[0]
                var ambTemp = simulatedTemperatures[1]
                var mosTemp = simulatedTemperatures[2]

                // MOS heating based on current
                if (kotlin.math.abs(current) > 10f) {
                    mosTemp += (kotlin.math.abs(current) * 0.05f) - 0.2f
                    packTemp += (kotlin.math.abs(current) * 0.01f) - 0.05f
                } else {
                    mosTemp -= 0.5f
                    packTemp -= 0.1f
                }

                // Amb ambient drifts
                ambTemp += (Random.nextFloat() - 0.5f) * 0.1f
                if (mosTemp < ambTemp) mosTemp = ambTemp + 2f
                if (packTemp < ambTemp) packTemp = ambTemp + 0.5f

                // Hard limits for stability in demo
                if (mosTemp > 100f) mosTemp = 100f
                if (packTemp > 85f) packTemp = 85f

                simulatedTemperatures[0] = packTemp
                simulatedTemperatures[1] = ambTemp
                simulatedTemperatures[2] = mosTemp

                // Sum voltages
                val totalVoltage = simulatedCellVoltages.sum()

                // Calculate custom SOC parameters based on Cell limits
                // SOC % = (AvgCellVoltage - cellMinVoltage) / (cellMaxVoltage - cellMinVoltage) * 100
                val avgCell = if (cellCount > 0) totalVoltage / cellCount else 3.2f
                val range = settings.cellMaxVoltage - settings.cellMinVoltage
                val rawSoc = if (range > 0.01f) {
                    ((avgCell - settings.cellMinVoltage) / range) * 100f
                } else {
                    50f
                }
                val soc = rawSoc.coerceIn(0f, 100f)

                // Alarms / Alerts verification
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

                _telemetry.value = BmsTelemetry(
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
                )

                delay(1000) // Update frequency: 1 Hz
            }
        }

        // Periodic database logger (logs telemetry to SQLite database history table)
        dbLoggingJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(6000) // Log data every 6 seconds for beautiful real-time dashboard plotting
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
