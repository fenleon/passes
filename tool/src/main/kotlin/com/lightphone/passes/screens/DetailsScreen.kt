package com.lightphone.passes.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.lightphone.passes.PassesClient
import com.lightphone.passes.Pass
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBarButton
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * The details panel: the fields that are filled (issuer, date, end date,
 * time, location, notes) shown as **plain data without field tags** — shared
 * by all the pass's stacked codes — with the pass name in the top bar
 * (tapping the title edits the name). Reached via the fullscreen's
 * **VIEW DETAILS**. **Tapping a value goes straight into editing it** (the
 * keyboard editor, the date picker, or the time picker; the read-only Code
 * values excepted) — the result persists that one field and leaves the rest of
 * the pass untouched. The top-right **EDIT** opens the full edit form (the
 * only way to fill a field that is still empty).
 */
class DetailsViewModel(private val passId: String, initialPass: Pass) :
    LightViewModel<Boolean>() {

    val pass = MutableStateFlow(initialPass)

    /** Re-reads the pass (fresh details after an edit or a code delete). */
    fun refresh() {
        viewModelScope.launch {
            val fresh = PassesClient.getPasses().find { it.id == passId }
            if (fresh != null) pass.value = fresh
        }
    }

    /** Persists one detail field, keeping every other field of the fresh pass
     *  as-is (trimmed; blank clears, matching the repository's storage rule). */
    fun saveField(field: String, value: String) {
        viewModelScope.launch {
            val fresh = PassesClient.getPasses().find { it.id == passId } ?: return@launch
            val v = value.trim().takeIf { it.isNotEmpty() }
            val updated = when (field) {
                "name" -> fresh.copy(name = value.trim())
                "issuer" -> fresh.copy(issuer = v)
                "date" -> fresh.copy(date = v)
                "endDate" -> fresh.copy(endDate = v)
                "startTime" -> fresh.copy(startTime = v)
                "endTime" -> fresh.copy(endTime = v)
                "location" -> fresh.copy(location = v)
                "notes" -> fresh.copy(notes = v)
                else -> return@launch
            }
            PassesClient.updatePass(
                updated.id,
                updated.name,
                updated.issuer,
                updated.date,
                updated.endDate,
                updated.startTime,
                updated.endTime,
                updated.location,
                updated.notes,
            )
            pass.value = updated
        }
    }
}

class DetailsScreen(
    sealedActivity: SealedLightActivity,
    private val pass: Pass,
) : LightScreen<Boolean, DetailsViewModel>(sealedActivity) {

    override val viewModelClass: Class<DetailsViewModel>
        get() = DetailsViewModel::class.java

    override fun createViewModel(): DetailsViewModel = DetailsViewModel(pass.id, pass)

    @Composable
    override fun Content() {
        val pass by viewModel.pass.collectAsState()
        val themeColors by LightThemeController.colors.collectAsState()

        // Re-runs on every show (fresh composition).
        LaunchedEffect(Unit) { viewModel.refresh() }

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
                    // The pass name in the title; tapping it still edits the
                    // name (feedback 2026-09-21).
                    center = LightTopBarCenter.Text(
                        text = pass.name,
                        onClick = { editField("name") },
                    ),
                    // EDIT opens the full edit form — the only way to fill a
                    // field that is still empty (tapping values edits what is
                    // visible).
                    textVariant = LightTextVariant.Button,
                    rightButton = LightBarButton.Text(
                        text = "EDIT",
                        onClick = { openEdit() },
                    ),
                )
                Box(modifier = Modifier.weight(1f)) {
                    LightScrollView {
                        FilledDetails(pass) { editField(it) }
                    }
                }
            }
        }
    }

    /** The top-right EDIT opens the edit panel (which only saves — deletion
     *  lives on the code fullscreen's bottom-left). A saved edit refreshes
     *  this panel onto the new details. */
    private fun openEdit() {
        navigateTo(screenFactory = {
            EditScreen(it, viewModel.pass.value)
        }) { viewModel.refresh() }
    }

    /** Tapping a detail value opens its editor directly; the result persists
     *  that one field (a dismissed editor changes nothing). */
    private fun editField(field: String) {
        val pass = viewModel.pass.value
        when (field) {
            "name" -> navigateTo(screenFactory = {
                FieldEditorScreen(it, "Edit Name", pass.name, singleLine = true, codeEntry = true, emojis = false)
            }) { value -> if (value != null) viewModel.saveField("name", value) }

            "issuer" -> navigateTo(screenFactory = {
                FieldEditorScreen(it, "Edit Issuer", pass.issuer.orEmpty(), singleLine = true, codeEntry = true, emojis = false)
            }) { value -> if (value != null) viewModel.saveField("issuer", value) }

            "location" -> navigateTo(screenFactory = {
                FieldEditorScreen(it, "Edit Location", pass.location.orEmpty(), singleLine = true, codeEntry = true, emojis = false)
            }) { value -> if (value != null) viewModel.saveField("location", value) }

            "notes" -> navigateTo(screenFactory = {
                FieldEditorScreen(it, "Edit Notes", pass.notes.orEmpty(), singleLine = false, codeEntry = false, emojis = true)
            }) { value -> if (value != null) viewModel.saveField("notes", value) }

            "date" -> navigateTo(screenFactory = {
                DatePickerScreen(it, pass.date.orEmpty())
            }) { value -> if (value != null) viewModel.saveField("date", value) }

            "endDate" -> navigateTo(screenFactory = {
                DatePickerScreen(it, pass.endDate.orEmpty())
            }) { value -> if (value != null) viewModel.saveField("endDate", value) }

            "startTime" -> navigateTo(screenFactory = {
                TimePickerScreen(it, "Start Time", pass.startTime.orEmpty())
            }) { value -> if (value != null) viewModel.saveField("startTime", value) }

            "endTime" -> navigateTo(screenFactory = {
                TimePickerScreen(it, "End Time", pass.endTime.orEmpty())
            }) { value -> if (value != null) viewModel.saveField("endTime", value) }
        }
    }
}

