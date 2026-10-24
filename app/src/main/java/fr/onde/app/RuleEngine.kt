package fr.onde.app

enum class NetworkKind(val label: String) {
    WIFI("Wi-Fi"), MOBILE("Données mobiles"), OTHER("Autre réseau / indéterminé")
}
data class Rule(val wifi: Boolean = true, val mobile: Boolean = true) {
    fun allowed(network: NetworkKind): Boolean = when (network) {
        NetworkKind.WIFI -> wifi
        NetworkKind.MOBILE -> mobile
        // Unknown, Ethernet or ambiguous transport: use the stricter decision.
        NetworkKind.OTHER -> wifi && mobile
    }
}
data class AppIdentity(val packageName: String, val uid: Int)

object RuleEngine {
    fun blockedPackages(
        apps: List<AppIdentity>,
        rules: Map<String, Rule>,
        network: NetworkKind
    ): Set<String> {
        // Android enforces VPN selection at UID level. Shared UIDs cannot be isolated.
        val blockedUids = apps.filter {
            !(rules[it.packageName] ?: Rule()).allowed(network)
        }.map { it.uid }.toSet()
        return apps.filter { it.uid in blockedUids }.map { it.packageName }.toSet()
    }
}
