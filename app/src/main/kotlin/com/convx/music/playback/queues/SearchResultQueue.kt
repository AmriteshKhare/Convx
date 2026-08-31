/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.playback.queues

import com.convx.music.extensions.toMediaItem
import com.convx.music.models.toMediaMetadata
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.YTItem

/**
 * The queue to start when a song row inside a *list* is tapped — search results, a shelf, a
 * suggestion list.
 *
 * Every one of those used to start a one-song [YouTubeQueue] radio, which threw away the list
 * the user was looking at: playing the third result meant the other results were simply gone,
 * replaced by YouTube's radio for that one track. Queue the surrounding songs instead, in the
 * order they are shown, and fall back to radio only when there is genuinely nothing to
 * surround it with.
 */
fun searchResultQueue(
    song: SongItem,
    siblings: List<YTItem>,
    title: String? = null,
): Queue {
    val songs = siblings.filterIsInstance<SongItem>().distinctBy { it.id }
    val startIndex = songs.indexOfFirst { it.id == song.id }
    return if (songs.size > 1 && startIndex >= 0) {
        ListQueue(
            title = title,
            items = songs.map { it.toMediaItem() },
            startIndex = startIndex,
        )
    } else {
        YouTubeQueue(WatchEndpoint(videoId = song.id), song.toMediaMetadata())
    }
}
