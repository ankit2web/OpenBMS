package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "battery_settings")
data class BatterySettings(
    @PrimaryKey val id: Int = 1,
    val chemistry: String = "LiFePO4", // "LiFePO4", "Li-Ion", "LTO", "Custom"
    val cellCount: Int = 8, // Support up to 24S, default 8S (e.g., 24V or 12V pack)
    val nominalCapacityAh: Float = 100f, // Nominal pack capacity
    val cellMaxVoltage: Float = 3.65f, // Max cell voltage (SOC = 100%)
    val cellMinVoltage: Float = 2.5f, // Min cell voltage (SOC = 0%)
    val chargeSwitchOn: Boolean = true,
    val dischargeSwitchOn: Boolean = true,
    val maxTempAlertThreshold: Float = 55.0f, // Temp alert threshold in °C
    val minTempAlertThreshold: Float = -5.0f, // Low temp alert threshold in °C
    val maxCurrentAlertThreshold: Float = 80.0f, // Max safety current threshold in A
    val bmsPassword: String = "123456", // Default security password
    val isBmsEncrypted: Boolean = false // AES-secured/password-protected encryption state
)
