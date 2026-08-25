import CoreImage
import Metal
import UIKit
import Vision

// MARK: - Weighting and Statistics

extension JoiColorAnalysis {
    /// Generates composite spatial, text, and saliency weight maps across all sampled pixels in the plane.
    ///
    /// Combines radial center bias, corner margin de-emphasis (0.72x), Vision attention heatmaps, local text edge contrast,
    /// and skin tone attenuation in detected human face regions.
    ///
    /// - Parameters:
    ///   - plane: The extracted CIELAB pixel plane.
    ///   - vision: Vision feature observations, if available.
    ///   - visionMode: Configured Vision framework analysis mode.
    /// - Returns: A complete `WeightMap` with aligned float weight arrays.
    func makeWeightMap(
        plane: PixelPlane,
        vision: VisionData?,
        visionMode: JoiColorVisionMode
    ) -> WeightMap {
        let count = plane.count
        var background = [Float](repeating: 0, count: count)
        let colorInfluence = [Float](repeating: 1, count: count)
        var textMembership = [Float](repeating: 0, count: count)
        let rawSaliency = vision?.saliencyMap.count == count
            ? vision?.saliencyMap
            : nil
        let saliencyStrength: Float
        switch visionMode {
        case .disabled:
            saliencyStrength = 0
        case .balanced:
            saliencyStrength = 0.20
        case .detailed:
            saliencyStrength = 0.28
        }
        // Baseline distribution centers around 0.5 so non-salient authentic artwork colors are never eliminated.
        let saliency = rawSaliency?.map { value in
            0.5 + (value - 0.5) * saliencyStrength
        }

        let centerX = Float(plane.width - 1) / 2
        let centerY = Float(plane.height - 1) / 2
        let maximumDistance = max(
            1,
            sqrt(centerX * centerX + centerY * centerY)
        )

        for y in 0..<plane.height {
            for x in 0..<plane.width {
                let index = y * plane.width + x
                let alpha = plane.alpha[index]
                guard alpha > 0 else {
                    continue
                }

                let xDistance = Float(x) - centerX
                let yDistance = Float(y) - centerY
                let distance = sqrt(
                    xDistance * xDistance + yDistance * yDistance
                )
                let centerWeight = 1 + 0.22 * (1 - distance / maximumDistance)
                let saliencyValue = saliency?[index] ?? 0.5
                let saliencyWeight = 0.82 + 0.43 * saliencyValue
                let cornerWeight = isCorner(
                    x: x,
                    y: y,
                    width: plane.width,
                    height: plane.height
                ) ? Float(0.72) : Float(1)

                background[index] = alpha
                    * centerWeight
                    * saliencyWeight
                    * cornerWeight
            }
        }

        if let vision {
            let textEvidenceFloor: Float = visionMode == .detailed
                ? 0.16
                : 0.12
            let textEdgeScale: Float = visionMode == .detailed
                ? 0.74
                : 0.68
            for region in vision.textRegions {
                apply(
                    region: region,
                    width: plane.width,
                    height: plane.height
                ) { index in
                    let edgeSupport = textEdgeSupport(
                        at: index,
                        plane: plane
                    )
                    textMembership[index] = max(
                        textMembership[index],
                        textEvidenceFloor
                            + edgeSupport * textEdgeScale
                    )
                }
            }
            let skinBackgroundPenalty: Float = visionMode == .detailed
                ? 0.08
                : 0.06
            for region in vision.faceRegions {
                // De-emphasizes skin tones from dominant background selection while preserving them for identity and mesh roles.
                apply(
                    region: region,
                    width: plane.width,
                    height: plane.height
                ) { index in
                    let skinInfluence = skinToneInfluence(
                        plane.points[index]
                    )
                    background[index] *= 1
                        - skinInfluence * skinBackgroundPenalty
                }
            }
        }

        if background.reduce(0, +) <= 0 {
            background = plane.alpha
        }

        return WeightMap(
            background: background,
            colorInfluence: colorInfluence,
            textMembership: textMembership,
            saliency: saliency
                ?? [Float](repeating: 0.5, count: count)
        )
    }

