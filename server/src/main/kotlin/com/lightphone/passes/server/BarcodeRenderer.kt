package com.lightphone.passes.server

import android.graphics.Bitmap
import android.util.LruCache
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/**
 * Android-side barcode rendering: rasterizes via [BarcodeRaster] (pure JVM,
 * unit-tested), compresses the verified pixel grid to a PNG, and caches by
 * payload + width so repeat views skip the render-and-verify pass.
 */
object BarcodeRenderer {

    private const val DEFAULT_TARGET_WIDTH_PX = 960
    private const val CACHE_KIB = 12 * 1024

    private val pngCache = object : LruCache<String, ByteArray>(CACHE_KIB) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size.coerceAtLeast(1)
    }

    /**
     * Renders [data] (or the decoded [rawData] binary payload when present) as a
     * PNG sized to [targetWidthPx]. Returns null when the type is unknown, the
     * payload can't be encoded, the symbol can't fit the target at one pixel per
     * module, or the rendered pixels fail payload verification.
     */
    fun renderPng(
        type: String,
        data: String,
        rawData: String?,
        targetWidthPx: Int = DEFAULT_TARGET_WIDTH_PX,
    ): ByteArray? {
        val raster = BarcodeRaster.raster(type, data, rawData, targetWidthPx) ?: return null
        val key = cacheKey(type, data, rawData, targetWidthPx)
        synchronized(pngCache) { pngCache.get(key) }?.let { return it }
        val png = rasterToPng(raster) ?: return null
        synchronized(pngCache) { pngCache.put(key, png) }
        return png
    }

    private fun rasterToPng(raster: Raster): ByteArray? {
        val bitmap = Bitmap.createBitmap(raster.width, raster.height, Bitmap.Config.RGB_565)
        bitmap.setPixels(raster.pixels, 0, raster.width, 0, 0, raster.width, raster.height)
        val out = ByteArrayOutputStream()
        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
            bitmap.recycle()
            return null
        }
        bitmap.recycle()
        return out.toByteArray()
    }

    private fun cacheKey(type: String, data: String, rawData: String?, width: Int): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$data|${rawData ?: ""}".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "$type:$width:$digest"
    }
}
