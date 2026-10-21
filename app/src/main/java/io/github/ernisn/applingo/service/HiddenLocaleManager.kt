package io.github.ernisn.applingo.service

import android.annotation.SuppressLint
import android.os.IBinder
import android.os.LocaleList
import rikka.shizuku.SystemServiceHelper
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * Reflective access to the hidden `android.app.ILocaleManager` binder interface. Only usable inside
 * the Shizuku user service process, which has no hidden-API restrictions and holds CHANGE_CONFIGURATION.
 *
 * Signatures (AOSP frameworks/base core/java/android/app/ILocaleManager.aidl):
 * - API 33:  setApplicationLocales(String packageName, int userId, LocaleList locales)
 * - API 34+: setApplicationLocales(String packageName, int userId, LocaleList locales, boolean fromDelegate)
 * - all:     getApplicationLocales(String packageName, int userId)
 */
@SuppressLint("PrivateApi")
internal class HiddenLocaleManager {

    private val service: Any
    private val getLocales: Method
    private val setLocales: Method

    init {
        val binder = SystemServiceHelper.getSystemService("locale")
            ?: throw IllegalStateException("System service 'locale' not found")
        service = Class.forName("android.app.ILocaleManager\$Stub")
            .getMethod("asInterface", IBinder::class.java)
            .invoke(null, binder)
            ?: throw IllegalStateException("ILocaleManager.Stub.asInterface returned null")
        val iface = Class.forName("android.app.ILocaleManager")
        getLocales = iface.getMethod("getApplicationLocales", String::class.java, Integer.TYPE)
        setLocales = try {
            iface.getMethod(
                "setApplicationLocales",
                String::class.java, Integer.TYPE, LocaleList::class.java, java.lang.Boolean.TYPE,
            )
        } catch (e: NoSuchMethodException) {
            iface.getMethod("setApplicationLocales", String::class.java, Integer.TYPE, LocaleList::class.java)
        }
    }

    fun getApplicationLocales(packageName: String, userId: Int): LocaleList =
        call(getLocales, packageName, userId) as LocaleList

    fun setApplicationLocales(packageName: String, userId: Int, locales: LocaleList) {
        if (setLocales.parameterCount == 4) {
            // fromDelegate=true would put the package on the system's "set by delegate" list, and the
            // system then strips locales the app doesn't declare on every app update ("Missing from the
            // LocaleConfig, reset app locales" in the log).
            call(setLocales, packageName, userId, locales, false)
        } else {
            call(setLocales, packageName, userId, locales)
        }
    }

    private fun call(method: Method, vararg args: Any): Any? = try {
        method.invoke(service, *args)
    } catch (e: InvocationTargetException) {
        throw e.targetException
    }
}
