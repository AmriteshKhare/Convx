/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.utils

fun String.resize(
    width: Int? = null,
    height: Int? = null,
): String {
    if (width == null && height == null) return this

    // i.ytimg.com handling (YouTube video thumbnails)
    // These URLs are addressed by filename, not by dimensions, so the only choice is which
    // rung of the ladder to ask for. It used to be binary — maxresdefault above 1200px and
    // hqdefault for everything else — which fed a 544px grid tile a 480x360 image that a
    // square crop cuts down to 360px of real detail; anything filling more than ~400px read
    // soft. Ask for the smallest file that still covers the request, and let
    // ArtworkQualityInterceptor step back down on the 404s (maxres and sd are missing for a
    // large share of videos).
    if (this.contains("i.ytimg.com")) {
        val target = maxOf(width ?: 0, height ?: 0)
        val targetQuality = when {
            target >= 1000 -> "maxresdefault.jpg" // 1280x720
            target >= 400 -> "sddefault.jpg"      // 640x480
            target >= 250 -> "hqdefault.jpg"      // 480x360
            else -> "mqdefault.jpg"               // 320x180
        }
        return this.replace(
            Regex("(default|mqdefault|hqdefault|sddefault|maxresdefault)\\.jpg"),
            targetQuality
        )
    }

    // googleusercontent.com handling (includes lh3-lh6, yt3, etc.)
    if (this.contains("googleusercontent.com") && this.contains("=w")) {
        val baseUrl = this.split("=w")[0]
        val w = width ?: 0
        val h = height ?: width ?: 0
        // Reverting to l90-rj (JPEG) for better compatibility while keeping high resolution
        return "$baseUrl=w$w-h$h-p-l90-rj"
    }

    // yt3.ggpht.com handling (avatars)
    if (this.contains("yt3.ggpht.com")) {
        // Correctly strip any existing size parameter (=s... or -s...) before appending the new one
        val baseUrl = this.split("=")[0].split("-s")[0]
        return "$baseUrl=s${width ?: height}"
    }

    // Fallback for other lh3-style URLs that might not have =w yet
    "https://lh\\d\\.googleusercontent\\.com/.*".toRegex().matchEntire(this)?.let {
        val w = width ?: 0
        val h = height ?: width ?: 0
        return "${this.split("=")[0]}=w$w-h$h-p-l90-rj"
    }

    // Apple Music (mzstatic) artwork: the size is the filename, "bb" = fit inside the box
    // keeping aspect, so a 16:9 editorial image asked for as a square is not cropped.
    if (this.contains("mzstatic.com")) {
        val px = maxOf(width ?: 0, height ?: 0)
        return this.replace(Regex("""/\d+x\d+[a-z]*\.(jpg|jpeg|png|webp)$"""), "/${px}x${px}bb.jpg")
    }

    return this
}
