import CoreImage
import Metal
import UIKit
import Vision

// MARK: - Color Models

/// A color representation in the device-independent CIELAB color space (CIE L*a*b* D65).
struct LABColor: Sendable, Equatable {
    /// Lightness channel in range `0...100`.
    let l: CGFloat
    /// Opponent red-green axis coordinate (negative = green, positive = red).
    let a: CGFloat
    /// Opponent yellow-blue axis coordinate (negative = blue, positive = yellow).
    let b: CGFloat

    /// Absolute black color constant `LABColor(0, 0, 0)`.
    static let zero = LABColor(l: 0, a: 0, b: 0)

    /// Calculates the perceptual distance (ΔE) to another CIELAB color using the specified algorithm.
    ///
    /// - Parameters:
    ///   - other: The comparison target color.
    ///   - algorithm: The difference formula to execute (`.euclidean`, `.cie94`, or `.ciede2000`). Defaults to `.cie94`.
    /// - Returns: The computed color difference value.
    func deltaE(
        to other: LABColor,
        using algorithm: DeltaEAlgorithm = .cie94
    ) -> CGFloat {
        switch algorithm {
        case .euclidean:
            let dL = l - other.l
            let dA = a - other.a
            let dB = b - other.b
            return sqrt(dL * dL + dA * dA + dB * dB)

        case .cie94:
            let dL = l - other.l
            let c1 = sqrt(a * a + b * b)
            let c2 = sqrt(other.a * other.a + other.b * other.b)
            let dC = c1 - c2
            let dA = a - other.a
            let dB = b - other.b
            let dHSquared = max(0, dA * dA + dB * dB - dC * dC)
            let sC = 1 + 0.045 * c1
            let sH = 1 + 0.015 * c1
            let cTerm = dC / sC
            let hTerm = sqrt(dHSquared) / sH
            return sqrt(dL * dL + cTerm * cTerm + hTerm * hTerm)

        case .ciede2000:
            return deltaECIEDE2000(to: other)
        }
    }

    /// Converts Cartesian CIELAB coordinates to cylindrical CIELCh (Lightness, Chroma, Hue angle).
    ///
    /// - Returns: The equivalent `LChColor` instance.
    func toLCh() -> LChColor {
        let chroma = sqrt(a * a + b * b)
        var hue = atan2(b, a) * 180 / .pi
        if hue < 0 {
            hue += 360
        }
        return LChColor(l: l, c: chroma, h: hue)
    }

    /// Computes the WCAG 2.1 relative luminance directly from the D65 Y tristimulus component without intermediate color space conversions.
    var relativeLuminance: CGFloat {
        let fy = (l + 16) / 116
        let delta: CGFloat = 6 / 29
        if fy > delta {
            return fy * fy * fy
        }
        return 3 * delta * delta * (fy - 4 / 29)
    }

    /// Converts the CIELAB color to a displayable sRGB `CGColor`.
    ///
    /// If the color falls outside the standard sRGB gamut, chroma is iteratively attenuated by 10% steps
    /// while preserving hue angle and lightness to avoid hue shift distortion.
    ///
    /// - Returns: A valid, in-gamut sRGB `CGColor`.
    func toCGColor() -> CGColor {
        var candidate = self

        for _ in 0..<18 {
            let components = candidate.unclampedSRGB()
            if components.allSatisfy({ $0 >= 0 && $0 <= 1 }) {
                return CGColor(
                    red: components[0],
                    green: components[1],
                    blue: components[2],
                    alpha: 1
                )
            }
            candidate = LABColor(
                l: candidate.l,
                a: candidate.a * 0.9,
                b: candidate.b * 0.9
            )
        }

        let components = candidate.unclampedSRGB().map {
            max(0, min(1, $0))
        }
        return CGColor(
            red: components[0],
            green: components[1],
            blue: components[2],
            alpha: 1
        )
    }

    /// Indicates whether the current CIELAB coordinates fall strictly inside standard sRGB gamut boundaries `[0, 1]`.
    var isInSRGBGamut: Bool {
        unclampedSRGB().allSatisfy { component in
            component >= 0 && component <= 1
        }
    }

