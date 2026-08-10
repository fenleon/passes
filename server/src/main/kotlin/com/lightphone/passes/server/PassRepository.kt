package com.lightphone.passes.server

import android.content.Context
import com.thelightphone.sdk.shared.lightJson
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/** A stored pass. List order is the display order. */
@Serializable
data class StoredPass(
    val id: String,
    val name: String,
    val data: String,
    /** Base64-encoded raw (binary) payload, when the code carries one. */
    val rawData: String? = null,
    val type: String,
)

/**
 * The pass list: an in-memory copy with write-through JSON in SharedPreferences
 * (list order = display order). All mutators are synchronized — the binder
 * dispatches on arbitrary threads.
 */
object PassRepository {

    private const val PREFS_NAME = "passes"
    private const val STORAGE_KEY = "passes_v1"

    private val passesSerializer = ListSerializer(StoredPass.serializer())

    private lateinit var appContext: Context
    private val mutablePasses = MutableStateFlow<List<StoredPass>>(emptyList())

    val passes: StateFlow<List<StoredPass>> = mutablePasses.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        val raw = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(STORAGE_KEY, null)
        mutablePasses.value = raw
            ?.let { runCatching { lightJson.decodeFromString(passesSerializer, it) }.getOrNull() }
            ?: emptyList()
    }

    fun add(name: String, data: String, rawData: String?, type: String): StoredPass {
        val pass = StoredPass(
            id = UUID.randomUUID().toString(),
            name = name,
            data = data,
            rawData = rawData?.takeIf { it.isNotBlank() },
            type = type,
        )
        synchronized(this) {
            mutablePasses.value = mutablePasses.value + pass
            persist()
        }
        return pass
    }

    fun rename(passId: String, name: String) {
        synchronized(this) {
            mutablePasses.value = mutablePasses.value.map { pass ->
                if (pass.id == passId) pass.copy(name = name) else pass
            }
            persist()
        }
    }

    fun delete(passId: String) {
        synchronized(this) {
            mutablePasses.value = mutablePasses.value.filterNot { it.id == passId }
            persist()
        }
    }

    fun get(passId: String): StoredPass? = mutablePasses.value.firstOrNull { it.id == passId }

    private fun persist() {
        val json = lightJson.encodeToString(passesSerializer, mutablePasses.value)
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(STORAGE_KEY, json)
            .commit()
    }
}
