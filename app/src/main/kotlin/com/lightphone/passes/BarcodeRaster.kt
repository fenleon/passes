package com.lightphone.passes

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.MultiFormatWriter
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.Base64

/** A black-on-white pixel grid ready to become a bitmap. */
data class Raster(val width: Int, val height: Int, val pixels: IntArray)

/**
 * Pure-JVM barcode rasterization + self-verification (no Android types), so the
 * pipeline is unit-testable on the host.
 *
 * Rasterizes at a target display width snapped to an exact integer multiple of
 * the module count — modules are never smoothly rescaled. Every raster is then
 * decoded back and checked against its payload; an unchecked symbol is refused.
 */
object BarcodeRaster {

    private val FORMATS: Map<String, BarcodeFormat> = mapOf(
        "qr" to BarcodeFormat.QR_CODE,
        "aztec" to BarcodeFormat.AZTEC,
        "ean13" to BarcodeFormat.EAN_13,
        "ean8" to BarcodeFormat.EAN_8,
        "pdf417" to BarcodeFormat.PDF_417,
        "upc_e" to BarcodeFormat.UPC_E,
        "datamatrix" to BarcodeFormat.DATA_MATRIX,
        "code39" to BarcodeFormat.CODE_39,
        "code93" to BarcodeFormat.CODE_93,
        "itf14" to BarcodeFormat.ITF,
        "codabar" to BarcodeFormat.CODABAR,
        "code128" to BarcodeFormat.CODE_128,
        "upc_a" to BarcodeFormat.UPC_A,
    )

    private const val MIN_TARGET_WIDTH_PX = 240
    private const val MAX_RENDER_PX = 2160
    private const val QR_MARGIN_MODULES = 4
    private const val COMPACT_MATRIX_MARGIN_MODULES = 2
    private const val LINEAR_MARGIN_MODULES = 10
    private const val QUIET_ZONE_SIDE_COUNT = 2
    private const val MIN_1D_HEIGHT_PX = 180
    private const val BLACK_PIXEL = 0xFF000000.toInt()
    private const val WHITE_PIXEL = 0xFFFFFFFF.toInt()

    private val SQUARE_FORMATS = setOf(BarcodeFormat.QR_CODE, BarcodeFormat.AZTEC)
    private val LINEAR_ZXING_FORMATS = setOf(
        BarcodeFormat.CODE_128,
        BarcodeFormat.CODE_39,
        BarcodeFormat.CODE_93,
        BarcodeFormat.CODABAR,
        BarcodeFormat.EAN_13,
        BarcodeFormat.EAN_8,
        BarcodeFormat.UPC_A,
        BarcodeFormat.UPC_E,
        BarcodeFormat.ITF,
    )

    fun formatFor(type: String): BarcodeFormat? = FORMATS[type.lowercase()]

    /**
     * Rasterizes [data] (or the decoded [rawData] binary payload when present) at
     * [targetWidthPx]. Returns null when the type is unknown, the payload can't be
     * encoded, the symbol can't fit at one pixel per module, or the pixels fail
     * payload verification.
     */
    fun raster(
        type: String,
        data: String,
        rawData: String?,
        targetWidthPx: Int = 960,
    ): Raster? {
        val format = formatFor(type) ?: return null
        val text = rawData?.let(::decodeRawData) ?: data
        if (text.isBlank()) return null
        val width = targetWidthPx.coerceIn(MIN_TARGET_WIDTH_PX, MAX_RENDER_PX)
        return renderRaster(format, text, width)
    }

    /**
     * Rasterizes a captured symbol (from the camera scanner) at [targetWidthPx]
     * with a 1-module quiet zone on each side — the exact original symbol, not
     * a re-encode. Returns null when the type is unknown, the symbol is
     * malformed, or it can't fit at one pixel per module.
     */
    fun rasterFromSymbol(
        type: String,
        symbol: StoredSymbol,
        targetWidthPx: Int = 960,
    ): Raster? {
        val format = formatFor(type) ?: return null
        if (symbol.width <= 0 || symbol.height <= 0 ||
            symbol.data.size < symbol.width * symbol.height
        ) {
            return null
        }
        val width = targetWidthPx.coerceIn(MIN_TARGET_WIDTH_PX, MAX_RENDER_PX)
        // One module of quiet zone each side (matches the airline renders and
        // Binary Eye's share output).
        val modulePx = width / (symbol.width + QUIET_ZONE_SIDE_COUNT)
        if (modulePx < 1) return null
        val renderWidth = (symbol.width + QUIET_ZONE_SIDE_COUNT) * modulePx
        val renderHeight = when {
            format in SQUARE_FORMATS -> renderWidth
            format == BarcodeFormat.DATA_MATRIX || format == BarcodeFormat.PDF_417 ->
                (symbol.height + QUIET_ZONE_SIDE_COUNT) * modulePx
            // 1D symbols are a single module row; stretch vertically like the
            // re-encode path.
            else -> (renderWidth * 0.30f).toInt().coerceAtLeast(MIN_1D_HEIGHT_PX)
        }
        val stretchVertically = format !in SQUARE_FORMATS &&
            format != BarcodeFormat.DATA_MATRIX &&
            format != BarcodeFormat.PDF_417
        val pixels = IntArray(renderWidth * renderHeight) { index ->
            val x = index % renderWidth
            val y = index / renderWidth
            val rawX = x / modulePx - 1 // minus the quiet-zone column
            val rawY = if (stretchVertically) {
                y * symbol.height / renderHeight
            } else {
                y / modulePx - 1 // minus the quiet-zone row
            }
            val quiet = rawX < 0 || rawX >= symbol.width ||
                (!stretchVertically && (rawY < 0 || rawY >= symbol.height))
            if (quiet) {
                WHITE_PIXEL
            } else {
                // Scanner convention: 0 = black module.
                if (symbol.data[rawY * symbol.width + rawX] == 0.toByte()) {
                    BLACK_PIXEL
                } else {
                    WHITE_PIXEL
                }
            }
        }
        return Raster(renderWidth, renderHeight, pixels)
    }

