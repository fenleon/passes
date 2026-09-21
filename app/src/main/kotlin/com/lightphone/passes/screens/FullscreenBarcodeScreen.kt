package com.lightphone.passes.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

/** Drag distance (px) that counts as a swipe between codes. */
private const val SWIPE_THRESHOLD_PX = 60f

/**
 * A pass's code full-screen — the pass's primary view, opened straight from the
 * home list. The code sits on a 1:1 white square sized so the space around it
 * is equidistant to the top bar and the bottom bar.
 *
 * The top bar holds **back in the top-left** (dismisses to the home list), **the
 * pass name as the title**, and **DELETE in the top-right** (the trash icon,
 * opens the delete confirmation). The **vertical dot column on the right edge**
 * always shows (LP3 toolbox page-dot spec: 1-gu dots at a 1.78-gu pitch,
 * vertically centered; the current code filled, the rest hollow rings) —
 * swiping **up/down** or tapping a dot turns between the stacked codes, and a
 * **small `+` the size of the dots sits under the column** (tapping it or
 * swiping up past the last code opens the scanner in add-to-pass mode; hidden
 * at the 10-code stack cap and in the expanded state). The bottom bar is
 * **VIEW DETAILS** (opens the details panel). **Tapping the code toggles the
 * inverted view**: the whole back panel turns white (the code renders
 * black-on-white), the dots invert (filled ⇄ hollow, black), the `+` hides,
 * the bars hide (their space stays reserved — the code never moves) and the
 * window brightness pins to max; tap again to collapse (the black back arrow
 * top-left also collapses). The code itself
 * always renders **black-on-white** — the raw raster on a white square, no
 * theme mapping, no extra white border. Bitmaps come from the
 * shared [BarcodeCache], so opening a code is instant.
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

    /** Swipe up (or a dot tap): the next stacked code (stops at the last). */
    fun next(widthPx: Int) {
        if (index.value >= pass.value.codes.lastIndex) return
        index.value += 1
        loadBarcode(widthPx)
    }

    /** Swipe down (or a dot tap): the previous stacked code (stops at the first). */
    fun previous(widthPx: Int) {
        if (index.value <= 0) return
        index.value -= 1
        loadBarcode(widthPx)
    }

    /** Jumping to a stacked code by tapping its dot. */
    fun goTo(target: Int, widthPx: Int) {
        index.value = target.coerceIn(0, pass.value.codes.lastIndex)
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
        // The `+` under the dots stacks another code; at the stack cap (10)
        // the pass accepts no more codes, so it hides (feedback 2026-08-24).
        val canAdd = codeCount < PassRepository.MAX_STACK_SIZE
        // Tapping the code toggles the expanded view: the bars hide (their
        // space is reserved so the code never moves — hiding the taller bottom
        // bar used to re-center the square lower) and the window brightness
        // pins to max while expanded (feedback 2026-09-10).
        var expanded by remember { mutableStateOf(false) }
        DisposableEffect(expanded) {
            if (expanded) setScreenBrightness(1f)
            onDispose { if (expanded) setScreenBrightness(null) }
        }

        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // Tapping the code inverts the whole back panel to white
                    // (feedback 2026-09-21) — the code renders black-on-white
                    // against it.
                    .background(if (expanded) Color.White else LightThemeTokens.colors.background),
            ) {
                // Top bar: back in the top-left (dismisses to the home list —
                // the old bottom-bar X is gone), the pass name as the title,
                // and DELETE top-right (the old bottom-left trash — feedback
                // 2026-09-21). Stack navigation stays on the right-edge dots
                // + up/down swiping.
                if (!expanded) {
                    LightTopBar(
                        leftButton = LightBarButton.LightIcon(
                            icon = LightIcons.BACK,
                            onClick = { goBack() },
                            contentDescription = "Back to the pass list",
                        ),
                        center = LightTopBarCenter.Text(
                            text = pass.name,
                            onClick = { openEdit() },
                        ),
                        rightButton = LightBarButton.LightIcon(
                            icon = LightIcons.TRASH,
                            onClick = { openDelete(widthPx) },
                            contentDescription = "Delete ${pass.name}",
                        ),
                    )
                } else {
                    // Reserved top-bar space (the expanded square keeps the
                    // exact collapsed constraints, so it never moves) — with
                    // a black back arrow top-left so the inverted view keeps
                    // its exit (feedback 2026-09-21). Same geometry as the
                    // collapsed bar's back: 1-gu gutter, 2-gu icon, 0.5-gu
                    // top inset.
                    Box(
                        modifier = Modifier
                            .height(3f.gridUnitsAsDp())
                            .fillMaxWidth(),
                    ) {
                        Image(
                            painter = painterResource(LightIcons.BACK.drawableResource),
                            contentDescription = "Collapse the expanded view",
                            colorFilter = ColorFilter.tint(Color.Black),
                            modifier = Modifier
                                .padding(start = 1f.gridUnitsAsDp(), top = 0.5f.gridUnitsAsDp())
                                .size(2f.gridUnitsAsDp())
                                .lightClickable(onClick = { expanded = false }),
                        )
                    }
                }
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        // The square is shrunk to fit between the top bar and
                        // the bottom bar, leaving a 1-gu gap all around.
                        // Swiping up/down turns between stacked codes; the
                        // drag is consumed even on single-code passes so a
                        // swipe never registers as a card tap (no accidental
                        // expand). Swiping up past the last code (scrolling
                        // down the stack) opens the add-code scanner — the
                        // swipe twin of the `+` under the dots (feedback
                        // 2026-09-21).
                        .pointerInput(codeCount) {
                            var dragTotal = 0f
                            detectVerticalDragGestures(
                                onDragStart = { dragTotal = 0f },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    dragTotal += dragAmount
                                },
                                onDragEnd = {
                                    when {
                                        dragTotal <= -SWIPE_THRESHOLD_PX &&
                                            index >= codeCount - 1 && canAdd -> openAddCode()
                                        codeCount > 1 && dragTotal <= -SWIPE_THRESHOLD_PX ->
                                            viewModel.next(widthPx)
                                        codeCount > 1 && dragTotal >= SWIPE_THRESHOLD_PX ->
                                            viewModel.previous(widthPx)
                                    }
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    // The code always renders black-on-white — the raw raster
                    // on a white square, sized so the space around it is
                    // equidistant to the title, the bar, and the gutters (a
                    // screen-width square collides with the title). No
                    // padding inside the square: the raster carries its own
                    // quiet zone, so there is no extra white border
                    // (feedback 2026-09-21). The width cap clears the
                    // always-present dot column so all the air is equal —
                    // between dots, card→dots, and dots→screen edge (G =
                    // 1.39 gu: the column's end padding is G/2, the
                    // pitch is the 0.85-gu dot + G, so the card edge
                    // lands at 1 + 2G + d − 1 gu extra → extra shrink =
                    // 5.26 gu). Tapping the
                    // code toggles the expanded (inverted) view (only the
                    // code and the dots remain; tap again returns).
                    val side = (minOf(
                        maxWidth - 5.26f.gridUnitsAsDp(),
                        maxHeight,
                    ) - 2f.gridUnitsAsDp()).coerceAtLeast(0.dp)
                    Box(
                        modifier = Modifier
                            .size(side)
                            .background(Color.White)
                            .lightClickable(onClick = { expanded = !expanded }),
                        contentAlignment = Alignment.Center,
                    ) {
                        val bitmap = barcode
                        when {
                            bitmap != null -> Image(
                                bitmap = bitmap,
                                contentDescription = "Barcode for ${pass.name}",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
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
                    // The stacked-code dots: right edge, vertically centered —
                    // the LP3 toolbox page-dot spec (0.75-gu dots, 1.78-gu
                    // pitch; current code filled, the rest hollow rings).
                    // Always shown — a single code reads "1 of 1" (feedback
                    // 2026-09-21). The small `+` under the column stacks
                    // another code (the old bottom-right `+`); hidden at the
                    // stack cap and in the inverted state.
                    StackDots(
                        count = codeCount,
                        current = index,
                        inverted = expanded,
                        showPlus = canAdd && !expanded,
                        onSelect = { viewModel.goTo(it, widthPx) },
                        onAdd = { openAddCode() },
                        // Padding outside align: the column shifts in by
                        // the gutter (padding inside align would keep the
                        // node flush with the edge).
                        modifier = Modifier
                            .padding(end = 0.7f.gridUnitsAsDp())
                            .align(Alignment.CenterEnd),
                    )
                }
                // Bottom bar: VIEW DETAILS alone (DELETE moved top-right, `+`
                // moved under the dots — feedback 2026-09-21).
                if (!expanded) {
                    LightBottomBar(
                        modifier = Modifier.navigationBarsPadding(),
                        items = listOf(
                            LightBarButton.Text(
                                text = "VIEW DETAILS",
                                onClick = { openDetails() },
                            ),
                        ),
                    )
                } else {
                    // Reserved bottom-bar space (4 gu + the nav-bar inset the
                    // bar carried) — see the top spacer above.
                    Spacer(
                        Modifier
                            .navigationBarsPadding()
                            .height(4f.gridUnitsAsDp()),
                    )
                }
            }
        }
    }

    /** Tapping the pass name opens the full edit form directly (feedback
     *  2026-09-21). */
    private fun openEdit() {
        navigateTo(screenFactory = { EditScreen(it, viewModel.pass.value) })
    }

    /** Tapping the code opens the details panel (its EDIT opens the edit form;
     *  delete lives in this screen's top-right, not there anymore — feedback
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

/**
 * The stacked-code page dots — the LP3 toolbox page-dot geometry (1-gu dots,
 * vertically centered, gutter-flush column), with the pitch opened up so all
 * the air is equidistant: inter-dot gap = card→dots = dots→screen edge = G
 * (1.39 gu), so the pitch is 1 gu dot + G = 2.39 gu and the caller pads the
 * column in by G/2 = 0.7 gu (2026-09-21 feedback — the toolbox's tighter
 * 1.78-gu pitch left the dot gaps visibly narrower than the card air).
 * Always rendered — a single code shows one dot
 * ("1 of 1", feedback 2026-09-21). The current code renders filled, the others
 * hollow rings (LP3 matches); [inverted] renders them all in black (the
 * white back panel of the expanded state — current still filled, feedback
 * 2026-09-21). When [showPlus], a bare `+` the
 * size of the dots hangs under the column ([onAdd] — the add-code scanner).
 * Each dot's tap target is its full pitch square, so there are no dead zones.
 */
@Composable
private fun StackDots(
    count: Int,
    current: Int,
    inverted: Boolean,
    showPlus: Boolean,
    onSelect: (Int) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = if (inverted) Color.Black else LightThemeTokens.colors.content
    // Dots at the toolbox's exact size: the toolbox capture
    // (reference/lp3/screens/toolbox/home.xml) has 34 px dots / 80 px pitch
    // on the LP3's 1080 px screen; in-app 1 gu = screenWidthDp/27 = 40 px,
    // so 34 px = 0.85 gu (the older "0.75 gu toolbox dot" note used the
    // wrong px-per-gu). Equal-air gap G = 1.39 gu → pitch 2.24 gu. The
    // column centers on the DOTS only; the `+` hangs in the next pitch slot
    // below the dots block (feedback 2026-09-21).
    val dot = 0.85f.gridUnitsAsDp()
    val pitch = 2.24f.gridUnitsAsDp()
    Box(modifier = modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            repeat(count) { i ->
                Box(
                    modifier = Modifier
                        .size(pitch)
                        .lightClickable(onClick = { onSelect(i) })
                        .semantics { contentDescription = "Code ${i + 1} of $count" },
                    contentAlignment = Alignment.Center,
                ) {
                    if (i == current) {
                        Box(
                            modifier = Modifier
                                .size(dot)
                                .background(ink, CircleShape),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(dot)
                                .border(2.dp, ink, CircleShape),
                        )
                    }
                }
            }
        }
        if (showPlus) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = pitch * (count + 1) / 2)
                    .size(pitch)
                    .lightClickable(onClick = onAdd)
                    .semantics { contentDescription = "Add a code" },
                contentAlignment = Alignment.Center,
            ) {
                // A bare `+` the size of the dots — the circled ADD
                // icon read as a third page dot (feedback 2026-09-21); the
                // 2-dp stroke matches the hollow rings.
                Box(modifier = Modifier.size(dot)) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(ink),
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxHeight()
                            .width(2.dp)
                            .background(ink),
                    )
                }
            }
        }
    }
}

