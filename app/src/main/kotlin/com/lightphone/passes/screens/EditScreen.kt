package com.lightphone.passes.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewModelScope
import com.lightphone.passes.Pass
import com.lightphone.passes.PassesClient
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.rememberKeyboardOptions
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * The edit panel: edits a pass's key details (name, issuer, date, end date,
 * start/end time, location, notes) one field at a time. Text fields (name,
 * issuer, location, notes) edit on the LP3 keyboard in the Notes-compose style
 * (SAVE in the top bar, small bottom-anchored text, keyboard flush at the
 * bottom); Date/End date open the calendar-style date picker, Start/End time
 * open the time picker. Bottom bar: DELETE (deletes **the current code** — the
 * whole pass when it's the last one), SAVE (persists).
 *
 * When the pass is one of a stack, the title reads "Edit Pass x of n" (x is
 * the position of [currentCodeId]).
 *
 * Result: `true` when the whole pass was deleted (the screens above pop
 * themselves), anything else returns to the details panel unchanged.
 */
class EditViewModel(private val pass: Pass, private val currentCodeId: String?) :
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

    /** Deletes the current code only; the whole pass goes when it was the last
     *  code (result `true` pops the details and barcode panels). */
    fun delete(screen: SimpleLightScreen<Boolean>) {
        if (busy.value) return
        val codeId = currentCodeId ?: pass.codes.firstOrNull()?.id ?: return
        viewModelScope.launch {
            busy.value = true
            PassesClient.deleteCode(codeId)
            busy.value = false
            screen.goBack(pass.codes.size <= 1)
        }
    }
}

class EditScreen(
    sealedActivity: SealedLightActivity,
    private val pass: Pass,
    /** The code being viewed when EDIT was opened — anchors the "Edit Pass x of n" title. */
    private val currentCodeId: String? = null,
) : LightScreen<Boolean, EditViewModel>(sealedActivity) {

    override val viewModelClass: Class<EditViewModel>
        get() = EditViewModel::class.java

    override fun createViewModel(): EditViewModel = EditViewModel(pass, currentCodeId)

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

        val codeIndex = pass.codes.indexOfFirst { it.id == currentCodeId }
        val title = if (codeIndex >= 0 && pass.codes.size > 1) {
            "Edit Pass ${codeIndex + 1} of ${pass.codes.size}"
        } else {
            "Edit Pass"
        }

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
                        contentDescription = "Back to ${pass.name}",
                    ),
                    center = LightTopBarCenter.Text(text = title),
                )
                Box(modifier = Modifier.weight(1f)) {
                    LightScrollView {
                        // Name is a labeled row like the others — the label
                        // makes it clear the pass can be renamed.
                        EditFieldRow(
                            label = "Name",
                            value = name,
                            placeholder = "Add name",
                            onClick = { editField("Edit Name", viewModel.name, initialCaps = true) },
                        )
                        EditFieldRow(
                            label = "Issuer",
                            value = issuer,
                            placeholder = "Add issuer",
                            onClick = { editField("Edit Issuer", viewModel.issuer) },
                        )
                        EditFieldRow(
                            label = "Date",
                            value = date,
                            placeholder = "Add date",
                            onClick = { pickDate() },
                        )
                        EditFieldRow(
                            label = "End date",
                            value = endDate,
                            placeholder = "Add end date",
                            onClick = { pickEndDate() },
                        )
                        EditFieldRow(
                            label = "Start time",
                            value = startTime,
                            placeholder = "Add start time",
                            onClick = { pickTime("Start time", viewModel.startTime) },
                        )
                        EditFieldRow(
                            label = "End time",
                            value = endTime,
                            placeholder = "Add end time",
                            onClick = { pickTime("End time", viewModel.endTime) },
                        )
                        EditFieldRow(
                            label = "Location",
                            value = location,
                            placeholder = "Add location",
                            onClick = { editField("Edit Location", viewModel.location) },
                        )
                        EditFieldRow(
                            label = "Notes",
                            value = notes,
                            placeholder = "Add notes",
                            onClick = { editField("Edit Notes", viewModel.notes, singleLine = false) },
                        )
                    }
                }
                // Corner actions (calendar EventForm grammar): DELETE bottom-left,
                // SAVE bottom-right. Dismissal = the top-bar back, no X.
                LightBottomBar(
                    modifier = Modifier.navigationBarsPadding(),
                    items = listOf(
                        LightBarButton.Text(
                            text = "DELETE",
                            onClick = { viewModel.delete(this@EditScreen) },
                        ),
                        null,
                        LightBarButton.Text(
                            text = "SAVE",
                            onClick = { viewModel.save(this@EditScreen) },
                        ),
                    ),
                )
            }
        }
    }

    /** Opens the LP3 keyboard editor for one field; the trimmed result (or "")
     *  replaces the field, an explicit back keeps the old value. */
    private fun editField(
        title: String,
        field: MutableStateFlow<String>,
        singleLine: Boolean = true,
        initialCaps: Boolean = false,
    ) {
        navigateTo(screenFactory = {
            FieldEditorScreen(it, title, field.value, singleLine, initialCaps)
        }) { value -> if (value != null) field.value = value }
    }

    /** Opens the calendar-style date picker; a picked date (or a clear) replaces
     *  the field, back keeps the old value. */
    private fun pickDate() {
        navigateTo(screenFactory = {
            DatePickerScreen(it, viewModel.date.value)
        }) { value -> if (value != null) viewModel.date.value = value }
    }

    /** Opens the date picker for the optional end date. */
    private fun pickEndDate() {
        navigateTo(screenFactory = {
            DatePickerScreen(it, viewModel.endDate.value)
        }) { value -> if (value != null) viewModel.endDate.value = value }
    }

    /** Opens the time picker (hour/minute columns); a saved time (or a clear)
     *  replaces the field, back keeps the old value. */
    private fun pickTime(title: String, field: MutableStateFlow<String>) {
        navigateTo(screenFactory = {
            TimePickerScreen(it, title, field.value)
        }) { value -> if (value != null) field.value = value }
    }
}

/** A label + current value (or "Add…" placeholder), tapped to open the editor. */
@Composable
private fun EditFieldRow(
    label: String,
    value: String,
    placeholder: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .lightClickable(onClick = onClick)
            .padding(horizontal = 2f.gridUnitsAsDp(), vertical = 0.75f.gridUnitsAsDp()),
    ) {
        LightText(label, variant = LightTextVariant.Fine, lighten = true)
        LightText(
            text = value.ifEmpty { placeholder },
            variant = LightTextVariant.Copy,
            lighten = value.isEmpty(),
            modifier = Modifier.padding(top = 0.25f.gridUnitsAsDp()),
        )
    }
}

/**
 * The LP3 keyboard editor for a single pass field, in the Notes-compose style:
 * SAVE in the top bar (no bottom bar, so the keyboard sits flush at the
 * bottom), and the text itself small and bottom-anchored, growing upward as the
 * user types — the same shape as the notes composer. Result: the edited text
 * (trimmed; "" clears the field).
 */
class FieldEditorScreen(
    sealedActivity: SealedLightActivity,
    private val title: String,
    private val initial: String,
    private val singleLine: Boolean,
    private val initialCaps: Boolean,
) : SimpleLightScreen<String>(sealedActivity) {

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val keyboardOptionsFlow = rememberKeyboardOptions()
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
                submitInTopBar = true,
                topBarSubmitLabel = "SAVE",
                bottomAligned = true,
                submitOnReturn = singleLine,
                singleLine = singleLine,
                initialCaps = initialCaps,
            )
        }
    }
}
