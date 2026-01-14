// manager/WifiScannerManager.kt
package com.example.signalint.manager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber

class WifiScannerManager(private val context: Context) {

    private val wifiManager = context.applicationContext
        .getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val _scanResults = MutableSharedFlow<List<ScanResult>>(replay = 0)
    val scanResults = _scanResults.asSharedFlow()

    private var isReceiverRegistered = false

    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            val success = intent.getBooleanExtra(
                WifiManager.EXTRA_RESULTS_UPDATED, false
            )

            if (success) {
                try {
                    val results = wifiManager.scanResults
                    Timber.d("WiFi scan found ${results.size} networks")
                    kotlinx.coroutines.runBlocking {
                        _scanResults.emit(results)
                    }
                } catch (e: SecurityException) {
                    Timber.e(e, "Permission denied")
                }
            } else {
                Timber.w("WiFi scan failed")
            }
        }
    }

    fun startListening() {
        if (!isReceiverRegistered) {
            try {
                val intentFilter = IntentFilter(
                    WifiManager.SCAN_RESULTS_AVAILABLE_ACTION
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(
                        scanReceiver, intentFilter,
                        Context.RECEIVER_NOT_EXPORTED
                    )
                } else {
                    context.registerReceiver(scanReceiver, intentFilter)
                }

                isReceiverRegistered = true
                Timber.d("WiFi receiver registered")
            } catch (e: Exception) {
                Timber.e(e, "Failed to register receiver")
            }
        }
    }

    fun stopListening() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(scanReceiver)
                isReceiverRegistered = false
                Timber.d("WiFi receiver unregistered")
            } catch (e: IllegalArgumentException) {
                Timber.w("Receiver not registered")
            }
        }
    }

    fun triggerScan(): Boolean {
        return try {
            val success = wifiManager.startScan()
            Timber.d("WiFi scan triggered: $success")
            success
        } catch (e: Exception) {
            Timber.e(e, "Scan error")
            false
        }
    }
}
