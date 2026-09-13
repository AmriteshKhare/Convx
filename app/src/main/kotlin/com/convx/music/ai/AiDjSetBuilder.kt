/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ai

import android.content.Context
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.convx.music.db.MusicDatabase
import com.convx.music.db.entities.PlaylistEntity
import com.convx.music.db.entities.PlaylistSongMap
import com.convx.music.db.entities.SongAnalysisEntity
import com.convx.music.db.entities.SongEntity
import com.convx.music.playback.audio.DjSetOrderer
import com.convx.music.playback.dj.BeatGrid
import com.convx.music.playback.dj.MusicalKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger

/** How many of the user's library songs get offered to the LLM as candidates
 *  — bounded so a large library doesn't blow up the prompt. */
private const val MAX_LIBRARY_CANDIDATES = 150

data class AiDjSetResult(
    val playlistId: String,
    val playlistName: String,
    val orderedTitles: List<String>,
    val unmatchedTitles: List<String>,
)

/**
 * Builds a new DJ-ready playlist from a vibe prompt, a seed song, or both.
 *
 * Song *selection* is the LLM's job (or, with a seed and no prompt, no LLM at
 * all — see below); song *ordering* never touches the LLM — [DjSetOrderer]
 * sequences whatever gets picked using the same BPM/key scoring the mixer
 * itself uses at playback time, which is a thing LLMs are unreliable at and
 * plain arithmetic is exact at.
 */
object AiDjSetBuilder {

