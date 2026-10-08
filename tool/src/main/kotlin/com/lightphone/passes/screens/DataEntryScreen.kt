package com.lightphone.passes.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.lightphone.passes.PassesClient
import com.thelightphone.lp3Keyboard.ui.KeyboardOptions
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightTextInputEditor
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.scaledForScreenHeight
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * "Type a code": the LP3 keyboard editor for code content, in the same
 * Notes-compose style as the other text entry (SAVE bottom-center below the
 * keyboard, small bottom-anchored text, keyboard flush at the bottom). The
 * keyboard shows **no microphone and no emoji key** (a code is text, not a
 * voice note or emoji) and starts capitalized. Typed codes are always stored
 * as QR — the only sensible format for arbitrary text.
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
        // Fixed options — no remote fetch, so the mic/emoji keys stay off even
        // when the platform server would enable them.
        val keyboardOptionsFlow = remember {
            MutableStateFlow(
                KeyboardOptions(
                    emojis = emptyList(),
                    displayReturn = true,
                    displayVoice = false,
                    enableKeyAnimation = true,
                    swipeEnabled = false,
                ),
            )
        }
        val inputStyle = LightThemeTokens.typography.heading
            .copy(color = LightThemeTokens.colors.content)
            .scaledForScreenHeight()
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
                bottomAligned = true,
                submitOnReturn = true,
                singleLine = true,
                initialCaps = true,
                inputTextStyle = inputStyle,
            )
        }
    }
}
