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
import com.thelightphone.sdk.LightQrCodeScanner
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens

/**
 * Add a pass by scanning a code (the SDK's camera scanner). The bottom bar
 * offers "TYPE A CODE" to switch to manual entry instead. Scans are stored as
 * QR — the upstream scanner decodes QR only; other symbologies return to the
 * scanner once the SDK gains multi-format support (the renderers here already
 * handle all 13 ZXing formats).
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
        var pendingScan by remember { mutableStateOf<String?>(null) }

        // A code was decoded: move straight to naming it (or, in add-to-pass
        // mode, stack it under the pass without asking for a name).
        LaunchedEffect(pendingScan) {
            val data = pendingScan ?: return@LaunchedEffect
            val target = addToPassId
            if (target != null) {
                val ok = PassesClient.addCode(
                    passId = target,
                    data = data,
                    rawData = null,
                    type = "qr",
                    typed = false,
                )
                if (ok) goBack(true)
            } else {
                navigateTo(
                    screenFactory = {
                        NameScreen(
                            it,
                            data,
                            null,
                            "qr",
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
}
