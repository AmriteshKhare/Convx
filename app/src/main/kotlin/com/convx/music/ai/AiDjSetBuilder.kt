/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ai

import android.content.Context
import androidx.media3.common.MediaItem
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.convx.music.db.MusicDatabase
import com.convx.music.db.entities.PlaylistEntity
import com.convx.music.db.entities.PlaylistSongMap
import com.convx.music.db.entities.Song
import com.convx.music.db.entities.SongAnalysisEntity
import com.convx.music.db.entities.SongEntity
import com.convx.music.extensions.toMediaItem
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

/** One AI-picked song plus where it sits in a concert-mode set. Ignored
 *  entirely outside [AiDjSetBuilder.buildSet]'s concertMode path. */
private data class ChosenSong(val title: String, val artist: String, val role: String)

/** Narrative order a concert-mode set is assembled in — see
 *  [AiDjSetBuilder.buildSet]'s ordering step. */
private val CONCERT_ROLES = listOf("opener", "rising", "peak", "closer")

data class AiDjSetResult(
    val playlistName: String,
    val orderedTitles: List<String>,
    val unmatchedTitles: List<String>,
    /** Plain-language summary of how the set was actually built — so it's
     *  visible whether the AI curated it or it's the seed's related songs
     *  passed straight through, rather than the two looking identical. */
    val method: String,
    /** Song ids in final playback order — the seed (if any) first. Not
     *  persisted anywhere until [AiDjSetBuilder.savePlaylist] is called. */
    val orderedIds: List<String>,
    /** Resolved AI/related picks, keyed by id. Excludes the seed, which is
     *  already a local library song — see [seedSong]. */
    val songsById: Map<String, SongItem>,
    /** The local library song this set was seeded from, if any. */
    val seedSong: Song?,
)

