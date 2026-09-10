/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.screens.artist

import com.convx.music.ui.utils.appTopBarWindowInsets
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import com.convx.music.ui.utils.bounceClick
import com.convx.music.ui.utils.combinedBounceClick
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
import com.convx.music.LocalTabView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import com.convx.music.ui.component.ArtistBlendStart
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
import com.convx.music.constants.ShowArtistDescriptionKey
import com.convx.music.constants.ShowArtistSubscriberCountKey
import com.convx.music.constants.ShowMonthlyListenersKey
import com.convx.music.db.entities.ArtistEntity
import com.convx.music.extensions.toMediaItem
import com.convx.music.models.toMediaMetadata
import com.convx.music.playback.queues.ListQueue
import com.convx.music.playback.queues.YouTubeQueue
import com.convx.music.ui.component.AnimatedPlayPauseIcon
import com.convx.music.ui.component.AlbumGridItem
import com.convx.music.ui.component.ExpandableText
import com.convx.music.ui.component.ContrastCircleButton
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
import com.convx.music.constants.ShowArtistVideoKey
import com.convx.music.constants.ShowArtistBackgroundVideoKey
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.convx.music.canvas.AppleMusicArtistBackgroundProvider
import com.convx.music.ui.component.floatingtabbar.gooey
import com.convx.music.ui.player.MeshGradientBackground
import com.convx.music.ui.player.dimmed
import com.convx.music.ui.player.rememberArtworkMeshPalette

/**
 * Flat white type tiers over the mesh, exactly like the player: BITCHORD_MESH/LIVE_MESH
 * force plain white chrome rather than a per-artwork computed contrast colour, because
 * the mesh itself is always dark enough for white to read. A WCAG-searched hue ladder
 * used to sit here instead — it guaranteed contrast, but the darkening scrim it required
 * to make that guarantee (bounding the mesh's brightest point) crushed the same
 * saturation boost it was applied to, so the page read flat next to the player's raw
 * mesh off the same artwork. Matching the player wins.
 */
