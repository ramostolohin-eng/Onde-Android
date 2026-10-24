package fr.onde.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Locale

private val Ink = Color(0xFF20332F)
private val Paper = Color(0xFFF3F6F1)
private val SignalGreen = Color(0xFFD5F76B)
private val Moss = Color(0xFF456A53)
private val Muted = Color(0xFF57675E)
private val Scheme = lightColorScheme(
    primary = Moss, onPrimary = Color.White, secondary = Ink,
    secondaryContainer = SignalGreen, onSecondaryContainer = Ink,
    background = Paper, onBackground = Ink, surface = Color.White,
    onSurface = Ink, surfaceVariant = Color(0xFFE5ECDC), onSurfaceVariant = Muted
)
private fun bytes(value: Long): String = when {
    value >= 1_000_000_000 -> String.format(Locale.FRANCE, "%.2f Go", value / 1e9)
    value >= 1_000_000 -> String.format(Locale.FRANCE, "%.1f Mo", value / 1e6)
    value >= 1_000 -> String.format(Locale.FRANCE, "%.1f ko", value / 1e3)
    else -> "$value o"
}
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = Scheme) { OndeApp() } }
    }
    private fun startFirewall() {
        runCatching {
            ContextCompat.startForegroundService(this,
                Intent(this, FirewallService::class.java).setAction(FirewallService.START))
        }.onFailure {
            Toast.makeText(this, "Android n’a pas autorisé le démarrage du service.", Toast.LENGTH_LONG).show()
        }
    }
    private fun openSettings(action: String, packageUri: Boolean = false) {
        runCatching {
            startActivity(Intent(action).apply {
                if (packageUri) data = Uri.parse("package:$packageName")
            })
        }.onFailure {
            Toast.makeText(this, "Ouvrez les paramètres Android manuellement.", Toast.LENGTH_LONG).show()
        }
    }
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun OndeApp(model: OndeViewModel = viewModel()) {
        val state by model.state.collectAsStateWithLifecycle()
        val status by FirewallRuntime.status.collectAsStateWithLifecycle()
        var screen by rememberSaveable { mutableIntStateOf(0) }
        var selectedPackage by rememberSaveable { mutableStateOf<String?>(null) }
        var query by rememberSaveable { mutableStateOf("") }
        var restrictedOnly by rememberSaveable { mutableStateOf(false) }
        var showSystem by rememberSaveable { mutableStateOf(false) }
        var disclosure by remember { mutableStateOf(false) }
        val selected = state.apps.find { it.packageName == selectedPackage }
        @Suppress("DEPRECATION")
        val versionName = remember {
            runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "?"
        }
        val vpnResult = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) startFirewall()
            else Toast.makeText(this, "Sans autorisation VPN, Onde ne peut pas filtrer les connexions.", Toast.LENGTH_LONG).show()
        }
        val notificationResult = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
        val requestStart: () -> Unit = {
            val prepare = VpnService.prepare(this)
            if (prepare == null) startFirewall() else vpnResult.launch(prepare)
        }
        val toggleProtection: () -> Unit = {
            if (status.running) stopService(Intent(this, FirewallService::class.java))
            else disclosure = true
        }
        val owner = LocalLifecycleOwner.current
        DisposableEffect(owner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) model.refresh()
            }
            owner.lifecycle.addObserver(observer)
            onDispose { owner.lifecycle.removeObserver(observer) }
        }
        BackHandler(enabled = selectedPackage != null || screen != 0) {
            if (selectedPackage != null) selectedPackage = null else screen = 0
        }
        Scaffold(
            containerColor = Paper,
            topBar = {
                TopAppBar(
                    title = { Text("onde •", fontSize = 26.sp, fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        if (selected != null) IconButton(onClick = { selectedPackage = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour aux applications")
                        }
                    },
                    actions = {
                        Icon(
                            Icons.Default.Shield,
                            if (status.running) "Protection démarrée" else "Protection arrêtée",
                            tint = if (status.running) Moss else Moss.copy(alpha = 0.4f)
                        )
                        Spacer(Modifier.width(20.dp))
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Paper)
                )
            },
            bottomBar = {
                NavigationBar(containerColor = Paper) {
                    val labels = listOf("Accueil", "Applications", "Données", "Réglages")
                    val icons = listOf(Icons.Default.Sensors, Icons.Default.Apps, Icons.Default.BarChart, Icons.Default.Tune)
                    labels.forEachIndexed { index, label ->
                        NavigationBarItem(
                            selected = screen == index,
                            onClick = { screen = index; selectedPackage = null },
                            icon = { Icon(icons[index], label) }, label = { Text(label, fontSize = 12.sp) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = SignalGreen)
                        )
                    }
                }
            }
        ) { inset ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(inset),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                if (selected != null) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            AppImage(selected, 60)
                            Column(Modifier.weight(1f)) {
                                Text(selected.name, style = MaterialTheme.typography.headlineMedium)
                                Text(selected.packageName, style = MaterialTheme.typography.bodySmall, color = Muted)
                            }
                        }
                    }
                    item {
                        val rule = state.rules[selected.packageName] ?: Rule()
                        Surface(shape = RoundedCornerShape(20.dp)) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Accès au réseau", style = MaterialTheme.typography.titleLarge)
                                SettingSwitch("Wi-Fi", "Autoriser cette connexion", Icons.Default.Wifi, rule.wifi) {
                                    model.update(selected, rule.copy(wifi = it))
                                }
                                SettingSwitch("Données mobiles", "Autoriser cette connexion", Icons.Default.SignalCellularAlt, rule.mobile) {
                                    model.update(selected, rule.copy(mobile = it))
                                }
                            }
                        }
                    }
                    item {
                        val usage = state.usage.byUid[selected.uid]
                        Text("Consommation sur ${state.days} jour(s)", color = Muted)
                        Text(if (state.usageAccess && usage != null) bytes(usage.total) else "Indisponible",
                            style = MaterialTheme.typography.headlineLarge)
                        if (usage != null) Text("Wi-Fi : ${bytes(usage.wifi)} · Mobile : ${bytes(usage.mobile)}", color = Muted)
                    }
                    item { Note("Les applications partageant un identifiant Android (UID) reçoivent les mêmes règles. Les statistiques de cet UID sont communes.") }
                    if (!status.running) item { Note("Protection arrêtée : les règles sont enregistrées, mais pas appliquées.") }
                } else when (screen) {
                    0 -> {
                        item { Heading("LE CONTRÔLE, SIMPLEMENT", "Gardez la main\nsur vos connexions.") }
                        item { ProtectionCard(status, toggleProtection) }
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Icon(Icons.Default.Wifi, null, tint = Moss)
                                    Text("Réseau actuel", color = Muted, fontSize = 14.sp)
                                    Text(NetworkReader.current(this@MainActivity).label, fontWeight = FontWeight.Medium)
                                }
                                Column(Modifier.weight(1f)) {
                                    Icon(Icons.Default.BarChart, null, tint = Moss)
                                    Text("${state.days} jour(s)", color = Muted, fontSize = 14.sp)
                                    Text(if (state.usageAccess && state.usage.error == null) bytes(state.usage.total.total) else "Accès requis",
                                        fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                        item {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Vos applications restreintes", modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleMedium)
                                TextButton(onClick = { screen = 1 }) { Text("Tout voir") }
                            }
                        }
                        val restricted = state.apps.filter {
                            val rule = state.rules[it.packageName] ?: Rule()
                            !rule.wifi || !rule.mobile
                        }
                        if (restricted.isEmpty()) item { Note("Aucune restriction. Ouvrez Applications pour choisir les accès à bloquer.") }
                        items(restricted.take(8), key = { it.packageName }) { app ->
                            AppSummary(app, restriction(state.rules[app.packageName] ?: Rule())) {
                                screen = 1; selectedPackage = app.packageName
                            }
                        }
                        item { Note("Filtrage local, sans serveur distant ni journalisation du contenu. Les règles restent sur cet appareil.") }
                    }
                    1 -> {
                        item { Heading("VOS RÈGLES, APPLICATION PAR APPLICATION", "Applications") }
                        item {
                            OutlinedTextField(
                                value = query, onValueChange = { query = it },
                                modifier = Modifier.fillMaxWidth(), singleLine = true,
                                label = { Text("Rechercher une application") },
                                leadingIcon = { Icon(Icons.Default.Search, null) },
                                shape = RoundedCornerShape(14.dp)
                            )
                        }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                FilterChip(selected = !restrictedOnly, onClick = { restrictedOnly = false }, label = { Text("Toutes") })
                                FilterChip(selected = restrictedOnly, onClick = { restrictedOnly = true }, label = { Text("Restreintes") })
                            }
                        }
                        if (!status.running) item { Note("Protection arrêtée : vos modifications seront appliquées au prochain démarrage.") }
                        item {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Application", Modifier.weight(1f), color = Muted)
                                Text("Wi-Fi", Modifier.width(58.dp), color = Muted, fontSize = 14.sp)
                                Text("Mobile", Modifier.width(58.dp), color = Muted, fontSize = 14.sp)
                            }
                        }
                        val visible = state.apps.filter { app ->
                            val rule = state.rules[app.packageName] ?: Rule()
                            (showSystem || !app.system) &&
                                (app.name.contains(query, true) || app.packageName.contains(query, true)) &&
                                (!restrictedOnly || !rule.wifi || !rule.mobile)
                        }
                        if (state.loading) item { CircularProgressIndicator() }
                        if (!state.loading && visible.isEmpty()) item { Note("Aucun résultat. Activez « Applications système » dans les réglages si nécessaire.") }
                        items(visible, key = { it.packageName }) { app ->
                            val rule = state.rules[app.packageName] ?: Rule()
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Row(Modifier.weight(1f).clickable { selectedPackage = app.packageName }.padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    AppImage(app)
                                    Column(Modifier.weight(1f)) {
                                        Text(app.name, fontSize = 16.sp, maxLines = 2, fontWeight = FontWeight.Medium)
                                        Text(if (app.system) "Système" else "Application", fontSize = 14.sp, color = Muted)
                                    }
                                }
                                Switch(checked = rule.wifi, onCheckedChange = { model.update(app, rule.copy(wifi = it)) },
                                    modifier = Modifier.width(58.dp).semantics { contentDescription = "Wi-Fi pour ${app.name}" })
                                Switch(checked = rule.mobile, onCheckedChange = { model.update(app, rule.copy(mobile = it)) },
                                    modifier = Modifier.width(58.dp).semantics { contentDescription = "Données mobiles pour ${app.name}" })
                            }
                            HorizontalDivider(color = Color(0xFFDFE6D9))
                        }
                        item { Note("Activé = connexion autorisée. Désactivé = connexion bloquée lorsque la protection fonctionne.") }
                    }
                    2 -> {
                        item { Heading("COMPRENDRE POUR MIEUX CHOISIR", "Consommation") }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                listOf(1 to "Aujourd’hui", 7 to "7 jours", 30 to "30 jours").forEach { (days, title) ->
                                    FilterChip(selected = state.days == days, onClick = { model.loadUsage(days) }, label = { Text(title) })
                                }
                            }
                        }
                        if (state.usageLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                        if (state.usage.error != null) {
                            item { Note(state.usage.error!!) }
                            if (!state.usageAccess) item {
                                Button(onClick = { openSettings(Settings.ACTION_USAGE_ACCESS_SETTINGS) }) { Text("Autoriser l’accès aux statistiques") }
                            }
                        } else {
                            item { UsageCard(state.usage) }
                            item { Text("Par application", style = MaterialTheme.typography.titleLarge) }
                            val ranked = state.apps.filter { state.usage.byUid[it.uid]?.total?.let { n -> n > 0 } == true }
                                .sortedByDescending { state.usage.byUid[it.uid]?.total }
                            items(ranked, key = { it.packageName }) { app ->
                                val usage = state.usage.byUid[app.uid] ?: Traffic()
                                AppSummary(app, "Wi-Fi ${bytes(usage.wifi)} · Mobile ${bytes(usage.mobile)}") {
                                    selectedPackage = app.packageName
                                }
                            }
                            if (ranked.isEmpty()) item { Note("Aucune consommation disponible pour cette période.") }
                            item { Note("Mesures fournies par Android, parfois différées. Le total inclut aussi les UID système et les applications désinstallées. Un UID partagé apparaît sur chaque application concernée : ne pas additionner ces lignes.") }
                        }
                        item { OutlinedButton(onClick = { model.loadUsage(state.days, force = true) }) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Actualiser") } }
                    }
                    3 -> {
                        item { Heading("UN RÉSEAU À VOTRE MESURE", "Réglages") }
                        item {
                            Surface(shape = RoundedCornerShape(20.dp)) {
                                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    SettingSwitch("Protection réseau", status.message, Icons.Default.Shield, status.running) { toggleProtection() }
                                    SettingSwitch("Applications système", "Afficher aussi les composants Android", Icons.Default.Android, showSystem) { showSystem = it }
                                }
                            }
                        }
                        item { Note("Attention : bloquer des services système peut perturber les téléchargements, les notifications ou d’autres fonctions. Une application peut aussi déléguer un transfert à un service non bloqué.") }
                        item {
                            Surface(color = Color(0xFFE5ECDC), shape = RoundedCornerShape(20.dp)) {
                                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    Icon(Icons.Default.Sensors, null, tint = Moss, modifier = Modifier.size(32.dp))
                                    Text("Un VPN local.\nPas un VPN distant.", style = MaterialTheme.typography.headlineMedium)
                                    Text("Seules les applications à bloquer sont dirigées vers un tunnel local qui jette leurs paquets. Les autres gardent leur connexion habituelle. Aucun serveur, aucun compte, aucun certificat.", fontSize = 16.sp)
                                    Text("Un autre VPN ne peut pas être utilisé en même temps. Le démarrage automatique et le mode VPN permanent ne sont pas pris en charge dans cette version.", fontSize = 14.sp, color = Muted)
                                }
                            }
                        }
                        item {
                            OutlinedButton(onClick = { openSettings(Settings.ACTION_USAGE_ACCESS_SETTINGS) }) { Text("Accès aux données d’utilisation") }
                        }
                        item {
                            if (Build.VERSION.SDK_INT >= 33) {
                                OutlinedButton(onClick = { notificationResult.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Autoriser les notifications") }
                            }
                        }
                        item { OutlinedButton(onClick = { openSettings(Settings.ACTION_VPN_SETTINGS) }) { Text("Ouvrir les paramètres VPN Android") } }
                        item { Note("Version de développement $versionName. Les transitions de réseau peuvent laisser un court délai avant la nouvelle règle. Ce n’est pas une protection anti-fuite certifiée.") }
                    }
                }
                if (state.error != null) item { Note(state.error!!) }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
        if (disclosure) AlertDialog(
            onDismissRequest = { disclosure = false },
            icon = { Icon(Icons.Default.Shield, null) },
            title = { Text("Autoriser le pare-feu local") },
            text = { Text("Onde utilise l’API VPN d’Android pour bloquer localement les connexions des applications choisies. Le trafic de ces applications est supprimé, pas transmis à un serveur ni enregistré. Les autres applications utilisent leur réseau normal. Un autre VPN sera remplacé. La protection s’arrête si vous arrêtez Onde ou retirez son autorisation.") },
            confirmButton = { TextButton(onClick = { disclosure = false; requestStart() }) { Text("Continuer") } },
            dismissButton = { TextButton(onClick = { disclosure = false }) { Text("Annuler") } }
        )
    }
}

