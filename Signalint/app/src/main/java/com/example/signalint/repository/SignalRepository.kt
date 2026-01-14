// repository/SignalRepository.kt
package com.example.signalint.repository

import com.example.signalint.data.signal.SignalDeviceAlias
import com.example.signalint.data.signal.SignalEntity
import kotlinx.coroutines.flow.Flow

interface SignalRepository {
    val allSignals: Flow<List<SignalEntity>>
    val allAliases: Flow<List<SignalDeviceAlias>>

    suspend fun saveScanResult(ssid: String, bssid: String, level: Int, freq: Int)
    suspend fun updateAlias(bssid: String, newName: String)
    suspend fun getFullLogForExport(): List<SignalEntity>
    suspend fun clearAllLogs()
}
