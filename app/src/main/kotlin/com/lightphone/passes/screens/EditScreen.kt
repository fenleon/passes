package com.lightphone.passes.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.lightphone.passes.Pass
import com.lightphone.passes.PassesClient
import com.thelightphone.lp3Keyboard.ui.KeyboardOptions
import com.thelightphone.lp3Keyboard.ui.viewmodel.defaultEmojis
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextInputEditor
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import com.thelightphone.sdk.ui.scaledForScreenHeight
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * The edit panel: edits a pass's key details (name, issuer, date, end date,
 * start/end time, location, notes) one field at a time. The top-bar title is
 * "Edit Pass" (stacking doesn't split the entity — the edits apply to all the
 * pass's codes, so "Edit Passes" looks like a mistake). No back button in the
 * top bar (feedback 2026-08-24) — dismissing without saving is the bottom-bar
 * X. The name, issuer, and location fields edit on the LP3 keyboard in the
 * **code-entry style** (larger, vertically centered — same editor for all
 * three; feedback 2026-08-24: issuer/location match the name), Notes is
 * multi-line Notes-style; SAVE is centered below the keyboard (feedback
 * 2026-08-25 — was bottom-right); the keyboard shows no mic anywhere and no
 * emoji key except in the Notes
 * field. Date/End date open the calendar-style date picker, Start/End time
 * the time picker — Date+End date and Start+End time share a row each. Every
 * row's input carries the ~2dp text-field underline (the radio search bar's
 * standard). After a field edit the form scrolls back to the row that was
 * tapped. Bottom bar: DELETE (opens the per-code delete screen), the X
 * (dismiss without saving), SAVE (persists).
 *
 * Result: `true` when the whole pass was deleted (the screens above pop
 * themselves), anything else returns to the details panel unchanged.
 */
class EditViewModel(private val pass: Pass) :
    LightViewModel<Boolean>() {

    val name = MutableStateFlow(pass.name)
    val issuer = MutableStateFlow(pass.issuer.orEmpty())
    val date = MutableStateFlow(pass.date.orEmpty())
    val endDate = MutableStateFlow(pass.endDate.orEmpty())
    val startTime = MutableStateFlow(pass.startTime.orEmpty())
    val endTime = MutableStateFlow(pass.endTime.orEmpty())
    val location = MutableStateFlow(pass.location.orEmpty())
    val notes = MutableStateFlow(pass.notes.orEmpty())
    val busy = MutableStateFlow(false)

    fun save(screen: SimpleLightScreen<Boolean>) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            val ok = PassesClient.updatePass(
                passId = pass.id,
                name = name.value,
                issuer = issuer.value,
                date = date.value,
                endDate = endDate.value,
                startTime = startTime.value,
                endTime = endTime.value,
                location = location.value,
                notes = notes.value,
            )
            busy.value = false
            if (ok) screen.goBack(false)
            // On a failed save stay on the editor; the user can submit again.
        }
    }

    /** Deletes codes via the delete screen — per-code rows with an X, plus a
     *  bottom-bar DELETE ALL (feedback 2026-08-24). It reports `true` back when
     *  the whole pass was deleted (pop everything) or `false` when individual
     *  codes were removed (pop back to the details panel, which refreshes); a
     *  dismissed delete changes nothing. */
    fun delete(screen: SimpleLightScreen<Boolean>) {
        if (busy.value) return
        screen.navigateTo(screenFactory = {
            DeleteConfirmScreen(it, pass)
        }) { deleted ->
            if (deleted != null) screen.goBack(deleted)
        }
    }
}

