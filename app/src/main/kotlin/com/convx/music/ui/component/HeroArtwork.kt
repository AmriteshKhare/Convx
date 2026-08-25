package com.convx.music.ui.component

import android.content.Context
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.convx.music.R
import com.convx.music.constants.DynamicThemeKey
import com.convx.music.constants.PureBlackHeroBackgroundKey
import com.convx.music.constants.SelectedThemeColorKey
import com.convx.music.ui.component.shapes.ContinuousRoundedRectangle
import com.convx.music.ui.theme.AppleTokens
import com.convx.music.ui.theme.DefaultThemeColor
import com.convx.music.ui.theme.LocalAccentColor
import com.convx.music.ui.theme.HeroPalette
import com.convx.music.ui.theme.extractHeroPalette
import com.convx.music.utils.rememberPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.convx.music.ui.theme.colorengine.textHierarchyOn

/**
 * Resolved hero image source with priority chain:
 * existing static artwork → animated/canvas artwork → first song artwork →
 * next song artwork → default music image as last resort.
 */
sealed class HeroSource {
    data class Artwork(val url: String, val isAnimated: Boolean = false) : HeroSource()
    data object Default : HeroSource()
}

/** Blur radius for [HeroBackground]'s Apple-Music-style blurred artwork. */
private val HeroBlurRadius = 48.dp

/**
 * The glass band laid across the hero artwork's foot (BitChord/Apple-Music
 * detail-page treatment): a heavily-blurred copy of the same artwork, centred
 * on the join between picture and page, masked to arrive from nothing and
 * leave to nothing.
 *
 * A blur samples across its own footprint, so colour from the artwork is
 * carried down past where the artwork ends and the page colour shows through
 * the upper half — the line that used to separate the two surfaces has nothing
 * left to be a line between. Place it behind the header text: the title then
 * sits on blurred colour rather than on whatever the picture happens to be
 * doing at its foot.
 *
 * Implemented without a progress-glass library because both layers are ours:
 * the band re-draws the artwork (decoded small — a 100dp blur erases all
 * detail) shifted so its bottom edge lands exactly on the band's centre, i.e.
 * on the join. [Modifier.blur] is a RenderEffect no-op below Android S, where
 * a *sharp* duplicate would look worse than no band at all — so below S this
 * falls back to a plain [pageColor] gradient instead: no smeared detail, but
 * still a fade rather than the hard edge doing nothing here would leave.
 */
@Composable
fun HeroMergeBand(
    artworkUrl: String?,
    modifier: Modifier = Modifier,
    /** Band height; generous, because half its run is spent arriving and half leaving. */
    bandHeight: Dp = 320.dp,
    /** Wide enough that nothing of the picture survives at full strength. */
    blurRadius: Dp = 100.dp,
    /** Aspect ratio of the hero artwork this band must line up with. */
    heroRatio: Float = 1f,
    /** The page colour below the join, for the pre-S plain-gradient fallback. */
    pageColor: Color = Color.Black,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        // Same footprint as the S+ Box below (fillMaxWidth/bandHeight), so callers'
        // own .align/.offset on `modifier` line the fallback up on the same join.
        Box(
            modifier
                .fillMaxWidth()
                .height(bandHeight)
                .background(
                    Brush.verticalGradient(
                        0.00f to Color.Transparent,
                        0.50f to pageColor,
                        1.00f to Color.Transparent,
                    ),
                ),
        )
        return
    }

    val bandHeightPx = LocalDensity.current.run { bandHeight.roundToPx() }

    Box(
        modifier
            .fillMaxWidth()
            .height(bandHeight)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(
                        0.00f to Color.Transparent,
                        0.50f to Color.Black,
                        1.00f to Color.Transparent,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            },
    ) {
        if (!artworkUrl.isNullOrBlank()) {
            // Baked once per track rather than `Modifier.blur(100.dp)`, which is a
            // RenderEffect and therefore re-runs on EVERY frame the band is on screen --
            // for a picture that never changes while the screen is up. This band is on
            // every hero screen, so that was a static blur being recomputed at scroll
            // cadence. BlurredBackdropImage decodes small and blurs on the CPU once; at
            // this magnification the result is indistinguishable.
            BlurredBackdropImage(
                model = artworkUrl,
                screenBlurRadiusDp = blurRadius.value,
                decodeSize = 160,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(heroRatio)
                    // Bottom of the copy sits at the band's centre — the join.
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, placeable.height) {
                            placeable.placeRelative(0, bandHeightPx / 2 - placeable.height)
                        }
                    },
            )
        }
    }
}

