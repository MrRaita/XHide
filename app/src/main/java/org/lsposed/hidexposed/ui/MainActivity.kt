package org.lsposed.hidexposed.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
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
    val userId: Int,
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
    val isXposedModule: Boolean,
    val isSystemApp: Boolean
) {
    val uniqueKey: String get() = ConfigStore.userKey(userId, packageName)
}

sealed interface FilterMode {
    data object XposedOnly : FilterMode
    data object AllApps : FilterMode
    data class ByUser(val userId: Int) : FilterMode
}

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
    var availableUserIds by remember { mutableStateOf<List<Int>>(listOf(0)) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var filterMode by remember { mutableStateOf<FilterMode>(FilterMode.XposedOnly) }

    fun isEntryHidden(entry: AppEntry, wl: Set<String>, bl: Set<String>, autoHide: Boolean): Boolean {
        val uKey = entry.uniqueKey
        return when {
            bl.contains(uKey) || bl.contains(entry.packageName) -> true
            wl.contains(uKey) || wl.contains(entry.packageName) -> false
            else -> autoHide && entry.isXposedModule
        }
    }

    fun refreshState() {
        scope.launch {
            isLoading = true
            val active = withContext(Dispatchers.IO) {
                ConfigStore.pingSystemHook(context)
            }
            val loadedApps = withContext(Dispatchers.IO) {
                loadInstalledAppsAcrossProfiles(context)
            }
            isHookActive = active
            apps = loadedApps
            availableUserIds = loadedApps.map { it.userId }.distinct().sorted()
            if (active) {
                withContext(Dispatchers.IO) {
                    val hiddenPkgs = loadedApps
                        .filter { isEntryHidden(it, whitelist, blacklist, autoHideModules) }
                        .map { it.packageName }
                        .distinct()
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
        val uKey = entry.uniqueKey

        // Clean up any legacy unprefixed package key if toggling per-user
        newWl.remove(entry.packageName)
        newBl.remove(entry.packageName)

        if (hideInLauncher) {
            newWl.remove(uKey)
            if (!entry.isXposedModule || !autoHideModules) {
                newBl.add(uKey)
            }
        } else {
            newBl.remove(uKey)
            if (entry.isXposedModule) {
                newWl.add(uKey)
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

    val filteredApps = remember(apps, searchQuery, filterMode) {
        apps.filter { app ->
            val matchesMode = when (val mode = filterMode) {
                is FilterMode.XposedOnly -> app.isXposedModule
                is FilterMode.AllApps -> !app.isSystemApp || app.isXposedModule || app.userId != 0
                is FilterMode.ByUser -> app.userId == mode.userId && (!app.isSystemApp || app.isXposedModule || app.userId != 0)
            }
            val matchesQuery = searchQuery.isBlank() ||
                app.label.contains(searchQuery, ignoreCase = true) ||
                app.packageName.contains(searchQuery, ignoreCase = true)
            matchesMode && matchesQuery
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("HideXposedIcons", fontWeight = FontWeight.Bold)
                        Text(
                            "System-Level Multi-User Icon Filter",
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
                                "Tüm profiller (User 0, User 10 vb.) üzerinde gerçek zamanlı gizleme aktif."
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
                            text = "Tüm profillerdeki Xposed modüllerini otomatik olarak launcher'dan gizler",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoHideModules,
                        onCheckedChange = { checked ->
                            autoHideModules = checked
                            val modulePackages = apps.filter { it.isXposedModule }.map { it.packageName }.distinct()
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

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Modül veya paket ara (örn. reddidnt, discord)...") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Multi-User Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filterMode is FilterMode.XposedOnly,
                    onClick = { filterMode = FilterMode.XposedOnly },
                    label = { Text("Xposed (${apps.count { it.isXposedModule }})") }
                )
                for (uid in availableUserIds) {
                    val countForUser = apps.count {
                        it.userId == uid && (!it.isSystemApp || it.isXposedModule || it.userId != 0)
                    }
                    FilterChip(
                        selected = (filterMode as? FilterMode.ByUser)?.userId == uid,
                        onClick = { filterMode = FilterMode.ByUser(uid) },
                        label = {
                            Text(
                                if (uid == 0) "User 0 ($countForUser)"
                                else "User $uid ($countForUser)"
                            )
                        }
                    )
                }
                FilterChip(
                    selected = filterMode is FilterMode.AllApps,
                    onClick = { filterMode = FilterMode.AllApps },
                    label = { Text("Tümü") }
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
                    items(filteredApps, key = { it.uniqueKey }) { entry ->
                        val isHidden = isEntryHidden(entry, whitelist, blacklist, autoHideModules)

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            tonalElevation = 2.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (entry.icon != null) {
                                    Image(
                                        bitmap = entry.icon,
                                        contentDescription = entry.label,
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = entry.label,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (entry.userId != 0) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = "User ${entry.userId}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        if (entry.isXposedModule) {
                                            Spacer(modifier = Modifier.width(6.dp))
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
                                Spacer(modifier = Modifier.width(10.dp))
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

private fun loadInstalledAppsAcrossProfiles(context: Context): List<AppEntry> {
    val pm = context.packageManager
    val launcherApps = context.getSystemService(LauncherApps::class.java)
    val userManager = context.getSystemService(UserManager::class.java)

    val profiles = LinkedHashSet<UserHandle>()
    profiles.add(Process.myUserHandle())
    try {
        launcherApps?.profiles?.let { profiles.addAll(it) }
    } catch (_: Throwable) {
    }
    try {
        userManager?.userProfiles?.let { profiles.addAll(it) }
    } catch (_: Throwable) {
    }
    // Probe common Clone / Work / Dual Messenger user IDs (e.g., User 10..15, 95) via public SDK API
    for (candidateId in listOf(10, 11, 12, 13, 14, 15, 95)) {
        try {
            profiles.add(UserHandle.getUserHandleForUid(candidateId * 100000))
        } catch (_: Throwable) {
        }
    }

    val entriesMap = LinkedHashMap<String, AppEntry>()

    // 1. Query all launcher activities across all profiles (User 0, User 10 Clone/Work Profile, etc.)
    if (launcherApps != null) {
        val allActivities = mutableListOf<android.content.pm.LauncherActivityInfo>()
        try {
            val fromIpc = launcherApps.getActivityList(ConfigStore.IPC_GET_ALL_USERS, Process.myUserHandle())
            if (fromIpc != null && fromIpc.size > 1) {
                allActivities.addAll(fromIpc)
            }
        } catch (_: Throwable) {
        }

        for (profile in profiles) {
            try {
                val list = launcherApps.getActivityList(null, profile)
                if (!list.isNullOrEmpty()) {
                    allActivities.addAll(list)
                }
            } catch (_: Throwable) {
            }
        }

        for (info in allActivities) {
            val appInfo = info.applicationInfo ?: continue
            val pkg = appInfo.packageName ?: continue
            if (pkg == "android" || ModuleCache.KNOWN_MANAGERS.contains(pkg)) continue

            val userId = info.user?.hashCode() ?: 0
            val key = ConfigStore.userKey(userId, pkg)
            if (entriesMap.containsKey(key)) continue

            val isXposed = detectIsXposedApp(appInfo, pm)
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0

            val label = try {
                info.label?.toString() ?: pm.getApplicationLabel(appInfo).toString()
            } catch (_: Throwable) {
                pkg
            }

            val iconBitmap = try {
                drawableToImageBitmap(info.getBadgedIcon(0))
            } catch (_: Throwable) {
                null
            }

            entriesMap[key] = AppEntry(
                userId = userId,
                packageName = pkg,
                label = label,
                icon = iconBitmap,
                isXposedModule = isXposed,
                isSystemApp = isSystem
            )
        }
    }

    // 2. Also include any User 0 Xposed modules that might not currently expose a launcher activity
    val user0Apps = try {
        pm.getInstalledApplications(PackageManager.GET_META_DATA)
    } catch (_: Throwable) {
        emptyList()
    }
    for (appInfo in user0Apps) {
        val pkg = appInfo.packageName ?: continue
        if (pkg == "android" || ModuleCache.KNOWN_MANAGERS.contains(pkg)) continue
        val key = ConfigStore.userKey(0, pkg)
        if (entriesMap.containsKey(key)) continue

        val isXposed = detectIsXposedApp(appInfo, pm)
        if (!isXposed) continue

        val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
            (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
        val label = try {
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Throwable) {
            pkg
        }
        val iconBitmap = try {
            drawableToImageBitmap(pm.getApplicationIcon(appInfo))
        } catch (_: Throwable) {
            null
        }

        entriesMap[key] = AppEntry(
            userId = 0,
            packageName = pkg,
            label = label,
            icon = iconBitmap,
            isXposedModule = true,
            isSystemApp = isSystem
        )
    }

    return entriesMap.values.sortedWith(
        compareByDescending<AppEntry> { it.isXposedModule }
            .thenBy { it.userId }
            .thenBy { it.label.lowercase() }
    )
}

private fun drawableToImageBitmap(drawable: Drawable?, sizePx: Int = 112): ImageBitmap? {
    if (drawable == null) return null
    return try {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(canvas)
        bitmap.asImageBitmap()
    } catch (_: Throwable) {
        null
    }
}

private fun detectIsXposedApp(appInfo: ApplicationInfo, pm: PackageManager): Boolean {
    val meta = appInfo.metaData ?: try {
        pm.getApplicationInfo(appInfo.packageName, PackageManager.GET_META_DATA).metaData
    } catch (_: Throwable) {
        null
    }
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
