# Obtenir un APK installable sans rien installer localement

Ce projet contient un workflow GitHub Actions (`.github/workflows/build-apk.yml`)
qui compile automatiquement un APK debug (installable directement sur un
téléphone Android, sans passer par le Play Store) à chaque envoi du code sur
GitHub. Vous n'avez besoin ni d'Android Studio, ni du SDK, ni de Gradle sur
votre machine.

## Étapes (10 minutes, une seule fois)

1. **Créer un compte GitHub** si vous n'en avez pas déjà un : https://github.com/signup

2. **Créer un nouveau dépôt** (repository) :
   - Sur https://github.com/new
   - Nom au choix, ex. `psa-fap-scanner`
   - Visibilité "Private" ou "Public", peu importe
   - Ne pas cocher "Add a README" (le projet en a déjà un)

3. **Envoyer le contenu du dossier `PsaFapScanner` dans ce dépôt.** Deux façons :

   **Sans ligne de commande (le plus simple)** :
   - Sur la page du dépôt fraîchement créé, cliquez sur "uploading an existing file"
   - Glissez-déposez tout le contenu du dossier `PsaFapScanner` (décompressez d'abord l'archive .zip)
   - Validez ("Commit changes")

   **Avec Git en ligne de commande**, depuis le dossier `PsaFapScanner` décompressé :
   ```bash
   git init
   git add .
   git commit -m "Premier envoi"
   git branch -M main
   git remote add origin https://github.com/VOTRE-COMPTE/psa-fap-scanner.git
   git push -u origin main
   ```

4. **Suivre la compilation** :
   - Sur la page du dépôt, onglet **Actions**
   - Le workflow "Build APK" démarre automatiquement (icône jaune = en cours, ~3-5 minutes)
   - Une fois vert ✅, cliquez sur l'exécution la plus récente

5. **Télécharger l'APK** :
   - En bas de la page de l'exécution, section **Artifacts**
   - Téléchargez `PsaFapScanner-debug-apk` (fichier .zip contenant l'APK)
   - Décompressez : vous obtenez `app-debug.apk`

## Installer l'APK sur le téléphone

1. Transférez `app-debug.apk` sur votre téléphone Android (câble USB, email à vous-même, Google Drive...).
2. Ouvrez le fichier depuis le téléphone (via un gestionnaire de fichiers).
3. Android demandera d'autoriser l'installation depuis cette source (**Réglages > Sécurité > Installer des applications inconnues**, à activer pour l'app utilisée pour ouvrir le fichier, ex. "Fichiers" ou votre navigateur).
4. Installez. L'app apparaît sous le nom "PSA/Citroën HDI Scanner".

## Relancer une compilation après modification du code

À chaque `git push` (ou nouvel envoi de fichiers modifiés sur GitHub), le
workflow recompile automatiquement un nouvel APK, disponible au même
endroit (onglet Actions → dernière exécution → Artifacts).

## Remarque sur la signature

Cet APK est un **build "debug"**, signé avec une clé de développement
automatique (pas une clé de production Play Store). C'est normal et suffit
largement pour une installation manuelle et un usage personnel/expérimental
comme celui-ci.
