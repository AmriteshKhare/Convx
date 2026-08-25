import CoreImage
import Metal
import UIKit
import Vision

// MARK: - Persistent Cache

/// Serializable RGBA float component representations of a `JoiColorScheme`.
struct SchemeComponents: Codable, Sendable {
    /// Background fill color components `[r, g, b, a]`.
    let background: [Double]
    /// Primary high-contrast typography color components `[r, g, b, a]`.
    let primaryText: [Double]
    /// Secondary typography color components `[r, g, b, a]`.
    let secondaryText: [Double]
    /// Tertiary typography color components `[r, g, b, a]`.
    let tertiaryText: [Double]
    /// Quaternary subtle typography color components `[r, g, b, a]`.
    let quaternaryText: [Double]
    /// Primary accent and interactive element color components `[r, g, b, a]`.
    let accent: [Double]
    /// Elevated surface and card container color components `[r, g, b, a]`.
    let surface: [Double]
    /// Subtle divider and border stroke color components `[r, g, b, a]`.
    let border: [Double]
    /// Depth shadow and elevation color components `[r, g, b, a]`.
    let shadow: [Double]
    /// Highlight tint color components `[r, g, b, a]`.
    let tint: [Double]
    /// Destructive action and warning indicator color components `[r, g, b, a]`.
    let destructive: [Double]
    /// Top stop of the background atmospheric gradient `[r, g, b, a]`.
    let backgroundGradientTop: [Double]
    /// Bottom stop of the background atmospheric gradient `[r, g, b, a]`.
    let backgroundGradientBottom: [Double]
    /// Top stop of the typography gradient `[r, g, b, a]`.
    let textGradientTop: [Double]
    /// Bottom stop of the typography gradient `[r, g, b, a]`.
    let textGradientBottom: [Double]
    /// Multi-stop mesh gradient color components `[[r, g, b, a], ...]`.
    let mesh: [[Double]]
}

/// Codable disk-serializable representation of `JoiColorResult`.
struct CodableColorResult: Codable, Sendable {
    /// Dark appearance color scheme components.
    let dark: SchemeComponents
    /// Light appearance color scheme components.
    let light: SchemeComponents
    /// The presentation styling profile applied to this analysis.
    let style: JoiColorStyle
    /// Perceived tone classification of the source image.
    let sourceImageTone: ImageTone
    /// Confidence score of the tone classification in range `0...1`.
    let sourceToneConfidence: Float
    /// Total pipeline execution duration in milliseconds.
    let processingTimeMs: Double
    /// Clustered centroid coordinates stored in CIELAB space `[[L, a, b], ...]`.
    let clusters: [[Double]]
    /// Dominant background color components `[r, g, b, a]`.
    let dominant: [Double]
    /// Secondary supporting color components `[r, g, b, a]`.
    let secondary: [Double]
    /// Semantic accent color components `[r, g, b, a]`.
    let accent: [Double]
    /// Unweighted average CIELAB color `[L, a, b]`.
    let averageLAB: [Double]
    /// Spatially-weighted average CIELAB color `[L, a, b]`.
    let weightedAverageLAB: [Double]

    /// Serializes a runtime `JoiColorResult` instance into a `CodableColorResult` container.
    ///
    /// - Parameter result: The runtime analysis result to convert.
    /// - Returns: A serializable representation suitable for disk storage.
    static func make(from result: JoiColorResult) -> CodableColorResult {
        func scheme(_ value: JoiColorScheme) -> SchemeComponents {
            SchemeComponents(
                background: rgba(value.background),
                primaryText: rgba(value.primaryText),
                secondaryText: rgba(value.secondaryText),
                tertiaryText: rgba(value.tertiaryText),
                quaternaryText: rgba(value.quaternaryText),
                accent: rgba(value.accent),
                surface: rgba(value.surface),
                border: rgba(value.border),
                shadow: rgba(value.shadow),
                tint: rgba(value.tint),
                destructive: rgba(value.destructive),
                backgroundGradientTop: rgba(
                    value.backgroundGradientTop
                ),
                backgroundGradientBottom: rgba(
                    value.backgroundGradientBottom
                ),
                textGradientTop: rgba(value.textGradientTop),
                textGradientBottom: rgba(value.textGradientBottom),
                mesh: value.meshColors.map(rgba)
            )
        }

        return CodableColorResult(
            dark: scheme(result.dark),
            light: scheme(result.light),
            style: result.style,
            sourceImageTone: result.sourceImageTone,
            sourceToneConfidence: result.sourceToneConfidence,
            processingTimeMs: result.processingTimeMs,
            clusters: result.palette.clusters.map {
                [Double($0.l), Double($0.a), Double($0.b)]
            },
            dominant: rgba(result.palette.dominant),
            secondary: rgba(result.palette.secondary),
            accent: rgba(result.palette.accent),
            averageLAB: [
                Double(result.palette.averageColor.l),
                Double(result.palette.averageColor.a),
                Double(result.palette.averageColor.b)
            ],
            weightedAverageLAB: [
                Double(result.palette.weightedAverageColor.l),
                Double(result.palette.weightedAverageColor.a),
                Double(result.palette.weightedAverageColor.b)
            ]
        )
    }

