package org.lsposed.hidexposed.ui

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lsposed.hidexposed.core.ModuleCache
import org.lsposed.hidexposed.data.ConfigStore
import org.lsposed.hidexposed.ui.theme.HideXposedTheme
import java.io.File
import java.util.zip.ZipFile

data class AppEntry(
    val packageName: String,
    val label: String,
    val isXposedModule: Boolean,
    val isSystemApp: Boolean
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ConfigStore.loadUiPrefs(this)
        setContent {
            HideXposedTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isHookActive by remember { mutableStateOf(false) }
    var autoHideModules by remember { mutableStateOf(ConfigStore.autoHideXposedModules) }
    var whitelist by remember { mutableStateOf(ConfigStore.getWhitelist()) }
    var blacklist by remember { mutableStateOf(ConfigStore.getBlacklist()) }
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var showOnlyModules by remember { mutableStateOf(true) }

    fun refreshState() {
        scope.launch {
            isLoading = true
            val active = withContext(Dispatchers.IO) {
                ConfigStore.pingSystemHook(context)
            }
            val loadedApps = withContext(Dispatchers.IO) {
                loadInstalledApps(context.packageManager)
            }
            isHookActive = active
            apps = loadedApps
            if (active) {
                withContext(Dispatchers.IO) {
                    val hiddenPkgs = loadedApps.filter { app ->
                        if (blacklist.contains(app.packageName)) true
                        else if (whitelist.contains(app.packageName)) false
                        else autoHideModules && app.isXposedModule
                    }.map { it.packageName }
                    ConfigStore.saveUiPrefsAndSync(
                        context = context,
                        autoHide = autoHideModules,
                        whitelist = whitelist,
                        blacklist = blacklist,
                        changedPackages = hiddenPkgs
                    )
                }
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshState()
    }

    fun updateAppHidden(entry: AppEntry, hideInLauncher: Boolean) {
        val newWl = whitelist.toMutableSet()
        val newBl = blacklist.toMutableSet()

        if (hideInLauncher) {
            newWl.remove(entry.packageName)
            if (!entry.isXposedModule || !autoHideModules) {
                newBl.add(entry.packageName)
            }
        } else {
            newBl.remove(entry.packageName)
            if (entry.isXposedModule) {
                newWl.add(entry.packageName)
            }
        }

        whitelist = newWl
        blacklist = newBl
        scope.launch(Dispatchers.IO) {
            ConfigStore.saveUiPrefsAndSync(
                context = context,
                autoHide = autoHideModules,
                whitelist = newWl,
                blacklist = newBl,
                changedPackages = listOf(entry.packageName)
            )
        }
    }

    val filteredApps = remember(apps, searchQuery, showOnlyModules) {
        apps.filter { app ->
            val matchesFilter = if (showOnlyModules) app.isXposedModule else !app.isSystemApp || app.isXposedModule
            val matchesQuery = searchQuery.isBlank() ||
                app.label.contains(searchQuery, ignoreCase = true) ||
                app.packageName.contains(searchQuery, ignoreCase = true)
            matchesFilter && matchesQuery
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("HideXposedIcons", fontWeight = FontWeight.Bold)
                        Text(
                            "System-Level Launcher Icon Filter",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { refreshState() }) {
                        Text("Yenile")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Status Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isHookActive) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    }
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(
                                if (isHookActive) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error
                            )
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isHookActive) "Sistem Hook Aktif (LauncherAppsService)" else "Sistem Hook Bekleniyor",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (isHookActive) {
                                "Modüller tüm launcher'larda (ana ekran ve arama) gerçek zamanlı gizleniyor."
                            } else {
                                "Vector / LSPosed üzerinden modülü etkinleştirip kapsamda Sistem Çerçevesi (android) seçili olduğundan emin olun."
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Auto-hide toggle card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Xposed Modüllerini Otomatik Gizle",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Yeni yüklenen Xposed modüllerini otomatik olarak launcher'dan gizler",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoHideModules,
                        onCheckedChange = { checked ->
                            autoHideModules = checked
                            val modulePackages = apps.filter { it.isXposedModule }.map { it.packageName }
                            scope.launch(Dispatchers.IO) {
                                ConfigStore.saveUiPrefsAndSync(
                                    context = context,
                                    autoHide = checked,
                                    whitelist = whitelist,
                                    blacklist = blacklist,
                                    changedPackages = modulePackages
                                )
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search & Filter Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Modül veya paket ara (örn. reddidnt)...") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = showOnlyModules,
                    onClick = { showOnlyModules = true },
                    label = { Text("Xposed Modülleri (${apps.count { it.isXposedModule }})") }
                )
                FilterChip(
                    selected = !showOnlyModules,
                    onClick = { showOnlyModules = false },
                    label = { Text("Tüm Uygulamalar") }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredApps, key = { it.packageName }) { entry ->
                        val isHidden = when {
                            blacklist.contains(entry.packageName) -> true
                            whitelist.contains(entry.packageName) -> false
                            else -> autoHideModules && entry.isXposedModule
                        }

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            tonalElevation = 2.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = entry.label,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (entry.isXposedModule) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Surface(
                                                color = MaterialTheme.colorScheme.secondaryContainer,
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = "Xposed",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = entry.packageName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(horizontalAlignment = Alignment.End) {
                                    Switch(
                                        checked = isHidden,
                                        onCheckedChange = { hide ->
                                            updateAppHidden(entry, hide)
                                        }
                                    )
                                    Text(
                                        text = if (isHidden) "Gizli" else "Görünür",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isHidden) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun loadInstalledApps(pm: PackageManager): List<AppEntry> {
    val packages = try {
        pm.getInstalledApplications(PackageManager.GET_META_DATA)
    } catch (_: Throwable) {
        emptyList()
    }

    val result = mutableListOf<AppEntry>()
    for (appInfo in packages) {
        val pkg = appInfo.packageName ?: continue
        if (pkg == "android" || ModuleCache.KNOWN_MANAGERS.contains(pkg)) continue

        val isXposed = detectIsXposedApp(appInfo)
        val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
            (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0

        if (!isXposed && isSystem) continue

        val label = try {
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Throwable) {
            pkg
        }

        result.add(
            AppEntry(
                packageName = pkg,
                label = label,
                isXposedModule = isXposed,
                isSystemApp = isSystem
            )
        )
    }

    return result.sortedWith(
        compareByDescending<AppEntry> { it.isXposedModule }
            .thenBy { it.label.lowercase() }
    )
}

private fun detectIsXposedApp(appInfo: ApplicationInfo): Boolean {
    val meta = appInfo.metaData
    if (meta != null && (
            meta.containsKey("xposedmodule") ||
                meta.containsKey("xposedminversion") ||
                meta.containsKey("xposeddescription") ||
                meta.containsKey("xposedscope")
            )
    ) {
        return true
    }

    val src = appInfo.sourceDir
    if (!src.isNullOrEmpty()) {
        try {
            val file = File(src)
            if (file.exists() && file.canRead()) {
                ZipFile(file).use { zip ->
                    if (zip.getEntry("assets/xposed_init") != null ||
                        zip.getEntry("META-INF/xposed/module.prop") != null ||
                        zip.getEntry("META-INF/xposed/java_init.list") != null ||
                        zip.getEntry("META-INF/xposed/scope.list") != null
                    ) {
                        return true
                    }
                }
            }
        } catch (_: Throwable) {
        }
    }
    return false
}
