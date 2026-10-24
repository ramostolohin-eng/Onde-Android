# Validation à effectuer avant utilisation réelle

**Aucune case n’a été validée sur Android dans l’environnement de création.**

Utiliser un appareil de test, pas une application critique. Pour tester un blocage, charger une nouvelle ressource réseau non présente en cache. Une page déjà affichée ou une vidéo mise en mémoire ne prouve pas qu’une connexion fonctionne.

## Construction

- [ ] Générer le wrapper officiel Gradle 8.11.1.
- [ ] Synchroniser le projet avec JDK 17 et SDK 35.
- [ ] Exécuter `testDebugUnitTest` : les dix tests doivent réussir.
- [ ] Exécuter `lintDebug`, examiner tous les avertissements et corriger les erreurs.
- [ ] Exécuter `assembleDebug`.
- [ ] Installer et ouvrir l’APK sur Android 10, 13, 14 et 15, puis versions ultérieures disponibles.
- [ ] Refaire les tests sur au moins deux constructeurs.

## Consentement et cycle de vie

- [ ] Annuler le premier dialogue : aucun service de protection ne démarre.
- [ ] Refuser l’autorisation VPN : état arrêté, message explicite.
- [ ] Accepter l’autorisation : service de premier plan actif, notification visible selon réglages Android.
- [ ] Arrêter depuis l’accueil puis depuis la notification : trafic rétabli.
- [ ] Révoquer le VPN dans les paramètres système : l’interface ne reste pas active à tort.
- [ ] Activer un autre VPN : Onde s’arrête et ne prétend plus protéger.
- [ ] Retirer l'autorisation VPN puis relancer le service : aucun plantage, message « Autorisation VPN requise ».
- [ ] Refuser les notifications Android 13 : observer et documenter le comportement du service.
- [ ] Fermer l’activité : le service continue si Android le permet.
- [ ] Forcer l’arrêt et redémarrer le téléphone : l’interface revient arrêtée, règles conservées.
- [ ] Désinstaller Onde : aucun paramètre réseau durable ne reste.

## Matrice du filtrage

Avec deux applications A et B indépendantes (UID différents) :

| Règle pour A | Sur Wi-Fi | Sur mobile | B autorisée |
| --- | --- | --- | --- |
| Wi-Fi oui, mobile oui | passe | passe | passe |
| Wi-Fi oui, mobile non | passe | bloqué | passe |
| Wi-Fi non, mobile oui | bloqué | passe | passe |
| Wi-Fi non, mobile non | bloqué | bloqué | passe |

- [ ] Tester HTTP, HTTPS, DNS, TCP, UDP / QUIC, IPv4 et IPv6 quand disponibles.
- [ ] Tester avec DNS privé Android activé puis désactivé.
- [ ] Vérifier qu’aucune restriction ne coupe le réseau si toutes les règles sont autorisées.
- [ ] Modifier une règle pendant que le VPN fonctionne et vérifier l’application après reconfiguration.
- [ ] Tester des connexions existantes puis de nouvelles connexions.
- [ ] Alterner Wi-Fi et mobile pendant un transfert, mesurer toute fuite ou interruption.
- [ ] Tester mode avion, absence de réseau, retour réseau et Ethernet si disponible.
- [ ] Tester l’économie d’énergie, écran éteint et arrière-plan prolongé.
- [ ] Installer / désinstaller une application pendant le fonctionnement.
- [ ] Vérifier le comportement des packages à UID partagé.
- [ ] Identifier les transferts délégués au gestionnaire de téléchargements.
- [ ] Examiner batterie / CPU avec et sans flux massif bloqué.

## Statistiques et interface

- [ ] Sans accès d’utilisation : message utile, aucun chiffre fictif.
- [ ] Avec accès : valeurs cohérentes avec les paramètres Android, après leur délai de mise à jour.
- [ ] Révoquer l’accès : affichage indisponible, pas de crash.
- [ ] Périodes 1 / 7 / 30 jours et passage à minuit.
- [ ] Cartes SIM multiples et statistiques mobiles sans identifiant d’abonné.
- [ ] Compter les UID partagés une seule fois lors de toute comparaison de totaux.
- [ ] Rechercher un nom puis un package ; vérifier l’état vide.
- [ ] Filtrer les applications restreintes et afficher les applications système.
- [ ] Icône de l'application (adaptative, thématique sur Android 13+) et icône de notification monochrome lisibles.
- [ ] Revenir plusieurs fois au premier plan : pas de ralentissement, statistiques rechargées au plus toutes les 30 secondes.
- [ ] Rotation, petite taille d’écran, police agrandie et TalkBack.
- [ ] Vérifier que le retour Android quitte une fiche avant l’écran.
- [ ] Vérifier que les règles sont conservées après fermeture / réouverture.

## Distribution

- [ ] Revoir les exigences targetSdk en vigueur.
- [ ] Documenter le besoin de QUERY_ALL_PACKAGES et l’utilisation du VPN.
- [ ] Vérifier les politiques relatives aux services de premier plan et aux statistiques.
- [ ] Audit du pare-feu, politique de confidentialité, consentement et déclarations de boutique.
- [ ] Générer une clé de signature de production hors du dépôt.