/** Where [HeroMergeBand] is centred: half over the artwork, half over the page. */
val HeroMergeBandHeight: Dp = 320.dp

/**
 * Hero palettes keyed by artwork URL (the image's stable id): the Coil decode +
 * palette extraction is expensive, so the same artwork never recomputes on
 * revisit or recomposition. Bounded for a long session.
 */
private val heroPaletteCache = android.util.LruCache<String, HeroPalette>(128)

/**
 * 0..1 scroll progress for [HeroBackground]'s `topBlurProgress`: ramps as the first
 * screenful scrolls up, then pins at 1. Pass a hero screen's list state.
 */
@Composable
fun rememberHeroTopBlur(
    state: androidx.compose.foundation.lazy.LazyListState,
    // Blur reaches full strength once the hero header has scrolled this fraction
    // of its OWN height. Deriving the span from the real header size (instead of a
    // fixed px threshold) keeps the ramp continuous through the moment the header
    // scrolls off — a fixed 700px threshold snapped when it didn't match the
    // header's actual height.
    fraction: Float = 0.6f,
): Float {
    val progress by remember(state) {
        androidx.compose.runtime.derivedStateOf {
            if (state.firstVisibleItemIndex > 0) return@derivedStateOf 1f
            val header = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }
            val span = (header?.size ?: 0) * fraction
            if (span <= 0f) 0f
            else (state.firstVisibleItemScrollOffset / span).coerceIn(0f, 1f)
        }
    }
    return progress
}

/**
 * Resolves the hero image per the priority chain.
 *
 * @param staticArt Primary artwork URL (e.g. album/playlist/artist art)
 * @param animatedArt Optional animated/canvas artwork URL
 * @param songs List of songs to fall back to for artwork
 */
@Composable
fun rememberHeroSource(
    staticArt: String?,
    animatedArt: String? = null,
    songs: List<Pair<String?, Boolean>> = emptyList(), // (thumbnailUrl, isAnimated)
): HeroSource {
    return remember(staticArt, animatedArt, songs) {
        when {
            !staticArt.isNullOrBlank() -> HeroSource.Artwork(staticArt, isAnimated = false)
            !animatedArt.isNullOrBlank() -> HeroSource.Artwork(animatedArt, isAnimated = true)
            else -> {
                val firstSongArt = songs.firstOrNull { !it.first.isNullOrBlank() }
                if (firstSongArt != null) {
                    HeroSource.Artwork(firstSongArt.first!!, isAnimated = firstSongArt.second)
                } else {
                    HeroSource.Default
                }
            }
        }
    }
}

/**
 * Extracts the dominant tint color from a hero artwork URL.
 * Returns [AppleTokens.AccentRed] as fallback until extraction completes.
 */
/**
 * Clamps an extracted artwork color into a pleasant deep background tint:
 * caps lightness (pale/skin/whitish colors become rich, legible) and floors
 * saturation (avoids washed-out grey). Keeps the hue.
 */
fun Color.asDeepTint(): Color {
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(toArgb(), hsl)
    hsl[1] = hsl[1].coerceAtLeast(0.28f)   // floor saturation
    hsl[2] = hsl[2].coerceIn(0.14f, 0.34f) // cap lightness → deep, not pale
    return Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
}

/** Same clamp family as [asDeepTint], tuned for a mesh-wash blob instead of a
 *  flat page background: lighter and more saturated so it reads as a glow. */
fun Color.asVividAccent(): Color {
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(toArgb(), hsl)
    hsl[1] = hsl[1].coerceAtLeast(0.45f)
    hsl[2] = hsl[2].coerceIn(0.45f, 0.68f)
    return Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
}

/** Same clamp family as [asDeepTint], a shade lighter — the wash a real blur
 *  of the artwork would leave behind, not the page's own deep tint. */
fun Color.asWashTint(): Color {
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(toArgb(), hsl)
    hsl[1] = hsl[1].coerceAtLeast(0.22f)
    hsl[2] = hsl[2].coerceIn(0.20f, 0.42f)
    return Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
}

