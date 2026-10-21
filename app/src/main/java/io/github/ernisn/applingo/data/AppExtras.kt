package io.github.ernisn.applingo.data

import android.app.LocaleConfig
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import io.github.ernisn.applingo.core.LocaleTags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Details that are too slow to load for the whole list, fetched when an app is opened. */
data class AppExtras(
    val installer: String?,
    /** Locales the app declares in its LocaleConfig, or null when it declares none. */
    val declaredLocales: List<String>?,
)

suspend fun loadAppExtras(context: Context, packageName: String): AppExtras = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    val installer = runCatching { pm.getInstallSourceInfo(packageName).installingPackageName }.getOrNull()
        ?.let { installer ->
            runCatching {
                pm.getApplicationLabel(pm.getApplicationInfo(installer, PackageManager.ApplicationInfoFlags.of(0)))
                    .toString()
            }.getOrDefault(installer)
        }
    val declared = runCatching {
        val packageContext = context.createPackageContext(packageName, 0)
        val config = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            LocaleConfig.fromContextIgnoringOverride(packageContext)
        } else {
            LocaleConfig(packageContext)
        }
        if (config.status == LocaleConfig.STATUS_SUCCESS) {
            config.supportedLocales?.toLanguageTags()?.let(LocaleTags::parse)
        } else {
            null
        }
    }.getOrNull()
    AppExtras(installer, declared)
}
