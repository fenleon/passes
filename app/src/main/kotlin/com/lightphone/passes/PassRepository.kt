package com.lightphone.passes

import com.thelightphone.sdk.shared.lightJson
import java.io.File
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
 * The pass list: an in-process copy with write-through JSON in a file under the
 * tool's sandboxed filesDir (single-module experiment 2026-08-18 — the old
 * companion's SharedPreferences store lived in a separate package and is
 * unreachable from here, so storage starts fresh; the v1 flat-list migration is
 * gone with it). List order = display order. All mutators are synchronized —
 * the UI may call from any thread.
 */
object PassRepository {

    private const val STORAGE_FILE = "passes.json"

    /** The largest a pass's stack can grow (feedback 2026-08-24: a cap keeps
     *  the delete list and swipe stack sane). Refuses to add past it. */
    const val MAX_STACK_SIZE = 10

    private val passesSerializer = ListSerializer(StoredPass.serializer())

    private var storageFile: File? = null
    private val mutablePasses = MutableStateFlow<List<StoredPass>>(emptyList())

    val passes: StateFlow<List<StoredPass>> = mutablePasses.asStateFlow()

    /** Idempotent — call once from the entry screen before first use. */
    fun init(filesDir: File) {
        if (storageFile != null) return
        storageFile = File(filesDir, STORAGE_FILE)
        val stored = storageFile
            ?.takeIf { it.isFile }
            ?.readText()
            ?.let { runCatching { lightJson.decodeFromString(passesSerializer, it) }.getOrNull() }
        if (stored != null) {
            mutablePasses.value = stored.alphabetical()
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
                    if (pass.codes.size >= MAX_STACK_SIZE) {
                        pass // stack is full — refuse to grow it
                    } else {
                        pass.copy(codes = pass.codes + code(data, rawData, type, typed))
                    }
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

    private fun persist() {
        val file = storageFile ?: return
        val json = lightJson.encodeToString(passesSerializer, mutablePasses.value)
        // Write-then-rename so a crash mid-write can't corrupt the store.
        val tmp = File(file.parentFile, "$STORAGE_FILE.tmp")
        tmp.writeText(json)
        tmp.renameTo(file)
    }
}
