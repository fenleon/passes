package com.lightphone.passes.server

import com.thelightphone.sdk.shared.LightResult
import com.thelightphone.sdk.shared.LightServiceMethod

/**
 * Implements the Passes methods on the companion's LightSdkService — the
 * server-side half of the tool model: the tool is a thin UI that calls these
 * over the SDK binder; everything privileged lives here.
 *
 * The resolver runs on a binder thread; the repository and stores are
 * internally synchronized, and SharedPreferences writes happen here (never on
 * the main thread).
 */
object PassesServiceMethods {

    fun dispatch(methodId: String, payload: String?): LightResult<String> = when (methodId) {
        LightServiceMethod.GetPasses.id -> {
            val passes = PassRepository.passes.value.map { pass ->
                LightServiceMethod.GetPasses.Pass(
                    id = pass.id,
                    name = pass.name,
                    data = pass.data,
                    rawData = pass.rawData,
                    type = pass.type,
                )
            }
            LightResult.Success(
                LightServiceMethod.GetPasses.encodeResponse(
                    LightServiceMethod.GetPasses.Response(passes),
                ),
            )
        }

        LightServiceMethod.AddPass.id -> {
            val request = LightServiceMethod.AddPass.decodeRequest(payload!!)
            PassRepository.add(request.name, request.data, request.rawData, request.type)
            LightResult.Success(LightServiceMethod.AddPass.encodeResponse(Unit))
        }

        LightServiceMethod.UpdatePassName.id -> {
            val request = LightServiceMethod.UpdatePassName.decodeRequest(payload!!)
            PassRepository.rename(request.passId, request.name)
            LightResult.Success(LightServiceMethod.UpdatePassName.encodeResponse(Unit))
        }

        LightServiceMethod.DeletePass.id -> {
            val request = LightServiceMethod.DeletePass.decodeRequest(payload!!)
            PassRepository.delete(request.passId)
            LightResult.Success(LightServiceMethod.DeletePass.encodeResponse(Unit))
        }

        LightServiceMethod.GetBarcode.id -> {
            val request = LightServiceMethod.GetBarcode.decodeRequest(payload!!)
            val pass = PassRepository.get(request.passId)
                ?: return LightResult.Error(LightResult.ErrorCode.Unknown, "pass not found")
            val png = BarcodeRenderer.renderPng(pass.type, pass.data, pass.rawData, request.width)
                ?: return LightResult.Error(
                    LightResult.ErrorCode.Unknown,
                    "unable to render ${pass.type} barcode",
                )
            LightResult.Success(
                LightServiceMethod.GetBarcode.encodeResponse(
                    LightServiceMethod.GetBarcode.Response(png),
                ),
            )
        }

        else -> LightResult.Error(
            LightResult.ErrorCode.Unknown,
            "unknown method: $methodId",
        )
    }
}
