// ui/SigIntScreen.kt
package com.example.signalint.ui

import android.Manifest
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
import com.example.signalint.viewmodel.SignalViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SigIntScreen(viewModel: SignalViewModel) {
    val context = LocalContext.current
    val signals by viewModel.signals.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()

    var showDialog by remember { mutableStateOf(false) }
    var selectedBssid by remember { mutableStateOf("") }
    var initialName by remember { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLoc = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        if (fineLoc) {
            viewModel.startScan()
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            "SIGINT COLLECTOR",
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF00E676)
                        )
                    },
                    actions = {
                        IconButton(onClick = { viewModel.exportLogToCsv(context) }) {
                            Icon(Icons.Default.Share, contentDescription = "Export CSV", tint = Color(0xFF03A9F4))
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
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                },
                containerColor = Color(0xFF00E676)
            ) {
                Icon(Icons.Default.Radar, contentDescription = "Scan")
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
                placeholder = { Text("Filter Unique Targets...", fontFamily = FontFamily.Monospace) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF1E1E1E),
                    unfocusedContainerColor = Color(0xFF1E1E1E),
                    focusedIndicatorColor = Color(0xFF00E676),
                    unfocusedIndicatorColor = Color.Gray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                singleLine = true
            )

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(signals, key = { it.bssid }) { signal ->
                    SignalListItem(
                        item = signal,
                        onClick = {
                            selectedBssid = signal.bssid
                            initialName = if (signal.isAliased) signal.displayName else ""
                            showDialog = true
                        }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }

    // ✅ FIXED: Use SignalRenameDialog instead of RenameDialog
    if (showDialog) {
        SignalRenameDialog(
            bssid = selectedBssid,
            currentName = initialName,
            onDismiss = { showDialog = false },
            onConfirm = { newName ->
                if (newName.isNotBlank()) {
                    viewModel.renameTarget(selectedBssid, newName)
                }
                showDialog = false
            }
        )
    }
}
