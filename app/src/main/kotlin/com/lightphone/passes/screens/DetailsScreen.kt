package com.lightphone.passes.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewModelScope
import com.lightphone.passes.Pass
import com.lightphone.passes.PassesClient
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * The details panel: the pass's name and the fields that are filled (issuer,
 * date, end date, time, location, notes) — shared by all the pass's stacked
 * codes. EDIT lives in the bottom bar's center (the only edit entry point).
 * Result: `true` when the whole pass was deleted (the barcode panel pops
 * itself); deleting just one code keeps this panel (it refreshes).
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
    /** Which stacked code was being viewed (anchors the edit title "Edit Pass x of n"). */
    private val currentCodeId: String? = null,
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
                    center = LightTopBarCenter.Text(text = "Details"),
                )
                Box(modifier = Modifier.weight(1f)) {
                    LightScrollView {
                        LightText(
                            text = pass.name,
                            variant = LightTextVariant.Heading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2f.gridUnitsAsDp(), vertical = 1.5f.gridUnitsAsDp()),
                        )
                        FilledDetails(pass)
                    }
                }
                // The only EDIT entry point, centered in the bottom bar.
                LightBottomBar(
                    modifier = Modifier.navigationBarsPadding(),
                    items = listOf(
                        null,
                        LightBarButton.Text(
                            text = "EDIT",
                            onClick = { openEdit() },
                        ),
                        null,
                    ),
                )
            }
        }
    }

    /** The bottom-bar EDIT opens the edit panel (delete lives there too). A
     *  deleted whole pass pops this panel, which pops the barcode panel. */
    private fun openEdit() {
        navigateTo(screenFactory = {
            EditScreen(it, viewModel.pass.value, currentCodeId)
        }) { deleted ->
            if (deleted == true) goBack(true) else viewModel.refresh()
        }
    }
}

/** The pass's detail rows — only fields that are filled. Date and End date
 *  share one row ("Aug 12, 2026 – Aug 20, 2026"), like the time range. */
@Composable
private fun FilledDetails(pass: Pass) {
    val rows = buildList {
        pass.issuer?.takeIf { it.isNotBlank() }?.let { add("Issuer" to it) }
        listOfNotNull(
            pass.date?.takeIf { it.isNotBlank() },
            pass.endDate?.takeIf { it.isNotBlank() },
        ).joinToString(" – ").takeIf { it.isNotEmpty() }?.let { range ->
            // A date range (both fields filled) is labeled "Dates".
            add(
                (if (pass.date.isNullOrBlank() || pass.endDate.isNullOrBlank()) "Date" else "Dates") to range,
            )
        }
        val time = listOfNotNull(
            pass.startTime?.takeIf { it.isNotBlank() },
            pass.endTime?.takeIf { it.isNotBlank() },
        ).joinToString(" – ")
        if (time.isNotEmpty()) add("Time" to time)
        pass.location?.takeIf { it.isNotBlank() }?.let { add("Location" to it) }
        pass.notes?.takeIf { it.isNotBlank() }?.let { add("Notes" to it) }
        // The decoded payload of each stacked code — read-only (it lives in the
        // edit form as neither field nor action), shown last like Paka's "code".
        pass.codes.forEach { code ->
            code.data.takeIf { it.isNotBlank() }?.let { add("Code" to it) }
        }
    }
    rows.forEach { (label, value) ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2f.gridUnitsAsDp(), vertical = 0.75f.gridUnitsAsDp()),
        ) {
            LightText(label, variant = LightTextVariant.Fine, lighten = true)
            LightText(
                text = value,
                variant = LightTextVariant.Copy,
                modifier = Modifier.padding(top = 0.25f.gridUnitsAsDp()),
            )
        }
    }
}
