package com.lightphone.passes.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewModelScope
import com.lightphone.passes.Pass
import com.lightphone.passes.PassesClient
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
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
 * The delete screen — the edit panel's delete destination. It lists the pass's
 * stacked codes as rows ("Pass 1", "Pass 2", …) with an X on each row to delete
 * just that code, and a single **DELETE ALL** in the bottom bar that removes the
 * whole pass (feedback 2026-08-24: the old centered confirm panel). Back sits
 * top-left; backing out returns unchanged, deleting one code returns refreshed,
 * DELETE ALL (or deleting the last code via its row X) removes the pass.
 *
 * Result: `true` when the whole pass was deleted (the screens above pop
 * themselves), `false` after a single-code delete (the edit panel pops, the
 * details panel refreshes onto the remaining stack), no result when dismissed.
 */
class DeleteViewModel(private val pass: Pass) : LightViewModel<Boolean>() {

    /** The pass's codes still present — every row X drops its code live; when
     *  the last one goes the pass is gone (reported `true` back). */
    val remainingCodes = MutableStateFlow(pass.codes)

    fun deleteCode(screen: SimpleLightScreen<Boolean>, codeId: String) {
        if (remainingCodes.value.isEmpty()) return
        viewModelScope.launch {
            PassesClient.deleteCode(codeId)
            val left = remainingCodes.value.filterNot { it.id == codeId }
            if (left.isEmpty()) {
                screen.goBack(true) // last code — the whole pass is gone
            } else {
                remainingCodes.value = left
            }
        }
    }

    fun deleteAll(screen: SimpleLightScreen<Boolean>) {
        if (remainingCodes.value.isEmpty()) return
        viewModelScope.launch {
            // Deleting one code after another; the final one removes the pass.
            remainingCodes.value.forEach { PassesClient.deleteCode(it.id) }
            screen.goBack(true)
        }
    }
}

class DeleteConfirmScreen(
    sealedActivity: SealedLightActivity,
    private val pass: Pass,
) : LightScreen<Boolean, DeleteViewModel>(sealedActivity) {

    override val viewModelClass: Class<DeleteViewModel>
        get() = DeleteViewModel::class.java

    override fun createViewModel(): DeleteViewModel = DeleteViewModel(pass)

    @Composable
    override fun Content() {
        val codes by viewModel.remainingCodes.collectAsState()
        val themeColors by LightThemeController.colors.collectAsState()

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
                        contentDescription = "Back to ${pass.name}",
                    ),
                    center = LightTopBarCenter.Text(text = "Delete Pass"),
                )
                Box(modifier = Modifier.weight(1f)) {
                    if (codes.isEmpty()) {
                        // Every row X tapped the list away (the pass is gone).
                        LightText(
                            text = "Deleting…",
                            variant = LightTextVariant.Copy,
                            modifier = Modifier.padding(2f.gridUnitsAsDp()),
                        )
                    } else {
                        LightScrollView {
                            codes.forEachIndexed { index, code ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 2f.gridUnitsAsDp(), vertical = 0.5f.gridUnitsAsDp()),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    LightText(
                                        text = "Pass ${index + 1}",
                                        variant = LightTextVariant.Copy,
                                        modifier = Modifier.weight(1f),
                                    )
                                    LightIcon(
                                        icon = LightIcons.CLOSE,
                                        contentDescription = "Delete Pass ${index + 1}",
                                        modifier = Modifier
                                            .widthIn(min = 3.5f.gridUnitsAsDp())
                                            .lightClickable { viewModel.deleteCode(this@DeleteConfirmScreen, code.id) },
                                    )
                                }
                            }
                        }
                    }
                }
                // DELETE ALL in the bottom bar (feedback 2026-08-24: the old
                // per-code DELETE / CANCEL pair is now this one whole-pass bar
                // action; per-code deletion lives on the rows' X).
                LightBottomBar(
                    modifier = Modifier.navigationBarsPadding(),
                    items = listOf(
                        null,
                        LightBarButton.Text(
                            text = "DELETE ALL",
                            onClick = { viewModel.deleteAll(this@DeleteConfirmScreen) },
                        ),
                        null,
                    ),
                )
            }
        }
    }
}