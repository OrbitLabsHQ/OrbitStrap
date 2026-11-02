package com.odil9.orbitstrap

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Context.store by preferencesDataStore("orbitstrap_v1")
private val KEY_CONFIG = stringPreferencesKey("config")
private val KEY_PRESETS = stringPreferencesKey("presets")
private val KEY_FAVORITES = stringPreferencesKey("favorites")
private val KEY_FLAG_FAVORITES = stringPreferencesKey("flag_favorites")
private val KEY_HISTORY = stringPreferencesKey("history")
private val KEY_ACCENT = stringPreferencesKey("accent")
private val KEY_THEME = stringPreferencesKey("theme")
private val KEY_LAST_JOIN = stringPreferencesKey("last_join_link")

private const val GLOBAL_ROBLOX = "com.roblox.client"
private const val VN_ROBLOX = "com.roblox.client.vnggames"
private val Bg = Color(0xFF090B12)
private val Panel = Color(0xFF151A28)
private val Panel2 = Color(0xFF20283A)
private val Muted = Color(0xFFA6AEC2)
private val Green = Color(0xFF73D6A3)
private val Amber = Color(0xFFFFC66D)
private val Violet = Color(0xFF9B8CFF)
private val Cyan = Color(0xFF70D9E8)

@Serializable
data class SavedPreset(val id: String, val name: String, val description: String, val json: String)

@Serializable
data class Snapshot(val timestamp: Long, val json: String, val label: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { OrbitStrapApp() }
    }
}

