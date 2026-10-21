package io.github.ernisn.applingo

import android.app.Application
import io.github.ernisn.applingo.background.BackgroundCheckWorker
import io.github.ernisn.applingo.data.ActivityLog
import io.github.ernisn.applingo.data.AppRepository
import io.github.ernisn.applingo.data.DesiredLocaleStore
import io.github.ernisn.applingo.data.LocaleController
import io.github.ernisn.applingo.data.UiPreferences
import io.github.ernisn.applingo.shizuku.ShizukuManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class ApplingoApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.start()
    }
}

/** Process-wide singletons, created once in [ApplingoApp.onCreate]. */
class AppContainer(private val app: Application) {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val shizuku = ShizukuManager(app, scope)
    val apps = AppRepository(app)
    val preferences = UiPreferences(app)
    val activityLog = ActivityLog(app)
    val locales = LocaleController(app, scope, shizuku, apps, DesiredLocaleStore(app), preferences, activityLog)

    fun start() {
        shizuku.start()
        locales.start()
        scope.launch {
            apps.loadSnapshot()
            refreshApps()
        }
        scope.launch {
            preferences.settings
                .map { it.backgroundCheck to it.backgroundCheckHours }
                .distinctUntilChanged()
                .collect { (enabled, hours) -> BackgroundCheckWorker.schedule(app, enabled, hours) }
        }
    }

    /** Rescans installed apps, then checks their locales again (new installs, updates). */
    suspend fun refreshApps() {
        apps.refresh()
        locales.requestReconcile()
    }
}
