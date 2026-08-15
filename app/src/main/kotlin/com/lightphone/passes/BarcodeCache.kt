package com.lightphone.passes

import androidx.compose.ui.graphics.ImageBitmap

/**
 * In-memory cache of rendered code bitmaps, keyed by code id — shared by the
 * barcode panel and the fullscreen view so flipping between codes (or opening
 * a code full-screen) never re-fetches, re-decodes, or re-flashes
 * "Generating…". The companion additionally caches the PNGs by payload +
 * width, so even a cold process renders each code only once.
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
