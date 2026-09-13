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
import androidx.compose.foundation.layout.fillMaxHeight
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
import com.convx.music.ui.ios.compat.AppTheme
import com.convx.music.ui.ios.compat.Text
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
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
import coil3.request.crossfade
import coil3.toBitmap
import com.convx.music.R
import com.convx.music.constants.DynamicThemeKey
import com.convx.music.constants.PureBlackHeroBackgroundKey
import com.convx.music.constants.ReduceDynamicBlurKey
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
import com.convx.music.ui.utils.Motion
import com.convx.music.ui.utils.resize
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

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
    /** The page colour below the join, for the below-13 plain-gradient fallback. */
    pageColor: Color = Color.Black,
    /**
     * When non-null (and on Android 13+), blurs the whole screen behind this band
     * with Haze instead of re-drawing a blurred copy of the artwork. The screen's
     * root content must be marked `Modifier.hazeSource(hazeState)` for the band to
     * have anything to sample. On Android 12 and below — where RenderEffect blur is
     * a no-op — the band falls back to a plain [pageColor] crossfade, so the hero
     * still dissolves into the page without a hard edge.
     */
    hazeState: HazeState? = null,
) {
    // Haze (RenderEffect) only reliably blurs on Android 13+; below that the
    // crossfade is the path that actually renders. Also honour the low-power
    // preference: giving the screen a live blur when the user asked for none
    // defeats the point of the setting.
    val reduceBlur by rememberPreference(ReduceDynamicBlurKey, false)
    val useHaze = hazeState != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !reduceBlur

    if (!useHaze || hazeState == null) {
        // Baked join: plain eased crossfade into [pageColor], peak centred on
        // the join, transparent at both edges so list rows below are never
        // covered. Same footprint as the Haze band so callers' align/offset
        // line up on the same join.
        Box(
            modifier
                .fillMaxWidth()
                .height(bandHeight)
                .background(
                    Brush.verticalGradient(
                        colorStops = Array(12) { i ->
                            val t = i / 11f
                            val window = when {
                                t < 0.85f -> t / 0.85f
                                else -> 1f - (t - 0.85f) / 0.15f
                            }
                            val eased = androidx.compose.animation.core.EaseOutCubic.transform(
                                window.coerceIn(0f, 1f),
                            )
                            t to pageColor.copy(alpha = eased)
                        },
                    ),
                ),
        )
        return
    }

    // One pane of glass across the join: Haze samples the whole screen behind
    // this band (both the artwork above the join and the tint below it), so the
    // artwork's colour is carried down past where the picture ends and the page
    // colour is carried up into it — a merge rather than a cut, and the line
    // that used to separate the two has no content left to be a line between.
    // The band's own edges are hidden by a vertical alpha mask so there is no
    // moment where the blur starts or stops.
    @OptIn(ExperimentalHazeMaterialsApi::class)
    Box(
        modifier
            .fillMaxWidth()
            .height(bandHeight)
            .hazeEffect(
                state = hazeState,
                style = HazeMaterials.ultraThin(pageColor),
            ) {
                // A blur has nothing to sample past the edge of its own layer, so
                // the first blur-radius of the mask shows mostly the flat material
                // colour. Keyed to the page colour behind the join so that band
                // reads as the tint it covers, not as a hard-edged pane.
                noiseFactor = 0f
                mask = Brush.verticalGradient(
                    0.00f to Color.Transparent,
                    0.50f to Color.Black,
                    1.00f to Color.Transparent,
                )
            },
    )
}

/** Where [HeroMergeBand] is centred: half over the artwork, half over the page. */
val HeroMergeBandHeight: Dp = 320.dp

/**
 * The progressive blur at the very top of a hero screen (BitChord/Apple-Music
 * treatment): as the artwork scrolls up under the status bar, a strip of Haze
 * ramps from nothing to full so the picture dissolves smoothly into the theme
 * behind the glass instead of sliding under a hard edge.
 *
 * Intensity follows [progress] (0..1, e.g. from [rememberHeroTopBlur]) on the
 * strip's height: zero blur when the hero is fully visible, full when it has
 * scrolled off. Below Android 13 (RenderEffect no-op) or when dynamic blur is
 * reduced, this paints a translucent scrim in [pageColor] instead — a plain
 * fade that still hides where the hero crosses into the status bar.
 */