    /// Evaluates the probability that a CIELAB point belongs to human skin tone based on lightness and opponent red/yellow coordinates.
    ///
    /// - Parameter point: The CIELAB point.
    /// - Returns: Skin tone affinity value in range `0...1`.
    func skinToneInfluence(_ point: LABPoint) -> Float {
        let chroma = point.chroma
        let lightnessSupport = smoothStep(
            edge0: 8,
            edge1: 20,
            value: point.l
        ) * (
            1 - smoothStep(
                edge0: 94,
                edge1: 100,
                value: point.l
            )
        )
        let redSupport = smoothStep(
            edge0: 1,
            edge1: 8,
            value: point.a
        ) * (
            1 - smoothStep(
                edge0: 34,
                edge1: 48,
                value: point.a
            )
        )
        let yellowSupport = smoothStep(
            edge0: 2,
            edge1: 9,
            value: point.b
        ) * (
            1 - smoothStep(
                edge0: 42,
                edge1: 56,
                value: point.b
            )
        )
        let chromaSupport = smoothStep(
            edge0: 6,
            edge1: 12,
            value: chroma
        ) * (
            1 - smoothStep(
                edge0: 54,
                edge1: 70,
                value: chroma
            )
        )
        return min(
            min(lightnessSupport, redSupport),
            min(yellowSupport, chromaSupport)
        )
    }

    /// Computes high-frequency spatial edge contrast at a specific pixel index to detect crisp text boundaries.
    ///
    /// - Parameters:
    ///   - index: Linear index in the pixel plane.
    ///   - plane: The pixel plane.
    /// - Returns: Normalized edge contrast score `0...1`.
    func textEdgeSupport(
        at index: Int,
        plane: PixelPlane
    ) -> Float {
        let x = index % plane.width
        let y = index / plane.width
        let point = plane.points[index]
        var distance: Float = 0
        var count: Float = 0

        if x > 0 {
            distance += sqrt(
                point.squaredDistance(to: plane.points[index - 1])
            )
            count += 1
        }
        if x + 1 < plane.width {
            distance += sqrt(
                point.squaredDistance(to: plane.points[index + 1])
            )
            count += 1
        }
        if y > 0 {
            distance += sqrt(
                point.squaredDistance(to: plane.points[index - plane.width])
            )
            count += 1
        }
        if y + 1 < plane.height {
            distance += sqrt(
                point.squaredDistance(to: plane.points[index + plane.width])
            )
            count += 1
        }

        guard count > 0 else { return 0 }
        return smoothStep(
            edge0: 4,
            edge1: 28,
            value: distance / count
        )
    }

