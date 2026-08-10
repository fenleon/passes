package com.lightphone.passes.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.lightphone.passes.Pass
import com.lightphone.passes.PassesClient
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class DetailViewModel(private val passId: String, initialPass: Pass) :
    LightViewModel<Unit>() {

    val pass = MutableStateFlow(initialPass)
    val barcode = MutableStateFlow<ImageBitmap?>(null)
    val barcodeError = MutableStateFlow(false)
    val loading = MutableStateFlow(true)

    /** Re-reads the pass (fresh name after a rename) and re-renders the barcode
     *  at the display width (the screen triggers it on every show). */
    fun refresh(widthPx: Int) {
        viewModelScope.launch {
            val fresh = PassesClient.getPasses().find { it.id == passId }
            if (fresh != null) pass.value = fresh
            loadBarcode(widthPx)
        }
    }

    fun loadBarcode(widthPx: Int) {
        viewModelScope.launch {
            barcode.value = null
            barcodeError.value = false
            loading.value = true
            val png = PassesClient.barcodePng(passId, widthPx)
            val bitmap = png?.let {
                BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap()
            }
            loading.value = false
            if (bitmap == null) {
                barcodeError.value = true
            } else {
                barcode.value = bitmap
            }
        }
    }

    fun remove(screen: SimpleLightScreen<Unit>) {
        viewModelScope.launch {
            PassesClient.deletePass(passId)
            screen.goBack()
        }
    }
}

class DetailScreen(
    sealedActivity: SealedLightActivity,
    private val pass: Pass,
) : LightScreen<Unit, DetailViewModel>(sealedActivity) {

    override val viewModelClass: Class<DetailViewModel>
        get() = DetailViewModel::class.java

    override fun createViewModel(): DetailViewModel = DetailViewModel(pass.id, pass)

    @Composable
    override fun Content() {
        val pass by viewModel.pass.collectAsState()
        val barcode by viewModel.barcode.collectAsState()
        val barcodeError by viewModel.barcodeError.collectAsState()
        val loading by viewModel.loading.collectAsState()
        val themeColors by LightThemeController.colors.collectAsState()

        // Render the code at the screen's actual pixel width so modules are never
        // rescaled. Re-runs on every show (fresh composition).
        val configuration = LocalConfiguration.current
        val density = LocalDensity.current
        val targetWidthPx = remember(configuration, density) {
            (configuration.screenWidthDp * density.density).toInt()
        }
        LaunchedEffect(Unit) { viewModel.refresh(targetWidthPx) }

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
                        contentDescription = "Back to Passes",
                    ),
                    center = LightTopBarCenter.Text(
                        text = pass.name,
                        onClick = { rename(targetWidthPx) },
                    ),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .lightClickable(
                            enabled = barcode != null,
                            onClick = { openFullscreen(barcode) },
                        ),
                ) {
                    when {
                        loading && barcode == null -> StatusText("Generating…")
                        barcodeError -> StatusText("Unable to render this barcode.")
                        barcode != null -> BarcodeView(bitmap = barcode!!)
                    }
                }
                LightBottomBar(
                    modifier = Modifier.navigationBarsPadding(),
                    items = listOf(
                        null,
                        LightBarButton.Text(
                            text = "REMOVE",
                            onClick = { viewModel.remove(this@DetailScreen) },
                        ),
                        null,
                    ),
                )
            }
        }
    }

    /** Tapping the pass name opens the rename editor. */
    private fun rename(widthPx: Int) {
        val current = viewModel.pass.value
        navigateTo(
            screenFactory = { NameScreen(it, NameMode.Rename(current.id, current.name)) },
        ) { saved -> if (saved == true) viewModel.refresh(widthPx) }
    }

    /** Tapping the code opens it full-screen (back button only). */
    private fun openFullscreen(bitmap: ImageBitmap?) {
        if (bitmap == null) return
        val current = viewModel.pass.value
        navigateTo(screenFactory = { FullscreenBarcodeScreen(it, current, bitmap) })
    }
}

/** The barcode on a white card, fitted to the screen. White is required so the
 *  code stays scannable; the card hugs the code rather than filling the screen. */
@Composable
private fun BarcodeView(bitmap: ImageBitmap) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 2f.gridUnitsAsDp()),
        contentAlignment = Alignment.Center,
    ) {
        val maxCodeWidth = (maxWidth - 8f.gridUnitsAsDp()).value
        val maxCodeHeight = (maxHeight - 8f.gridUnitsAsDp()).value
        if (maxCodeWidth > 0 && maxCodeHeight > 0) {
            val scale = minOf(
                maxCodeWidth / bitmap.width,
                maxCodeHeight / bitmap.height,
            )
            val size = DpSize(
                width = Dp(bitmap.width * scale),
                height = Dp(bitmap.height * scale),
            )
            Box(
                modifier = Modifier.background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "Barcode",
                    modifier = Modifier
                        .padding(1f.gridUnitsAsDp())
                        .size(size),
                )
            }
        }
    }
}

@Composable
private fun StatusText(text: String) {
    LightText(
        text = text,
        variant = LightTextVariant.Copy,
        lighten = true,
        modifier = Modifier.padding(24.dp),
    )
}
