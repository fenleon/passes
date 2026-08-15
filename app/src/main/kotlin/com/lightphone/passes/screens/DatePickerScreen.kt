package com.lightphone.passes.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
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

private val DISPLAY_DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
private val MONTH_TITLE = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)
private val ISO_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)

/** Parses a stored date value: our display format, or ISO (from typed entry). */
private fun parseStoredDate(value: String): LocalDate? {
    if (value.isBlank()) return null
    return runCatching { LocalDate.parse(value, DISPLAY_DATE) }
        .recoverCatching { LocalDate.parse(value, ISO_DATE) }
        .getOrNull()
}

/** Formats a picked date for storage and display. */
private fun formatDate(date: LocalDate): String = DISPLAY_DATE.format(date)

/**
 * Pick a date on a full-screen month grid, modeled on the calendar app's month
 * view: weekday letters, centered month title, tap a day to pick it, ‹ › (or a
 * swipe) to change month, swipe = previous/next month. Result: "MMM d, yyyy"
 * (or "" when CLEARED); null (back) = cancelled, keeps the old value.
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
        var selected by remember { mutableStateOf(initialDate) }

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
                        contentDescription = "Cancel",
                    ),
                    center = LightTopBarCenter.Text(text = MONTH_TITLE.format(month)),
                )
                Box(modifier = Modifier.weight(1f)) {
                    MonthGrid(
                        month = month,
                        selected = selected,
                        onDaySelected = { day ->
                            selected = day
                            goBack(formatDate(day))
                        },
                        onPreviousMonth = { month = month.minusMonths(1) },
                        onNextMonth = { month = month.plusMonths(1) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                LightBottomBar(
                    modifier = Modifier.navigationBarsPadding(),
                    items = listOf(
                        LightBarButton.Text(
                            text = "‹",
                            onClick = { month = month.minusMonths(1) },
                        ),
                        if (initial.isNotBlank()) {
                            LightBarButton.Text(
                                text = "CLEAR",
                                onClick = { goBack("") },
                            )
                        } else {
                            null
                        },
                        LightBarButton.Text(
                            text = "›",
                            onClick = { month = month.plusMonths(1) },
                        ),
                    ),
                )
            }
        }
    }
}

/** The calendar month grid: weekday letters + a 7×6 day grid. Swiping left/right
 *  changes the month (like the calendar app); tapping a day picks it. */
@Composable
private fun MonthGrid(
    month: LocalDate,
    selected: LocalDate,
    onDaySelected: (LocalDate) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .pointerInput(month) {
                var dragTotal = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragTotal = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        dragTotal += dragAmount
                    },
                    onDragEnd = {
                        when {
                            dragTotal <= -SWIPE_THRESHOLD_PX -> onNextMonth()
                            dragTotal >= SWIPE_THRESHOLD_PX -> onPreviousMonth()
                        }
                    },
                )
            }
            .padding(horizontal = 2f.gridUnitsAsDp()),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            WEEKDAY_LETTERS.forEach { letter ->
                LightText(
                    text = letter.toString(),
                    variant = LightTextVariant.Fine,
                    lighten = true,
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
                            LightText(
                                text = dayNumber.toString(),
                                variant = LightTextVariant.Copy,
                                lighten = day != selected,
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val SWIPE_THRESHOLD_PX = 60f
private val WEEKDAY_LETTERS = listOf('S', 'M', 'T', 'W', 'T', 'F', 'S')
