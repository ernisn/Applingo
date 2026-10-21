package io.github.ernisn.applingo.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/** Applingo's own record of the locales the user chose for one app — the source of truth for repairs. */
@Serializable
data class DesiredLocale(
    val packageName: String,
    val userId: Int,
    /**
     * Canonical BCP 47 tags in priority order. Empty means a reset to the system language that
     * couldn't be written yet; the entry is dropped once the system has it.
     */
    val locales: List<String>,
    val updatedAt: Long,
)

@Serializable
data class DesiredLocales(val entries: List<DesiredLocale> = emptyList())

private val json = Json { ignoreUnknownKeys = true }

private object DesiredLocalesSerializer : Serializer<DesiredLocales> {
    override val defaultValue = DesiredLocales()

    override suspend fun readFrom(input: InputStream): DesiredLocales = try {
        json.decodeFromString(input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("Cannot read desired locales", e)
    }

    override suspend fun writeTo(t: DesiredLocales, output: OutputStream) {
        output.write(json.encodeToString(t).encodeToByteArray())
    }
}

private val Context.desiredLocalesStore: DataStore<DesiredLocales> by dataStore(
    fileName = "desired_locales.json",
    serializer = DesiredLocalesSerializer,
)

class DesiredLocaleStore(context: Context) {

    private val store = context.desiredLocalesStore

    /** Desired locales of [userId], keyed by package name. */
    fun observe(userId: Int): Flow<Map<String, DesiredLocale>> =
        store.data.map { data -> data.entries.filter { it.userId == userId }.associateBy { it.packageName } }

    suspend fun current(userId: Int): Map<String, DesiredLocale> = observe(userId).first()

    /**
     * Records [locales] for the package. An empty list forgets the package, unless [pendingReset]
     * keeps it as a marker that the system still has to be reset.
     */
    suspend fun set(userId: Int, packageName: String, locales: List<String>, pendingReset: Boolean = false) {
        store.updateData { data ->
            val others = data.entries.filterNot { it.userId == userId && it.packageName == packageName }
            if (locales.isEmpty() && !pendingReset) {
                data.copy(entries = others)
            } else {
                data.copy(entries = others + DesiredLocale(packageName, userId, locales, System.currentTimeMillis()))
            }
        }
    }
}
