// viewmodel/BleViewModel.kt
package com.example.signalint.viewmodel

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.signalint.data.ble.BleDeviceAlias
import com.example.signalint.data.ble.BleDeviceDisplayModel
import com.example.signalint.data.ble.BleDeviceEntity
import com.example.signalint.data.ble.ChatMessage
import com.example.signalint.manager.BleGattManager
import com.example.signalint.manager.BleScannerManager
import com.example.signalint.repository.BleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class BleViewModel @Inject constructor(
    private val repository: BleRepository,
    private val scannerManager: BleScannerManager,
    private val gattManager: BleGattManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<BleUiState>(BleUiState.Idle)
    val uiState: StateFlow<BleUiState> = _uiState.asStateFlow()

    // ✅ ADDED: Expose isScanning as a derived StateFlow
    val isScanning: StateFlow<Boolean> = _uiState
        .map { it is BleUiState.Scanning }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages = _chatMessages.asStateFlow()

    val connectionState = gattManager.connectionState

    private var scanJob: Job? = null

    val devices: StateFlow<List<BleDeviceDisplayModel>> = combine(
        repository.allDevices,
        repository.allAliases,
        _searchQuery.debounce(300)
    ) { rawDevices, aliases, query ->
        processDevices(rawDevices, aliases, query)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    init {
        observeGattMessages()
    }

    private fun processDevices(
        rawDevices: List<BleDeviceEntity>,
        aliases: List<BleDeviceAlias>,
        query: String
    ): List<BleDeviceDisplayModel> {
        val aliasMap = aliases.associateBy { it.macAddress }

        val uniqueDevices = rawDevices
            .groupBy { it.macAddress }
            .mapNotNull { (_, history) -> history.maxByOrNull { it.timestamp } }
            .sortedByDescending { it.timestamp }

        return uniqueDevices
            .map { device ->
                val alias = aliasMap[device.macAddress]
                val hasAlias = alias != null
                BleDeviceDisplayModel(
                    id = device.id,
                    macAddress = device.macAddress,
                    displayName = alias?.customName ?: device.deviceName,
                    originalName = device.deviceName,
                    rssi = device.rssi,
                    estimatedDistance = device.estimatedDistance,
                    timestamp = device.timestamp,
                    isAliased = hasAlias
                )
            }
            .filter { device ->
                if (query.isBlank()) true
                else device.displayName.contains(query, ignoreCase = true) ||
                        device.macAddress.contains(query, ignoreCase = true)
            }
    }

    fun startScan(durationMillis: Long = 10_000) {
        if (_uiState.value is BleUiState.Scanning) return

        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _uiState.value = BleUiState.Scanning

            try {
                withTimeout(durationMillis) {
                    scannerManager.startScan()
                        .catch { e ->
                            Timber.e(e, "Scan error")
                            _uiState.value = BleUiState.Error(e.message ?: "Scan failed")
                        }
                        .collect { result ->
                            result.onSuccess { scanResult ->
                                repository.saveScanResult(
                                    scanResult.deviceName,
                                    scanResult.macAddress,
                                    scanResult.rssi,
                                    scanResult.estimatedDistance
                                )
                            }.onFailure { e ->
                                Timber.e(e, "Scan result error")
                                _uiState.value = BleUiState.Error(e.message ?: "Scan error")
                            }
                        }
                }
            } catch (e: TimeoutCancellationException) {
                Timber.d("Scan completed after timeout")
            } finally {
                _uiState.value = BleUiState.Idle
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        _uiState.value = BleUiState.Idle
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun renameDevice(macAddress: String, newName: String) {
        viewModelScope.launch {
            try {
                repository.updateAlias(macAddress, newName)
                Timber.d("Device renamed: $macAddress -> $newName")
            } catch (e: Exception) {
                Timber.e(e, "Failed to rename device")
            }
        }
    }

    fun clearData() {
        viewModelScope.launch {
            try {
                repository.clearAllLogs()
                Timber.d("All logs cleared")
            } catch (e: Exception) {
                Timber.e(e, "Failed to clear logs")
            }
        }
    }

    fun connectToDevice(macAddress: String) {
        gattManager.connect(macAddress)
        Timber.d("Connecting to device: $macAddress")
    }

    fun disconnectFromDevice() {
        gattManager.disconnect()
        Timber.d("Disconnected from device")
    }

    fun sendChatMessage(message: String) {
        val success = gattManager.sendMessage(message)
        if (success) {
            addChatMessage(message, isSent = true)
        } else {
            Timber.w("Failed to send message")
        }
    }

    private fun addChatMessage(message: String, isSent: Boolean) {
        _chatMessages.value = _chatMessages.value + ChatMessage(
            message, System.currentTimeMillis(), isSent
        )
    }

    private fun observeGattMessages() {
        viewModelScope.launch {
            gattManager.receivedMessages.collect { messages ->
                messages.forEach { msg ->
                    addChatMessage(msg, isSent = false)
                }
            }
        }
    }

    fun exportLogToCsv(context: Context) {
        viewModelScope.launch {
            _uiState.value = BleUiState.Exporting

            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val fullLogs = repository.getFullLogForExport()
                    val fileName = "BLE_LOG_${System.currentTimeMillis()}.csv"
                    val file = File(context.cacheDir, fileName)
                    val writer = FileWriter(file)

                    writer.append("Timestamp,Time_Formatted,Device_Name,MAC_Address,RSSI_dBm,Distance_Meters\n")

                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                    fullLogs.forEach { log ->
                        val dateStr = dateFormat.format(Date(log.timestamp))
                        val safeName = log.deviceName.replace(",", " ")
                        writer.append(
                            "${log.timestamp},$dateStr,$safeName,${log.macAddress}," +
                                    "${log.rssi},${"%.2f".format(log.estimatedDistance)}\n"
                        )
                    }

                    writer.flush()
                    writer.close()

                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        file
                    )

                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/csv"
                        putExtra(Intent.EXTRA_SUBJECT, "BLE Signal Intelligence Log")
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }

                    val shareIntent = Intent.createChooser(intent, "Export BLE Data")
                    shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(shareIntent)

                    Timber.d("Exported ${fullLogs.size} BLE logs")
                }
            }

            _uiState.value = when {
                result.isSuccess -> BleUiState.ExportSuccess
                else -> {
                    Timber.e(result.exceptionOrNull(), "Export failed")
                    BleUiState.Error("Export failed: ${result.exceptionOrNull()?.message}")
                }
            }

            delay(2000)
            _uiState.value = BleUiState.Idle
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopScan()
        disconnectFromDevice()
        Timber.d("BleViewModel cleared")
    }
}

sealed class BleUiState {
    object Idle : BleUiState()
    object Scanning : BleUiState()
    object Exporting : BleUiState()
    object ExportSuccess : BleUiState()
    data class Error(val message: String) : BleUiState()
}
