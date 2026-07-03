package com.example.data

import kotlinx.coroutines.flow.Flow

class BmsRepository(private val bmsDao: BmsDao) {
    val settingsFlow: Flow<BatterySettings?> = bmsDao.getSettingsFlow()
    val recentLogsFlow: Flow<List<BatteryHistoryLog>> = bmsDao.getRecentLogsFlow()

    suspend fun getSettings(): BatterySettings {
        return bmsDao.getSettings() ?: BatterySettings().also {
            bmsDao.insertOrUpdateSettings(it)
        }
    }

    suspend fun updateSettings(settings: BatterySettings) {
        bmsDao.insertOrUpdateSettings(settings)
    }

    suspend fun insertLog(log: BatteryHistoryLog) {
        bmsDao.insertLog(log)
    }

    suspend fun clearHistory() {
        bmsDao.clearHistory()
    }
}
