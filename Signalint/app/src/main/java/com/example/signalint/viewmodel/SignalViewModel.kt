// viewmodel/SignalViewModel.kt
package com.example.signalint.viewmodel

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.signalint.data.signal.SignalDeviceAlias
import com.example.signalint.data.signal.SignalDisplayModel
import com.example.signalint.data.signal.SignalEntity
import com.example.signalint.manager.WifiScannerManager
import com.example.signalint.repository.SignalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class SignalViewModel @Inject constructor(
    private val repository: SignalRepository,
    private val scannerManager: WifiScannerManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<SignalUiState>(SignalUiState.Idle)
    val uiState: StateFlow<SignalUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    val signals: StateFlow<List<SignalDisplayModel>> = combine(
        repository.allSignals,
        repository.allAliases,
        _searchQuery.debounce(300)
    ) { rawSignals, aliases, query ->
        processSignals(rawSignals, aliases, query)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    init {
        setupScannerCallback()
    }

    private fun setupScannerCallback() {
        viewModelScope.launch {
            scannerManager.scanResults.collect { results ->
                results.forEach { result ->
                    repository.saveScanResult(
                        ssid = result.SSID ?: "",
                        bssid = result.BSSID,
                        level = result.level,
                        freq = result.frequency
                    )
                }
                _isScanning.value = false
                Timber.d("Received ${results.size} WiFi scan results")
            }
        }
    }

    private fun processSignals(
        rawSignals: List<SignalEntity>,
        aliases: List<SignalDeviceAlias>,
        query: String
    ): List<SignalDisplayModel> {
        val aliasMap = aliases.associateBy { it.bssid }

        val uniqueSignals = rawSignals
            .groupBy { it.bssid }
            .mapNotNull { (_, history) -> history.maxByOrNull { it.timestamp } }
            .sortedByDescending { it.timestamp }

        return uniqueSignals
            .map { signal ->
                val alias = aliasMap[signal.bssid]
                val hasAlias = alias != null
                val displayName = alias?.customName ?: signal.ssid.ifEmpty { "Hidden Network" }

                SignalDisplayModel(
                    id = signal.id,
                    bssid = signal.bssid,
                    displayName = displayName,
                    originalSsid = signal.ssid,
                    signalLevel = signal.signalLevel,
                    frequency = signal.frequency,
                    timestamp = signal.timestamp,
                    isAliased = hasAlias
                )
            }
            .filter { signal ->
                if (query.isBlank()) true
                else signal.displayName.contains(query, ignoreCase = true) ||
                        signal.bssid.contains(query, ignoreCase = true)
            }
    }

    fun startScan() {
        _isScanning.value = true
        scannerManager.startListening()
        val success = scannerManager.triggerScan()

        if (!success) {
            _isScanning.value = false
            Timber.w("WiFi scan trigger failed")
        } else {
            Timber.d("WiFi scan started")
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun renameTarget(bssid: String, newName: String) {
        viewModelScope.launch {
            try {
                repository.updateAlias(bssid, newName)
                Timber.d("Target renamed: $bssid -> $newName")
            } catch (e: Exception) {
                Timber.e(e, "Failed to rename target")
            }
        }
    }

    fun clearData() {
        viewModelScope.launch {
            try {
                repository.clearAllLogs()
                Timber.d("All signal logs cleared")
            } catch (e: Exception) {
                Timber.e(e, "Failed to clear logs")
            }
        }
    }

    fun exportLogToCsv(context: Context) {
        viewModelScope.launch {
            _uiState.value = SignalUiState.Exporting

            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val fullLogs = repository.getFullLogForExport()
                    val fileName = "SIGINT_LOG_${System.currentTimeMillis()}.csv"
                    val file = File(context.cacheDir, fileName)
                    val writer = FileWriter(file)

                    writer.append("Timestamp,Time_Formatted,SSID,BSSID,Frequency_MHz,Signal_dBm\n")

                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                    fullLogs.forEach { log ->
                        val dateStr = dateFormat.format(Date(log.timestamp))
                        val safeSsid = log.ssid.replace(",", " ")
                        writer.append(
                            "${log.timestamp},$dateStr,$safeSsid,${log.bssid}," +
                                    "${log.frequency},${log.signalLevel}\n"
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
                        putExtra(Intent.EXTRA_SUBJECT, "Signal Intelligence Log")
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }

                    val shareIntent = Intent.createChooser(intent, "Export Intelligence Data")
                    shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(shareIntent)

                    Timber.d("Exported ${fullLogs.size} signal logs")
                }
            }

            _uiState.value = when {
                result.isSuccess -> SignalUiState.ExportSuccess
                else -> {
                    Timber.e(result.exceptionOrNull(), "Export failed")
                    SignalUiState.Error("Export failed: ${result.exceptionOrNull()?.message}")
                }
            }

            // Reset to Idle after 2 seconds
            kotlinx.coroutines.delay(2000)
            _uiState.value = SignalUiState.Idle
        }
    }

    override fun onCleared() {
        super.onCleared()
        scannerManager.stopListening()
        Timber.d("SignalViewModel cleared")
    }
}

sealed class SignalUiState {
    object Idle : SignalUiState()
    object Scanning : SignalUiState()
    object Exporting : SignalUiState()
    object ExportSuccess : SignalUiState()
    data class Error(val message: String) : SignalUiState()
}