/**
 * The artwork palette for [url], from cache when it is there and by extraction when it is not.
 *
 * Cheap on a repeat call even when the cache misses: the 100x100 decode Coil does here
 * lands in its own memory cache, so a second caller pays a lookup rather than a fetch.
 */
suspend fun heroPaletteOf(context: Context, url: String): HeroPalette? {
    heroPaletteCache.get(url)?.let { return it }
    return withContext(Dispatchers.IO) {
        try {
            val request = ImageRequest.Builder(context)
                .data(url)
                .size(100, 100)
                .allowHardware(false)
                .build()
            context.imageLoader.execute(request).image?.toBitmap()
                ?.extractHeroPalette(cacheKey = url)
                ?.let { raw ->
                    val background = raw.background.asDeepTint()
                    val accent = raw.accent.asVividAccent()
                    // Type is derived AFTER the tint clamps, not before: the engine's own
                    // scheme is built against the background IT chose, and asDeepTint then
                    // moves that background. Running the same ladder against the colour the
                    // screen actually paints is what keeps the contrast targets true --
                    // and it is what replaces "white at 70% alpha" for descriptions.
                    val text = textHierarchyOn(background = background, tint = accent)
                    HeroPalette(
                        background = background,
                        accent = accent,
                        wash = raw.wash.asWashTint(),
                        primaryText = text[0],
                        secondaryText = text[1],
                    )
                }
                // Not cached on a miss: a later visit with a working network should still
                // get the real artwork colours rather than a stand-in pinned forever.
                ?.also { heroPaletteCache.put(url, it) }
        } catch (_: Exception) {
            null
        }
    }
}

/** The flat background half of [heroPaletteOf] — every existing caller only wants this. */
suspend fun heroTintOf(context: Context, url: String): Color? = heroPaletteOf(context, url)?.background

/**
 * Starts extracting [url]'s palette now, off any composition.
 *
 * Called as navigation into a hero screen begins, so the colours are already in the cache by
 * the time that screen composes -- otherwise the extraction starts on arrival and the
 * background visibly washes in a beat after the artwork.
 */
fun prewarmHeroTint(context: Context, url: String?) {
    if (url.isNullOrEmpty() || heroPaletteCache.get(url) != null) return
    val appContext = context.applicationContext
    heroTintScope.launch { heroPaletteOf(appContext, url) }
}

private val heroTintScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * The full [HeroPalette] a screen builds its background from — flat tint,
 * mesh-wash accent and edge wash together, so all three come from the one
 * cached extraction instead of three separate ones drifting out of sync.
 */
@Composable
fun rememberHeroPalette(url: String?): HeroPalette {
    val context = LocalContext.current

    // Where a failed extraction lands. Black is only ever the *pending*
    // value below; a device that couldn't fetch the artwork (offline, 403,
    // decode failure, image evicted under memory pressure) resolves to the
    // user's own theme colour instead, so a miss degrades to their accent,
    // not to black.
    val fallbackTint = LocalAccentColor.current.asDeepTint()
    val fallbackPalette = remember(fallbackTint) {
        HeroPalette(background = fallbackTint, accent = fallbackTint.asVividAccent(), wash = fallbackTint.asWashTint())
    }

    // The same accent fallback as a failed extraction, not black, while the real
    // extraction is pending -- seeded from the cache so a revisited image shows
    // its colours at once. A bare black background reads as broken, not pending
    // -- most visibly on the tile<->screen container morph, where every
    // navigation (and every recomposition mid predictive-back-cancel) re-seeds
    // this and would briefly show a dead black screen behind the morphing artwork.
    var palette by remember(url) { mutableStateOf(url?.let { heroPaletteCache.get(it) } ?: fallbackPalette) }

    LaunchedEffect(url) {
        // No early return on a cache hit. Extraction can already be in flight from
        // prewarmHeroTint, and bailing out because "someone is handling it" left the
        // palette stuck on its pending fallback forever.
        palette = (url?.let { heroPaletteOf(context, it) }) ?: fallbackPalette
    }

    // Root-cause fix, not per-screen: every hero-tinted screen (Artist, Album,
    // Playlist, Search, ...) reads its background straight off this value, but
    // only HeroBackground's own separate PureBlackHeroBackgroundKey check
    // honored the setting — every screen that composes its own tint plane
    // instead of going through HeroBackground silently ignored it. Applying the
    // override here fixes every caller at once instead of repeating this check
    // in 17 files.
    val (pureBlack) = rememberPreference(PureBlackHeroBackgroundKey, false)
    return if (pureBlack) {
        HeroPalette(background = Color.Black, accent = Color.Black, wash = Color.Black)
    } else {
        palette
    }
}

