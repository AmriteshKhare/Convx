import CoreImage
import Metal
import UIKit
import Vision

// MARK: - Internal Pipeline Models

/// Defines parameter tuning scalers for saturation and lightness curves mapped from a `JoiColorStyle`.
struct JoiColorStyleProfile: Sendable {
    /// Multiplier applied to background color chroma.
    let backgroundChroma: CGFloat
    /// Multiplier applied to typography color chroma.
    let textChroma: CGFloat
    /// Multiplier applied to interactive accent color chroma.
    let accentChroma: CGFloat
    /// Multiplier applied to mesh gradient stops chroma.
    let meshChroma: CGFloat
    /// Multiplier applied to elevated surface container chroma.
    let surfaceChroma: CGFloat
    /// Lightness adjustment offset applied to high-contrast text.
    let textLightnessInset: CGFloat

    /// Initializes a style profile matching the given presentation style.
    ///
    /// - Parameter style: The target `JoiColorStyle` (`.balanced`, `.vibrant`, or `.pastel`).
    init(style: JoiColorStyle) {
        switch style {
        case .balanced:
            backgroundChroma = 1.08
            textChroma = 1
            accentChroma = 1
            meshChroma = 1.07
            surfaceChroma = 1
            textLightnessInset = 2

        case .vibrant:
            backgroundChroma = 1.12
            textChroma = 1.28
            accentChroma = 1.18
            meshChroma = 1.16
            surfaceChroma = 1.08
            textLightnessInset = 6

        case .pastel:
            backgroundChroma = 0.68
            textChroma = 0.72
            accentChroma = 0.76
            meshChroma = 0.7
            surfaceChroma = 0.78
            textLightnessInset = 0
        }
    }
}

/// A lightweight 32-bit floating point vector representation of a CIELAB coordinate.
struct LABPoint: Sendable {
    /// Lightness coordinate in range `0...100`.
    var l: Float
    /// Opponent red-green coordinate.
    var a: Float
    /// Opponent yellow-blue coordinate.
    var b: Float

    /// Euclidean distance from the neutral gray axis (chroma).
    var chroma: Float {
        sqrt(a * a + b * b)
    }

    /// Converts this 32-bit Float vector into a native CoreGraphics `LABColor`.
    var color: LABColor {
        LABColor(l: CGFloat(l), a: CGFloat(a), b: CGFloat(b))
    }

    /// Computes squared Euclidean distance in CIELAB coordinates to another point.
    ///
    /// - Parameter other: Target point.
    /// - Returns: Squared distance.
    func squaredDistance(to other: LABPoint) -> Float {
        let dL = l - other.l
        let dA = a - other.a
        let dB = b - other.b
        return dL * dL + dA * dA + dB * dB
    }
}

/// An extracted grid of pixels containing CIELAB coordinates, alpha channels, and a deterministic fingerprint.
struct PixelPlane: Sendable {
    /// Sampled grid width in pixels.
    let width: Int
    /// Sampled grid height in pixels.
    let height: Int
    /// Original unscaled image width.
    let sourceWidth: Int
    /// Original unscaled image height.
    let sourceHeight: Int
    /// Array of CIELAB points corresponding to each sampled pixel index.
    let points: [LABPoint]
    /// Normalized alpha channel opacity values `0...1`.
    let alpha: [Float]
    /// 64-bit FNV-1a hash fingerprint used for deterministic PRNG seeding.
    let fingerprint: UInt64

    /// Total count of pixel samples in the plane.
    var count: Int {
        points.count
    }
}

/// Wraps rendered 8-bit RGBA pixel byte buffers and associated CoreGraphics metadata.
struct RenderedRGBA: Sendable {
    /// Pixel width of the rendered buffer.
    let width: Int
    /// Pixel height of the rendered buffer.
    let height: Int
    /// Raw RGBA byte array.
    let bytes: [UInt8]
    /// Stride bytes per image row.
    let bytesPerRow: Int
    /// Underlying CGImage backing.
    let cgImage: CGImage
}