@Composable
fun HeroTopBlur(
    hazeState: HazeState?,
    progress: Float,
    pageColor: Color = AppTheme.colorScheme.background,
    modifier: Modifier = Modifier,
    /** Height of the blur strip + the run below it the fade needs to disappear. */
    height: Dp = 140.dp,
) {
    val reduceBlur by rememberPreference(ReduceDynamicBlurKey, false)
    val useHaze =
        hazeState != null &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !reduceBlur &&
            progress > 0.001f

    if (!useHaze) {
        // Below 13 / blur reduced: a translucent scrim in the page's colour that
        // fades in with scroll — the same dissolve, done with a colour the
        // hardware can always draw. No Haze, no RenderEffect, no cost.
        Box(
            modifier
                .fillMaxWidth()
                .height(height)
                .graphicsLayer { alpha = progress.coerceIn(0f, 1f) }
                .background(pageColor),
        )
        return
    }

    @OptIn(ExperimentalHazeMaterialsApi::class)
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .hazeEffect(
                state = hazeState,
                style = HazeMaterials.ultraThin(pageColor),
            ) {
                blurRadius = 36.dp
                noiseFactor = 0f
                // The whole strip's intensity follows scroll: zero blur when the
                // hero is fully visible, full when it has scrolled under the bar.
                alpha = progress.coerceIn(0f, 1f)
                // Within the strip itself, blur keeps full strength down to the
                // foot and ramps to nothing at the very bottom edge, so the glass
                // stops before the strip does and never reads as a hard pane.
                progressive = HazeProgressive.verticalGradient(
                    easing = androidx.compose.animation.core.EaseInCubic,
                    startIntensity = 1f,
                    endIntensity = 0f,
                )
            },
    )
}

/**
 * Scroll-driven progressive blur for a hero artwork: two pre-baked blur plates
 * cross-faded in sequence by [progress], so the picture reads as progressively
 * smearing as it scrolls under the top bar — reaching [blurRadius] at full
 * scroll — and sharpening back on the way down.
 *
 * Each plate is baked once on a tiny decode (see [BlurredBackdropImage]) and
 * cross-faded by alpha, so no RenderEffect re-runs per frame — the scroll only
 * drives two alpha writes. Inert (draws nothing) at [progress] 0.
 */
@Composable
fun HeroScrollBlur(
    artworkUrl: String?,
    progress: Float,
    modifier: Modifier = Modifier,
    blurRadius: Dp = HeroBlurRadius,
    decodeSize: Int = 160,
) {
    val reduceBlur by rememberPreference(ReduceDynamicBlurKey, false)
    val p = progress.coerceIn(0f, 1f)
    if (reduceBlur || artworkUrl.isNullOrBlank() || p <= 0.01f) return

    // Three-step eased crossfade: soft arrives first, hands to mid, hands to
    // full — reads as growing blur (iOS-style) while scroll drives alpha only,
    // no per-frame RenderEffect. Same foot mask as the hero so blur never
    // invents a new edge.
    val eased = androidx.compose.animation.core.EaseOutCubic.transform(p)
    val softAlpha = heroBlurBand(eased, start = 0.02f, peak = 0.30f, end = 0.70f)
    val midAlpha = heroBlurBand(eased, start = 0.25f, peak = 0.60f, end = 0.90f)
    val fullAlpha = ((eased - 0.55f) / 0.45f).coerceIn(0f, 1f)
    if (softAlpha <= 0f && midAlpha <= 0f && fullAlpha <= 0f) return

    Box(modifier.fillMaxSize()) {
        if (softAlpha > 0f) {
            BlurredBackdropImage(
                model = artworkUrl,
                screenBlurRadiusDp = (blurRadius * 0.3f).value,
                decodeSize = decodeSize,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = softAlpha },
            )
        }
        if (midAlpha > 0f) {
            BlurredBackdropImage(
                model = artworkUrl,
                screenBlurRadiusDp = (blurRadius * 0.6f).value,
                decodeSize = decodeSize,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = midAlpha },
            )
        }
        if (fullAlpha > 0f) {
            BlurredBackdropImage(
                model = artworkUrl,
                screenBlurRadiusDp = blurRadius.value,
                decodeSize = decodeSize,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = fullAlpha },
            )
        }
    }
}

/** Triangular 0→1→0 window: full at [peak], gone at [start]/[end]. */
private fun heroBlurBand(p: Float, start: Float, peak: Float, end: Float): Float =
    when {
        p <= start || p >= end -> 0f
        p < peak -> (p - start) / (peak - start)
        else -> 1f - (p - peak) / (end - peak)
    }

