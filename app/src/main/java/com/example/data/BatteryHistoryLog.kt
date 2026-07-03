package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "battery_history_logs")
data class BatteryHistoryLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val soc: Float,
    val voltage: Float,
    val current: Float,
    val temperaturePack: Float,
    val temperatureMos: Float,
    val chargeSwitchOn: Boolean,
    val dischargeSwitchOn: Boolean
)
