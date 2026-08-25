/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.utils

import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.ImageResult

/**
 * Re-fetches YouTube artwork at the next size down when the requested one does not
 * exist.
 *
 * `i.ytimg.com` thumbnails are addressed by filename, not by dimensions, and not every
 * video has every size — `maxresdefault.jpg` in particular is missing for a large share
 * of tracks and answers 404. The player asks for it anyway (see
 * `com.convx.music.ui.utils.resize`), because when it *is* there it is by far the best
 * artwork available; without this the miss just showed as no artwork at all.
 *
 * Only the error path costs anything: a successful load never runs a second request, and
 * list/grid artwork already asks for `hqdefault`, which effectively always exists. So the
 * extra round trip is at most one small 404 per *played* song, not one per row.
 */
class ArtworkQualityInterceptor : Interceptor {

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val result = chain.proceed()
        if (result !is ErrorResult) return result

        val url = chain.request.data as? String ?: return result
        if (!url.contains(YouTubeThumbnailHost)) return result

        val currentQuality = QualityPattern.find(url)?.value ?: return result
        val startIndex = QualityLadder.indexOf(currentQuality)
        // Unknown or already-lowest quality: nothing left to step down to.
        if (startIndex < 0 || startIndex >= QualityLadder.lastIndex) return result

        var lastResult: ImageResult = result
        for (index in (startIndex + 1)..QualityLadder.lastIndex) {
            val candidate = url.replace(QualityPattern, QualityLadder[index])
            val request = chain.request.newBuilder().data(candidate).build()
            val attempt = chain.withRequest(request).proceed()
            if (attempt !is ErrorResult) return attempt
            lastResult = attempt
        }
        return lastResult
    }

    private companion object {
        const val YouTubeThumbnailHost = "i.ytimg.com"

        /** Best first. Each entry is a whole filename, which is how YouTube sizes these. */
        val QualityLadder = listOf(
            "maxresdefault.jpg",
            "sddefault.jpg",
            "hqdefault.jpg",
            "mqdefault.jpg",
            "default.jpg",
        )

        val QualityPattern = Regex("(?:maxres|sd|hq|mq)?default\\.jpg")
    }
}
