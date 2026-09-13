/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.viewmodels

import android.content.Context
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
)

@HiltViewModel
class AiDjSetViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
) : ViewModel() {

    private val _state = MutableStateFlow(AiDjSetState())
    val state: StateFlow<AiDjSetState> = _state.asStateFlow()

    private var job: Job? = null

    fun generate(prompt: String?, seedSongId: String?, songCount: Int) {
        if (_state.value.isRunning) return
        _state.value = AiDjSetState(isRunning = true)

        job = viewModelScope.launch {
            val result = AiDjSetBuilder.buildSet(
                context = context,
                database = database,
                prompt = prompt,
                seedSongId = seedSongId,
                songCount = songCount,
                onLog = { line -> _state.update { it.copy(logs = it.logs + line) } },
            )
            _state.update { it.copy(isRunning = false, result = result, failed = result == null) }
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
