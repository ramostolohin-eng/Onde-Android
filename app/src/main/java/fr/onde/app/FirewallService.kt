package fr.onde.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class FirewallStatus(
    val running: Boolean = false,
    val network: NetworkKind = NetworkKind.OTHER,
    val blocked: Int = 0,
    val message: String = "Protection arrêtée"
)
object FirewallRuntime {
    internal val mutable = MutableStateFlow(FirewallStatus())
    val status = mutable.asStateFlow()
}
class FirewallService : VpnService() {
    companion object {
        const val START = "fr.onde.app.START"
        const val STOP = "fr.onde.app.STOP"
        private const val CHANNEL = "firewall"
        private const val NOTIFICATION = 11
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val changes = Channel<Unit>(Channel.CONFLATED)
    private val tunnelLock = Any()
    private lateinit var store: RuleStore
    private lateinit var cm: ConnectivityManager
    @Volatile private var tun: ParcelFileDescriptor? = null
    private var reader: Job? = null
    @Volatile private var started = false
    @Volatile private var signature: Pair<NetworkKind, Set<String>>? = null
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> changes.trySend(Unit) }
    private val defaultCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { changes.trySend(Unit) }
        override fun onLost(network: Network) { changes.trySend(Unit) }
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { changes.trySend(Unit) }
    }
    private val physicalCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { changes.trySend(Unit) }
        override fun onLost(network: Network) { changes.trySend(Unit) }
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { changes.trySend(Unit) }
    }
    private val packages = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { signature = null; changes.trySend(Unit) }
    }
    override fun onCreate() {
        super.onCreate()
        store = RuleStore(this)
        cm = getSystemService(ConnectivityManager::class.java)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Protection réseau", NotificationManager.IMPORTANCE_LOW)
        )
        store.preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        cm.registerDefaultNetworkCallback(defaultCallback)
        cm.registerNetworkCallback(
            NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(), physicalCallback
        )
        ContextCompat.registerReceiver(this, packages,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addDataScheme("package")
            }, ContextCompat.RECEIVER_NOT_EXPORTED)
        scope.launch {
            for (signal in changes) {
                delay(120)
                while (changes.tryReceive().isSuccess) { /* coalesce */ }
                if (started) {
                    try { rebuild() }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) {
                        FirewallRuntime.mutable.value = FirewallStatus(message = "Erreur de protection. Arrêtez puis réactivez Onde.")
                        withContext(Dispatchers.Main) { stopSelf() }
                    }
                }
            }
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { stopSelf(); return START_NOT_STICKY }
        // Le service est lancé avec startForegroundService() : Android exige l'appel à
        // startForeground() dans les secondes qui suivent, même si l'on s'arrête aussitôt après.
        val notification = notification("Initialisation de la protection")
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else { startForeground(NOTIFICATION, notification) }
        if (prepare(this) != null) {
            FirewallRuntime.mutable.value = FirewallStatus(message = "Autorisation VPN requise")
            stopSelf()
            return START_NOT_STICKY
        }
        started = true
        changes.trySend(Unit)
        // Do not silently restart a killed VPN: its UI must reflect actual protection.
        return START_NOT_STICKY
    }
    private fun notification(text: String): Notification {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), flags)
        val stop = PendingIntent.getService(this, 1, Intent(this, javaClass).setAction(STOP), flags)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_onde).setContentTitle("Onde · Pare-feu local")
            .setContentText(text).setContentIntent(open).setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Arrêter", stop).build()
    }
    private fun rebuild() {
        synchronized(tunnelLock) {
            if (!started) return
            val network = NetworkReader.current(this)
            val apps = AppsRepository.load(this, includeIcons = false)
            val blocked = RuleEngine.blockedPackages(
                apps.map { AppIdentity(it.packageName, it.uid) }, store.rules(apps), network
            )
            val nextSignature = network to blocked
            if (signature == nextSignature) return
            val restricted = if (blocked.isEmpty()) {
                // CRITICAL: an empty VPN allowlist routes ALL apps, so never establish it.
                closeTunnel()
                0
            } else {
                openDropTunnel(network, blocked)
            }
            signature = nextSignature
            val text = "${network.label} · $restricted " +
                if (restricted > 1) "applications restreintes" else "application restreinte"
            FirewallRuntime.mutable.value = FirewallStatus(true, network, restricted, text)
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification(text))
        }
    }
    /** Établit le tunnel local pour [blocked] et renvoie le nombre d'applications réellement routées. */
    private fun openDropTunnel(network: NetworkKind, blocked: Set<String>): Int {
        val builder = Builder().setSession("Onde · ${network.label}")
            .setMtu(1500).setBlocking(false)
            .addAddress("10.111.0.1", 32).addRoute("0.0.0.0", 0)
            .addAddress("fd77:6f6e:6465::1", 128).addRoute("::", 0)
            .addDnsServer("192.0.2.1")
            .setConfigureIntent(PendingIntent.getActivity(this, 2,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        var added = 0
        blocked.forEach { pkg ->
            try { builder.addAllowedApplication(pkg); added++ }
            catch (_: android.content.pm.PackageManager.NameNotFoundException) { /* uninstalled */ }
        }
        if (added == 0) {
            // Tous les packages ont disparu entre-temps : pas de tunnel, et surtout pas de liste vide
            // (Android routerait alors toutes les applications dans le tunnel). La signature est
            // mémorisée par l'appelant, ce qui évite toute reconstruction en boucle.
            closeTunnel()
            return 0
        }
        // "Allowed" here means routed INTO the drop tunnel, not allowed Internet.
        // All other applications bypass this VPN and keep their normal routing.
        val next = builder.establish() ?: error("VPN establishment refused")
        closeTunnel()
        tun = next
        reader = scope.launch { drainTunnel(next) }
        return added
    }
    /** Lit et supprime chaque paquet. Aucun transfert, aucun serveur distant, aucun journal du contenu. */
    private suspend fun drainTunnel(next: ParcelFileDescriptor) {
        try {
            val fd = next.fileDescriptor
            val poll = StructPollfd().apply { this.fd = fd; events = OsConstants.POLLIN.toShort() }
            val packet = ByteArray(32767)
            while (currentCoroutineContext().isActive) {
                if (Os.poll(arrayOf(poll), 250) > 0) {
                    if (poll.revents.toInt() and (OsConstants.POLLERR or OsConstants.POLLHUP or OsConstants.POLLNVAL) != 0)
                        error("Tunnel closed")
                    if (poll.revents.toInt() and OsConstants.POLLIN != 0) {
                        try { Os.read(fd, packet, 0, packet.size) }
                        catch (e: android.system.ErrnoException) {
                            if (e.errno != OsConstants.EAGAIN && e.errno != OsConstants.EINTR) throw e
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (tun === next && currentCoroutineContext().isActive) {
                signature = null
                FirewallRuntime.mutable.value = FirewallStatus(message = "Tunnel interrompu. Réactivez la protection.")
                stopSelf()
            }
        }
    }
    private fun closeTunnel() {
        synchronized(tunnelLock) {
        reader?.cancel()
        reader = null
        runCatching { tun?.close() }
        tun = null
        }
    }
    override fun onRevoke() {
        FirewallRuntime.mutable.value = FirewallStatus(message = "Autorisation VPN retirée ou remplacée par un autre VPN")
        stopSelf()
    }
    override fun onDestroy() {
        started = false
        scope.cancel()
        changes.close()
        closeTunnel()
        store.preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        runCatching { cm.unregisterNetworkCallback(defaultCallback) }
        runCatching { cm.unregisterNetworkCallback(physicalCallback) }
        runCatching { unregisterReceiver(packages) }
        val old = FirewallRuntime.mutable.value
        FirewallRuntime.mutable.value = FirewallStatus(message =
            if (old.running) "Protection arrêtée" else old.message)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
