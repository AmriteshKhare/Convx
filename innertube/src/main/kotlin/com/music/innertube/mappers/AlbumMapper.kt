package com.music.innertube.mappers

import com.music.innertube.InnerTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.Artist
import com.music.innertube.models.Run
import com.music.innertube.models.SongItem
import com.music.innertube.models.YouTubeClient.Companion.WEB_REMIX
import com.music.innertube.models.getContinuation
import com.music.innertube.models.getItems
import com.music.innertube.models.oddElements
import com.music.innertube.models.response.BrowseResponse
import com.music.innertube.pages.AlbumPage
import com.music.innertube.pages.NewReleaseAlbumPage
import io.ktor.client.call.body

internal object AlbumMapper {
    suspend fun album(innerTube: InnerTube, browseId: String, withSongs: Boolean): Result<AlbumPage> = runCatching {
        val response = innerTube.browse(WEB_REMIX, browseId, setLogin = true).body<BrowseResponse>()

        fun mapRuns(runs: List<Run>?): List<Run>? = runs?.map { run ->
            Run(
                text = run.text,
                navigationEndpoint = run.navigationEndpoint
            )
        }

        val descriptionRuns = sequence {
            // Check all tabs in twoColumnBrowseResultsRenderer
            response.contents?.twoColumnBrowseResultsRenderer?.tabs?.forEach { tab ->
                tab?.tabRenderer?.content?.sectionListRenderer?.contents?.forEach { content ->
                    content.musicDescriptionShelfRenderer?.description?.runs?.let { yield(it) }
                }
            }
            // Check all tabs in singleColumnBrowseResultsRenderer
            response.contents?.singleColumnBrowseResultsRenderer?.tabs?.forEach { tab ->
                tab.tabRenderer?.content?.sectionListRenderer?.contents?.forEach { content ->
                    content.musicDescriptionShelfRenderer?.description?.runs?.let { yield(it) }
                }
            }
            // Check headers
            response.header?.musicDetailHeaderRenderer?.description?.runs?.let { yield(it) }
            response.header?.musicImmersiveHeaderRenderer?.description?.runs?.let { yield(it) }
            response.header?.musicEditablePlaylistDetailHeaderRenderer?.header?.musicDetailHeaderRenderer?.description?.runs?.let { yield(it) }
            response.header?.musicEditablePlaylistDetailHeaderRenderer?.header?.musicResponsiveHeaderRenderer?.description?.runs?.let { yield(it) }
            
            // Check musicResponsiveHeaderRenderer in contents
            response.contents?.twoColumnBrowseResultsRenderer?.tabs?.forEach { tab ->
                tab?.tabRenderer?.content?.sectionListRenderer?.contents?.forEach { content ->
                    content.musicResponsiveHeaderRenderer?.description?.runs?.let { yield(it) }
                }
            }
            response.contents?.singleColumnBrowseResultsRenderer?.tabs?.forEach { tab ->
                tab.tabRenderer?.content?.sectionListRenderer?.contents?.forEach { content ->
                    content.musicResponsiveHeaderRenderer?.description?.runs?.let { yield(it) }
                }
            }
        }.firstOrNull()?.let(::mapRuns)

        val description = descriptionRuns?.joinToString(separator = "") { it.text }

        if (browseId.contains("FEmusic_library_privately_owned_release_detail")) {
            val playlistId =
                response.header?.musicDetailHeaderRenderer?.menu?.menuRenderer?.topLevelButtons?.firstOrNull()?.buttonRenderer?.navigationEndpoint?.watchPlaylistEndpoint?.playlistId!!
            val albumItem = AlbumItem(
                browseId = browseId,
                playlistId = playlistId,
                title = response.header.musicDetailHeaderRenderer.title.runs?.firstOrNull()?.text!!,
                artists = response.header.musicDetailHeaderRenderer.subtitle.runs?.filter { it.navigationEndpoint != null }?.map {
                    Artist(
                        name = it.text,
                        id = it.navigationEndpoint?.browseEndpoint?.browseId
                    )
                },
                year = response.header.musicDetailHeaderRenderer.subtitle.runs?.lastOrNull()?.text?.toIntOrNull(),
                thumbnail = response.header.musicDetailHeaderRenderer.thumbnail.croppedSquareThumbnailRenderer?.thumbnail?.thumbnails?.lastOrNull()!!.url,
                explicit = false, // TODO: Extract explicit badge for albums from YouTube response
                description = description
            )
            return@runCatching AlbumPage(
                album = albumItem,
                songs = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()?.musicShelfRenderer?.contents?.getItems()?.mapNotNull {
                    AlbumPage.getSong(it, albumItem)
                }!!.toMutableList(),
                otherVersions = emptyList(),
                description = description,
                descriptionRuns = descriptionRuns
            )
        } else {
            val playlistId =
                response.microformat?.microformatDataRenderer?.urlCanonical?.substringAfterLast('=')!!
            val albumItem = AlbumItem(
                browseId = browseId,
                playlistId = playlistId,
                title = response.contents?.twoColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()?.musicResponsiveHeaderRenderer?.title?.runs?.firstOrNull()?.text!!,
                artists = response.contents.twoColumnBrowseResultsRenderer.tabs.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()?.musicResponsiveHeaderRenderer?.straplineTextOne?.runs?.oddElements()
                    ?.map {
                        Artist(
                            name = it.text,
                            id = it.navigationEndpoint?.browseEndpoint?.browseId
                        )
                    }!!,
                year = response.contents.twoColumnBrowseResultsRenderer.tabs.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()?.musicResponsiveHeaderRenderer?.subtitle?.runs?.lastOrNull()?.text?.toIntOrNull(),
                thumbnail = response.contents.twoColumnBrowseResultsRenderer.tabs.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()?.musicResponsiveHeaderRenderer?.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails?.lastOrNull()?.url!!,
                explicit = false, // TODO: Extract explicit badge for albums from YouTube response
                description = description
            )
            return@runCatching AlbumPage(
                album = albumItem,
                songs = if (withSongs) albumSongs(
                    innerTube, playlistId, albumItem
                ).getOrThrow() else emptyList(),
                otherVersions = response.contents.twoColumnBrowseResultsRenderer.secondaryContents?.sectionListRenderer?.contents
                    ?.find { it.musicCarouselShelfRenderer?.header?.musicCarouselShelfBasicHeaderRenderer?.title?.runs?.any { it.text.contains("versions", ignoreCase = true) } == true }
                    ?.musicCarouselShelfRenderer?.contents
                    ?.mapNotNull { it.musicTwoRowItemRenderer }
                    ?.mapNotNull(NewReleaseAlbumPage::fromMusicTwoRowItemRenderer)
                    .orEmpty(),
                releasesForYou = response.contents.twoColumnBrowseResultsRenderer.secondaryContents?.sectionListRenderer?.contents
                    ?.find { it.musicCarouselShelfRenderer?.header?.musicCarouselShelfBasicHeaderRenderer?.title?.runs?.any { it.text.contains("releases", ignoreCase = true) || it.text.contains("more from", ignoreCase = true) } == true }
                    ?.musicCarouselShelfRenderer?.contents
                    ?.mapNotNull { it.musicTwoRowItemRenderer }
                    ?.mapNotNull(NewReleaseAlbumPage::fromMusicTwoRowItemRenderer)
                    .orEmpty(),
                description = description,
                descriptionRuns = descriptionRuns
            )
        }
    }

