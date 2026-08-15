package com.lightphone.passes.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
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
import com.thelightphone.sdk.ui.verticalGridUnitsAsDp
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val DISPLAY_TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
private val TWELVE_HOUR_TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

/** Parses a stored time value: our display format, or a typed 12-hour one. */
private fun parseStoredTime(value: String): LocalTime? {
    if (value.isBlank()) return null
    return runCatching { LocalTime.parse(value, DISPLAY_TIME) }
        .recoverCatching { LocalTime.parse(value, TWELVE_HOUR_TIME) }
        .getOrNull()
}

/** Formats a picked time for storage and display (24-hour, e.g. "14:30"). */
private fun formatTime(time: LocalTime): String = DISPLAY_TIME.format(time)

/**
 * Pick a time on two scrollable columns (HOUR / MIN, 24-hour). Tap a value to
 * select it; SAVE stores "HH:mm" ("" when CLEARED); back cancels.
 */
class TimePickerScreen(
    sealedActivity: SealedLightActivity,
    private val title: String,
    private val initial: String,
) : SimpleLightScreen<String>(sealedActivity) {

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val initialTime = remember(initial) { parseStoredTime(initial) ?: LocalTime.NOON }
        var hour by remember { mutableStateOf(initialTime.hour) }
        var minute by remember { mutableStateOf(initialTime.minute) }

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
                    center = LightTopBarCenter.Text(text = title),
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 2f.gridUnitsAsDp()),
                ) {
                    TimeColumn(
                        label = "HOUR",
                        values = (0..23).toList(),
                        selected = hour,
                        onSelect = { hour = it },
                        modifier = Modifier.weight(1f),
                    )
                    TimeColumn(
                        label = "MIN",
                        values = (0..59).toList(),
                        selected = minute,
                        onSelect = { minute = it },
                        modifier = Modifier.weight(1f),
                    )
                }
                LightBottomBar(
                    modifier = Modifier.navigationBarsPadding(),
                    items = listOf(
                        if (initial.isNotBlank()) {
                            LightBarButton.Text(
                                text = "CLEAR",
                                onClick = { goBack("") },
                            )
                        } else {
                            null
                        },
                        null,
                        LightBarButton.Text(
                            text = "SAVE",
                            onClick = { goBack(formatTime(LocalTime.of(hour, minute))) },
                        ),
                    ),
                )
            }
        }
    }
}

/** One scrollable value column; the selected value renders full-strength. The
 *  list opens scrolled to the current value. */
@Composable
private fun TimeColumn(
    label: String,
    values: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        LightText(
            text = label,
            variant = LightTextVariant.Fine,
            lighten = true,
            align = TextAlign.Center,
            modifier = Modifier.padding(vertical = 0.5f.gridUnitsAsDp()),
        )
        val density = LocalDensity.current
        val rowHeight = 1.6f.verticalGridUnitsAsDp()
        val rowHeightPx = with(density) { rowHeight.toPx() }
        val initialIndex = values.indexOf(selected).coerceAtLeast(0)
        val scrollState = rememberScrollState(initial = (initialIndex * rowHeightPx).roundToInt())
        LightScrollView(
            scrollState = scrollState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            values.forEach { value ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
                        .lightClickable { onSelect(value) },
                    contentAlignment = Alignment.Center,
                ) {
                    LightText(
                        text = value.toString().padStart(2, '0'),
                        variant = LightTextVariant.Copy,
                        lighten = value != selected,
                    )
                }
            }
        }
    }
}