    private fun renderRaster(format: BarcodeFormat, text: String, targetWidth: Int): Raster? {
        // Aztec/PDF417 encode bytes 1:1 (ISO-8859-1); refuse non-Latin-1 payloads
        // rather than let the encoder garble them.
        if ((format == BarcodeFormat.AZTEC || format == BarcodeFormat.PDF_417) &&
            !Charsets.ISO_8859_1.newEncoder().canEncode(text)
        ) {
            return null
        }

        val content = if (format == BarcodeFormat.CODABAR) {
            // ZXing requires (and readers expect) start/stop sentinels; add them
            // when the stored value doesn't carry them (mirrors the original app).
            withCodabarSentinels(text)
        } else {
            text
        }

        val matrix = try {
            encodeMatrix(format, content)
        } catch (e: WriterException) {
            return null
        } catch (e: IllegalArgumentException) {
            return null
        }

        // Snap to an exact integer multiple of the module count; refuse symbols
        // that cannot fit at one pixel per module (they would be rescaled).
        val modulePx = targetWidth / matrix.width
        if (modulePx < 1) return null
        val width = matrix.width * modulePx
        val height = when {
            format in SQUARE_FORMATS -> width
            format == BarcodeFormat.DATA_MATRIX || format == BarcodeFormat.PDF_417 ->
                matrix.height * modulePx
            else -> (width * 0.30f).toInt().coerceAtLeast(MIN_1D_HEIGHT_PX)
        }

        val pixels = renderPixels(matrix, width, height, format)
        if (!verify(width, height, pixels, format, text, content)) return null
        return Raster(width, height, pixels)
    }

    /** Encoder hints: per-format margins + charsets, QR error correction M. */
    private fun encodeMatrix(format: BarcodeFormat, content: String): BitMatrix {
        val hints = HashMap<EncodeHintType, Any>()
        hints[EncodeHintType.MARGIN] = when (format) {
            BarcodeFormat.QR_CODE, BarcodeFormat.PDF_417 -> QR_MARGIN_MODULES
            // AztecWriter/DataMatrixWriter ignore MARGIN; the quiet zone is
            // added explicitly below.
            BarcodeFormat.AZTEC, BarcodeFormat.DATA_MATRIX -> COMPACT_MATRIX_MARGIN_MODULES
            // OneDimensionalCodeWriter treats MARGIN as a total allowance, not a
            // per-side quiet zone; pad linear symbols explicitly below.
            else -> 0
        }
        // Binary payloads (e.g. a KlimaTicket Aztec) map bytes 1:1 via Latin-1.
        when (format) {
            BarcodeFormat.AZTEC, BarcodeFormat.PDF_417 ->
                hints[EncodeHintType.CHARACTER_SET] = "ISO-8859-1"
            BarcodeFormat.QR_CODE, BarcodeFormat.DATA_MATRIX ->
                hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
            else -> Unit
        }
        if (format == BarcodeFormat.QR_CODE) {
            hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.M
        }

        // Natural size (1px per module); the bitmap is then snapped to an exact
        // integer module grid.
        val raw = MultiFormatWriter().encode(content, format, 0, 0, hints)
        return when {
            format == BarcodeFormat.AZTEC || format == BarcodeFormat.DATA_MATRIX ->
                raw.withQuietZone(COMPACT_MATRIX_MARGIN_MODULES)
            format in LINEAR_ZXING_FORMATS -> raw.withHorizontalQuietZone(LINEAR_MARGIN_MODULES)
            else -> raw
        }
    }

