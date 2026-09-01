package com.lightphone.passes.screens

import android.graphics.BitmapFactory
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.lightphone.passes.BarcodeCache
import com.lightphone.passes.Code
import com.lightphone.passes.Pass
import com.lightphone.passes.PassesClient
import com.lightphone.passes.PassRepository
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
 * A pass's code full-screen — the pass's primary view since 2026-08-30, opened
 * straight from the home list (the old barcode panel is gone). The code sits
 * on a 1:1 white square, sized so the space around it is equidistant to the
 * top bar and the bottom bar (a screen-width square is taller than the space
 * between the bars and collides with the title — feedback 2026-08-25).
 *
 * The top bar holds **"x of n"** as its title with `<` / `>` buttons to turn
 * between stacked codes; a single code shows **the pass name** as the title
 * (feedback 2026-08-30) — swiping left/right also turns between stacked codes
 * (bounded). The bottom bar is **DELETE** (bottom-left, the trash icon, opens
 * the delete confirmation), the **X** (center, dismisses back to the home
 * list), and the **`+`** (bottom-right, stacks another code onto the pass —
 * hidden at the 10-code stack cap). **Tapping the code opens the details
 * panel** (feedback 2026-08-30 — the code no longer dismisses; the X does).
 * Bitmaps come from the shared [BarcodeCache], so opening a code is instant.
 */
class BarcodeViewModel(
    private val passId: String,
    initialPass: Pass,
    initialIndex: Int = 0,
) : LightViewModel<Boolean>() {

    val pass = MutableStateFlow(initialPass)
    val index = MutableStateFlow(initialIndex.coerceIn(0, initialPass.codes.lastIndex))
    val barcode = MutableStateFlow<ImageBitmap?>(null)
    val barcodeError = MutableStateFlow(false)
    val loading = MutableStateFlow(true)

    /** Set when a code was just added; the next refresh lands on it (it is
     *  appended last in the pass's codes). */
    private var jumpToLast = false

    val current: Code
        get() = pass.value.codes[index.value.coerceIn(0, pass.value.codes.lastIndex)]

    /** Re-reads the pass (fresh after an add, edit, or code delete) and
     *  re-renders the current code at the display width. Runs on every show. */
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

    /** Swipe left (or the `>` arrow): the next stacked code (stops at the last). */
    fun next(widthPx: Int) {
        if (index.value >= pass.value.codes.lastIndex) return
        index.value += 1
        loadBarcode(widthPx)
    }

    /** Swipe right (or the `<` arrow): the previous stacked code (stops at the first). */
    fun previous(widthPx: Int) {
        if (index.value <= 0) return
        index.value -= 1
        loadBarcode(widthPx)
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
        // The bottom-right `+` stacks another code; at the stack cap (10) the
        // pass accepts no more codes, so it hides (feedback 2026-08-24).
        val canAdd = codeCount < PassRepository.MAX_STACK_SIZE

        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                // Top bar: "x of n" as the title with `<` (previous) and `>`
                // (next) buttons to move between stacked codes (feedback
                // 2026-08-24: the fullscreen's own stack navigation, rather
                // than the card-under label); a single code shows the pass
                // name as the title instead (feedback 2026-08-30).
                // Dismissal is the bottom-bar X.
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
                    center = LightTopBarCenter.Text(
                        text = if (stacked) "${index + 1} of ${codeCount}" else pass.name,
                    ),
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
                        // the bottom bar, leaving a 1-gu gap all around — a
                        // screen-width square is taller than the space between
                        // the bars and collides with the title (feedback
                        // 2026-08-25).
                        // Swiping turns between stacked codes, like calendar
                        // pages.
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
                    // bar, and the side gutters (equidistant — feedback
                    // 2026-08-25). Tapping the card opens the details panel
                    // (feedback 2026-08-30 — it used to dismiss).
                    val side = (minOf(maxWidth, maxHeight) - 2f.gridUnitsAsDp())
                        .coerceAtLeast(0.dp)
                    Box(
                        modifier = Modifier
                            .size(side)
                            .background(Color.White)
                            .lightClickable(onClick = { openDetails() }),
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
                // Corner actions (feedback 2026-08-30): DELETE bottom-left
                // (the delete confirmation — REMOVE PASS / REMOVE ALL), X
                // center (dismiss back to the home list), `+` bottom-right
                // (stack another code; hidden at the stack cap).
                LightBottomBar(
                    modifier = Modifier.navigationBarsPadding(),
                    items = listOf(
                        LightBarButton.LightIcon(
                            icon = LightIcons.TRASH,
                            onClick = { openDelete(widthPx) },
                            contentDescription = "Delete ${pass.name}",
                        ),
                        LightBarButton.LightIcon(
                            icon = LightIcons.CLOSE,
                            onClick = { goBack() },
                            contentDescription = "Close ${pass.name}",
                        ),
                        if (canAdd) {
                            LightBarButton.LightIcon(
                                icon = LightIcons.ADD,
                                onClick = { openAddCode() },
                                contentDescription = "Add code to ${pass.name}",
                            )
                        } else {
                            null
                        },
                    ),
                )
            }
        }
    }

    /** Tapping the code opens the details panel (its EDIT opens the edit form;
     *  delete lives in this screen's bottom-left, not there anymore — feedback
     *  2026-08-30). */
    private fun openDetails() {
        navigateTo(screenFactory = { DetailsScreen(it, viewModel.pass.value) })
    }

    /** The bottom-left DELETE (trash icon) opens the delete confirmation.
     *  Deleting the whole pass pops back to the home list; deleting one stacked
     *  code refreshes onto the remaining stack; a dismissed confirmation
     *  changes nothing. */
    private fun openDelete(widthPx: Int) {
        navigateTo(screenFactory = {
            DeleteConfirmScreen(it, viewModel.pass.value, viewModel.index.value)
        }) { deleted ->
            when (deleted) {
                true -> goBack()
                false -> viewModel.refresh(widthPx)
                null -> Unit
            }
        }
    }

    /** The bottom-right `+` opens the scanner in add-to-pass mode: a scanned
     *  or typed code stacks under this pass and the next refresh lands on it
     *  (see [BarcodeViewModel.markCodeAdded]). */
    private fun openAddCode() {
        navigateTo(screenFactory = { ScanScreen(it, viewModel.pass.value.id) }) { added ->
            if (added == true) viewModel.markCodeAdded()
        }
    }
}
