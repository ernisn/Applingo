package io.github.ernisn.applingo.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.github.ernisn.applingo.ApplingoApp
import io.github.ernisn.applingo.shizuku.ServiceUnavailableException
import java.util.concurrent.TimeUnit

/**
 * Rescans apps and checks their locales while Applingo isn't open, so a choice the system dropped
 * (e.g. on an app update) is restored without the user having to open the app. Runs periodically,
 * and once ([checkNow]) after boot and whenever Shizuku comes up.
 *
 * The one-off check has to run as a job: when Shizuku starts it wakes Applingo only for a moment, and
 * a process without a running component is frozen within seconds (Samsung does it after ~3 s), which
 * left the check half done until Applingo was next opened.
 */
class BackgroundCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as ApplingoApp).container
        val oneOff = inputData.getBoolean(KEY_ONE_OFF, false)
        // An open screen checks by itself when the service connects, and reports what it restored.
        if (oneOff && container.isUiStarted) return Result.success()
        try {
            container.shizuku.awaitService(if (oneOff) ONE_OFF_SERVICE_WAIT_MS else SERVICE_WAIT_MS)
        } catch (e: ServiceUnavailableException) {
            Log.i(TAG, "Shizuku not available, skipping this check")
            return Result.success()
        }
        container.apps.loadSnapshot()
        // Records are checked even if the snapshot is stale; the periodic run also picks up new installs.
        if (!oneOff) container.apps.refresh()
        val summary = container.locales.reconcileNow(announce = false)
        Log.i(TAG, "${if (oneOff) "One-off" else "Background"} check done: $summary")
        return Result.success()
    }

    companion object {
        private const val TAG = "BackgroundCheck"
        private const val WORK_NAME = "applingo-background-check"
        private const val ONE_OFF_WORK_NAME = "applingo-check-now"
        private const val KEY_ONE_OFF = "one_off"
        private const val SERVICE_WAIT_MS = 20_000L
        // Binding the service can take a while right after boot.
        private const val ONE_OFF_SERVICE_WAIT_MS = 60_000L

        fun schedule(context: Context, enabled: Boolean, hours: Int) {
            val workManager = WorkManager.getInstance(context)
            if (!enabled) {
                workManager.cancelUniqueWork(WORK_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<BackgroundCheckWorker>(hours.toLong(), TimeUnit.HOURS).build()
            workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        /** Checks once, as soon as possible; a newer request replaces one still waiting for Shizuku. */
        fun checkNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<BackgroundCheckWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setInputData(workDataOf(KEY_ONE_OFF to true))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(ONE_OFF_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}

/**
 * Checks once after boot. Covers setups where Shizuku never wakes Applingo then: Sui, or Shizuku started
 * before the first unlock, when Applingo's provider can't start yet. A later Shizuku start wakes it anyway.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) BackgroundCheckWorker.checkNow(context)
    }
}
