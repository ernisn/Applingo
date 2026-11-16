package io.github.ernisn.applingo

import android.app.Activity
import android.app.Application
import android.os.Bundle
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class ApplingoApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        registerActivityLifecycleCallbacks(container.startedActivities)
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

    internal val startedActivities = StartedActivityCounter()

    /** Whether an Applingo screen is visible; false while running for Shizuku, a job or boot. */
    val isUiStarted: Boolean get() = startedActivities.count > 0

    fun start() {
        scope.launch {
            // Shizuku starting wakes this process only briefly; without a screen open, hand the check to a
            // job right away, before the process is frozen (see BackgroundCheckWorker).
            shizuku.binderReceived.collect { if (!isUiStarted) BackgroundCheckWorker.checkNow(app) }
        }
        shizuku.start()
        locales.start()
        scope.launch {
            apps.loadSnapshot()
            // The snapshot is shown right away; rescanning on every launch is opt-in (it slows startup),
            // except on first run when there is nothing to show yet.
            if (apps.apps.value == null || preferences.settings.first().refreshOnLaunch) {
                refreshApps()
            } else {
                locales.requestReconcile()
            }
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

/** Counts started activities; callbacks arrive on the main thread, [count] may be read from any. */
internal class StartedActivityCounter : Application.ActivityLifecycleCallbacks {
    @Volatile
    var count = 0
        private set

    override fun onActivityStarted(activity: Activity) {
        count++
    }

    override fun onActivityStopped(activity: Activity) {
        count--
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
