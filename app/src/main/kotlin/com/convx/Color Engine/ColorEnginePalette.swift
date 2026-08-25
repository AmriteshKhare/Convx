import CoreImage
import Metal
import UIKit
import Vision

// MARK: - Palette

extension JoiColorAnalysis {
    /// Assembles the public `JoiColorPalette` model containing primary role colors, hex representations, harmonies, and cluster coordinates.
    ///
    /// - Parameters:
    ///   - roles: Semantic role selection output (background, secondary, accent).
    ///   - clusters: Presentation clusters representing observed artwork colors.
    ///   - backgroundSource: The base LCh color derived for the background.
    ///   - averages: Unweighted and spatially weighted average CIELAB colors of the image.
    /// - Returns: A materialized `JoiColorPalette` instance.
    func makePalette(
        roles: RoleSelection,
        clusters: [Cluster],
        backgroundSource: LChColor,
        averages: (average: LABColor, weighted: LABColor)
    ) -> JoiColorPalette {
        let dominant = fitToSRGBGamut(backgroundSource).toCGColor()
        let secondary = roles.secondary.color.toCGColor()
        let accentSource = roles.accent?.color.toLCh()
            ?? backgroundSource
        let accent = fitToSRGBGamut(accentSource).toCGColor()
        return JoiColorPalette(
            dominant: dominant,
            secondary: secondary,
            accent: accent,
            dominantHex: hex(dominant),
            secondaryHex: hex(secondary),
            accentHex: hex(accent),
            averageColor: averages.average,
            weightedAverageColor: averages.weighted,
            harmonies: backgroundSource.allHarmonies(),
            clusters: clusters.map(\.color)
        )
    }
}

