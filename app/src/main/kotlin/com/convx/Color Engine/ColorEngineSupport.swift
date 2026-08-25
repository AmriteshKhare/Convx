import CoreImage
import Metal
import UIKit
import Vision

// MARK: - Geometry and Color Utilities

extension JoiColorAnalysis {
    /// Maps a normalized Vision framework bounding box to pixel indices in a grid buffer and executes an action closure for every covered pixel.
    ///
    /// - Parameters:
    ///   - region: Normalized bounding box in Vision coordinate space (origin at bottom-left, coordinates `0...1`).
    ///   - width: Pixel grid width.
    ///   - height: Pixel grid height.
    ///   - action: Closure receiving the linear 1D index `y * width + x`.
    func apply(
        region: CGRect,
        width: Int,
        height: Int,
        action: (Int) -> Void
    ) {
        let minimumX = max(0, Int(region.minX * CGFloat(width)))
        let maximumX = min(width, Int(ceil(region.maxX * CGFloat(width))))
        let minimumY = max(
            0,
            Int((1 - region.maxY) * CGFloat(height))
        )
        let maximumY = min(
            height,
            Int(ceil((1 - region.minY) * CGFloat(height)))
        )
        guard minimumX < maximumX, minimumY < maximumY else {
            return
        }

        for y in minimumY..<maximumY {
            for x in minimumX..<maximumX {
                action(y * width + x)
            }
        }
    }

    /// Determines whether a grid coordinate `(x, y)` lies within outer 8% corner margins.
    ///
    /// - Parameters:
    ///   - x: Horizontal pixel coordinate.
    ///   - y: Vertical pixel coordinate.
    ///   - width: Grid width in pixels.
    ///   - height: Grid height in pixels.
    /// - Returns: `true` if coordinate is inside a corner region; otherwise `false`.
    func isCorner(
        x: Int,
        y: Int,
        width: Int,
        height: Int
    ) -> Bool {
        let horizontalMargin = max(1, Int(Float(width) * 0.08))
        let verticalMargin = max(1, Int(Float(height) * 0.08))
        let isHorizontalEdge = x < horizontalMargin
            || x >= width - horizontalMargin
        let isVerticalEdge = y < verticalMargin
            || y >= height - verticalMargin
        return isHorizontalEdge && isVerticalEdge
    }

    /// Normalizes a float array into range `0...1` based on its minimum and maximum values.
    ///
    /// - Parameters:
    ///   - values: Array of raw float values.
    ///   - fallback: Fallback value returned when all elements are identical.
    /// - Returns: Normalized float array in range `0...1`.
    func normalized(
        _ values: [Float],
        fallback: Float
    ) -> [Float] {
        guard
            let minimum = values.min(),
            let maximum = values.max(),
            maximum - minimum > 0.000_001
        else {
            return [Float](repeating: fallback, count: values.count)
        }
        let range = maximum - minimum
        return values.map {
            clamp(
                ($0 - minimum) / range,
                minimum: 0,
                maximum: 1
            )
        }
    }

    /// Computes elapsed execution duration in milliseconds from a starting DispatchTime uptime timestamp.
    ///
    /// - Parameter start: Starting nanoseconds from `DispatchTime.now().uptimeNanoseconds`.
    /// - Returns: Elapsed duration in milliseconds.
    func elapsedMS(since start: UInt64) -> Double {
        Double(DispatchTime.now().uptimeNanoseconds - start) / 1_000_000
    }
}

/// Precomputed 256-entry lookup table converting 8-bit non-linear sRGB byte values to linear RGB floats.
let sRGBLinear8BitLUT: [Float] = (0...255).map { byte in
    let value = Float(byte) / 255
    if value <= 0.04045 {
        return value / 12.92
    }
    return pow((value + 0.055) / 1.055, 2.4)
}

/// Resolution step count for the fast CIELAB transformation lookup table.
let labTransformLUTResolution = 4_096

/// Precomputed 4096-entry lookup table for the piecewise cubic root function `f(t)` in standard CIE 1976 conversions.
let labTransformLUT: [Float] = (
    0...labTransformLUTResolution
).map { index in
    let value = Float(index) / Float(labTransformLUTResolution)
    let delta: Float = 6 / 29
    if value > delta * delta * delta {
        return pow(value, 1 / 3)
    }
    return value / (3 * delta * delta) + 4 / 29
}

/// LUT-accelerated conversion from 8-bit unsigned integer sRGB channels to CIELAB `LABPoint`.
///
/// - Parameters:
///   - red: Red byte `0...255`.
///   - green: Green byte `0...255`.
///   - blue: Blue byte `0...255`.
/// - Returns: CIELAB `LABPoint`.
func rgb8ToLAB(
    red: UInt8,
    green: UInt8,
    blue: UInt8
) -> LABPoint {
    linearRGBToLAB(
        red: sRGBLinear8BitLUT[Int(red)],
        green: sRGBLinear8BitLUT[Int(green)],
        blue: sRGBLinear8BitLUT[Int(blue)]
    )
}

