package com.obdscan.psafap

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.obdscan.psafap.bluetooth.Elm327Connection
import com.obdscan.psafap.obd.CsvExporter
import com.obdscan.psafap.obd.FapProfile
import com.obdscan.psafap.obd.FapReading
import com.obdscan.psafap.obd.IdentifierScanner
import com.obdscan.psafap.obd.PsaFapCommands
import com.obdscan.psafap.obd.PsaFapProfiles
import com.obdscan.psafap.obd.RegenDetector
import com.obdscan.psafap.obd.RegenState
import com.obdscan.psafap.obd.ScanResult
import com.obdscan.psafap.obd.StandardPids
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class DashboardData(
    val rpm: Int? = null,
    val speedKmh: Int? = null,
    val coolantTempC: Int? = null,
    val intakeTempC: Int? = null,
    val engineLoadPct: Int? = null,
    val boostKpa: Int? = null
)

private const val MAX_CHART_POINTS = 120
private const val MAX_WATCH_POINTS = 200

class ObdViewModel(application: Application) : AndroidViewModel(application) {

    var connection: Elm327Connection? = null
        private set

    var connectionState by mutableStateOf("Déconnecté")
        private set

    var dashboard by mutableStateOf(DashboardData())
        private set

    var fapStatus by mutableStateOf<PsaFapCommands.FapStatus?>(null)
        private set

    var dtcCodes by mutableStateOf<List<String>>(emptyList())
        private set

    // Profil par défaut : Peugeot 206 HDI 1.6 / Bosch EDC16C34 (KWP2000, expérimental)
    var selectedProfile by mutableStateOf<FapProfile>(PsaFapProfiles.PEUGEOT_206_HDI_EDC16C34)

    val fapHistory = mutableStateListOf<FapReading>()

    var regenState by mutableStateOf(RegenState.INCONNU)
        private set

    var isFapAutoRefreshOn by mutableStateOf(false)
        private set

    var lastExportUri by mutableStateOf<Uri?>(null)
        private set

    // --- Scanner d'identifiants ---
    var isScanning by mutableStateOf(false)
        private set
    var scanProgress by mutableStateOf(0f) // 0..1
        private set
    val scanResults = mutableStateListOf<ScanResult>()
    private var scanJob: Job? = null

    // --- Observation d'un identifiant candidat ---
    var watchedIdentifier by mutableStateOf<String?>(null)
        private set
    val watchedValues = mutableStateListOf<Float>()
    var isWatching by mutableStateOf(false)
        private set
    private var watchJob: Job? = null

    private var pollingJob: Job? = null
    private var fapPollingJob: Job? = null

    fun connect(device: BluetoothDevice) {
        viewModelScope.launch {
            connectionState = "Connexion en cours..."
            try {
                val conn = Elm327Connection(device)
                conn.connect()
                connection = conn
                connectionState = "Connecté à ${safeDeviceName(device)}"
                startPolling()
            } catch (e: Exception) {
                connectionState = "Échec de connexion : ${e.message}"
            }
        }
    }

