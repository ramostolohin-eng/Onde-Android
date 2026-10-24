# Journal des modifications

## 0.1.1

Corrections issues d'une relecture statique (rien n'a encore été compilé ni exécuté sur Android).

- **Service VPN** : `startForeground()` est maintenant appelé avant toute vérification, comme l'exige
  `startForegroundService()`. Évite un plantage si l'autorisation VPN a été retirée entre-temps.
- **Service VPN** : plus de reconstruction en boucle quand aucun package bloqué n'existe plus. Aucun
  tunnel n'est créé dans ce cas, et la liste VPN n'est jamais vide.
- **Service VPN** : `rebuild()` découpé en fonctions lisibles (`openDropTunnel`, `drainTunnel`).
- **Interface** : remplacement de `ShieldMoon` (existence incertaine dans `material-icons-extended`)
  par `Shield` avec une teinte atténuée à l'arrêt ; `ArrowBack` remplacé par la version auto-miroir.
- **Interface** : la version affichée dans Réglages vient du `versionName` réel de l'application.
- **Performance** : cache des icônes entre deux chargements, statistiques d'utilisation rechargées au
  plus toutes les 30 secondes au retour au premier plan (le bouton Actualiser force le rechargement).
- **Icônes** : icône de lanceur adaptative (avec variante monochrome) et vraie icône de notification
  monochrome, l'ancienne icône carrée s'affichant comme un carré blanc dans la barre d'état.
- **Build** : `kotlinOptions` remplacé par `compilerOptions`, lint non bloquant, nom de l'application
  dans `strings.xml`.
- **Workflow GitHub Actions** : l'APK est construit avant le lint.
- **Tests** : trois tests unitaires ajoutés (dix au total).

## 0.1.0

Première version de développement.