/// Converts floating-point sRGB channel values in range `0...1` to CIELAB `LABPoint`.
///
/// - Parameters:
///   - red: Red float `0...1`.
///   - green: Green float `0...1`.
///   - blue: Blue float `0...1`.
/// - Returns: CIELAB `LABPoint`.
func rgbToLAB(
    red: Float,
    green: Float,
    blue: Float
) -> LABPoint {
    func linearize(_ value: Float) -> Float {
        if value <= 0.04045 {
            return value / 12.92
        }
        return pow((value + 0.055) / 1.055, 2.4)
    }

    return linearRGBToLAB(
        red: linearize(red),
        green: linearize(green),
        blue: linearize(blue)
    )
}

/// Converts linear RGB channel values to CIELAB `LABPoint` via standard D65 standard illuminant matrices and lookup tables.
///
/// - Parameters:
///   - red: Linear red float `0...1`.
///   - green: Linear green float `0...1`.
///   - blue: Linear blue float `0...1`.
/// - Returns: CIELAB `LABPoint`.
func linearRGBToLAB(
    red: Float,
    green: Float,
    blue: Float
) -> LABPoint {
    let x = (
        0.4124564 * red
            + 0.3575761 * green
            + 0.1804375 * blue
    ) / 0.95047
    let y = 0.2126729 * red
        + 0.7151522 * green
        + 0.0721750 * blue
    let z = (
        0.0193339 * red
            + 0.1191920 * green
            + 0.9503041 * blue
    ) / 1.08883

    func labTransform(_ value: Float) -> Float {
        let position = clamp(
            value,
            minimum: 0,
            maximum: 1
        ) * Float(labTransformLUTResolution)
        let lowerIndex = Int(position)
        let upperIndex = min(
            labTransformLUTResolution,
            lowerIndex + 1
        )
        let fraction = position - Float(lowerIndex)
        let lower = labTransformLUT[lowerIndex]
        return lower + (labTransformLUT[upperIndex] - lower) * fraction
    }

    let fx = labTransform(x)
    let fy = labTransform(y)
    let fz = labTransform(z)
    return LABPoint(
        l: 116 * fy - 16,
        a: 500 * (fx - fy),
        b: 200 * (fy - fz)
    )
}

/// Fast, deterministic 64-bit pseudo-random number generator based on the SplitMix64 algorithm.
struct SplitMix64 {
    private var state: UInt64

    /// Initializes the generator with a 64-bit seed.
    ///
    /// - Parameter seed: 64-bit integer seed.
    init(seed: UInt64) {
        state = seed &+ 0x9E3779B97F4A7C15
    }

    /// Advances the generator state and returns the next pseudo-random 64-bit unsigned integer.
    ///
    /// - Returns: Uniform pseudo-random `UInt64`.
    mutating func next() -> UInt64 {
        state &+= 0x9E3779B97F4A7C15
        var value = state
        value = (value ^ (value >> 30)) &* 0xBF58476D1CE4E5B9
        value = (value ^ (value >> 27)) &* 0x94D049BB133111EB
        return value ^ (value >> 31)
    }

    /// Generates a uniform double-precision floating point value in half-open interval `[0, 1)`.
    ///
    /// - Returns: Floating-point value in `[0, 1)`.
    mutating func nextUnitDouble() -> Double {
        Double(next() >> 11) / Double(1 << 53)
    }
}

/// 64-bit FNV-1a offset basis constant.
let fnvOffsetBasis: UInt64 = 14_695_981_039_346_656_037

/// 64-bit FNV-1a prime multiplier constant.
let fnvPrime: UInt64 = 1_099_511_628_211

/// Updates an existing FNV-1a hash with an additional byte.
///
/// - Parameters:
///   - hash: Current accumulator hash value.
///   - byte: Input byte to mix.
/// - Returns: Updated 64-bit hash.
func fnvMix(_ hash: UInt64, _ byte: UInt8) -> UInt64 {
    (hash ^ UInt64(byte)) &* fnvPrime
}

/// Computes a deterministic 64-bit FNV-1a hash formatted as a lowercase hexadecimal string.
///
/// - Parameter value: String input to hash.
/// - Returns: Hexadecimal hash string.
func stableHexHash(_ value: String) -> String {
    var hash = fnvOffsetBasis
    for byte in value.utf8 {
        hash = fnvMix(hash, byte)
    }
    return String(hash, radix: 16, uppercase: false)
}

