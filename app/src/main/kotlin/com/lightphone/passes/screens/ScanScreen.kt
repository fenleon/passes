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
import com.lightphone.passes.PassesClient
import com.thelightphone.sdk.LightBarcodeScanner
import com.thelightphone.sdk.LightScannedBarcode
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import java.util.Base64

/**
 * Add a pass by scanning a code (SDK camera scanner — QR, Aztec, PDF417, Data
 * Matrix, Code 128, EAN/UPC, ...). The bottom bar offers "TYPE A CODE" to
 * switch to manual entry instead. The scanned format is preserved so the code
 * renders back in the same symbol; binary payloads (Aztec/PDF417 ticketing
 * codes) keep their raw bytes.
 *
 * With [addToPassId] set ("add to this pass" from the barcode panel), a
 * scanned/typed code is stacked straight under that pass — no naming step —
 * and the screen pops with `true` so the panel can refresh and show the new
 * code.
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
            val code = pendingScan ?: return@LaunchedEffect
            val target = addToPassId
            if (target != null) {
                val ok = PassesClient.addCode(
                    passId = target,
                    data = code.value,
                    rawData = rawDataFor(code),
                    type = code.formatName,
                    typed = false,
                )
                if (ok) goBack(true)
            } else {
                navigateTo(
                    screenFactory = {
                        NameScreen(
                            it,
                            code.value,
                            rawDataFor(code),
                            code.formatName,
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
                LightBarcodeScanner(
                    title = "Scan Code",
                    onScanned = { pendingScan = it },
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

    /** Aztec/PDF417 are byte-oriented: keep the raw payload so the renderer can
     *  reproduce the symbol 1:1. Text formats (QR, linear codes) just keep the
     *  decoded string. */
    private fun rawDataFor(code: LightScannedBarcode): String? {
        val bytes = code.rawBytes ?: return null
        return if (code.formatName == "aztec" || code.formatName == "pdf417") {
            Base64.getEncoder().encodeToString(bytes)
        } else {
            null
        }
    }
}
