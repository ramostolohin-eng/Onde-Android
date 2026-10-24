# Onde : application Android native

Projet source Kotlin / Jetpack Compose pour contrôler les accès Wi-Fi et données mobiles de chaque application, sans root, via un VPN local.

**État : version de développement 0.1.1 (voir `CHANGELOG.md`), pas un produit validé.** Le projet a été rédigé et contrôlé statiquement, mais n’a pas été compilé ni exécuté dans un environnement Android. Les dix tests JUnit sont fournis, pas exécutés. Aucun APK n’est inclus. Ne pas présenter cette version comme une protection anti-fuite ou prête pour publication.

## Ce qui est implémenté

- Accueil avec état réel du service et commande démarrer / arrêter.
- Liste des applications disposant de la permission Internet, recherche, filtre des applications restreintes.
- Interrupteurs Wi-Fi et données mobiles séparés.
- Fiche application et synchronisation des règles des applications partageant un UID.
- Règles persistantes sur l’appareil avec SharedPreferences.
- Demande explicite de consentement, puis autorisation VPN Android.
- Service de premier plan avec notification et action d’arrêt.
- Tunnel IPv4 et IPv6 qui supprime les paquets des applications restreintes.
- Réévaluation des règles au changement de réseau et à l’installation / désinstallation d’applications.
- Statistiques Android réelles Wi-Fi / mobile, sur 1, 7 ou 30 jours, graphique et détail par UID.
- Accès aux réglages Android pour autorisations d’utilisation, notifications et VPN.
- Affichage facultatif des applications système.
- Dix tests unitaires du moteur de règles.

Les statistiques n’utilisent pas les valeurs fictives de la maquette web. Elles restent indisponibles tant que l’accès aux données d’utilisation n’est pas accordé.

## Compiler avec GitHub Actions (méthode prévue pour Juliana)

Le workflow `.github/workflows/android.yml` installe Java 17, le SDK Android 35 et Gradle 8.11.1, exécute les tests unitaires, compile un APK debug et le conserve dans les artefacts du workflow, puis lance le lint. Le lint passe après l'APK : un avertissement ne bloque donc pas l'obtention d'un fichier installable, et son rapport reste disponible.

**Aucun Android Studio, Gradle local, secret de signature ou wrapper n’est nécessaire pour cette méthode.** Le workflow utilise directement le Gradle installé par `setup-gradle`. Il n’a pas encore été exécuté sur GitHub : sa présence ne garantit pas une compilation réussie.

1. Décompresser l’archive.
2. Créer un dépôt GitHub, privé si souhaité.
3. Envoyer **le contenu** du dossier `Onde-Android` à la racine du dépôt : `settings.gradle.kts`, `app/` et `.github/` doivent se trouver au même niveau. Ne pas envoyer seulement le ZIP.
4. Vérifier que le fichier `.github/workflows/android.yml` est présent dans le dépôt. Le dossier `.github` est parfois masqué dans les explorateurs de fichiers.
5. Si l’import par navigateur ignore les fichiers masqués, créer ce fichier via **Add file > Create new file**, saisir son chemin complet et y copier le workflow fourni.
6. Ouvrir l’onglet **Actions** et autoriser GitHub Actions si demandé.
7. Choisir **Compiler Onde Android**, puis **Run workflow**. Les prochains push déclenchent aussi une compilation.
8. Quand le workflow réussit, ouvrir l’exécution et télécharger **Onde-debug-numéro** dans la section **Artifacts**.
9. Extraire cette archive : elle contient `app-debug.apk`, installable sur un téléphone Android 10 ou plus récent après autorisation d’installation depuis cette source.

Si l’exécution échoue, ouvrir l’étape rouge et examiner le journal. Les rapports de tests / lint déjà générés sont aussi conservés dans **Onde-rapports-numéro**. Aucun test sur téléphone ou émulateur n’est exécuté par ce workflow.

