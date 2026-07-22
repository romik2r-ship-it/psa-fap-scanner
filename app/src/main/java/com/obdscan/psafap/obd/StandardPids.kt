package com.obdscan.psafap.obd

import com.obdscan.psafap.bluetooth.Elm327Connection

/**
 * PID OBD-II Mode 01 standard (SAE J1979). Fonctionnent sur tout véhicule
 * conforme OBD-II, y compris les PSA/Citroën HDI.
 */
object StandardPids {

    private fun parseBytes(raw: String): List<Int>? {
        val clean = raw.replace("ERROR", "").trim()
        if (clean.isBlank() || clean.contains("NO DATA") || clean.contains("UNABLE")) return null
        val tokens = clean.split(" ").filter { it.matches(Regex("[0-9A-Fa-f]{2}")) }
        return tokens.map { it.toInt(16) }
    }

    suspend fun getRpm(conn: Elm327Connection): Int? {
        val bytes = parseBytes(conn.sendCommand("010C")) ?: return null
        val idx = bytes.indexOf(0x0C)
        if (idx == -1 || idx + 2 >= bytes.size) return null
        val a = bytes[idx + 1]
        val b = bytes[idx + 2]
        return ((a * 256) + b) / 4
    }

    suspend fun getSpeedKmh(conn: Elm327Connection): Int? {
        val bytes = parseBytes(conn.sendCommand("010D")) ?: return null
        val idx = bytes.indexOf(0x0D)
        if (idx == -1 || idx + 1 >= bytes.size) return null
        return bytes[idx + 1]
    }

    suspend fun getCoolantTempC(conn: Elm327Connection): Int? {
        val bytes = parseBytes(conn.sendCommand("0105")) ?: return null
        val idx = bytes.indexOf(0x05)
        if (idx == -1 || idx + 1 >= bytes.size) return null
        return bytes[idx + 1] - 40
    }

    suspend fun getIntakeAirTempC(conn: Elm327Connection): Int? {
        val bytes = parseBytes(conn.sendCommand("010F")) ?: return null
        val idx = bytes.indexOf(0x0F)
        if (idx == -1 || idx + 1 >= bytes.size) return null
        return bytes[idx + 1] - 40
    }

    /** Charge moteur calculée, en % (utile en complément du suivi FAP). */
    suspend fun getEngineLoadPct(conn: Elm327Connection): Int? {
        val bytes = parseBytes(conn.sendCommand("0104")) ?: return null
        val idx = bytes.indexOf(0x04)
        if (idx == -1 || idx + 1 >= bytes.size) return null
        return (bytes[idx + 1] * 100) / 255
    }

    /** Pression d'admission (boost turbo), en kPa — PID standard MAP. */
    suspend fun getIntakeManifoldPressureKpa(conn: Elm327Connection): Int? {
        val bytes = parseBytes(conn.sendCommand("010B")) ?: return null
        val idx = bytes.indexOf(0x0B)
        if (idx == -1 || idx + 1 >= bytes.size) return null
        return bytes[idx + 1]
    }

    data class DtcResult(val codes: List<String>, val raw: String)

    /** Lecture des codes défaut confirmés (Mode 03). */
    suspend fun readDtcs(conn: Elm327Connection): DtcResult {
        val raw = conn.sendCommand("03")
        val bytes = parseBytes(raw) ?: return DtcResult(emptyList(), raw)
        // On saute le premier octet (nombre de codes / mode) et on décode par paires
        val codes = mutableListOf<String>()
        var i = 1
        while (i + 1 < bytes.size) {
            val a = bytes[i]
            val b = bytes[i + 1]
            if (a == 0 && b == 0) { i += 2; continue }
            val firstChar = when ((a shr 6) and 0x03) {
                0 -> "P"; 1 -> "C"; 2 -> "B"; else -> "U"
            }
            val digit2 = (a shr 4) and 0x03
            val digit3 = a and 0x0F
            val digit4 = (b shr 4) and 0x0F
            val digit5 = b and 0x0F
            codes.add("$firstChar$digit2${digit3.toString(16)}${digit4.toString(16)}${digit5.toString(16)}".uppercase())
            i += 2
        }
        return DtcResult(codes, raw)
    }

    /** Efface les codes défaut et les données de freeze frame (Mode 04). */
    suspend fun clearDtcs(conn: Elm327Connection): String {
        return conn.sendCommand("04")
    }
}
