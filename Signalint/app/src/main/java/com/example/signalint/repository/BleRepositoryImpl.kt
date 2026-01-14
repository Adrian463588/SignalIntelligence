// repository/BleRepositoryImpl.kt
package com.example.signalint.repository

import com.example.signalint.data.ble.BleDao
import com.example.signalint.data.ble.BleDeviceAlias
import com.example.signalint.data.ble.BleDeviceEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class BleRepositoryImpl @Inject constructor(
    private val bleDao: BleDao
) : BleRepository {

    override val allDevices: Flow<List<BleDeviceEntity>> = bleDao.getAllDevices()
    override val allAliases: Flow<List<BleDeviceAlias>> = bleDao.getAllAliases()

    override suspend fun saveScanResult(
        deviceName: String,
        macAddress: String,
        rssi: Int,
        distance: Double
    ) {
        val entity = BleDeviceEntity(
            deviceName = deviceName,
            macAddress = macAddress,
            rssi = rssi,
            estimatedDistance = distance
        )
        bleDao.insertDevice(entity)
    }

    override suspend fun updateAlias(macAddress: String, newName: String) {
        bleDao.insertAlias(BleDeviceAlias(macAddress, newName))
    }

    override suspend fun getFullLogForExport(): List<BleDeviceEntity> {
        return bleDao.getAllDevicesList()
    }

    override suspend fun clearAllLogs() {
        bleDao.clearLogs()
    }
}
