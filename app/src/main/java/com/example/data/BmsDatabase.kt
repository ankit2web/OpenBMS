package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [BatteryHistoryLog::class, BatterySettings::class],
    version = 2,
    exportSchema = false
)
abstract class BmsDatabase : RoomDatabase() {
    abstract fun bmsDao(): BmsDao

    companion object {
        @Volatile
        private var INSTANCE: BmsDatabase? = null

        fun getDatabase(context: Context): BmsDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BmsDatabase::class.java,
                    "bms_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
