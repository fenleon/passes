package com.lightphone.passes

import com.thelightphone.sdk.callRemoteServiceMethod
import com.thelightphone.sdk.shared.LightResult
import com.thelightphone.sdk.shared.LightServiceMethod
import com.thelightphone.sdk.shared.getOrNull

/** A pass as served by the companion: a name, shared details, and stacked codes. */
typealias Pass = LightServiceMethod.GetPasses.Pass

/** One barcode of a pass. */
typealias Code = LightServiceMethod.GetPasses.Code

/**
 * Thin RPC client for the Passes methods. Everything privileged (storage,
 * barcode rendering, camera, URL opening) lives in the companion (:server);
 * the tool only renders state fetched over the SDK binder.
 */
object PassesClient {

    suspend fun getPasses(): List<Pass> =
        callRemoteServiceMethod(LightServiceMethod.GetPasses, Unit)
            .getOrNull()?.passes.orEmpty()

    /** Creates a new pass with its first code. */
    suspend fun addPass(name: String, data: String, rawData: String?, type: String, typed: Boolean = false): Boolean =
        callRemoteServiceMethod(
            LightServiceMethod.AddPass,
            LightServiceMethod.AddPass.Request(name, data, rawData, type, typed),
        ) is LightResult.Success

    /** Stacks another code under an existing pass (the barcode panel's "+"). */
    suspend fun addCode(passId: String, data: String, rawData: String?, type: String, typed: Boolean = false): Boolean =
        callRemoteServiceMethod(
            LightServiceMethod.AddCode,
            LightServiceMethod.AddCode.Request(passId, data, rawData, type, typed),
        ) is LightResult.Success

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
    ): Boolean =
        callRemoteServiceMethod(
            LightServiceMethod.UpdatePass,
            LightServiceMethod.UpdatePass.Request(
                passId,
                name,
                issuer,
                date,
                endDate,
                startTime,
                endTime,
                location,
                notes,
            ),
        ) is LightResult.Success

    /** Deletes one stacked code from its pass (the last code removes the pass). */
    suspend fun deleteCode(codeId: String) {
        callRemoteServiceMethod(
            LightServiceMethod.DeleteCode,
            LightServiceMethod.DeleteCode.Request(codeId),
        )
    }

    /** A code's barcode as PNG bytes (rendered by the companion at [width] px). */
    suspend fun barcodePng(codeId: String, width: Int = 960): ByteArray? =
        callRemoteServiceMethod(
            LightServiceMethod.GetBarcode,
            LightServiceMethod.GetBarcode.Request(codeId, width),
        ).getOrNull()?.png
}