@Composable
private fun OrbitStrapApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf("Home") }
    var config by remember { mutableStateOf("{}") }
    var presets by remember { mutableStateOf(defaultPresets()) }
    var favorites by remember { mutableStateOf(setOf<String>()) }
    var flagFavorites by remember { mutableStateOf(setOf<String>()) }
    var history by remember { mutableStateOf(listOf<Snapshot>()) }
    var compareSnapshot by remember { mutableStateOf<Snapshot?>(null) }
    var accentName by remember { mutableStateOf("Violet") }
    var darkMode by remember { mutableStateOf(true) }
    var dirty by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Ready") }
    var validation by remember { mutableStateOf("Not checked yet") }
    var showPresetDialog by remember { mutableStateOf(false) }
    var presetName by remember { mutableStateOf("") }
    var presetDescription by remember { mutableStateOf("") }
    var lastJoinLink by remember { mutableStateOf("") }

    val accent = when (accentName) {
        "Cyan" -> Cyan
        "Rose" -> Color(0xFFFF8FB1)
        "Amber" -> Amber
        "Green" -> Green
        else -> Violet
    }
    val scheme = if (darkMode) darkColorScheme(
        primary = accent, secondary = Cyan, background = Bg, surface = Panel,
        onBackground = Color.White, onSurface = Color.White
    ) else lightColorScheme(primary = accent, secondary = Cyan)

    LaunchedEffect(Unit) {
        runCatching {
            val prefs = context.store.data.first()
            config = prefs[KEY_CONFIG] ?: "{}"
            presets = prefs[KEY_PRESETS]?.let { Json.decodeFromString<List<SavedPreset>>(it) } ?: defaultPresets()
            favorites = prefs[KEY_FAVORITES]?.split("|")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
            flagFavorites = prefs[KEY_FLAG_FAVORITES]?.let { Json.decodeFromString<List<String>>(it) }?.toSet() ?: emptySet()
            history = prefs[KEY_HISTORY]?.let { Json.decodeFromString<List<Snapshot>>(it) } ?: emptyList()
            accentName = prefs[KEY_ACCENT] ?: "Violet"
            darkMode = prefs[KEY_THEME] != "light"
            lastJoinLink = prefs[KEY_LAST_JOIN] ?: ""
            ConfigUtils.validate(config)
        }.onFailure { message = "Saved data could not be loaded safely: ${it.message}" }
    }

    suspend fun persistAll() {
        context.store.edit {
            it[KEY_CONFIG] = config
            it[KEY_PRESETS] = Json.encodeToString(presets)
            it[KEY_FAVORITES] = favorites.joinToString("|")
            it[KEY_FLAG_FAVORITES] = Json.encodeToString(flagFavorites.toList())
            it[KEY_HISTORY] = Json.encodeToString(history.take(30))
            it[KEY_ACCENT] = accentName
            it[KEY_THEME] = if (darkMode) "dark" else "light"
            it[KEY_LAST_JOIN] = lastJoinLink
        }
    }

    fun validate(text: String): String = runCatching { ConfigUtils.validate(text) }
        .getOrElse { "Invalid JSON · ${it.message ?: "parse error"}" }

    fun saveConfig() {
        validation = validate(config)
        if (validation.startsWith("Valid")) {
            history = (listOf(Snapshot(System.currentTimeMillis(), config, "Manual save")) + history).take(30)
            scope.launch {
                persistAll()
                dirty = false
                message = "Saved locally · restore point created"
            }
        } else message = "Correct the JSON before saving"
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(config) }
                ?: error("Could not open destination")
            message = "Configuration exported"
        }.onFailure { message = "Export failed: ${it.message}" }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) runCatching {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: error("Could not read selected file")
            ConfigUtils.validate(text)
            config = text
            dirty = true
            validation = validate(text)
            message = "Configuration imported; save to keep it"
        }.onFailure { message = "Import rejected: ${it.message}" }
    }
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) runCatching {
            val backup = BackupEnvelope(
                appVersion = "1.2.0", createdAt = System.currentTimeMillis(), config = config,
                presets = presets, favorites = favorites.toList(), flagFavorites = flagFavorites.toList(), history = history
            )
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                it.write(Json { prettyPrint = true }.encodeToString(backup))
            } ?: error("Could not write backup")
            message = "Full backup exported"
        }.onFailure { message = "Backup failed: ${it.message}" }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) runCatching {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: error("Could not read backup")
            require(text.length <= 5_000_000) { "Backup is larger than 5 MB" }
            // Accept either a full OrbitStrap backup or a plain JSON config file.
            val restored = runCatching { Json.decodeFromString<BackupEnvelope>(text) }.getOrNull()
            if (restored != null) {
                ConfigUtils.validate(restored.config)
                config = restored.config
                presets = restored.presets
                favorites = restored.favorites.toSet()
                flagFavorites = restored.flagFavorites.toSet()
                history = restored.history.take(30)
            } else {
                ConfigUtils.validate(text)
                config = text
            }
            dirty = true
            scope.launch { persistAll(); dirty = false; message = if (restored == null) "Configuration restored" else "Full backup restored" }
        }.onFailure { message = "Restore failed: ${it.message}" }
    }

    MaterialTheme(colorScheme = scheme) {
        Row(Modifier.fillMaxSize().background(if (darkMode) Bg else Color(0xFFF2F4FA))) {
            NavigationRail(
                containerColor = if (darkMode) Color(0xFF101522) else Color(0xFFE8EBF5),
                modifier = Modifier.fillMaxHeight().width(72.dp)
            ) {
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier.padding(bottom = 18.dp).size(42.dp).clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(listOf(accent, Color(0xFF51499B)))),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.RocketLaunch, null, tint = Color.White) }
                listOf(
                    "Home" to Icons.Default.Home, "Flags" to Icons.Default.Tune,
                    "Presets" to Icons.Default.Dashboard, "Join" to Icons.Default.Link,
                    "History" to Icons.Default.History, "Compare" to Icons.Default.CompareArrows,
                    "Tools" to Icons.Default.Build,
                    "Settings" to Icons.Default.Settings
                ).forEach { (label, icon) ->
                    NavigationRailItem(
                        selected = page == label, onClick = { page = label },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, fontSize = 9.sp) }, alwaysShowLabel = false
                    )
                }
                Spacer(Modifier.weight(1f))
                Text("1.1", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(bottom = 12.dp))
            }
            Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("ORBITSTRAP", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Text(page, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    }
                    Surface(color = Panel2, shape = CircleShape) {
                        Text(if (dirty) " UNSAVED " else " SAVED ", color = if (dirty) Amber else Green,
                            fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (message.isNotBlank()) {
                    Text(message, color = Muted, fontSize = 11.sp, maxLines = 2)
                    Spacer(Modifier.height(8.dp))
                }
                when (page) {
                    "Home" -> HomeScreen(
                        configCount = runCatching { ConfigUtils.parseObject(config).size }.getOrDefault(0),
                        presetCount = presets.size,
                        onLaunchGlobal = { launchRoblox(context, GLOBAL_ROBLOX) { message = it } },
                        onLaunchVn = { launchRoblox(context, VN_ROBLOX) { message = it } },
                        onGo = { page = it },
                        onImport = { importLauncher.launch(arrayOf("application/json", "text/*")) },
                        onExport = { exportLauncher.launch("orbitstrap-config.json") },
                        onBackup = { backupLauncher.launch("orbitstrap-full-backup.json") }
                    )
                    "Flags" -> FlagsScreen(
                        value = config,
                        favorites = flagFavorites,
                        onValue = { config = it; dirty = true },
                        onFavorite = { key -> flagFavorites = if (key in flagFavorites) flagFavorites - key else flagFavorites + key
                            scope.launch { persistAll() } },
                        validation = validation,
                        onValidate = { validation = validate(config) },
                        onSave = { saveConfig() },
                        onReset = { config = "{}"; dirty = true; validation = "Draft reset; save to persist" },
                        onImport = { importLauncher.launch(arrayOf("application/json", "text/*")) },
                        onExport = { exportLauncher.launch("orbitstrap-config.json") },
                        onCopy = {
                            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                .setPrimaryClip(ClipData.newPlainText("OrbitStrap JSON", config))
                            message = "Copied JSON to clipboard"
                        },
                        onFlagEdit = { key, raw, old ->
                            runCatching { ConfigUtils.upsertFlag(config, key, raw, old) }
                                .onSuccess {
                                    config = it; dirty = true; validation = validate(it)
                                    if (old != null && old != key && old in flagFavorites) {
                                        flagFavorites = (flagFavorites - old) + key
                                    }
                                    scope.launch { persistAll() }
                                    message = "Flag updated in local JSON"
                                }
                                .onFailure { message = "Flag not changed: ${it.message}" }
                        },
                        onFlagDelete = { key ->
                            runCatching { ConfigUtils.deleteFlag(config, key) }
                                .onSuccess {
                                    config = it; dirty = true; flagFavorites = flagFavorites - key
                                    scope.launch { persistAll() }
                                    message = "Flag removed from local JSON"
                                }
                                .onFailure { message = "Could not remove flag: ${it.message}" }
                        }
                    )
                    "Presets" -> PresetsScreen(
                        presets = presets, favorites = favorites,
                        onApply = { p -> config = p.json; dirty = true; validation = validate(p.json); page = "Flags"; message = "Preset loaded into editor" },
                        onFavorite = { p -> favorites = if (p.id in favorites) favorites - p.id else favorites + p.id
                            scope.launch { persistAll() } },
                        onDelete = { p -> presets = presets.filterNot { it.id == p.id }; scope.launch { persistAll(); message = "Preset deleted" } },
                        onCreate = { showPresetDialog = true },
                        onExport = { exportLauncher.launch("orbitstrap-config.json") }
                    )
                    "Join" -> JoinScreen(
                        link = lastJoinLink,
                        onLinkChange = { lastJoinLink = it },
                        onJoin = { link ->
                            lastJoinLink = link
                            scope.launch { context.store.edit { prefs -> prefs[KEY_LAST_JOIN] = link } }
                            openRobloxLink(context, link) { message = it }
                        },
                        onClear = { lastJoinLink = ""; scope.launch { context.store.edit { it.remove(KEY_LAST_JOIN) } } }
                    )
                    "History" -> HistoryScreen(
                        history = history,
                        onRestore = { snapshot ->
                            config = snapshot.json; dirty = true; validation = validate(snapshot.json); page = "Flags"
                            message = "Snapshot loaded into editor; save to keep it"
                        },
                        onDelete = { item -> history = history.filterNot { it.timestamp == item.timestamp }; scope.launch { persistAll() } },
                        onCompare = { item -> compareSnapshot = item; page = "Compare" },
                        onClear = { history = emptyList(); scope.launch { persistAll(); message = "History cleared" } }
                    )
                    "Compare" -> CompareScreen(
                        currentConfig = config,
                        snapshot = compareSnapshot,
                        onBack = { page = "History" }
                    )
                    "Tools" -> ToolsScreen(
                        context = context,
                        onBackup = { backupLauncher.launch("orbitstrap-backup.json") },
                        onRestore = { restoreLauncher.launch(arrayOf("application/json")) },
                        onExport = { exportLauncher.launch("orbitstrap-config.json") },
                        onImport = { importLauncher.launch(arrayOf("application/json", "text/*")) },
                        onCopy = {
                            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                .setPrimaryClip(ClipData.newPlainText("OrbitStrap JSON", config))
                            message = "Copied JSON"
                        },
                        onFormat = {
                            runCatching { config = ConfigUtils.format(config); dirty = true; validation = validate(config); message = "JSON formatted" }
                                .onFailure { message = "Format failed: ${it.message}" }
                        }
                    )
                    "Settings" -> SettingsScreen(
                        accentName = accentName,
                        onAccent = { accentName = it; scope.launch { persistAll() } },
                        darkMode = darkMode,
                        onTheme = { darkMode = it; scope.launch { persistAll() } },
                        onReset = {
                            config = "{}"; presets = defaultPresets(); favorites = emptySet(); flagFavorites = emptySet()
                            history = emptyList(); lastJoinLink = ""
                            dirty = true
                            scope.launch { persistAll(); dirty = false; message = "App data reset to defaults" }
                        }
                    )
                }
            }
        }
    }

    if (showPresetDialog) {
        AlertDialog(
            onDismissRequest = { showPresetDialog = false },
            title = { Text("Create preset") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(presetName, { presetName = it }, label = { Text("Preset name") }, singleLine = true)
                    OutlinedTextField(presetDescription, { presetDescription = it }, label = { Text("Description") })
                    Text("The current JSON will be saved as a reusable local preset.", color = Muted, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (presetName.isNotBlank() && validate(config).startsWith("Valid")) {
                        presets = presets + SavedPreset(System.currentTimeMillis().toString(), presetName.trim(), presetDescription.trim(), config)
                        scope.launch { persistAll(); message = "Preset created" }
                        presetName = ""; presetDescription = ""; showPresetDialog = false
                    } else message = "Enter a name and make sure the JSON is valid"
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showPresetDialog = false }) { Text("Cancel") } }
        )
    }
}

