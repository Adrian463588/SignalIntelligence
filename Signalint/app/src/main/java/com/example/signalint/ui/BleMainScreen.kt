// ui/BleMainScreen.kt
package com.example.signalint.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.signalint.data.ble.BleDeviceDisplayModel
import com.example.signalint.viewmodel.BleViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BleMainScreen(viewModel: BleViewModel) {
    val context = LocalContext.current
    val devices by viewModel.devices.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState() // ✅ FIXED
    val chatMessages by viewModel.chatMessages.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()

    // ✅ FIXED: Explicit type annotation
    var showRenameDialog by remember { mutableStateOf(false) }
    var showChatDialog by remember { mutableStateOf(false) }
    var selectedDevice: BleDeviceDisplayModel? by remember { mutableStateOf(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions[Manifest.permission.BLUETOOTH_SCAN] == true &&
                    permissions[Manifest.permission.BLUETOOTH_CONNECT] == true &&
                    permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        } else {
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        }

        if (allGranted) {
            viewModel.startScan()
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            "BLE SCANNER",
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF00E676)
                        )
                    },
                    actions = {
                        IconButton(onClick = { viewModel.exportLogToCsv(context) }) {
                            Icon(Icons.Default.Share, contentDescription = "Export", tint = Color(0xFF03A9F4))
                        }

                        IconButton(onClick = { viewModel.clearData() }) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear", tint = Color.Red)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF121212)
                    )
                )

                if (isScanning) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF00E676),
                        trackColor = Color(0xFF121212)
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        arrayOf(
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        )
                    } else {
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                    }

                    permissionLauncher.launch(permissions)
                },
                containerColor = Color(0xFF00E676)
            ) {
                Icon(Icons.Default.Radar, contentDescription = "Scan", tint = Color.Black)
            }
        },
        containerColor = Color(0xFF121212)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            TextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                placeholder = { Text("Search devices...", fontFamily = FontFamily.Monospace) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF1E1E1E),
                    unfocusedContainerColor = Color(0xFF1E1E1E),
                    focusedIndicatorColor = Color(0xFF00E676),
                    unfocusedIndicatorColor = Color.Gray
                ),
                singleLine = true
            )

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(devices, key = { it.macAddress }) { device ->
                    BleDeviceListItem(
                        device = device,
                        onClick = {
                            selectedDevice = device
                            showRenameDialog = true
                        },
                        onConnectClick = {
                            selectedDevice = device
                            viewModel.connectToDevice(device.macAddress)
                            showChatDialog = true
                        }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }

    // ✅ FIXED: Use BleRenameDialog instead of RenameDialog
    if (showRenameDialog && selectedDevice != null) {
        BleRenameDialog(
            macAddress = selectedDevice!!.macAddress,
            currentName = if (selectedDevice!!.isAliased) selectedDevice!!.displayName else "",
            onDismiss = { showRenameDialog = false },
            onConfirm = { newName ->
                if (newName.isNotBlank()) {
                    viewModel.renameDevice(selectedDevice!!.macAddress, newName)
                }
                showRenameDialog = false
            }
        )
    }

    if (showChatDialog && selectedDevice != null) {
        ChatDialog(
            deviceName = selectedDevice!!.displayName,
            messages = chatMessages,
            onDismiss = {
                viewModel.disconnectFromDevice()
                showChatDialog = false
            },
            onSendMessage = { message ->
                viewModel.sendChatMessage(message)
            }
        )
    }
}
