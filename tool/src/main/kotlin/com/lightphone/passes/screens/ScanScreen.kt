package com.lightphone.passes.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.util.Base64
import com.lightphone.passes.PassesClient
import com.thelightphone.sdk.LightQrCodeScanner
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarcodeFormats
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightScannedBarcode
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens

/**
 * Add a pass by scanning a code (the SDK's camera scanner). The bottom bar
 * offers "TYPE A CODE" to switch to manual entry instead. The scanner decodes
 * all 13 ZXing symbologies ([LightBarcodeFormats.COMMON]); a scanned code is
 * stored under its scanned format with its raw payload bytes (binary payloads
 * like Aztec ticketing codes keep their bytes; the renderers re-encode 1:1).
 *
 * With [addToPassId] set ("add to this pass" from the code fullscreen's `+`),
 * a scanned/typed code is stacked straight under that pass — no naming step —
 * and the screen pops with `true` so the fullscreen can refresh and show the
 * new code.
 */
class ScanScreen(
    sealedActivity: SealedLightActivity,
    private val addToPassId: String? = null,
) : SimpleLightScreen<Boolean>(sealedActivity) {

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        var pendingScan by remember { mutableStateOf<LightScannedBarcode?>(null) }

        // A code was decoded: move straight to naming it (or, in add-to-pass
        // mode, stack it under the pass without asking for a name).
        LaunchedEffect(pendingScan) {
            val scan = pendingScan ?: return@LaunchedEffect
            val type = scannedTypeName(scan.format) ?: return@LaunchedEffect
            val rawData = scan.rawBytes?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
            val target = addToPassId
            if (target != null) {
                val ok = PassesClient.addCode(
                    passId = target,
                    data = scan.value,
                    rawData = rawData,
                    type = type,
                    typed = false,
                )
                if (ok) goBack(true)
            } else {
                navigateTo(
                    screenFactory = {
                        NameScreen(
                            it,
                            scan.value,
                            rawData,
                            type,
                            typed = false,
                        )
                    },
                ) { saved -> if (saved == true) goBack(true) }
            }
        }

        LightTheme(colors = themeColors) {
            // The scanner fills the whole screen so its viewfinder scrim and
            // frame cover the full capture area (same as the SDK's
            // authenticator); the bottom bar floats above the scrim.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                LightQrCodeScanner(
                    title = "Scan Code",
                    onScanned = {},
                    formats = LightBarcodeFormats.COMMON,
                    onScannedBarcode = { pendingScan = it },
                    onBack = { goBack() },
                    modifier = Modifier.background(LightThemeTokens.colors.background),
                )
                LightBottomBar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding(),
                    items = listOf(
                        null,
                        LightBarButton.Text(
                            text = "TYPE A CODE",
                            onClick = {
                                navigateTo(
                                    screenFactory = { DataEntryScreen(it, addToPassId) },
                                ) { saved -> if (saved == true) goBack(true) }
                            },
                        ),
                        null,
                    ),
                )
            }
        }
    }
}

/** Scanned [LightBarcodeFormats] constant → the stored type name (all 13
 *  COMMON symbologies map; anything else can't be scanned). */
private fun scannedTypeName(format: Int): String? = when (format) {
    LightBarcodeFormats.QR -> "qr"
    LightBarcodeFormats.AZTEC -> "aztec"
    LightBarcodeFormats.EAN_13 -> "ean13"
    LightBarcodeFormats.EAN_8 -> "ean8"
    LightBarcodeFormats.PDF417 -> "pdf417"
    LightBarcodeFormats.UPC_E -> "upc_e"
    LightBarcodeFormats.DATA_MATRIX -> "datamatrix"
    LightBarcodeFormats.CODE_39 -> "code39"
    LightBarcodeFormats.CODE_93 -> "code93"
    LightBarcodeFormats.ITF -> "itf14"
    LightBarcodeFormats.CODABAR -> "codabar"
    LightBarcodeFormats.CODE_128 -> "code128"
    LightBarcodeFormats.UPC_A -> "upc_a"
    else -> null
}