    suspend fun albumSongs(innerTube: InnerTube, playlistId: String, album: AlbumItem?): Result<List<SongItem>> = runCatching {
        var response = innerTube.browse(WEB_REMIX, "VL$playlistId").body<BrowseResponse>()
        val songs = response.contents?.twoColumnBrowseResultsRenderer
            ?.secondaryContents?.sectionListRenderer
            ?.contents?.firstOrNull()
            ?.musicPlaylistShelfRenderer?.contents?.getItems()
            ?.mapNotNull {
                AlbumPage.getSong(it, album)
            }!!
            .toMutableList()
        var continuation = response.contents.twoColumnBrowseResultsRenderer.secondaryContents.sectionListRenderer
            .contents.firstOrNull()?.musicPlaylistShelfRenderer?.contents?.getContinuation()
        val seenContinuations = mutableSetOf<String>()
        var requestCount = 0
        val maxRequests = 50 // Prevent excessive API calls
        
        while (continuation != null && requestCount < maxRequests) {
            // Prevent infinite loops by tracking seen continuations
            if (continuation in seenContinuations) {
                break
            }
            seenContinuations.add(continuation)
            requestCount++
            
            response = innerTube.browse(
                client = WEB_REMIX,
                continuation = continuation,
            ).body<BrowseResponse>()
            songs += response.onResponseReceivedActions?.firstOrNull()?.appendContinuationItemsAction?.continuationItems?.getItems()?.mapNotNull {
                AlbumPage.getSong(it, album)
            }.orEmpty()
            continuation = response.continuationContents?.musicPlaylistShelfContinuation?.continuations?.getContinuation()
        }
        songs
    }
}