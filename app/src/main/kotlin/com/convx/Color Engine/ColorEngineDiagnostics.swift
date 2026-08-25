import CoreImage
import Metal
import UIKit
import Vision

// MARK: - Diagnostics Models

/// Collects runtime telemetry, step execution timings, and algorithmic metrics throughout a color analysis pass.
struct AnalysisDiagnostics: Sendable {
    /// Step-by-step elapsed timings `[(Step Name, Milliseconds)]`.
    var timings: [(String, Double)] = []
    /// Indicates whether the analysis was fulfilled directly from memory/disk cache.
    var cacheHit = false
    /// Native pixel dimensions of the source image.
    var sourceSize = CGSize.zero
    /// Downsampled width used for pixel plane extraction.
    var sampleWidth = 0
    /// Downsampled height used for pixel plane extraction.
    var sampleHeight = 0
    /// Total number of active pixels processed in the plane.
    var pixelCount = 0
    /// Number of clusters requested based on detail assessment.
    var requestedClusters = 0
    /// Final count of merged clusters produced by K-Means.
    var actualClusters = 0
    /// Number of iterations executed during K-Means fitting.
    var kMeansIterations = 0
    /// Number of quantized samples generated during spatial-color binning.
    var quantizedSampleCount = 0
    /// Number of representative samples passed to K-Means fitting.
    var fittingSampleCount = 0
    /// Number of distinct semantic identity candidate clusters discovered.
    var identityClusterCount = 0
    /// Detail and complexity metrics evaluated for the image.
    var detailAssessment: DetailAssessment?
    /// Vision analysis mode configured for this run.
    var visionMode = JoiColorVisionMode.disabled
    /// Presentation style profile applied to the result.
    var style = JoiColorStyle.balanced
    /// Active rendering backend description (e.g. "Metal + CPU" or "CPU").
    var renderer = "CPU"
    /// Count of text regions detected by Vision.
    var textRegionCount = 0
    /// Count of human face bounding boxes detected by Vision.
    var faceRegionCount = 0
    /// Count of salient object regions detected by Vision.
    var salientRegionCount = 0
    /// Luminance and chromatic coverage statistics for the image.
    var toneStatistics: ToneStatistics?
    /// Cluster selected for the dominant background role.
    var backgroundRole: Cluster?
    /// Cluster selected for the primary foreground text role.
    var foregroundRole: Cluster?
    /// Cluster selected for the interactive accent role.
    var accentRole: Cluster?
    /// Algorithmic or performance budget warnings recorded during execution.
    var warnings: [String] = []
}

// MARK: - Debug Report

/// Formats and outputs structured diagnostic telemetry reports to the debugging console.
struct JoiColorAnalysisDebugReport: Sendable {
    /// The unique cache key associated with this analysis pass.
    let cacheKey: String
    /// Collected diagnostic telemetry metrics.
    let diagnostics: AnalysisDiagnostics
    /// The finalized color analysis result.
    let result: JoiColorResult
    /// Total wall-clock execution duration in milliseconds.
    let totalMs: Double

