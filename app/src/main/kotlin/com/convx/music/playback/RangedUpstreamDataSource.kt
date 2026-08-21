package com.convx.music.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.IOException

/**
 * Splits one logical read into bounded range requests over an upstream factory.
 *
 * googlevideo paces a single continuous response down to roughly playback
 * speed (~15 kB/s measured by BitChord's author), while the same bytes asked
 * for as bounded ranges come back at line rate (~5.7 MB/s). A whole-track
 * download over one continuous stream therefore holds the radio awake for the
 * track's full duration; chunked, a five-megabyte Opus lands in about a second
 * and the radio sleeps between chunks.
 *
 * Only the download path wires this in. Playback keeps its continuous stream,
 * where pacing-to-realtime is harmless and chunking would just add requests.
 *
 * Each chunk opens a fresh child [DataSource] from [upstreamFactory] with a
 * position+length-bounded [DataSpec], so Range headers, proxy/DNS handling and
 * TLS reuse all stay stock. Non-googlevideo hosts pass through untouched:
 * some CDNs ignore Range headers, and chunking those would silently re-read
 * from byte zero for every chunk.
 */
@UnstableApi
class RangedUpstreamDataSource(
    private val upstreamFactory: DataSource.Factory,
    private val chunkSize: Long = DEFAULT_CHUNK_SIZE,
) : BaseDataSource(true) {

    private var upstream: DataSource? = null
    private var uri: Uri? = null
    private var requestHeaders: Map<String, String> = emptyMap()
    private var passthrough = false

    /** Bytes left in the logical spec handed to [open]; [C.LENGTH_UNSET] if unbounded. */
    private var specRemaining: Long = C.LENGTH_UNSET.toLong()

    /** Absolute stream position where the next chunk should start. */
    private var nextChunkPosition: Long = 0

    /** Bytes left in the currently-open chunk (unused in passthrough mode). */
    private var currentChunkRemaining: Long = 0

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        uri = dataSpec.uri
        requestHeaders = dataSpec.httpRequestHeaders
        specRemaining =
            if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.length else C.LENGTH_UNSET.toLong()
        nextChunkPosition = dataSpec.position
        passthrough = !dataSpec.uri.host.orEmpty().contains("googlevideo")
        if (passthrough) {
            openChild(dataSpec.position, dataSpec.length)
        } else {
            openNextChunk()
        }
        transferStarted(dataSpec)
        // Unbounded: callers (CacheDataSource / DownloadManager) handle EOF via
        // RESULT_END_OF_INPUT, and we can't know the total without probing.
        return C.LENGTH_UNSET.toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        while (true) {
            if (specRemaining == 0L) return C.RESULT_END_OF_INPUT
            if (!passthrough && currentChunkRemaining == 0L) {
                closeUpstream()
                if (!openNextChunk()) return C.RESULT_END_OF_INPUT
                continue
            }
            val want =
                if (passthrough) length
                else minOf(length.toLong(), currentChunkRemaining).toInt()
            val read = requireNotNull(upstream).read(buffer, offset, want)
            if (read == C.RESULT_END_OF_INPUT) {
                // Server ended early: resource finished, or shorter than the
                // spec promised. Either way this logical stream is over.
                closeUpstream()
                specRemaining = 0L
                return C.RESULT_END_OF_INPUT
            }
            if (read > 0) {
                nextChunkPosition += read
                if (!passthrough) currentChunkRemaining -= read
                if (specRemaining != C.LENGTH_UNSET.toLong()) specRemaining -= read
                bytesTransferred(read)
                return read
            }
        }
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        closeUpstream()
        uri = null
        specRemaining = C.LENGTH_UNSET.toLong()
        currentChunkRemaining = 0
    }

    private fun openNextChunk(): Boolean {
        if (specRemaining == 0L) return false
        val len =
            if (specRemaining != C.LENGTH_UNSET.toLong()) minOf(chunkSize, specRemaining)
            else chunkSize
        openChild(nextChunkPosition, len)
        currentChunkRemaining = len
        return true
    }

    private fun openChild(position: Long, length: Long) {
        val builder = DataSpec.Builder()
            .setUri(requireNotNull(uri))
            .setPosition(position)
            .setLength(length)
        if (requestHeaders.isNotEmpty()) builder.setHttpRequestHeaders(requestHeaders)
        upstream = upstreamFactory.createDataSource().also { it.open(builder.build()) }
    }

    private fun closeUpstream() {
        upstream?.let {
            try {
                it.close()
            } catch (_: IOException) {
                // Best effort; the caller retries the whole action anyway.
            }
        }
        upstream = null
    }

    companion object {
        // BitChord's measured sweet spot: large enough that request overhead
        // vanishes, small enough that canceling mid-download wastes little.
        private const val DEFAULT_CHUNK_SIZE = 2L * 1024 * 1024
    }
}
