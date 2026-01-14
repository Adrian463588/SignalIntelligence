// data/AppDatabase.kt
package com.example.signalint.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.signalint.data.ble.BleDao
import com.example.signalint.data.ble.BleDeviceAlias
import com.example.signalint.data.ble.BleDeviceEntity
import com.example.signalint.data.signal.SignalDao
import com.example.signalint.data.signal.SignalDeviceAlias
import com.example.signalint.data.signal.SignalEntity

@Database(
    entities = [
        BleDeviceEntity::class,
        BleDeviceAlias::class,
        SignalEntity::class,
        SignalDeviceAlias::class
    ],
    version = 2, // ✅ INCREMENT THIS (was 1)
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bleDao(): BleDao
    abstract fun signalDao(): SignalDao

    companion object {
        private const val DATABASE_NAME = "signal_intelligence.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                .fallbackToDestructiveMigration() // For development only
                .build()
        }

        // For testing only
        internal fun clearInstance() {
            INSTANCE = null
        }
    }
}
