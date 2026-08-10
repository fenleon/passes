package com.lightphone.passes.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.rememberKeyboardOptions
import com.thelightphone.sdk.ui.LightTextInputEditor
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens

/**
 * "Type a code": the LP3 keyboard editor for code content. Typed codes are
 * always stored as QR — the only sensible format for arbitrary text.
 */
class DataEntryScreen(sealedActivity: SealedLightActivity) :
    SimpleLightScreen<Boolean>(sealedActivity) {

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val keyboardOptionsFlow = rememberKeyboardOptions()
        val textState = rememberTextFieldState("")

        LightTheme(colors = themeColors) {
            LightTextInputEditor(
                title = "Enter Code",
                state = textState,
                keyboardOptionsFlow = keyboardOptionsFlow,
                onSubmit = { result ->
                    val text = result.toString().trim()
                    if (text.isNotEmpty()) {
                        navigateTo(
                            screenFactory = {
                                NameScreen(it, NameMode.Create(text, null, "qr"))
                            },
                        ) { saved -> if (saved == true) goBack(true) }
                    }
                },
                onBack = { goBack() },
                modifier = Modifier.background(LightThemeTokens.colors.background),
            )
        }
    }
}