/**
 * Hero palettes keyed by artwork URL (the image's stable id): the Coil decode +
 * palette extraction is expensive, so the same artwork never recomputes on
 * revisit or recomposition. Bounded for a long session.
 */
private val heroPaletteCache = android.util.LruCache<String, HeroPalette>(128)

/**
 * 0..1 scroll progress for [HeroBackground]'s `topBlurProgress`: ramps as the first
 * screenful scrolls up, then pins at 1. Pass a hero screen's list state.
 *
 * Pixel threshold, not a fraction of the header's own height: these screens also
 * layer [ChromeScrim] over this same strip, ramped by [rememberChromeScrimProgress]
 * on the same fixed 300px. A header-height-relative ramp (the old `fraction`
 * approach) reaches full blur far later than a tall hero header's own 300px, so
 * for most of the scroll the strip was under near-opaque scrim with barely any
 * blur yet — reading as "no blur" even though it was there, just buried. Sharing
 * the threshold keeps the two in lockstep so the glass is visible as the scrim
 * darkens, not swallowed by it.
 */
@Composable
fun rememberHeroTopBlur(
    state: androidx.compose.foundation.lazy.LazyListState,
    thresholdPx: Float = 300f,
): Float {
    val progress by remember(state) {
        androidx.compose.runtime.derivedStateOf {
            val scrolledPx = if (state.firstVisibleItemIndex == 0) {
                state.firstVisibleItemScrollOffset.toFloat()
            } else {
                thresholdPx
            }
            (scrolledPx / thresholdPx).coerceIn(0f, 1f)
        }
    }
    return progress
}

