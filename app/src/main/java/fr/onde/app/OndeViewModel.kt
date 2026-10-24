package fr.onde.app

import android.app.Application
import android.content.SharedPreferences
import android.os.SystemClock
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class OndeState(
    val apps: List<InstalledApp> = emptyList(),
    val rules: Map<String, Rule> = emptyMap(),
    val loading: Boolean = true,
    val usageLoading: Boolean = false,
    val usageAccess: Boolean = false,
    val days: Int = 7,
    val usage: UsageReport = UsageReport(),
    val error: String? = null
)
class OndeViewModel(application: Application) : AndroidViewModel(application) {
    private val store = RuleStore(application)
    private val mutable = MutableStateFlow(OndeState())
    val state = mutable.asStateFlow()
    private var usageJob: Job? = null
    private var appsJob: Job? = null
    private val icons = ConcurrentHashMap<String, Bitmap>()
    private var lastUsageLoad = 0L
    private var lastUsageDays = -1
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        mutable.value = mutable.value.copy(rules = store.rules(mutable.value.apps))
    }
    init {
        store.preferences.registerOnSharedPreferenceChangeListener(listener)
        refresh()
    }
    fun refresh() {
        appsJob?.cancel()
        appsJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { AppsRepository.load(getApplication(), iconCache = icons) } }
            result.fold(
                onSuccess = { mutable.value = mutable.value.copy(
                    apps = it, rules = store.rules(it), loading = false,
                    usageAccess = UsageRepository.hasAccess(getApplication()), error = null) },
                onFailure = { mutable.value = mutable.value.copy(loading = false, error = "Impossible de charger les applications.") }
            )
        }
        // Les statistiques coûtent plusieurs requêtes système : on les recharge au retour au premier
        // plan seulement si elles ont plus de 30 secondes, ou si l'autorisation vient de changer.
        val access = UsageRepository.hasAccess(getApplication())
        loadUsage(mutable.value.days, force = access != mutable.value.usageAccess)
    }
    fun update(app: InstalledApp, rule: Rule) = store.update(mutable.value.apps, app, rule)
    fun loadUsage(days: Int, force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        val fresh = days == lastUsageDays && now - lastUsageLoad < 30_000
        if (!force && fresh) return
        lastUsageDays = days
        lastUsageLoad = now
        usageJob?.cancel()
        usageJob = viewModelScope.launch {
            mutable.value = mutable.value.copy(days = days, usageLoading = true)
            val report = withContext(Dispatchers.IO) { UsageRepository.load(getApplication(), days) }
            mutable.value = mutable.value.copy(
                usage = report, usageLoading = false,
                usageAccess = UsageRepository.hasAccess(getApplication()))
        }
    }
    override fun onCleared() {
        store.preferences.unregisterOnSharedPreferenceChangeListener(listener)
        super.onCleared()
    }
}
