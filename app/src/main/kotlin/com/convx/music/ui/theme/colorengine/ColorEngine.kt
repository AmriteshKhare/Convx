/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.theme.colorengine

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Artwork colour analysis: a deterministic, perceptually-aware palette and a pair of
 * ready-to-use light/dark schemes for one image.
 *
 * Kotlin port of the Swift `Color Engine` (`ColorEngine.swift`, `JoiColorAnalysis.swift`).
 * The seven stages are unchanged:
 *
 * 1. sample the artwork down (resolution chosen from a 16px detail probe)
 * 2. scene understanding — optional, see [VisionData]
 * 3. spatial / text / skin weight map
 * 4. tone statistics and average colours
 * 5. quantised deterministic k-means plus medoid selection
 * 6. semantic roles, WCAG text hierarchy, scheme construction
 * 7. palette packaging
 *
 * Deterministic means what it says: the same bitmap always yields the same palette,
 * because the clustering PRNG is seeded from the pixels themselves and every tie-break
 * falls back to a stable bin id. That is what makes the result safe to cache by URL.
 *
 * Stage 2 has no Android implementation here — Apple's Vision framework has no
 * equivalent that does not mean adding ML Kit. Pass [VisionData] in if you have a
 * detector; otherwise the geometric and spatial weighting of stage 3 carries the
 * pipeline, which is what [VisionMode.DISABLED] selects.
 */
object ColorEngine {

    /** Bump when the pipeline's output changes, so stale cache entries are ignored. */
    private const val Version = "v26-kt1"

    // ~48 analyses. Each entry is a few hundred bytes of colours, not pixels, so this is
    // sized for how many artworks a session actually touches rather than for memory.
    private val cache = LruCache<String, ColorEngineResult>(48)

    /**
     * Analyses [bitmap] and returns its schemes and palette, or null if the bitmap
     * cannot be sampled (recycled, zero-sized, or clustering found nothing).
     *
     * [cacheKey] should identify the *image* — an artwork URL is the natural choice.
     * Runs the pixel work on [Dispatchers.Default]; safe to call from a composable's
     * side effect.
     */
    suspend fun analyze(
        bitmap: Bitmap,
        cacheKey: String,
        style: ColorStyle = ColorStyle.BALANCED,
        visionMode: VisionMode = VisionMode.DISABLED,
        vision: VisionData? = null,
    ): ColorEngineResult? {
        val effectiveKey = "$cacheKey|$Version|vision=$visionMode|style=$style"
        cache.get(effectiveKey)?.let { return it }
        val result = withContext(Dispatchers.Default) {
            runPipeline(bitmap, style, visionMode, vision)
        } ?: return null
        cache.put(effectiveKey, result)
        return result
    }

    /** Cached result for [cacheKey], if this image has already been analysed. */
    fun cached(
        cacheKey: String,
        style: ColorStyle = ColorStyle.BALANCED,
        visionMode: VisionMode = VisionMode.DISABLED,
    ): ColorEngineResult? = cache.get("$cacheKey|$Version|vision=$visionMode|style=$style")

    fun invalidateAll() = cache.evictAll()

    private fun runPipeline(
        bitmap: Bitmap,
        style: ColorStyle,
        visionMode: VisionMode,
        vision: VisionData?,
    ): ColorEngineResult? {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return null
        // A HARDWARE bitmap lives in graphics memory and throws on getPixels. The app's
        // image loader defaults to that config, so any caller must request
        // `allowHardware(false)` -- fail here rather than deep in the sampler.
        if (bitmap.config == Bitmap.Config.HARDWARE) return null
        val startNanos = System.nanoTime()

        val assessment = assessDetail(bitmap, visionMode) ?: return null
        val (sampleWidth, sampleHeight) =
            sampleDimensions(bitmap.width, bitmap.height, assessment.thumbnailSize)
        val pixels = sampleBitmap(bitmap, assessment.thumbnailSize) ?: return null
        val plane = makePixelPlane(pixels, sampleWidth, sampleHeight) ?: return null

        val weightMap = makeWeightMap(plane, vision, visionMode)
        val toneStatistics = analyzeTone(plane)
        val averages = calculateAverages(plane, weightMap.background)

        val kMeans = deterministicKMeans(plane, weightMap, assessment.requestedClusterCount)
        if (kMeans.clusters.isEmpty()) return null

        val roles = selectRoles(kMeans.clusters, kMeans.identityClusters)
        val styleProfile = StyleProfile(style)
        val backgrounds =
            makeBackgroundPair(kMeans.clusters, roles, toneStatistics, styleProfile)
        val meshSources =
            makeMeshSourceColors(kMeans.clusters, roles, toneStatistics, styleProfile)

        val darkScheme = makeScheme(
            roles = roles,
            background = backgrounds.dark,
            meshColors = makeSchemeMeshColors(meshSources, backgrounds.dark, isDark = true),
            isDark = true,
            style = styleProfile,
        )
        val lightScheme = makeScheme(
            roles = roles,
            background = backgrounds.light,
            meshColors = makeSchemeMeshColors(meshSources, backgrounds.light, isDark = false),
            isDark = false,
            style = styleProfile,
        )

        val palette = makePalette(
            roles = roles,
            clusters = presentationClusters(kMeans.clusters, roles.identityClusters),
            backgroundSource = backgrounds.source,
            averages = averages,
        )

        return ColorEngineResult(
            dark = darkScheme,
            light = lightScheme,
            palette = palette,
            style = style,
            sourceImageTone = toneStatistics.tone,
            sourceToneConfidence = toneStatistics.confidence,
            processingTimeMs = (System.nanoTime() - startNanos) / 1_000_000.0,
        )
    }
}

// ---------------------------------------------------------------------------
// Results
// ---------------------------------------------------------------------------

/** One appearance's worth of tokens, ready to hand to Compose. */
data class ColorEngineScheme(
    val background: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val tertiaryText: Color,
    val quaternaryText: Color,
    val accent: Color,
    val surface: Color,
    val border: Color,
    val shadow: Color,
    val tint: Color,
    val destructive: Color,
    val backgroundGradientTop: Color,
    val backgroundGradientBottom: Color,
    val textGradientTop: Color,
    val textGradientBottom: Color,
    val meshColors: List<Color>,
)

/** The raw artwork colours behind the schemes. */
data class ColorEnginePalette(
    val dominant: Color,
    val secondary: Color,
    val accent: Color,
    val dominantHex: String,
    val secondaryHex: String,
    val accentHex: String,
    val averageColor: LabColor,
    val weightedAverageColor: LabColor,
    val harmonies: Map<ColorHarmony, List<LChColor>>,
    val clusters: List<LabColor>,
)

data class ColorEngineResult(
    val dark: ColorEngineScheme,
    val light: ColorEngineScheme,
    val palette: ColorEnginePalette,
    val style: ColorStyle,
    val sourceImageTone: ImageTone,
    val sourceToneConfidence: Float,
    val processingTimeMs: Double,
) {
    /** The scheme matching the artwork's own tone — a dark cover keeps its dark scheme. */
    val native: ColorEngineScheme get() = if (sourceImageTone == ImageTone.DARK) dark else light

    /** The scheme for a given appearance. */
    fun forDarkTheme(isDark: Boolean): ColorEngineScheme = if (isDark) dark else light
}
