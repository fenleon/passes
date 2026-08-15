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
                    codes = pass.codes.map { code ->
                        LightServiceMethod.GetPasses.Code(
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
            LightResult.Success(
                LightServiceMethod.GetPasses.encodeResponse(
                    LightServiceMethod.GetPasses.Response(passes),
                ),
            )
        }

        LightServiceMethod.AddPass.id -> {
            val request = LightServiceMethod.AddPass.decodeRequest(payload!!)
            PassRepository.add(
                request.name,
                request.data,
                request.rawData,
                request.type,
                request.typed,
            )
            LightResult.Success(LightServiceMethod.AddPass.encodeResponse(Unit))
        }

        LightServiceMethod.AddCode.id -> {
            val request = LightServiceMethod.AddCode.decodeRequest(payload!!)
            PassRepository.addCode(
                request.passId,
                request.data,
                request.rawData,
                request.type,
                request.typed,
            )
            LightResult.Success(LightServiceMethod.AddCode.encodeResponse(Unit))
        }

        LightServiceMethod.UpdatePass.id -> {
            val request = LightServiceMethod.UpdatePass.decodeRequest(payload!!)
            PassRepository.update(
                request.passId,
                request.name,
                request.issuer,
                request.date,
                request.endDate,
                request.startTime,
                request.endTime,
                request.location,
                request.notes,
            )
            LightResult.Success(LightServiceMethod.UpdatePass.encodeResponse(Unit))
        }

        LightServiceMethod.DeleteCode.id -> {
            val request = LightServiceMethod.DeleteCode.decodeRequest(payload!!)
            PassRepository.deleteCode(request.codeId)
            LightResult.Success(LightServiceMethod.DeleteCode.encodeResponse(Unit))
        }

        LightServiceMethod.GetBarcode.id -> {
            val request = LightServiceMethod.GetBarcode.decodeRequest(payload!!)
            val code = PassRepository.codeFor(request.codeId)
                ?: return LightResult.Error(LightResult.ErrorCode.Unknown, "code not found")
            val png = BarcodeRenderer.renderPng(code.type, code.data, code.rawData, request.width)
                ?: return LightResult.Error(
                    LightResult.ErrorCode.Unknown,
                    "unable to render ${code.type} barcode",
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
