// repository/BleRepository.kt
package com.example.signalint.repository

import com.example.signalint.data.ble.BleDeviceAlias
import com.example.signalint.data.ble.BleDeviceEntity
import kotlinx.coroutines.flow.Flow

interface BleRepository {
    val allDevices: Flow<List<BleDeviceEntity>>
    val allAliases: Flow<List<BleDeviceAlias>>

    suspend fun saveScanResult(
        deviceName: String,
        macAddress: String,
        rssi: Int,
        distance: Double
    )

    suspend fun updateAlias(macAddress: String, newName: String)
    suspend fun getFullLogForExport(): List<BleDeviceEntity>
    suspend fun clearAllLogs()
}
