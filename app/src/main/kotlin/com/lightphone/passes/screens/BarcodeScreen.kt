package com.lightphone.passes.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.lightphone.passes.BarcodeCache
import com.lightphone.passes.Code
import com.lightphone.passes.Pass
import com.lightphone.passes.PassesClient
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Horizontal drag distance (px) that counts as a swipe between codes. */
private const val SWIPE_THRESHOLD_PX = 60f

/**
 * The barcode panel: the pass's stacked codes, one full-size on white. The
 * pass name (and "x of n") sits in the top bar; **swiping left/right switches
 * between the stacked codes** like turning calendar pages, and the back button
 * steps back through the stack before leaving. The top-right `+` (only on the
 * last code) stacks another code onto the pass. The bottom bar holds VIEW
 * DETAILS. Typed codes show their text under the barcode; scanned payloads are
 * usually noise, so they stay hidden.
 */
class BarcodeViewModel(
    private val passId: String,
    initialPass: Pass,
    initialIndex: Int = 0,
) : LightViewModel<Unit>() {

    val pass = MutableStateFlow(initialPass)
    val index = MutableStateFlow(initialIndex.coerceIn(0, initialPass.codes.lastIndex))
    val barcode = MutableStateFlow<ImageBitmap?>(null)
    val barcodeError = MutableStateFlow(false)
    val loading = MutableStateFlow(true)

    /** Set when a code was just added; the next refresh lands on it (it is
     *  appended last in the pass's codes). */
    private var jumpToLast = false

    val stacked: Boolean
        get() = pass.value.codes.size > 1

    val current: Code
        get() = pass.value.codes[index.value.coerceIn(0, pass.value.codes.lastIndex)]

    /** Re-reads the pass (fresh after an add or edit) and re-renders the
     *  current code at the display width. Runs on every show. */
    fun refresh(widthPx: Int) {
        viewModelScope.launch {
            val fresh = PassesClient.getPasses().find { it.id == passId }
            if (fresh != null) pass.value = fresh
            if (jumpToLast) {
                jumpToLast = false
                index.value = pass.value.codes.lastIndex
            }
            index.value = index.value.coerceIn(0, pass.value.codes.lastIndex)
            loadBarcode(widthPx)
        }
    }

    /** Swipe left (or the old arrow): the next stacked code (stops at the last). */
    fun next(widthPx: Int) {
        if (index.value >= pass.value.codes.lastIndex) return
        index.value += 1
        loadBarcode(widthPx)
    }

    /** Swipe right, or the back button on a stacked pass: step to the previous
     *  code; on the first code the back button leaves the panel. */
    fun previous(widthPx: Int): Boolean {
        if (index.value > 0) {
            index.value -= 1
            loadBarcode(widthPx)
            return true
        }
        return false
    }

    /** Remember that a code was just added, so the next refresh lands on it. */
    fun markCodeAdded() {
        jumpToLast = true
    }

    fun loadBarcode(widthPx: Int) {
        viewModelScope.launch {
            // Cached bitmap (already rendered this session) → instant, no flash.
            val cached = BarcodeCache.get(current.id)
            if (cached != null) {
                barcode.value = cached
                barcodeError.value = false
                loading.value = false
                return@launch
            }
            barcode.value = null
            barcodeError.value = false
            loading.value = true
            val png = PassesClient.barcodePng(current.id, widthPx)
            val bitmap = png?.let {
                BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap()
            }
            loading.value = false
            if (bitmap == null) {
                barcodeError.value = true
            } else {
                BarcodeCache.put(current.id, bitmap)
                barcode.value = bitmap
            }
        }
    }
}

