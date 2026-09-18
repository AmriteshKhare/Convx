/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.screens.artist

import com.convx.music.ui.utils.appTopBarWindowInsets
import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.unit.Dp
import com.convx.music.ui.theme.AppleColors
import com.convx.music.ui.component.GlassPillRow
import com.convx.music.ui.component.StretchPillIcon
import com.convx.music.ui.component.pillGlassBackground
import com.convx.music.ui.component.LocalGlassEffectConfig
import com.convx.music.ui.component.resolvedTextColor
import com.convx.music.ui.utils.bounceClick
import com.convx.music.ui.utils.combinedBounceClick
import com.convx.music.ui.utils.albumRoute
import com.convx.music.ui.utils.artistRoute
import com.convx.music.ui.utils.onlinePlaylistRoute
import com.convx.music.ui.utils.bleedStart
import com.convx.music.ui.utils.plusStart

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import com.convx.music.ui.ios.compat.HorizontalDivider
import com.convx.music.ui.ios.compat.Icon
import com.convx.music.ui.ios.compat.IconButton
import com.convx.music.ui.ios.compat.LocalContentColor
import com.convx.music.ui.ios.compat.AppTheme
import com.convx.music.ui.ios.compat.OutlinedButton
import com.convx.music.ui.ios.compat.ripple
import com.convx.music.ui.ios.compat.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import com.convx.music.ui.ios.compat.Surface
import com.convx.music.ui.ios.compat.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.composables.sfsymbols.SfSymbols
import com.composables.sfsymbols.monochrome.SFHeart
import com.composables.sfsymbols.monochrome.SFHeartFill
import com.composables.sfsymbols.monochrome.SFInfoCircle
import com.composables.sfsymbols.monochrome.SFLink
import com.composables.sfsymbols.monochrome.SFMusicMicrophone
import com.composables.sfsymbols.monochrome.SFPlus
import com.composables.sfsymbols.monochrome.SFStar
import com.composables.sfsymbols.monochrome.SFChevronRight
import com.convx.music.LocalTabView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import com.convx.music.ui.component.ArtistHeroImage
import com.convx.music.ui.component.HeroCardHeader
import com.convx.music.ui.component.TopFadeBlur
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import coil3.compose.AsyncImage
import com.convx.music.ui.utils.additiveChrome
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.convx.music.LocalDatabase
import com.convx.music.LocalListenTogetherManager
import com.convx.music.LocalPlayerAwareWindowInsets
import com.convx.music.LocalPlayerConnection
import com.convx.music.R
import com.convx.music.constants.AppBarHeight
import com.convx.music.constants.HideExplicitKey
import com.convx.music.db.entities.ArtistEntity
import com.convx.music.extensions.toMediaItem
import com.convx.music.models.toMediaMetadata
import com.convx.music.playback.queues.ListQueue
import com.convx.music.playback.queues.YouTubeQueue
import com.convx.music.ui.component.AnimatedPlayPauseIcon
import com.convx.music.ui.component.AlbumGridItem
import com.convx.music.ui.component.ExpandableText
import com.convx.music.ui.component.GlassCircleButton
import com.convx.music.ui.component.HideOnScrollFAB
import com.convx.music.ui.component.LinkSegment
import com.convx.music.ui.component.LocalAppBackdrop
import com.convx.music.ui.component.LocalMenuState
import com.convx.music.ui.component.backdrop.backdrops.layerBackdrop
import com.convx.music.ui.component.backdrop.backdrops.rememberBackdropFreeze
import com.convx.music.ui.component.backdrop.backdrops.rememberLayerBackdrop
import com.convx.music.ui.component.NavigationTitle
import com.convx.music.ui.component.SongListItem
import com.convx.music.ui.component.YouTubeGridItem
import com.convx.music.ui.component.YouTubeListItem
import com.convx.music.ui.theme.HeroTintedContent
import com.convx.music.ui.component.rememberHeroTint
import com.convx.music.ui.component.shimmer.ButtonPlaceholder
import com.convx.music.ui.component.shimmer.ListItemPlaceHolder
import com.convx.music.ui.component.shimmer.ShimmerHost
import com.convx.music.ui.component.shimmer.TextPlaceholder
import com.convx.music.ui.menu.AlbumMenu
import com.convx.music.ui.menu.SongMenu
import com.convx.music.ui.menu.YouTubeAlbumMenu
import com.convx.music.ui.menu.YouTubeArtistMenu
import com.convx.music.ui.menu.YouTubePlaylistMenu
import com.convx.music.ui.menu.YouTubeSongMenu
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.convx.music.ui.ios.foundation.IosTheme
import com.convx.music.ui.ios.foundation.LocalIosColors
import com.convx.music.ui.theme.AppleTokens
import com.convx.music.ui.theme.LocalAccentTextColor
import com.convx.music.ui.theme.LocalMetadataColor
import com.convx.music.ui.theme.rememberBrandFontFamily
import com.convx.music.ui.theme.rememberCustomArtistFontFamily
import com.convx.music.ui.utils.backToMain
import com.convx.music.ui.utils.rememberHeroZoom
import com.convx.music.ui.utils.heroPullZoom
import com.convx.music.ui.utils.listOverscroll
import com.convx.music.ui.utils.fadingEdge
import com.convx.music.ui.utils.resize
import com.convx.music.utils.listItemShape
import com.convx.music.constants.PureBlackHeroBackgroundKey
import com.convx.music.utils.rememberPreference
import com.convx.music.viewmodels.ArtistViewModel
import com.valentinilk.shimmer.shimmer
import com.convx.music.artistvideo.ArtistVideo
import com.convx.music.constants.DataSaverEnabledKey
import com.convx.music.constants.ShowArtistBackgroundVideoKey
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.convx.music.ui.component.floatingtabbar.gooey

