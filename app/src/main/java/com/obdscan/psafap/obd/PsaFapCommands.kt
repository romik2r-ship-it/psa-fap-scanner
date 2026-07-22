package com.obdscan.psafap.obd

import com.obdscan.psafap.bluetooth.Elm327Connection

/** Protocole physique/liaison à utiliser pour dialoguer avec le calculateur. */
enum class ObdProtocol(val elmCode: String, val label: String) {
    AUTO("0", "Détection automatique"),
    ISO9141_2("3", "ISO 9141-2 (K-line)"),
    KWP2000_SLOW_INIT("4", "ISO 14230-4 KWP2000 (init lent 5 bauds)"),
    KWP2000_FAST_INIT("5", "ISO 14230-4 KWP2000 (init rapide)"),
    CAN_11BIT_500K("6", "ISO 15765-4 CAN 11 bits / 500 kbps")
}

/**
 * Mode d'accès aux paramètres constructeur :
 *  - KWP_LOCAL_ID : service UDS/KWP 0x21 "ReadDataByLocalIdentifier", identifiant
 *    sur 1 octet. C'est le mode utilisé par les calculateurs plus anciens
 *    dialoguant en K-line (KWP2000), comme l'EDC16 sur 206 HDI.
 *  - CAN_UDS_DID : service 0x22 "ReadDataByIdentifier", identifiant sur 2 octets,
 *    utilisé par les calculateurs plus récents dialoguant en CAN-UDS.
 */
enum class FapAccessMode { KWP_LOCAL_ID, CAN_UDS_DID }

/**
 * Requêtes spécifiques au suivi du FAP (filtre à particules) sur les
 * moteurs PSA/Citroën HDI.
 *
 * IMPORTANT : contrairement aux PID Mode 01 standard, ces données ne sont
 * PAS normalisées publiquement. Le protocole (KWP2000 vs CAN-UDS), le mode
 * d'accès (identifiant local 1 octet vs DID 2 octets) et les identifiants
 * eux-mêmes varient selon le calculateur, son année et sa révision logicielle.
 */
data class FapProfile(
    val label: String,
    val protocol: ObdProtocol,
    val accessMode: FapAccessMode,
    val header: String? = null,       // en-tête CAN, uniquement pertinent si accessMode = CAN_UDS_DID
    val idSootMass: String? = null,
    val idRegenCounter: String? = null,
    val idDiffPressure: String? = null,
    val idDistanceSinceRegen: String? = null,
    val idRegenActive: String? = null,
    val notes: String = ""
)

object PsaFapProfiles {

    /**
     * Peugeot 206 HDI 1.6, calculateur Bosch EDC16C34.
     *
     * Ce calculateur dialogue en K-line / KWP2000 (pas en CAN-UDS), via le
     * service 0x21 avec des identifiants locaux d'un octet — mode utilisé
     * par les outils constructeur type PP2000/Lexia sur cette génération.
     *
     * Aucun identifiant local FAP n'est garanti ici : contrairement aux PID
     * OBD-II standard, ces valeurs ne sont pas documentées publiquement pour
     * ce calculateur. Utilisez l'écran "Scanner" pour découvrir les
     * identifiants qui répondent sur votre véhicule, puis reportez ceux qui
     * semblent pertinents (masse de suie, pression différentielle...) ici
     * une fois identifiés par observation (ex : valeur qui chute nettement
     * après une régénération).
     */
    val PEUGEOT_206_HDI_EDC16C34 = FapProfile(
        label = "Peugeot 206 HDI 1.6 — Bosch EDC16C34 (expérimental)",
        protocol = ObdProtocol.KWP2000_FAST_INIT,
        accessMode = FapAccessMode.KWP_LOCAL_ID,
        header = null,
        idSootMass = null,
        idRegenCounter = null,
        idDiffPressure = null,
        idDistanceSinceRegen = null,
        idRegenActive = null,
        notes = "Aucun identifiant local connu pour l'instant sur ce profil : " +
            "utilisez le Scanner pour les découvrir. Si l'init rapide (protocole 5) " +
            "échoue, essayez le protocole 4 (init lent 5 bauds) dans Réglages."
    )

    /** Exemple de profil pour un calculateur plus récent en CAN-UDS (autres modèles PSA). */
    val GENERIC_CAN_UDS_EDC17 = FapProfile(
        label = "HDI générique CAN-UDS (EDC17, à vérifier)",
        protocol = ObdProtocol.CAN_11BIT_500K,
        accessMode = FapAccessMode.CAN_UDS_DID,
        header = "18DA10F1",
        idSootMass = "2E39",
        idRegenCounter = "2E3A",
        idDiffPressure = "2E3B",
        idDistanceSinceRegen = "2E3C",
        notes = "Identifiants non garantis, à valider avec le Scanner également."
    )

    val PROFILES = listOf(PEUGEOT_206_HDI_EDC16C34, GENERIC_CAN_UDS_EDC17)
}

