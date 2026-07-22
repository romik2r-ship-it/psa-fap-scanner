package com.obdscan.psafap.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.obdscan.psafap.obd.FapProfile
import com.obdscan.psafap.obd.FapReading
import com.obdscan.psafap.obd.PsaFapCommands
import com.obdscan.psafap.obd.RegenState

@Composable
fun FapScreen(
    profile: FapProfile,
    status: PsaFapCommands.FapStatus?,
    history: List<FapReading>,
    regenState: RegenState,
    isAutoRefreshOn: Boolean,
    onRefresh: () -> Unit,
    onToggleAutoRefresh: () -> Unit,
    onClearHistory: () -> Unit,
    onExportCsv: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollStateCompat())
            .padding(16.dp)
    ) {
        Text("Suivi FAP — ${profile.label}", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Ces valeurs utilisent des identifiants constructeur non officiels. " +
                "Si tout s'affiche à « — », le profil ne correspond probablement pas " +
                "à votre calculateur : ajustez-le dans le code (PsaFapProfiles).",
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(Modifier.height(16.dp))
        RegenBadge(regenState)

        Spacer(Modifier.height(16.dp))
        Row {
            Button(onClick = onRefresh) { Text("Relevé ponctuel") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onToggleAutoRefresh) {
                Text(if (isAutoRefreshOn) "Arrêter le suivi continu" else "Suivi continu (temps réel)")
            }
        }

        Spacer(Modifier.height(16.dp))
        MetricLine("Masse de suie estimée", status?.sootMassGrams?.let { "%.1f g".format(it) } ?: "—")
        MetricLine("Compteur de régénérations", status?.regenCount?.toString() ?: "—")
        MetricLine("Pression différentielle FAP", status?.diffPressureMbar?.let { "$it mbar" } ?: "—")
        MetricLine("Distance depuis dernière régén.", status?.distanceSinceRegenKm?.let { "$it km" } ?: "—")

        if (history.size >= 2) {
            Spacer(Modifier.height(24.dp))
            RealtimeLineChart(
                title = "Pression différentielle",
                values = history.mapNotNull { it.diffPressureMbar?.toFloat() },
                unit = "mbar"
            )
            Spacer(Modifier.height(16.dp))
            RealtimeLineChart(
                title = "Masse de suie estimée",
                values = history.mapNotNull { it.sootMassGrams?.toFloat() },
                unit = "g",
                lineColor = Color(0xFFB25E00)
            )
        }

        Spacer(Modifier.height(24.dp))
        Row {
            OutlinedButton(onClick = onExportCsv, enabled = history.isNotEmpty()) {
                Text("Exporter en CSV (${history.size} relevés)")
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onClearHistory, enabled = history.isNotEmpty()) {
                Text("Vider l'historique")
            }
        }

        if (status != null) {
            Spacer(Modifier.height(24.dp))
            Text("Réponses brutes (diagnostic)", style = MaterialTheme.typography.titleSmall)
            status.rawResponses.forEach { (key, value) ->
                Text("$key : $value", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun RegenBadge(state: RegenState) {
    val (label, color) = when (state) {
        RegenState.INCONNU -> "État régénération : inconnu" to Color(0xFF9E9E9E)
        RegenState.INACTIVE -> "Pas de régénération en cours" to Color(0xFF4CAF50)
        RegenState.EN_COURS -> "Régénération probablement EN COURS" to Color(0xFFFF9800)
        RegenState.VIENT_DE_SE_TERMINER -> "Régénération vient de se terminer" to Color(0xFF2196F3)
    }
    Box(
        Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Text(label, color = color, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun MetricLine(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

// Petit alias pour éviter un import direct incertain selon version Compose
@Composable
private fun rememberScrollStateCompat() = androidx.compose.foundation.rememberScrollState()