/// Results of visual complexity and detail assessment used to configure downsample resolution and cluster counts.
struct DetailAssessment: Sendable {
    /// Luminance variance across the preliminary thumbnail.
    let variance: Float
    /// Sobel-style high-frequency edge energy.
    let edgeEnergy: Float
    /// Chromatic dispersion across color channels.
    let chromaSpread: Float
    /// Composite visual detail score in range `0...1`.
    let score: Float
    /// Recommended downsample thumbnail dimension in pixels.
    let thumbnailSize: Int
    /// Target number of clusters requested for K-Means fitting.
    let requestedClusterCount: Int
}

/// Output data extracted from Apple Vision framework processing.
struct VisionData: Sendable {
    /// Normalized 2D saliency heatmap values `0...1` matching pixel plane indices.
    let saliencyMap: [Float]
    /// Bounding boxes of detected text regions.
    let textRegions: [CGRect]
    /// Bounding boxes of detected human faces.
    let faceRegions: [CGRect]
    /// Bounding boxes of detected salient objects.
    let salientRegions: [CGRect]
}

/// Spatial and semantic weight arrays aligned with pixel plane samples.
struct WeightMap: Sendable {
    /// Background role affinity weights.
    let background: [Float]
    /// Color influence weights.
    let colorInfluence: [Float]
    /// Text membership probability weights based on detected text bounding boxes and local edge contrast.
    let textMembership: [Float]
    /// Saliency confidence weights derived from Vision attention maps.
    let saliency: [Float]
}

/// An aggregated quantized color sample representing a discrete spatial-color bin.
struct QuantizedColorSample: Sendable {
    /// Mathematical average CIELAB coordinate of the quantized bin.
    let point: LABPoint
    /// Authentic observed pixel medoid point closest to maximum semantic evidence within the bin.
    let representativePoint: LABPoint
    /// Weight applied during K-Means centroid fitting.
    let centroidWeight: Float
    /// Accumulated spatial background weight.
    let backgroundWeight: Float
    /// Total visible pixel population count in this bin.
    let population: Float
    /// Population overlapping detected text regions.
    let textPopulation: Float
    /// Population overlapping salient regions.
    let saliencyPopulation: Float
    /// Raw integer sample count.
    let count: Int
    /// Deterministic stable identifier.
    let stableID: Int
}

/// Mutable accumulator used during spatial-color binning to aggregate coordinates and identify representative medoids.
struct QuantizedColorAccumulator {
    var sumL: Float = 0
    var sumA: Float = 0
    var sumB: Float = 0
    var centroidWeight: Float = 0
    var backgroundWeight: Float = 0
    var population: Float = 0
    var textPopulation: Float = 0
    var saliencyPopulation: Float = 0
    var count = 0
    var representativePoint = LABPoint(l: 0, a: 0, b: 0)
    var representativeScore = -Float.greatestFiniteMagnitude
    var representativeIndex = Int.max

    /// Adds a sampled pixel to the accumulator, updating centroid sums and selecting the best authentic medoid.
    ///
    /// - Parameters:
    ///   - point: Pixel CIELAB coordinate.
    ///   - alpha: Effective opacity weight.
    ///   - background: Spatial background weight.
    ///   - textMembership: Text affinity score.
    ///   - saliency: Vision saliency value.
    ///   - sampleIndex: Linear index in the pixel plane.
    mutating func add(
        point: LABPoint,
        alpha: Float,
        background: Float,
        textMembership: Float,
        saliency: Float,
        sampleIndex: Int
    ) {
        let chromaSupport = smoothStep(
            edge0: 8,
            edge1: 42,
            value: point.chroma
        )
        let identitySupport = 1
            + chromaSupport * 0.28
            + textMembership * 0.32
        let clusteringWeight = max(background, alpha * 0.08)
            * identitySupport

        sumL += point.l * clusteringWeight
        sumA += point.a * clusteringWeight
        sumB += point.b * clusteringWeight
        centroidWeight += clusteringWeight
        backgroundWeight += background
        population += alpha
        textPopulation += alpha * textMembership
        saliencyPopulation += alpha * saliency
        count += 1

        let evidenceScore = alpha * (
            1
                + chromaSupport * 0.24
                + textMembership * 0.52
                + saliency * 0.18
        )
        if evidenceScore > representativeScore + 0.000_001
            || (
                abs(evidenceScore - representativeScore) <= 0.000_001
                    && sampleIndex < representativeIndex
            ) {
            representativePoint = point
            representativeScore = evidenceScore
            representativeIndex = sampleIndex
        }
    }
}

