package com.obdscan.psafap.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Gère la connexion série (SPP) vers un adaptateur ELM327 et l'échange
 * de commandes AT / requêtes OBD.
 *
 * Un seul thread logique lit/écrit à la fois grâce au Mutex : ne jamais
 * appeler sendCommand() en parallèle depuis plusieurs coroutines sans lui.
 */
class Elm327Connection(private val device: BluetoothDevice) {

    companion object {
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val DEFAULT_TIMEOUT_MS = 3000L
    }

    private var socket: BluetoothSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null
    private val mutex = Mutex()

    val isConnected: Boolean
        get() = socket?.isConnected == true

    @SuppressLint("MissingPermission")
    suspend fun connect() = withContext(Dispatchers.IO) {
        val sock = device.createRfcommSocketToServiceRecord(SPP_UUID)
        sock.connect()
        socket = sock
        input = sock.inputStream
        output = sock.outputStream
        initAdapter()
    }

    private suspend fun initAdapter() {
        sendCommand("ATZ")    // reset
        sendCommand("ATE0")   // désactive l'écho
        sendCommand("ATL0")   // pas de line feed
        sendCommand("ATS0")   // pas d'espaces superflus dans certaines réponses
        sendCommand("ATH1")   // affiche les en-têtes (utile pour le mode diagnostic)
        sendCommand("ATSP0")  // détection auto du protocole par défaut au démarrage
    }

    /**
     * Force un protocole précis (persistant jusqu'au prochain changement).
     * Codes utiles pour PSA :
     *  - "0" : auto
     *  - "3" : ISO 9141-2 (K-line)
     *  - "4" : ISO 14230-4 KWP2000, init lent 5 bauds
     *  - "5" : ISO 14230-4 KWP2000, init rapide
     *  - "6" : ISO 15765-4 CAN 11 bits / 500 kbps
     * Sur les calculateurs anciens type EDC16, le K-line (4 ou 5) est souvent
     * requis ; ATSP0 seul ne le détecte pas toujours de façon fiable.
     */
    suspend fun setProtocol(code: String) {
        sendCommand("ATSP$code")
    }

    /** Renvoie le protocole actuellement utilisé par l'ELM327 (pour affichage/debug). */
    suspend fun describeProtocol(): String {
        return sendCommand("ATDPN")
    }

    /**
     * Configure l'en-tête cible. Utile en CAN-UDS pour viser un calculateur
     * précis (moteur, BSI...). En K-line/KWP2000, généralement inutile car
     * le port OBD adresse directement le calculateur moteur — laisser le
     * profil KWP sans en-tête dans ce cas.
     */
    suspend fun setHeader(header: String) {
        sendCommand("ATSH$header")
    }

    suspend fun resetHeader() {
        sendCommand("ATSH7E0")
    }

    /**
     * Envoie une commande brute et attend la réponse jusqu'au prompt '>'.
     */
    suspend fun sendCommand(command: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): String =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    withTimeout(timeoutMs) {
                        val out = output ?: throw IOException("Non connecté")
                        val inp = input ?: throw IOException("Non connecté")

                        out.write("$command\r".toByteArray())
                        out.flush()

                        val response = StringBuilder()
                        val buffer = ByteArray(1024)
                        while (true) {
                            val bytesRead = inp.read(buffer)
                            if (bytesRead == -1) break
                            response.append(String(buffer, 0, bytesRead))
                            if (response.contains('>')) break
                        }
                        response.toString()
                            .replace("\r", " ")
                            .replace(">", "")
                            .replace("SEARCHING...", "")
                            .trim()
                    }
                } catch (e: Exception) {
                    "ERROR: ${e.message}"
                }
            }
        }

    fun close() {
        try { input?.close() } catch (_: IOException) {}
        try { output?.close() } catch (_: IOException) {}
        try { socket?.close() } catch (_: IOException) {}
        socket = null
    }
}