class EditScreen(
    sealedActivity: SealedLightActivity,
    private val pass: Pass,
) : LightScreen<Boolean, EditViewModel>(sealedActivity) {

    override val viewModelClass: Class<EditViewModel>
        get() = EditViewModel::class.java

    override fun createViewModel(): EditViewModel = EditViewModel(pass)

    /** The form's scroll offset when a field/picker was tapped; restored on
     *  return (feedback 2026-08-24: entering text should land back where the
     *  edited row was). Captured at tap time (the rows live in [Content]'s
     *  scroll state), then reported through [pendingScroll] when the editor
     *  round-trips — the screen instance survives the trip, the scroll state
     *  does not. */
    private var lastTappedOffset = 0

    /** One-shot scroll-restore signal: a non-null value scrolls the form back
     *  to that offset after a field editor or picker returns. */
    private val pendingScroll = MutableStateFlow<Int?>(null)

    @Composable
    override fun Content() {
        val name by viewModel.name.collectAsState()
        val issuer by viewModel.issuer.collectAsState()
        val date by viewModel.date.collectAsState()
        val endDate by viewModel.endDate.collectAsState()
        val startTime by viewModel.startTime.collectAsState()
        val endTime by viewModel.endTime.collectAsState()
        val location by viewModel.location.collectAsState()
        val notes by viewModel.notes.collectAsState()
        val themeColors by LightThemeController.colors.collectAsState()
        val title = "Edit Pass"

        val scrollState = rememberScrollState()
        val pending by pendingScroll.collectAsState()
        LaunchedEffect(pending) {
            val offset = pending ?: return@LaunchedEffect
            pendingScroll.value = null
            if (offset > 0) scrollState.scrollTo(offset)
        }
        // Capture the form's offset at tap time ([scrollOffset]/[lastTappedOffset])
        // so a returning editor/picker can scroll back to the row that opened it.
        val tap = { lastTappedOffset = scrollState.value }

        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                // No back button — dismissal without saving is the bottom-bar
                // X (feedback 2026-08-24).
                LightTopBar(center = LightTopBarCenter.Text(text = title))
                Box(modifier = Modifier.weight(1f)) {
                    LightScrollView(scrollState = scrollState) {
                        // The name needs no label — it's the pass's own name, the first row of
                        // the form (feedback 2026-08-25).
                        EditFieldRow(
                            label = null,
                            value = name,
                            placeholder = "Add Name",
                            onClick = { tap(); editField("Edit Name", viewModel.name, codeEntry = true) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2f.gridUnitsAsDp(), vertical = 0.75f.gridUnitsAsDp()),
                        )
                        EditFieldRow(
                            label = "Issuer",
                            value = issuer,
                            placeholder = "Add Issuer",
                            onClick = { tap(); editField("Edit Issuer", viewModel.issuer, codeEntry = true) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2f.gridUnitsAsDp(), vertical = 0.75f.gridUnitsAsDp()),
                        )
                        // Date + End date share one row (feedback 2026-08-24),
                        // like Start time + End time below.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2f.gridUnitsAsDp(), vertical = 0.75f.gridUnitsAsDp()),
                        ) {
                            EditFieldRow(
                                label = "Start Date",
                                value = date,
                                placeholder = "Add Date",
                                onClick = { tap(); pickDate() },
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(1f.gridUnitsAsDp()))
                            EditFieldRow(
                                label = "End Date",
                                value = endDate,
                                placeholder = "Add Date",
                                onClick = { tap(); pickEndDate() },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2f.gridUnitsAsDp(), vertical = 0.75f.gridUnitsAsDp()),
                        ) {
                            EditFieldRow(
                                label = "Start Time",
                                value = startTime,
                                placeholder = "Add Time",
                                onClick = { tap(); pickTime("Start Time", viewModel.startTime) },
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(1f.gridUnitsAsDp()))
                            EditFieldRow(
                                label = "End Time",
                                value = endTime,
                                placeholder = "Add Time",
                                onClick = { tap(); pickTime("End Time", viewModel.endTime) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        EditFieldRow(
                            label = "Location",
                            value = location,
                            placeholder = "Add Location",
                            onClick = { tap(); editField("Edit Location", viewModel.location, codeEntry = true) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2f.gridUnitsAsDp(), vertical = 0.75f.gridUnitsAsDp()),
                        )
                        EditFieldRow(
                            label = "Notes",
                            value = notes,
                            placeholder = "Add Notes",
                            onClick = { tap(); editField("Edit Notes", viewModel.notes, singleLine = false, emojis = true) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2f.gridUnitsAsDp(), vertical = 0.75f.gridUnitsAsDp()),
                        )
                    }
                }
                // Corner actions (calendar EventForm grammar): DELETE bottom-left
                // (opens the per-code delete screen — see [EditViewModel.delete]),
                // SAVE bottom-right, and an X centered for dismissing without
                // saving (no top-bar back, feedback 2026-08-24).
                LightBottomBar(
                    modifier = Modifier.navigationBarsPadding(),
                    items = listOf(
                        LightBarButton.Text(
                            text = "DELETE",
                            onClick = { viewModel.delete(this@EditScreen) },
                        ),
                        LightBarButton.LightIcon(
                            icon = LightIcons.CLOSE,
                            onClick = { goBack() },
                            contentDescription = "Close without saving",
                        ),
                        LightBarButton.Text(
                            text = "SAVE",
                            onClick = { viewModel.save(this@EditScreen) },
                        ),
                    ),
                )
            }
        }
    }

    /** The form's scroll offset when a field/picker was tapped; restored on a
     *  successful return. Captured at tap time from [Content]'s scroll state. */
    private fun scrollOffset(): Int = lastTappedOffset

    /** Opens the LP3 keyboard editor for one field; the trimmed result (or "")
     *  replaces the field, an explicit back keeps the old value. [codeEntry]
     *  switches the field to the code-entry editor style (larger, vertically
     *  centered input text); [emojis] adds the keyboard's emoji key (Notes only). */
    private fun editField(
        title: String,
        field: MutableStateFlow<String>,
        singleLine: Boolean = true,
        codeEntry: Boolean = false,
        emojis: Boolean = false,
    ) {
        val restoreOffset = scrollOffset()
        navigateTo(screenFactory = {
            FieldEditorScreen(it, title, field.value, singleLine, codeEntry, emojis)
        }) { value ->
            if (value != null) {
                field.value = value
                pendingScroll.value = restoreOffset
            }
        }
    }

    /** Opens the calendar-style date picker; a picked date (or a clear) replaces
     *  the field, back keeps the old value. */
    private fun pickDate() {
        val restoreOffset = scrollOffset()
        navigateTo(screenFactory = {
            DatePickerScreen(it, viewModel.date.value)
        }) { value ->
            if (value != null) {
                viewModel.date.value = value
                pendingScroll.value = restoreOffset
            }
        }
    }

    /** Opens the date picker for the optional end date. */
    private fun pickEndDate() {
        val restoreOffset = scrollOffset()
        navigateTo(screenFactory = {
            DatePickerScreen(it, viewModel.endDate.value)
        }) { value ->
            if (value != null) {
                viewModel.endDate.value = value
                pendingScroll.value = restoreOffset
            }
        }
    }

    /** Opens the time picker (hour/minute columns); a saved time (or a clear)
     *  replaces the field, back keeps the old value. */
    private fun pickTime(title: String, field: MutableStateFlow<String>) {
        val restoreOffset = scrollOffset()
        navigateTo(screenFactory = {
            TimePickerScreen(it, title, field.value)
        }) { value ->
            if (value != null) {
                field.value = value
                pendingScroll.value = restoreOffset
            }
        }
    }
}

