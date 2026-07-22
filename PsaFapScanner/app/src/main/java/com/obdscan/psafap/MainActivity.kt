package com.obdscan.psafap

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.obdscan.psafap.ui.DashboardScreen
import com.obdscan.psafap.ui.DeviceListScreen
import com.obdscan.psafap.ui.FapScreen
import com.obdscan.psafap.ui.ScannerScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AppRoot()
            }
        }
    }
}

@Composable
fun AppRoot() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val bluetoothManager = remember {
        context.getSystemService(android.content.Context.BLUETOOTH_SERVICE) as BluetoothManager
    }
    val adapter: BluetoothAdapter? = remember { bluetoothManager.adapter }

    var hasPermissions by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results -> hasPermissions = results.values.all { it } }

    LaunchedEffect(Unit) {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        permissionLauncher.launch(perms)
    }

    val viewModel: ObdViewModel = viewModel()
    val navController = rememberNavController()

    Scaffold(
        bottomBar = { AppBottomBar(navController) }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            if (!hasPermissions) {
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Text("Autorisation Bluetooth requise pour continuer.")
                }
                return@Box
            }
            if (adapter == null || !adapter.isEnabled) {
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Text("Le Bluetooth est désactivé ou indisponible sur cet appareil.")
                }
                return@Box
            }

            NavHost(navController = navController, startDestination = "devices") {
                composable("devices") {
                    DeviceListScreen(adapter = adapter) { device ->
                        viewModel.connect(device)
                        navController.navigate("dashboard")
                    }
                }
                composable("dashboard") {
                    DashboardScreen(
                        connectionState = viewModel.connectionState,
                        data = viewModel.dashboard,
                        dtcCodes = viewModel.dtcCodes,
                        onRefreshDtcs = { viewModel.refreshDtcs() },
                        onClearDtcs = { viewModel.clearDtcs() }
                    )
                }
                composable("fap") {
                    val uri = viewModel.lastExportUri
                    LaunchedEffect(uri) {
                        if (uri != null) {
                            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/csv"
                                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(android.content.Intent.createChooser(shareIntent, "Partager le relevé CSV"))
                        }
                    }
                    FapScreen(
                        profile = viewModel.selectedProfile,
                        status = viewModel.fapStatus,
                        history = viewModel.fapHistory,
                        regenState = viewModel.regenState,
                        isAutoRefreshOn = viewModel.isFapAutoRefreshOn,
                        onRefresh = { viewModel.refreshFapStatus() },
                        onToggleAutoRefresh = { viewModel.toggleFapAutoRefresh() },
                        onClearHistory = { viewModel.clearFapHistory() },
                        onExportCsv = { viewModel.exportFapHistoryToCsv() }
                    )
                }
                composable("scanner") {
                    ScannerScreen(
                        isScanning = viewModel.isScanning,
                        scanProgress = viewModel.scanProgress,
                        results = viewModel.scanResults,
                        watchedIdentifier = viewModel.watchedIdentifier,
                        isWatching = viewModel.isWatching,
                        watchedValues = viewModel.watchedValues,
                        onStartScan = { viewModel.runIdentifierScan() },
                        onCancelScan = { viewModel.cancelScan() },
                        onWatch = { id -> viewModel.startWatching(id) },
                        onStopWatch = { viewModel.stopWatching() }
                    )
                }
            }
        }
    }
}

@Composable
fun AppBottomBar(navController: NavHostController) {
    NavigationBar {
        NavigationBarItem(
            selected = false,
            onClick = { navController.navigate("devices") },
            icon = {},
            label = { Text("Connexion") }
        )
        NavigationBarItem(
            selected = false,
            onClick = { navController.navigate("dashboard") },
            icon = {},
            label = { Text("Tableau de bord") }
        )
        NavigationBarItem(
            selected = false,
            onClick = { navController.navigate("fap") },
            icon = {},
            label = { Text("FAP") }
        )
        NavigationBarItem(
            selected = false,
            onClick = { navController.navigate("scanner") },
            icon = {},
            label = { Text("Scanner") }
        )
    }
}
