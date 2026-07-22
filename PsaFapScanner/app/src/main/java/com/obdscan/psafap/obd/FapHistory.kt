package com.obdscan.psafap.obd

import android.content.Context
import androidx.core.content.FileProvider
import com.obdscan.psafap.DashboardData
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Un relevé combiné (FAP + données moteur au même instant) conservé en
 * mémoire pour l'affichage du graphique et l'export CSV.
 */
data class FapReading(
    val timestampMs: Long,
    val sootMassGrams: Double?,
    val diffPressureMbar: Int?,
    val regenCount: Int?,
    val distanceSinceRegenKm: Int?,
    val regenActiveFromDid: Boolean?,
    val rpm: Int?,
    val speedKmh: Int?,
    val coolantTempC: Int?
)

enum class RegenState { INCONNU, INACTIVE, EN_COURS, VIENT_DE_SE_TERMINER }

/**
 * Détecte l'état de régénération du FAP.
 *
 * 1. Si le profil expose un DID de statut reconnu (`regenActiveFromDid` non
 *    null dans le relevé le plus récent), on lui fait confiance directement.
 * 2. Sinon, heuristique de repli basée sur les conditions typiques d'une
 *    régénération HDI : chute significative et rapide de la masse de suie
 *    combinée à un régime moteur soutenu (régénération active en roulage),
 *    ou incrémentation du compteur de régénérations entre deux relevés
 *    (régénération qui vient de se terminer).
 *
 * Ces seuils sont des points de départ raisonnables, pas des valeurs
 * calibrées constructeur — à ajuster selon vos observations réelles.
 */
object RegenDetector {

    private const val SOOT_DROP_THRESHOLD_G = 2.0   // chute de masse de suie jugée significative
    private const val MIN_RPM_FOR_ACTIVE_REGEN = 1400 // régénération en roulage suppose un régime soutenu
    private const val WINDOW_MS = 5 * 60 * 1000L      // fenêtre d'observation glissante (5 min)

    fun detect(history: List<FapReading>): RegenState {
        if (history.isEmpty()) return RegenState.INCONNU
        val latest = history.last()

        // 1. DID direct si disponible et reconnu
        latest.regenActiveFromDid?.let {
            return if (it) RegenState.EN_COURS else RegenState.INACTIVE
        }

        // 2. Heuristique de repli
        val cutoff = latest.timestampMs - WINDOW_MS
        val window = history.filter { it.timestampMs >= cutoff }
        if (window.size < 2) return RegenState.INCONNU

        val first = window.first()
        val soots = window.mapNotNull { it.sootMassGrams }
        val regenCounts = window.mapNotNull { it.regenCount }

        // Compteur de régénérations qui vient d'augmenter → régénération terminée récemment
        if (regenCounts.size >= 2 && regenCounts.last() > regenCounts.first()) {
            return RegenState.VIENT_DE_SE_TERMINER
        }

        // Chute rapide de la masse de suie avec régime soutenu → régénération probablement en cours
        if (soots.size >= 2) {
            val drop = soots.first() - soots.last()
            val sustainedRpm = window.mapNotNull { it.rpm }.let { rpms ->
                rpms.isNotEmpty() && rpms.average() >= MIN_RPM_FOR_ACTIVE_REGEN
            }
            if (drop >= SOOT_DROP_THRESHOLD_G && sustainedRpm) {
                return RegenState.EN_COURS
            }
        }

        return RegenState.INACTIVE
    }
}

object CsvExporter {

    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.FRANCE)

    /**
     * Écrit l'historique en CSV dans le stockage privé de l'app et retourne
     * une Uri partageable (via FileProvider) prête pour un Intent.ACTION_SEND.
     */
    fun export(context: Context, history: List<FapReading>): android.net.Uri {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val fileName = "fap_releve_${System.currentTimeMillis()}.csv"
        val file = File(dir, fileName)

        FileWriter(file).use { writer ->
            writer.append("horodatage,masse_suie_g,pression_diff_mbar,compteur_regen,distance_depuis_regen_km,regen_actif,rpm,vitesse_kmh,temp_liquide_c\n")
            history.forEach { r ->
                writer.append(timestampFormat.format(Date(r.timestampMs))).append(',')
                writer.append(r.sootMassGrams?.toString() ?: "").append(',')
                writer.append(r.diffPressureMbar?.toString() ?: "").append(',')
                writer.append(r.regenCount?.toString() ?: "").append(',')
                writer.append(r.distanceSinceRegenKm?.toString() ?: "").append(',')
                writer.append(r.regenActiveFromDid?.toString() ?: "").append(',')
                writer.append(r.rpm?.toString() ?: "").append(',')
                writer.append(r.speedKmh?.toString() ?: "").append(',')
                writer.append(r.coolantTempC?.toString() ?: "").append('\n')
            }
        }

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