    private fun renderPixels(
        matrix: BitMatrix,
        width: Int,
        height: Int,
        format: BarcodeFormat,
    ): IntArray {
        val modulePx = width / matrix.width
        val stretchVertically = format !in SQUARE_FORMATS &&
            format != BarcodeFormat.DATA_MATRIX &&
            format != BarcodeFormat.PDF_417
        return IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            val rawX = x / modulePx
            val rawY = if (stretchVertically) y * matrix.height / height else y / modulePx
            if (matrix.get(rawX, rawY)) BLACK_PIXEL else WHITE_PIXEL
        }
    }

    /**
     * Decodes the rendered pixels again and checks the payload. Format-aware:
     * EAN/UPC check digits, Codabar sentinels, Aztec/PDF417 compared as bytes.
     */
    private fun verify(
        width: Int,
        height: Int,
        pixels: IntArray,
        format: BarcodeFormat,
        original: String,
        content: String,
    ): Boolean = runCatching {
        val source = RGBLuminanceSource(width, height, pixels)
        val hints = mutableMapOf<DecodeHintType, Any>(
            DecodeHintType.TRY_HARDER to true,
            DecodeHintType.POSSIBLE_FORMATS to possibleFormats(format),
        )
        if (format == BarcodeFormat.DATA_MATRIX) hints[DecodeHintType.PURE_BARCODE] = true
        val result = MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source)), hints)
        payloadMatches(format, original, content, result.text)
    }.getOrDefault(false)

    private fun payloadMatches(
        format: BarcodeFormat,
        original: String,
        content: String,
        decoded: String,
    ): Boolean = when (format) {
        BarcodeFormat.AZTEC, BarcodeFormat.PDF_417 ->
            decoded.toByteArray(Charsets.ISO_8859_1).contentEquals(
                content.toByteArray(Charsets.ISO_8859_1),
            )

        // Decoders return the check digit; accept either the stored length or
        // stored + check digit.
        BarcodeFormat.EAN_13, BarcodeFormat.EAN_8, BarcodeFormat.UPC_A, BarcodeFormat.UPC_E ->
            decoded == content ||
                (decoded.length == content.length + 1 &&
                    decoded.startsWith(content) &&
                    decoded.all(Char::isDigit))

        // Decoders may keep or drop the start/stop sentinels.
        BarcodeFormat.CODABAR ->
            decoded == content ||
                decoded == original ||
                (original.length >= 2 && decoded == original.substring(1, original.lastIndex))

        else -> decoded == content
    }

    private fun possibleFormats(format: BarcodeFormat): List<BarcodeFormat> = when (format) {
        BarcodeFormat.QR_CODE -> listOf(BarcodeFormat.QR_CODE)
        BarcodeFormat.AZTEC -> listOf(BarcodeFormat.AZTEC)
        BarcodeFormat.PDF_417 -> listOf(BarcodeFormat.PDF_417)
        BarcodeFormat.DATA_MATRIX -> listOf(BarcodeFormat.DATA_MATRIX)
        BarcodeFormat.CODE_128 -> listOf(BarcodeFormat.CODE_128)
        BarcodeFormat.CODE_39 -> listOf(BarcodeFormat.CODE_39)
        BarcodeFormat.CODE_93 -> listOf(BarcodeFormat.CODE_93)
        BarcodeFormat.CODABAR -> listOf(BarcodeFormat.CODABAR)
        BarcodeFormat.EAN_13 -> listOf(BarcodeFormat.EAN_13)
        BarcodeFormat.EAN_8 -> listOf(BarcodeFormat.EAN_8)
        BarcodeFormat.UPC_A -> listOf(BarcodeFormat.UPC_A, BarcodeFormat.EAN_13)
        BarcodeFormat.UPC_E -> listOf(BarcodeFormat.UPC_E)
        BarcodeFormat.ITF -> listOf(BarcodeFormat.ITF)
        else -> emptyList()
    }

    private fun decodeRawData(base64: String): String? = runCatching {
        String(Base64.getMimeDecoder().decode(base64), Charsets.ISO_8859_1)
    }.getOrNull()

    private fun withCodabarSentinels(value: String): String {
        val upper = value.uppercase()
        val starts = upper.isNotEmpty() && upper.first() in "ABCD"
        val ends = upper.isNotEmpty() && upper.last() in "ABCD"
        return (if (starts) "" else "A") + upper + (if (ends) "" else "A")
    }

    private fun BitMatrix.withQuietZone(quietModules: Int): BitMatrix {
        return BitMatrix(
            width + quietModules * QUIET_ZONE_SIDE_COUNT,
            height + quietModules * QUIET_ZONE_SIDE_COUNT,
        ).also { padded ->
            for (y in 0 until height) {
                for (x in 0 until width) {
                    if (get(x, y)) padded.set(quietModules + x, quietModules + y)
                }
            }
        }
    }

    private fun BitMatrix.withHorizontalQuietZone(quietModules: Int): BitMatrix {
        return BitMatrix(width + quietModules * QUIET_ZONE_SIDE_COUNT, height).also { padded ->
            for (y in 0 until height) {
                for (x in 0 until width) {
                    if (get(x, y)) padded.set(quietModules + x, y)
                }
            }
        }
    }
}