private val MeshWhiteTiers = listOf(
    Color.White,
    Color.White.copy(alpha = 0.35f),
    Color.White.copy(alpha = 0.30f),
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
    val artistVideoUrl by viewModel.artistVideoUrl.collectAsState()
    val artistVideoSong by viewModel.artistVideoSong.collectAsState()
    val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)
    val showArtistDescription by rememberPreference(key = ShowArtistDescriptionKey, defaultValue = true)
    val showArtistSubscriberCount by rememberPreference(key = ShowArtistSubscriberCountKey, defaultValue = true)
    val showMonthlyListeners by rememberPreference(key = ShowMonthlyListenersKey, defaultValue = true)
    val dataSaverEnabled by rememberPreference(key = DataSaverEnabledKey, defaultValue = false)
    val showArtistVideoPref by rememberPreference(key = ShowArtistVideoKey, defaultValue = true)
    val showArtistVideo = if (dataSaverEnabled) false else showArtistVideoPref
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

    // Apple Music style: a single dominant color pulled from the artist's own
    // artwork washes the screen instead of a flat Material surface. The
    // floating back/share chrome stays real liquid glass sampling this screen's
    // own content behind it; the play-row buttons (info/play/subscribe) and the
    // subscriber/listener pills are a flat fill in the tint's own contrast
    // color instead — see ContrastCircleButton below.
    val artistThumbnail = artistPage?.artist?.thumbnail ?: libraryArtist?.artist?.thumbnailUrl

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

    // The BitChord player's own mesh extraction (Palette-based, four blobs) — same call
    // as Player.kt's BITCHORD_MESH style, raw, so this screen's mesh matches the
    // player's for the same artwork instead of running its own extra saturation pass.
    val meshPalette = rememberArtworkMeshPalette(artistThumbnail)
    val tint = remember(meshPalette) { meshPalette.colors.first().dimmed() }
    com.convx.music.PublishAmbientBackgroundColor(tint)
    // Same flat white every icon/text reads in the player on an artwork
    // background, rather than a per-artwork computed contrast colour.
    val onTint = Color.White

    // Flat white tiers, like the player — see [MeshWhiteTiers].
    val textTiers = MeshWhiteTiers
    val chromeIconColor = textTiers[0]
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
    MeshGradientBackground(
        palette = meshPalette,
        trackKey = artistThumbnail,
        modifier = Modifier.matchParentSize(),
    )
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
                    ShimmerHost (
                        modifier = Modifier
                            .offset {
                                IntOffset(x = 0, y = headerOffset)
                            }
                    ) {
                        // Artist Image Placeholder
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1.1f),
                        ) {
                            Spacer(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .shimmer()
                                    .background(AppTheme.colorScheme.onSurface)
                                    .fadingEdge(
                                        top = systemBarsTopPadding + AppBarHeight,
                                        bottom = 200.dp,
                                    ),
                            )
                        }
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
            } else {
                item(key = "header") {
                    val thumbnail = artistPage?.artist?.thumbnail ?: libraryArtist?.artist?.thumbnailUrl
                    val artistName = artistPage?.artist?.title ?: libraryArtist?.artist?.name

                    var backgroundVideoUrl by remember { mutableStateOf<String?>(null) }
                    LaunchedEffect(artistName, showArtistBackgroundVideo) {
                        if (artistName != null && showArtistBackgroundVideo) {
                            withContext(Dispatchers.IO) {
                                backgroundVideoUrl = AppleMusicArtistBackgroundProvider.getByArtistName(artistName)
                            }
                        }
                    }

                    Box {
                        // Wide layout: the full-bleed square would eat the whole
                        // fold, so the portrait becomes a bounded card with the
                        // artist name set beside it (see HeroCardHeader).
                        val tabView = LocalTabView.current

                        // BitChord-style hero: full-bleed photo (height = width /
                        // 0.95), progressive blur ramping from the blend start so
                        // the foot smears into the page colour instead of meeting
                        // it at an edge. The name block below sits on that blend
                        // start and the rest follows it up.
                        if (!tabView && (thumbnail != null || backgroundVideoUrl != null)) {
                            ArtistHeroImage(
                                artworkUrl = thumbnail,
                                pageColor = tint,
                                // Baked join: static blur plates + eased foot wash
                                // dissolve the edge. No Haze here — the live band
                                // sampled the list and ghosted over it. No scroll
                                // blur either — the photo stays crisp on scroll.
                                hazeState = null,
                                heroScale = heroZoom.scale,
                                modifier = Modifier.offset {
                                    IntOffset(x = 0, y = headerOffset)
                                },
                                overlay = {
                                    if (backgroundVideoUrl != null && showArtistBackgroundVideo) {
                                        ArtistVideo(
                                            videoUrl = backgroundVideoUrl!!,
                                            modifier = Modifier.fillMaxSize(),
                                            onClick = { }
                                        )
                                    }
                                },
                            )
                        }

                        // Artist Name and Controls Section - positioned at bottom of image

                        if (tabView) {
                            HeroCardHeader(
                                artworkUrl = thumbnail,
                                circular = true,
                                title = {
                                    Text(
                                        text = artistName?.titlecaseWords() ?: "Unknown",
                                        style = AppTheme.typography.headlineLarge,
                                        fontFamily = rememberCustomArtistFontFamily() ?: rememberBrandFontFamily(),
                                        fontWeight = FontWeight.SemiBold,
                                        color = LocalAccentTextColor.current,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        fontSize = 42.sp,
                                    )
                                },
                                subtitle = artistPage?.subscriberCountText?.takeIf {
                                    showArtistSubscriberCount
                                }?.let { subscribers ->
                                    {
                                        Text(
                                            text = subscribers,
                                            style = AppTheme.typography.titleMedium,
                                            color = AppTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                        )
                                    }
                                },
                            )
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    top = if (thumbnail != null && !tabView) {
                                        // The name sits on the blend start — the same
                                        // fraction the hero's blur ramps from — and the
                                        // buttons/stats/about follow it up in the same
                                        // column, so the whole block reads as a caption
                                        // dissolving out of the picture, not part of it.
                                        LocalResources.current.displayMetrics.widthPixels.let { screenWidth ->
                                            with(density) {
                                                (screenWidth / 0.95f).toDp() * ArtistBlendStart
                                            }
                                        }
                                    } else {
                                        16.dp
                                    }
                                )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    // 20.dp: rows below sit on the same gutter, so
                                    // the name/buttons line up with the song list.
                                    .padding(horizontal = 20.dp)
                            ) {
                                // The name moves into HeroCardHeader in tab view.
                                if (!tabView) Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp)
                                ) {

                                    //artist video
                                    if (showArtistVideo && !(showArtistBackgroundVideo && backgroundVideoUrl != null)) {
                                        artistVideoUrl?.let { videoUrl ->
                                            artistPage?.artist?.radioEndpoint?.let { radioEndpoint ->
                                                Spacer(modifier = Modifier.width(5.dp))
                                                ArtistVideo(
                                                    videoUrl = videoUrl,
                                                    modifier = Modifier
                                                        .height(45.dp)
                                                        .aspectRatio(16f / 9f),
                                                    onClick = {
                                                        val watchEndpoint = artistVideoSong?.endpoint
                                                            ?: artistPage?.artist?.radioEndpoint
                                                        watchEndpoint?.let {
                                                            playerConnection.playQueue(YouTubeQueue(it))
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(5.dp))

                                    // Artist Name — drop-cap style, first letter bigger than the rest.
                                    val displayArtistName = artistName?.titlecaseWords() ?: "Unknown"
                                    Text(
                                        text = buildAnnotatedString {
                                            if (displayArtistName.isNotEmpty()) {
                                                withStyle(SpanStyle(fontSize = 60.sp)) {
                                                    append(displayArtistName.first().uppercase())
                                                }
                                                append(displayArtistName.drop(1))
                                            } else {
                                                append(displayArtistName)
                                            }
                                        },
                                        style = AppTheme.typography.headlineLarge,
                                        fontFamily = rememberCustomArtistFontFamily() ?: rememberBrandFontFamily(),
                                        fontWeight = FontWeight.SemiBold,
                                        // The page's biggest heading: carries the artwork
                                        // tint plainly rather than flat content colour.
                                        color = LocalAccentTextColor.current,
                                        // Two lines: a long name was ellipsed away on one
                                        // ("Metro Boomin" clipped mid-word behind the share
                                        // button) rather than wrapping into the space the
                                        // header already has.
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                        fontSize = 44.sp,
                                        modifier = Modifier.weight(1f, fill = false).artistTitleChrome()
                                    )
                                }


                                // Buttons Row — starts on the fade: tight under the
                                // name so it sits on the hero's blurred foot,
                                // not below it on flat tint.
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    // Info Button (Left) — sits a touch lower than the
                                    // bigger center Play button, not perfectly centered
                                    // with it, so it reads as "grounded" beside it.
                                    ContrastCircleButton(
                                        onTint = onTint,
                                        onClick = { /* Could show description or bio */ },
                                        size = 48.dp,
                                        modifier = Modifier.offset(y = 8.dp),
                                    ) {
                                        Icon(
                                            imageVector = SfSymbols.Monochrome.SFInfoCircle,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    // Primary Play Button (Center - Large)
                                    val artistId = artistPage?.artist?.id
                                    val isPlayingThisArtist = isPlaying && artistId != null &&
                                        mediaMetadata?.artists?.any { it.id == artistId } == true
                                    ContrastCircleButton(
                                        onTint = onTint,
                                        filled = true,
                                        onClick = {
                                             if (isPlayingThisArtist) {
                                                 playerConnection.player.pause()
                                             } else if (artistId != null &&
                                                 mediaMetadata?.artists?.any { it.id == artistId } == true
                                             ) {
                                                 playerConnection.player.play()
                                             } else {
                                                 val songSection = artistPage?.sections?.find { section ->
                                                     section.items.firstOrNull() is SongItem
                                                 }
                                                 val items = songSection?.items?.filterIsInstance<SongItem>()
                                                 if (!items.isNullOrEmpty()) {
                                                     playerConnection.playQueue(
                                                         ListQueue(
                                                             title = artistName ?: "Artist",
                                                             items = items.map { it.toMediaItem() }
                                                         )
                                                     )
                                                 } else {
                                                     artistPage?.artist?.radioEndpoint?.let {
                                                         playerConnection.playQueue(YouTubeQueue(it))
                                                     }
                                                 }
                                             }
                                         },
                                         size = 72.dp,
                                     ) {
                                         AnimatedPlayPauseIcon(
                                             isPlaying = isPlayingThisArtist,
                                             tint = if (onTint == Color.White) Color.Black else Color.White,
                                             size = 32.dp,
                                         )
                                    }

                                    // Favorite/Subscribe Button (Right)
                                    val isSubscribed = libraryArtist?.artist?.bookmarkedAt != null
                                    ContrastCircleButton(
                                        onTint = onTint,
                                        onClick = {
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
                                                            ).toggleLike()
                                                        )
                                                    }
                                                }
                                            }
                                        },
                                        size = 48.dp,
                                        modifier = Modifier.offset(y = 8.dp),
                                    ) {
                                        Icon(
                                            imageVector = if (isSubscribed) SfSymbols.Monochrome.SFHeartFill else SfSymbols.Monochrome.SFHeart,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = if (isSubscribed) AppTheme.colorScheme.error else LocalContentColor.current
                                        )
                                    }
                                }

                                // Subscriber counts and the artist blurb sit BELOW the play
                                // controls: the buttons are what the page is for, and a long
                                // description pushed them off the fold.
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp, bottom = 16.dp)
                                ) {
                                    if (showArtistSubscriberCount) {
                                        artistPage?.subscriberCountText?.let { subscribers ->
                                            // Flat faded pill + plain text, like the heart
                                            // button beside the play trio — no glass, no icon.
                                            Text(
                                                text = "${subscribers.split(' ').firstOrNull() ?: ""} ${stringResource(R.string.subscribers)}",
                                                style = AppTheme.typography.labelLarge,
                                                color = chromeTextColor,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier
                                                    .clip(CircleShape)
                                                    .background(onTint.copy(alpha = 0.16f))
                                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                                    .artistChrome(),
                                            )
                                        }
                                    }

                                    if (showMonthlyListeners) {
                                        artistPage?.monthlyListenerCount?.let { monthlyListeners ->
                                            Text(
                                                text = "${monthlyListeners.split(' ').firstOrNull() ?: ""} ${stringResource(R.string.monthly_listeners)}",
                                                style = AppTheme.typography.labelLarge,
                                                color = chromeTextColor,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier
                                                    .clip(CircleShape)
                                                    .background(onTint.copy(alpha = 0.16f))
                                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                                    .artistChrome(),
                                            )
                                        }
                                    }
                                }

                                if (!showLocal && showArtistDescription && artistPage != null) {
                                    val description = artistPage?.description
                                    val descriptionRuns = artistPage?.descriptionRuns
                                    
                                    if (!description.isNullOrEmpty() || !descriptionRuns.isNullOrEmpty()) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 16.dp)
                                        ) {
                                            Text(
                                                text = stringResource(R.string.about_artist),
                                                style = AppTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = chromeTextColor,
                                                modifier = Modifier.padding(bottom = 8.dp).artistChrome()
                                            )
                                            
                                            androidx.compose.runtime.CompositionLocalProvider(
                                                // Engine-derived secondary tier, not the
                                                // heading colour: contrast-checked against
                                                // the mesh this screen actually paints.
                                                androidx.compose.material3.LocalContentColor provides textTiers[1],
                                            ) {
                                                ExpandableText(
                                                    text = description.orEmpty(),
                                                    runs = descriptionRuns?.map {
                                                        LinkSegment(
                                                            text = it.text,
                                                            url = it.navigationEndpoint?.urlEndpoint?.url
                                                        )
                                                    },
                                                    collapsedMaxLines = 3,
                                                    modifier = Modifier.artistChrome(),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            // Breathing room between the hero tail and the first
                            // section, so Top Songs starts clear of the photo.
                            Spacer(modifier = Modifier.height(36.dp))
                        }
                    }
                }


                if (showLocal) {
                    if (librarySongs.isNotEmpty()) {
                        item(key = "local_songs_title") {
                            NavigationTitle(
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
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.MoreVert,
                                            contentDescription = null,
                                            tint = LocalContentColor.current,
                                            modifier = Modifier
                                                .size(22.dp)
                                                .additiveChrome()
                                                .graphicsLayer { alpha = 0.45f },
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
                            NavigationTitle(
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
                                                    navController.navigate("album/${album.id}")
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
                        if (section.items.isNotEmpty()) {
                            item(key = "section_${section.title}") {
                                // Redesigned header to match mockup "Section >" style
                                NavigationTitle(
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

                        // Check if this is a "Latest Release" style section to render as a card
                        val firstItem = section.items.firstOrNull()
                        if (section.title.contains("Latest", ignoreCase = true) && firstItem is AlbumItem) {
                            item(key = "featured_release") {
                                FeaturedReleaseCard(
                                    album = firstItem,
                                    onTint = onTint,
                                    onClick = { navController.navigate("album/${firstItem.id}") },
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).animateItem()
                                )
                            }
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
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.MoreVert,
                                                contentDescription = null,
                                                tint = LocalContentColor.current,
                                                modifier = Modifier
                                                    .size(22.dp)
                                                    .additiveChrome()
                                                    .graphicsLayer { alpha = 0.45f },
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

                                                            is AlbumItem -> navController.navigate("album/${item.id}")
                                                            is ArtistItem -> navController.navigate("artist/${item.id}")
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
            }
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
            ) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back_ios),
                    contentDescription = null,
                )
            }

            GlassCircleButton(
                onClick = {
                    viewModel.artistPage?.artist?.shareLink?.let { link ->
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Artist Link", link)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, R.string.link_copied, Toast.LENGTH_SHORT).show()
                    }
                },
            ) {
                Icon(
                    painter = painterResource(R.drawable.link),
                    contentDescription = null,
                )
            }
            }
        }
        }
      }
      }
    }
}

@Composable
fun FeaturedReleaseCard(
    album: AlbumItem,
    onTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.Black.copy(alpha = 0.2f)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = album.thumbnail.resize(400, 400),
                contentDescription = null,
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = album.year?.toString() ?: "Latest Release",
                    style = AppTheme.typography.labelMedium,
                    color = onTint.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.artistChrome(),
                )
                Text(
                    text = album.title,
                    style = AppTheme.typography.titleMedium,
                    color = onTint,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.artistTitleChrome(),
                )
                Text(
                    text = "Album",
                    style = AppTheme.typography.bodySmall,
                    color = onTint.copy(alpha = 0.5f),
                    maxLines = 1,
                    modifier = Modifier.artistChrome(),
                )
            }

            IconButton(onClick = onClick) {
                Icon(
                    imageVector = SfSymbols.Monochrome.SFPlus,
                    contentDescription = null,
                    tint = onTint,
                    modifier = Modifier.size(20.dp).artistChrome()
                )
            }
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