private fun restriction(rule: Rule) = when {
    !rule.wifi && !rule.mobile -> "Tout accès bloqué"
    !rule.wifi -> "Wi-Fi bloqué"
    !rule.mobile -> "Données mobiles bloquées"
    else -> "Connexions autorisées"
}
@Composable
private fun Heading(eyebrow: String, title: String) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(eyebrow, color = Moss, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Text(title, fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.Medium, letterSpacing = (-1).sp)
    }
}
@Composable
private fun Note(text: String) {
    Surface(color = Color(0xFFE7EDDF), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.Info, null, tint = Moss, modifier = Modifier.size(20.dp))
            Text(text, fontSize = 14.sp, color = Muted, modifier = Modifier.weight(1f))
        }
    }
}
@Composable
private fun AppImage(app: InstalledApp, size: Int = 42) {
    val bitmap = app.icon
    if (bitmap != null) Image(bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.size(size.dp))
    else Box(Modifier.size(size.dp).background(Color(0xFFE5ECDC), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.Apps, null, tint = Moss)
    }
}
@Composable
private fun AppSummary(app: InstalledApp, subtitle: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppImage(app)
            Column(Modifier.weight(1f)) {
                Text(app.name, fontWeight = FontWeight.Medium)
                Text(subtitle, fontSize = 14.sp, color = Muted)
            }
            Icon(Icons.Default.ChevronRight, null)
        }
    }
}
@Composable
private fun SettingSwitch(title: String, subtitle: String, icon: ImageVector, value: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = Moss)
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 14.sp, color = Muted)
        }
        Switch(checked = value, onCheckedChange = change,
            modifier = Modifier.semantics { contentDescription = title })
    }
}
@Composable
private fun ProtectionCard(status: FirewallStatus, toggle: () -> Unit) {
    Surface(color = if (status.running) SignalGreen else Color(0xFFE1E8DA), shape = RoundedCornerShape(24.dp)) {
        Box(Modifier.fillMaxWidth().padding(24.dp)) {
            Canvas(Modifier.size(145.dp).align(Alignment.CenterEnd)) {
                repeat(3) { i ->
                    drawCircle(Moss.copy(alpha = 0.18f), radius = size.minDimension * (0.25f + i * 0.12f), style = Stroke(1.dp.toPx()))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(if (status.running) "PROTECTION ACTIVE" else "PROTECTION ARRÊTÉE",
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(if (status.running) "Chaque connexion\nsuit vos règles." else "Vos connexions,\nvos décisions.",
                    fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Medium)
                Text(status.message, fontSize = 14.sp, modifier = Modifier.widthIn(max = 230.dp))
                Button(onClick = toggle, colors = ButtonDefaults.buttonColors(containerColor = Ink)) {
                    Icon(Icons.Default.PowerSettingsNew, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (status.running) "Arrêter la protection" else "Activer la protection")
                }
            }
        }
    }
}
@Composable
private fun UsageCard(report: UsageReport) {
    Surface(color = Color(0xFFE5ECDC), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Total utilisé", color = Muted)
            Text(bytes(report.total.total), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Medium)
            val max = (report.days.maxOfOrNull { it.traffic.total } ?: 1L).coerceAtLeast(1L)
            Canvas(Modifier.fillMaxWidth().height(150.dp).semantics {
                contentDescription = "Consommation par jour. Wi-Fi ${bytes(report.total.wifi)}, mobile ${bytes(report.total.mobile)}."
            }) {
                val n = report.days.size
                if (n > 0) {
                    val step = size.width / n
                    report.days.forEachIndexed { i, day ->
                        val x = step * i + step / 2
                        val mobileHeight = (day.traffic.mobile.toDouble() / max * size.height).toFloat()
                        val wifiHeight = (day.traffic.wifi.toDouble() / max * size.height).toFloat()
                        drawLine(Color(0xFFB5D859), androidx.compose.ui.geometry.Offset(x, size.height),
                            androidx.compose.ui.geometry.Offset(x, size.height - mobileHeight),
                            strokeWidth = step * 0.65f, cap = StrokeCap.Butt)
                        drawLine(Moss, androidx.compose.ui.geometry.Offset(x, size.height - mobileHeight),
                            androidx.compose.ui.geometry.Offset(x, size.height - mobileHeight - wifiHeight),
                            strokeWidth = step * 0.65f, cap = StrokeCap.Butt)
                    }
                }
            }
            if (report.days.size > 1) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Jour ${report.days.first().label}", fontSize = 14.sp, color = Muted)
                Text("Jour ${report.days.last().label}", fontSize = 14.sp, color = Muted)
            }
            Text("Wi-Fi : ${bytes(report.total.wifi)}", fontSize = 14.sp)
            Text("Mobile : ${bytes(report.total.mobile)}", fontSize = 14.sp)
        }
    }
}