@Composable
fun rememberHeroTint(url: String?): Color = rememberHeroPalette(url).background

/**
 * The screen surface below a hero image: an eased wash down to [screenBackground]
 * with two soft colour blobs breaking up what would otherwise be a flat vertical
 * ramp — draws the same idea a blurred-artwork backdrop reaches for, but from the
 * extracted [palette] alone, so it costs the same regardless of API level and
 * never re-shows a picture that is already on screen above it.
 *
 * Sized entirely by [modifier]; callers place it as the first child behind their
 * hero content, same as any other background layer.
 */
@Composable
fun HeroMeshWash(
    palette: HeroPalette,
    screenBackground: Color,
    modifier: Modifier = Modifier,
    /** Fraction of the surface's height the wash is still at full strength. */
    washFraction: Float = 0.5f,
) {
    Canvas(modifier) {
        drawRect(
            Brush.verticalGradient(
                0f to palette.wash,
                washFraction to palette.wash,
                washFraction + (1f - washFraction) * 0.45f to lerpColor(palette.wash, screenBackground, 0.20f),
                washFraction + (1f - washFraction) * 0.80f to lerpColor(palette.wash, screenBackground, 0.65f),
                1f to screenBackground,
            ),
        )
        heroWashBlob(palette.accent.copy(alpha = 0.16f), Offset(0.16f, washFraction + 0.10f), 0.80f)
        heroWashBlob(palette.wash.copy(alpha = 0.26f), Offset(0.90f, washFraction + 0.32f), 0.95f)
    }
}

/** One soft radial blob: full colour at [at], gone by [radiusFraction] of the width. */
private fun DrawScope.heroWashBlob(color: Color, at: Offset, radiusFraction: Float) {
    val center = Offset(at.x * size.width, at.y * size.height)
    val radius = size.width * radiusFraction
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color, color.copy(alpha = 0f)),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}

/**
 * The screen background tint actually used — [contentTint] (per-screen, derived
 * from artwork/thumbnails) when the user is on system/dynamic color, or their
 * own picked color (Theme settings) as a flat override when they're not. Ties
 * "custom theme color" directly to what screens paint behind their content,
 * not just Material's accent colors.
 */
@Composable
fun rememberAppBackgroundTint(contentTint: Color): Color {
    val (isDynamic) = rememberPreference(DynamicThemeKey, defaultValue = true)
    val (selectedColorInt) = rememberPreference(SelectedThemeColorKey, defaultValue = DefaultThemeColor.toArgb())
    return if (isDynamic) contentTint else Color(selectedColorInt)
}

/**
 * Album-screen style hero header: a full-width square artwork that fades
 * (DstIn vertical gradient) into whatever tint plane sits behind it, so the
 * image dissolves into the color. Meant to be the FIRST item of a LazyColumn
 * whose parent paints [com.convx.music.ui.theme.AppleTokens] tint behind it.
 *
 * Falls back to the default music image when [artworkUrl] is null/blank.
 */
@Composable
fun AlbumStyleHeroImage(
    artworkUrl: String?,
    modifier: Modifier = Modifier,
    // Extra pull-up beyond the status bar, to lift the square higher like AlbumScreen.
    extraPullUp: androidx.compose.ui.unit.Dp = 48.dp,
    // Driven by rememberHeroZoom() for pull-to-zoom. Applied to the drawn image
    // only, so growing it never re-lays-out the list underneath.
    heroScale: Float = 1f,
) {
    val topInset = androidx.compose.foundation.layout.WindowInsets.systemBars
        .asPaddingValues().calculateTopPadding()
    val density = LocalDensity.current
    val pullUpPx = with(density) { (topInset + extraPullUp).roundToPx() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            // Report a shorter height and draw shifted up: the image top tucks
            // under the status bar and following content sits tight (no gap).
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val h = (placeable.height - pullUpPx).coerceAtLeast(0)
                layout(placeable.width, h) {
                    placeable.place(0, -pullUpPx)
                }
            }
            .graphicsLayer {
                scaleX = heroScale
                scaleY = heroScale
                compositingStrategy = CompositingStrategy.Offscreen
            },
    ) {
        if (!artworkUrl.isNullOrBlank()) {
            AsyncImage(
                model = artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                // The fade lives on the image, NOT the box: the merge band below
                // is a sibling and must reach the join at full strength.
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Black, Color.Transparent),
                                startY = size.height * 0.55f,
                                endY = size.height,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    },
            )
        } else {
            Image(
                painter = painterResource(R.drawable.music_note),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize(0.4f)
                    .align(Alignment.Center)
                    .graphicsLayer { alpha = 0.3f },
            )
        }

        // BitChord-style join: a blurred copy of the artwork centred on the
        // foot, so picture and page colour smear into one another instead of
        // meeting at an edge. Hangs half below the box, under the header text.
        HeroMergeBand(
            artworkUrl = artworkUrl,
            heroRatio = 1f,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = HeroMergeBandHeight / 2),
        )
    }
}