/** One tappable (or read-only) piece of a details line. A null [field] marks
 *  a read-only value — the decoded Code payloads. */
private class DetailSpan(val text: String, val field: String?)

/** A details line: one [separator] per span gap (" – " for ranges, ", " for
 *  pairs — the joining the panel has always shown). [tight] marks date/time
 *  lines — consecutive tight lines render with a reduced gap (feedback
 *  2026-09-21); [code] marks a decoded code value (a gap above the first one
 *  separates the codes from the fields). */
private class DetailLine(
    val separators: List<String>,
    val spans: List<DetailSpan>,
    val tight: Boolean = false,
    val code: Boolean = false,
)

/**
 * The pass's detail values — only fields that are filled, shown as plain data
 * with **no field tags**, Copy size (feedback 2026-09-21: went Detail, user wanted it back up).
 * Start date and start time always pair on one line, end date and end time on
 * one line; the exception is start date + start/end time with no end date,
 * which shows the date on one line and the time range below. **Equal start
 * and end dates collapse into one line — date, start–end time** (feedback
 * 2026-09-21). Each date and each time on a line is its own tap target.
 */
@Composable
private fun FilledDetails(pass: Pass, onEditField: (String) -> Unit) {
    val sd = pass.date?.takeIf { it.isNotBlank() }
    val st = pass.startTime?.takeIf { it.isNotBlank() }
    val ed = pass.endDate?.takeIf { it.isNotBlank() }
    val et = pass.endTime?.takeIf { it.isNotBlank() }
    fun span(text: String?, field: String) = text?.takeIf { it.isNotBlank() }
        ?.let { DetailSpan(it, field) }
    fun line(vararg spans: DetailSpan?, separator: String = ", ", tight: Boolean = false) =
        spans.filterNotNull().takeIf { it.isNotEmpty() }
            ?.let { DetailLine(listOf(separator), it, tight) }
    fun lineSpans(separators: List<String>, spans: List<DetailSpan>, tight: Boolean = false) =
        DetailLine(separators, spans, tight)
    fun single(text: String, field: String?, code: Boolean = false) =
        DetailLine(listOf(", "), listOf(DetailSpan(text, field)), code = code)

    val lines = buildList {
        pass.issuer?.takeIf { it.isNotBlank() }?.let { add(single(it, "issuer")) }
        // Pairing rules: start date+time on one line, end date+time on one
        // line; a lone time pair is its own line; no end date with both times
        // = date line + "st – et" range line; **equal dates = one line**
        // "date, st – et" (feedback 2026-09-21). Date/time lines are tight.
        if (sd != null && ed != null && sd == ed) {
            when {
                st != null && et != null -> add(
                    lineSpans(listOf(", ", " – "), listOf(span(sd, "date")!!, span(st, "startTime")!!, span(et, "endTime")!!), tight = true),
                )

                else -> line(span(sd, "date"), span(st, "startTime"), span(et, "endTime"), tight = true)
                    ?.let { add(it) }
            }
        } else when {
            sd == null && ed == null -> line(span(st, "startTime"), span(et, "endTime"), separator = " – ", tight = true)
                ?.let { add(it) }

            sd == null -> line(span(ed, "endDate"), span(et, "endTime"), tight = true)
                ?.let { add(it) }

            ed == null -> when {
                st != null && et != null -> add(
                    lineSpans(
                        listOf(", ", " – "),
                        listOf(span(sd, "date")!!, span(st, "startTime")!!, span(et, "endTime")!!),
                        tight = true,
                    ),
                )

                else -> line(span(sd, "date"), span(st, "startTime"), tight = true)
                    ?.let { add(it) }
            }

            st == null && et == null -> line(span(sd, "date"), span(ed, "endDate"), separator = " – ", tight = true)
                ?.let { add(it) }

            else -> {
                line(span(sd, "date"), span(st, "startTime"), tight = true)?.let { add(it) }
                line(span(ed, "endDate"), span(et, "endTime"), tight = true)?.let { add(it) }
            }
        }
        pass.location?.takeIf { it.isNotBlank() }?.let { add(single(it, "location")) }
        pass.notes?.takeIf { it.isNotBlank() }?.let { add(single(it, "notes")) }
        // The decoded payload of each stacked code — read-only (it lives in
        // the edit form as neither field nor action), shown last with a gap
        // above the first one (feedback 2026-09-21).
        pass.codes.forEach { code ->
            code.data.takeIf { it.isNotBlank() }?.let { add(single(it, null, code = true)) }
        }
    }
    lines.forEachIndexed { i, line ->
        val tight = line.tight && i > 0 && lines[i - 1].tight
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = if (line.code && (i == 0 || !lines[i - 1].code)) 1.5f.gridUnitsAsDp() else 0.dp,
                )
                .padding(horizontal = 3f.gridUnitsAsDp(), vertical = (if (tight) 0.25f else 0.75f).gridUnitsAsDp()),
        ) {
            line.spans.forEachIndexed { j, span ->
                if (j > 0) {
                    LightText(text = line.separators[j - 1], variant = LightTextVariant.Copy)
                }
                LightText(
                    text = span.text,
                    variant = LightTextVariant.Copy,
                    modifier = span.field
                        ?.let { f -> Modifier.lightClickable(onClick = { onEditField(f) }) }
                        ?: Modifier,
                )
            }
        }
    }
}
