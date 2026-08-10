package com.lightphone.passes

import com.thelightphone.sdk.callRemoteServiceMethod
import com.thelightphone.sdk.shared.LightResult
import com.thelightphone.sdk.shared.LightServiceMethod
import com.thelightphone.sdk.shared.getOrNull

/** A pass as served by the companion. */
typealias Pass = LightServiceMethod.GetPasses.Pass

/**
 * Thin RPC client for the Passes methods. Everything privileged (storage,
 * barcode rendering, camera, URL opening) lives in the companion (:server);
 * the tool only renders state fetched over the SDK binder.
 */
object PassesClient {

    suspend fun getPasses(): List<Pass> =
        callRemoteServiceMethod(LightServiceMethod.GetPasses, Unit)
            .getOrNull()?.passes.orEmpty()

    suspend fun addPass(name: String, data: String, rawData: String?, type: String): Boolean =
        callRemoteServiceMethod(
            LightServiceMethod.AddPass,
            LightServiceMethod.AddPass.Request(name, data, rawData, type),
        ) is LightResult.Success

    suspend fun renamePass(passId: String, name: String): Boolean =
        callRemoteServiceMethod(
            LightServiceMethod.UpdatePassName,
            LightServiceMethod.UpdatePassName.Request(passId, name),
        ) is LightResult.Success

    suspend fun deletePass(passId: String) {
        callRemoteServiceMethod(
            LightServiceMethod.DeletePass,
            LightServiceMethod.DeletePass.Request(passId),
        )
    }

    /** The pass's barcode as PNG bytes (rendered by the companion at [width] px). */
    suspend fun barcodePng(passId: String, width: Int = 960): ByteArray? =
        callRemoteServiceMethod(
            LightServiceMethod.GetBarcode,
            LightServiceMethod.GetBarcode.Request(passId, width),
        ).getOrNull()?.png
}
