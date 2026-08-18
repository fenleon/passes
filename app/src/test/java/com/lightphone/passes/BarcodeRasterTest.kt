package com.lightphone.passes

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Host-side coverage of the render-and-verify pipeline: every supported format
 * must rasterize at display width AND decode back to its payload (verification
 * is part of [BarcodeRaster.raster]); refusal cases must return null.
 */
class BarcodeRasterTest {

    private val samples = mapOf(
        "qr" to "https://example.com/boarding-pass",
        "aztec" to "ABC123",
        "ean13" to "590123412345",
        "ean8" to "9638507",
        "pdf417" to "PDF417 sample payload",
        "upc_e" to "0425261",
        "datamatrix" to "Data Matrix 123",
        "code39" to "CODE39-123",
        "code93" to "CODE93-123",
        "itf14" to "12345678901234",
        "codabar" to "40156",
        "code128" to "CODE128-ABC-123",
        "upc_a" to "03600029145",
    )

    @Test
    fun everyFormatRendersAndVerifiesAtDisplayWidth() {
        for ((type, data) in samples) {
            val raster = BarcodeRaster.raster(type, data, null, targetWidthPx = 1079)
            assertNotNull(raster, "$type should rasterize + verify")
        }
    }

    @Test
    fun everyFormatRendersAndVerifiesAtDefaultWidth() {
        for ((type, data) in samples) {
            val raster = BarcodeRaster.raster(type, data, null)
            assertNotNull(raster, "$type should rasterize + verify at default width")
        }
    }

    @Test
    fun eanAndUpcAcceptCheckDigitForms() {
        // 13-digit EAN-13 (check digit included) must still verify.
        assertNotNull(BarcodeRaster.raster("ean13", "5901234123457", null))
        assertNotNull(BarcodeRaster.raster("ean8", "96385074", null))
        assertNotNull(BarcodeRaster.raster("upc_a", "036000291452", null))
        assertNotNull(BarcodeRaster.raster("upc_e", "04252614", null))
    }

    @Test
    fun codabarWithSentinelsRenders() {
        assertNotNull(BarcodeRaster.raster("codabar", "A40156B", null))
    }

    @Test
    fun binaryAztecPayloadVerifiesAsBytes() {
        // "café" as Latin-1 bytes, base64 — the rawData path for binary payloads.
        val rawData = Base64.getEncoder()
            .encodeToString("café".toByteArray(Charsets.ISO_8859_1))
        assertNotNull(BarcodeRaster.raster("aztec", "", rawData))
    }

    @Test
    fun nonLatin1AztecAndPdf417AreRefused() {
        assertNull(BarcodeRaster.raster("aztec", "日本語", null))
        assertNull(BarcodeRaster.raster("pdf417", "日本語", null))
    }

    @Test
    fun blankDataIsRefused() {
        assertNull(BarcodeRaster.raster("qr", "", null))
        assertNull(BarcodeRaster.raster("code128", "   ", null))
    }

    @Test
    fun unknownTypeIsRefused() {
        assertNull(BarcodeRaster.raster("bogus", "data", null))
    }

    @Test
    fun qrWithLongPayloadStillRenders() {
        val longUrl = "https://example.com/ticket?id=" + "x".repeat(200)
        assertNotNull(BarcodeRaster.raster("qr", longUrl, null))
    }
}
