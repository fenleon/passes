package com.lightphone.passes.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewModelScope
import com.lightphone.passes.PassesClient
import com.lightphone.passes.StoredSymbol
import com.thelightphone.lp3Keyboard.ui.KeyboardOptions
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightTextInputEditor
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.scaledForScreenHeight
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Names a newly scanned or typed code before it is saved as a new pass. */
class NameViewModel(
    private val data: String,
    private val rawData: String?,
    private val type: String,
    private val typed: Boolean,
    private val symbol: StoredSymbol? = null,
) : LightViewModel<Boolean>() {

    val saving = MutableStateFlow(false)

    fun save(name: String, screen: SimpleLightScreen<Boolean>) {
        if (saving.value) return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            saving.value = true
            val ok = PassesClient.addPass(trimmed, data, rawData, type, typed, symbol)
            saving.value = false
            if (ok) screen.goBack(true)
            // On a failed save stay on the editor; the user can submit again.
        }
    }
}

/**
 * The name editor for a new pass, in the same Notes-compose style as the edit
 * panel's text fields (SAVE bottom-center below the keyboard, bottom-anchored
 * text, keyboard flush at the bottom) — no mic/emoji keys, capitalized start.
 */
class NameScreen(
    sealedActivity: SealedLightActivity,
    private val data: String,
    private val rawData: String?,
    private val type: String,
    private val typed: Boolean,
    private val symbol: StoredSymbol? = null,
) : LightScreen<Boolean, NameViewModel>(sealedActivity) {

    override val viewModelClass: Class<NameViewModel>
        get() = NameViewModel::class.java

    override fun createViewModel(): NameViewModel =
        NameViewModel(data, rawData, type, typed, symbol)

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

        LightTheme(colors = themeColors) {
            LightTextInputEditor(
                title = "Name Pass",
                state = textState,
                keyboardOptionsFlow = keyboardOptionsFlow,
                onSubmit = { result -> viewModel.save(result.toString(), this@NameScreen) },
                onBack = { goBack() },
                modifier = Modifier.background(LightThemeTokens.colors.background),
                submitLabel = "SAVE",
                bottomAligned = true,
                submitOnReturn = true,
                initialCaps = true,
                singleLine = true,
                inputTextStyle = inputStyle,
            )
        }
    }
}