L’APK debug convient aux essais, pas à une publication Play Store. Sa clé debug est générée sur le runner : deux compilations peuvent ne pas avoir la même signature. Une mise à jour peut alors nécessiter de désinstaller l’ancienne version, ce qui efface les règles. Une version de distribution devra utiliser une clé de signature stable et protégée.

Les quotas et possibilités GitHub Actions dépendent du compte et du dépôt. Aucun résultat de compilation n’est envoyé à un service externe au dépôt, hors téléchargements des outils et dépendances nécessaires.

## Prérequis pour compiler localement (facultatif)

Sur votre ordinateur :

1. Android Studio compatible avec Android Gradle Plugin 8.9.1.
2. JDK 17 pour Gradle.
3. Android SDK Platform 35 et outils de compilation recommandés par Android Studio.
4. Gradle **8.11.1** pour la première génération du wrapper.
5. Internet pour télécharger les plugins, outils et dépendances Maven.
6. Un appareil Android 10 (API 29) ou plus récent, idéalement un téléphone de test avec Wi-Fi et données mobiles.

Versions épinglées : AGP 8.9.1, Kotlin 2.1.10, Compose BOM 2025.02.00, compileSdk / targetSdk 35. Ce sont des choix de développement : avant publication, vérifier les exigences SDK et politiques Google Play en vigueur.

## Ouvrir et compiler

Le wrapper Gradle binaire n’est pas inclus, car il ne pouvait pas être téléchargé dans l’environnement de création. Ce n’est pas un faux wrapper : générez le wrapper officiel localement.

Depuis la racine du projet, avec Gradle 8.11.1 installé et le SDK Android configuré :

```sh
gradle wrapper --gradle-version 8.11.1 --distribution-type bin
```

Puis ouvrir ce dossier avec **Android Studio > Open**, sélectionner JDK 17 comme Gradle JDK et laisser la synchronisation se terminer.

Le chemin du SDK peut être configuré par Android Studio dans `local.properties` ou par `ANDROID_HOME`. Ne pas versionner le chemin personnel.

### macOS / Linux

```sh
chmod +x gradlew
./gradlew testDebugUnitTest lintDebug assembleDebug
```

### Windows PowerShell

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

APK de développement attendu après une compilation réussie :

```text
app/build/outputs/apk/debug/app-debug.apk
```

Ce fichier sera signé avec la clé de débogage locale. Pour installer sur un appareil connecté :

```sh
./gradlew installDebug
```

Ou lancer l’application avec le bouton Run d’Android Studio.

Pour une publication, créer un bundle signé depuis Android Studio et conserver la clé de signature en lieu sûr. Aucune clé privée ni configuration de signature de production n’est fournie.

## Première utilisation

1. Ouvrir Onde.
2. Dans Applications, désactiver le réseau à bloquer pour une application de test.
3. Revenir à l’accueil, toucher Activer la protection.
4. Lire la divulgation, puis accepter la demande VPN d’Android.
5. Dans Réglages, autoriser les notifications sur Android 13 ou plus si souhaité. Android impose tout de même un service de premier plan.
6. Pour les statistiques, accorder l’accès aux données d’utilisation dans les paramètres système.
7. Tester le comportement réel en Wi-Fi puis en données mobiles.

Les applications système sont masquées par défaut. Les afficher dans Réglages, avec prudence.

## Architecture du filtrage

```text
Règles persistantes + réseau actif
                 |
           RuleEngine
                 |
  Liste des packages / UID à BLOQUER
                 |
   VpnService.addAllowedApplication()
                 |
      Interface TUN locale IPv4 / IPv6
                 |
       Lecture et suppression des paquets

Autres applications : connexion Android habituelle, hors tunnel.
```

Le mot « allowed » dans l’API Android signifie **autorisé à entrer dans le tunnel**, et non autorisé à accéder à Internet. Le tunnel n’a volontairement aucun moteur de transfert.

**Cas essentiel :** si aucun package n’est bloqué, aucun tunnel n’est créé. Android traiterait une liste VPN vide comme « toutes les applications », ce qui couperait accidentellement tout Internet. Le service reste actif pour surveiller les changements de réseau et de règles.