/**
 * Designs a DJ-ready set from a vibe prompt, a seed song, or an artist/genre
 * concert theme — and hands back the result to preview or play immediately.
 * Nothing is written to the database until [savePlaylist] is called
 * separately: generating a set the user doesn't like should cost nothing to
 * undo.
 *
 * Song *selection* is the LLM's job (or, with a seed and no prompt outside
 * concert mode, no LLM at all — see below). Song *ordering* is either the
 * LLM's own sequence (concert mode — see [buildSet]'s concertMode doc) or
 * [DjSetOrderer], which sequences by the same BPM/key scoring the mixer
 * itself uses at playback time.
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
    /** Assumed average track length, used only to convert a duration target
     *  into an approximate song count for the AI prompt / fallback loop. */
    private const val ASSUMED_AVERAGE_SONG_SECONDS = 210

    suspend fun buildSet(
        context: Context,
        database: MusicDatabase,
        prompt: String?,
        seedSongId: String?,
        songCount: Int,
        /** When set, overrides [songCount]: keep adding songs until their
         *  combined duration reaches this many seconds instead of a fixed
         *  count. */
        targetDurationSeconds: Long? = null,
        /** Designs the set like a real live show instead of a flat list: the
         *  AI researches the artist/genre/vibe from its own knowledge, tags
         *  each pick opener/rising/peak/closer, and — unlike the plain vibe
         *  path — the AI's own output order is trusted as the running order
         *  directly, on the idea that a real setlist's flow (which song
         *  follows which) is itself part of what's being designed, not
         *  something a BPM-nearest-neighbour pass should re-decide afterward.
         *  Always goes through the AI, even with a blank [prompt] (a seed's
         *  artist becomes the theme) — there's no non-AI way to design a set
         *  arc. */
        concertMode: Boolean = false,
        onLog: suspend (String) -> Unit,
    ): AiDjSetResult? = withContext(Dispatchers.IO) {
        val effectiveSongCount = targetDurationSeconds
            ?.let { (it / ASSUMED_AVERAGE_SONG_SECONDS).toInt().coerceAtLeast(1) }
            ?: songCount
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

        // What the AI is actually designing a set for — the typed prompt, or
        // (concert mode only) the seed's own artist as a synthesised theme,
        // since a concert needs *something* to build an arc around.
        val conceptLabel = trimmedPrompt.ifBlank {
            seedSong?.artists?.firstOrNull()?.name ?: seedSong?.song?.title.orEmpty()
        }
        val useAi = trimmedPrompt.isNotEmpty() || (concertMode && conceptLabel.isNotBlank())

        if (concertMode && conceptLabel.isNotBlank()) {
            // The seed's "related" results skew toward whatever YouTube
            // associates loosely with it — similar artists, generic radio —
            // which is exactly the "artist-related, not what the artist would
            // actually perform" drift this is trying to avoid. A direct
            // search for the concert's own theme gives the AI real
            // same-artist/same-vibe material as a hint, though the AI is told
            // below to lean on its own knowledge of the artist over this list.
            YouTube.search(conceptLabel, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                ?.items?.filterIsInstance<SongItem>()
                ?.forEach { if (it.id != seedSongId) related.putIfAbsent(it.id, it) }
        }

        val chosenSongs = mutableListOf<ChosenSong>()
        var usedAi = false

        if (useAi) {
            onLog(
                if (concertMode) "Asking AI to design a concert set for \"$conceptLabel\"..."
                else "Asking AI to pick songs for the vibe..."
            )
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

            val systemPrompt = if (concertMode) {
                """
                    You are a knowledgeable concert-setlist designer with real,
                    accurate knowledge of "$conceptLabel"'s discography and how
                    their live shows are actually structured. Research this
                    from what you genuinely know — don't guess or pad with
                    generic genre filler.

                    Rules:
                    - Prefer this artist's own real songs (or, if this is a
                      genre/vibe rather than one artist, songs that genuinely
                      fit it) — not merely similar artists or a loose radio mix.
                    - Where it's true to how this artist/genre actually performs
                      live, you may include a well-known remix, mashup, or VIP
                      edit instead of the plain studio version — use judgement,
                      don't force it if their real sets are mostly album cuts.
                    - Include a known walk-on/intro moment if this artist
                      genuinely opens their real shows with one.
                    - The candidates below are a hint, not a limit — use your
                      own knowledge of real songs first, and only fall back to
                      this list if you don't know better ones:
                    $poolJson

                    Design the full running order as an actual concert arc,
                    structured into these parts, in this order:
                    - opener: 1 song that eases the crowd in, or a known live intro
                    - rising: a few songs building energy
                    - peak: the biggest hits, highest energy — most of the set
                    - closer: 1-2 songs that wind the set down, or a known encore/finale

                    List the songs in the exact sequence they should play — the
                    order you output IS the running order used directly, with no
                    reordering afterward, so put real thought into what follows
                    what for a natural energy/tempo flow, not just genre match.

                    Pick exactly $effectiveSongCount songs total across all four parts.
                    Output ONLY a JSON array of objects, in that exact sequence:
                    [{"title": "...", "artist": "...", "role": "opener|rising|peak|closer"}].
                    Do NOT include markdown formatting, explanations, or any other text.
                """.trimIndent()
            } else {
                """
                    You are a DJ curating a set for the user. They describe a vibe or mood.
                    Candidate songs, prefer these when they fit (title/artist pairs):
                    $poolJson

                    Pick exactly $effectiveSongCount songs that best match the requested vibe. You may
                    suggest a well-known song not in the candidate list if nothing there fits.
                    Output ONLY a JSON array of objects: [{"title": "...", "artist": "..."}].
                    Do NOT include markdown formatting, explanations, or any other text.
                """.trimIndent()
            }

            val response = AiChatClient.complete(context, systemPrompt, conceptLabel, onLog)
            if (response != null) {
                val clean = response.replace("```json", "").replace("```", "").trim()
                try {
                    val array = JSONArray(clean)
                    for (i in 0 until array.length()) {
                        val obj = array.optJSONObject(i) ?: continue
                        val title = obj.optString("title")
                        if (title.isNotBlank()) {
                            val role = obj.optString("role").lowercase().takeIf { it in CONCERT_ROLES } ?: "peak"
                            chosenSongs += ChosenSong(title, obj.optString("artist"), role)
                            usedAi = true
                        }
                    }
                } catch (e: Exception) {
                    onLog("Couldn't parse the AI response, falling back to related songs.")
                }
            }
            if (usedAi) {
                onLog("AI picked: " + chosenSongs.joinToString { "${it.title} — ${it.artist} [${it.role}]" })
            }
        }

        val resolved = LinkedHashMap<String, SongItem>()
        val roleById = mutableMapOf<String, String>()
        val unmatched = mutableListOf<String>()

        if (usedAi && chosenSongs.isNotEmpty()) {
            onLog("Resolving ${chosenSongs.size} songs...")
            val semaphore = Semaphore(4)
            val done = AtomicInteger(0)
            // async{}.awaitAll() preserves list order regardless of completion
            // order — load-bearing for concert mode, which trusts this order
            // as the final running order.
            val results = chosenSongs.map { chosen ->
                async {
                    semaphore.withPermit {
                        val fromRelated = related.values.firstOrNull { it.title.equals(chosen.title, ignoreCase = true) }
                        val item = fromRelated ?: run {
                            val query = if (chosen.artist.isBlank()) chosen.title else "${chosen.title} ${chosen.artist}"
                            YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                                ?.items?.filterIsInstance<SongItem>()?.firstOrNull()
                        }
                        val count = done.incrementAndGet()
                        onLog("Resolving [$count/${chosenSongs.size}]: ${chosen.title}")
                        chosen to item
                    }
                }
            }.awaitAll()
            results.forEach { (chosen, item) ->
                if (item != null) {
                    resolved[item.id] = item
                    roleById[item.id] = chosen.role
                } else {
                    unmatched += chosen.title
                }
            }
        } else {
            // No prompt, or the AI call didn't produce anything usable: fall
            // back to the seed's related songs directly, no network beyond
            // what was already fetched above.
            if (targetDurationSeconds != null) {
                var totalSeconds = 0L
                for (item in related.values) {
                    if (totalSeconds >= targetDurationSeconds) break
                    resolved[item.id] = item
                    totalSeconds += item.duration ?: ASSUMED_AVERAGE_SONG_SECONDS
                }
            } else {
                related.values.take(effectiveSongCount).forEach { resolved[it.id] = it }
            }
            onLog("Using ${resolved.size} related songs directly (no vibe given).")
        }

        if (resolved.isEmpty()) {
            onLog("No songs found.")
            return@withContext null
        }

        val method = when {
            concertMode && usedAi -> "AI-designed concert set for \"$conceptLabel\" (opener, rising, peak, closer)"
            usedAi && trimmedPrompt.isNotEmpty() -> "AI-curated from your vibe"
            seedSong != null -> "Related songs for \"${seedSong.song.title}\", no AI involved"
            else -> "Related songs, no AI involved"
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

        val orderedIds = if (concertMode && roleById.isNotEmpty()) {
            // Trust the AI's own sequence: bucket by role, preserving the
            // order it was returned in within (and across) buckets, rather
            // than re-sorting by BPM/key. A real setlist's flow is part of
            // what was just designed, backed by the model's own knowledge of
            // the artist — a nearest-neighbour pass afterward would throw
            // that reasoning away in favour of a heuristic that knows nothing
            // about the songs beyond a cached tempo, if one even exists yet.
            val opener = listOfNotNull(seedSongId) + ids.filter { it != seedSongId && roleById[it] == "opener" }
            val rest = CONCERT_ROLES.drop(1).flatMap { role -> ids.filter { it != seedSongId && roleById[it] == role } }
            // Every resolved id was tagged with a role during parsing (unknown
            // roles default to "peak"), so nothing should be left over here —
            // kept as a safety net rather than a reason to drop a song.
            val placed = opener + rest
            placed + ids.filterNot { it in placed }
        } else {
            val analysisRows = database.songAnalyses(ids, SongAnalysisEntity.CURRENT_ANALYSIS_VERSION)
            val analysisMap = analysisRows.associate {
                it.id to (
                    BeatGrid(it.bpm, it.anchorBeatMs, it.bpmConfidence) to
                        MusicalKey(it.keyIndex, it.keyIsMinor, it.keyConfidence)
                    )
            }
            DjSetOrderer.order(ids, analysisMap)
        }
        val seedTitle = seedSong?.song?.title

        val name = when {
            concertMode -> "Concert: $conceptLabel"
            trimmedPrompt.isNotEmpty() && seedTitle != null -> "AI DJ Mix: $seedTitle + $trimmedPrompt"
            trimmedPrompt.isNotEmpty() -> "AI DJ Mix: $trimmedPrompt"
            seedTitle != null -> "AI DJ Mix: $seedTitle"
            else -> "AI DJ Mix"
        }.take(100)

        onLog("Done! Built \"$name\" with ${orderedIds.size} songs.")
        AiDjSetResult(
            playlistName = name,
            orderedTitles = orderedIds.map { id -> resolved[id]?.title ?: seedTitle.takeIf { id == seedSongId } ?: id },
            unmatchedTitles = unmatched,
            method = method,
            orderedIds = orderedIds,
            songsById = resolved,
            seedSong = seedSong,
        )
    }

    /**
     * Persists a previously built [result] as a real playlist. Kept separate
     * from [buildSet] on purpose: generating a set is free to preview and
     * replay, and should cost nothing to discard — this is the only step
     * that actually writes to the library.
     */
    suspend fun savePlaylist(database: MusicDatabase, result: AiDjSetResult): String = withContext(Dispatchers.IO) {
        val playlistId = PlaylistEntity.generatePlaylistId()
        database.insert(
            PlaylistEntity(
                id = playlistId,
                name = result.playlistName,
                bookmarkedAt = LocalDateTime.now(),
                lastUpdateTime = LocalDateTime.now(),
                thumbnailUrl = result.seedSong?.song?.thumbnailUrl ?: result.songsById.values.firstOrNull()?.thumbnail,
                isEditable = true,
            )
        )
        result.orderedIds.forEachIndexed { index, id ->
            // The seed is already a local song — nothing to upsert, it just
            // joins the new playlist at its ordered position.
            result.songsById[id]?.let { item ->
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
        playlistId
    }
}

/** Builds a queue for immediate playback without saving anything — the
 *  "try it before you commit to it" path [AiDjSetBuilder.buildSet] exists for. */
fun AiDjSetResult.toMediaItems(): List<MediaItem> = orderedIds.mapNotNull { id ->
    songsById[id]?.toMediaItem() ?: seedSong?.takeIf { it.song.id == id }?.toMediaItem()
}
