// data/signal/SignalModels.kt
package com.example.signalint.data.signal

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "signal_log",
    indices = [Index(value = ["bssid", "timestamp"])]
)
data class SignalEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val ssid: String,
    val bssid: String,
    val signalLevel: Int,
    val frequency: Int,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "signal_device_alias",
    primaryKeys = ["bssid"]
)
data class SignalDeviceAlias(
    val bssid: String,
    val customName: String
)

data class SignalDisplayModel(
    val id: Int,
    val bssid: String,
    val displayName: String,
    val originalSsid: String,
    val signalLevel: Int,
    val frequency: Int,
    val timestamp: Long,
    val isAliased: Boolean
)
