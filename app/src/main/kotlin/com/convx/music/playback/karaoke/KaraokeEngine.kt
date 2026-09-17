/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.convx.music.playback.karaoke

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.util.UnstableApi
import com.convx.music.playback.AudioCache
import com.convx.music.playback.smart.AudioDecoder
import com.convx.music.playback.smart.KaraokeBands
import com.convx.music.playback.smart.VocalTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.Executors
import kotlin.math.ceil

/**
 * Apple Music Sing, on-device: open-unmix's vocal target (the MIT-weighted model already bundled
 * for vocal-aware transitions) turned into a time/band mask, applied live by
 * [KaraokeAudioProcessor] with the Sing slider's [level].
 *
 * The model reads a fixed 22.3 s window, so the track is analysed window by window, starting at
 * the one being played and working outward, from whatever the player has already cached. A
 * window whose audio is not cached yet is retried as the cache fills. Frames are marked ready as
 * each window lands, so the reduction switches on progressively rather than after a long wait.
 *
 * Dormant until the user turns Sing on: no thread, no model session, no mask memory exist before
 * that, and switching it off closes the session and drops every mask. The processors are a
 * plain buffer copy while off.
 */
@UnstableApi
class KaraokeEngine(private val context: Context) {

    data class State(
        val enabled: Boolean = false,
        /** 1 = original vocals, 0 = removed. Apple's Sing starts well down, not at zero. */
        val level: Float = 0.2f,
        /** Fraction of the current track's windows analysed. */
        val progress: Float = 0f,
        val mediaId: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val processors = java.util.Collections.newSetFromMap(
        java.util.WeakHashMap<KaraokeAudioProcessor, Boolean>()
    )
    private var tracker: VocalTracker? = null
    private val executorLazy = lazy {
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "convx-karaoke").apply { isDaemon = true; priority = Thread.MIN_PRIORITY + 1 }
        }
    }
    private val executor by executorLazy

    @Volatile private var track: Track? = null
    private val masks = object : LinkedHashMap<String, KaraokeMask>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, KaraokeMask>?) = size > 3
    }

    private data class Track(val mediaId: String, val uri: Uri, val durationMs: Long, val startMs: Long)

    fun register(processor: KaraokeAudioProcessor) {
        synchronized(processors) { processors += processor }
        push()
    }

    fun setEnabled(on: Boolean) {
        _state.update { it.copy(enabled = on) }
        push()
        if (on) {
            track?.let { schedule(it) }
        } else if (executorLazy.isInitialized()) {
            executor.execute {
                tracker?.release(); tracker = null
                synchronized(masks) { masks.clear() }
                push()
            }
        }
    }

    fun setLevel(level: Float) {
        _state.update { it.copy(level = level.coerceIn(0f, 1f)) }
        push()
    }

    /**
     * From MusicService on every track change and seek of the ACTIVE player (which rebases its own
     * processor): which track to analyse, and where to start.
     */
    fun onPosition(mediaId: String?, durationMs: Long, positionMs: Long) {
        if (mediaId == null) return
        val current = track
        // New track, or the same one now that the player knows its duration.
        if (current?.mediaId != mediaId || (current.durationMs <= 0 && durationMs > 0)) {
            val uri = Uri.parse("https://music.youtube.com/watch?v=$mediaId")
            val next = Track(mediaId, uri, durationMs, positionMs)
            track = next
            _state.update { it.copy(mediaId = mediaId, progress = progressOf(synchronized(masks) { masks[mediaId] })) }
            push()
            if (_state.value.enabled) schedule(next)
        }
    }

    private fun push() {
        val s = _state.value
        val mask = s.mediaId?.let { synchronized(masks) { masks[it] } }
        synchronized(processors) {
            processors.forEach {
                it.enabled = s.enabled
                it.level = s.level
                it.mask = mask
            }
        }
    }

    private fun schedule(t: Track) = executor.execute { analyse(t) }

    private fun analyse(t: Track) {
        if (t.durationMs <= 0) return
        val windowSeconds = VocalTracker.FIXED_FRAMES / KaraokeMask.FRAME_RATE
        val totalFrames = ceil(t.durationMs / 1000.0 * KaraokeMask.FRAME_RATE).toInt()
        val mask = synchronized(masks) { masks.getOrPut(t.mediaId) { KaraokeMask(t.mediaId, totalFrames) } }
        push()
        val windows = ceil(totalFrames / VocalTracker.FIXED_FRAMES.toDouble()).toInt()
        val first = (t.startMs / 1000.0 / windowSeconds).toInt().coerceIn(0, windows - 1)
        // Outward from the playing window, forward first: that is where the listener is going.
        val order = buildList {
            for (i in first until windows) add(i)
            for (i in first - 1 downTo 0) add(i)
        }
        val done = BooleanArray(windows) { w -> mask.ready[w * VocalTracker.FIXED_FRAMES] }
        var idlePasses = 0
        while (track?.mediaId == t.mediaId && _state.value.enabled && !done.all { it }) {
            var progressed = false
            for (w in order) {
                if (done[w] || track?.mediaId != t.mediaId || !_state.value.enabled) continue
                if (analyseWindow(t, mask, w, windowSeconds)) {
                    done[w] = true
                    progressed = true
                    _state.update { if (it.mediaId == t.mediaId) it.copy(progress = progressOf(mask)) else it }
                }
            }
            if (!progressed) {
                // Not cached yet: wait for the player's buffer to grow, give up after ~2 min idle.
                if (++idlePasses > 60) break
                Thread.sleep(2_000)
            } else {
                idlePasses = 0
            }
        }
    }

    private fun analyseWindow(t: Track, mask: KaraokeMask, window: Int, windowSeconds: Double): Boolean {
        val start = window * windowSeconds
        val end = minOf(start + windowSeconds, t.durationMs / 1000.0)
        val rendition = AudioCache.renditionsOf(t.uri).maxByOrNull { it.cachedPrefix } ?: return false
        val decoded = runCatching {
            AudioCache.renditionDataSource(t.uri, rendition).use { AudioDecoder.decodeRegionStereo(it, start, end) }
        }.getOrNull() ?: return false
        val (pcm, actualStart) = decoded
        // The extractor may start a little early; drop the lead-in so frame 0 is `start`.
        val skip = ((start - actualStart) * pcm.sampleRate).toInt().coerceAtLeast(0)
        val wanted = ((end - start) * pcm.sampleRate).toInt()
        if (pcm.left.size - skip < wanted * 0.97) return false // region not fully cached yet
        val left = pcm.left.copyOfRange(skip, minOf(pcm.left.size, skip + wanted))
        val right = pcm.right.copyOfRange(skip, minOf(pcm.right.size, skip + wanted))
        val model = tracker ?: VocalTracker(context).also { tracker = it }
        val started = System.currentTimeMillis()
        val (bands, frames) = model.separationMask(left, right, pcm.sampleRate) ?: return false
        val base = window * VocalTracker.FIXED_FRAMES
        for (f in 0 until frames) {
            val target = base + f
            if (target >= mask.frames) break
            System.arraycopy(bands, f * KaraokeBands.COUNT, mask.data, target * KaraokeBands.COUNT, KaraokeBands.COUNT)
            mask.ready[target] = true
        }
        Log.d(TAG, "window $window of ${t.mediaId}: $frames frames in ${System.currentTimeMillis() - started}ms")
        return true
    }

    private fun progressOf(mask: KaraokeMask?): Float =
        if (mask == null || mask.frames == 0) 0f else mask.ready.count { it }.toFloat() / mask.frames

    fun release() {
        track = null
        if (executorLazy.isInitialized()) {
            executor.execute { tracker?.release(); tracker = null }
            executor.shutdown()
        }
    }

    private companion object {
        const val TAG = "ConvxKaraoke"
    }
}