@Serializable
private data class BackupEnvelope(
    val appVersion: String,
    val createdAt: Long,
    val config: String,
    val presets: List<SavedPreset>,
    val favorites: List<String>,
    val flagFavorites: List<String> = emptyList(),
    val history: List<Snapshot>
)

private fun defaultPresets() = listOf(
    SavedPreset("balanced", "Balanced", "Conservative sample scheduler target; support may change.", """{"DFIntTaskSchedulerTargetFps":"60"}"""),
    SavedPreset("high-refresh", "High refresh draft", "Example only; game and device may ignore this value.", """{"DFIntTaskSchedulerTargetFps":"120"}"""),
    SavedPreset("visual-minimal", "Visual minimal", "Example visual preference; behavior is not guaranteed.", """{"FFlagDisablePostFx":"True"}"""),
    SavedPreset("empty", "Default / empty", "A clean configuration object.", "{}")
)

private fun launchRoblox(context: Context, packageName: String, report: (String) -> Unit) {
    val intent = context.packageManager.getLaunchIntentForPackage(packageName)
    if (intent == null) report("${if (packageName == VN_ROBLOX) "Roblox VN" else "Roblox"} was not found.")
    else runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); report("Roblox launch requested.") }
        .onFailure { report("Launch failed: ${it.message}") }
}