    /// Computes unclamped sRGB channel values via D65 standard illuminant transformations.
    ///
    /// - Returns: Array of `[r, g, b]` floats which may exceed `0...1` if out of gamut.
    private func unclampedSRGB() -> [CGFloat] {
        let fy = (l + 16) / 116
        let fx = a / 500 + fy
        let fz = fy - b / 200
        let delta: CGFloat = 6 / 29

        func inverseLab(_ value: CGFloat) -> CGFloat {
            if value > delta {
                return value * value * value
            }
            return 3 * delta * delta * (value - 4 / 29)
        }

        let x = inverseLab(fx) * 0.95047
        let y = inverseLab(fy)
        let z = inverseLab(fz) * 1.08883

        let linearRed = 3.2404542 * x - 1.5371385 * y - 0.4985314 * z
        let linearGreen = -0.9692660 * x + 1.8760108 * y + 0.0415560 * z
        let linearBlue = 0.0556434 * x - 0.2040259 * y + 1.0572252 * z

        func encodeSRGB(_ value: CGFloat) -> CGFloat {
            if value <= 0.0031308 {
                return 12.92 * value
            }
            return 1.055 * pow(value, 1 / 2.4) - 0.055
        }

        return [
            encodeSRGB(linearRed),
            encodeSRGB(linearGreen),
            encodeSRGB(linearBlue)
        ]
    }

    /// Computes the comprehensive ISO/CIE standard CIEDE2000 color difference formula.
    ///
    /// - Parameter other: The target comparison color.
    /// - Returns: The CIEDE2000 perceptual distance.
    private func deltaECIEDE2000(to other: LABColor) -> CGFloat {
        let l1 = l
        let a1 = a
        let b1 = b
        let l2 = other.l
        let a2 = other.a
        let b2 = other.b

        let c1 = sqrt(a1 * a1 + b1 * b1)
        let c2 = sqrt(a2 * a2 + b2 * b2)
        let averageC = (c1 + c2) / 2
        let averageC7 = pow(averageC, 7)
        let twentyFive7 = pow(CGFloat(25), 7)
        let g = 0.5 * (1 - sqrt(averageC7 / (averageC7 + twentyFive7)))

        let adjustedA1 = a1 * (1 + g)
        let adjustedA2 = a2 * (1 + g)
        let adjustedC1 = sqrt(adjustedA1 * adjustedA1 + b1 * b1)
        let adjustedC2 = sqrt(adjustedA2 * adjustedA2 + b2 * b2)

        func hue(_ y: CGFloat, _ x: CGFloat) -> CGFloat {
            guard x != 0 || y != 0 else {
                return 0
            }
            var result = atan2(y, x) * 180 / .pi
            if result < 0 {
                result += 360
            }
            return result
        }

        let h1 = hue(b1, adjustedA1)
        let h2 = hue(b2, adjustedA2)
        let deltaC = adjustedC2 - adjustedC1

        let deltaHDegrees: CGFloat
        if adjustedC1 * adjustedC2 == 0 {
            deltaHDegrees = 0
        } else if abs(h2 - h1) <= 180 {
            deltaHDegrees = h2 - h1
        } else if h2 - h1 > 180 {
            deltaHDegrees = h2 - h1 - 360
        } else {
            deltaHDegrees = h2 - h1 + 360
        }

        let deltaH = 2
            * sqrt(adjustedC1 * adjustedC2)
            * sin(deltaHDegrees * .pi / 360)

        let averageL = (l1 + l2) / 2
        let averageAdjustedC = (adjustedC1 + adjustedC2) / 2

        let averageH: CGFloat
        if adjustedC1 * adjustedC2 == 0 {
            averageH = h1 + h2
        } else if abs(h1 - h2) <= 180 {
            averageH = (h1 + h2) / 2
        } else if h1 + h2 < 360 {
            averageH = (h1 + h2 + 360) / 2
        } else {
            averageH = (h1 + h2 - 360) / 2
        }

        let hueRadians = averageH * .pi / 180
        let t = 1
            - 0.17 * cos(hueRadians - 30 * .pi / 180)
            + 0.24 * cos(2 * hueRadians)
            + 0.32 * cos(3 * hueRadians + 6 * .pi / 180)
            - 0.20 * cos(4 * hueRadians - 63 * .pi / 180)

        let lightnessOffset = averageL - 50
        let sL = 1 + 0.015
            * lightnessOffset
            * lightnessOffset
            / sqrt(20 + lightnessOffset * lightnessOffset)
        let sC = 1 + 0.045 * averageAdjustedC
        let sH = 1 + 0.015 * averageAdjustedC * t

        let averageAdjustedC7 = pow(averageAdjustedC, 7)
        let rC = 2 * sqrt(
            averageAdjustedC7 / (averageAdjustedC7 + twentyFive7)
        )
        let deltaTheta = 30
            * exp(-pow((averageH - 275) / 25, 2))
            * .pi
            / 180
        let rT = -sin(2 * deltaTheta) * rC

        let lTerm = (l2 - l1) / sL
        let cTerm = deltaC / sC
        let hTerm = deltaH / sH
        return sqrt(
            lTerm * lTerm
                + cTerm * cTerm
                + hTerm * hTerm
                + rT * cTerm * hTerm
        )
    }
}