    /**
     * At least one of [prompt]/[seedSongId] must be given.
     *
     * A [seedSongId] with a blank/null [prompt] skips the AI call entirely:
     * the candidate pool is just that song's YouTube "related" results,
     * ordered directly. That path needs no API key and costs nothing — the
     * local/free option. A non-blank [prompt] asks the configured AI provider
     * to pick [songCount] songs fitting the vibe from the library (plus the
     * seed's related songs, when there is a seed); if that call fails for any
     * reason, a seed's related songs are used as a fallback instead of
     * failing outright.
     */
    suspend fun buildSet(
        context: Context,
        database: MusicDatabase,
        prompt: String?,
        seedSongId: String?,
        songCount: Int,
        onLog: suspend (String) -> Unit,
    ): AiDjSetResult? = withContext(Dispatchers.IO) {
        val trimmedPrompt = prompt?.trim().orEmpty()
        if (trimmedPrompt.isEmpty() && seedSongId.isNullOrBlank()) {
            onLog("Need a vibe or a song to start from.")
            return@withContext null
        }

        onLog(
            "Gathering candidates... (prompt=${if (trimmedPrompt.isEmpty()) "none" else "\"$trimmedPrompt\""}, " +
                "seed=${seedSongId ?: "none"})"
        )
        val related = LinkedHashMap<String, SongItem>()
        // The seed itself is already a local song (this is always launched from
        // a song the user already has), not one of its own "related" results —
        // fetch it directly rather than hoping it shows up in that list.
        val seedSong = if (!seedSongId.isNullOrBlank()) database.song(seedSongId).first() else null
        if (!seedSongId.isNullOrBlank()) {
            val nextResult = YouTube.next(WatchEndpoint(videoId = seedSongId))
            val endpoint = nextResult.getOrNull()?.relatedEndpoint
            if (endpoint != null) {
                val page = YouTube.related(endpoint)
                page.onFailure { onLog("Related songs request failed: ${it.message}") }
                page.getOrNull()?.songs?.forEach { if (it.id != seedSongId) related[it.id] = it }
            }
            if (related.isEmpty()) {
                // No related endpoint (local file, or YouTube just has nothing to
                // relate it to) — fall back to more songs by the same artist
                // instead of a dead end.
                val artist = seedSong?.artists?.firstOrNull()?.name
                if (!artist.isNullOrBlank()) {
                    onLog("No related songs for the seed — searching more by $artist instead.")
                    YouTube.search(artist, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                        ?.items?.filterIsInstance<SongItem>()
                        ?.forEach { if (it.id != seedSongId) related[it.id] = it }
                }
            }
            onLog("Found ${related.size} candidates from the seed.")
        }

        val chosenTitles = mutableListOf<Pair<String, String>>() // title to artist
        var usedAi = false

        if (trimmedPrompt.isNotEmpty()) {
            onLog("Asking AI to pick songs for the vibe...")
            val librarySongs = database.allSearchableSongs().first().take(MAX_LIBRARY_CANDIDATES)
            val poolJson = JSONArray().apply {
                related.values.forEach {
                    put(JSONObject().apply {
                        put("title", it.title)
                        put("artist", it.artists.joinToString { a -> a.name })
                    })
                }
                librarySongs.forEach {
                    put(JSONObject().apply {
                        put("title", it.song.title)
                        put("artist", it.artists.firstOrNull()?.name.orEmpty())
                    })
                }
            }.toString()

            val systemPrompt = """
                You are a DJ curating a set for the user. They describe a vibe or mood.
                Candidate songs, prefer these when they fit (title/artist pairs):
                $poolJson

                Pick exactly $songCount songs that best match the requested vibe. You may
                suggest a well-known song not in the candidate list if nothing there fits.
                Output ONLY a JSON array of objects: [{"title": "...", "artist": "..."}].
                Do NOT include markdown formatting, explanations, or any other text.
            """.trimIndent()

            val response = AiChatClient.complete(context, systemPrompt, trimmedPrompt, onLog)
            if (response != null) {
                val clean = response.replace("```json", "").replace("```", "").trim()
                try {
                    val array = JSONArray(clean)
                    for (i in 0 until array.length()) {
                        val obj = array.optJSONObject(i) ?: continue
                        val title = obj.optString("title")
                        if (title.isNotBlank()) {
                            chosenTitles += title to obj.optString("artist")
                            usedAi = true
                        }
                    }
                } catch (e: Exception) {
                    onLog("Couldn't parse the AI response, falling back to related songs.")
                }
            }
        }

        val resolved = LinkedHashMap<String, SongItem>()
        val unmatched = mutableListOf<String>()

        if (usedAi && chosenTitles.isNotEmpty()) {
            onLog("Resolving ${chosenTitles.size} songs...")
            val semaphore = Semaphore(4)
            val done = AtomicInteger(0)
            val results = chosenTitles.map { (title, artist) ->
                async {
                    semaphore.withPermit {
                        val fromRelated = related.values.firstOrNull { it.title.equals(title, ignoreCase = true) }
                        val item = fromRelated ?: run {
                            val query = if (artist.isBlank()) title else "$title $artist"
                            YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                                ?.items?.filterIsInstance<SongItem>()?.firstOrNull()
                        }
                        val count = done.incrementAndGet()
                        onLog("Resolving [$count/${chosenTitles.size}]: $title")
                        title to item
                    }
                }
            }.awaitAll()
            results.forEach { (title, item) ->
                if (item != null) resolved[item.id] = item else unmatched += title
            }
        } else {
            // No prompt, or the AI call didn't produce anything usable: fall
            // back to the seed's related songs directly, no network beyond
            // what was already fetched above.
            onLog("Using ${minOf(related.size, songCount)} related songs directly (no vibe given).")
            related.values.take(songCount).forEach { resolved[it.id] = it }
        }

        if (resolved.isEmpty()) {
            onLog("No songs found.")
            return@withContext null
        }

        onLog("Ordering for the mix...")
        val ids = resolved.keys.toMutableList()
        if (seedSongId != null) {
            // Always present and always first, regardless of whether it also
            // turned up as a "related" candidate — this is the song the set
            // was asked to be built from.
            ids.remove(seedSongId)
            ids.add(0, seedSongId)
        }
        val analysisRows = database.songAnalyses(ids, SongAnalysisEntity.CURRENT_ANALYSIS_VERSION)
        val analysisMap = analysisRows.associate {
            it.id to (
                BeatGrid(it.bpm, it.anchorBeatMs, it.bpmConfidence) to
                    MusicalKey(it.keyIndex, it.keyIsMinor, it.keyConfidence)
                )
        }
        val orderedIds = DjSetOrderer.order(ids, analysisMap)
        val seedTitle = seedSong?.song?.title

        val name = when {
            trimmedPrompt.isNotEmpty() && seedTitle != null -> "AI DJ Mix: $seedTitle + $trimmedPrompt"
            trimmedPrompt.isNotEmpty() -> "AI DJ Mix: $trimmedPrompt"
            seedTitle != null -> "AI DJ Mix: $seedTitle"
            else -> "AI DJ Mix"
        }.take(100)

        onLog("Creating playlist...")
        val playlistId = PlaylistEntity.generatePlaylistId()
        database.insert(
            PlaylistEntity(
                id = playlistId,
                name = name,
                bookmarkedAt = LocalDateTime.now(),
                lastUpdateTime = LocalDateTime.now(),
                thumbnailUrl = seedSong?.song?.thumbnailUrl,
                isEditable = true,
            )
        )
        orderedIds.forEachIndexed { index, id ->
            // The seed is already a local song — nothing to upsert, it just
            // joins the new playlist at its ordered position.
            resolved[id]?.let { item ->
                database.upsert(
                    SongEntity(
                        id = item.id,
                        title = item.title,
                        duration = item.duration ?: 0,
                        thumbnailUrl = item.thumbnail,
                    )
                )
            }
            database.insert(PlaylistSongMap(playlistId = playlistId, songId = id, position = index))
        }

        onLog("Done! Created \"$name\" with ${orderedIds.size} songs.")
        AiDjSetResult(
            playlistId = playlistId,
            playlistName = name,
            orderedTitles = orderedIds.map { id -> resolved[id]?.title ?: seedTitle.takeIf { id == seedSongId } ?: id },
            unmatchedTitles = unmatched,
        )
    }
}