    /// Reconstructs a runtime `JoiColorResult` model with native CoreGraphics and UIKit types.
    ///
    /// - Returns: The reconstituted `JoiColorResult` complete with color schemes, dynamic wrappers, and palettes.
    func result() -> JoiColorResult {
        func scheme(_ value: SchemeComponents) -> JoiColorScheme {
            JoiColorScheme(
                background: cgColor(value.background),
                primaryText: cgColor(value.primaryText),
                secondaryText: cgColor(value.secondaryText),
                tertiaryText: cgColor(value.tertiaryText),
                quaternaryText: cgColor(value.quaternaryText),
                accent: cgColor(value.accent),
                surface: cgColor(value.surface),
                border: cgColor(value.border),
                shadow: cgColor(value.shadow),
                tint: cgColor(value.tint),
                destructive: cgColor(value.destructive),
                backgroundGradientTop: cgColor(
                    value.backgroundGradientTop
                ),
                backgroundGradientBottom: cgColor(
                    value.backgroundGradientBottom
                ),
                textGradientTop: cgColor(value.textGradientTop),
                textGradientBottom: cgColor(value.textGradientBottom),
                meshColors: value.mesh.map(cgColor)
            )
        }

        let darkScheme = scheme(dark)
        let lightScheme = scheme(light)
        let paletteClusters = clusters.compactMap { values -> LABColor? in
            guard values.count >= 3 else {
                return nil
            }
            return LABColor(
                l: CGFloat(values[0]),
                a: CGFloat(values[1]),
                b: CGFloat(values[2])
            )
        }
        let average = labColor(averageLAB)
        let weightedAverage = labColor(weightedAverageLAB)
        let harmonyBase = paletteClusters.first?.toLCh()
            ?? LChColor(l: 50, c: 0, h: 0)
        let palette = JoiColorPalette(
            dominant: cgColor(dominant),
            secondary: cgColor(secondary),
            accent: cgColor(accent),
            dominantHex: hex(cgColor(dominant)),
            secondaryHex: hex(cgColor(secondary)),
            accentHex: hex(cgColor(accent)),
            averageColor: average,
            weightedAverageColor: weightedAverage,
            harmonies: harmonyBase.allHarmonies(),
            clusters: paletteClusters
        )
        let dynamic = JoiDynamicColors(
            dark: darkScheme,
            light: lightScheme
        )
        return JoiColorResult(
            dark: darkScheme,
            light: lightScheme,
            dynamic: dynamic,
            palette: palette,
            style: style,
            sourceImageTone: sourceImageTone,
            sourceToneConfidence: sourceToneConfidence,
            processingTimeMs: processingTimeMs
        )
    }

    /// Helper converting a 3-element Double array into an immutable `LABColor`.
    ///
    /// - Parameter values: Array containing `[L, a, b]` coordinates.
    /// - Returns: Reconstructed `LABColor` or `.zero` if the array is malformed.
    private func labColor(_ values: [Double]) -> LABColor {
        guard values.count >= 3 else {
            return .zero
        }
        return LABColor(
            l: CGFloat(values[0]),
            a: CGFloat(values[1]),
            b: CGFloat(values[2])
        )
    }
}

