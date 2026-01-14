// manager/BleScannerManager.kt
package com.example.signalint.manager

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.pow

class BleScannerManager(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter = bluetoothManager?.adapter
    private val bleScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner

    companion object {
        private const val TAG = "BleScannerManager"
        private const val MEASURED_POWER_AT_1M = -59
        private const val PATH_LOSS_EXPONENT = 2.0
    }

    data class BleScanResult(
        val deviceName: String,
        val macAddress: String,
        val rssi: Int,
        val estimatedDistance: Double
    )

    @SuppressLint("MissingPermission")
    fun startScan(): Flow<Result<BleScanResult>> = callbackFlow {
        if (bleScanner == null) {
            trySend(Result.failure(IllegalStateException("BLE Scanner not available")))
            close()
            return@callbackFlow
        }

        if (!isBluetoothEnabled()) {
            trySend(Result.failure(IllegalStateException("Bluetooth is disabled")))
            close()
            return@callbackFlow
        }

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val deviceName = device.name ?: "Unknown Device"
                val macAddress = device.address
                val rssi = result.rssi
                val distance = calculateDistance(rssi)

                Log.d(TAG, "Found: $deviceName ($macAddress) RSSI: $rssi dBm, ~${"%.2f".format(distance)}m")

                trySend(
                    Result.success(
                        BleScanResult(deviceName, macAddress, rssi, distance)
                    )
                )
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Scan failed with error: $errorCode")
                trySend(Result.failure(Exception("Scan failed: $errorCode")))
            }
        }

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            bleScanner.startScan(null, scanSettings, scanCallback)
            Log.d(TAG, "BLE Scan started")
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied: ${e.message}")
            trySend(Result.failure(e))
            close()
            return@callbackFlow
        }

        awaitClose {
            try {
                bleScanner.stopScan(scanCallback)
                Log.d(TAG, "BLE Scan stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping scan: ${e.message}")
            }
        }
    }

    private fun calculateDistance(rssi: Int): Double {
        return if (rssi == 0) {
            -1.0
        } else {
            10.0.pow((MEASURED_POWER_AT_1M - rssi) / (10.0 * PATH_LOSS_EXPONENT))
        }
    }

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true
}
