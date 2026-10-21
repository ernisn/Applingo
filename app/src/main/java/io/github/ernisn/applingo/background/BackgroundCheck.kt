package io.github.ernisn.applingo.background

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.ernisn.applingo.ApplingoApp
import io.github.ernisn.applingo.shizuku.ServiceUnavailableException
import java.util.concurrent.TimeUnit

/**
 * Periodically rescans apps and checks their locales while Applingo isn't open, so a choice the
 * system dropped (e.g. on an app update) is restored without the user having to open the app.
 * Needs Shizuku to be running at that moment; otherwise the run is skipped until next time.
 */
class BackgroundCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as ApplingoApp).container
        try {
            container.shizuku.awaitService(SERVICE_WAIT_MS)
        } catch (e: ServiceUnavailableException) {
            Log.i(TAG, "Shizuku not available, skipping this check")
            return Result.success()
        }
        container.apps.loadSnapshot()
        container.apps.refresh()
        val summary = container.locales.reconcileNow(announce = false)
        Log.i(TAG, "Background check done: $summary")
        return Result.success()
    }

    companion object {
        private const val TAG = "BackgroundCheck"
        private const val WORK_NAME = "applingo-background-check"
        private const val SERVICE_WAIT_MS = 20_000L

        fun schedule(context: Context, enabled: Boolean, hours: Int) {
            val workManager = WorkManager.getInstance(context)
            if (!enabled) {
                workManager.cancelUniqueWork(WORK_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<BackgroundCheckWorker>(hours.toLong(), TimeUnit.HOURS).build()
            workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
