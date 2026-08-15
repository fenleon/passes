package com.lightphone.passes.server

import android.content.Context
import com.thelightphone.sdk.shared.lightJson
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/** A stored code — one barcode of a pass (a pass stacks several under one name). */
@Serializable
data class StoredCode(
    val id: String,
    val data: String,
    /** Base64-encoded raw (binary) payload, when the code carries one. */
    val rawData: String? = null,
    val type: String,
    /** True when the code was typed manually — only typed codes show their
     *  text under the barcode (scanned payloads are noise). */
    val typed: Boolean = false,
)

/**
 * A stored pass: a name, its stacked codes, and the details shared by all of
 * them (issuer, date, end date, times, location, notes). List order is the
 * display order (alphabetical by name).
 */
@Serializable
data class StoredPass(
    val id: String,
    val name: String,
    val codes: List<StoredCode>,
    /** Optional detail fields — stored trimmed, empty ones as null. */
    val issuer: String? = null,
    val date: String? = null,
    val endDate: String? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val location: String? = null,
    val notes: String? = null,
)

/**
 * The pass list: an in-memory copy with write-through JSON in SharedPreferences
 * (list order = display order). All mutators are synchronized — the binder
 * dispatches on arbitrary threads. v1 stored flat passes (one code each);
 * v2 nests codes under a pass so stacked passes share their details.
 */
object PassRepository {

    private const val PREFS_NAME = "passes"
    private const val STORAGE_KEY = "passes_v2"
    private const val LEGACY_STORAGE_KEY = "passes_v1"

    private val passesSerializer = ListSerializer(StoredPass.serializer())

    private lateinit var appContext: Context
    private val mutablePasses = MutableStateFlow<List<StoredPass>>(emptyList())

    val passes: StateFlow<List<StoredPass>> = mutablePasses.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(STORAGE_KEY, null)
            ?.let { runCatching { lightJson.decodeFromString(passesSerializer, it) }.getOrNull() }
        if (stored != null) {
            mutablePasses.value = stored.alphabetical()
        } else {
            // First run on v2: migrate the flat v1 list (passes sharing a name
            // become one pass with stacked codes; details come from the first)
            // and persist it so the migration is one-time.
            val migrated = migrateFromV1(prefs.getString(LEGACY_STORAGE_KEY, null))
            if (migrated != null) {
                mutablePasses.value = migrated
                persist()
            }
        }
    }

    /** Creates a new pass (name + first code). */
    fun add(
        name: String,
        data: String,
        rawData: String?,
        type: String,
        typed: Boolean,
    ): StoredPass {
        val pass = StoredPass(
            id = UUID.randomUUID().toString(),
            name = name,
            codes = listOf(code(data, rawData, type, typed)),
        )
        synchronized(this) {
            mutablePasses.value = (mutablePasses.value + pass).alphabetical()
            persist()
        }
        return pass
    }

    /** Stacks another code under an existing pass (the barcode panel's "+"). */
    fun addCode(passId: String, data: String, rawData: String?, type: String, typed: Boolean) {
        synchronized(this) {
            mutablePasses.value = mutablePasses.value.map { pass ->
                if (pass.id == passId) {
                    pass.copy(codes = pass.codes + code(data, rawData, type, typed))
                } else {
                    pass
                }
            }
            persist()
        }
    }

    /** Updates a pass's name and shared details (applies to all its codes). */
    fun update(
        passId: String,
        name: String,
        issuer: String?,
        date: String?,
        endDate: String?,
        startTime: String?,
        endTime: String?,
        location: String?,
        notes: String?,
    ) {
        synchronized(this) {
            mutablePasses.value = mutablePasses.value.map { pass ->
                if (pass.id == passId) {
                    pass.copy(
                        name = name.trim(),
                        issuer = issuer?.trim()?.takeIf { it.isNotEmpty() },
                        date = date?.trim()?.takeIf { it.isNotEmpty() },
                        endDate = endDate?.trim()?.takeIf { it.isNotEmpty() },
                        startTime = startTime?.trim()?.takeIf { it.isNotEmpty() },
                        endTime = endTime?.trim()?.takeIf { it.isNotEmpty() },
                        location = location?.trim()?.takeIf { it.isNotEmpty() },
                        notes = notes?.trim()?.takeIf { it.isNotEmpty() },
                    )
                } else {
                    pass
                }
            }.alphabetical()
            persist()
        }
    }

    /** Deletes one stacked code from its pass; deleting the last code removes
     *  the whole pass. */
    fun deleteCode(codeId: String) {
        synchronized(this) {
            mutablePasses.value = mutablePasses.value.mapNotNull { pass ->
                val remaining = pass.codes.filterNot { it.id == codeId }
                when {
                    remaining.size == pass.codes.size -> pass // not this pass
                    remaining.isEmpty() -> null // last code — the pass is gone
                    else -> pass.copy(codes = remaining)
                }
            }
            persist()
        }
    }

    fun get(passId: String): StoredPass? = mutablePasses.value.firstOrNull { it.id == passId }

    /** Finds a stacked code by its own id (GetBarcode renders one code at a time). */
    fun codeFor(codeId: String): StoredCode? =
        mutablePasses.value.asSequence()
            .flatMap { it.codes.asSequence() }
            .firstOrNull { it.id == codeId }

    private fun code(data: String, rawData: String?, type: String, typed: Boolean) = StoredCode(
        id = UUID.randomUUID().toString(),
        data = data,
        rawData = rawData?.takeIf { it.isNotBlank() },
        type = type,
        typed = typed,
    )

    /** Passes are stored alphabetically by name (case-insensitive); the sort is
     *  stable, so stacked passes keep their insertion order. */
    private fun List<StoredPass>.alphabetical(): List<StoredPass> =
        sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

    /** The v1 flat record (one code per pass) — kept for the one-time migration. */
    @Serializable
    private data class LegacyPass(
        val id: String,
        val name: String,
        val data: String,
        val rawData: String? = null,
        val type: String,
        val typed: Boolean = false,
        val issuer: String? = null,
        val date: String? = null,
        val endDate: String? = null,
        val startTime: String? = null,
        val endTime: String? = null,
        val location: String? = null,
        val notes: String? = null,
    )

    private fun migrateFromV1(raw: String?): List<StoredPass>? {
        if (raw == null) return null
        val flat = runCatching {
            lightJson.decodeFromString(ListSerializer(LegacyPass.serializer()), raw)
        }.getOrNull() ?: return null
        return flat.groupBy { it.name }.map { (name, members) ->
            val first = members.first()
            StoredPass(
                id = first.id,
                name = name,
                codes = members.map {
                    StoredCode(
                        id = it.id,
                        data = it.data,
                        rawData = it.rawData,
                        type = it.type,
                        typed = it.typed,
                    )
                },
                issuer = first.issuer,
                date = first.date,
                endDate = first.endDate,
                startTime = first.startTime,
                endTime = first.endTime,
                location = first.location,
                notes = first.notes,
            )
        }
    }

    private fun persist() {
        val json = lightJson.encodeToString(passesSerializer, mutablePasses.value)
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(STORAGE_KEY, json)
            .commit()
    }
}