    /// Performs comprehensive luminance and chromatic analysis to classify source image tone (.dark or .light).
    ///
    /// Evaluates a 201-bin fixed lightness histogram (0.5 L resolution) to compute weighted mean, median, 65th percentile,
    /// and dark/light/chromatic coverage shares.
    ///
    /// - Parameter plane: The pixel plane.
    /// - Returns: A `ToneStatistics` model.
    func analyzeTone(
        plane: PixelPlane
    ) -> ToneStatistics {
        var totalWeight: Float = 0
        var weightedMean: Float = 0
        var darkWeight: Float = 0
        var lightWeight: Float = 0
        var chromaticWeight: Float = 0
        var strongChromaticWeight: Float = 0
        let histogramResolution: Float = 0.5
        let histogramBinCount = 201
        var lightnessHistogram = [Float](
            repeating: 0,
            count: histogramBinCount
        )

        for index in 0..<plane.count {
            let weight = plane.alpha[index]
            guard weight > 0 else {
                continue
            }
            let point = plane.points[index]
            totalWeight += weight
            weightedMean += point.l * weight
            let bin = clamp(
                Int((point.l / histogramResolution).rounded()),
                minimum: 0,
                maximum: histogramBinCount - 1
            )
            lightnessHistogram[bin] += weight

            if point.l < 43 {
                darkWeight += weight
            }
            if point.l > 61 {
                lightWeight += weight
            }
            if point.chroma >= 12 {
                chromaticWeight += weight
            }
            if point.chroma >= 24 {
                strongChromaticWeight += weight
            }
        }

        totalWeight = max(totalWeight, Float.ulpOfOne)
        weightedMean /= totalWeight
        let median = histogramPercentile(
            lightnessHistogram,
            totalWeight: totalWeight,
            percentile: 0.5,
            resolution: histogramResolution
        )
        let percentile65 = histogramPercentile(
            lightnessHistogram,
            totalWeight: totalWeight,
            percentile: 0.65,
            resolution: histogramResolution
        )
        let darkCoverage = darkWeight / totalWeight
        let lightCoverage = lightWeight / totalWeight
        let chromaticCoverage = chromaticWeight / totalWeight
        let strongChromaticCoverage = strongChromaticWeight / totalWeight

        // Chroma is deliberately excluded from tone classification so vivid dark artwork is not misclassified as light.
        let coverageBias = (lightCoverage - darkCoverage) * 9
        let perceptualLightness = 0.42 * weightedMean
            + 0.38 * median
            + 0.20 * percentile65
            + coverageBias
        let threshold: Float = 50
        let tone: ImageTone = perceptualLightness < threshold ? .dark : .light
        let confidence = clamp(
            abs(perceptualLightness - threshold) / 26
                + abs(lightCoverage - darkCoverage) * 0.28,
            minimum: 0,
            maximum: 1
        )
        let chromaticConfidence = clamp(
            chromaticCoverage * 0.68
                + strongChromaticCoverage * 0.72,
            minimum: 0,
            maximum: 1
        )

        return ToneStatistics(
            tone: tone,
            confidence: confidence,
            weightedMean: weightedMean,
            weightedMedian: median,
            percentile65: percentile65,
            darkCoverage: darkCoverage,
            lightCoverage: lightCoverage,
            chromaticCoverage: chromaticCoverage,
            strongChromaticCoverage: strongChromaticCoverage,
            chromaticConfidence: chromaticConfidence
        )
    }

    /// Evaluates a cumulative percentile threshold from the lightness histogram without requiring a general-purpose sort.
    ///
    /// - Parameters:
    ///   - histogram: Lightness frequency histogram bins.
    ///   - totalWeight: Cumulative weight across all bins.
    ///   - percentile: Target percentile fraction `0...1` (e.g. 0.5 for median).
    ///   - resolution: Lightness width per bin in CIELAB L units.
    /// - Returns: Interpolated lightness value at the target percentile.
    func histogramPercentile(
        _ histogram: [Float],
        totalWeight: Float,
        percentile: Float,
        resolution: Float
    ) -> Float {
        let target = totalWeight * percentile
        var accumulated: Float = 0
        for index in histogram.indices {
            accumulated += histogram[index]
            if accumulated >= target {
                return Float(index) * resolution
            }
        }
        return Float(histogram.count - 1) * resolution
    }

    /// Computes unweighted arithmetic average and spatially-weighted average CIELAB colors of the image.
    ///
    /// - Parameters:
    ///   - plane: The extracted pixel plane.
    ///   - weights: Spatial weight array aligned with pixel indices.
    /// - Returns: Tuple containing `(average: LABColor, weighted: LABColor)`.
    func calculateAverages(
        plane: PixelPlane,
        weights: [Float]
    ) -> (average: LABColor, weighted: LABColor) {
        var totalAlpha: Float = 0
        var totalWeight: Float = 0
        var raw = LABPoint(l: 0, a: 0, b: 0)
        var weighted = LABPoint(l: 0, a: 0, b: 0)

        for index in 0..<plane.count {
            let alpha = plane.alpha[index]
            let weight = weights[index]
            let point = plane.points[index]

            raw.l += point.l * alpha
            raw.a += point.a * alpha
            raw.b += point.b * alpha
            totalAlpha += alpha

            weighted.l += point.l * weight
            weighted.a += point.a * weight
            weighted.b += point.b * weight
            totalWeight += weight
        }

        if totalAlpha > 0 {
            raw.l /= totalAlpha
            raw.a /= totalAlpha
            raw.b /= totalAlpha
        }
        if totalWeight > 0 {
            weighted.l /= totalWeight
            weighted.a /= totalWeight
            weighted.b /= totalWeight
        } else {
            weighted = raw
        }
        return (raw.color, weighted.color)
    }
}



