package com.lightphone.passes.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewModelScope
import com.lightphone.passes.Pass
import com.lightphone.passes.PassesClient
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * The details panel: the pass's name and the fields that are filled (issuer,
 * date, end date, time, location, notes) shown as **plain data without field
 * tags** (feedback 2026-08-30) — shared by all the pass's stacked codes. EDIT
 * sits in the top bar's right slot (the only edit entry point). Reached by
 * tapping the code on the fullscreen (feedback 2026-08-30).
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
                    // No center title — the pass name is the content heading
                    // under the bar (feedback 2026-08-24), so the bar is just
                    // back + EDIT.
                    center = null,
                    // EDIT — the only edit entry point, top-right, in the same
                    // size as a bottom-bar text action (feedback 2026-08-24).
                    textVariant = LightTextVariant.Button,
                    rightButton = LightBarButton.Text(
                        text = "EDIT",
                        onClick = { openEdit() },
                    ),
                )
                Box(modifier = Modifier.weight(1f)) {
                    LightScrollView {
                        LightText(
                            text = pass.name,
                            variant = LightTextVariant.Heading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 3f.gridUnitsAsDp(), vertical = 1.5f.gridUnitsAsDp()),
                        )
                        FilledDetails(pass)
                    }
                }
            }
        }
    }

    /** The top-right EDIT opens the edit panel (which only saves — deletion
     *  lives on the code fullscreen's bottom-left since 2026-08-30). A saved
     *  edit refreshes this panel onto the new details. */
    private fun openEdit() {
        navigateTo(screenFactory = {
            EditScreen(it, viewModel.pass.value)
        }) { viewModel.refresh() }
    }
}

/**
 * The pass's detail values — only fields that are filled, shown as plain data
 * with **no field tags** (feedback 2026-08-30: the "Issuer"/"Date" labels were
 * noise — the values just sit under the name). Start date and start time
 * always pair on one line, end date and end time always pair on one line; the
 * exception is start date + start/end time with no end date, which shows the
 * date on one line and the time range below (feedback 2026-08-30). All text is
 * white (content color).
 */
@Composable
private fun FilledDetails(pass: Pass) {
    val sd = pass.date?.takeIf { it.isNotBlank() }
    val st = pass.startTime?.takeIf { it.isNotBlank() }
    val ed = pass.endDate?.takeIf { it.isNotBlank() }
    val et = pass.endTime?.takeIf { it.isNotBlank() }
    // Pairing rules (feedback 2026-08-30): start date+time on one line, end
    // date+time on one line; a lone time pair is its own line; no end date
    // with both times = date line + "st – et" range line.
    val dateTimeRows = when {
        sd == null && ed == null -> listOfNotNull(st, et).joinToString(" – ")
            .takeIf { it.isNotEmpty() }?.let { listOf(it) } ?: emptyList()
        sd == null -> listOf(listOfNotNull(ed, et).joinToString(", "))
        ed == null -> when {
            st != null && et != null -> listOf(sd, "$st – $et")
            else -> listOf(listOfNotNull(sd, st).joinToString(", "))
        }
        st == null && et == null -> listOf("$sd – $ed")
        else -> listOf(
            listOfNotNull(sd, st).joinToString(", "),
            listOfNotNull(ed, et).joinToString(", "),
        )
    }
    val rows = buildList {
        pass.issuer?.takeIf { it.isNotBlank() }?.let { add(it) }
        addAll(dateTimeRows)
        pass.location?.takeIf { it.isNotBlank() }?.let { add(it) }
        pass.notes?.takeIf { it.isNotBlank() }?.let { add(it) }
        // The decoded payload of each stacked code — read-only (it lives in the
        // edit form as neither field nor action), shown last like Paka's "code".
        pass.codes.forEach { code ->
            code.data.takeIf { it.isNotBlank() }?.let { add(it) }
        }
    }
    rows.forEach { value ->
        LightText(
            text = value,
            variant = LightTextVariant.Copy,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 3f.gridUnitsAsDp(), vertical = 0.75f.gridUnitsAsDp()),
        )
    }
}
