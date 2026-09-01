package com.lightphone.passes.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lightphone.passes.formatStoredDate
import com.lightphone.passes.parseStoredDate
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
import com.thelightphone.sdk.ui.verticalGridUnitsAsDp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MONTH_TITLE = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)

/** The pickers' selection underline: a ~2dp bar at the text's bottom edge —
 *  the text-field underline thickness, thinner than LightText's 4dp selection
 *  bar (feedback 2026-08-25). Shared by the date and time pickers. */
@Composable
internal fun Modifier.thinUnderline(): Modifier {
    val density = LocalDensity.current
    val content = LightThemeTokens.colors.content
    val thicknessPx = with(density) { 2.dp.toPx() }
    return drawBehind {
        drawRect(
            color = content,
            topLeft = Offset(0f, size.height - thicknessPx),
            size = Size(size.width, thicknessPx),
        )
    }
}

/**
 * Pick a date on a full-screen month grid, modeled on the calendar app's month
 * view: weekday letters, centered month title (with the "selected month"
 * underline), tap a day to pick it, `<` / `>` in the top bar (or a swipe) to
 * change month (feedback 2026-08-24: the arrows moved from the bottom bar up
 * into the top bar). Everything renders white; the selected day carries the
 * selection underline. When the pass already has a date, the bottom bar shows
 * CLEAR / X / SAVE (tap a day to select it, SAVE stores it); for a fresh pick
 * the bar is just X and tapping a day stores it immediately.
 * Result: "MMM d, yyyy" (or "" when CLEARED); null (back / X) = cancelled,
 * keeps the old value.
 */
class DatePickerScreen(
    sealedActivity: SealedLightActivity,
    private val initial: String,
) : SimpleLightScreen<String>(sealedActivity) {

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val initialDate = remember(initial) { parseStoredDate(initial) ?: LocalDate.now() }
        var month by remember { mutableStateOf(initialDate.withDayOfMonth(1)) }
        val hasDate = initial.isNotBlank()
        // A fresh pick starts with nothing selected — today shows its dot, no
        // underline (feedback 2026-08-25).
        var selected by remember { mutableStateOf(if (hasDate) initialDate else null) }

        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                // Month navigation lives in the top bar (feedback 2026-08-24):
                // `<` previous month, the month title, `>` next month — no
                // underline on the month (feedback 2026-08-25). Dismissal is
                // the bottom-bar X, so there's no back button here.
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(
                        icon = LightIcons.BACK,
                        onClick = { month = month.minusMonths(1) },
                        contentDescription = "Previous month",
                    ),
                    center = LightTopBarCenter.Text(
                        text = MONTH_TITLE.format(month),
                    ),
                    rightButton = LightBarButton.LightIcon(
                        icon = LightIcons.ARROW_RIGHT,
                        onClick = { month = month.plusMonths(1) },
                        contentDescription = "Next month",
                    ),
                )
                // Swiping left/right turns the month (same as the top-bar
                // arrows): left = next month, right = previous (feedback
                // 2026-08-30 — the picker's grid turns like calendar pages).
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .pointerInput(Unit) {
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
                                            month = month.plusMonths(1)
                                        dragTotal >= SWIPE_THRESHOLD_PX ->
                                            month = month.minusMonths(1)
                                    }
                                },
                            )
                        },
                ) {
                    MonthGrid(
                        month = month,
                        selected = selected,
                        onDaySelected = { day ->
                            if (hasDate) {
                                // Editing an existing date: tap selects (the
                                // underline) and SAVE commits it.
                                selected = day
                            } else {
                                // Fresh pick: tapping a day stores it right away.
                                goBack(formatStoredDate(day))
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (hasDate) {
                    // Editing: CLEAR (remove the date), X (dismiss), SAVE (keep
                    // the selected day) — the calendar-form corner grammar
                    // (feedback 2026-08-24).
                    LightBottomBar(
                        modifier = Modifier.navigationBarsPadding(),
                        items = listOf(
                            LightBarButton.Text(
                                text = "CLEAR",
                                onClick = { goBack("") },
                            ),
                            LightBarButton.LightIcon(
                                icon = LightIcons.CLOSE,
                                onClick = { goBack() },
                                contentDescription = "Close without changing",
                            ),
                            LightBarButton.Text(
                                text = "SAVE",
                                onClick = { goBack(formatStoredDate(selected ?: initialDate)) },
                            ),
                        ),
                    )
                } else {
                    // Fresh pick: just X to dismiss (feedback 2026-08-24).
                    LightBottomBar(
                        modifier = Modifier.navigationBarsPadding(),
                        items = listOf(
                            LightBarButton.LightIcon(
                                icon = LightIcons.CLOSE,
                                onClick = { goBack() },
                                contentDescription = "Close without changing",
                            ),
                        ),
                    )
                }
            }
        }
    }
}

/** The calendar month grid: weekday letters + a 7×6 day grid. Everything is
 *  white (content color); the selected day carries a thin underline, today a
 *  round dot under the number that hides while today is selected (feedback
 *  2026-08-25). Tapping a day picks it. */
@Composable
private fun MonthGrid(
    month: LocalDate,
    selected: LocalDate?,
    onDaySelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = LocalDate.now()
    Column(
        modifier = modifier
            .padding(horizontal = 2f.gridUnitsAsDp()),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            WEEKDAY_LETTERS.forEach { letter ->
                LightText(
                    text = letter.toString(),
                    variant = LightTextVariant.Fine,
                    align = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 0.5f.gridUnitsAsDp()),
                )
            }
        }
        // Sunday-first, like the calendar month view.
        val offset = month.dayOfWeek.value % 7
        val daysInMonth = month.lengthOfMonth()
        repeat(6) { week ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3f.verticalGridUnitsAsDp()),
            ) {
                for (col in 0 until 7) {
                    val dayNumber = week * 7 + col - offset + 1
                    val day = if (dayNumber in 1..daysInMonth) {
                        month.withDayOfMonth(dayNumber)
                    } else {
                        null
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .lightClickable(enabled = day != null) { day?.let(onDaySelected) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (day != null) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                LightText(
                                    text = dayNumber.toString(),
                                    variant = LightTextVariant.Copy,
                                    modifier = if (day == selected) {
                                        Modifier.thinUnderline()
                                    } else {
                                        Modifier
                                    },
                                )
                                // Today carries a round dot under the number,
                                // not an underline — the dot disappears when
                                // today is the selected day, which marks itself
                                // with the underline instead (feedback
                                // 2026-08-25).
                                if (day == today && day != selected) {
                                    Spacer(Modifier.height(0.25f.gridUnitsAsDp()))
                                    Box(
                                        modifier = Modifier
                                            .size(0.25f.gridUnitsAsDp())
                                            .background(
                                                LightThemeTokens.colors.content,
                                                CircleShape,
                                            ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val SWIPE_THRESHOLD_PX = 60f
private val WEEKDAY_LETTERS = listOf('S', 'M', 'T', 'W', 'T', 'F', 'S')
