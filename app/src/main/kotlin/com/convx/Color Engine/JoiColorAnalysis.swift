import CoreImage
import Metal
import UIKit
import Vision

// MARK: - JoiColorAnalysis

/// The central actor orchestrating the 7-stage deterministic, perceptually-aware artwork color analysis pipeline.
actor JoiColorAnalysis {
    /// Shared singleton instance.
    static let shared = JoiColorAnalysis()

    /// Persistent and in-memory cache manager.
    private let cache = JoiColorAnalysisCache()
    /// Shared CoreImage rendering context.
    let context: CIContext
    /// Indicates whether Metal GPU rendering acceleration is active.
    private let usesMetalRenderer: Bool
    /// Global debug reporting flag.
    private var isDebugEnabled = false

    /// Initializes the color analysis engine, configuring Metal GPU rendering when available and warming lookup tables.
    init() {
        let sRGB = CGColorSpace(name: CGColorSpace.sRGB)
            ?? CGColorSpaceCreateDeviceRGB()
        let options: [CIContextOption: Any] = [
            .workingColorSpace: sRGB,
            .outputColorSpace: sRGB,
            .cacheIntermediates: false
        ]
        if let metalDevice = MTLCreateSystemDefaultDevice() {
            context = CIContext(
                mtlDevice: metalDevice,
                options: options
            )
            usesMetalRenderer = true
        } else {
            context = CIContext(options: options)
            usesMetalRenderer = false
        }
        _ = sRGBLinear8BitLUT.count
        _ = labTransformLUT.count
    }

    /// Configures the global debug logging flag.
    ///
    /// - Parameter enabled: When `true`, enables detailed diagnostic performance output for all analysis runs.
    func setDebugEnabled(_ enabled: Bool) {
        isDebugEnabled = enabled
    }

    /// Executes complete color analysis on an image, checking cache before executing the pipeline.
    ///
    /// - Parameters:
    ///   - image: The source `UIImage` to analyze.
    ///   - cacheKey: A unique cache key identifier.
    ///   - visionMode: Configured Vision framework analysis fidelity. Defaults to `.balanced`.
    ///   - style: The presentation styling profile. Defaults to `.balanced`.
    ///   - debug: When `true`, logs performance metrics and role breakdown to console.
    /// - Returns: A complete `JoiColorResult` model.
    /// - Throws: `JoiError` if any pipeline stage fails.
    func analyze(
        image: UIImage,
        cacheKey: String,
        visionMode: JoiColorVisionMode = .balanced,
        style: JoiColorStyle = .balanced,
        debug: Bool = false
    ) async throws -> JoiColorResult {
        let effectiveCacheKey = "\(cacheKey)"
            + "|vision=\(visionMode.rawValue)"
            + "|style=\(style.rawValue)"
        let shouldDebug = debug || isDebugEnabled

        if let cached = await cache.result(for: effectiveCacheKey) {
            if shouldDebug {
                var diagnostics = AnalysisDiagnostics()
                diagnostics.cacheHit = true
                diagnostics.visionMode = visionMode
                diagnostics.style = style
                diagnostics.renderer = usesMetalRenderer
                    ? "Metal + CPU"
                    : "CPU"
                JoiColorAnalysisDebugReport(
                    cacheKey: cacheKey,
                    diagnostics: diagnostics,
                    result: cached,
                    totalMs: 0
                ).printLog()
            }
            return cached
        }

        let output = try await pipeline(
            image: image,
            visionMode: visionMode,
            style: style
        )
        await cache.store(output.result, for: effectiveCacheKey)

        if shouldDebug {
            JoiColorAnalysisDebugReport(
                cacheKey: cacheKey,
                diagnostics: output.diagnostics,
                result: output.result,
                totalMs: output.result.processingTimeMs
            ).printLog()
        }
        return output.result
    }

    /// Invalidates all cached result permutations (across all vision modes and styles) for a specific cache key.
    ///
    /// - Parameter cacheKey: The base cache key identifier.
    func invalidate(cacheKey: String) async {
        for mode in [
            JoiColorVisionMode.disabled,
            .balanced,
            .detailed
        ] {
            for style in JoiColorStyle.allCases {
                await cache.invalidate(
                    "\(cacheKey)"
                        + "|vision=\(mode.rawValue)"
                        + "|style=\(style.rawValue)"
                )
            }
        }
    }

    /// Flushes all cached results from memory and disk storage.
    func invalidateAll() async {
        await cache.invalidateAll()
    }

    /// Executes the 7-stage deterministic color analysis pipeline:
    /// 1. Orientation normalization and visual detail assessment
    /// 2. Vision saliency, face, and text detection
    /// 3. Spatial, text, and skin tone weight map construction
    /// 4. Lightness tone statistics and average color calculations
    /// 5. Quantized deterministic K-Means clustering and medoid selection
    /// 6. Semantic role selection, WCAG text hierarchy, and theme scheme construction
    /// 7. Final palette packaging and dynamic UIColors assembly
    ///
    /// - Parameters:
    ///   - image: The input image.
    ///   - visionMode: The Vision analysis mode.
    ///   - style: The presentation styling profile.
    /// - Returns: A tuple containing the `JoiColorResult` and collected `AnalysisDiagnostics`.
    /// - Throws: `JoiError` if any stage encounters unrecoverable failure.
    private func pipeline(
        image: UIImage,
        visionMode: JoiColorVisionMode,
        style: JoiColorStyle
    ) async throws -> (
        result: JoiColorResult,
        diagnostics: AnalysisDiagnostics
    ) {
        let totalStart = DispatchTime.now().uptimeNanoseconds
        var diagnostics = AnalysisDiagnostics()
        diagnostics.visionMode = visionMode
        diagnostics.style = style
        diagnostics.renderer = usesMetalRenderer
            ? "Metal + CPU"
            : "CPU"

        let normalizationStart = DispatchTime.now().uptimeNanoseconds
        guard let source = normalizedSourceImage(image) else {
            throw JoiError.invalidImage
        }
        diagnostics.sourceSize = source.extent.size

        let assessment = try assessDetail(
            source,
            visionMode: visionMode
        )
        diagnostics.detailAssessment = assessment
        diagnostics.requestedClusters = assessment.requestedClusterCount
        let sampleDimensions = sampleDimensions(
            for: source.extent.size,
            maximumDimension: assessment.thumbnailSize
        )
        diagnostics.sampleWidth = sampleDimensions.width
        diagnostics.sampleHeight = sampleDimensions.height

        guard let rendered = render(
            source,
            width: sampleDimensions.width,
            height: sampleDimensions.height
        ) else {
            throw JoiError.normalizationFailed
        }
        let plane = try makePixelPlane(
            rendered,
            sourceSize: source.extent.size
        )
        diagnostics.pixelCount = plane.count
        diagnostics.timings.append(
            ("1. Normalize + sample", elapsedMS(since: normalizationStart))
        )

        let visionStart = DispatchTime.now().uptimeNanoseconds
        let vision = await runVision(
            renderedImage: rendered,
            mode: visionMode
        )
        diagnostics.textRegionCount = vision?.textRegions.count ?? 0
        diagnostics.faceRegionCount = vision?.faceRegions.count ?? 0
        diagnostics.salientRegionCount = vision?.salientRegions.count ?? 0
        diagnostics.timings.append(
            (
                visionMode == .disabled
                    ? "2. Vision (disabled)"
                    : "2. Vision (\(visionMode.rawValue))",
                elapsedMS(since: visionStart)
            )
        )

        let weightsStart = DispatchTime.now().uptimeNanoseconds
        let weightMap = makeWeightMap(
            plane: plane,
            vision: vision,
            visionMode: visionMode
        )
        diagnostics.timings.append(
            ("3. Weight map", elapsedMS(since: weightsStart))
        )

        let statisticsStart = DispatchTime.now().uptimeNanoseconds
        let toneStatistics = analyzeTone(
            plane: plane
        )
        diagnostics.toneStatistics = toneStatistics
        let averages = calculateAverages(
            plane: plane,
            weights: weightMap.background
        )
        diagnostics.timings.append(
            ("4. Tone + statistics", elapsedMS(since: statisticsStart))
        )

        let clusteringStart = DispatchTime.now().uptimeNanoseconds
        let kMeans = deterministicKMeans(
            plane: plane,
            weightMap: weightMap,
            clusterCount: assessment.requestedClusterCount
        )
        guard !kMeans.clusters.isEmpty else {
            throw JoiError.clusteringFailed
        }
        diagnostics.actualClusters = kMeans.clusters.count
        diagnostics.kMeansIterations = kMeans.iterations
        diagnostics.quantizedSampleCount = kMeans.quantizedSampleCount
        diagnostics.fittingSampleCount = kMeans.fittingSampleCount
        diagnostics.identityClusterCount = kMeans.identityClusters.count
        diagnostics.timings.append(
            (
                "5. Quantized deterministic K-Means",
                elapsedMS(since: clusteringStart)
            )
        )

        let rolesStart = DispatchTime.now().uptimeNanoseconds
        let roles = selectRoles(
            clusters: kMeans.clusters,
            identityClusters: kMeans.identityClusters
        )
        diagnostics.backgroundRole = roles.background
        diagnostics.foregroundRole = roles.foreground
        diagnostics.accentRole = roles.accent

        if roles.accent == nil
            && !roles.identityClusters.contains(where: { cluster in
                hasIdentityEvidence(cluster)
            }) {
            diagnostics.warnings.append(
                "Neutral artwork: synthetic hue forcing disabled."
            )
        }
        if toneStatistics.confidence < 0.22 {
            diagnostics.warnings.append(
                "Source tone is near the dark/light decision boundary."
            )
        }
        if roles.foreground == nil {
            diagnostics.warnings.append(
                "No independent foreground role passed the evidence gate."
            )
        }

        let styleProfile = JoiColorStyleProfile(style: style)
        let backgrounds = makeBackgroundPair(
            clusters: kMeans.clusters,
            roles: roles,
            statistics: toneStatistics,
            style: styleProfile
        )
        let meshSources = makeMeshSourceColors(
            clusters: kMeans.clusters,
            roles: roles,
            statistics: toneStatistics,
            style: styleProfile
        )
        let darkMeshColors = makeSchemeMeshColors(
            sources: meshSources,
            background: backgrounds.dark,
            isDark: true
        )
        let lightMeshColors = makeSchemeMeshColors(
            sources: meshSources,
            background: backgrounds.light,
            isDark: false
        )
        let darkScheme = makeScheme(
            roles: roles,
            background: backgrounds.dark,
            meshColors: darkMeshColors,
            isDark: true,
            style: styleProfile
        )
        let lightScheme = makeScheme(
            roles: roles,
            background: backgrounds.light,
            meshColors: lightMeshColors,
            isDark: false,
            style: styleProfile
        )
        diagnostics.timings.append(
            ("6. Semantic role assignment", elapsedMS(since: rolesStart))
        )

        let resultStart = DispatchTime.now().uptimeNanoseconds
        let palette = makePalette(
            roles: roles,
            clusters: presentationClusters(
                atmosphere: kMeans.clusters,
                identities: roles.identityClusters
            ),
            backgroundSource: backgrounds.source,
            averages: averages
        )
        let dynamic = JoiDynamicColors(
            dark: darkScheme,
            light: lightScheme
        )
        diagnostics.timings.append(
            ("7. Palette + dynamic colors", elapsedMS(since: resultStart))
        )

        let total = elapsedMS(since: totalStart)
        let performanceBudget: Double = visionMode == .disabled ? 100 : 200
        if total > performanceBudget {
            diagnostics.warnings.append(
                "Performance budget exceeded: "
                    + "\(decimal(total, places: 1)) ms > "
                    + "\(Int(performanceBudget)) ms."
            )
        }
        let result = JoiColorResult(
            dark: darkScheme,
            light: lightScheme,
            dynamic: dynamic,
            palette: palette,
            style: style,
            sourceImageTone: toneStatistics.tone,
            sourceToneConfidence: toneStatistics.confidence,
            processingTimeMs: total
        )
        return (result, diagnostics)
    }
}



