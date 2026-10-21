package io.github.ernisn.applingo.service

import android.os.LocaleList
import android.os.Process
import androidx.annotation.Keep
import kotlin.system.exitProcess

/**
 * Runs in a separate process started by Shizuku with shell or root identity, where hidden framework
 * interfaces can be called directly. Shizuku instantiates it reflectively, so the constructor is kept.
 */
@Keep
class ApplingoUserService : IApplingoService.Stub() {

    private val localeManager by lazy { HiddenLocaleManager() }

    override fun destroy() {
        exitProcess(0)
    }

    override fun getUid(): Int = Process.myUid()

    override fun getApplicationLocales(packageNames: Array<String>, userId: Int): Array<String?> = guard {
        Array(packageNames.size) { i ->
            try {
                localeManager.getApplicationLocales(packageNames[i], userId).toLanguageTags()
            } catch (e: Exception) {
                null
            }
        }
    }

    override fun setApplicationLocales(packageName: String, userId: Int, languageTags: String) = guard {
        localeManager.setApplicationLocales(packageName, userId, LocaleList.forLanguageTags(languageTags))
    }

    override fun forceStopPackage(packageName: String, userId: Int) = guard {
        val process = ProcessBuilder("am", "force-stop", "--user", userId.toString(), packageName)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText().trim()
        check(process.waitFor() == 0) { output.ifEmpty { "am force-stop failed" } }
    }

    // Only a few exception types can cross the binder boundary; anything else would kill this process.
    private inline fun <T> guard(block: () -> T): T = try {
        block()
    } catch (e: Throwable) {
        throw when (e) {
            is SecurityException,
            is IllegalArgumentException,
            is IllegalStateException,
            is NullPointerException,
            is UnsupportedOperationException -> e
            else -> IllegalStateException(e.toString(), e)
        }
    }
}
