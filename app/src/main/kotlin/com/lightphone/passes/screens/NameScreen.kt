package com.lightphone.passes.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewModelScope
import com.lightphone.passes.PassesClient
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.rememberKeyboardOptions
import com.thelightphone.sdk.ui.LightTextInputEditor
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** What this name editor is doing: naming a new code or renaming a saved one. */
sealed interface NameMode {
    data class Create(val data: String, val rawData: String?, val type: String) : NameMode
    data class Rename(val passId: String, val currentName: String) : NameMode
}

class NameViewModel(private val mode: NameMode) : LightViewModel<Boolean>() {

    val saving = MutableStateFlow(false)

    fun save(name: String, screen: SimpleLightScreen<Boolean>) {
        if (saving.value) return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            saving.value = true
            val ok = when (mode) {
                is NameMode.Create ->
                    PassesClient.addPass(trimmed, mode.data, mode.rawData, mode.type)
                is NameMode.Rename ->
                    PassesClient.renamePass(mode.passId, trimmed)
            }
            saving.value = false
            if (ok) screen.goBack(true)
            // On a failed save stay on the editor; the user can submit again.
        }
    }
}

class NameScreen(
    sealedActivity: SealedLightActivity,
    private val mode: NameMode,
) : LightScreen<Boolean, NameViewModel>(sealedActivity) {

    override val viewModelClass: Class<NameViewModel>
        get() = NameViewModel::class.java

    override fun createViewModel(): NameViewModel = NameViewModel(mode)

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val keyboardOptionsFlow = rememberKeyboardOptions()
        val initial = (mode as? NameMode.Rename)?.currentName.orEmpty()
        val textState = rememberTextFieldState(initial)

        LightTheme(colors = themeColors) {
            LightTextInputEditor(
                title = if (mode is NameMode.Rename) "Rename Pass" else "Name Pass",
                state = textState,
                keyboardOptionsFlow = keyboardOptionsFlow,
                onSubmit = { result -> viewModel.save(result.toString(), this@NameScreen) },
                onBack = { goBack() },
                modifier = Modifier.background(LightThemeTokens.colors.background),
                initialCaps = true,
            )
        }
    }
}
