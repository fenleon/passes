package com.lightphone.passes.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lightphone.passes.Pass
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBarButton
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

/** Horizontal drag distance (px) that counts as a swipe between codes. */
private const val SWIPE_THRESHOLD_PX = 60f

/**
 * The code full-screen on white — the panel's clean presentation for showing
 * to a scanner. It keeps the same top bar (pass name, "x of n" when stacked,
 * next-code arrow / `+` on the last) and the same stack navigation: swipe
 * left/right turns between codes (bounded). Back returns to the panel; no
 * bottom bar, no code text. Bitmaps come from the shared [BarcodeCache], so
 * opening it for a code the panel already showed is instant.
 */
class FullscreenBarcodeScreen(
    sealedActivity: SealedLightActivity,
    private val pass: Pass,
    private val codeId: String,
) : LightScreen<Unit, BarcodeViewModel>(sealedActivity) {

    override val viewModelClass: Class<BarcodeViewModel>
        get() = BarcodeViewModel::class.java

    override fun createViewModel(): BarcodeViewModel {
        val index = pass.codes.indexOfFirst { it.id == codeId }.coerceAtLeast(0)
        return BarcodeViewModel(pass.id, pass, index)
    }

    @Composable
    override fun Content() {
        val pass by viewModel.pass.collectAsState()
        val index by viewModel.index.collectAsState()
        val barcode by viewModel.barcode.collectAsState()
        val barcodeError by viewModel.barcodeError.collectAsState()
        val loading by viewModel.loading.collectAsState()
        val themeColors by LightThemeController.colors.collectAsState()

        val configuration = LocalConfiguration.current
        val density = LocalDensity.current
        val widthPx = remember(configuration, density) {
            (configuration.screenWidthDp * density.density).toInt()
        }
        LaunchedEffect(Unit) { viewModel.refresh(widthPx) }

        val codeCount = pass.codes.size
        val stacked = codeCount > 1

        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                // Top bar: the pass name, the next-code arrow while one exists
                // (no + here — adding lives on the pass panel), and a back
                // button that steps back through the stack first, leaving only
                // from the first code — the panel's behavior.
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(
                        icon = LightIcons.BACK,
                        onClick = { if (!viewModel.previous(widthPx)) goBack() },
                        contentDescription = if (stacked && index > 0) {
                            "Previous code"
                        } else {
                            "Back to ${pass.name}"
                        },
                    ),
                    center = LightTopBarCenter.Text(text = pass.name),
                    rightButton = if (stacked && index < codeCount - 1) {
                        LightBarButton.LightIcon(
                            icon = LightIcons.ARROW_RIGHT,
                            onClick = { viewModel.next(widthPx) },
                            contentDescription = "Next code",
                        )
                    } else {
                        null
                    },
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        // Swiping turns between stacked codes, like the panel.
                        .pointerInput(codeCount) {
                            if (codeCount <= 1) return@pointerInput
                            var dragTotal = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { dragTotal = 0f },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    dragTotal += dragAmount
                                },
                                onDragEnd = {
                                    when {
                                        dragTotal <= -SWIPE_THRESHOLD_PX ->
                                            viewModel.next(widthPx)
                                        dragTotal >= SWIPE_THRESHOLD_PX ->
                                            viewModel.previous(widthPx)
                                    }
                                },
                            )
                        },
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 1f.gridUnitsAsDp(), vertical = 1f.gridUnitsAsDp())
                                .background(Color.White)
                                // Tapping the code again shrinks it back to the
                                // pass panel.
                                .lightClickable(onClick = { goBack() }),
                            contentAlignment = Alignment.Center,
                        ) {
                            val bitmap = barcode
                            when {
                                bitmap != null -> Image(
                                    bitmap = bitmap,
                                    contentDescription = "Barcode for ${pass.name}",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(1f.gridUnitsAsDp()),
                                )

                                barcodeError -> LightText(
                                    text = "Unable to render this barcode.",
                                    variant = LightTextVariant.Copy,
                                    modifier = Modifier.padding(24.dp),
                                )

                                else -> LightText(
                                    text = if (loading) "Generating…" else "",
                                    variant = LightTextVariant.Copy,
                                    modifier = Modifier.padding(24.dp),
                                )
                            }
                        }
                        // "x of n" below the code, same as the panel.
                        if (stacked) {
                            LightText(
                                text = "${index + 1} of $codeCount",
                                variant = LightTextVariant.Detail,
                                lighten = true,
                                align = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 2f.gridUnitsAsDp(), vertical = 1f.gridUnitsAsDp()),
                            )
                        }
                    }
                }
            }
        }
    }
}