/** [rememberHeroTopBlur], for a header that leads a grid instead of a list. */
@Composable
fun rememberHeroTopBlur(
    state: androidx.compose.foundation.lazy.grid.LazyGridState,
    thresholdPx: Float = 300f,
): Float {
    val progress by remember(state) {
        androidx.compose.runtime.derivedStateOf {
            val scrolledPx = if (state.firstVisibleItemIndex == 0) {
                state.firstVisibleItemScrollOffset.toFloat()
            } else {
                thresholdPx
            }
            (scrolledPx / thresholdPx).coerceIn(0f, 1f)
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

/**
 * Transforms an ambient / hero background color into a refined, desaturated liquid glass surface tint:
 * greyish on the colored side (low saturation 0.12f..0.22f), contrast-adjusted for dark or light surfaces
 * so it remains subtle, felt without overpowering, and maintains perfect legibility.
 */
fun Color.asGlassSurfaceTint(isDark: Boolean = true): Color {
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(toArgb(), hsl)
    hsl[1] = hsl[1].coerceIn(0.12f, 0.22f) // low saturation: greyish with ambient color whisper
    hsl[2] = if (isDark) {
        hsl[2].coerceIn(0.20f, 0.28f) // deep glass slate
    } else {
        hsl[2].coerceIn(0.88f, 0.94f) // frosted light glass
    }
    return Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
}

/** Specular highlight / rim light ("pluck") tint: the ambient hue, muted enough
 *  to read as lit glass rather than neon — saturated enough to see the color,
 *  never colorful. Shared by every glass surface on the screen (see
 *  [Modifier.liquidGlass]'s default rim), so all rims match. */
fun Color.asRimPop(isDark: Boolean = true): Color {
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(toArgb(), hsl)
    hsl[1] = hsl[1].coerceIn(0.40f, 0.55f) // visible hue, not neon
    hsl[2] = if (isDark) hsl[2].coerceIn(0.62f, 0.74f) else hsl[2].coerceIn(0.75f, 0.90f)
    return Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
}

/** Muted icon tint for deselected nav items and floating chrome (back, share,
 *  menu) on ambient screens: same hue as the mesh background, just enough
 *  saturation to see the color, drawn under additiveChrome (BlendMode.Plus)
 *  for its glow. Replaces [asIconPop]'s vivid floor (0.80) which turned
 *  Library blue. */
fun Color.asSoftIconTint(): Color {
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(toArgb(), hsl)
    hsl[1] = hsl[1].coerceIn(0.32f, 0.45f) // hue visible, never colorful
    hsl[2] = hsl[2].coerceIn(0.66f, 0.76f) // bright enough to glow through Plus
    return Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
}

/** Glass surface fill for ambient screens: the neutral frost grey carried
 *  20% toward the mesh hue, so the surface feels tinted without going
 *  colorful. Painted at ~0.36 opacity by [Modifier.liquidGlass]. */
fun Color.asAmbientSurfaceTint(isDark: Boolean = true): Color {
    val frosted = asGlassSurfaceTint(isDark)
    return lerpColor(frosted, this, 0.20f)
}

/** Selection-pill wash: lighter than the ambient surface with a hint more of
 *  the mesh hue. Alpha is picked at the call site by contrast (0.70 on light
 *  ambient, 0.80 on dark). */
fun Color.asPuckTint(isDark: Boolean = true): Color {
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(toArgb(), hsl)
    hsl[1] = hsl[1].coerceIn(0.15f, 0.25f) // a hint of the hue
    hsl[2] = if (isDark) {
        hsl[2].coerceIn(0.32f, 0.42f) // lighter than the 0.20..0.28 surface
    } else {
        hsl[2].coerceIn(0.80f, 0.88f)
    }
    return Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
}

/** Bright and saturated icon tint for additive chrome (BlendMode.Plus) over glass:
 *  vibrant hue with high saturation and sufficient brightness so it glows vividly. */
fun Color.asIconPop(): Color {
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(toArgb(), hsl)
    hsl[1] = hsl[1].coerceAtLeast(0.80f) // bright and saturated
    hsl[2] = hsl[2].coerceIn(0.58f, 0.76f) // light and vivid for additive chroma glow
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
                .size(200, 200)
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

    val isDark = AppTheme.colorScheme.surface.luminance() <= 0.5f
    val fallbackTint = com.convx.music.ui.theme.GlassTintTokens.baseSurfaceColor(isDark)
    val fallbackPalette = remember(fallbackTint) {
        HeroPalette(
            background = fallbackTint,
            accent = fallbackTint,
            wash = fallbackTint,
            primaryText = if (isDark) Color.White else Color.Black,
            secondaryText = if (isDark) Color(0xFFEBEBF5) else Color(0xFF3C3C43),
        )
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
    // The screen's own Haze source. Without one the merge band below can only fall
    // back to a flat crossfade — a fade rather than a merge, which is what the
    // playlist screens were getting while Album and Artist got the real thing.
    hazeState: HazeState? = null,
    // The colour under the join, for the band's pre-13 / reduced-blur fallback.
    pageColor: Color = Color.Black,
    // 0..1 scroll progress (e.g. from rememberHeroTopBlur): the artwork blurs
    // progressively as it scrolls up, sharpening back on the way down.
    scrollBlurProgress: Float = 0f,
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
            // Thumb-then-full: tile-sized bitmap lands opaque frame one (morph
            // grows, never swaps), 1024px fades over it. Fade lives on the box,
            // NOT the image: the merge band below is a sibling and must reach
            // the join at full strength.
            ThumbThenFullHero(
                thumbUrl = artworkUrl,
                fullUrl = artworkUrl,
                contentScale = ContentScale.Crop,
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

        // Scroll-driven progressive blur over the artwork, same foot fade so the
        // plates dissolve where the art does. Inert at rest.
        HeroScrollBlur(
            artworkUrl = artworkUrl,
            progress = scrollBlurProgress,
            modifier = Modifier.drawWithContent {
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

        // BitChord-style join: a blurred copy of the artwork centred on the
        // foot, so picture and page colour smear into one another instead of
        // meeting at an edge. Hangs half below the box, under the header text.
        HeroMergeBand(
            artworkUrl = artworkUrl,
            heroRatio = 1f,
            pageColor = pageColor,
            hazeState = hazeState,
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
    val primaryColor = AppTheme.colorScheme.primary
    val (pureBlack) = rememberPreference(PureBlackHeroBackgroundKey, false)
    // Reuses the existing Default branch (flat tint, no image) to suppress the
    // artwork backdrop entirely rather than adding a second code path.
    val effectiveTint = if (pureBlack) Color.Black else tint
    val effectiveHeroSource = if (pureBlack) HeroSource.Default else heroSource
    // Per-screen Haze source (Album/Artist pattern): this background becomes the
    // backdrop the top blur strip samples, so the artwork dissolves as it scrolls
    // under the status bar instead of sliding under a hard edge.
    val hazeState = remember { HazeState() }
    Box(modifier = modifier.background(effectiveTint).hazeSource(hazeState)) {
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
                    val blurP = topBlurProgress.coerceIn(0f, 1f)
                    // Blurred lower layer: fills behind the list and dissolves
                    // into the tint (the primary-color gradient) toward the
                    // bottom. Decoded at low resolution — a 48dp blur erases all
                    // detail, so a ~160px source is visually identical to full-res
                    // while cutting decode time and bitmap memory dramatically.
                    // Baked once per artwork rather than Modifier.blur, which is a
                    // RenderEffect and so re-runs on EVERY frame -- full-screen, on every
                    // screen that uses this background, for a picture that does not change
                    // while the screen is up. Same 160px source and same 48dp screen-space
                    // radius, so the result is the same; it is computed on the CPU once and
                    // cached per artwork instead.
                    BlurredBackdropImage(
                        model = heroSource.url,
                        screenBlurRadiusDp = HeroBlurRadius.value,
                        decodeSize = 160,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
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
                        onReady = { visible = true },
                    )
                    // The ramp's middle step between the sharp layer below and the
                    // heavy base above: arrives as the sharp layer leaves and
                    // departs as the heavy base takes over, so the scroll blur
                    // reads as growing rather than one hard sharp-to-blurred cut.
                    // Same footprint (scale, foot fade) as the base it blends into.
                    val midAlpha = alpha * heroBlurBand(blurP, start = 0.05f, peak = 0.50f, end = 0.95f)
                    if (midAlpha > 0.01f) {
                        BlurredBackdropImage(
                            model = heroSource.url,
                            screenBlurRadiusDp = (HeroBlurRadius * 0.45f).value,
                            decodeSize = 160,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    this.alpha = midAlpha
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
                        )
                    }
                    // Past-the-base push at the top of the scroll, so full scroll
                    // reads as very blurred rather than settling at the base radius.
                    val heavyAlpha = alpha * ((blurP - 0.60f) / 0.40f).coerceIn(0f, 1f)
                    if (heavyAlpha > 0.01f) {
                        BlurredBackdropImage(
                            model = heroSource.url,
                            screenBlurRadiusDp = (HeroBlurRadius * 1.5f).value,
                            decodeSize = 160,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    this.alpha = heavyAlpha
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
                        )
                    }
                    // Sharp upper layer: the top half stays crisp, then dissolves
                    // so the blur takes over from roughly where the list begins.
                    // Skipped entirely in fullBlur mode (uniformly blurred background).
                    if (!fullBlur) {
                    AsyncImage(
                        model = heroSource.url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        // Second, independent path to `visible`. This layer used to rely
                        // entirely on the blurred one below flipping it; now that the blur
                        // is a CPU bake, a slow or failed bake would otherwise leave the
                        // whole hero pinned at alpha 0.
                        onSuccess = { visible = true },
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                // Hands over to the mid plate above (gone by mid
                                // scroll) rather than straight to the heavy base —
                                // no per-frame RenderEffect, so it stays smooth.
                                this.alpha = alpha * (1f - ((blurP - 0.05f) / 0.45f).coerceIn(0f, 1f))
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
                                        startY = size.height * 0.55f,
                                        endY = size.height
                                    ),
                                    blendMode = BlendMode.DstIn
                                )
                            },
                        onSuccess = { visible = true },
                    )
                    // Scroll-driven progressive blur over the sharp art, same foot
                    // fade so the plates dissolve where the art does. Inert at
                    // rest; fully blurred plate over the art at full scroll.
                    HeroScrollBlur(
                        artworkUrl = heroSource.url,
                        progress = topBlurProgress,
                        modifier = Modifier
                            .graphicsLayer {
                                compositingStrategy = CompositingStrategy.Offscreen
                            }
                            .drawWithContent {
                                drawContent()
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(Color.Black, Color.Transparent),
                                        startY = size.height * 0.55f,
                                        endY = size.height
                                    ),
                                    blendMode = BlendMode.DstIn
                                )
                            },
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
                screenBackground = AppTheme.colorScheme.background,
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

        // Progressive top blur: as [topBlurProgress] ramps with scroll, a strip of
        // Haze blurs the artwork beneath the status bar into the page colour, so the
        // hero dissolves instead of sliding under a hard edge. Inert (alpha 0) when
        // [topBlurProgress] stays 0, so screens that don't pass it are unaffected.
        HeroTopBlur(
            hazeState = hazeState,
            progress = topBlurProgress,
            pageColor = AppTheme.colorScheme.background,
            modifier = Modifier.align(Alignment.TopCenter),
        )
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
                .background(AppTheme.colorScheme.surfaceContainerHigh),
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
                style = AppTheme.typography.displaySmall,
                color = AppTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        subtitle = subtitleText?.let {
            {
                Text(
                    text = it,
                    style = AppTheme.typography.titleMedium,
                    color = AppTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        actions = actions,
    )
}

/**
 * Where the hero's blend zone starts, as a fraction of the photo height: above
 * this the plate stays sharp, below it blur and colour fade ramp to full at the
 * foot. Shared with the screen's header math so the title sits on the blend
 * start rather than somewhere up the sharp photo or down the flat tint.
 */
const val ArtistBlendStart = 0.85f

/**
 * BitChord-style artist hero: the photo full-bleed, very slightly taller than
 * wide, with a progressive blur running from zero at [ArtistBlendStart] to
 * [blurRadius] at the foot — sharp where the face is, increasingly smeared
 * where the page begins — so the picture dissolves into the page colour
 * instead of meeting it at an edge. The ramp is three plates at growing radii
 * over the same height (like the top bar's own progressive glass), not one
 * mask, so no single gradient edge survives to read as a line. A
 * full-strength foot wash in [pageColor] settles the join before
 * [HeroMergeBand]'s glass goes over it (a blur averages what it is given and
 * cannot invent agreement), and a static top shade keeps the floating
 * back/share chrome legible while the hero is fully visible, before the
 * scroll-driven scrim takes over.
 *
 * Below Android S RenderEffect blur is a no-op and the blurred plates simply
 * stop contributing; the wash plus the band's own plain-gradient fallback
 * still dissolve the edge, so the fallback is a fade, not a cut.
 */
@Composable
fun ArtistHeroImage(
    artworkUrl: String?,
    pageColor: Color,
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
    heroRatio: Float = 0.95f,
    blurRadius: Dp = 56.dp,
    heroScale: Float = 1f,
    overlay: @Composable BoxScope.() -> Unit = {},
    // 0..1 scroll progress (e.g. from rememberHeroTopBlur): the photo blurs
    // progressively as it scrolls up, sharpening back on the way down.
    scrollBlurProgress: Float = 0f,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(heroRatio)
            .graphicsLayer {
                scaleX = heroScale
                scaleY = heroScale
                compositingStrategy = CompositingStrategy.Offscreen
            },
    ) {
        if (!artworkUrl.isNullOrBlank()) {
            // Sharp plate, full-bleed: thumb tier lands opaque frame one so the
            // morph grows, 1200px fades over it like every other detail hero.
            ThumbThenFullHero(
                thumbUrl = artworkUrl,
                fullUrl = artworkUrl,
                fullPx = 1200,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // Foot wash: the photo stays fully sharp to its foot — the eased
            // wash alone dissolves the edge into the page colour. Edge only
            // (from 0.85): eased stops so no single gradient edge survives
            // to read as a line.
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = Array(12) { i ->
                                val t = i / 11f
                                val eased = androidx.compose.animation.core.EaseOutCubic.transform(
                                    ((t - 0.85f) / 0.15f).coerceIn(0f, 1f),
                                )
                                t to pageColor.copy(alpha = eased)
                            },
                        ),
                    ),
            )

            // Scroll-driven progressive blur over the photo. The outer box already
            // renders offscreen, so the plates blend cleanly. Inert at rest.
            HeroScrollBlur(
                artworkUrl = artworkUrl,
                progress = scrollBlurProgress,
                blurRadius = blurRadius,
            )
        } else {
            // Stand-in while there is no photo: a neutral wash, not flat tint,
            // so the fade structure still reads in previews and offline.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color(0xFF6B5B73),
                            1f to pageColor,
                        ),
                    ),
            )
            Image(
                painter = painterResource(R.drawable.music_note),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize(0.3f)
                    .align(Alignment.Center)
                    .graphicsLayer { alpha = 0.3f },
            )
        }

        // Static shade under the glass bar, BitChord's own: the scroll scrim
        // only arrives once the list moves, and at rest the buttons sit on raw
        // photo without it.
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.28f)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.35f),
                        1f to Color.Transparent,
                    ),
                ),
        )

        overlay()

        // The join itself: blurred colour centred on the foot, title/buttons
        // hang onto what it leaves behind. Sibling of the plates above, never a
        // parent — the glass must reach the join at full strength.
        HeroMergeBand(
            artworkUrl = artworkUrl,
            heroRatio = heroRatio,
            pageColor = pageColor,
            hazeState = hazeState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = HeroMergeBandHeight / 2),
        )
    }
}
