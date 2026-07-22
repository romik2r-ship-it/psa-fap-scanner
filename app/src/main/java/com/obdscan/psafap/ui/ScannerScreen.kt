package com.obdscan.psafap.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.obdscan.psafap.obd.ScanResult

@Composable
fun ScannerScreen(
    isScanning: Boolean,
    scanProgress: Float,
    results: List<ScanResult>,
    watchedIdentifier: String?,
    isWatching: Boolean,
    watchedValues: List<Float>,
    onStartScan: () -> Unit,
    onCancelScan: () -> Unit,
    onWatch: (String) -> Unit,
    onStopWatch: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Scanner d'identifiants", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Découvre les identifiants qui répondent sur votre calculateur (mode " +
                "diagnostic KWP2000, service 0x21). Faites tourner le moteur pour " +
                "obtenir des valeurs représentatives. Un scan complet peut prendre " +
                "plusieurs minutes en K-line.",
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(Modifier.height(16.dp))
        if (isScanning) {
            LinearProgressIndicator(progress = { scanProgress }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onCancelScan) { Text("Annuler le scan") }
        } else {
            Button(onClick = onStartScan) { Text("Lancer le scan (00–FF)") }
        }

        if (watchedIdentifier != null) {
            Spacer(Modifier.height(24.dp))
            Text("Observation de l'identifiant $watchedIdentifier", style = MaterialTheme.typography.titleSmall)
            RealtimeLineChart(
                title = "Valeur brute (dernier octet)",
                values = watchedValues,
                unit = ""
            )
            Text(
                "Comparez cette courbe à votre conduite : une chute nette après un " +
                    "régime soutenu suggère une masse de suie ; une variation liée à " +
                    "la charge moteur suggère une pression.",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedButton(onClick = onStopWatch) { Text("Arrêter l'observation") }
        }

        Spacer(Modifier.height(24.dp))
        Text("Résultats (${results.size})", style = MaterialTheme.typography.titleMedium)

        LazyColumn(Modifier.weight(1f)) {
            items(results) { result ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("ID: ${result.identifier}", style = MaterialTheme.typography.titleSmall)
                            Text(result.rawResponse, style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { onWatch(result.identifier) }) {
                            Text("Observer")
                        }
                    }
                }
            }
        }
    }
}
