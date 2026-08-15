package com.lightphone.passes.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class HomeViewModel : LightViewModel<Unit>() {

    val passes = MutableStateFlow<List<Pass>>(emptyList())
    val loading = MutableStateFlow(true)

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            loading.value = true
            var result = PassesClient.getPasses()
            // A cold start can bind before the companion is ready; retry briefly
            // so the list doesn't flash "no passes" over a real one.
            repeat(EMPTY_REFRESH_RETRIES) {
                if (result.isNotEmpty()) return@repeat
                delay(EMPTY_REFRESH_DELAY_MS)
                result = PassesClient.getPasses()
            }
            passes.value = result
            loading.value = false
        }
    }

    private companion object {
        const val EMPTY_REFRESH_RETRIES = 4
        const val EMPTY_REFRESH_DELAY_MS = 500L
    }
}

@InitialScreen
class HomeScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, HomeViewModel>(sealedActivity) {

    override val viewModelClass: Class<HomeViewModel>
        get() = HomeViewModel::class.java

    override fun createViewModel(): HomeViewModel = HomeViewModel()

    @Composable
    override fun Content() {
        val passes by viewModel.passes.collectAsState()
        val loading by viewModel.loading.collectAsState()
        val themeColors by LightThemeController.colors.collectAsState()

        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    when {
                        loading && passes.isEmpty() -> StatusText("Loading…")
                        passes.isEmpty() -> Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 1f.gridUnitsAsDp()),
                            contentAlignment = Alignment.Center,
                        ) {
                            LightText(
                                text = "no passes added",
                                variant = LightTextVariant.Copy,
                                lighten = true,
                                align = TextAlign.Center,
                            )
                        }
                        else -> LightScrollView {
                            // One row per pass; stacked codes under the name show
                            // a count to the right. The companion stores the list
                            // alphabetically, so the rows already are.
                            passes.forEach { pass ->
                                PassRow(pass, pass.codes.size) { openBarcode(pass) }
                            }
                        }
                    }
                }
                LightBottomBar(
                    modifier = Modifier.navigationBarsPadding(),
                    items = listOf(
                        null,
                        LightBarButton.Text(
                            text = "ADD NEW",
                            onClick = { openScanner() },
                        ),
                        null,
                    ),
                )
            }
        }
    }

    private fun openBarcode(pass: Pass) {
        navigateTo(screenFactory = { BarcodeScreen(it, pass) })
    }

    private fun openScanner() {
        navigateTo(screenFactory = { ScanScreen(it) })
    }
}

@Composable
private fun StatusText(text: String) {
    LightText(
        text = text,
        variant = LightTextVariant.Copy,
        lighten = true,
        modifier = Modifier.padding(24.dp),
    )
}

/** One pass row: the name, and the stacked-code count ("n") to the right when
 *  more than one code sits under the name. */
@Composable
private fun PassRow(pass: Pass, count: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .lightClickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LightText(
            text = pass.name,
            variant = LightTextVariant.Copy,
            modifier = Modifier.weight(1f),
        )
        if (count > 1) {
            LightText(
                text = count.toString(),
                variant = LightTextVariant.Detail,
                lighten = true,
            )
        }
    }
}