/// Computes smooth Hermite interpolation between two thresholds.
///
/// - Parameters:
///   - edge0: Lower edge threshold.
///   - edge1: Upper edge threshold.
///   - value: Input value.
/// - Returns: Smoothly interpolated value in range `0...1`.
func smoothStep(
    edge0: Float,
    edge1: Float,
    value: Float
) -> Float {
    guard edge1 > edge0 else {
        return value >= edge1 ? 1 : 0
    }
    let t = clamp(
        (value - edge0) / (edge1 - edge0),
        minimum: 0,
        maximum: 1
    )
    return t * t * (3 - 2 * t)
}

/// Clamps a comparable value within closed bounds `[minimum, maximum]`.
///
/// - Parameters:
///   - value: The value to clamp.
///   - minimum: Lower bound.
///   - maximum: Upper bound.
/// - Returns: Clamped value.
func clamp<T: Comparable>(
    _ value: T,
    minimum: T,
    maximum: T
) -> T {
    min(max(value, minimum), maximum)
}

/// Extracts normalized `[r, g, b, a]` double components from a `CGColor`.
///
/// - Parameter color: The source `CGColor`.
/// - Returns: 4-element array containing `[red, green, blue, alpha]` in range `0...1`.
func rgba(_ color: CGColor) -> [Double] {
    let components = color.components ?? []
    if components.count >= 4 {
        return [
            Double(components[0]),
            Double(components[1]),
            Double(components[2]),
            Double(components[3])
        ]
    }
    if components.count == 2 {
        return [
            Double(components[0]),
            Double(components[0]),
            Double(components[0]),
            Double(components[1])
        ]
    }
    if components.count == 3 {
        return [
            Double(components[0]),
            Double(components[1]),
            Double(components[2]),
            1
        ]
    }
    return [0, 0, 0, 1]
}

/// Materializes a `CGColor` in sRGB color space from an array of `[r, g, b, a]` double components.
///
/// - Parameter components: Array of color components.
/// - Returns: A valid `CGColor`.
func cgColor(_ components: [Double]) -> CGColor {
    guard components.count >= 3 else {
        return CGColor(gray: 0, alpha: 1)
    }
    return CGColor(
        red: CGFloat(clamp(components[0], minimum: 0, maximum: 1)),
        green: CGFloat(clamp(components[1], minimum: 0, maximum: 1)),
        blue: CGFloat(clamp(components[2], minimum: 0, maximum: 1)),
        alpha: CGFloat(
            clamp(
                components.count >= 4 ? components[3] : 1,
                minimum: 0,
                maximum: 1
            )
        )
    )
}

/// Formats a `CGColor` as a standard 7-character hexadecimal string (`#RRGGBB`).
///
/// - Parameter color: The source `CGColor`.
/// - Returns: Hexadecimal color string.
func hex(_ color: CGColor) -> String {
    let components = rgba(color)
    let red = Int((clamp(components[0], minimum: 0, maximum: 1) * 255).rounded())
    let green = Int((clamp(components[1], minimum: 0, maximum: 1) * 255).rounded())
    let blue = Int((clamp(components[2], minimum: 0, maximum: 1) * 255).rounded())
    return "#\(twoDigitHex(red))\(twoDigitHex(green))\(twoDigitHex(blue))"
}

/// Formats an integer in range `0...255` as an uppercase 2-character hexadecimal string.
///
/// - Parameter value: Integer byte value.
/// - Returns: 2-character hex string.
func twoDigitHex(_ value: Int) -> String {
    let result = String(
        clamp(value, minimum: 0, maximum: 255),
        radix: 16,
        uppercase: true
    )
    return result.count == 1 ? "0\(result)" : result
}

/// Formats a floating-point number with a fixed number of decimal places without locale dependencies.
///
/// - Parameters:
///   - value: Input floating point number.
///   - places: Number of decimal places `0...6`.
/// - Returns: Formatted decimal string.
func decimal(_ value: Double, places: Int) -> String {
    guard value.isFinite else {
        return "n/a"
    }
    let safePlaces = clamp(places, minimum: 0, maximum: 6)
    let multiplier = pow(10.0, Double(safePlaces))
    let scaled = Int((value * multiplier).rounded())
    let sign = scaled < 0 ? "-" : ""
    let magnitude = abs(scaled)
    guard safePlaces > 0 else {
        return "\(sign)\(magnitude)"
    }
    let whole = magnitude / Int(multiplier)
    let fraction = magnitude % Int(multiplier)
    let rawFraction = String(fraction)
    let padding = String(
        repeating: "0",
        count: max(0, safePlaces - rawFraction.count)
    )
    return "\(sign)\(whole).\(padding)\(rawFraction)"
}




