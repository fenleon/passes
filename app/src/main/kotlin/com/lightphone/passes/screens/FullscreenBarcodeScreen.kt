package com.lightphone.passes.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.dp
import com.lightphone.passes.Pass
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
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

/** Horizontal drag distance (px) that counts as a swipe between codes. */
private const val SWIPE_THRESHOLD_PX = 60f

/**
 * The code full-screen: the panel's clean scanner presentation — the code on a
 * 1:1 white square, sized so the space around it is equidistant to the top bar
 * and the bottom-bar X (a screen-width square is taller than the space between
 * the bars and collides with the title — feedback 2026-08-25). The window
 * brightness goes to max while this screen is up (so the scanner sees the
 * sharpest possible symbol) and is restored on exit (feedback 2026-08-24). The
 * top bar holds **"x of n"** as its title with `<` / `>` buttons to turn
 * between stacked codes; a single code shows **no title and no buttons**
 * (feedback 2026-08-25) — the bottom bar holds only an X that dismisses back
 * to the pass panel (tapping the card does the same). Swiping left/right also
 * turns between stacked codes (bounded). Bitmaps come from the shared
 * [BarcodeCache], so opening it for a code the panel already showed is instant.
 */
class FullscreenBarcodeScreen(
    sealedActivity: SealedLightActivity,
    private val pass: Pass,
    private val codeId: String,
) : LightScreen<Boolean, BarcodeViewModel>(sealedActivity) {

    override val viewModelClass: Class<BarcodeViewModel>
        get() = BarcodeViewModel::class.java

    override fun createViewModel(): BarcodeViewModel {
        val index = pass.codes.indexOfFirst { it.id == codeId }.coerceAtLeast(0)
        return BarcodeViewModel(pass.id, pass, index)
    }

    // While the code is up full-screen the window brightness goes to max, so
    // the scanner sees the sharpest possible symbol; it is restored on the way
    // out (back, tap-to-shrink, or the pass panel being popped underneath).
    override fun willShow() {
        super.willShow()
        setScreenBrightness(1f)
    }

    override fun willHide() {
        super.willHide()
        setScreenBrightness(null)
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
        // The `<` / `>` buttons only appear when there is a code to move to —
        // the first code shows only `>`, the last only `<` (feedback
        // 2026-08-25).
        val canPrevious = stacked && index > 0
        val canNext = stacked && index < codeCount - 1

        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                // Top bar: "x of n" as the title with `<` (previous) and `>`
                // (next) buttons to move between stacked codes (feedback
                // 2026-08-24: the fullscreen's own stack navigation, rather
                // than the card-under label) — shown only when the pass stacks;
                // a single code shows no title at all (feedback 2026-08-25).
                // Dismissal is the bottom-bar X (or tapping the card).
                LightTopBar(
                    leftButton = if (canPrevious) {
                        LightBarButton.LightIcon(
                            icon = LightIcons.BACK,
                            onClick = { viewModel.previous(widthPx) },
                            contentDescription = "Previous code",
                        )
                    } else {
                        null
                    },
                    center = if (stacked) {
                        LightTopBarCenter.Text(text = "${index + 1} of ${codeCount}")
                    } else {
                        null
                    },
                    rightButton = if (canNext) {
                        LightBarButton.LightIcon(
                            icon = LightIcons.ARROW_RIGHT,
                            onClick = { viewModel.next(widthPx) },
                            contentDescription = "Next code",
                        )
                    } else {
                        null
                    },
                )
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        // The square is shrunk to fit between the top bar and
                        // the bottom-bar X, leaving a 1-gu gap all around — a
                        // screen-width square is taller than the space between
                        // the bars and collides with the title (feedback
                        // 2026-08-25).
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
                    contentAlignment = Alignment.Center,
                ) {
                    // The code sits on a 1:1 white square; both dimensions are
                    // capped at the available space minus a 1-gu margin each
                    // side, so the card keeps the same gap to the title, the
                    // X bar, and the side gutters (equidistant — feedback
                    // 2026-08-25). Tapping the card dismisses it (same as the
                    // bottom-bar X).
                    val side = (minOf(maxWidth, maxHeight) - 2f.gridUnitsAsDp())
                        .coerceAtLeast(0.dp)
                    Box(
                        modifier = Modifier
                            .size(side)
                            .background(Color.White)
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
                }
                // Bottom bar: just an X to dismiss the fullscreen back to the
                // pass panel (feedback 2026-08-24). Single centered icon: with
                // one item the SDK centers it.
                LightBottomBar(
                    modifier = Modifier.navigationBarsPadding(),
                    items = listOf(
                        LightBarButton.LightIcon(
                            icon = LightIcons.CLOSE,
                            onClick = { goBack() },
                            contentDescription = "Close ${pass.name}",
                        ),
                    ),
                )
            }
        }
    }
}