    @Suppress("MissingPermission")
    private fun safeDeviceName(device: BluetoothDevice): String =
        try { device.name ?: device.address } catch (_: SecurityException) { device.address }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            val conn = connection ?: return@launch
            while (isActive && conn.isConnected) {
                dashboard = DashboardData(
                    rpm = StandardPids.getRpm(conn),
                    speedKmh = StandardPids.getSpeedKmh(conn),
                    coolantTempC = StandardPids.getCoolantTempC(conn),
                    intakeTempC = StandardPids.getIntakeAirTempC(conn),
                    engineLoadPct = StandardPids.getEngineLoadPct(conn),
                    boostKpa = StandardPids.getIntakeManifoldPressureKpa(conn)
                )
                delay(500)
            }
        }
    }

    fun refreshFapStatus() {
        val conn = connection ?: return
        viewModelScope.launch {
            val status = PsaFapCommands.readFapStatus(conn, selectedProfile)
            fapStatus = status
            recordReading(status)
        }
    }

    fun toggleFapAutoRefresh() {
        if (isFapAutoRefreshOn) {
            fapPollingJob?.cancel()
            isFapAutoRefreshOn = false
            return
        }
        isFapAutoRefreshOn = true
        fapPollingJob = viewModelScope.launch {
            val conn = connection ?: return@launch
            while (isActive && conn.isConnected && isFapAutoRefreshOn) {
                val status = PsaFapCommands.readFapStatus(conn, selectedProfile)
                fapStatus = status
                recordReading(status)
                delay(4000)
            }
        }
    }

    private fun recordReading(status: PsaFapCommands.FapStatus) {
        val reading = FapReading(
            timestampMs = status.timestampMs,
            sootMassGrams = status.sootMassGrams,
            diffPressureMbar = status.diffPressureMbar,
            regenCount = status.regenCount,
            distanceSinceRegenKm = status.distanceSinceRegenKm,
            regenActiveFromDid = status.regenActiveFromDid,
            rpm = dashboard.rpm,
            speedKmh = dashboard.speedKmh,
            coolantTempC = dashboard.coolantTempC
        )
        fapHistory.add(reading)
        if (fapHistory.size > MAX_CHART_POINTS) {
            fapHistory.removeAt(0)
        }
        regenState = RegenDetector.detect(fapHistory)
    }

    fun clearFapHistory() {
        fapHistory.clear()
        regenState = RegenState.INCONNU
    }

    fun exportFapHistoryToCsv() {
        if (fapHistory.isEmpty()) return
        lastExportUri = CsvExporter.export(getApplication(), fapHistory.toList())
    }

    /** Lance un scan d'identifiants pour découvrir ceux qui répondent sur le calculateur connecté. */
    fun runIdentifierScan(range: IntRange = 0x00..0xFF) {
        val conn = connection ?: return
        if (isScanning) return
        scanJob?.cancel()
        scanResults.clear()
        isScanning = true
        scanProgress = 0f
        scanJob = viewModelScope.launch {
            try {
                val results = IdentifierScanner.scanLocalIds(conn, selectedProfile, range) { current, total ->
                    scanProgress = current.toFloat() / total.toFloat()
                }
                scanResults.addAll(results)
            } finally {
                isScanning = false
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        isScanning = false
    }

    /** Observe en continu un identifiant candidat (issu du scan) pour l'afficher sur un graphique. */
    fun startWatching(identifier: String) {
        stopWatching()
        watchedIdentifier = identifier
        watchedValues.clear()
        isWatching = true
        watchJob = viewModelScope.launch {
            val conn = connection ?: return@launch
            PsaFapCommands.openExtendedSession(conn, selectedProfile)
            while (isActive && conn.isConnected && isWatching) {
                val raw = PsaFapCommands.rawReadIdentifier(conn, selectedProfile, identifier)
                val bytes = raw.trim().split(" ").filter { it.matches(Regex("[0-9A-Fa-f]{2}")) }
                val lastByte = bytes.lastOrNull()?.toIntOrNull(16)
                if (lastByte != null) {
                    watchedValues.add(lastByte.toFloat())
                    if (watchedValues.size > MAX_WATCH_POINTS) watchedValues.removeAt(0)
                }
                delay(1000)
            }
            PsaFapCommands.closeSession(conn, selectedProfile)
        }
    }

    fun stopWatching() {
        watchJob?.cancel()
        isWatching = false
    }

    fun refreshDtcs() {
        val conn = connection ?: return
        viewModelScope.launch {
            dtcCodes = StandardPids.readDtcs(conn).codes
        }
    }

    fun clearDtcs() {
        val conn = connection ?: return
        viewModelScope.launch {
            StandardPids.clearDtcs(conn)
            refreshDtcs()
        }
    }

    fun disconnect() {
        pollingJob?.cancel()
        fapPollingJob?.cancel()
        scanJob?.cancel()
        watchJob?.cancel()
        isFapAutoRefreshOn = false
        isScanning = false
        isWatching = false
        connection?.close()
        connection = null
        connectionState = "Déconnecté"
    }

    override fun onCleared() {
        disconnect()
    }
}
