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
 * The delete confirmation — opened by the code fullscreen's bottom-left DELETE
 * (the trash icon). A single-code pass confirms simply: the pass name as the
 * title, "Are you sure you'd like to remove this pass?", one centered
 * **CONFIRM**. A **stacked** pass asks (feedback 2026-08-30): the name, "Do you
 * want to remove x of n passes or all passes in this stack?" (x = the code
 * being viewed), and two stacked buttons — **REMOVE PASS** (just that one code)
 * and **REMOVE ALL** (the whole stack). Back sits top-left and cancels.
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
                    center = if (stacked) null else LightTopBarCenter.Text(text = pass.name),
                )
                // A single-code pass asks one confirmation (the pass name is
                // the title); a stacked pass names the pass and asks "x of n" —
                // the code being viewed — or all (feedback 2026-08-30).
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 3f.gridUnitsAsDp()),
                    contentAlignment = Alignment.Center,
                ) {
                    if (stacked) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            LightText(
                                text = pass.name,
                                variant = LightTextVariant.Heading,
                                align = TextAlign.Center,
                            )
                            LightText(
                                text = "Do you want to remove ${currentIndex + 1} of ${pass.codes.size} passes or all passes in this stack?",
                                variant = LightTextVariant.Copy,
                                align = TextAlign.Center,
                                modifier = Modifier.padding(top = 1.5f.gridUnitsAsDp()),
                            )
                        }
                    } else {
                        LightText(
                            text = "Are you sure you'd like to remove this pass?",
                            variant = LightTextVariant.Copy,
                            align = TextAlign.Center,
                        )
                    }
                }
                if (stacked) {
                    // Two stacked full-width buttons — REMOVE PASS (the code
                    // being viewed) above REMOVE ALL (the whole stack) —
                    // feedback 2026-08-30.
                    Column(modifier = Modifier.navigationBarsPadding()) {
                        DeleteBarButton("REMOVE PASS") {
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
