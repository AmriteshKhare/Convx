/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.viewmodels

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.convx.music.ai.AiDjSetBuilder
import com.convx.music.ai.AiDjSetResult
import com.convx.music.db.MusicDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AiDjSetState(
    val isRunning: Boolean = false,
    val logs: List<String> = emptyList(),
    val result: AiDjSetResult? = null,
    /** Set only when the run ended with nothing to show — a log line already
     *  said why (missing key, no songs found, etc). */
    val failed: Boolean = false,
    val isSaving: Boolean = false,
    /** Set once [save] commits [result] to the library — before that, the set
     *  exists only in memory and can be played or discarded for free. */
    val savedPlaylistId: String? = null,
)

@HiltViewModel
class AiDjSetViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
) : ViewModel() {

    private val _state = MutableStateFlow(AiDjSetState())
    val state: StateFlow<AiDjSetState> = _state.asStateFlow()

    private var job: Job? = null

    fun generate(
        prompt: String?,
        seedSongId: String?,
        songCount: Int,
        targetDurationSeconds: Long? = null,
        concertMode: Boolean = false,
    ) {
        if (_state.value.isRunning) return
        _state.value = AiDjSetState(isRunning = true)

        job = viewModelScope.launch {
            val result = AiDjSetBuilder.buildSet(
                context = context,
                database = database,
                prompt = prompt,
                seedSongId = seedSongId,
                songCount = songCount,
                targetDurationSeconds = targetDurationSeconds,
                concertMode = concertMode,
                // Also to Logcat: the running-state UI drops these once the
                // result lands, which is exactly the moment they're most
                // useful for diagnosing a bad set after the fact.
                onLog = { line -> Log.d("AiDjSet", line); _state.update { it.copy(logs = it.logs + line) } },
            )
            _state.update { it.copy(isRunning = false, result = result, failed = result == null) }
        }
    }

    /** Commits the current result to the library. No-op if there's nothing to
     *  save, a save is already running, or it's already been saved once. */
    fun save() {
        val result = _state.value.result ?: return
        if (_state.value.isSaving || _state.value.savedPlaylistId != null) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            val playlistId = AiDjSetBuilder.savePlaylist(database, result)
            _state.update { it.copy(isSaving = false, savedPlaylistId = playlistId) }
        }
    }

    /** Leaving the screen mid-generation shouldn't leave a zombie job writing
     *  to the DB after the ViewModel that owns its state is gone. */
    override fun onCleared() {
        job?.cancel()
    }

    fun reset() {
        job?.cancel()
        _state.value = AiDjSetState()
    }
}