/** Apple Music artist page type: white, with the secondary tiers as plain alpha steps. */
private val ArtistTextTiers = listOf(
    Color.White,
    Color.White.copy(alpha = 0.6f),
    Color.White.copy(alpha = 0.35f),
    Color.White.copy(alpha = 0.24f),
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ArtistScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: ArtistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current ?: return
    val listenTogetherManager = LocalListenTogetherManager.current
    val isGuest = listenTogetherManager?.isInRoom == true && !listenTogetherManager.isHost
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val artistPage = viewModel.artistPage
    val libraryArtist by viewModel.libraryArtist.collectAsState()
    val librarySongs by viewModel.librarySongs.collectAsState()
    val libraryAlbums by viewModel.libraryAlbums.collectAsState()
    val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)
    val dataSaverEnabled by rememberPreference(key = DataSaverEnabledKey, defaultValue = false)
    val showArtistBackgroundVideoPref by rememberPreference(key = ShowArtistBackgroundVideoKey, defaultValue = true)
    val showArtistBackgroundVideo = if (dataSaverEnabled) false else showArtistBackgroundVideoPref

    val lazyListState = rememberLazyListState()

    // Per-screen Haze source: the list content the post-hero TopFadeBlur
    // samples once scrolled past the photo.
    val hazeState = remember { HazeState() }
    val snackbarHostState = remember { SnackbarHostState() }
    var showLocal by rememberSaveable { mutableStateOf(false) }
    val density = LocalDensity.current

    // Calculate the offset value outside of the offset lambda
    val systemBarsTopPadding = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
    val headerOffset = with(density) {
        -(systemBarsTopPadding + AppBarHeight).roundToPx()
    }

    // How far the list has scrolled past the hero art, 0..1 — drives the
    // floating back/share chrome's scrim from transparent (over the art) to
    // a solid dark shade (over regular content), same progressive treatment
    // as the app's other collapsing top bars.
    val chromeScrimThresholdPx = with(density) { 220.dp.toPx() }
    val chromeScrimAlpha by remember {
        derivedStateOf {
            val scrolledPx = if (lazyListState.firstVisibleItemIndex == 0) {
                lazyListState.firstVisibleItemScrollOffset.toFloat()
            } else {
                chromeScrimThresholdPx
            }
            (scrolledPx / chromeScrimThresholdPx).coerceIn(0f, 1f)
        }
    }

    LaunchedEffect(libraryArtist) {
        // always show local page for local artists. Show local page remote artist when offline
        showLocal = libraryArtist?.artist?.isLocal == true
    }

    // Instant seed: route thumb arg -> live tile thumb -> DB/page. Paints the hero on frame
    // one; Apple's image and the page/DB overwrite when they land.
    val artistThumbnail = artistPage?.artist?.thumbnail ?: libraryArtist?.artist?.thumbnailUrl
    val instantArtistThumb = com.convx.music.ui.component.detailSeed(
        viewModel.thumbArg,
        com.convx.music.ui.utils.pendingSharedArtworkThumbFor(viewModel.artistId),
        artistThumbnail,
    )
    val artistChrome = com.convx.music.ui.component.rememberDetailChromeEntrance(iconCount = 2)

    // Glass chrome (back/share buttons, chips) samples LocalAppBackdrop. The app
    // root's backdrop (MainActivity's Modifier.layerBackdrop(appBackdrop)) captures
    // the WHOLE NavHost — so a glass surface INSIDE this screen sampling it makes
    // the capture include itself: a native RenderNode cycle (stack overflow in
    // prepareTreeImpl). A screen-local layerBackdrop doesn't help either: this
    // screen is itself inside appBackdrop, so its layer is re-recorded and
    // re-drawn within appBackdrop's own draw pass, re-forming the cycle. So we
    // provide an UNATTACHED backdrop (never .layerBackdrop'd onto anything): its
    // drawBackdrop early-returns, drawing no live refraction, but the glass still
    // renders its translucent surface tint + specular highlight — frosted chrome,
    // no self-reference. (True artwork-refracting glass here would need a capture
    // layer rendered OUTSIDE the NavHost.)
    val heroBackdrop = rememberLayerBackdrop()

    // A SECOND, ATTACHED backdrop: .layerBackdrop'd onto the LazyColumn below,
    // which is a *sibling* of the floating chrome row below, not an ancestor —
    // the chrome row samples a texture of already-drawn list content, it never
    // captures its own draw pass, so no cycle (proven working the same way on
    // the playlist/album screens — contrary to the note above, a capture layer
    // outside the NavHost turned out not to be necessary).
    val heroZoom = rememberHeroZoom()

    // Apple Music colours. The page is Apple's server-computed artwork bgColor darkened by the
    // rule fitted on the reference screenshots (AppleColors.pageBackground, 99%); artists Apple
    // has no entry for fall back to the on-device estimate of that bgColor.
    val appleArtistState = com.convx.music.utils.rememberAppleArtist(
        artistPage?.artist?.title ?: libraryArtist?.artist?.name,
    )
    val appleLoading = appleArtistState.loading
    val appleArtist = appleArtistState.artist
    // Only estimate from YouTube's art once Apple has settled without a match.
    val estimatedArtworkBg = com.convx.music.utils.rememberEstimatedArtworkBg(
        (instantArtistThumb ?: artistThumbnail).takeIf { !appleLoading && appleArtist == null },
    )
    val artworkBgRgb = appleArtist?.hero?.bgColor ?: estimatedArtworkBg ?: 0x1C1C1E
    val pageBgRgb = AppleColors.pageBackground(artworkBgRgb)
    val tint by androidx.compose.animation.animateColorAsState(AppleColors.color(pageBgRgb), label = "artistPage")
    com.convx.music.PublishAmbientBackgroundColor(tint)
    val onTint = Color.White
    val textTiers = ArtistTextTiers
    val chromeTextColor = textTiers[1]

    // Fills the screen tint into the capture BEFORE the content, exactly like
    // MainActivity's appBackdrop does with its own background. Without it the
    // list records onto a transparent canvas (the tint is painted by the outer
    // Box, outside this capture), so the blurred result is itself part
    // transparent and the sharp content shows straight through it — the glass
    // read as a doubled/ghosted image, or as no glass at all where the list is
    // sparse. This is the difference that made the nav bar look right and these
    // screens look wrong.
    val listBackdrop = rememberLayerBackdrop(
        onDraw = remember(tint) {
            val bg = tint
            { drawRect(bg); drawContent() }
        }
    )
    val backdropFreeze = rememberBackdropFreeze()

    // A flat fill of `tint` reads as a hard-edged color mismatch once the hero
    // art ends — the eased foot wash inside ArtistHeroImage settles the join
    // before the list begins instead.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(tint)
            .hazeSource(hazeState)
    ) {
    HeroTintedContent(tint = tint, backdrop = heroBackdrop) {
      // HeroTintedContent derives its colours from the flat `tint` plane, which on
      // this screen is only the mesh's dimmed base — the blobs drawn over it are what
      // the type is really read against. So the engine ladder above replaces those
      // four roles here, and everything downstream follows without a per-widget
      // colour argument: list-row titles and the hairline rules under them
      // (AppleTokens.divider) off LocalContentColor, the artist name on each row off
      // LocalMetadataColor, headings and the big page title off LocalAccentTextColor,
      // and any AppTheme.colorScheme / iOS-component read off LocalIosColors.
      val baseIosColors = IosTheme.colors
      val meshIosColors = remember(baseIosColors, textTiers) {
          baseIosColors.copy(
              label = textTiers[0],
              secondaryLabel = textTiers[1],
              tertiaryLabel = textTiers[2],
              separator = textTiers[0].copy(alpha = 0.18f),
              opaqueSeparator = textTiers[0].copy(alpha = 0.24f),
          )
      }
      CompositionLocalProvider(
          LocalAccentTextColor provides textTiers[0],
          LocalContentColor provides textTiers[0],
          // The engine's secondary tier, not its tertiary: the artist name under a
          // song title is the one piece of row metadata people actually read, and the
          // 3.0:1 tier is a step too far back for it on a bright mesh.
          LocalMetadataColor provides textTiers[1],
          LocalIosColors provides meshIosColors,
      ) {
        val sideInset = LocalPlayerAwareWindowInsets.current
            .asPaddingValues()
            .calculateStartPadding(LocalLayoutDirection.current)

        // Capture from a plain Box wrapping the LazyColumn, not the LazyColumn's
        // own modifier: LazyColumn promotes its items to their own RenderNodes
        // for scroll recycling, which a capture attached directly to it doesn't
        // reliably flatten (images came through, text/icons didn't). A plain
        // Box one level up just sees "a fully-drawn child" and captures all of
        // it, same as it would any other already-rendered composable.
        Box(modifier = Modifier
            .nestedScroll(backdropFreeze.connection)

            // OUTER layer, and it must come BEFORE layerBackdrop: the layer has to
            // enclose the backdrop node, or that node's draw re-runs whenever anything
            // else in the window redraws. The mini player, the playing indicator and
            // the position poll are all siblings that tick on their own schedule, and
            // each tick was re-recording this entire list. MainActivity pairs an outer
            // and inner layer for exactly this; the screen-local backdrops were left
            // with only the inner half.
            .graphicsLayer()
            .layerBackdrop(listBackdrop, frozen = backdropFreeze.frozen)
            // Content becomes ONE cached RenderNode, so the backdrop's
            // layer.record { drawContent() } records a single drawRenderNode
            // instead of re-issuing every op in the list.
            .graphicsLayer()) {
        LazyColumn(
            state = lazyListState,
            // No bounce here: the top pull drives the hero zoom instead.
            overscrollEffect = heroZoom.listOverscroll(),
            modifier = Modifier.heroPullZoom(heroZoom),
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        ) {
            if (artistPage == null && !showLocal) {
                item(key = "shimmer") {
                    Column(
                        modifier = Modifier
                            .offset {
                                IntOffset(x = 0, y = headerOffset)
                            }
                    ) {
                        // Hero sits outside the shimmer sweep: the seeded thumb
                        // mirrors the loaded photo statically, and no grey
                        // shimmer block ever covers the artwork area.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .shimmer()
                                    .background(Color.White.copy(alpha = 0.08f)),
                            )
                        }
                    ShimmerHost {
                        // Artist Name and Controls Section
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            // Artist Name Placeholder
                            TextPlaceholder(
                                height = 36.dp,
                                modifier = Modifier
                                    .fillMaxWidth(0.7f)
                                    .padding(bottom = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(14.dp))

                            // Buttons Row Placeholder
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Subscribe Button Placeholder
                                ButtonPlaceholder(
                                    modifier = Modifier
                                        .width(120.dp)
                                        .height(52.dp)
                                )

                                Spacer(modifier = Modifier.weight(1f))

                                // Right side buttons
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Radio Button Placeholder
                                    ButtonPlaceholder(
                                        modifier = Modifier
                                            .width(100.dp)
                                            .height(52.dp)
                                    )

                                    // Shuffle Button Placeholder
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .shimmer()
                                            .background(
                                                AppTheme.colorScheme.onSurface,
                                                RoundedCornerShape(26.dp)
                                            )
                                    )
                                }
                            }
                        }
                        // Songs List Placeholder
                        repeat(6) {
                            ListItemPlaceHolder()
                        }
                    }
                    }
                }
            } else {
                item(key = "header") {
                    val thumbnail = artistPage?.artist?.thumbnail ?: libraryArtist?.artist?.thumbnailUrl
                    val artistName = artistPage?.artist?.title ?: libraryArtist?.artist?.name
                    val displayArtistName = artistName?.titlecaseWords() ?: "Unknown"
                    val tabView = LocalTabView.current
                    val artistId = artistPage?.artist?.id
                    // YouTube queue items often carry no (or a different) artist id, so an id match
                    // alone left the button on "play" while this page's own queue was playing.
                    val queueTitle by playerConnection.queueTitle.collectAsState()
                    val currentIsThisArtist =
                        (artistId != null && mediaMetadata?.artists?.any { it.id == artistId } == true) ||
                            (artistName != null && (queueTitle == artistName ||
                                mediaMetadata?.artists?.any { it.name.equals(artistName, ignoreCase = true) } == true))
                    val isPlayingThisArtist = isPlaying && currentIsThisArtist
                    val isSubscribed = libraryArtist?.artist?.bookmarkedAt != null
                    val featured = remember(artistPage) { artistPage?.sections?.featuredRelease() }
                    var showAbout by rememberSaveable { mutableStateOf(false) }

                    val onPlay: () -> Unit = {
                        if (isPlayingThisArtist) {
                            playerConnection.player.pause()
                        } else if (currentIsThisArtist) {
                            playerConnection.player.play()
                        } else {
                            val items = artistPage?.sections
                                ?.find { it.items.firstOrNull() is SongItem }
                                ?.items?.filterIsInstance<SongItem>()
                            if (!items.isNullOrEmpty()) {
                                playerConnection.playQueue(
                                    ListQueue(title = artistName ?: "Artist", items = items.map { it.toMediaItem() }),
                                )
                            } else {
                                artistPage?.artist?.radioEndpoint?.let { playerConnection.playQueue(YouTubeQueue(it)) }
                            }
                        }
                    }
                    val onSubscribe: () -> Unit = {
                        database.transaction {
                            val artist = libraryArtist?.artist
                            if (artist != null) {
                                update(artist.toggleLike())
                            } else {
                                artistPage?.artist?.let {
                                    insert(
                                        ArtistEntity(
                                            id = it.id,
                                            name = it.title,
                                            channelId = it.channelId,
                                            thumbnailUrl = it.thumbnail,
                                        ).toggleLike(),
                                    )
                                }
                            }
                        }
                    }

                    // Hero scroll blur: fully climbed after ~90% of the photo has scrolled away.
                    // Read by the hero in its draw phase, so it is not quantised.
                    val heroBlurRangePx = LocalResources.current.displayMetrics.widthPixels * 0.9f
                    val heroScrollBlur by remember(lazyListState, heroBlurRangePx) {
                        derivedStateOf {
                            val raw = if (lazyListState.firstVisibleItemIndex == 0) {
                                lazyListState.firstVisibleItemScrollOffset / heroBlurRangePx
                            } else {
                                1f
                            }
                            raw.coerceIn(0f, 1f)
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (!tabView) {
                            // Square hero: YouTube's art immediately, Apple's own artist image
                            // crossfades over it the moment it resolves -- never blocks on the
                            // lookup when a fallback is already known.
                            val fallbackArtworkUrl = thumbnail ?: instantArtistThumb
                            ArtistHeroImage(
                                artworkUrl = fallbackArtworkUrl,
                                appleArtworkUrl = appleArtist?.hero?.sized(1200),
                                pageColor = tint,
                                heroScale = heroZoom.scale,
                                loading = appleLoading && fallbackArtworkUrl.isNullOrBlank(),
                                liveOverlay = appleArtist?.motionVideoUrl != null && showArtistBackgroundVideo,
                                scrollBlur = { heroScrollBlur },
                                modifier = Modifier.offset { IntOffset(x = 0, y = headerOffset) },
                                overlay = {
                                    val motion = appleArtist?.motionVideoUrl
                                    if (motion != null && showArtistBackgroundVideo) {
                                        ArtistVideo(videoUrl = motion, modifier = Modifier.fillMaxSize(), onClick = {})
                                    }
                                },
                            )
                        } else {
                            HeroCardHeader(
                                artworkUrl = appleArtist?.hero?.sized(800) ?: thumbnail,
                                circular = true,
                                title = {
                                    Text(
                                        text = displayArtistName,
                                        style = AppTheme.typography.headlineLarge,
                                        fontFamily = rememberCustomArtistFontFamily() ?: rememberBrandFontFamily(),
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        fontSize = 42.sp,
                                    )
                                },
                                subtitle = artistPage?.subscriberCountText?.let { subscribers ->
                                    {
                                        Text(
                                            text = subscribers,
                                            style = AppTheme.typography.titleMedium,
                                            color = textTiers[1],
                                            maxLines = 1,
                                        )
                                    }
                                },
                            )
                        }

                        // Geometry measured on the iOS 27 references (402pt screen): wordmark box
                        // 96 tall ending 10 above the hero foot, buttons 12 below it, card 32 below them.
                        val heroSide = with(density) { LocalResources.current.displayMetrics.widthPixels.toDp() }
                        val itemTopOnScreen = with(density) { (-headerOffset).toDp() }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    top = if (tabView) {
                                        16.dp
                                    } else {
                                        (heroSide - ArtistWordmarkFootInset - ArtistWordmarkHeight - itemTopOnScreen)
                                            .coerceAtLeast(0.dp)
                                    },
                                ),
                        ) {
                            if (!tabView) {
                                Box(
                                    contentAlignment = Alignment.BottomCenter,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(ArtistWordmarkHeight)
                                        .padding(horizontal = 20.dp),
                                ) {
                                    // No name while Apple settles, so a logo never replaces text mid-view.
                                    if (!appleLoading) ArtistWordmark(logo = appleArtist?.logo, name = displayArtistName)
                                }
                                Spacer(modifier = Modifier.height(ArtistWordmarkFootInset + 2.dp))
                            }

                            ArtistActionRow(
                                keyColor = appleArtist?.keyColor,
                                pageBgRgb = pageBgRgb,
                                isPlaying = isPlayingThisArtist,
                                isSubscribed = isSubscribed,
                                onInfo = { showAbout = !showAbout },
                                onPlay = onPlay,
                                onSubscribe = onSubscribe,
                            )

                            val description = artistPage?.description
                            val descriptionRuns = artistPage?.descriptionRuns
                            AnimatedVisibility(
                                visible = showAbout && (!description.isNullOrEmpty() || !descriptionRuns.isNullOrEmpty()),
                            ) {
                                CompositionLocalProvider(
                                    androidx.compose.material3.LocalContentColor provides textTiers[1],
                                ) {
                                    ExpandableText(
                                        text = description.orEmpty(),
                                        runs = descriptionRuns?.map {
                                            LinkSegment(text = it.text, url = it.navigationEndpoint?.urlEndpoint?.url)
                                        },
                                        collapsedMaxLines = 6,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 20.dp, end = 20.dp, top = 20.dp),
                                    )
                                }
                            }

                            if (featured != null) {
                                Spacer(modifier = Modifier.height(32.dp))
                                FeaturedReleaseCard(
                                    album = featured.second,
                                    caption = featured.second.year?.toString() ?: featured.first,
                                    pageBgRgb = pageBgRgb,
                                    onClick = {
                                        navController.navigate(albumRoute(featured.second.id, featured.second.thumbnail))
                                    },
                                    modifier = Modifier.padding(horizontal = 20.dp),
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                        }
                    }
                }


                if (showLocal) {
                    if (librarySongs.isNotEmpty()) {
                        item(key = "local_songs_title") {
                            ArtistSectionHeader(
                                title = stringResource(R.string.songs),
                                modifier = Modifier.animateItem().artistTitleChrome(),
                                onClick = {
                                    navController.navigate("artist/${viewModel.artistId}/songs")
                                }
                            )
                        }

                        val filteredLibrarySongs = if (hideExplicit) {
                            librarySongs.filter { !it.song.explicit }
                        } else {
                            librarySongs
                        }
                        itemsIndexed(
                            items = filteredLibrarySongs,
                            key = { index, item -> "local_song_${item.id}_$index" }
                        ) { index, song ->
                            SongListItem(
                                song = song,
                                showInLibraryIcon = true,
                                isActive = song.id == mediaMetadata?.id,
                                isPlaying = isPlaying,
                                shape = listItemShape(index, filteredLibrarySongs.size),
                                flat = true,
                                secondaryAdditiveChrome = true,
                                // Thumbnails flush on the page gutter, aligned with
                                // the section titles; outer rules full, inter-row
                                // rules shorter.
                                thumbnailStartInset = 0.dp,
                                leadingDivider = index == 0,
                                trailingDividerInset = if (index == filteredLibrarySongs.lastIndex) {
                                    null
                                } else {
                                    ArtistInterRowDividerInset
                                },
                                trailingContent = {
                                    androidx.compose.material3.IconButton(
                                        onClick = {
                                            menuState.show {
                                                SongMenu(
                                                    originalSong = song,
                                                    navController = navController,
                                                    onDismiss = menuState::dismiss,
                                                )
                                            }
                                        },
                                        modifier = Modifier.additiveChrome(),
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.more_horiz),
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.6f),
                                            modifier = Modifier
                                                .size(22.dp),
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedBounceClick(
                                        onClick = {
                                            if (song.id == mediaMetadata?.id) {
                                                playerConnection.togglePlayPause()
                                            } else {
                                                playerConnection.playQueue(
                                                    ListQueue(
                                                        title = libraryArtist?.artist?.name ?: "Unknown Artist",
                                                        items = librarySongs.map { it.toMediaItem() },
                                                        startIndex = index
                                                    )
                                                )
                                            }
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            menuState.show {
                                                SongMenu(
                                                    originalSong = song,
                                                    navController = navController,
                                                    onDismiss = menuState::dismiss,
                                                )
                                            }
                                        },
                                    )
                                    .animateItem(),
                            )
                        }
                    }

                    if (libraryAlbums.isNotEmpty()) {
                        item(key = "local_albums_title") {
                            ArtistSectionHeader(
                                title = stringResource(R.string.albums),
                                modifier = Modifier.animateItem().artistTitleChrome(),
                                onClick = {
                                    navController.navigate("artist/${viewModel.artistId}/albums")
                                }
                            )
                        }

                        item(key = "local_albums_list") {
                            val filteredLibraryAlbums = if (hideExplicit) {
                                libraryAlbums.filter { !it.album.explicit }
                            } else {
                                libraryAlbums
                            }
                            LazyRow(
                                contentPadding = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal).asPaddingValues().plusStart(sideInset),
                                modifier = Modifier.bleedStart(sideInset),
                            ) {
                                itemsIndexed(
                                    // indexOf(it) in the key lambda was a linear scan per
                                    // item; the index is already supplied here.
                                    items = filteredLibraryAlbums,
                                    key = { index, it -> "local_album_${it.id}_$index" }
                                ) { _, album ->
                                    AlbumGridItem(
                                        album = album,
                                        isActive = mediaMetadata?.album?.id == album.id,
                                        isPlaying = isPlaying,
                                        coroutineScope = coroutineScope,
                                        modifier = Modifier
                                            .combinedBounceClick(
                                                onClick = {
                                                    navController.navigate(albumRoute(album.id, album.thumbnailUrl))
                                                },
                                                onLongClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    menuState.show {
                                                        AlbumMenu(
                                                            originalAlbum = album,
                                                            navController = navController,
                                                            onDismiss = menuState::dismiss
                                                        )
                                                    }
                                                }
                                            )
                                            .animateItem()
                                    )
                                }
                            }
                        }
                    }
                } else {
                    artistPage?.sections?.fastForEach { section ->
                        if (section.items.isNotEmpty() && !section.isFeaturedLatest()) {
                            item(key = "section_${section.title}") {
                                // Redesigned header to match mockup "Section >" style
                                ArtistSectionHeader(
                                    title = when (section.title) {
                                        "Songs" -> "Top Songs"
                                        "Popular" -> "Popular"
                                        else -> section.title
                                    },
                                    modifier = Modifier.animateItem().artistTitleChrome(),
                                    onClick = section.moreEndpoint?.let {
                                        {
                                            navController.navigate(
                                                "artist/${viewModel.artistId}/items?browseId=${it.browseId}?params=${it.params}",
                                            )
                                        }
                                    },
                                )
                            }
                        }

                        if (section.isFeaturedLatest()) {
                            // Drawn as the featured card in the header.
                        } else if ((section.items.firstOrNull() as? SongItem)?.album != null) {
                            // Was also recomputed inside every row's listItemShape(...),
                            // i.e. a full distinctBy pass per row.
                            val distinctSongs = section.items.distinctBy { it.id }
                            itemsIndexed(
                                items = distinctSongs,
                                key = { _, it -> "youtube_song_${it.id}" },
                            ) { index, song ->
                                YouTubeListItem(
                                    item = song as SongItem,
                                    subtitle = (song as SongItem).album?.name,
                                    isActive = mediaMetadata?.id == song.id,
                                    isPlaying = isPlaying,
                                    shape = listItemShape(index, distinctSongs.size),
                                    flat = true,
                                    secondaryAdditiveChrome = true,
                                    // Thumbnails flush on the page gutter, aligned with
                                    // the section titles; outer rules full, inter-row
                                    // rules shorter.
                                    thumbnailStartInset = 0.dp,
                                    leadingDivider = index == 0,
                                    trailingDividerInset = if (index == distinctSongs.lastIndex) {
                                        null
                                    } else {
                                        ArtistInterRowDividerInset
                                    },
                                    trailingContent = {
                                        androidx.compose.material3.IconButton(
                                            onClick = {
                                                menuState.show {
                                                    YouTubeSongMenu(
                                                        song = song,
                                                        navController = navController,
                                                        onDismiss = menuState::dismiss,
                                                    )
                                                }
                                            },
                                            modifier = Modifier.additiveChrome(),
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.more_horiz),
                                                contentDescription = null,
                                                tint = Color.White.copy(alpha = 0.6f),
                                                modifier = Modifier
                                                    .size(22.dp),
                                            )
                                        }
                                    },
                                    modifier = Modifier
                                        .combinedBounceClick(
                                            onClick = {
                                                if (song.id == mediaMetadata?.id) {
                                                    playerConnection.togglePlayPause()
                                                } else {
                                                    playerConnection.playQueue(
                                                        YouTubeQueue(
                                                            WatchEndpoint(videoId = song.id),
                                                            song.toMediaMetadata()
                                                        ),
                                                    )
                                                }
                                            },
                                            onLongClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                menuState.show {
                                                    YouTubeSongMenu(
                                                        song = song,
                                                        navController = navController,
                                                        onDismiss = menuState::dismiss,
                                                    )
                                                }
                                            },
                                        )
                                        .animateItem(),
                                )
                            }
                        } else {
                            item(key = "section_list_${section.title}") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 20.dp).plusStart(sideInset),
                                    modifier = Modifier.bleedStart(sideInset),
                                ) {
                                    items(
                                        items = section.items.distinctBy { it.id },
                                        key = { "youtube_album_${it.id}" },
                                    ) { item ->
                                        YouTubeGridItem(
                                            item = item,
                                            isActive = when (item) {
                                                is SongItem -> mediaMetadata?.id == item.id
                                                is AlbumItem -> mediaMetadata?.album?.id == item.id
                                                else -> false
                                            },
                                            isPlaying = isPlaying,
                                            coroutineScope = coroutineScope,
                                            thumbnailRatio = 1f, // Use square thumbnails for all items in horizontal scroll
                                            modifier = Modifier
                                                .combinedBounceClick(
                                                    onClick = {
                                                        when (item) {
                                                            is SongItem ->
                                                                playerConnection.playQueue(
                                                                    YouTubeQueue(
                                                                        WatchEndpoint(videoId = item.id),
                                                                        item.toMediaMetadata()
                                                                    ),
                                                                )

                                                            is AlbumItem -> navController.navigate(albumRoute(item.id, item.thumbnail))
                                                            is ArtistItem -> navController.navigate(artistRoute(item.id, item.thumbnail))
                                                            is PlaylistItem -> navController.navigate(onlinePlaylistRoute(item.id, item.title, item.thumbnail))
                                                        }
                                                    },
                                                    onLongClick = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        menuState.show {
                                                            when (item) {
                                                                is SongItem ->
                                                                    YouTubeSongMenu(
                                                                        song = item,
                                                                        navController = navController,
                                                                        onDismiss = menuState::dismiss,
                                                                    )

                                                                is AlbumItem ->
                                                                    YouTubeAlbumMenu(
                                                                        albumItem = item,
                                                                        navController = navController,
                                                                        onDismiss = menuState::dismiss,
                                                                    )

                                                                is ArtistItem ->
                                                                    YouTubeArtistMenu(
                                                                        artist = item,
                                                                        onDismiss = menuState::dismiss,
                                                                    )

                                                                is PlaylistItem ->
                                                                    YouTubePlaylistMenu(
                                                                        playlist = item,
                                                                        coroutineScope = coroutineScope,
                                                                        onDismiss = menuState::dismiss,
                                                                    )
                                                            }
                                                        }
                                                    },
                                                )
                                                .animateItem(),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        }

        val showLocalFab = librarySongs.isNotEmpty() && libraryArtist?.artist?.isLocal != true
        
        // Library/Local Toggle FAB
        HideOnScrollFAB(
            visible = showLocalFab,
            lazyListState = lazyListState,
            icon = if (showLocal) R.drawable.language else R.drawable.library_music,
            onClick = {
                showLocal = showLocal.not()
                if (!showLocal && artistPage == null) viewModel.fetchArtistsFromYTM()
            },
            hazeState = hazeState,
        )
        
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
                .align(Alignment.BottomCenter)
        )

        // Floating glass back/share buttons over the hero art, replacing the
        // Material TopAppBar — always visible, no title-bar-on-scroll behavior.
        // Backed by a scrim that ramps from transparent to a dark shade as the
        // list scrolls past the hero art, so the buttons stay legible over
        // regular content too.
        // GlassCircleButton below reads LocalAppBackdrop at its own composition
        // point, so wrapping it here (not redeclaring a val above) is enough.
        CompositionLocalProvider(LocalAppBackdrop provides listBackdrop) {
        // Settings-style top glass, post-hero only: zero over the photo (the
        // hero's own top shade keeps the buttons legible there), fading in
        // once the list scrolls past the hero foot. Same TopFadeBlur ramp +
        // scrim as every pushed screen, keyed to this screen's mesh tint.
        val displayMetrics = LocalResources.current.displayMetrics
        val heroThresholdPx = remember(density, displayMetrics) {
            displayMetrics.widthPixels.toFloat() / 0.95f
        }
        val topBarInAlpha by remember {
            derivedStateOf {
                val scrolledPx = if (lazyListState.firstVisibleItemIndex == 0) {
                    lazyListState.firstVisibleItemScrollOffset.toFloat()
                } else {
                    Float.MAX_VALUE
                }
                ((scrolledPx - heroThresholdPx) / 200f).coerceIn(0f, 1f)
            }
        }
        if (topBarInAlpha > 0.001f) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .graphicsLayer { alpha = topBarInAlpha },
            ) {
                TopFadeBlur(
                    hazeState = hazeState,
                    pageColor = tint,
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AppBarHeight * 2.5f)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.55f * chromeScrimAlpha),
                            0.35f to Color.Black.copy(alpha = 0.4f * chromeScrimAlpha),
                            0.7f to Color.Black.copy(alpha = 0.12f * chromeScrimAlpha),
                            1f to Color.Transparent,
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(appTopBarWindowInsets())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
            GlassCircleButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
                modifier = with(artistChrome) {
                    Modifier.backEnter().then(com.convx.music.ui.component.backExitModifier())
                },
            ) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back_ios),
                    contentDescription = null,
                )
            }

            // Share + more in one glass capsule, 44 tall, as on iOS.
            GlassPillRow(
                modifier = with(artistChrome) { Modifier.containerEnter() }
                    .height(44.dp)
                    .then(pillGlassBackground(ArtistCapsuleShape))
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CompositionLocalProvider(
                    LocalContentColor provides LocalGlassEffectConfig.current.resolvedTextColor(),
                ) {
                    StretchPillIcon(
                        entrance = artistChrome,
                        index = 0,
                        modifier = Modifier.clickable {
                            viewModel.artistPage?.artist?.shareLink?.let { link ->
                                val send = android.content.Intent(android.content.Intent.ACTION_SEND)
                                    .setType("text/plain")
                                    .putExtra(android.content.Intent.EXTRA_TEXT, link)
                                context.startActivity(android.content.Intent.createChooser(send, null))
                            }
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ios_share),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    StretchPillIcon(
                        entrance = artistChrome,
                        index = 1,
                        modifier = Modifier.clickable {
                            viewModel.artistPage?.artist?.let { artist ->
                                menuState.show {
                                    YouTubeArtistMenu(artist = artist, onDismiss = menuState::dismiss)
                                }
                            }
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.more_horiz),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
            }
        }
        }
      }
      }
    }
}