/**
 * Full-bleed hero background: flat tint plane with the hero image on top,
 * fading vertically into the tint at its bottom edge.
 *
 * NO gradient scrim — the image dissolves into the color plane.
 * Adaptive contrast: descendants should use [AppleTokens.onColor] for text.
 */
@Composable
fun HeroBackground(
    tint: Color,
    heroSource: HeroSource,
    modifier: Modifier = Modifier,
    // Apple Music browse/player style: full-bleed heavily-blurred artwork with a
    // darkening scrim, instead of the default sharp top-hero fading to tint.
    blurArtwork: Boolean = false,
    // With [blurArtwork], drop the crisp upper layer so the whole background is
    // uniformly blurred (no sharp-top/blurred-bottom split).
    fullBlur: Boolean = false,
    // The [HeroSource.Default] placeholder music-note. Off for screens that want
    // a clean flat tint behind glass (e.g. search).
    showDefaultIcon: Boolean = true,
    // Decorative primary-color wash fading in at the bottom of the screen.
    // Ignored when [meshWash] is set — the two are alternatives, not layers.
    bottomGradient: Boolean = false,
    // Artwork-derived wash + colour blobs instead of the flat primary-color
    // [bottomGradient] — pass the same [HeroPalette] the screen's own [tint]
    // came from so the wash matches the artwork, not the user's accent.
    meshWash: HeroPalette? = null,
    // 0..1 — extra blur ramped onto the crisp upper artwork as the list scrolls up.
    topBlurProgress: Float = 0f,
    // Pull-to-zoom scale for the hero artwork (1 = rest; >1 while overscrolling the top).
    heroScale: Float = 1f,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val (pureBlack) = rememberPreference(PureBlackHeroBackgroundKey, false)
    // Reuses the existing Default branch (flat tint, no image) to suppress the
    // artwork backdrop entirely rather than adding a second code path.
    val effectiveTint = if (pureBlack) Color.Black else tint
    val effectiveHeroSource = if (pureBlack) HeroSource.Default else heroSource
    Box(modifier = modifier.background(effectiveTint)) {
        when (val heroSource = effectiveHeroSource) {
            is HeroSource.Artwork -> {
                // Fade-in animation for the hero image (the "smooth transition"
                // as the artwork resolves).
                var visible by remember { mutableStateOf(false) }
                val alpha by animateFloatAsState(
                    targetValue = if (visible) 1f else 0f,
                    animationSpec = tween(durationMillis = 600),
                    label = "heroFadeIn",
                )

                if (blurArtwork) {
                    // Blurred lower layer: fills behind the list and dissolves
                    // into the tint (the primary-color gradient) toward the
                    // bottom. Decoded at low resolution — a 48dp blur erases all
                    // detail, so a ~160px source is visually identical to full-res
                    // while cutting decode time and bitmap memory dramatically.
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(heroSource.url)
                            .size(160)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(HeroBlurRadius)
                            .graphicsLayer {
                                this.alpha = alpha
                                scaleX = heroScale
                                scaleY = heroScale
                                compositingStrategy = CompositingStrategy.Offscreen
                            }
                            .drawWithContent {
                                drawContent()
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(Color.Black, Color.Transparent),
                                        startY = size.height * 0.5f,
                                        endY = size.height,
                                    ),
                                    blendMode = BlendMode.DstIn,
                                )
                            },
                        onSuccess = { visible = true },
                    )
                    // Sharp upper layer: the top half stays crisp, then dissolves
                    // so the blur takes over from roughly where the list begins.
                    // Skipped entirely in fullBlur mode (uniformly blurred background).
                    if (!fullBlur) {
                    AsyncImage(
                        model = heroSource.url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                // Cross-fade to the pre-blurred layer underneath as you
                                // scroll — no per-frame RenderEffect, so it stays smooth.
                                this.alpha = alpha * (1f - topBlurProgress.coerceIn(0f, 1f))
                                scaleX = heroScale
                                scaleY = heroScale
                                compositingStrategy = CompositingStrategy.Offscreen
                            }
                            .drawWithContent {
                                drawContent()
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(Color.Black, Color.Transparent),
                                        startY = size.height * 0.35f,
                                        endY = size.height * 0.6f,
                                    ),
                                    blendMode = BlendMode.DstIn,
                                )
                            },
                    )
                    }
                } else {
                    AsyncImage(
                        model = heroSource.url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                this.alpha = alpha
                                compositingStrategy = CompositingStrategy.Offscreen
                            }
                            .drawWithContent {
                                drawContent()
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(Color.Black, Color.Transparent),
                                        startY = size.height * 0.4f,
                                        endY = size.height
                                    ),
                                    blendMode = BlendMode.DstIn
                                )
                            },
                        onSuccess = { visible = true },
                    )
                }
            }
            is HeroSource.Default -> {
                // No placeholder icon: a clean flat tint plane behind the content.
            }
        }
        if (meshWash != null) {
            HeroMeshWash(
                palette = meshWash,
                screenBackground = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (bottomGradient) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.55f to Color.Transparent,
                            1f to primaryColor.copy(alpha = 0.55f),
                        ),
                    ),
            )
        }
        content()
    }
}

