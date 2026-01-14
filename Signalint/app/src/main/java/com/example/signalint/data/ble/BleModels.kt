// data/ble/BleModels.kt
package com.example.signalint.data.ble

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ble_device_log",
    indices = [Index(value = ["macAddress", "timestamp"])]
)
data class BleDeviceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val deviceName: String,
    val macAddress: String,
    val rssi: Int,
    val estimatedDistance: Double,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "ble_device_alias",
    primaryKeys = ["macAddress"]
)
data class BleDeviceAlias(
    val macAddress: String,
    val customName: String
)

data class BleDeviceDisplayModel(
    val id: Int,
    val macAddress: String,
    val displayName: String,
    val originalName: String,
    val rssi: Int,
    val estimatedDistance: Double,
    val timestamp: Long,
    val isAliased: Boolean,
    val isConnectable: Boolean = true
)

data class ChatMessage(
    val message: String,
    val timestamp: Long,
    val isSent: Boolean
)