/** Wordmark box on the iOS 27 artist page: logos fit inside 288×96, bottom-aligned. */
private val ArtistWordmarkHeight = 96.dp
private val ArtistWordmarkMaxWidth = 288.dp

/** Gap between the wordmark box and the hero foot. */
private val ArtistWordmarkFootInset = 10.dp

private val ArtistCapsuleShape = com.convx.music.ui.component.shapes.ContinuousRoundedRectangle(percent = 50)

private fun com.music.innertube.pages.ArtistSection.isFeaturedLatest(): Boolean =
    title.contains("Latest", ignoreCase = true) && items.firstOrNull() is AlbumItem

/**
 * (section title, album) of the release the header card features: YouTube's "Latest release"
 * when the page has one, otherwise the first album/single shelf's lead item — iOS always shows
 * the card, and plenty of YouTube artist pages carry no "Latest" shelf.
 */
private fun List<com.music.innertube.pages.ArtistSection>.featuredRelease(): Pair<String, AlbumItem>? =
    (firstOrNull { it.isFeaturedLatest() } ?: firstOrNull { it.items.firstOrNull() is AlbumItem })
        ?.let { it.title to it.items.first() as AlbumItem }

/** The artist's Apple Music logo when they have one, the name set large otherwise. */
@Composable
private fun ArtistWordmark(logo: com.convx.music.canvas.AppleArtwork?, name: String) {
    var logoFailed by remember(logo) { mutableStateOf(false) }
    if (logo != null && !logoFailed) {
        AsyncImage(
            model = logo.sized(1000, png = true),
            contentDescription = name,
            contentScale = ContentScale.Fit,
            alignment = Alignment.BottomCenter,
            onError = { logoFailed = true },
            modifier = Modifier
                .widthIn(max = ArtistWordmarkMaxWidth)
                .fillMaxSize(),
        )
    } else {
        Text(
            text = name,
            color = Color.White,
            fontSize = 34.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = rememberCustomArtistFontFamily(),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Info / play / favourite: 48, 72, 48 with 24 between. Play is filled with the artist's key
 * colour (white without one) and its glyph is the page colour pulled 30% to black; the side
 * buttons are an 8% white wash with the key colour as their glyph.
 */
@Composable
private fun ArtistActionRow(
    keyColor: Int?,
    pageBgRgb: Int,
    isPlaying: Boolean,
    isSubscribed: Boolean,
    onInfo: () -> Unit,
    onPlay: () -> Unit,
    onSubscribe: () -> Unit,
) {
    val accent = AppleColors.color(AppleColors.playFill(keyColor))
    val controlFill = Color.White.copy(alpha = AppleColors.CONTROL_FILL_ALPHA)
    Row(
        horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        ArtistControlCircle(size = 48.dp, fill = controlFill, onClick = onInfo) {
            Text(
                text = "i",
                color = accent,
                fontSize = 25.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
            )
        }
        ArtistControlCircle(size = 72.dp, fill = accent, onClick = onPlay) {
            AnimatedPlayPauseIcon(
                isPlaying = isPlaying,
                tint = AppleColors.color(AppleColors.playGlyph(pageBgRgb)),
                size = 30.dp,
            )
        }
        ArtistControlCircle(size = 48.dp, fill = controlFill, onClick = onSubscribe) {
            Icon(
                imageVector = if (isSubscribed) Icons.Filled.Star else SfSymbols.Monochrome.SFStar,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun ArtistControlCircle(
    size: Dp,
    fill: Color,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(fill)
            .clickable(onClick = onClick),
    ) {
        content()
    }
}

/**
 * Featured release card: 120 tall, 24 corners, 12 padding, 96 artwork with 10 corners, caption
 * and subtitle 15pt at 60% white around a 17pt semibold title, and a 32pt add button. The fill
 * is the measured white wash for this page colour ([AppleColors.cardFillAlpha]).
 */
@Composable
private fun FeaturedReleaseCard(
    album: AlbumItem,
    caption: String,
    pageBgRgb: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = AppleColors.cardFillAlpha(pageBgRgb)))
            .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        AsyncImage(
            model = album.thumbnail?.resize(300, 300),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(10.dp)),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = caption,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 15.sp,
                lineHeight = 20.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = album.title,
                color = Color.White,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            album.description?.takeIf { it.isNotBlank() }?.let { subtitle ->
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.14f)),
        ) {
            Icon(
                imageVector = SfSymbols.Monochrome.SFPlus,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
    }
}

/** "Top Songs ›": 22pt bold on the 20pt gutter, chevron only when the section opens. */
@Composable
private fun ArtistSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 10.dp),
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (onClick != null) {
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = SfSymbols.Monochrome.SFChevronRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * Uppercases the first letter of each word, leaving the rest of the casing alone.
 *
 * The artist header used to `.lowercase()` the whole name and rely on a drop-cap span
 * to restore the first letter, so "Metro Boomin" rendered as "Metro boomin". Only
 * touching each word's leading character fixes that without flattening names whose
 * casing is deliberate — SZA, A$AP Rocky and MF DOOM all survive unchanged, which a
 * lowercase-then-capitalize pass would have mangled.
 */
private fun String.titlecaseWords(): String =
    split(' ').joinToString(" ") { word ->
        word.replaceFirstChar { it.uppercaseChar() }
    }

/** Secondary text on this page: white added onto the mesh, player-style. */
private const val ArtistChromeAlpha = 0.45f

/** Titles only get a hint: brightest line, backdrop glow. */
private const val ArtistTitleChromeAlpha = 0.85f

/**
 * Inter-row rules stop just inside the thumbnails; the section's outer rules
 * (first row's leading, last row's trailing) stay full width with padding.
 */
private val ArtistInterRowDividerInset = 28.dp

// Draw modifiers only, no layout nodes. Alpha after additiveChrome, never before.
private fun Modifier.artistChrome() =
    additiveChrome().graphicsLayer { alpha = ArtistChromeAlpha }

private fun Modifier.artistTitleChrome() =
    additiveChrome().graphicsLayer { alpha = ArtistTitleChromeAlpha }