/// Luminance and chromatic coverage statistics used to classify image tone.
struct ToneStatistics: Sendable {
    /// Perceptual tone classification (.dark or .light).
    let tone: ImageTone
    /// Confidence score of the classification `0...1`.
    let confidence: Float
    /// Weighted mean lightness.
    let weightedMean: Float
    /// Weighted median lightness.
    let weightedMedian: Float
    /// 65th percentile lightness from the cumulative histogram.
    let percentile65: Float
    /// Proportion of image covered by dark pixels (L < 43).
    let darkCoverage: Float
    /// Proportion of image covered by light pixels (L > 61).
    let lightCoverage: Float
    /// Proportion of image covered by chromatic pixels (Chroma >= 12).
    let chromaticCoverage: Float
    /// Proportion of image covered by strongly chromatic pixels (Chroma >= 24).
    let strongChromaticCoverage: Float
    /// Confidence score in the chromatic presence of the image.
    let chromaticConfidence: Float
}

/// A materialized color cluster containing CIELAB coordinates, demographic shares, and semantic affinities.
struct Cluster: Sendable {
    /// Authentic representative CIELAB point.
    var point: LABPoint
    /// Proportional share of the spatial background weight.
    var weightedShare: Float
    /// Proportional share of the total visible pixel population.
    var populationShare: Float
    /// Affinity ratio with detected typography regions.
    var textAffinity: Float
    /// Mean saliency score across the cluster population.
    var meanSaliency: Float
    /// Number of raw pixels assigned to this cluster.
    var count: Int
    /// Deterministic stable identifier.
    var stableID: Int

    /// Chroma distance from the neutral axis.
    var chroma: Float {
        point.chroma
    }

    /// CoreGraphics `LABColor` representation.
    var color: LABColor {
        point.color
    }
}

/// Output package produced by K-Means clustering.
struct KMeansOutput: Sendable {
    /// Converged atmospheric background clusters.
    let clusters: [Cluster]
    /// Preserved fine-grained semantic identity clusters.
    let identityClusters: [Cluster]
    /// Iteration count until convergence.
    let iterations: Int
    /// Total quantized samples processed.
    let quantizedSampleCount: Int
    /// Number of representative samples used during fitting.
    let fittingSampleCount: Int
}

/// Selected semantic color roles assigned from clustered artwork evidence.
struct RoleSelection: Sendable {
    /// Primary dominant background cluster.
    let background: Cluster
    /// Secondary supporting color cluster.
    let secondary: Cluster
    /// Foreground typography color cluster, or `nil` if synthesized neutrals are preferred.
    let foreground: Cluster?
    /// High-contrast interactive accent color cluster, or `nil` if artwork is monochromatic.
    let accent: Cluster?
    /// All discovered semantic identity clusters.
    let identityClusters: [Cluster]
}

/// Paired background colors in CIELCh space across visual appearances.
struct BackgroundPair: Sendable {
    /// Base background source color.
    let source: LChColor
    /// Adjusted background color for dark appearance themes.
    let dark: LChColor
    /// Adjusted background color for light appearance themes.
    let light: LChColor
}

/// A group of clusters belonging to the same hue sector (within ±24° angular span) for mesh gradient synthesis.
struct MeshHueFamily: Sendable {
    /// Primary representative cluster of the hue family.
    var representative: Cluster
    /// Member clusters within this hue sector.
    var members: [Cluster]
    /// Cumulative spatial background support share.
    var weightedSupport: Float
    /// Cumulative pixel population support share.
    var populationSupport: Float
}