class BarcodeScreen(
    sealedActivity: SealedLightActivity,
    private val pass: Pass,
) : LightScreen<Unit, BarcodeViewModel>(sealedActivity) {

    override val viewModelClass: Class<BarcodeViewModel>
        get() = BarcodeViewModel::class.java

    override fun createViewModel(): BarcodeViewModel = BarcodeViewModel(pass.id, pass)

    @Composable
    override fun Content() {
        val pass by viewModel.pass.collectAsState()
        val index by viewModel.index.collectAsState()
        val barcode by viewModel.barcode.collectAsState()
        val barcodeError by viewModel.barcodeError.collectAsState()
        val loading by viewModel.loading.collectAsState()
        val themeColors by LightThemeController.colors.collectAsState()

        // Render the code at the full display width so the scanner gets the
        // sharpest possible symbol. Re-runs on every show (fresh composition).
        val configuration = LocalConfiguration.current
        val density = LocalDensity.current
        val widthPx = remember(configuration, density) {
            (configuration.screenWidthDp * density.density).toInt()
        }
        LaunchedEffect(Unit) { viewModel.refresh(widthPx) }

        val codeCount = pass.codes.size
        val current = pass.codes[index.coerceIn(0, pass.codes.lastIndex)]
        val stacked = codeCount > 1

        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(
                        icon = LightIcons.BACK,
                        onClick = { if (!viewModel.previous(widthPx)) goBack() },
                        // Stepping through a stack: back moves to the previous
                        // code first, and only leaves from the first.
                        contentDescription = if (stacked && index > 0) {
                            "Previous code"
                        } else {
                            "Back to Passes"
                        },
                    ),
                    center = LightTopBarCenter.Text(text = pass.name),
                    // Right slot: the next-code arrow while one exists, the +
                    // (add another code) on the very last code — swipe to the
                    // end to extend the stack.
                    rightButton = when {
                        stacked && index < codeCount - 1 -> LightBarButton.LightIcon(
                            icon = LightIcons.ARROW_RIGHT,
                            onClick = { viewModel.next(widthPx) },
                            contentDescription = "Next code",
                        )
                        index == codeCount - 1 -> LightBarButton.LightIcon(
                            icon = LightIcons.ADD,
                            onClick = { openAddCode() },
                            contentDescription = "Add code to ${pass.name}",
                        )
                        else -> null
                    },
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        // Swiping left/right turns between stacked codes, like
                        // calendar months — only when the pass is stacked.
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
                    when {
                        loading && barcode == null -> StatusText("Generating…")
                        barcodeError -> StatusText("Unable to render this barcode.")
                        barcode != null -> BarcodeView(
                            bitmap = barcode!!,
                            code = current,
                            onExpand = { openFullscreen() },
                        )
                    }
                }
                // VIEW DETAILS opens the panel where the name, issuer etc. live
                // (the only EDIT entry point lives there, bottom-bar center).
                LightBottomBar(
                    modifier = Modifier.navigationBarsPadding(),
                    items = listOf(
                        null,
                        LightBarButton.Text(
                            text = "VIEW DETAILS",
                            onClick = { openDetails() },
                        ),
                        null,
                    ),
                )
            }
        }
    }

    /** VIEW DETAILS shows the pass's shared details; EDIT lives there. When the
     *  whole pass is deleted there, this panel pops. */
    private fun openDetails() {
        navigateTo(screenFactory = {
            DetailsScreen(it, viewModel.pass.value, viewModel.current.id)
        }) { deleted ->
            if (deleted == true) goBack()
        }
    }

    /** The + opens the scanner in add-to-pass mode: a scanned or typed code is
     *  stacked under this pass and shown here (see [BarcodeViewModel.markCodeAdded]). */
    private fun openAddCode() {
        navigateTo(screenFactory = { ScanScreen(it, viewModel.pass.value.id) }) { added ->
            if (added == true) viewModel.markCodeAdded()
        }
    }

    /** Tapping the code opens it full-screen — the code presented large, with
     *  the same top bar and stack navigation (swipe + arrow). */
    private fun openFullscreen() {
        navigateTo(
            screenFactory = {
                FullscreenBarcodeScreen(it, viewModel.pass.value, viewModel.current.id)
            },
        )
    }
}

/** The code on a white card (white is required so the code stays scannable) at
 *  full display width, and — only for typed codes — its text below. No "x of n"
 *  here; the stacked position shows only in the fullscreen. Tapping the card
 *  opens the code full-screen. */
@Composable
private fun BarcodeView(bitmap: ImageBitmap, code: Code, onExpand: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 1f.gridUnitsAsDp(), vertical = 1f.gridUnitsAsDp())
                .background(Color.White)
                .lightClickable(onClick = onExpand),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = bitmap,
                contentDescription = "Barcode",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(1f.gridUnitsAsDp()),
            )
        }
        if (code.typed) {
            LightText(
                text = code.data,
                variant = LightTextVariant.Copy,
                align = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2f.gridUnitsAsDp(), vertical = 1f.gridUnitsAsDp()),
            )
        }
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
