package com.lightphone.passes.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * The delete-confirm panel — opened by the barcode panel's bottom-left X. It
 * asks before deleting **the one code being viewed**: when the pass stacks
 * several codes, only that code goes (the rest are kept); the whole pass is
 * removed only when it was the last code.
 *
 * Result: `true` when the whole pass was deleted (the screens above pop
 * themselves), `false` after a single-code delete (the barcode panel refreshes
 * onto the remaining stack), no result when dismissed.
 */
class DeleteConfirmViewModel(private val pass: Pass, private val codeId: String) :
    LightViewModel<Boolean>() {

    val busy = MutableStateFlow(false)

    val index: Int = pass.codes.indexOfFirst { it.id == codeId }.coerceAtLeast(0)
    val stacked: Boolean = pass.codes.size > 1

    fun delete(screen: SimpleLightScreen<Boolean>) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            PassesClient.deleteCode(codeId)
            busy.value = false
            // Only the last code's deletion removes the whole pass (result true).
            screen.goBack(!stacked)
        }
    }
}

class DeleteConfirmScreen(
    sealedActivity: SealedLightActivity,
    private val pass: Pass,
    private val codeId: String,
) : LightScreen<Boolean, DeleteConfirmViewModel>(sealedActivity) {

    override val viewModelClass: Class<DeleteConfirmViewModel>
        get() = DeleteConfirmViewModel::class.java

    override fun createViewModel(): DeleteConfirmViewModel = DeleteConfirmViewModel(pass, codeId)

    @Composable
    override fun Content() {
        val busy by viewModel.busy.collectAsState()
        val themeColors by LightThemeController.colors.collectAsState()

        val title = if (viewModel.stacked) {
            "Delete Pass ${viewModel.index + 1} of ${pass.codes.size}"
        } else {
            "Delete Pass"
        }
        val body = if (viewModel.stacked) {
            "Delete this code? The other codes are kept."
        } else {
            "Delete ${pass.name}?"
        }

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
                    center = LightTopBarCenter.Text(text = title),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    LightText(
                        text = body,
                        variant = LightTextVariant.Copy,
                        align = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 3f.gridUnitsAsDp()),
                    )
                }
                LightBottomBar(
                    modifier = Modifier.navigationBarsPadding(),
                    items = listOf(
                        LightBarButton.Text(
                            text = "DELETE",
                            onClick = { viewModel.delete(this@DeleteConfirmScreen) },
                        ),
                        null,
                        LightBarButton.Text(
                            text = "CANCEL",
                            onClick = { goBack() },
                        ),
                    ),
                )
            }
        }
    }
}
