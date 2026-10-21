package io.github.ernisn.applingo.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import io.github.ernisn.applingo.BuildConfig
import io.github.ernisn.applingo.service.ApplingoUserService
import io.github.ernisn.applingo.service.IApplingoService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

sealed interface ShizukuState {
    /** Waiting for Shizuku to hand over its binder right after start-up. */
    data object Checking : ShizukuState
    data object NotInstalled : ShizukuState
    data object NotRunning : ShizukuState
    data object Unsupported : ShizukuState
    data object PermissionRequired : ShizukuState
    /** The user chose "deny and don't ask again"; only the Shizuku app can grant it now. */
    data object PermissionDenied : ShizukuState
    data object Connecting : ShizukuState
    data class Connected(val uid: Int) : ShizukuState
    data class Failed(val message: String) : ShizukuState
}

class ServiceUnavailableException : IllegalStateException("Shizuku service is not connected")

/**
 * Tracks Shizuku (or Sui) and keeps Applingo's user service bound. Survives Shizuku restarts: when
 * its binder comes back, permission is re-checked and the service is bound again automatically.
 */
class ShizukuManager(private val context: Context, private val scope: CoroutineScope) {

    private val _state = MutableStateFlow<ShizukuState>(ShizukuState.Checking)
    val state: StateFlow<ShizukuState> = _state.asStateFlow()

    private val _service = MutableStateFlow<IApplingoService?>(null)
    val service: StateFlow<IApplingoService?> = _service.asStateFlow()

    private var timeoutJob: Job? = null
    private var recentRebinds = 0

    private val userServiceArgs by lazy {
        Shizuku.UserServiceArgs(ComponentName(context.packageName, ApplingoUserService::class.java.name))
            .tag(SERVICE_TAG)
            .processNameSuffix("service")
            .daemon(false)
            .debuggable(BuildConfig.DEBUG)
            .version(serviceVersion())
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            timeoutJob?.cancel()
            if (binder == null || !binder.pingBinder()) {
                _state.value = ShizukuState.Failed("Service binder is not alive")
                return
            }
            val service = IApplingoService.Stub.asInterface(binder)
            val uid = try {
                service.uid
            } catch (e: Exception) {
                _state.value = ShizukuState.Failed(e.message ?: e.toString())
                return
            }
            _service.value = service
            _state.value = ShizukuState.Connected(uid)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            _service.value = null
            // The service process died while Shizuku is still up: start it again, but don't loop.
            if (Shizuku.pingBinder() && recentRebinds++ < MAX_REBINDS) {
                bind()
            } else {
                _state.value = notRunningState()
            }
        }
    }

    private val binderReceived = Shizuku.OnBinderReceivedListener { onBinderAvailable() }

    private val binderDead = Shizuku.OnBinderDeadListener {
        timeoutJob?.cancel()
        _service.value = null
        _state.value = notRunningState()
    }

    private val permissionResult = Shizuku.OnRequestPermissionResultListener { _, result ->
        if (result == PackageManager.PERMISSION_GRANTED) bind() else _state.value = permissionState()
    }

    fun start() {
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)
        scope.launch {
            delay(BINDER_GRACE_MS)
            if (_state.value == ShizukuState.Checking) _state.value = notRunningState()
        }
    }

    fun requestPermission() {
        if (Shizuku.pingBinder() && !Shizuku.shouldShowRequestPermissionRationale()) {
            Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
        }
    }

    fun retry() {
        recentRebinds = 0
        if (Shizuku.pingBinder()) onBinderAvailable() else _state.value = notRunningState()
    }

    /** Intent that opens the Shizuku app, if installed. */
    fun managerLaunchIntent(): Intent? =
        context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Waits for the user service, or throws [ServiceUnavailableException] after [timeoutMs]. */
    suspend fun awaitService(timeoutMs: Long = 10_000): IApplingoService =
        _service.value
            ?: withTimeoutOrNull(timeoutMs) { _service.filterNotNull().first() }
            ?: throw ServiceUnavailableException()

    private fun onBinderAvailable() {
        _state.value = when {
            Shizuku.isPreV11() -> ShizukuState.Unsupported
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> {
                bind()
                return
            }
            else -> permissionState()
        }
    }

    private fun bind() {
        _state.value = ShizukuState.Connecting
        try {
            Shizuku.bindUserService(userServiceArgs, connection)
        } catch (e: Exception) {
            Log.w(TAG, "bindUserService failed", e)
            _state.value = ShizukuState.Failed(e.message ?: e.toString())
            return
        }
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(BIND_TIMEOUT_MS)
            if (_state.value == ShizukuState.Connecting) {
                _state.value = ShizukuState.Failed("Timed out starting the Shizuku service")
            }
        }
        scope.launch {
            delay(REBIND_WINDOW_MS)
            recentRebinds = 0
        }
    }

    private fun permissionState(): ShizukuState =
        if (Shizuku.shouldShowRequestPermissionRationale()) ShizukuState.PermissionDenied
        else ShizukuState.PermissionRequired

    private fun notRunningState(): ShizukuState = try {
        context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        ShizukuState.NotRunning
    } catch (e: PackageManager.NameNotFoundException) {
        ShizukuState.NotInstalled
    }

    /** Changes on every install, so Shizuku restarts the service whenever its code may have changed. */
    private fun serviceVersion(): Int =
        (context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime / 1000).toInt()

    private companion object {
        const val TAG = "ShizukuManager"
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        const val SERVICE_TAG = "applingo"
        const val PERMISSION_REQUEST_CODE = 1
        const val BINDER_GRACE_MS = 1_500L
        const val BIND_TIMEOUT_MS = 15_000L
        const val REBIND_WINDOW_MS = 60_000L
        const val MAX_REBINDS = 3
    }
}
