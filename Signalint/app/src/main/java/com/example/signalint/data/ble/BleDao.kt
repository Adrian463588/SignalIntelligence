// data/ble/BleDao.kt
package com.example.signalint.data.ble

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BleDao {

    // --- Device Logs ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevice(device: BleDeviceEntity)

    @Query("SELECT * FROM ble_device_log ORDER BY timestamp DESC")
    fun getAllDevices(): Flow<List<BleDeviceEntity>>

    @Query("SELECT * FROM ble_device_log ORDER BY timestamp DESC")
    suspend fun getAllDevicesList(): List<BleDeviceEntity>

    @Query("DELETE FROM ble_device_log")
    suspend fun clearLogs()

    // --- Device Aliases ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlias(alias: BleDeviceAlias)

    @Query("SELECT * FROM ble_device_alias")
    fun getAllAliases(): Flow<List<BleDeviceAlias>>

    @Query("SELECT customName FROM ble_device_alias WHERE macAddress = :macAddress")
    suspend fun getAlias(macAddress: String): String?

    @Query("DELETE FROM ble_device_alias WHERE macAddress = :macAddress")
    suspend fun deleteAlias(macAddress: String)
}
