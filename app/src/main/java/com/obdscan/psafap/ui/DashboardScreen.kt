package com.obdscan.psafap.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.obdscan.psafap.DashboardData

@Composable
fun DashboardScreen(
    connectionState: String,
    data: DashboardData,
    dtcCodes: List<String>,
    onRefreshDtcs: () -> Unit,
    onClearDtcs: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(connectionState, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))

        MetricRow("Régime moteur", data.rpm?.let { "$it tr/min" } ?: "—")
        MetricRow("Vitesse", data.speedKmh?.let { "$it km/h" } ?: "—")
        MetricRow("Température liquide de refroidissement", data.coolantTempC?.let { "$it °C" } ?: "—")
        MetricRow("Température admission", data.intakeTempC?.let { "$it °C" } ?: "—")
        MetricRow("Charge moteur", data.engineLoadPct?.let { "$it %" } ?: "—")
        MetricRow("Pression admission (boost)", data.boostKpa?.let { "$it kPa" } ?: "—")

        Spacer(Modifier.height(24.dp))
        Text("Codes défaut (DTC)", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.padding(vertical = 8.dp)) {
            Button(onClick = onRefreshDtcs) { Text("Lire les codes") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onClearDtcs) { Text("Effacer") }
        }

        if (dtcCodes.isEmpty()) {
            Text("Aucun code lu pour l'instant.")
        } else {
            LazyColumn {
                items(dtcCodes) { code ->
                    Text("• $code", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
