package com.lightphone.passes.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.lightphone.passes.PassesClient
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.rememberKeyboardOptions
import com.thelightphone.sdk.ui.LightTextInputEditor
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import kotlinx.coroutines.launch

/**
 * "Type a code": the LP3 keyboard editor for code content, in the same
 * Notes-compose style as the other text entry (SAVE in the top bar, small
 * bottom-anchored text, keyboard flush at the bottom). Typed codes are always
 * stored as QR — the only sensible format for arbitrary text.
 *
 * With [addToPassId] set, the typed code is stacked straight under that pass
 * (typed, so its text shows under the barcode) and the screen pops with `true`.
 */
class DataEntryScreen(
    sealedActivity: SealedLightActivity,
    private val addToPassId: String? = null,
) : SimpleLightScreen<Boolean>(sealedActivity) {

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val keyboardOptionsFlow = rememberKeyboardOptions()
        val textState = rememberTextFieldState("")
        val scope = rememberCoroutineScope()

        LightTheme(colors = themeColors) {
            LightTextInputEditor(
                title = "Enter Code",
                state = textState,
                keyboardOptionsFlow = keyboardOptionsFlow,
                onSubmit = { result ->
                    val text = result.toString().trim()
                    if (text.isEmpty()) return@LightTextInputEditor
                    val target = addToPassId
                    if (target != null) {
                        scope.launch {
                            val ok = PassesClient.addCode(target, text, null, "qr", typed = true)
                            if (ok) goBack(true)
                        }
                    } else {
                        navigateTo(
                            screenFactory = {
                                NameScreen(it, text, null, "qr", typed = true)
                            },
                        ) { saved -> if (saved == true) goBack(true) }
                    }
                },
                onBack = { goBack() },
                modifier = Modifier.background(LightThemeTokens.colors.background),
                submitLabel = "SAVE",
                submitInTopBar = true,
                topBarSubmitLabel = "SAVE",
                bottomAligned = true,
                submitOnReturn = true,
                singleLine = true,
            )
        }
    }
}
