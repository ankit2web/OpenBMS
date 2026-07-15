package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.bluetooth.BmsBluetoothManager
import com.example.bluetooth.BmsConnectionState
import com.example.bluetooth.BmsTelemetry
import com.example.bluetooth.ScanDevice
import com.example.data.BatteryHistoryLog
import com.example.data.BatterySettings
import com.example.data.BmsDatabase
import com.example.data.BmsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BmsViewModel(application: Application) : AndroidViewModel(application) {
    private val database = BmsDatabase.getDatabase(application)
    private val repository = BmsRepository(database.bmsDao())
    private val bluetoothManager = BmsBluetoothManager(application, repository)

    // States from Bluetooth manager
    val telemetry: StateFlow<BmsTelemetry> = bluetoothManager.telemetry
    val scannedDevices: StateFlow<List<ScanDevice>> = bluetoothManager.scannedDevices
    val connectionState: StateFlow<BmsConnectionState> = bluetoothManager.connectionState

    // States from Database
    val settingsState: StateFlow<BatterySettings?> = repository.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val historyLogs: StateFlow<List<BatteryHistoryLog>> = repository.recentLogsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        // Ensure default settings are initialized
        viewModelScope.launch {
            repository.getSettings()
        }
    }

    // Bluetooth scanning & connection
    fun startScan() {
        bluetoothManager.startScanning()
    }

    fun stopScan() {
        bluetoothManager.stopScanning()
    }

    fun connectDevice(address: String) {
        bluetoothManager.connectDevice(address)
    }

    fun disconnect() {
        bluetoothManager.disconnect()
    }

    // Toggle Charge / Discharge Switches
    fun toggleChargeSwitch(enabled: Boolean) {
        viewModelScope.launch {
            val currentSettings = repository.getSettings()
            repository.updateSettings(currentSettings.copy(chargeSwitchOn = enabled))
        }
    }

    fun toggleDischargeSwitch(enabled: Boolean) {
        viewModelScope.launch {
            val currentSettings = repository.getSettings()
            repository.updateSettings(currentSettings.copy(dischargeSwitchOn = enabled))
        }
    }

    // Chemistry configurations updates
    fun updateChemistry(
        chemistryName: String,
        cellCount: Int,
        capacityAh: Float,
        customMaxV: Float? = null,
        customMinV: Float? = null
    ) {
        viewModelScope.launch {
            val currentSettings = repository.getSettings()

            // Pre-defined chemistry parameters
            val maxCellV: Float
            val minCellV: Float
            when (chemistryName) {
                "Li-Ion" -> {
                    maxCellV = 4.20f
                    minCellV = 3.00f
                }
                "LiFePO4" -> {
                    maxCellV = 3.65f
                    minCellV = 2.50f
                }
                "LTO" -> {
                    maxCellV = 2.80f
                    minCellV = 1.50f
                }
                else -> { // Custom
                    maxCellV = customMaxV ?: currentSettings.cellMaxVoltage
                    minCellV = customMinV ?: currentSettings.cellMinVoltage
                }
            }

            repository.updateSettings(
                currentSettings.copy(
                    chemistry = chemistryName,
                    cellCount = cellCount.coerceIn(1, 24),
                    nominalCapacityAh = capacityAh.coerceAtLeast(1.0f),
                    cellMaxVoltage = maxCellV,
                    cellMinVoltage = minCellV
                )
            )
        }
    }

    // Safety Threshold Slider modifications
    fun updateSafetyLimits(maxTemp: Float, minTemp: Float, maxCurrent: Float) {
        viewModelScope.launch {
            val currentSettings = repository.getSettings()
            repository.updateSettings(
                currentSettings.copy(
                    maxTempAlertThreshold = maxTemp,
                    minTempAlertThreshold = minTemp,
                    maxCurrentAlertThreshold = maxCurrent
                )
            )
        }
    }

    // Demo control panels
    fun setSimulatedCharger(connected: Boolean) {
        bluetoothManager.setChargerState(connected)
    }

    fun setSimulatedLoad(connected: Boolean) {
        bluetoothManager.setLoadState(connected)
    }

    fun triggerSimulatedCellImbalance() {
        bluetoothManager.setManualImbalance()
    }

    fun triggerSimulatedHighTemp() {
        bluetoothManager.triggerHighTemp()
    }

    // History data wipe
    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    // --- BMS Security & Encryption Controls ---

    fun changeBmsPassword(oldPass: String, newPass: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val settings = repository.getSettings()
            if (settings.bmsPassword == oldPass) {
                if (newPass.length < 4) {
                    onResult(false, "Password must be at least 4 characters")
                    return@launch
                }
                repository.updateSettings(settings.copy(bmsPassword = newPass))
                onResult(true, "Password successfully updated!")
            } else {
                onResult(false, "Current password is incorrect")
            }
        }
    }

    fun toggleBmsEncryption(enabled: Boolean, passwordToConfirm: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val settings = repository.getSettings()
            if (settings.bmsPassword == passwordToConfirm) {
                repository.updateSettings(settings.copy(isBmsEncrypted = enabled))
                if (enabled) {
                    bluetoothManager.lockBms()
                    onResult(true, "BMS Encryption Activated! Secure handshake required.")
                } else {
                    bluetoothManager.unlockBms()
                    onResult(true, "BMS Encryption Deactivated. Connection is unencrypted.")
                }
            } else {
                onResult(false, "Incorrect security password confirmation")
            }
        }
    }

    fun authorizeBms(password: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val authorized = bluetoothManager.authorizeBms(password)
            onResult(authorized)
        }
    }
}

class BmsViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BmsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BmsViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