/// Actor managing the two-tier in-memory and persistent on-disk cache for color analysis results.
actor JoiColorAnalysisCache {
    /// Internal reference-type wrapper holding a `JoiColorResult` inside `NSCache`.
    private final class Entry: NSObject {
        let result: JoiColorResult

        init(result: JoiColorResult) {
            self.result = result
        }
    }

    /// Fast in-memory cache instance.
    private let memory = NSCache<NSString, Entry>()
    /// Shared file manager instance for disk operations.
    private let fileManager = FileManager.default
    /// Base directory URL for versioned disk cache persistence.
    private let directory: URL
    /// Cache eviction time-to-live interval (7 days).
    private let timeToLive: TimeInterval = 7 * 24 * 60 * 60
    /// Maximum count of persisted files allowed on disk before pruning triggers.
    private let maximumFileCount = 300

    /// Initializes the analysis cache, configuring memory limits and preparing the versioned disk cache directory.
    init() {
        memory.countLimit = 60
        let cacheDirectory = fileManager.urls(
            for: .cachesDirectory,
            in: .userDomainMask
        )[0]
        directory = cacheDirectory.appendingPathComponent(
            "JoiColorAnalysis_\(joiColorAnalysisVersion)",
            isDirectory: true
        )
        try? fileManager.createDirectory(
            at: directory,
            withIntermediateDirectories: true
        )
    }

    /// Retrieves a cached color analysis result for the given cache key.
    ///
    /// - Parameter key: The unique identifier for the analysis query.
    /// - Returns: The cached `JoiColorResult` if present and unexpired; otherwise `nil`.
    func result(for key: String) -> JoiColorResult? {
        if let entry = memory.object(forKey: key as NSString) {
            return entry.result
        }

        let url = diskURL(for: key)
        guard
            let attributes = try? fileManager.attributesOfItem(
                atPath: url.path
            ),
            let modificationDate = attributes[.modificationDate] as? Date,
            Date().timeIntervalSince(modificationDate) < timeToLive,
            let data = try? Data(contentsOf: url),
            let codable = try? JSONDecoder().decode(
                CodableColorResult.self,
                from: data
            )
        else {
            return nil
        }

        let result = codable.result()
        memory.setObject(
            Entry(result: result),
            forKey: key as NSString
        )
        return result
    }

    /// Stores a color analysis result in both in-memory and on-disk caches.
    ///
    /// - Parameters:
    ///   - result: The analyzed color result to store.
    ///   - key: The unique cache key identifier.
    func store(_ result: JoiColorResult, for key: String) {
        memory.setObject(
            Entry(result: result),
            forKey: key as NSString
        )

        if let data = try? JSONEncoder().encode(
            CodableColorResult.make(from: result)
        ) {
            try? data.write(to: diskURL(for: key), options: .atomic)
        }
        pruneIfNeeded()
    }

    /// Invalidates and removes cached entries for a given key from both memory and disk.
    ///
    /// - Parameter key: The cache key to invalidate.
    func invalidate(_ key: String) {
        memory.removeObject(forKey: key as NSString)
        try? fileManager.removeItem(at: diskURL(for: key))
    }

    /// Flushes all cached analysis entries from memory and clears the on-disk cache directory.
    func invalidateAll() {
        memory.removeAllObjects()
        try? fileManager.removeItem(at: directory)
        try? fileManager.createDirectory(
            at: directory,
            withIntermediateDirectories: true
        )
    }

    /// Generates a deterministic disk file URL for a given cache key based on its FNV-1a hex hash.
    ///
    /// - Parameter key: The string cache key.
    /// - Returns: The absolute URL on disk for the cached file.
    private func diskURL(for key: String) -> URL {
        directory
            .appendingPathComponent(stableHexHash(key))
            .appendingPathExtension("joicolor")
    }

    /// Prunes expired and excess cache files from disk when the total file count exceeds `maximumFileCount`.
    private func pruneIfNeeded() {
        guard
            let files = try? fileManager.contentsOfDirectory(
                at: directory,
                includingPropertiesForKeys: [.contentModificationDateKey],
                options: .skipsHiddenFiles
            ),
            files.count > maximumFileCount
        else {
            return
        }

        let sorted = files.sorted { first, second in
            let firstDate = (
                try? first.resourceValues(
                    forKeys: [.contentModificationDateKey]
                ).contentModificationDate
            ) ?? .distantPast
            let secondDate = (
                try? second.resourceValues(
                    forKeys: [.contentModificationDateKey]
                ).contentModificationDate
            ) ?? .distantPast
            return firstDate < secondDate
        }

        for file in sorted.prefix(files.count - maximumFileCount) {
            try? fileManager.removeItem(at: file)
        }
    }
}
