// repository/SignalRepositoryImpl.kt
package com.example.signalint.repository

import com.example.signalint.data.signal.SignalDao
import com.example.signalint.data.signal.SignalDeviceAlias
import com.example.signalint.data.signal.SignalEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SignalRepositoryImpl @Inject constructor(
    private val signalDao: SignalDao
) : SignalRepository {

    override val allSignals: Flow<List<SignalEntity>> = signalDao.getAllSignals()
    override val allAliases: Flow<List<SignalDeviceAlias>> = signalDao.getAllAliases()

    override suspend fun saveScanResult(ssid: String, bssid: String, level: Int, freq: Int) {
        val entity = SignalEntity(
            ssid = ssid,
            bssid = bssid,
            signalLevel = level,
            frequency = freq
        )
        signalDao.insertSignal(entity)
    }

    override suspend fun updateAlias(bssid: String, newName: String) {
        signalDao.insertAlias(SignalDeviceAlias(bssid, newName))
    }

    override suspend fun getFullLogForExport(): List<SignalEntity> {
        return signalDao.getAllSignalsList()
    }

    override suspend fun clearAllLogs() {
        signalDao.clearLogs()
    }
}