object PsaFapCommands {

    private fun parseHexPayload(raw: String): List<Int>? {
        val clean = raw.trim()
        if (clean.isBlank() || clean.contains("NO DATA") || clean.contains("ERROR")) return null
        val tokens = clean.split(" ").filter { it.matches(Regex("[0-9A-Fa-f]{2}")) }
        return if (tokens.isEmpty()) null else tokens.map { it.toInt(16) }
    }

    /** Construit la requête de lecture complète selon le mode d'accès du profil. */
    private fun buildReadCommand(profile: FapProfile, identifier: String): String =
        when (profile.accessMode) {
            FapAccessMode.KWP_LOCAL_ID -> "21$identifier"  // ex: "21 9A" -> "219A"
            FapAccessMode.CAN_UDS_DID -> "22$identifier"   // ex: "22 2E39" -> "222E39"
        }

    /**
     * Prépare la connexion pour dialoguer avec le calculateur cible du
     * profil : force le protocole, applique l'en-tête si pertinent (CAN
     * uniquement), puis ouvre une session diagnostic étendue.
     */
    suspend fun openExtendedSession(conn: Elm327Connection, profile: FapProfile) {
        conn.setProtocol(profile.protocol.elmCode)
        if (profile.accessMode == FapAccessMode.CAN_UDS_DID && profile.header != null) {
            conn.setHeader(profile.header)
        }
        conn.sendCommand("1003") // StartDiagnosticSession, extended
    }

    suspend fun closeSession(conn: Elm327Connection, profile: FapProfile) {
        conn.sendCommand("1001") // retour session par défaut
        if (profile.accessMode == FapAccessMode.CAN_UDS_DID) {
            conn.resetHeader()
        }
    }

    data class FapStatus(
        val timestampMs: Long = System.currentTimeMillis(),
        val sootMassGrams: Double? = null,
        val regenCount: Int? = null,
        val diffPressureMbar: Int? = null,
        val distanceSinceRegenKm: Int? = null,
        val regenActiveFromDid: Boolean? = null,
        val rawResponses: Map<String, String> = emptyMap()
    )

    /**
     * Lit l'état du FAP selon le profil fourni. Si un identifiant du profil
     * est null (non encore découvert), le champ correspondant reste null
     * sans requête inutile.
     */
    suspend fun readFapStatus(conn: Elm327Connection, profile: FapProfile): FapStatus {
        openExtendedSession(conn, profile)

        val rawSoot = profile.idSootMass?.let { conn.sendCommand(buildReadCommand(profile, it)) }
        val rawRegen = profile.idRegenCounter?.let { conn.sendCommand(buildReadCommand(profile, it)) }
        val rawPressure = profile.idDiffPressure?.let { conn.sendCommand(buildReadCommand(profile, it)) }
        val rawDistance = profile.idDistanceSinceRegen?.let { conn.sendCommand(buildReadCommand(profile, it)) }
        val rawRegenActive = profile.idRegenActive?.let { conn.sendCommand(buildReadCommand(profile, it)) }

        closeSession(conn, profile)

        val sootBytes = rawSoot?.let { parseHexPayload(it) }
        val regenBytes = rawRegen?.let { parseHexPayload(it) }
        val pressureBytes = rawPressure?.let { parseHexPayload(it) }
        val distanceBytes = rawDistance?.let { parseHexPayload(it) }
        val regenActiveBytes = rawRegenActive?.let { parseHexPayload(it) }

        // Formules de conversion à recalibrer une fois l'identifiant confirmé.
        val soot = sootBytes?.lastOrNull()?.let { it * 0.1 }
        val regen = regenBytes?.lastOrNull()
        val pressure = pressureBytes?.takeLast(2)?.let { (it[0] * 256) + it[1] }
        val distance = distanceBytes?.takeLast(2)?.let { (it[0] * 256) + it[1] }
        val regenActiveFromDid = regenActiveBytes?.lastOrNull()?.let { (it and 0x01) == 1 }

        val responses = buildMap {
            rawSoot?.let { put("soot", it) }
            rawRegen?.let { put("regen", it) }
            rawPressure?.let { put("pressure", it) }
            rawDistance?.let { put("distance", it) }
            rawRegenActive?.let { put("regenActive", it) }
        }

        return FapStatus(
            sootMassGrams = soot,
            regenCount = regen,
            diffPressureMbar = pressure,
            distanceSinceRegenKm = distance,
            regenActiveFromDid = regenActiveFromDid,
            rawResponses = responses
        )
    }

    /**
     * Envoie une requête de lecture brute pour un identifiant donné, selon
     * le mode d'accès du profil — utile pour le Scanner et l'observation
     * manuelle d'un identifiant candidat.
     */
    suspend fun rawReadIdentifier(conn: Elm327Connection, profile: FapProfile, identifier: String): String {
        return conn.sendCommand(buildReadCommand(profile, identifier))
    }
}
