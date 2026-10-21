package io.github.ernisn.applingo.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.util.AtomicFile
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class AppInfo(
    val packageName: String,
    val label: String,
    val versionName: String? = null,
    val versionCode: Long = 0,
    val firstInstallTime: Long = 0,
    val lastUpdateTime: Long = 0,
    val isSystem: Boolean = false,
    val isUpdatedSystem: Boolean = false,
    val isLaunchable: Boolean = false,
    val isEnabled: Boolean = true,
    val targetSdk: Int = 0,
    val minSdk: Int = 0,
    val uid: Int = 0,
)

@Serializable
private data class AppCache(
    val version: Int = CACHE_VERSION,
    /** UI locales the labels were resolved in; labels are reloaded when they change. */
    val labelLocales: String = "",
    val apps: List<AppInfo> = emptyList(),
)

private const val CACHE_VERSION = 1

/**
 * Installed apps, served from an on-disk snapshot first so the list shows instantly, then refreshed
 * from PackageManager. Labels (the slow part) are only reloaded for packages that changed.
 */
class AppRepository(private val context: Context) {

    private val _apps = MutableStateFlow<List<AppInfo>?>(null)
    /** null until either the snapshot or the first scan is available. */
    val apps: StateFlow<List<AppInfo>?> = _apps.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    var lastRefreshAt: Long = 0
        private set

    private val cacheFile = AtomicFile(File(context.noBackupFilesDir, "app_cache.json"))
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var cachedLabelLocales = ""

    suspend fun loadSnapshot() = withContext(Dispatchers.IO) {
        if (_apps.value != null) return@withContext
        val cache = try {
            json.decodeFromString<AppCache>(cacheFile.readFully().decodeToString())
        } catch (e: Exception) {
            if (e !is java.io.FileNotFoundException) Log.w(TAG, "Ignoring unreadable app cache", e)
            return@withContext
        }
        if (cache.version != CACHE_VERSION) return@withContext
        cachedLabelLocales = cache.labelLocales
        if (_apps.value == null) _apps.value = cache.apps
    }

    suspend fun refresh() = mutex.withLock {
        _refreshing.value = true
        try {
            val apps = withContext(Dispatchers.IO) { scan() }
            _apps.value = apps
            lastRefreshAt = System.currentTimeMillis()
            withContext(Dispatchers.IO) { writeSnapshot(apps) }
        } finally {
            _refreshing.value = false
        }
    }

    private suspend fun scan(): List<AppInfo> {
        val pm = context.packageManager
        val labelLocales = context.resources.configuration.locales.toLanguageTags()
        val previous = if (labelLocales == cachedLabelLocales) {
            _apps.value.orEmpty().associateBy { it.packageName }
        } else {
            emptyMap()
        }
        cachedLabelLocales = labelLocales

        val launchable = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            PackageManager.ResolveInfoFlags.of(0),
        ).mapTo(HashSet()) { it.activityInfo.packageName }

        val packages = pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
            .filter { it.applicationInfo != null && it.packageName != context.packageName }

        val labels = loadLabels(
            pm,
            packages.filter { previous[it.packageName]?.lastUpdateTime != it.lastUpdateTime },
        )
        return packages.map { info ->
            val label = labels[info.packageName] ?: previous[info.packageName]?.label ?: info.packageName
            info.toAppInfo(label, info.packageName in launchable)
        }
    }

    private suspend fun loadLabels(pm: PackageManager, packages: List<PackageInfo>): Map<String, String> =
        coroutineScope {
            packages.chunked(LABEL_CHUNK).map { chunk ->
                async(labelDispatcher) {
                    chunk.associate { info ->
                        info.packageName to (info.applicationInfo?.loadLabel(pm)?.toString() ?: info.packageName)
                    }
                }
            }.awaitAll().fold(HashMap()) { acc, part -> acc.apply { putAll(part) } }
        }

    private fun writeSnapshot(apps: List<AppInfo>) {
        val stream = cacheFile.startWrite()
        try {
            stream.write(json.encodeToString(AppCache(labelLocales = cachedLabelLocales, apps = apps)).encodeToByteArray())
            cacheFile.finishWrite(stream)
        } catch (e: Exception) {
            cacheFile.failWrite(stream)
            Log.w(TAG, "Could not write app cache", e)
        }
    }

    private fun PackageInfo.toAppInfo(label: String, launchable: Boolean): AppInfo {
        val app = applicationInfo!!
        return AppInfo(
            packageName = packageName,
            label = label,
            versionName = versionName,
            versionCode = longVersionCode,
            firstInstallTime = firstInstallTime,
            lastUpdateTime = lastUpdateTime,
            isSystem = app.flags and ApplicationInfo.FLAG_SYSTEM != 0,
            isUpdatedSystem = app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0,
            isLaunchable = launchable,
            isEnabled = app.enabled,
            targetSdk = app.targetSdkVersion,
            minSdk = app.minSdkVersion,
            uid = app.uid,
        )
    }

    private companion object {
        const val TAG = "AppRepository"
        const val LABEL_CHUNK = 24
        val labelDispatcher = Dispatchers.IO.limitedParallelism(4)
    }
}