/** Side of the hero card in tab view. Matches the reference's ~1/4-width square. */
val HeroCardSize: Dp = 200.dp

/**
 * Top clearance the wide hero reserves for the floating Back button, which lives
 * at the top-left of the content area in tab view and would otherwise sit over
 * the artwork. One number — raise it if the button still crowds the card.
 */
val HeroBackButtonClearance: Dp = 56.dp

/**
 * Wide-layout hero: the artwork as a card with the title beside it.
 *
 * The phone hero is a full-bleed square with the title overlaid at its bottom —
 * on a wide layout that wastes the whole right half of the screen and pushes the
 * actual content below the fold. The reference instead makes the artwork a
 * bounded card, sets the title, subtitle and actions in a column next to it, and
 * starts the list directly underneath.
 *
 * Screens choose between this and their phone header on [LocalTabView].
 */
@Composable
fun HeroCardHeader(
    artworkUrl: String?,
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    cardSize: Dp = HeroCardSize,
    // Artist pages are circular in the reference; albums and playlists are square.
    circular: Boolean = false,
    subtitle: (@Composable () -> Unit)? = null,
    actions: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val shape = if (circular) {
        ContinuousRoundedRectangle(percent = 50)
    } else {
        ContinuousRoundedRectangle(16.dp)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            // Top clearance drops the card below the floating Back button.
            .padding(start = 24.dp, end = 24.dp, top = HeroBackButtonClearance, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(cardSize)
                .shadow(elevation = 12.dp, shape = shape)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            if (!artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Image(
                    painter = painterResource(R.drawable.music_note),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize(0.4f)
                        .align(Alignment.Center)
                        .graphicsLayer { alpha = 0.3f },
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            title()
            subtitle?.invoke()
            actions?.invoke(this)
        }
    }
}

/**
 * Convenience overload for the common "big title, quieter subtitle line" pair,
 * so each screen doesn't restate the same two Text styles.
 */
@Composable
fun HeroCardHeader(
    artworkUrl: String?,
    titleText: String,
    modifier: Modifier = Modifier,
    subtitleText: String? = null,
    cardSize: Dp = HeroCardSize,
    circular: Boolean = false,
    actions: (@Composable ColumnScope.() -> Unit)? = null,
) {
    HeroCardHeader(
        artworkUrl = artworkUrl,
        modifier = modifier,
        cardSize = cardSize,
        circular = circular,
        title = {
            Text(
                text = titleText,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        subtitle = subtitleText?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        actions = actions,
    )
}