/** A label + current value (or "Add…" placeholder), tapped to open the editor.
 *  The value is underlined with the ~2dp text-field underline (the radio search
 *  bar's standard — the LP3's input underline, thinner than the selection
 *  underline) so the row reads as editable input (feedback 2026-08-24); the
 *  placeholder renders in full white, same as a value (feedback 2026-08-25).
 *  The air between the label and the value is twice the air between the value
 *  and the underline (feedback 2026-08-25). */
@Composable
private fun EditFieldRow(
    label: String?,
    value: String,
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .lightClickable(onClick = onClick),
    ) {
        if (label != null) {
            LightText(label, variant = LightTextVariant.Superfine)
        }
        LightText(
            text = value.ifEmpty { placeholder },
            variant = LightTextVariant.Copy,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 0.5f.gridUnitsAsDp()),
        )
        // The full-width input underline — the LP3 text-field underline
        // (feedback 2026-08-25: the line must span the whole row, not just the
        // text). The air between the label and the value is twice the air
        // between the value and the underline (feedback 2026-08-25).
        Spacer(Modifier.height(0.25f.gridUnitsAsDp()))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(LightThemeTokens.colors.content),
        )
    }
}

/**
 * The LP3 keyboard editor for a single pass field. Name, issuer, and location
 * use the code-entry editor style (input text larger and vertically centered —
 * the same editor, feedback 2026-08-24); Notes uses the Notes-compose style
 * (small text bottom-anchored, growing upward). SAVE sits centered below the
 * keyboard (feedback 2026-08-25 — was bottom-right). The keyboard shows no mic
 * key anywhere and no emoji key except on the Notes field ([emojis] — Notes
 * keeps its return key too, giving new lines; the single-line fields submit on
 * return).
 * Result: the edited text (trimmed; "" clears the field).
 */
class FieldEditorScreen(
    sealedActivity: SealedLightActivity,
    private val title: String,
    private val initial: String,
    private val singleLine: Boolean,
    private val codeEntry: Boolean,
    private val emojis: Boolean,
) : SimpleLightScreen<String>(sealedActivity) {

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        // Fixed options — no remote fetch, so the mic/emoji keys stay off even
        // when the platform server would enable them. `displayReturn` is tied
        // to `singleLine`: the field submits on return when single-line, so
        // the keyboard hides the key rather than show a useless one.
        val keyboardOptionsFlow = remember {
            MutableStateFlow(
                KeyboardOptions(
                    emojis = if (emojis) defaultEmojis else emptyList(),
                    displayReturn = !singleLine,
                    displayVoice = false,
                    enableKeyAnimation = true,
                    swipeEnabled = false,
                ),
            )
        }
        val inputStyle = if (codeEntry) {
            LightThemeTokens.typography.heading
                .copy(color = LightThemeTokens.colors.content)
                .scaledForScreenHeight()
        } else {
            null
        }
        val textState = rememberTextFieldState(initial)

        LightTheme(colors = themeColors) {
            LightTextInputEditor(
                title = title,
                state = textState,
                keyboardOptionsFlow = keyboardOptionsFlow,
                onSubmit = { result -> goBack(result.toString().trim()) },
                onBack = { goBack() },
                modifier = Modifier.background(LightThemeTokens.colors.background),
                submitLabel = "SAVE",
                // SAVE sits centered below the keyboard (feedback 2026-08-25 —
                // was bottom-right).
                bottomAligned = !codeEntry,
                centered = codeEntry,
                singleLine = singleLine,
                inputTextStyle = inputStyle,
            )
        }
    }
}
