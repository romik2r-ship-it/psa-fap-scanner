package com.obdscan.psafap.obd

import com.obdscan.psafap.bluetooth.Elm327Connection

data class ScanResult(val identifier: String, val rawResponse: String)

/**
 * Outil de découverte : interroge une plage d'identifiants (locaux 1 octet
 * en KWP, ou DID 2 octets en CAN-UDS) et remonte les réponses non vides.
 *
 * Usage typique pour calibrer un profil sans documentation publique :
 * 1. Lancer un scan moteur tournant, à froid.
 * 2. Noter les identifiants qui répondent avec une valeur plausible.
 * 3. Observer ces identifiants dans le temps (écran "Observer") pendant
 *    une régénération connue ou un trajet routier : celui dont la valeur
 *    chute nettement après une phase à régime soutenu est un bon candidat
 *    pour la masse de suie ; celui qui varie avec la charge moteur est un
 *    bon candidat pour la pression différentielle.
 */
object IdentifierScanner {

    /**
     * Scanne une plage d'identifiants locaux 1 octet (0x00 à 0xFF par défaut).
     * Un scan complet peut prendre plusieurs minutes en K-line (liaison lente) :
     * privilégier une plage réduite si possible.
     */
    suspend fun scanLocalIds(
        conn: Elm327Connection,
        profile: FapProfile,
        range: IntRange = 0x00..0xFF,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): List<ScanResult> {
        require(profile.accessMode == FapAccessMode.KWP_LOCAL_ID) {
            "scanLocalIds nécessite un profil en mode KWP_LOCAL_ID"
        }
        val results = mutableListOf<ScanResult>()
        PsaFapCommands.openExtendedSession(conn, profile)

        val ids = range.toList()
        ids.forEachIndexed { index, value ->
            val idHex = "%02X".format(value)
            val raw = PsaFapCommands.rawReadIdentifier(conn, profile, idHex)
            if (isMeaningfulResponse(raw)) {
                results.add(ScanResult(idHex, raw))
            }
            onProgress(index + 1, ids.size)
        }

        PsaFapCommands.closeSession(conn, profile)
        return results
    }

    /** Scanne une plage de DID 2 octets (mode CAN-UDS). */
    suspend fun scanCanDids(
        conn: Elm327Connection,
        profile: FapProfile,
        range: IntRange = 0x2E00..0x2EFF,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): List<ScanResult> {
        require(profile.accessMode == FapAccessMode.CAN_UDS_DID) {
            "scanCanDids nécessite un profil en mode CAN_UDS_DID"
        }
        val results = mutableListOf<ScanResult>()
        PsaFapCommands.openExtendedSession(conn, profile)

        val ids = range.toList()
        ids.forEachIndexed { index, value ->
            val idHex = "%04X".format(value)
            val raw = PsaFapCommands.rawReadIdentifier(conn, profile, idHex)
            if (isMeaningfulResponse(raw)) {
                results.add(ScanResult(idHex, raw))
            }
            onProgress(index + 1, ids.size)
        }

        PsaFapCommands.closeSession(conn, profile)
        return results
    }

    private fun isMeaningfulResponse(raw: String): Boolean {
        val clean = raw.trim()
        if (clean.isBlank()) return false
        if (clean.contains("NO DATA")) return false
        if (clean.contains("ERROR")) return false
        if (clean.contains("?")) return false
        // Réponse négative UDS/KWP typique : "7F <service> <code>"
        if (clean.replace(" ", "").startsWith("7F", ignoreCase = true)) return false
        return true
    }
}
