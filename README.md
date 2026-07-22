# PSA/Citroën HDI Scanner

Application Android (Kotlin + Jetpack Compose) pour dialoguer avec un
adaptateur ELM327 Bluetooth. Cette version cible en priorité, à titre
expérimental, une **Peugeot 206 HDI 1.6, calculateur Bosch EDC16C34**.

## Ouvrir le projet

1. Android Studio (Koala ou plus récent) → `File > Open` → dossier `PsaFapScanner`.
2. Laisser Gradle synchroniser.
3. Appairer l'ELM327 dans les réglages Bluetooth d'Android avant de lancer l'app.
4. Lancer sur un téléphone réel (le Bluetooth classique est peu fiable en émulateur).

## Ce qui est fiable dès maintenant

- **Tableau de bord OBD standard** (Mode 01/03/04) : régime, vitesse,
  températures, charge moteur, pression d'admission, lecture/effacement des
  codes défaut. Ce sont des PID normalisés SAE J1979, ils fonctionnent sur
  tout véhicule OBD-II, y compris votre 206.

## Ce qui est expérimental : le module FAP

Point technique important pour l'EDC16C34 : contrairement aux calculateurs
plus récents qui utilisent le CAN-UDS (service `0x22`), **votre calculateur
dialogue en K-line / KWP2000 (ISO 14230)**, avec le service `0x21`
("ReadDataByLocalIdentifier", identifiants sur 1 octet). L'app est
configurée en conséquence : le profil `PEUGEOT_206_HDI_EDC16C34` force le
protocole KWP2000 (init rapide par défaut, repli possible sur init lent) et
n'utilise pas d'en-tête CAN.

**Aucun identifiant local FAP n'est pré-rempli pour ce calculateur** : ces
informations ne sont pas documentées publiquement (contrairement aux PID
standard), et je n'ai pas de source fiable à vous donner sans risquer de
vous faire croire à des valeurs inventées. À la place, l'app fournit un
outil de calibration :

### Onglet Scanner

1. Connectez-vous, moteur tournant.
2. Lancez le scan (0x00 à 0xFF) : l'app interroge chaque identifiant local
   et ne garde que ceux qui répondent avec une valeur exploitable. Cela
   peut prendre plusieurs minutes (K-line = liaison lente).
3. Pour chaque identifiant intéressant, appuyez sur **Observer** : la
   valeur brute (dernier octet de la réponse) s'affiche en temps réel sur
   un graphique.
4. Repérez le comportement caractéristique :
   - une valeur qui **chute nettement** après une phase de régime soutenu
     (typique d'une régénération) → bon candidat pour la masse de suie
   - une valeur qui **suit la charge moteur/le régime** → bon candidat
     pour la pression différentielle FAP
   - une valeur qui **s'incrémente rarement** → bon candidat pour le
     compteur de régénérations
5. Une fois identifiés, reportez ces identifiants dans
   `obd/PsaFapCommands.kt`, dans `PsaFapProfiles.PEUGEOT_206_HDI_EDC16C34`
   (champs `idSootMass`, `idDiffPressure`, etc.), et ajustez si besoin la
   formule de conversion dans `readFapStatus()`.

### Si le protocole ne s'établit pas

Si le scan ne renvoie rien du tout (pas même une erreur exploitable),
essayez de changer `protocol` dans `PEUGEOT_206_HDI_EDC16C34` de
`KWP2000_FAST_INIT` vers `KWP2000_SLOW_INIT` (certains clones ELM327 ou
calculateurs plus anciens préfèrent l'init lent 5 bauds). Les clones
ELM327 bon marché ont aussi des soucis connus de compatibilité KWP2000 sur
K-line — un clone authentique FTDI/vraie puce ELM327 augmente les chances
de succès.

### Détection de régénération

`RegenDetector` utilise en priorité un identifiant direct de statut s'il
est configuré (`idRegenActive`), sinon une heuristique de repli (chute de
la masse de suie combinée à un régime soutenu, ou incrémentation du
compteur de régénérations). Cette heuristique ne sera fiable qu'une fois
`idSootMass` calibré via le Scanner.

### Export CSV

L'historique des relevés FAP (masse de suie, pression, compteur,
régime/vitesse associés) peut être exporté en CSV depuis l'onglet FAP et
partagé via n'importe quelle app (mail, Drive...).

## Structure du code

```
app/src/main/java/com/obdscan/psafap/
├── MainActivity.kt              # UI racine, navigation, permissions
├── ObdViewModel.kt               # état applicatif, polling, scan, observation
├── bluetooth/Elm327Connection.kt # connexion série + sélection de protocole (KWP/CAN)
├── obd/StandardPids.kt           # PID Mode 01/03/04 standard OBD-II
├── obd/PsaFapCommands.kt         # profils véhicule + requêtes FAP (KWP local ID / CAN DID)
├── obd/IdentifierScanner.kt      # scan d'identifiants pour calibration
├── obd/FapHistory.kt             # historique, détection de régénération, export CSV
└── ui/                           # écrans Compose (dashboard, FAP, scanner, devices)
```

## Prochaine étape pour vous, concrètement

1. Installer l'app sur le téléphone, connecter l'ELM327 à la 206.
2. Aller dans **Scanner**, lancer le scan moteur tournant.
3. Noter les identifiants qui répondent avec des valeurs qui varient de
   façon cohérente avec le régime/la charge (observez-les via **Observer**).
4. Reporter dans le code les identifiants candidats trouvés — les
   formules de conversion pourront ensuite être affinées pour figer un
   profil calibré pour votre véhicule.
