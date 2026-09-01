package com.lightphone.passes

import com.thelightphone.sdk.shared.LightServiceMethod

/** A pass as stored: a name, shared details, and stacked codes. */
typealias Pass = LightServiceMethod.GetPasses.Pass

/** One barcode of a pass. */
typealias Code = LightServiceMethod.GetPasses.Code

/**
 * The single-module (2026-08-18) data facade: storage + barcode rendering now
 * live in-process (PassRepository + BarcodeRenderer), replacing the old
 * companion RPC. The public API is unchanged so the screens didn't move; the
 * methods are still suspend to match their call sites.
 */
object PassesClient {

    suspend fun getPasses(): List<Pass> =
        PassRepository.displayOrder().map { pass ->
            Pass(
                id = pass.id,
                name = pass.name,
                codes = pass.codes.map { code ->
                    Code(
                        id = code.id,
                        data = code.data,
                        rawData = code.rawData,
                        type = code.type,
                        typed = code.typed,
                    )
                },
                issuer = pass.issuer,
                date = pass.date,
                endDate = pass.endDate,
                startTime = pass.startTime,
                endTime = pass.endTime,
                location = pass.location,
                notes = pass.notes,
            )
        }

    /** Creates a new pass with its first code. */
    suspend fun addPass(
        name: String,
        data: String,
        rawData: String?,
        type: String,
        typed: Boolean = false,
        symbol: StoredSymbol? = null,
    ): Boolean =
        PassRepository.add(name, data, rawData, type, typed, symbol) != null

    /** Stacks another code under an existing pass (the code fullscreen's "+"). */
    suspend fun addCode(
        passId: String,
        data: String,
        rawData: String?,
        type: String,
        typed: Boolean = false,
        symbol: StoredSymbol? = null,
    ): Boolean {
        PassRepository.addCode(passId, data, rawData, type, typed, symbol)
        return true
    }

    suspend fun updatePass(
        passId: String,
        name: String,
        issuer: String? = null,
        date: String? = null,
        endDate: String? = null,
        startTime: String? = null,
        endTime: String? = null,
        location: String? = null,
        notes: String? = null,
    ): Boolean {
        PassRepository.update(passId, name, issuer, date, endDate, startTime, endTime, location, notes)
        return true
    }

    /** Deletes one stacked code from its pass (the last code removes the pass). */
    suspend fun deleteCode(codeId: String) {
        PassRepository.deleteCode(codeId)
    }

    /** A code's barcode as PNG bytes (rendered in-process at [width] px). */
    suspend fun barcodePng(codeId: String, width: Int = 960): ByteArray? {
        val code = PassRepository.codeFor(codeId) ?: return null
        return BarcodeRenderer.renderPng(code.type, code.data, code.rawData, width, code.symbol)
    }
}
