package io.github.ernisn.applingo.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

enum class ActivityKind {
    /** The user chose locales in Applingo. */
    Applied,
    /** The user reset an app to the system language in Applingo. */
    Reset,
    /** The system lost or trimmed a choice and Applingo wrote it back. */
    Repaired,
    /** The system differs from the record but automatic repair is off. */
    Mismatch,
    /** Locales changed outside Applingo were taken over as the new record. */
    Adopted,
    /** Choices loaded from an exported file. */
    Imported,
    /** Writing to the system failed. */
    Failed,
}

@Serializable
data class ActivityEntry(
    val time: Long,
    val kind: ActivityKind,
    val packageName: String,
    val label: String,
    val from: List<String> = emptyList(),
    val to: List<String> = emptyList(),
    val message: String? = null,
)

@Serializable
private data class ActivityEntries(val entries: List<ActivityEntry> = emptyList())

private val json = Json { ignoreUnknownKeys = true }

private object ActivitySerializer : Serializer<ActivityEntries> {
    override val defaultValue = ActivityEntries()

    override suspend fun readFrom(input: InputStream): ActivityEntries = try {
        json.decodeFromString(input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("Cannot read activity log", e)
    }

    override suspend fun writeTo(t: ActivityEntries, output: OutputStream) {
        output.write(json.encodeToString(t).encodeToByteArray())
    }
}

private val Context.activityStore: DataStore<ActivityEntries> by dataStore(
    fileName = "activity_log.json",
    serializer = ActivitySerializer,
)

/** What happened to which app's locales, newest first, so lost settings can be explained later. */
class ActivityLog(context: Context) {

    private val store = context.activityStore

    val entries: Flow<List<ActivityEntry>> = store.data.map { it.entries }

    suspend fun add(entries: List<ActivityEntry>) {
        if (entries.isEmpty()) return
        store.updateData { data ->
            ActivityEntries((entries.sortedByDescending { it.time } + data.entries).take(MAX_ENTRIES))
        }
    }

    suspend fun add(entry: ActivityEntry) = add(listOf(entry))

    suspend fun clear() {
        store.updateData { ActivityEntries() }
    }

    private companion object {
        const val MAX_ENTRIES = 300
    }
}
