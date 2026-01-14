// data/signal/SignalDao.kt
package com.example.signalint.data.signal

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SignalDao {

    // --- Signal Logs ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSignal(signal: SignalEntity)

    @Query("SELECT * FROM signal_log ORDER BY timestamp DESC")
    fun getAllSignals(): Flow<List<SignalEntity>>

    @Query("SELECT * FROM signal_log ORDER BY timestamp DESC")
    suspend fun getAllSignalsList(): List<SignalEntity>

    @Query("DELETE FROM signal_log")
    suspend fun clearLogs()

    // --- Signal Aliases ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlias(alias: SignalDeviceAlias)

    @Query("SELECT * FROM signal_device_alias")
    fun getAllAliases(): Flow<List<SignalDeviceAlias>>

    @Query("SELECT customName FROM signal_device_alias WHERE bssid = :bssid")
    suspend fun getAlias(bssid: String): String?

    @Query("DELETE FROM signal_device_alias WHERE bssid = :bssid")
    suspend fun deleteAlias(bssid: String)
}
