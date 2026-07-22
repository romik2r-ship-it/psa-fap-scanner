package com.obdscan.psafap.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@SuppressLint("MissingPermission")
@Composable
fun DeviceListScreen(
    adapter: BluetoothAdapter?,
    onDeviceSelected: (BluetoothDevice) -> Unit
) {
    val pairedDevices = remember { adapter?.bondedDevices?.toList() ?: emptyList() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Sélectionnez votre adaptateur ELM327", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Appairez d'abord l'adaptateur dans les réglages Bluetooth d'Android s'il n'apparaît pas ici.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(16.dp))

        if (pairedDevices.isEmpty()) {
            Text("Aucun périphérique appairé trouvé.")
        } else {
            LazyColumn {
                items(pairedDevices) { device ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onDeviceSelected(device) }
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(device.name ?: "Périphérique inconnu", style = MaterialTheme.typography.titleMedium)
                            Text(device.address, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
