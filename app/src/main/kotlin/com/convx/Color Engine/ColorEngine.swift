//
//  ColorEngine.swift
//  Joi
//
//  Safe, deterministic and perceptually-aware artwork color analysis.
//

import CoreImage
import Metal
import UIKit
import Vision

// MARK: - Version and Errors

/// The active version tag for the Joi color analysis pipeline and cache serialization schema.
let joiColorAnalysisVersion = "v26"

/// Errors encountered during the Joi color analysis pipeline.
enum JoiError: Error {
    /// The input image could not be converted into a valid CIImage representation.
    case invalidImage
    /// Normalization or detail assessment failed during the initial processing stage.
    case normalizationFailed
    /// Pixel buffer decoding or LAB color extraction failed.
    case pixelExtractionFailed
    /// Centroid generation or K-Means clustering failed to produce valid color clusters.
    case clusteringFailed
}

// MARK: - Public Analysis Options

/// Configures the level of Apple Vision framework processing used during color analysis.
enum JoiColorVisionMode: String, Codable, Sendable {
    /// Disables Vision analysis, utilizing only geometric and spatial weighting.
    case disabled
    /// Performs balanced Vision analysis with attention saliency and detected text bounding boxes.
    case balanced
    /// Performs deep Vision analysis with character-level text bounding boxes, objectness, and attention saliency.
    case detailed
}

/// Defines presentation styling profiles that adjust the saturation and lightness curves of extracted colors
/// without favoring or altering any specific hue family.
enum JoiColorStyle: String, CaseIterable, Codable, Sendable {
    /// Default balanced contrast and saturation suitable for general UI themes.
    case balanced
    /// Elevated chroma and contrast for expressive, vibrant artwork presentation.
    case vibrant
    /// Softened chroma and attenuated contrast for subtle, pastel aesthetics.
    case pastel
}

/// The perceptual tone classification of a source image based on luminance statistics.
enum ImageTone: String, Codable, Sendable {
    /// Dark-toned visual content where primary luminance is concentrated in low lightness bands.
    case dark
    /// Light-toned visual content where primary luminance is concentrated in high lightness bands.
    case light
}

/// Supported color difference formulas in CIELAB color space.
enum DeltaEAlgorithm: Sendable {
    /// Fast Euclidean distance in CIELAB coordinates.
    case euclidean
    /// CIE94 perceptual distance metric optimized for graphic arts and surface colors.
    case cie94
    /// CIEDE2000 standard metric providing maximum perceptual uniformity and compensation for lightness, chroma, and hue differences.
    case ciede2000
}

/// Standard color harmony relationships derived from geometric hue angles on the cylindrical LCh color wheel.
enum ColorHarmony: String, CaseIterable, Sendable, Hashable {
    /// Direct opposite hue angle (+180°).
    case complementary
    /// Two hues flanking the complementary hue (+150°, +210°).
    case splitComplementary
    /// Three evenly spaced hues at 120° intervals (+120°, +240°).
    case triadic
    /// Four hues arranged as two complementary pairs (+90°, +180°, +270°).
    case tetradic
    /// Neighboring adjacent hues on either side (±30°).
    case analogous
}

// MARK: - UIImage API

extension UIImage {
    /// Analyzes the color composition of the receiver with backwards-compatible parameters.
    ///
    /// - Parameters:
    ///   - cacheKey: A unique identifier used to persist and retrieve cached color analysis results.
    ///   - visionEnabled: A boolean flag indicating whether Vision framework saliency/text detection should be executed.
    ///   - style: The presentation styling profile applied to the generated color scheme. Defaults to `.balanced`.
    ///   - debug: When set to `true`, prints a detailed diagnostic performance and role assignment report to the console.
    /// - Returns: A complete `JoiColorResult` containing dark and light color schemes, dynamic UIColors, palettes, and metadata.
    /// - Throws: `JoiError` if image normalization, pixel extraction, or clustering fails.
    func joiColors(
        cacheKey: String,
        visionEnabled: Bool = true,
        style: JoiColorStyle = .balanced,
        debug: Bool = false
    ) async throws -> JoiColorResult {
        try await JoiColorAnalysis.shared.analyze(
            image: self,
            cacheKey: cacheKey,
            visionMode: visionEnabled ? .balanced : .disabled,
            style: style,
            debug: debug
        )
    }

    /// Analyzes the color composition of the receiver with explicit Vision fidelity selection.
    ///
    /// - Parameters:
    ///   - cacheKey: A unique identifier used to persist and retrieve cached color analysis results.
    ///   - visionMode: The Vision analysis fidelity mode (`.disabled`, `.balanced`, or `.detailed`).
    ///   - style: The presentation styling profile applied to the generated color scheme. Defaults to `.balanced`.
    ///   - debug: When set to `true`, prints a detailed diagnostic performance and role assignment report to the console.
    /// - Returns: A complete `JoiColorResult` containing dark and light color schemes, dynamic UIColors, palettes, and metadata.
    /// - Throws: `JoiError` if image normalization, pixel extraction, or clustering fails.
    func joiColors(
        cacheKey: String,
        visionMode: JoiColorVisionMode,
        style: JoiColorStyle = .balanced,
        debug: Bool = false
    ) async throws -> JoiColorResult {
        try await JoiColorAnalysis.shared.analyze(
            image: self,
            cacheKey: cacheKey,
            visionMode: visionMode,
            style: style,
            debug: debug
        )
    }

    /// Convenience debugging helper that executes color analysis with diagnostic console logging enabled.
    ///
    /// - Parameters:
    ///   - cacheKey: A unique identifier used to persist and retrieve cached color analysis results.
    ///   - visionEnabled: A boolean flag indicating whether Vision framework analysis should be executed. Defaults to `true`.
    ///   - style: The presentation styling profile applied to the generated color scheme. Defaults to `.balanced`.
    /// - Returns: A complete `JoiColorResult` containing dark and light color schemes, dynamic UIColors, palettes, and metadata.
    /// - Throws: `JoiError` if image normalization, pixel extraction, or clustering fails.
    @discardableResult
    func joiColorsDebug(
        cacheKey: String,
        visionEnabled: Bool = true,
        style: JoiColorStyle = .balanced
    ) async throws -> JoiColorResult {
        try await joiColors(
            cacheKey: cacheKey,
            visionEnabled: visionEnabled,
            style: style,
            debug: true
        )
    }
}

