package com.lightphone.passes

import androidx.compose.ui.graphics.ImageBitmap

/**
 * In-memory cache of rendered code bitmaps, keyed by code id — shared across
 * the code fullscreen's turns so flipping between codes never re-fetches,
 * re-decodes, or re-flashes "Generating…". BarcodeRenderer additionally caches
 * the PNGs by payload + width, so even a cold process renders each code only
 * once.
 */
object BarcodeCache {
    private const val MAX_ENTRIES = 24

    // Access-ordered so the eldest (least recently used) entry is dropped
    // when the cap is hit — a wallet never needs more than a handful.
    private val bitmaps = object : LinkedHashMap<String, ImageBitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean =
            size > MAX_ENTRIES
    }

    @Synchronized
    fun get(codeId: String): ImageBitmap? = bitmaps[codeId]

    @Synchronized
    fun put(codeId: String, bitmap: ImageBitmap) {
        bitmaps[codeId] = bitmap
    }
}
