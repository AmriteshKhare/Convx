/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM
import androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
import androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.STATE_ENDED
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import com.convx.music.db.MusicDatabase
import com.convx.music.extensions.currentMetadata
import com.convx.music.extensions.getCurrentQueueIndex
import com.convx.music.extensions.getQueueWindows
import com.convx.music.extensions.metadata
import com.convx.music.extensions.togglePlayPause
import com.convx.music.playback.MusicService.MusicBinder
import com.convx.music.constants.AskBeforeReplacingQueueKey
import com.convx.music.playback.queues.ListQueue
import com.convx.music.playback.queues.Queue
import com.convx.music.utils.dataStore
import com.convx.music.utils.get
import com.convx.music.utils.reportException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerConnection(
    context: Context,
    binder: MusicBinder,
    val database: MusicDatabase,
    private val scope: CoroutineScope,
) : Player.Listener {
    private companion object {
        private const val TAG = "PlayerConnection"
        /** Tap -> seek gap: long enough for the press animation to lead, short enough to
         *  still read as instant. Also the window rapid taps coalesce in. */
        private const val SKIP_COMMIT_DELAY_MS = 140L
        private const val PLAYER_INIT_TIMEOUT_MS = 5000L // 5 second timeout for player initialization
    }

    val service = binder.service
    private val appContext = context.applicationContext
    private val playerReadinessFlow = service.isPlayerReady
    
    /**
     * Safe player accessor checks readiness & handles errors.
     * Should be used by all player access within this class.
     */
    private fun getPlayerSafe(): ExoPlayer {
        return try {
            if (!playerReadinessFlow.value) {
                Timber.tag(TAG).w("Player accessed before service initialization complete; returning best-effort reference")
            }
            service.player
        } catch (e: UninitializedPropertyAccessException) {
            Timber.tag(TAG).e(e, "Fatal: player property accessed but not initialized")
            throw IllegalStateException("MusicService.player not initialized; possible race condition in service startup", e)
        }
    }

    /**
     * Public accessor for player. Throws if player not ready.
     * Callers should check [isPlayerInitialized] before calling, or handle exceptions.
     */
    val player: ExoPlayer
        get() = getPlayerSafe()

    /** Tracks whether player initialization completed successfully */
    private val isPlayerInitialized = MutableStateFlow(service.isPlayerReady.value)

    val playbackState: MutableStateFlow<Int>
    private val playWhenReady: MutableStateFlow<Boolean>
    val isPlaying: kotlinx.coroutines.flow.StateFlow<Boolean>
    
    init {
        Timber.tag(TAG).d("PlayerConnection init: playerReady=${playerReadinessFlow.value}")
        
        // Initialize with player state or safe defaults if player not ready
        val initialState = try {
            val initialPlayer = getPlayerSafe()
            Triple(initialPlayer.playbackState, initialPlayer.playWhenReady, 
                   initialPlayer.playWhenReady && initialPlayer.playbackState != STATE_ENDED)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error during PlayerConnection initialization, using defaults")
            Triple(Player.STATE_IDLE, false, false)
        }
        
        playbackState = MutableStateFlow(initialState.first)
        playWhenReady = MutableStateFlow(initialState.second)
        isPlaying = combine(playbackState, playWhenReady) { state, ready ->
            ready && state != STATE_ENDED
        }.stateIn(
            scope,
            SharingStarted.Lazily,
            initialState.third
        )
        
        // Track service readiness changes in background.
        scope.launch {
            playerReadinessFlow.collect { ready ->
                isPlayerInitialized.value = ready
                if (ready) {
                    Timber.tag(TAG).d("Service player initialization detected by PlayerConnection")
                }
            }
        }
        
        Timber.tag(TAG).d("PlayerConnection state flows initialized successfully")
    }
    
    // Effective playing state, considers Cast when active
    val isEffectivelyPlaying = combine(
        isPlaying,
        service.castConnectionHandler?.isCasting ?: MutableStateFlow(false),
        service.castConnectionHandler?.castIsPlaying ?: MutableStateFlow(false)
    ) { localPlaying, isCasting, castPlaying ->
        if (isCasting) castPlaying else localPlaying
    }.stateIn(
        scope,
        SharingStarted.Lazily,
        player.playbackState != STATE_ENDED && player.playWhenReady
    )
    
    val mediaMetadata = MutableStateFlow(player.currentMetadata)
    val currentSong =
        mediaMetadata.flatMapLatest {
            database.song(it?.id)
        }
    val currentLyrics = mediaMetadata.flatMapLatest { mediaMetadata ->
        database.lyrics(mediaMetadata?.id)
    }
    val currentFormat =
        mediaMetadata.flatMapLatest { mediaMetadata ->
            database.format(mediaMetadata?.id)
        }

    /** BPM, key, tier and transition style, straight from the DJ engine. */
    val djState = service.djState

    /** The fade length of the DJ crossfade that just handed off, or 0 outside
     *  one — see [com.convx.music.playback.MusicService.crossfadeDurationMs]. */
    val crossfadeDurationMs = service.crossfadeDurationMs

    val queueTitle = MutableStateFlow<String?>(null)
    val queueWindows = MutableStateFlow<List<Timeline.Window>>(emptyList())
    val currentMediaItemIndex = MutableStateFlow(-1)
    val currentWindowIndex = MutableStateFlow(-1)

    val shuffleModeEnabled = MutableStateFlow(false)
    val repeatMode = MutableStateFlow(REPEAT_MODE_OFF)

    val canSkipPrevious = MutableStateFlow(true)
    val canSkipNext = MutableStateFlow(true)

    /** Set by the floating mini bar's lyrics/queue icons (which live outside
     *  Player.kt's own composable scope) to ask the player to open already
     *  showing lyrics/queue. Player.kt observes these and resets them. */
    val requestShowLyrics = MutableStateFlow(false)
    val requestShowQueue = MutableStateFlow(false)

    val error = MutableStateFlow<PlaybackException?>(null)
    val isMuted = service.isMuted

    val waitingForNetworkConnection = service.waitingForNetworkConnection
    
    // Callback to check if playback changes should be blocked (e.g., Listen Together guest)
    var shouldBlockPlaybackChanges: (() -> Boolean)? = null
    
    // Flag to allow internal sync operations to bypass blocking (set by ListenTogetherManager)
    @Volatile
    var allowInternalSync: Boolean = false

    var onSkipPrevious: (() -> Unit)? = null
    var onSkipNext: (() -> Unit)? = null

    private var attachedPlayer: Player? = null

    init {
        try {
            // Observe player changes (e.g. crossfade swap)
            scope.launch {
                service.playerFlow.collect { newPlayer ->
                    if (newPlayer != null && newPlayer != attachedPlayer) {
                        updateAttachedPlayer(newPlayer)
                    }
                }
            }
            
            // Initial setup if flow hasn't emitted yet but service is ready
            if (attachedPlayer == null && service.isPlayerReady.value) {
                 updateAttachedPlayer(player)
            }

            Timber.tag(TAG).d("PlayerConnection flow observer registered")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to initialize PlayerConnection listener or state")
            // Propagate the error so MainActivity can retry
            throw e
        }
    }

    private fun updateAttachedPlayer(newPlayer: Player) {
        attachedPlayer?.removeListener(this)
        attachedPlayer = newPlayer
        newPlayer.addListener(this)
        
        // Refresh all state from new player
        playbackState.value = newPlayer.playbackState
        playWhenReady.value = newPlayer.playWhenReady
        mediaMetadata.value = newPlayer.currentMetadata
        queueTitle.value = service.queueTitle
        queueWindows.value = newPlayer.getQueueWindows()
        currentWindowIndex.value = newPlayer.getCurrentQueueIndex()
        currentMediaItemIndex.value = newPlayer.currentMediaItemIndex
        shuffleModeEnabled.value = newPlayer.shuffleModeEnabled
        repeatMode.value = newPlayer.repeatMode
        
        Timber.tag(TAG).d("Attached to new player instance: $newPlayer")
    }

    /**
     * Set when a playback change was refused because this device is a Listen
     * Together guest without control. The UI observes it to say so and resets it;
     * blocking silently reads as the app being broken.
     */
    val playbackBlockedByRoom = MutableStateFlow(false)

    /** True while playback controls must be inert: in a room, without control. */
    val playbackLockedByRoom: Boolean
        get() = !allowInternalSync && shouldBlockPlaybackChanges?.invoke() == true

    /**
     * A queue the user asked to play while something else was already queued, parked here
     * until they say whether to keep or clear what is playing. Consumed by
     * `QueueConflictDialog`, which is hosted once at the root — the prompt has to live at a
     * single point because every "play this" in the app funnels through [playQueue].
     */
    val pendingQueue = MutableStateFlow<Queue?>(null)

    /**
     * The queue that was just silently replaced (asking-before-replacing is off), so a
     * snackbar can offer to undo it. Not set when the user replaced via the prompt or force
     * came from an internal caller — both are already a deliberate choice with nothing to
     * undo.
     */
    val queueReplacedForUndo = MutableStateFlow<Queue?>(null)

    /**
     * @param force skips the keep-or-clear prompt. Set by the prompt itself when the user
     *   picks "clear and play", and by internal callers that are already a deliberate replace
     *   (queue restore, Listen Together sync).
     */
    fun playQueue(queue: Queue, force: Boolean = false) {
        // Every "play this" in the app funnels through here — song rows, album and
        // playlist headers, search results, Home tiles, radio. It was the one entry
        // point with no room gate, so an owner-only room still let a guest start its
        // own local or online playback and desync the whole session.
        if (playbackLockedByRoom) {
            Timber.tag(TAG).d("playQueue blocked - Listen Together guest without control")
            playbackBlockedByRoom.value = true
            return
        }
        if (!playerReadinessFlow.value) {
            Timber.tag(TAG).w("playQueue called before player ready; delegating to service")
        }
        val isReplace = hasLiveQueue() && !startsInsideCurrentQueue(queue)
        if (!force && appContext.dataStore.get(AskBeforeReplacingQueueKey, false) && isReplace) {
            pendingQueue.value = queue
            return
        }
        // Only a silent replace (prompt off) needs an undo offer — a forced replace is
        // already the user's or the system's deliberate choice.
        val previousQueue = if (!force && isReplace) snapshotCurrentQueue() else null
        try {
            service.playQueue(queue)
            previousQueue?.let { queueReplacedForUndo.value = it }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in playQueue")
            throw e
        }
    }

    /** Captures what is currently playing as a replayable [ListQueue], for undo. */
    private fun snapshotCurrentQueue(): Queue? = try {
        if (player.mediaItemCount == 0) null else ListQueue(
            items = (0 until player.mediaItemCount).map { player.getMediaItemAt(it) },
            startIndex = player.currentMediaItemIndex,
            position = player.currentPosition,
        )
    } catch (e: Exception) {
        Timber.tag(TAG).w(e, "snapshotCurrentQueue failed")
        null
    }

    /** Appends [queue] to what is already playing instead of replacing it. */
    fun enqueueQueue(queue: Queue) {
        if (playbackLockedByRoom) {
            playbackBlockedByRoom.value = true
            return
        }
        service.enqueueQueue(queue)
    }

    /**
     * True when there is a queue worth asking about: at least one more item than the one the
     * user just tapped would replace, and a player that is not sitting idle after being
     * emptied.
     */
    private fun hasLiveQueue(): Boolean = try {
        playerReadinessFlow.value &&
            player.mediaItemCount > 0 &&
            player.playbackState != Player.STATE_IDLE
    } catch (e: Exception) {
        Timber.tag(TAG).w(e, "hasLiveQueue check failed")
        false
    }

    /**
     * True when the song this queue would start on is already sitting in the current queue —
     * i.e. the user tapped another row of the list they are already playing. Replacing there
     * is what they expect, so the keep-or-clear prompt would only be in the way; it is meant
     * for starting something genuinely unrelated.
     */
    private fun startsInsideCurrentQueue(queue: Queue): Boolean = try {
        val startId = when (queue) {
            is ListQueue -> queue.items.getOrNull(queue.startIndex)?.mediaId
            else -> queue.preloadItem?.id
        }
        startId != null && (0 until player.mediaItemCount).any { player.getMediaItemAt(it).mediaId == startId }
    } catch (e: Exception) {
        Timber.tag(TAG).w(e, "startsInsideCurrentQueue check failed")
        false
    }

    fun startRadioSeamlessly() {
        // Block if Listen Together guest
        if (shouldBlockPlaybackChanges?.invoke() == true) {
            Timber.tag("PlayerConnection").d("startRadioSeamlessly blocked - Listen Together guest")
            return
        }
        if (!playerReadinessFlow.value) {
            Timber.tag(TAG).w("startRadioSeamlessly called before player ready; delegating to service")
        }
        try {
            service.startRadioSeamlessly()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in startRadioSeamlessly")
            throw e
        }
    }

    fun playNext(item: MediaItem) = playNext(listOf(item))

    fun playNext(items: List<MediaItem>) {
        // Block if Listen Together guest (unless internal sync)
        if (playbackLockedByRoom) {
            Timber.tag("PlayerConnection").d("playNext blocked - Listen Together guest")
            playbackBlockedByRoom.value = true
            return
        }
        try {
            service.playNext(items)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in playNext")
            throw e
        }
    }

    fun addToQueue(item: MediaItem) = addToQueue(listOf(item))

    fun addToQueue(items: List<MediaItem>) {
        // Block if Listen Together guest (unless internal sync)
        if (playbackLockedByRoom) {
            Timber.tag("PlayerConnection").d("addToQueue blocked - Listen Together guest")
            playbackBlockedByRoom.value = true
            return
        }
        try {
            service.addToQueue(items)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in addToQueue")
            throw e
        }
    }

    fun toggleLike() {
        try {
            service.toggleLike()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in toggleLike")
        }
    }

    fun toggleMute() {
        service.toggleMute()
    }

    fun setMuted(muted: Boolean) {
        service.setMuted(muted)
    }

    fun toggleLibrary() {
        try {
            service.toggleLibrary()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in toggleLibrary")
        }
    }

    /**
     * Toggle play/pause - handles Cast when active
     */
    fun togglePlayPause() {
        try {
            val castHandler = service.castConnectionHandler
            if (castHandler?.isCasting?.value == true) {
                if (castHandler.castIsPlaying.value) {
                    castHandler.pause()
                } else {
                    castHandler.play()
                }
            } else {
                player.togglePlayPause()
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in togglePlayPause")
        }
    }
    
    /**
     * Start playback - handles Cast when active
     */
    fun play() {
        try {
            val castHandler = service.castConnectionHandler
            if (castHandler?.isCasting?.value == true) {
                castHandler.play()
            } else {
                when (player.playbackState) {
                    Player.STATE_IDLE -> player.prepare()
                    Player.STATE_ENDED -> player.seekTo(player.currentMediaItemIndex, 0)
                    else -> {}
                }
                player.playWhenReady = true
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in play")
        }
    }
    
    /**
     * Pause playback - handles Cast when active
     */
    fun pause() {
        try {
            val castHandler = service.castConnectionHandler
            if (castHandler?.isCasting?.value == true) {
                castHandler.pause()
            } else {
                player.playWhenReady = false
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in pause")
        }
    }

    /**
     * Seek to position - handles Cast when active
     */
    fun seekTo(position: Long) {
        try {
            val castHandler = service.castConnectionHandler
            if (castHandler?.isCasting?.value == true) {
                castHandler.seekTo(position)
            } else {
                player.seekTo(position)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in seekTo")
        }
    }

    private var pendingSkip = 0
    private var skipCommitJob: kotlinx.coroutines.Job? = null

    /**
     * Tap-first skip for the transport buttons: the button's own animation plays on the tap,
     * and the actual seek lands [SKIP_COMMIT_DELAY_MS] later, off the tap's frame. Taps inside
     * that window coalesce into ONE jump (+3 = the third track ahead), so rapid tapping runs a
     * single track transition instead of stacking a player seek + UI transition per tap.
     */
    fun requestSkip(forward: Boolean) {
        pendingSkip += if (forward) 1 else -1
        skipCommitJob?.cancel()
        skipCommitJob = scope.launch {
            kotlinx.coroutines.delay(SKIP_COMMIT_DELAY_MS)
            val steps = pendingSkip
            pendingSkip = 0
            when {
                steps == 1 -> seekToNext()
                steps == -1 -> seekToPrevious()
                steps != 0 -> jumpBy(steps)
            }
        }
    }

    /** Several skips at once: walk the timeline (repeat/shuffle aware), one seek. Also the
     *  swipe-to-skip path: a swiped-in PREVIOUS card must land on the previous track even
     *  3s into this one, where [seekToPrevious] would restart instead. */
    fun jumpBy(steps: Int) {
        try {
            val castHandler = service.castConnectionHandler
            if (castHandler?.isCasting?.value == true) {
                repeat(kotlin.math.abs(steps)) { if (steps > 0) castHandler.skipToNext() else castHandler.skipToPrevious() }
                return
            }
            val timeline = player.currentTimeline
            if (timeline.isEmpty) return
            // Repeat-one repeats the track, not the skip: step as if repeat were off.
            val mode = if (player.repeatMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_OFF else player.repeatMode
            var index = player.currentMediaItemIndex
            for (step in 0 until kotlin.math.abs(steps)) {
                val next = if (steps > 0) {
                    timeline.getNextWindowIndex(index, mode, player.shuffleModeEnabled)
                } else {
                    timeline.getPreviousWindowIndex(index, mode, player.shuffleModeEnabled)
                }
                if (next == androidx.media3.common.C.INDEX_UNSET) break
                index = next
            }
            if (index == player.currentMediaItemIndex) return
            player.seekTo(index, 0)
            if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
                player.prepare()
            }
            player.playWhenReady = true
            if (steps > 0) onSkipNext?.invoke() else onSkipPrevious?.invoke()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in jumpBy($steps)")
        }
    }

    fun seekToNext() {
        try {
            // When casting, use Cast skip instead of local player
            val castHandler = service.castConnectionHandler
            if (castHandler?.isCasting?.value == true) {
                castHandler.skipToNext()
                return
            }
            Timber.tag(TAG).d(
                "SKIP_DEBUG seekToNext() called: beforeIndex=${player.currentMediaItemIndex} " +
                    "beforeId=${player.currentMediaItem?.mediaId} repeatMode=${player.repeatMode} " +
                    "shuffle=${player.shuffleModeEnabled} hasNext=${player.hasNextMediaItem()} " +
                    "nextIndex=${player.nextMediaItemIndex}"
            )
            player.seekToNext()
            Timber.tag(TAG).d(
                "SKIP_DEBUG seekToNext() returned: afterIndex=${player.currentMediaItemIndex} " +
                    "afterId=${player.currentMediaItem?.mediaId}"
            )
            if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
                player.prepare()
            }
            player.playWhenReady = true
            onSkipNext?.invoke()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in seekToNext")
        }
    }

    var onRestartSong: (() -> Unit)? = null

    fun seekToPrevious() {
        try {
            // When casting, use Cast skip instead of local player
            val castHandler = service.castConnectionHandler
            if (castHandler?.isCasting?.value == true) {
                castHandler.skipToPrevious()
                return
            }

            // Logic to mimic standard seekToPrevious behavior but with explicit callbacks
            // If we are more than 3 seconds in, just restart the song
            if (player.currentPosition > 3000 || !player.hasPreviousMediaItem()) {
                player.seekTo(0)
                if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
                    player.prepare()
                }
                player.playWhenReady = true
                onRestartSong?.invoke()
            } else {
                // Otherwise go to previous media item
                player.seekToPreviousMediaItem()
                if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
                    player.prepare()
                }
                player.playWhenReady = true
                onSkipPrevious?.invoke()
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in seekToPrevious")
        }
    }

    override fun onPlaybackStateChanged(state: Int) {
        playbackState.value = state
        error.value = player.playerError
    }

    override fun onPlayWhenReadyChanged(
        newPlayWhenReady: Boolean,
        reason: Int,
    ) {
        playWhenReady.value = newPlayWhenReady
    }

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        mediaMetadata.value = mediaItem?.metadata
        currentMediaItemIndex.value = player.currentMediaItemIndex
        currentWindowIndex.value = player.getCurrentQueueIndex()
        updateCanSkipPreviousAndNext()
    }

    override fun onTimelineChanged(
        timeline: Timeline,
        reason: Int,
    ) {
        queueWindows.value = player.getQueueWindows()
        queueTitle.value = service.queueTitle
        currentMediaItemIndex.value = player.currentMediaItemIndex
        currentWindowIndex.value = player.getCurrentQueueIndex()
        updateCanSkipPreviousAndNext()
    }

    override fun onShuffleModeEnabledChanged(enabled: Boolean) {
        shuffleModeEnabled.value = enabled
        queueWindows.value = player.getQueueWindows()
        currentWindowIndex.value = player.getCurrentQueueIndex()
        updateCanSkipPreviousAndNext()
    }

    override fun onRepeatModeChanged(mode: Int) {
        repeatMode.value = mode
        updateCanSkipPreviousAndNext()
    }

    override fun onPlayerErrorChanged(playbackError: PlaybackException?) {
        if (playbackError != null) {
            reportException(playbackError)
        }
        error.value = playbackError
    }

    private fun updateCanSkipPreviousAndNext() {
        if (!player.currentTimeline.isEmpty) {
            val window =
                player.currentTimeline.getWindow(player.currentMediaItemIndex, Timeline.Window())
            canSkipPrevious.value = player.isCommandAvailable(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) ||
                    !window.isLive ||
                    player.isCommandAvailable(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            canSkipNext.value = window.isLive &&
                    window.isDynamic ||
                    player.isCommandAvailable(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        } else {
            canSkipPrevious.value = false
            canSkipNext.value = false
        }
    }

    fun dispose() {
        try {
            attachedPlayer?.removeListener(this)
            attachedPlayer = null
            Timber.tag(TAG).d("PlayerConnection disposed successfully")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error during PlayerConnection disposal")
        }
    }
}