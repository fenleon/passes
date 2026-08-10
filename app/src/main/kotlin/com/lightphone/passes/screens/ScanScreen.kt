package com.lightphone.passes.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.thelightphone.sdk.LightQrCodeScanner
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens

/**
 * Add a pass by scanning a QR code (SDK camera scanner). The bottom bar offers
 * "TYPE A CODE" to switch to manual entry instead. Scanned codes are stored as
 * QR — the scanner only decodes that format.
 */
class ScanScreen(sealedActivity: SealedLightActivity) :
    SimpleLightScreen<Unit>(sealedActivity) {

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        var pendingScan by remember { mutableStateOf<String?>(null) }

        // A code was decoded: move straight to naming it.
        LaunchedEffect(pendingScan) {
            val data = pendingScan ?: return@LaunchedEffect
            navigateTo(
                screenFactory = { NameScreen(it, NameMode.Create(data, null, "qr")) },
            ) { saved -> if (saved == true) goBack() }
        }

        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    LightQrCodeScanner(
                        title = "Scan QR Code",
                        onScanned = { pendingScan = it },
                        onBack = { goBack() },
                        modifier = Modifier.background(LightThemeTokens.colors.background),
                    )
                }
                LightBottomBar(
                    modifier = Modifier.navigationBarsPadding(),
                    items = listOf(
                        null,
                        LightBarButton.Text(
                            text = "TYPE A CODE",
                            onClick = {
                                navigateTo(
                                    screenFactory = { DataEntryScreen(it) },
                                ) { saved -> if (saved == true) goBack() }
                            },
                        ),
                        null,
                    ),
                )
            }
        }
    }
}
