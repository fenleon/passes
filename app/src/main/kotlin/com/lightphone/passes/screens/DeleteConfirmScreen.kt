package com.lightphone.passes.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewModelScope
import com.lightphone.passes.Pass
import com.lightphone.passes.PassesClient
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * The delete confirmation — opened by the code fullscreen's top-right DELETE
 * (the trash icon). Every pass sees the same layout (feedback 2026-09-21):
 * the pass name as the title, "Are you sure you'd like to remove this
 * pass?", and — on a **stacked** pass — two stacked full-width buttons,
 * **CONFIRM** (removes just the code being viewed) above **REMOVE ALL** (the
 * whole stack); a single-code pass gets one centered **CONFIRM** bottom-bar
 * action (the whole pass). Back sits top-left and cancels.
 *
 * Result: `true` when the whole pass was deleted (the fullscreen pops back to
 * the home list), `false` when one stacked code was deleted (the fullscreen
 * refreshes onto the remaining stack), no result when dismissed.
 */
class DeleteViewModel(
    private val pass: Pass,
    private val currentIndex: Int,
) : LightViewModel<Boolean>() {

    /** Whether a delete is in flight — blocks double-taps. */
    val deleting = MutableStateFlow(false)

    /** Deletes the whole pass (every stacked code — the last one removes the
     *  pass) and pops `true`. */
    fun deleteAll(screen: SimpleLightScreen<Boolean>) {
        if (deleting.value) return
        viewModelScope.launch {
            deleting.value = true
            pass.codes.forEach { PassesClient.deleteCode(it.id) }
            screen.goBack(true)
        }
    }

    /** Deletes just the stacked code being viewed and pops `false`. */
    fun deleteOne(screen: SimpleLightScreen<Boolean>) {
        if (deleting.value) return
        viewModelScope.launch {
            deleting.value = true
            PassesClient.deleteCode(pass.codes[currentIndex.coerceIn(0, pass.codes.lastIndex)].id)
            screen.goBack(false)
        }
    }
}

class DeleteConfirmScreen(
    sealedActivity: SealedLightActivity,
    private val pass: Pass,
    private val currentIndex: Int,
) : LightScreen<Boolean, DeleteViewModel>(sealedActivity) {

    override val viewModelClass: Class<DeleteViewModel>
        get() = DeleteViewModel::class.java

    override fun createViewModel(): DeleteViewModel = DeleteViewModel(pass, currentIndex)

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val stacked = pass.codes.size > 1

        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(
                        icon = LightIcons.BACK,
                        onClick = { goBack() },
                        contentDescription = "Keep ${pass.name}",
                    ),
                    center = LightTopBarCenter.Text(text = pass.name),
                )
                // One question for every pass (feedback 2026-09-21); a stacked
                // pass answers with CONFIRM (the code being viewed) above
                // REMOVE ALL (the whole stack) below.
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 3f.gridUnitsAsDp()),
                    contentAlignment = Alignment.Center,
                ) {
                    LightText(
                        text = "Are you sure you'd like to remove this pass?",
                        variant = LightTextVariant.Copy,
                        align = TextAlign.Center,
                    )
                }
                if (stacked) {
                    Column(modifier = Modifier.navigationBarsPadding()) {
                        DeleteBarButton("CONFIRM") {
                            viewModel.deleteOne(this@DeleteConfirmScreen)
                        }
                        DeleteBarButton("REMOVE ALL") {
                            viewModel.deleteAll(this@DeleteConfirmScreen)
                        }
                    }
                } else {
                    // A single centered CONFIRM deletes the whole pass; back
                    // (top-left) cancels.
                    LightBottomBar(
                        modifier = Modifier.navigationBarsPadding(),
                        items = listOf(
                            LightBarButton.Text(
                                text = "CONFIRM",
                                onClick = { viewModel.deleteAll(this@DeleteConfirmScreen) },
                            ),
                        ),
                    )
                }
            }
        }
    }
}

/** A full-width bottom-bar-height text button — one half of the stacked pair
 *  on a stacked pass's delete confirm. */
@Composable
private fun DeleteBarButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4f.gridUnitsAsDp())
            .lightClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        LightText(text, variant = LightTextVariant.Button)
    }
}
