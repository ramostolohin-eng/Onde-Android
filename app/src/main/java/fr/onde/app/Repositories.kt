package fr.onde.app

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Process
import androidx.core.graphics.drawable.toBitmap
import java.time.LocalDate
import java.time.ZoneId

data class InstalledApp(
    val packageName: String, val uid: Int, val name: String,
    val system: Boolean, val icon: Bitmap?
)
class RuleStore(context: Context) {
    val preferences = context.getSharedPreferences("onde_rules", Context.MODE_PRIVATE)
    fun rule(packageName: String) = Rule(
        preferences.getBoolean("$packageName.wifi", true),
        preferences.getBoolean("$packageName.mobile", true)
    )
    fun rules(apps: List<InstalledApp>) = apps.associate { it.packageName to rule(it.packageName) }
    fun update(apps: List<InstalledApp>, target: InstalledApp, value: Rule) {
        // Keep all packages sharing the same UID visibly synchronized.
        preferences.edit().apply {
            apps.filter { it.uid == target.uid }.forEach {
                putBoolean("${it.packageName}.wifi", value.wifi)
                putBoolean("${it.packageName}.mobile", value.mobile)
            }
        }.apply()
    }
}
object AppsRepository {
    @Suppress("DEPRECATION")
    /**
     * @param iconCache icônes déjà décodées, réutilisées d'un chargement à l'autre pour éviter
     * de reconvertir toutes les icônes à chaque retour au premier plan. Les entrées des
     * applications disparues sont retirées.
     */
    fun load(
        context: Context,
        includeIcons: Boolean = true,
        iconCache: MutableMap<String, Bitmap>? = null
    ): List<InstalledApp> {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(0).filter {
            it.packageName != context.packageName &&
                pm.checkPermission(android.Manifest.permission.INTERNET, it.packageName) ==
                PackageManager.PERMISSION_GRANTED
        }.map {
            InstalledApp(it.packageName, it.uid, pm.getApplicationLabel(it).toString(),
                it.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                if (includeIcons) icon(pm, it, iconCache) else null)
        }.sortedBy { it.name.lowercase(java.util.Locale.getDefault()) }
        iconCache?.keys?.retainAll(apps.map { it.packageName }.toSet())
        return apps
    }
    private fun icon(pm: PackageManager, info: ApplicationInfo, cache: MutableMap<String, Bitmap>?): Bitmap? {
        cache?.get(info.packageName)?.let { return it }
        val bitmap = runCatching { pm.getApplicationIcon(info).toBitmap(96, 96) }.getOrNull()
        if (bitmap != null) cache?.put(info.packageName, bitmap)
        return bitmap
    }
}
object NetworkReader {
    fun current(context: Context): NetworkKind {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return NetworkKind.OTHER
        val wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val mobile = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        return when {
            wifi && !mobile -> NetworkKind.WIFI
            mobile && !wifi -> NetworkKind.MOBILE
            else -> NetworkKind.OTHER
        }
    }
}
data class Traffic(val wifi: Long = 0, val mobile: Long = 0) {
    val total get() = wifi + mobile
    operator fun plus(other: Traffic) = Traffic(wifi + other.wifi, mobile + other.mobile)
}
data class DailyTraffic(val label: String, val traffic: Traffic)
data class UsageReport(
    val byUid: Map<Int, Traffic> = emptyMap(),
    val days: List<DailyTraffic> = emptyList(),
    val error: String? = null
) {
    val total: Traffic get() = byUid.values.fold(Traffic()) { a, b -> a + b }
}
object UsageRepository {
    fun hasAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        return ops.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
        ) == AppOpsManager.MODE_ALLOWED
    }
    @Suppress("DEPRECATION")
    private fun query(context: Context, start: Long, end: Long): Map<Int, Traffic> {
        val manager = context.getSystemService(NetworkStatsManager::class.java)
        val result = mutableMapOf<Int, Traffic>()
        for (type in listOf(ConnectivityManager.TYPE_WIFI, ConnectivityManager.TYPE_MOBILE)) {
            val stats = manager.querySummary(type, null, start, end)
                ?: throw IllegalStateException("Statistiques non disponibles")
            try {
                val bucket = NetworkStats.Bucket()
                while (stats.hasNextBucket()) {
                    stats.getNextBucket(bucket)
                    val bytes = bucket.rxBytes + bucket.txBytes
                    val old = result[bucket.uid] ?: Traffic()
                    result[bucket.uid] = old + if (type == ConnectivityManager.TYPE_WIFI)
                        Traffic(wifi = bytes) else Traffic(mobile = bytes)
                }
            } finally { stats.close() }
        }
        return result
    }
    fun load(context: Context, days: Int): UsageReport {
        if (!hasAccess(context)) return UsageReport(error = "Autorisez l’accès aux données d’utilisation pour consulter les statistiques.")
        return try {
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now()
            val first = today.minusDays(days.toLong() - 1)
            val now = System.currentTimeMillis()
            val start = first.atStartOfDay(zone).toInstant().toEpochMilli()
            val total = query(context, start, now)
            val daily = (0 until days).map { offset ->
                val date = first.plusDays(offset.toLong())
                val from = date.atStartOfDay(zone).toInstant().toEpochMilli()
                val to = minOf(date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), now)
                val traffic = query(context, from, to).values.fold(Traffic()) { a, b -> a + b }
                DailyTraffic(date.dayOfMonth.toString(), traffic)
            }
            UsageReport(total, daily)
        } catch (_: SecurityException) {
            UsageReport(error = "Android refuse l’accès aux statistiques. Vérifiez l’autorisation d’utilisation.")
        } catch (_: Exception) {
            UsageReport(error = "Statistiques indisponibles sur cet appareil. Le pare-feu peut fonctionner sans elles.")
        }
    }
}