private fun openRobloxLink(context: Context, raw: String, report: (String) -> Unit) {
    runCatching {
        val text = raw.trim()
        require(text.isNotEmpty()) { "Paste a Roblox game link first" }
        require(text.length <= 2048) { "Link is too long" }
        val uri = Uri.parse(text)
        val isRobloxScheme = uri.scheme.equals("roblox", ignoreCase = true)
        val host = uri.host?.lowercase(Locale.ROOT).orEmpty()
        val isRobloxWeb = (uri.scheme == "https" || uri.scheme == "http") &&
            (host == "roblox.com" || host.endsWith(".roblox.com"))
        require(isRobloxScheme || isRobloxWeb) { "Only roblox:// or an official roblox.com link is accepted" }
        if (isRobloxScheme) require(host == "experiences" || host == "game") { "Unsupported Roblox link format" }
        val targetPackage = when {
            context.packageManager.getLaunchIntentForPackage(GLOBAL_ROBLOX) != null -> GLOBAL_ROBLOX
            context.packageManager.getLaunchIntentForPackage(VN_ROBLOX) != null -> VN_ROBLOX
            else -> throw IllegalStateException("Install Global Roblox or Roblox VN first")
        }
        val intent = Intent(Intent.ACTION_VIEW, uri).setPackage(targetPackage)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        report("Roblox received the link. The experience may still require access or a valid server.")
    }.onFailure { report("Could not open link: ${it.message}") }
}

private suspend fun checkRobloxEndpoints(): List<String> = withContext(Dispatchers.IO) {
    listOf("https://www.roblox.com/", "https://games.roblox.com/").map { address ->
        val connection = URL(address).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 4_000
            connection.readTimeout = 4_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "OrbitStrap-Diagnostics/1.1")
            val started = System.nanoTime()
            val status = connection.responseCode
            val elapsedMs = (System.nanoTime() - started) / 1_000_000
            "${URL(address).host} · HTTP $status · ${elapsedMs} ms"
        } catch (error: Exception) {
            "${URL(address).host} · failed · ${error.javaClass.simpleName}"
        } finally {
            connection.disconnect()
        }
    }
}

@Composable
private fun CardBox(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp), content = content)
    }
}