    /// Formats and prints an ASCII diagnostic box containing performance timings, color roles, and warnings to standard output.
    func printLog() {
        let width = 72
        let top = "╔" + String(repeating: "═", count: width) + "╗"
        let middle = "╠" + String(repeating: "─", count: width) + "╣"
        let bottom = "╚" + String(repeating: "═", count: width) + "╝"

        func row(_ value: String) -> String {
            let clipped = String(value.prefix(max(0, width - 3)))
            let padding = max(0, width - clipped.count - 2)
            return "║ \(clipped)"
                + String(repeating: " ", count: padding)
                + " ║"
        }

        func timing(_ name: String, _ milliseconds: Double) -> String {
            let value = "\(decimal(milliseconds, places: 2)) ms"
            let dots = max(1, width - name.count - value.count - 3)
            return "║ \(name)"
                + String(repeating: "·", count: dots)
                + "\(value) ║"
        }

        var lines = [
            top,
            row("🎨 JoiColorAnalysis \(joiColorAnalysisVersion)"),
            row("Key: \(cacheKey)"),
            middle
        ]

        if diagnostics.cacheHit {
            lines.append(row("✅ Cache: memory/disk hit"))
        } else {
            lines.append(
                row(
                    "📐 Source: \(Int(diagnostics.sourceSize.width))×"
                        + "\(Int(diagnostics.sourceSize.height))  "
                        + "Sample: \(diagnostics.sampleWidth)×"
                        + "\(diagnostics.sampleHeight)"
                )
            )
            lines.append(row("🔢 Pixels: \(diagnostics.pixelCount)"))
            lines.append(
                row(
                    "⚙️ Renderer: \(diagnostics.renderer)  "
                        + "Style: \(diagnostics.style.rawValue)"
                )
            )

            if let detail = diagnostics.detailAssessment {
                lines.append(
                    row(
                        "🧩 Detail score: \(decimal(Double(detail.score), places: 3))  "
                            + "variance=\(decimal(Double(detail.variance), places: 4))  "
                            + "edges=\(decimal(Double(detail.edgeEnergy), places: 4))  "
                            + "chroma=\(decimal(Double(detail.chromaSpread), places: 2))"
                    )
                )
            }

            lines.append(
                row(
                    "🧮 Clusters: \(diagnostics.actualClusters)/"
                        + "\(diagnostics.requestedClusters)  "
                        + "iterations=\(diagnostics.kMeansIterations)  "
                        + "samples=\(diagnostics.pixelCount)→"
                        + "\(diagnostics.quantizedSampleCount)→"
                        + "\(diagnostics.fittingSampleCount)  "
                        + "identity=\(diagnostics.identityClusterCount)"
                )
            )
            lines.append(
                row(
                    "👁 Vision: \(diagnostics.visionMode.rawValue)  "
                        + "text=\(diagnostics.textRegionCount)  "
                        + "faces=\(diagnostics.faceRegionCount)  "
                        + "salient=\(diagnostics.salientRegionCount)"
                )
            )

            if let tone = diagnostics.toneStatistics {
                lines.append(
                    row(
                        "🌓 Tone: \(tone.tone.rawValue)  "
                            + "confidence=\(decimal(Double(tone.confidence), places: 2))  "
                            + "mean=\(decimal(Double(tone.weightedMean), places: 1))  "
                            + "median=\(decimal(Double(tone.weightedMedian), places: 1))"
                    )
                )
                lines.append(
                    row(
                        "   Coverage: dark=\(percentage(tone.darkCoverage))  "
                            + "light=\(percentage(tone.lightCoverage))  "
                            + "chromatic=\(percentage(tone.chromaticCoverage))  "
                            + "strong=\(percentage(tone.strongChromaticCoverage))"
                    )
                )
            }

            lines.append(middle)
            lines.append(row("Selected Roles"))
            lines.append(
                roleRow(
                    name: "Background",
                    cluster: diagnostics.backgroundRole
                )
            )
            lines.append(
                roleRow(
                    name: "Foreground",
                    cluster: diagnostics.foregroundRole
                )
            )
            lines.append(
                roleRow(
                    name: "Accent",
                    cluster: diagnostics.accentRole
                )
            )

            if !diagnostics.warnings.isEmpty {
                lines.append(middle)
                lines.append(row("Notes"))
                for warning in diagnostics.warnings {
                    lines.append(row("• \(warning)"))
                }
            }

            lines.append(middle)
            lines.append(row("Step Timings"))
            for step in diagnostics.timings {
                lines.append(timing(step.0, step.1))
            }
        }

        lines.append(middle)
        lines.append(
            row(
                "Dark BG \(hex(result.dark.background))  "
                    + "Primary \(hex(result.dark.primaryText))  "
                    + "Accent \(hex(result.dark.accent))"
            )
        )
        lines.append(
            row(
                "Light BG \(hex(result.light.background))  "
                    + "Primary \(hex(result.light.primaryText))  "
                    + "Accent \(hex(result.light.accent))"
            )
        )
        lines.append(row("Style \(result.style.rawValue)"))
        lines.append(timing("⏱ TOTAL", totalMs))
        lines.append(bottom)

        print(lines.joined(separator: "\n"))
    }

    /// Formats a single semantic role diagnostic row with hexadecimal color and LCh breakdown.
    ///
    /// - Parameters:
    ///   - name: The human-readable role name (e.g., "Background", "Foreground", "Accent").
    ///   - cluster: The assigned cluster, or `nil` if synthesized.
    /// - Returns: Formatted text string.
    private func roleRow(name: String, cluster: Cluster?) -> String {
        guard let cluster else {
            return "\(name): neutral synthesis"
        }
        let lch = cluster.color.toLCh()
        return "\(name): \(hex(cluster.color.toCGColor()))  "
            + "L=\(decimal(Double(lch.l), places: 1)) "
            + "C=\(decimal(Double(lch.c), places: 1)) "
            + "H=\(decimal(Double(lch.h), places: 1))° "
            + "weight=\(percentage(cluster.weightedShare)) "
            + "population=\(percentage(cluster.populationShare))"
    }

    /// Formats a float fraction into a rounded percentage string.
    ///
    /// - Parameter value: Float value in range `0...1`.
    /// - Returns: Percentage string with one decimal place.
    private func percentage(_ value: Float) -> String {
        "\(decimal(Double(value * 100), places: 1))%"
    }
}


