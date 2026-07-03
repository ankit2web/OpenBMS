package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BmsDao {
    @Query("SELECT * FROM battery_settings WHERE id = 1")
    fun getSettingsFlow(): Flow<BatterySettings?>

    @Query("SELECT * FROM battery_settings WHERE id = 1")
    suspend fun getSettings(): BatterySettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSettings(settings: BatterySettings)

    @Query("SELECT * FROM battery_history_logs ORDER BY timestamp DESC LIMIT 200")
    fun getRecentLogsFlow(): Flow<List<BatteryHistoryLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: BatteryHistoryLog)

    @Query("DELETE FROM battery_history_logs")
    suspend fun clearHistory()
}
