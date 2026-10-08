package com.lightphone.passes

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test
    fun capturedSymbolRendersWithOneModuleBorder() {
        // 5×5 modules, black at the corners + center (0 = black) so nothing
        // but the quiet ring trims away.
        val symbol = StoredSymbol(
            5, 5,
            ByteArray(25) { 1 }.also {
                it[0] = 0; it[4] = 0; it[20] = 0; it[24] = 0; it[2 * 5 + 2] = 0
            },
        )
        val raster = BarcodeRaster.rasterFromSymbol("aztec", symbol, targetWidthPx = 560)
        assertNotNull(raster)
        val modulePx = 560 / (5 + 2) // 80
        // Content (5 modules) + exactly one border module each side.
        assertEquals(7 * modulePx, raster.width)
        assertEquals(raster.width, raster.height) // square format
        val black = 0xFF000000.toInt()
        val white = 0xFFFFFFFF.toInt()
        // The border ring is white; the corner module starts one module in.
        assertEquals(white, raster.pixels[0])
        assertEquals(black, raster.pixels[modulePx * raster.width + modulePx])
    }

    @Test
    fun capturedLinearSymbolStretchesVertically() {
        // A 1D symbol is a single module row.
        val symbol = StoredSymbol(11, 1, byteArrayOf(0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0))
        val raster = BarcodeRaster.rasterFromSymbol("code128", symbol)
        assertNotNull(raster)
        assertEquals((11 + 2) * (960 / (11 + 2)), raster.width) // content + 1-module border
        assertTrue(raster.height >= 180, "1D render must meet the minimum height")
    }

    @Test
    fun everyFormatRendersWithAWhiteBorder() {
        // Every format shows the uniform 1-module quiet zone: the raster's
        // outermost row/column is white (a code with no border doesn't scan).
        for ((type, data) in samples) {
            val raster = BarcodeRaster.raster(type, data, null)
            assertNotNull(raster, "$type should rasterize + verify")
            val white = 0xFFFFFFFF.toInt()
            val lastRow = (raster.height - 1) * raster.width
            val borderWhite = (0 until raster.width).all { raster.pixels[it] == white } &&
                (0 until raster.width).all { raster.pixels[lastRow + it] == white } &&
                (0 until raster.height).all { raster.pixels[it * raster.width] == white } &&
                (0 until raster.height).all {
                    raster.pixels[it * raster.width + raster.width - 1] == white
                }
            assertTrue(borderWhite, "$type must render with a white border")
        }
    }

    @Test
    fun malformedSymbolIsRefused() {
        assertNull(BarcodeRaster.rasterFromSymbol("aztec", StoredSymbol(5, 5, ByteArray(10))))
        assertNull(BarcodeRaster.rasterFromSymbol("aztec", StoredSymbol(0, 0, ByteArray(0))))
        assertNull(BarcodeRaster.rasterFromSymbol("bogus", StoredSymbol(5, 5, ByteArray(25))))
    }
}