L’API VPN route les UID sélectionnés. Les applications partageant un UID ont donc nécessairement la même décision réseau. L’interface les synchronise et le moteur applique la règle la plus restrictive en cas d’incohérence.

Un réseau autre que Wi-Fi ou mobile, absent ou ambigu utilise `wifi && mobile` : l’accès n’est autorisé que si les deux interrupteurs le sont.

Le paquet n’est pas analysé, stocké ni envoyé à un service distant. Aucun SDK publicitaire, compte, serveur, analyseur d’audience ou certificat d’interception n’est ajouté.

## Fichiers clés

| Fichier | Responsabilité |
| --- | --- |
| `MainActivity.kt` | Écrans Compose, navigation, consentement et autorisations |
| `OndeViewModel.kt` | Chargement asynchrone, état de l’interface |
| `RuleEngine.kt` | Décisions réseau pures et traitement des UID partagés |
| `Repositories.kt` | Applications installées, règles persistantes et statistiques |
| `FirewallService.kt` | Cycle de vie du VPN, notification, tunnel et suppression des paquets |
| `RuleEngineTest.kt` | Dix cas unitaires du moteur de règles |

## Limites à connaître

- **Pas de compilation, de test Android ni d’audit de sécurité effectués ici.** Les contrôles statiques ne prouvent pas le fonctionnement du tunnel ou des dépendances.
- Un seul VPN actif par profil Android. Onde remplace un autre VPN et inversement.
- Le service ne redémarre pas automatiquement après arrêt forcé, redémarrage du téléphone ou suppression du processus.
- Le VPN permanent / Always-on est déclaré non pris en charge. Ne pas activer un mode système de blocage de toutes les connexions hors VPN : les applications autorisées utilisent précisément le réseau hors tunnel.
- Les changements de réseau ou de règles nécessitent la reconstruction du tunnel. Un délai de détection / reconfiguration peut laisser passer des connexions avant la nouvelle règle. Aucune garantie anti-fuite ni blocage atomique n’est annoncée.
- Les connexions déjà ouvertes, les VPN concurrents et les personnalisations constructeur nécessitent des tests spécifiques.
- Les applications peuvent déléguer certains téléchargements à un service système qui possède un autre UID. Pour bloquer aussi ce trafic il faut restreindre ce service, ce qui peut affecter d’autres applications.
- La sélection Wi-Fi / mobile concerne le réseau Android actif, pas un routage simultané et indépendant de chaque socket. Les appareils utilisant plusieurs réseaux simultanément doivent être testés.
- Les profils professionnels, autres utilisateurs Android, partage de connexion, DNS avancé et multipath ne sont pas gérés de façon spécifique.
- Les statistiques viennent de NetworkStatsManager : délais de mise à jour, agrégation par UID, variations constructeur et restrictions d’autorisation possibles. Les applications partageant un UID affichent le même usage.
- Le total inclut des UID qui ne figurent pas dans la liste visible, par exemple système ou applications désinstallées. Ne pas additionner les lignes d’un UID partagé.
- `QUERY_ALL_PACKAGES`, `VpnService`, le service spécial de premier plan et l’accès aux statistiques exigent une justification et des déclarations de confidentialité si l’application est distribuée. L’acceptation Google Play n’est pas garantie.
- L’application ne change pas les interrupteurs système du Wi-Fi ou de la carte SIM, ni les réglages réseau natifs de chaque autre application : elle filtre leurs connexions par VPN.

## Avant de distribuer

Exécuter la checklist de `TESTS_MANUELS.md`, corriger les éventuels problèmes de compilation, profiler batterie / CPU, tester plusieurs constructeurs et versions Android, auditer les transitions et arrêts du VPN, vérifier l’accessibilité et rédiger une politique de confidentialité conforme à la version réellement distribuée.

## Documentation officielle utilisée

- https://developer.android.com/develop/connectivity/vpn
- https://developer.android.com/reference/android/net/VpnService.Builder
- https://developer.android.com/reference/android/app/usage/NetworkStatsManager