/// Cylindrical representation of CIELAB color space (Lightness, Chroma, Hue).
struct LChColor: Sendable, Equatable {
    /// Lightness channel in range `0...100`.
    let l: CGFloat
    /// Chroma (saturation / distance from neutral axis) where `0` represents an achromatic neutral.
    let c: CGFloat
    /// Hue angle in degrees in range `0..<360`.
    let h: CGFloat

    /// Converts cylindrical LCh coordinates back to Cartesian `LABColor`.
    ///
    /// - Returns: Reconstructed `LABColor` model.
    func toLAB() -> LABColor {
        let radians = normalizedHue * .pi / 180
        return LABColor(
            l: l,
            a: c * cos(radians),
            b: c * sin(radians)
        )
    }

    /// Returns a copy of this color with adjusted lightness.
    ///
    /// - Parameter value: New lightness value `0...100`.
    /// - Returns: Modified `LChColor`.
    func withLightness(_ value: CGFloat) -> LChColor {
        LChColor(l: value, c: c, h: normalizedHue)
    }

    /// Returns a copy of this color with adjusted chroma.
    ///
    /// - Parameter value: New non-negative chroma value.
    /// - Returns: Modified `LChColor`.
    func withChroma(_ value: CGFloat) -> LChColor {
        LChColor(l: l, c: max(0, value), h: normalizedHue)
    }

    /// Returns a copy of this color with adjusted hue angle.
    ///
    /// - Parameter value: New hue angle in degrees (automatically normalized to `0..<360`).
    /// - Returns: Modified `LChColor`.
    func withHue(_ value: CGFloat) -> LChColor {
        LChColor(l: l, c: c, h: Self.normalizeHue(value))
    }

    /// Converts to displayable sRGB `CGColor` via CIELAB intermediate projection.
    ///
    /// - Returns: In-gamut sRGB `CGColor`.
    func toCGColor() -> CGColor {
        toLAB().toCGColor()
    }

    /// Computes derived harmonious colors corresponding to a specific `ColorHarmony` rule.
    ///
    /// - Parameter type: The harmony geometry to evaluate.
    /// - Returns: Array of harmonious `LChColor` instances.
    func harmony(_ type: ColorHarmony) -> [LChColor] {
        switch type {
        case .complementary:
            return [withHue(h + 180)]
        case .splitComplementary:
            return [withHue(h + 150), withHue(h + 210)]
        case .triadic:
            return [withHue(h + 120), withHue(h + 240)]
        case .tetradic:
            return [withHue(h + 90), withHue(h + 180), withHue(h + 270)]
        case .analogous:
            return [withHue(h - 30), withHue(h + 30)]
        }
    }

    /// Evaluates all supported color harmonies for this color.
    ///
    /// - Returns: Dictionary mapping each `ColorHarmony` case to its generated harmonious color array.
    func allHarmonies() -> [ColorHarmony: [LChColor]] {
        Dictionary(
            uniqueKeysWithValues: ColorHarmony.allCases.map {
                ($0, harmony($0))
            }
        )
    }

    /// Normalized hue angle strictly within range `0..<360`.
    private var normalizedHue: CGFloat {
        Self.normalizeHue(h)
    }

    /// Wraps any arbitrary hue angle into the canonical range `0..<360`.
    ///
    /// - Parameter hue: Raw hue angle in degrees.
    /// - Returns: Normalized hue angle in degrees.
    private static func normalizeHue(_ hue: CGFloat) -> CGFloat {
        let value = hue.truncatingRemainder(dividingBy: 360)
        return value < 0 ? value + 360 : value
    }
}