@Composable
private fun HomeScreen(
    configCount: Int, presetCount: Int, onLaunchGlobal: () -> Unit, onLaunchVn: () -> Unit,
    onGo: (String) -> Unit, onImport: () -> Unit, onExport: () -> Unit, onBackup: () -> Unit
) {
    val context = LocalContext.current
    val globalVersion = remember { runCatching { context.packageManager.getPackageInfo(GLOBAL_ROBLOX, 0).versionName }.getOrNull() }
    val vnVersion = remember { runCatching { context.packageManager.getPackageInfo(VN_ROBLOX, 0).versionName }.getOrNull() }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(23.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF302A5C), Color(0xFF171B2B), Color(0xFF14242D))))) {
                Column(Modifier.padding(19.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("ANDROID COMPANION", color = Color(0xFFD5D0FF), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Text("Your Roblox setup.\nYour way.", fontSize = 26.sp, lineHeight = 31.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Launch, manage local profiles, inspect device status and open supported game links.", color = Color(0xFFB8BED0), fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onLaunchGlobal, modifier = Modifier.weight(1f)) { Icon(Icons.Default.PlayArrow, null); Text(" Roblox") }
                        OutlinedButton(onClick = onLaunchVn, modifier = Modifier.weight(1f)) { Text("Roblox VN") }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                MetricCard("CONFIG KEYS", configCount.toString(), Modifier.weight(1f))
                MetricCard("PRESETS", presetCount.toString(), Modifier.weight(1f))
            }
        }
        item {
            CardBox {
                Text("Installed clients", fontWeight = FontWeight.Bold)
                InfoLine("Global Roblox", globalVersion ?: "Not detected")
                InfoLine("Roblox VN", vnVersion ?: "Not detected")
                Text("OrbitStrap launches the installed official app; it does not patch its package.", color = Muted, fontSize = 10.sp)
            }
        }
        item {
            CardBox {
                Text("Quick actions", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    OutlinedButton(onClick = { onGo("Flags") }, modifier = Modifier.weight(1f)) { Text("Flag manager") }
                    OutlinedButton(onClick = { onGo("Presets") }, modifier = Modifier.weight(1f)) { Text("Profiles") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    OutlinedButton(onClick = { onGo("Join") }, modifier = Modifier.weight(1f)) { Text("Join by link") }
                    OutlinedButton(onClick = { onGo("Tools") }, modifier = Modifier.weight(1f)) { Text("Device tools") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) { Text("Import") }
                    OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) { Text("Export") }
                    OutlinedButton(onClick = onBackup, modifier = Modifier.weight(1f)) { Text("Backup") }
                }
            }
        }
        item { Text("Local-first · non-root · Android-native", color = Muted, fontSize = 10.sp) }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(color = Panel, shape = RoundedCornerShape(16.dp), modifier = modifier) {
        Column(Modifier.padding(13.dp)) {
            Text(label, color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text(value, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FlagsScreen(
    value: String, favorites: Set<String>, onValue: (String) -> Unit, onFavorite: (String) -> Unit,
    validation: String, onValidate: () -> Unit, onSave: () -> Unit, onReset: () -> Unit,
    onImport: () -> Unit, onExport: () -> Unit, onCopy: () -> Unit,
    onFlagEdit: (newKey: String, rawValue: String, oldKey: String?) -> Unit,
    onFlagDelete: (String) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var favoritesOnly by remember { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf(false) }
    var editingKey by remember { mutableStateOf<String?>(null) }
    var draftKey by remember { mutableStateOf("") }
    var draftValue by remember { mutableStateOf("\"\"") }
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    val flagEntries = remember(value) { runCatching { ConfigUtils.parseObject(value).toList().sortedBy { it.first.lowercase(Locale.ROOT) } }.getOrDefault(emptyList()) }
    val visibleEntries = flagEntries.filter { (key, _) ->
        (key.contains(query, true) || query.isBlank()) && (!favoritesOnly || key in favorites)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
        Text("Edit the local JSON draft. Roblox may ignore unsupported or restricted flags.", color = Muted, fontSize = 11.sp)
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Visual editor") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Key manager") })
        }
        if (selectedTab == 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = onValidate, modifier = Modifier.weight(1f)) { Text("Validate") }
                Button(onClick = onSave, modifier = Modifier.weight(1f)) { Text("Save") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) { Text("Import") }
                OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) { Text("Export") }
                OutlinedButton(onClick = onCopy, modifier = Modifier.weight(1f)) { Text("Copy") }
            }
            Text(validation, color = if (validation.startsWith("Valid")) Green else Amber, fontSize = 11.sp)
            Surface(color = Color(0xFF0D111B), shape = RoundedCornerShape(13.dp), modifier = Modifier.weight(1f).fillMaxWidth()) {
                BasicTextField(
                    value = value, onValueChange = onValue,
                    modifier = Modifier.fillMaxSize().padding(13.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFFDDE5F8), fontSize = 12.sp, lineHeight = 18.sp),
                    decorationBox = { inner -> Box { if (value.isEmpty()) Text("Paste JSON here…", color = Muted, fontSize = 12.sp); inner() } }
                )
            }
            OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) { Text("Reset editor") }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(query, { query = it }, modifier = Modifier.weight(1f), singleLine = true, label = { Text("Search flag names") })
                Button(onClick = { editingKey = null; draftKey = ""; draftValue = "\"\""; showEditor = true }) {
                    Icon(Icons.Default.Add, null); Text(" Add")
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = favoritesOnly, onClick = { favoritesOnly = !favoritesOnly }, label = { Text("Favorites") })
                Spacer(Modifier.weight(1f))
                Text("${visibleEntries.size} / ${flagEntries.size}", color = Muted, fontSize = 11.sp)
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.weight(1f)) {
                items(visibleEntries, key = { it.first }) { (key, flagValue) ->
                    CardBox {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(key, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                Text(flagValue.toString(), color = Muted, fontSize = 11.sp, maxLines = 2)
                            }
                            IconButton(onClick = { onFavorite(key) }) {
                                Icon(if (key in favorites) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = "Favorite flag", tint = Amber)
                            }
                            IconButton(onClick = { editingKey = key; draftKey = key; draftValue = flagValue.toString(); showEditor = true }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit flag")
                            }
                            IconButton(onClick = { pendingDelete = key }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete flag", tint = Amber)
                            }
                        }
                    }
                }
                if (visibleEntries.isEmpty()) item { CardBox { Text("No matching flags"); Text("Add a key or change the search filter.", color = Muted, fontSize = 11.sp) } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedButton(onClick = onValidate, modifier = Modifier.weight(1f)) { Text("Validate") }
                Button(onClick = onSave, modifier = Modifier.weight(1f)) { Text("Save changes") }
            }
        }
    }

    if (showEditor) {
        AlertDialog(
            onDismissRequest = { showEditor = false },
            title = { Text(if (editingKey == null) "Add flag" else "Edit flag") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    OutlinedTextField(draftKey, { draftKey = it }, label = { Text("Flag name") }, singleLine = true)
                    OutlinedTextField(draftValue, { draftValue = it }, label = { Text("Raw JSON value") }, minLines = 2)
                    Text("Enter a JSON primitive: \"text\", 120, true, false, or null.", color = Muted, fontSize = 10.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { onFlagEdit(draftKey, draftValue, editingKey); showEditor = false }) { Text("Save flag") }
            },
            dismissButton = { TextButton(onClick = { showEditor = false }) { Text("Cancel") } }
        )
    }
    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete flag?") },
            text = { Text("Remove ${pendingDelete} from the local JSON draft? This will not change the live Roblox app.") },
            confirmButton = { TextButton(onClick = { pendingDelete?.let(onFlagDelete); pendingDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun PresetsScreen(
    presets: List<SavedPreset>, favorites: Set<String>, onApply: (SavedPreset) -> Unit,
    onFavorite: (SavedPreset) -> Unit, onDelete: (SavedPreset) -> Unit,
    onCreate: () -> Unit, onExport: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var onlyFavorites by remember { mutableStateOf(false) }
    val shown = presets.filter {
        (it.name.contains(query, true) || it.description.contains(query, true)) && (!onlyFavorites || it.id in favorites)
    }
    Column(verticalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Search presets") })
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = onlyFavorites, onClick = { onlyFavorites = !onlyFavorites }, label = { Text("Favorites only") })
            Spacer(Modifier.weight(1f)); Text("${shown.size} presets", color = Muted, fontSize = 11.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Button(onClick = onCreate, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Add, null); Text(" Create preset") }
            OutlinedButton(onClick = onExport) { Text("Export JSON") }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(shown, key = { it.id }) { preset ->
                CardBox {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(preset.name, fontWeight = FontWeight.Bold)
                            Text(preset.description, color = Muted, fontSize = 11.sp)
                        }
                        IconButton(onClick = { onFavorite(preset) }) {
                            Icon(if (preset.id in favorites) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Favorite", tint = Amber)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Button(onClick = { onApply(preset) }, modifier = Modifier.weight(1f)) { Text("Load into editor") }
                        if (preset.id !in setOf("balanced", "high-refresh", "visual-minimal", "empty")) {
                            OutlinedButton(onClick = { onDelete(preset) }) { Text("Delete") }
                        }
                    }
                }
            }
            if (shown.isEmpty()) item { CardBox { Text("No presets found") } }
        }
    }
}

@Composable
private fun CompareScreen(currentConfig: String, snapshot: Snapshot?, onBack: () -> Unit) {
    val oldMap = remember(snapshot) { runCatching { snapshot?.let { ConfigUtils.parseObject(it.json) } }.getOrNull() }
    val newMap = remember(currentConfig) { runCatching { ConfigUtils.parseObject(currentConfig) }.getOrNull() }
    var changesOnly by remember { mutableStateOf(true) }
    val rows = remember(oldMap, newMap) {
        if (oldMap == null || newMap == null) emptyList() else {
            (oldMap.keys + newMap.keys).toSortedSet().map { key ->
                val oldValue = oldMap[key]
                val newValue = newMap[key]
                val status = when {
                    oldValue == null -> "ADDED"
                    newValue == null -> "REMOVED"
                    oldValue.toString() != newValue.toString() -> "CHANGED"
                    else -> "UNCHANGED"
                }
                Triple(key, status, "${oldValue?.toString() ?: "—"}  →  ${newValue?.toString() ?: "—"}")
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Snapshot vs current draft", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = onBack) { Text("Back") }
        }
        if (snapshot == null) {
            CardBox { Text("Choose a snapshot from History first.", color = Muted) }
        } else if (oldMap == null || newMap == null) {
            CardBox { Text("Comparison unavailable", color = Amber); Text("Both configurations must be valid JSON objects.", color = Muted, fontSize = 11.sp) }
        } else {
            CardBox {
                Text(snapshot.label, fontWeight = FontWeight.Bold)
                Text("${rows.count { it.second == "ADDED" }} added · ${rows.count { it.second == "REMOVED" }} removed · ${rows.count { it.second == "CHANGED" }} changed", color = Muted, fontSize = 11.sp)
                FilterChip(selected = changesOnly, onClick = { changesOnly = !changesOnly }, label = { Text("Changes only") })
            }
            val visible = if (changesOnly) rows.filter { it.second != "UNCHANGED" } else rows
            LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.weight(1f)) {
                items(visible, key = { it.first }) { (key, status, detail) ->
                    CardBox {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(key, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Text(status, color = when (status) { "ADDED" -> Green; "REMOVED" -> Amber; "CHANGED" -> Violet; else -> Muted }, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(detail, color = Muted, fontSize = 10.sp)
                    }
                }
                if (visible.isEmpty()) item { CardBox { Text("No differences found") } }
            }
        }
    }
}

@Composable
private fun JoinScreen(link: String, onLinkChange: (String) -> Unit, onJoin: (String) -> Unit, onClear: () -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(11.dp)) {
        item {
            CardBox {
                Text("Open a Roblox game link", fontWeight = FontWeight.Bold)
                Text("Paste a roblox:// link or an official roblox.com experience link. OrbitStrap checks the link host and asks the installed Roblox app to open it.", color = Muted, fontSize = 11.sp)
                OutlinedTextField(
                    value = link, onValueChange = onLinkChange, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Roblox game link") }, minLines = 3
                )
                Button(onClick = { onJoin(link) }, enabled = link.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.OpenInNew, null); Text(" Open in Roblox")
                }
                TextButton(onClick = onClear, enabled = link.isNotBlank()) { Text("Clear saved link") }
            }
        }
        item {
            CardBox {
                Text("Supported link types", fontWeight = FontWeight.Bold)
                Text("• roblox://experiences/start?...\n• https://www.roblox.com/games/...\n• https://www.roblox.com/share?...", color = Muted, fontSize = 11.sp)
                Text("A valid link does not guarantee that the server still exists or that your account can join it.", color = Muted, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun HistoryScreen(
    history: List<Snapshot>, onRestore: (Snapshot) -> Unit, onDelete: (Snapshot) -> Unit,
    onCompare: (Snapshot) -> Unit, onClear: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Recent local snapshots · ${history.size}/30", color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = onClear, enabled = history.isNotEmpty()) { Text("Clear all") }
        }
        if (history.isEmpty()) {
            CardBox { Text("No snapshots yet"); Text("Save a valid configuration to create a restore point.", color = Muted, fontSize = 11.sp) }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                items(history, key = { it.timestamp }) { item ->
                    CardBox {
                        Text(item.label, fontWeight = FontWeight.Bold)
                        Text(SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()).format(Date(item.timestamp)), color = Muted, fontSize = 10.sp)
                        Text("${item.json.length} characters", color = Muted, fontSize = 10.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            Button(onClick = { onRestore(item) }, modifier = Modifier.weight(1f)) { Text("Restore") }
                            OutlinedButton(onClick = { onCompare(item) }) { Text("Compare") }
                            OutlinedButton(onClick = { onDelete(item) }) { Text("Delete") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolsScreen(
    context: Context, onBackup: () -> Unit, onRestore: () -> Unit, onExport: () -> Unit,
    onImport: () -> Unit, onCopy: () -> Unit, onFormat: () -> Unit
) {
    var refreshToken by remember { mutableIntStateOf(0) }
    val connectivity = remember(refreshToken) {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork
        val caps = network?.let { manager.getNetworkCapabilities(it) }
        when {
            caps == null -> "Offline / no active network"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Connected · Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Connected · mobile data"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Connected · Ethernet"
            else -> "Connected · other transport"
        }
    }
    val refreshRate = remember(refreshToken) {
        runCatching { "${(context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.refreshRate.toInt()} Hz" }
            .getOrDefault("Unavailable")
    }
    val batterySummary = remember(refreshToken) {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) "${(level * 100) / scale}%" else "Unavailable"
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        "$percent · ${if (charging) "charging/full" else "not charging"}"
    }
    val thermal = remember(refreshToken) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            when (pm.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> "Normal"
                PowerManager.THERMAL_STATUS_LIGHT -> "Light"
                PowerManager.THERMAL_STATUS_MODERATE -> "Moderate"
                PowerManager.THERMAL_STATUS_SEVERE -> "Severe"
                PowerManager.THERMAL_STATUS_CRITICAL -> "Critical"
                PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency"
                PowerManager.THERMAL_STATUS_SHUTDOWN -> "Shutdown threshold"
                else -> "Unknown"
            }
        } else "Not available on this Android version"
    }
    val memory = remember(refreshToken) {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val info = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)
        val totalGb = info.totalMem / (1024.0 * 1024.0 * 1024.0)
        val availableGb = info.availMem / (1024.0 * 1024.0 * 1024.0)
        "${String.format(Locale.US, "%.1f", availableGb)} GB free / ${String.format(Locale.US, "%.1f", totalGb)} GB total"
    }
    val scope = rememberCoroutineScope()
    var endpointResults by remember { mutableStateOf<List<String>>(emptyList()) }
    var endpointBusy by remember { mutableStateOf(false) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            CardBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Device diagnostics", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { refreshToken++ }) { Icon(Icons.Default.Refresh, contentDescription = "Refresh diagnostics") }
                }
                InfoLine("Android", "${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}")
                InfoLine("Device", "${Build.MANUFACTURER} ${Build.MODEL}")
                InfoLine("Display refresh", refreshRate)
                InfoLine("Battery", batterySummary)
                InfoLine("Thermal status", thermal)
                InfoLine("Device RAM", memory)
                InfoLine("Network", connectivity)
                Text("Thermal and memory readings are device-wide snapshots. OEM behavior can differ.", color = Muted, fontSize = 10.sp)
            }
        }
        item {
            CardBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Roblox service reachability", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (endpointBusy) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                }
                Text("Checks HTTPS access to Roblox web services. This is not in-game server ping.", color = Muted, fontSize = 10.sp)
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            endpointBusy = true
                            endpointResults = runCatching { checkRobloxEndpoints() }
                                .getOrElse { listOf("Check failed · ${it.message ?: it.javaClass.simpleName}") }
                            endpointBusy = false
                        }
                    },
                    enabled = !endpointBusy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Test Roblox connection") }
                endpointResults.forEach { Text(it, color = Muted, fontSize = 10.sp) }
            }
        }
        item {
            CardBox {
                Text("Configuration tools", fontWeight = FontWeight.Bold)
                ToolButton("Format JSON", onFormat)
                ToolButton("Copy JSON", onCopy)
                ToolButton("Import configuration", onImport)
                ToolButton("Export configuration", onExport)
            }
        }
        item {
            CardBox {
                Text("Full backup", fontWeight = FontWeight.Bold)
                Text("Includes JSON, custom presets, favorites, and snapshot history.", color = Muted, fontSize = 11.sp)
                ToolButton("Create full backup", onBackup)
                ToolButton("Restore backup or JSON", onRestore)
            }
        }
        item {
            CardBox {
                Text("Android limitations", fontWeight = FontWeight.Bold)
                Text("A separate app cannot reliably read Roblox's private process stats, change its CPU/GPU governor, inject overlays into the game, or write private client data without authorized platform-level access. OrbitStrap does not bypass allowlists or anti-cheat protections.", color = Muted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    accentName: String, onAccent: (String) -> Unit, darkMode: Boolean,
    onTheme: (Boolean) -> Unit, onReset: () -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            CardBox {
                Text("Appearance", fontWeight = FontWeight.Bold)
                Text("Accent color", color = Muted, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Violet", "Cyan", "Rose", "Amber", "Green").forEach { name ->
                        FilterChip(selected = accentName == name, onClick = { onAccent(name) }, label = { Text(name) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Dark theme", modifier = Modifier.weight(1f))
                    Switch(checked = darkMode, onCheckedChange = onTheme)
                }
            }
        }
        item {
            CardBox {
                Text("Storage", fontWeight = FontWeight.Bold)
                Text("Config, presets, favorites, history, and the most recent game link are stored locally.", color = Muted, fontSize = 11.sp)
                TextButton(onClick = onReset) { Text("Reset all app data", color = Amber) }
            }
        }
        item {
            CardBox {
                Text("About OrbitStrap", fontWeight = FontWeight.Bold)
                InfoLine("Version", "1.1.0")
                InfoLine("Package", "com.odil9.orbitstrap")
                InfoLine("Support", "Android 8.0+ · non-root")
                Text("FFlag behavior is controlled by Roblox and can change. A saved JSON file is not proof that Roblox reads or honors those values.", color = Muted, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
        Text(value, fontSize = 11.sp)
    }
}

@Composable
private fun ToolButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label) }
}
