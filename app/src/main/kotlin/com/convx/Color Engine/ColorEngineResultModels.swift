import CoreImage
import Metal
import UIKit
import Vision

// MARK: - Result Models

/// A complete tokenized semantic color scheme for a specific visual appearance (light or dark).
struct JoiColorScheme: Sendable {
    /// Background container fill color.
    let background: CGColor
    /// High-contrast primary typography and icon color.
    let primaryText: CGColor
    /// Secondary supporting typography color.
    let secondaryText: CGColor
    /// Tertiary subtle typography color.
    let tertiaryText: CGColor
    /// Quaternary placeholder and faint typography color.
    let quaternaryText: CGColor
    /// Primary interactive accent and call-to-action color.
    let accent: CGColor
    /// Elevated card container and surface fill color.
    let surface: CGColor
    /// Subtle divider, card border, and stroke color.
    let border: CGColor
    /// Ambient shadow and elevation color.
    let shadow: CGColor
    /// Interactive selection and highlight tint color.
    let tint: CGColor
    /// Destructive action and warning indicator color.
    let destructive: CGColor
    /// Top stop of the atmospheric background vertical gradient.
    let backgroundGradientTop: CGColor
    /// Bottom stop of the atmospheric background vertical gradient.
    let backgroundGradientBottom: CGColor
    /// Top stop of the typography vertical gradient.
    let textGradientTop: CGColor
    /// Bottom stop of the typography vertical gradient.
    let textGradientBottom: CGColor
    /// Multi-stop mesh gradient color stops.
    let meshColors: [CGColor]
}

/// Extracted palette metadata containing dominant artwork colors, hex representations, harmonies, and cluster coordinates.
struct JoiColorPalette: Sendable {
    /// Dominant background color.
    let dominant: CGColor
    /// Secondary supporting artwork color.
    let secondary: CGColor
    /// High-contrast interactive accent color.
    let accent: CGColor
    /// Hexadecimal string representation of the dominant color (e.g. `#RRGGBB`).
    let dominantHex: String
    /// Hexadecimal string representation of the secondary color (e.g. `#RRGGBB`).
    let secondaryHex: String
    /// Hexadecimal string representation of the accent color (e.g. `#RRGGBB`).
    let accentHex: String
    /// Unweighted arithmetic average CIELAB color of the image.
    let averageColor: LABColor
    /// Spatially-weighted average CIELAB color emphasizing focal visual content.
    let weightedAverageColor: LABColor
    /// Harmonious color relationships derived from the base background hue.
    let harmonies: [ColorHarmony: [LChColor]]
    /// Clustered centroid coordinates discovered during analysis.
    let clusters: [LABColor]
}

/// Trait-responsive dynamic `UIColor` container resolving automatically according to UIKit `userInterfaceStyle` trait environment.
struct JoiDynamicColors: Sendable {
    /// Dynamic background container fill color.
    let background: UIColor
    /// Dynamic high-contrast primary typography color.
    let primaryText: UIColor
    /// Dynamic secondary supporting typography color.
    let secondaryText: UIColor
    /// Dynamic tertiary subtle typography color.
    let tertiaryText: UIColor
    /// Dynamic quaternary faint typography color.
    let quaternaryText: UIColor
    /// Dynamic interactive accent color.
    let accent: UIColor
    /// Dynamic elevated surface container color.
    let surface: UIColor
    /// Dynamic divider and border stroke color.
    let border: UIColor
    /// Dynamic ambient shadow color.
    let shadow: UIColor
    /// Dynamic interactive tint color.
    let tint: UIColor
    /// Dynamic destructive action color.
    let destructive: UIColor
    /// Dynamic top stop of the background gradient.
    let backgroundGradientTop: UIColor
    /// Dynamic bottom stop of the background gradient.
    let backgroundGradientBottom: UIColor
    /// Dynamic top stop of the typography gradient.
    let textGradientTop: UIColor
    /// Dynamic bottom stop of the typography gradient.
    let textGradientBottom: UIColor
    /// Dynamic multi-stop mesh gradient colors.
    let meshColors: [UIColor]

    /// Initializes dynamic trait-adaptive UIColors by pairing corresponding dark and light `JoiColorScheme` tokens.
    ///
    /// - Parameters:
    ///   - dark: The dark appearance color scheme.
    ///   - light: The light appearance color scheme.
    init(
        dark: JoiColorScheme,
        light: JoiColorScheme
    ) {
        func dynamic(_ darkColor: CGColor, _ lightColor: CGColor) -> UIColor {
            UIColor { traits in
                UIColor(
                    cgColor: traits.userInterfaceStyle == .dark
                        ? darkColor
                        : lightColor
                )
            }
        }

        background = dynamic(dark.background, light.background)
        primaryText = dynamic(dark.primaryText, light.primaryText)
        secondaryText = dynamic(dark.secondaryText, light.secondaryText)
        tertiaryText = dynamic(dark.tertiaryText, light.tertiaryText)
        quaternaryText = dynamic(
            dark.quaternaryText,
            light.quaternaryText
        )
        accent = dynamic(dark.accent, light.accent)
        surface = dynamic(dark.surface, light.surface)
        border = dynamic(dark.border, light.border)
        shadow = dynamic(dark.shadow, light.shadow)
        tint = dynamic(dark.tint, light.tint)
        destructive = dynamic(dark.destructive, light.destructive)
        backgroundGradientTop = dynamic(
            dark.backgroundGradientTop,
            light.backgroundGradientTop
        )
        backgroundGradientBottom = dynamic(
            dark.backgroundGradientBottom,
            light.backgroundGradientBottom
        )
        textGradientTop = dynamic(
            dark.textGradientTop,
            light.textGradientTop
        )
        textGradientBottom = dynamic(
            dark.textGradientBottom,
            light.textGradientBottom
        )
        let meshColorCount = min(
            dark.meshColors.count,
            light.meshColors.count
        )
        meshColors = (0..<meshColorCount).map { index in
            dynamic(
                dark.meshColors[index],
                light.meshColors[index]
            )
        }
    }
}

/// The top-level output returned by Joi Color Analysis.
struct JoiColorResult: Sendable {
    /// Tokenized color scheme optimized for dark appearance interfaces.
    let dark: JoiColorScheme
    /// Tokenized color scheme optimized for light appearance interfaces.
    let light: JoiColorScheme
    /// Trait-responsive dynamic `UIColor` wrappers resolving automatically to dark or light appearances.
    let dynamic: JoiDynamicColors
    /// Raw artwork palette metadata, hex strings, harmonies, and cluster coordinates.
    let palette: JoiColorPalette
    /// Presentation styling profile applied during analysis.
    let style: JoiColorStyle
    /// Perceptual luminance tone classification of the source image (.dark or .light).
    let sourceImageTone: ImageTone
    /// Classification confidence score in range `0...1`.
    let sourceToneConfidence: Float
    /// Total pipeline execution wall-clock time in milliseconds.
    let processingTimeMs: Double

    /// Returns the color scheme matching the native perceptual tone of the source image (`dark` if dark-toned, `light` otherwise).
    var `default`: JoiColorScheme {
        sourceImageTone == .dark ? dark : light
    }
}


