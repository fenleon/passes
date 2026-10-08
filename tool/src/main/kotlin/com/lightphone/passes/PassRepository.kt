package com.lightphone.passes

import com.thelightphone.sdk.shared.lightJson
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/** The stored date format — what the picker saves ("Aug 12, 2026"). */
internal val DISPLAY_DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

/** ISO fallback (dates that were typed in by hand). */
internal val ISO_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)

/** Parses a stored date value: our display format, or ISO. */
internal fun parseStoredDate(value: String): LocalDate? {
    if (value.isBlank()) return null
    return runCatching { LocalDate.parse(value, DISPLAY_DATE) }
        .recoverCatching { LocalDate.parse(value, ISO_DATE) }
        .getOrNull()
}

/** Formats a date for storage and display. */
internal fun formatStoredDate(date: LocalDate): String = DISPLAY_DATE.format(date)

/**
 * The exact scanned symbol as a module grid (captured by the camera scanner):
 * width, height, and one byte per module (0 = black). When present it is
 * rendered directly instead of re-encoding the payload — the original symbol
 * is reproduced byte for byte (e.g. an airline Aztec whose re-encode differs).
 */
@Serializable
data class StoredSymbol(
    val width: Int,
    val height: Int,
    val data: ByteArray,
)

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
    /** The exact scanned symbol grid, when the code was captured by camera. */
    val symbol: StoredSymbol? = null,
)

/**
 * A stored pass: a name, its stacked codes, and the details shared by all of
 * them (issuer, date, end date, times, location, notes). Storage order is
 * alphabetical by name; the Home list uses [PassRepository.displayOrder].
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
 * gone with it). Storage order = alphabetical; the Home list reads
 * [displayOrder]. All mutators are synchronized — the UI may call from any
 * thread.
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

    /** Upcoming (start today or later; or a still-current pass whose end date
     *  is today or later) — ranked above no-date and past passes. */
    private const val BUCKET_UPCOMING = 0

    /** No date at all — sits between the upcoming and past groups. */
    private const val BUCKET_NONE = 1

    /** Past: start before today, and an end date before today when one is set. */
    private const val BUCKET_PAST = 2

    /** The list in display order (the Home rows). Upcoming passes rank first —
     *  today's date before future ones, then the no-date passes, then the past
     *  ones. Within a date the secondary order is alphabetical (case-
     *  insensitive); on the same start date a pass without an end date outranks
     *  one with (feedback 2026-08-30). */
    fun displayOrder(): List<StoredPass> {
        val today = LocalDate.now()
        return mutablePasses.value.sortedWith { a, b ->
            val bucketA = bucket(a, today)
            val bucketB = bucket(b, today)
            when {
                bucketA != bucketB -> bucketA - bucketB
                bucketA == BUCKET_UPCOMING -> {
                    val byDate = rankDate(a, today).compareTo(rankDate(b, today))
                    if (byDate != 0) {
                        byDate
                    } else {
                        val byEnd = b.endDate.isNullOrBlank()
                            .compareTo(a.endDate.isNullOrBlank())
                        if (byEnd != 0) byEnd
                        else a.name.compareTo(b.name, ignoreCase = true)
                    }
                }
                else -> a.name.compareTo(b.name, ignoreCase = true)
            }
        }
    }

    /** Which display group a pass belongs to. */
    private fun bucket(pass: StoredPass, today: LocalDate): Int {
        val start = parseStoredDate(pass.date ?: "") ?: return BUCKET_NONE
        return when {
            start >= today -> BUCKET_UPCOMING
            // Still current (started before today, ends today or later) — it is
            // usable today, so it ranks with the upcoming group.
            pass.endDate?.let { parseStoredDate(it) }?.let { it >= today } == true ->
                BUCKET_UPCOMING
            else -> BUCKET_PAST
        }
    }

    /** The sort date inside the upcoming group: the start date, except a
     *  still-current pass ranks with today's passes. */
    private fun rankDate(pass: StoredPass, today: LocalDate): LocalDate {
        val start = parseStoredDate(pass.date ?: "") ?: return today
        return if (start >= today) start else today
    }

    /** Idempotent — call once from the entry screen before first use. */
    fun init(filesDir: File) {
        if (storageFile != null) return
        storageFile = File(filesDir, STORAGE_FILE)
        val stored = storageFile
            ?.takeIf { it.isFile }
            ?.readText()
            ?.let {
                runCatching { lightJson.decodeFromString(passesSerializer, it) }.getOrNull()
            }
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
        symbol: StoredSymbol? = null,
    ): StoredPass {
        val pass = StoredPass(
            id = UUID.randomUUID().toString(),
            name = name,
            codes = listOf(code(data, rawData, type, typed, symbol)),
        )
        synchronized(this) {
            mutablePasses.value = (mutablePasses.value + pass).alphabetical()
            persist()
        }
        return pass
    }

    /** Stacks another code under an existing pass (the code fullscreen's "+"). */
    fun addCode(
        passId: String,
        data: String,
        rawData: String?,
        type: String,
        typed: Boolean,
        symbol: StoredSymbol? = null,
    ) {
        synchronized(this) {
            mutablePasses.value = mutablePasses.value.map { pass ->
                if (pass.id == passId) {
                    if (pass.codes.size >= MAX_STACK_SIZE) {
                        pass // stack is full — refuse to grow it
                    } else {
                        pass.copy(codes = pass.codes + code(data, rawData, type, typed, symbol))
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

    private fun code(
        data: String,
        rawData: String?,
        type: String,
        typed: Boolean,
        symbol: StoredSymbol? = null,
    ) = StoredCode(
        id = UUID.randomUUID().toString(),
        data = data,
        rawData = rawData?.takeIf { it.isNotBlank() },
        type = type,
        typed = typed,
        symbol = symbol,
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
