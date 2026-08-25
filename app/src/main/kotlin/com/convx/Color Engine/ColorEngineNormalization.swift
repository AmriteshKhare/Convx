import CoreImage
import Metal
import UIKit
import Vision

// MARK: - Image Normalization

extension JoiColorAnalysis {
    /// Converts a `UIImage` into an orientation-corrected `CIImage` anchored at the coordinate origin.
    ///
    /// - Parameter image: The source `UIImage`.
    /// - Returns: An orientation-corrected `CIImage`, or `nil` if the extent is empty or infinite.
    func normalizedSourceImage(_ image: UIImage) -> CIImage? {
        guard
            var source = CIImage(
                image: image,
                options: [.applyOrientationProperty: true]
            ),
            !source.extent.isEmpty,
            !source.extent.isInfinite
        else {
            return nil
        }

        if source.extent.origin != .zero {
            source = source.transformed(
                by: CGAffineTransform(
                    translationX: -source.extent.origin.x,
                    y: -source.extent.origin.y
                )
            )
        }
        return source
    }

    /// Computes aspect-ratio preserved integer sample dimensions constrained by a maximum bounding dimension.
    ///
    /// - Parameters:
    ///   - sourceSize: Native pixel dimensions of the image.
    ///   - maximumDimension: Maximum allowed width or height in pixels.
    /// - Returns: A tuple containing the calculated `(width, height)`.
    func sampleDimensions(
        for sourceSize: CGSize,
        maximumDimension: Int
    ) -> (width: Int, height: Int) {
        guard sourceSize.width > 0, sourceSize.height > 0 else {
            return (maximumDimension, maximumDimension)
        }

        let aspect = sourceSize.width / sourceSize.height
        if aspect >= 1 {
            return (
                maximumDimension,
                max(1, Int((CGFloat(maximumDimension) / aspect).rounded()))
            )
        }
        return (
            max(1, Int((CGFloat(maximumDimension) * aspect).rounded())),
            maximumDimension
        )
    }

    /// Evaluates visual complexity, luminance variance, edge energy, and chroma distribution of the image.
    ///
    /// Renders a fast 16x16 thumbnail to determine the ideal downsample resolution and target cluster count.
    ///
    /// - Parameters:
    ///   - source: Orientation-normalized `CIImage`.
    ///   - visionMode: Configured Vision framework analysis mode.
    /// - Returns: A `DetailAssessment` structure with complexity scores and recommended thumbnail sizes.
    /// - Throws: `JoiError.normalizationFailed` or `JoiError.pixelExtractionFailed` if preview rendering fails.
    func assessDetail(
        _ source: CIImage,
        visionMode: JoiColorVisionMode
    ) throws -> DetailAssessment {
        let previewSize = sampleDimensions(
            for: source.extent.size,
            maximumDimension: 16
        )
        guard let preview = render(
            source,
            width: previewSize.width,
            height: previewSize.height
        ) else {
            throw JoiError.normalizationFailed
        }

        let rgb = try decodedRGB(preview)
        let width = preview.width
        let height = preview.height
        let pixelCount = width * height
        guard pixelCount > 0 else {
            throw JoiError.pixelExtractionFailed
        }

        var luminance = [Float](repeating: 0, count: pixelCount)
        var chroma = [Float](repeating: 0, count: pixelCount)
        var luminanceSum: Float = 0

        for index in 0..<pixelCount {
            let red = rgb[index * 3]
            let green = rgb[index * 3 + 1]
            let blue = rgb[index * 3 + 2]
            let value = 0.2126 * red + 0.7152 * green + 0.0722 * blue
            luminance[index] = value
            luminanceSum += value
            chroma[index] = max(red, green, blue) - min(red, green, blue)
        }

        let mean = luminanceSum / Float(pixelCount)
        var variance: Float = 0
        var chromaMean: Float = 0
        for index in 0..<pixelCount {
            let delta = luminance[index] - mean
            variance += delta * delta
            chromaMean += chroma[index]
        }
        variance /= Float(pixelCount)
        chromaMean /= Float(pixelCount)

        var edgeEnergy: Float = 0
        var edgeCount = 0
        for y in 0..<height {
            for x in 0..<width {
                let index = y * width + x
                if x + 1 < width {
                    edgeEnergy += abs(
                        luminance[index] - luminance[index + 1]
                    )
                    edgeCount += 1
                }
                if y + 1 < height {
                    edgeEnergy += abs(
                        luminance[index]
                            - luminance[index + width]
                    )
                    edgeCount += 1
                }
            }
        }
        if edgeCount > 0 {
            edgeEnergy /= Float(edgeCount)
        }

        let score = clamp(
            variance * 3.8 + edgeEnergy * 0.9 + chromaMean * 0.22,
            minimum: 0,
            maximum: 1
        )
        // High-quality downsampling preserves spatial details for clustering.
        // Higher Vision modes scale thumbnail resolution to preserve small typography/object elements.
        let thumbnailSize: Int
        switch visionMode {
        case .disabled:
            thumbnailSize = score < 0.105 ? 64 : 96
        case .balanced:
            thumbnailSize = score < 0.105 ? 80 : 112
        case .detailed:
            thumbnailSize = score < 0.105 ? 96 : 128
        }
        let clusterCount: Int
        if score < 0.065 {
            clusterCount = 5
        } else if score < 0.16 {
            clusterCount = 7
        } else {
            clusterCount = 8
        }

        return DetailAssessment(
            variance: variance,
            edgeEnergy: edgeEnergy,
            chromaSpread: chromaMean * 100,
            score: score,
            thumbnailSize: thumbnailSize,
            requestedClusterCount: clusterCount
        )
    }

    /// Renders a cropped and aspect-fit scaled `CIImage` into an 8-bit RGBA pixel buffer using `CIContext`.
    ///
    /// - Parameters:
    ///   - source: Source `CIImage`.
    ///   - width: Destination buffer width.
    ///   - height: Destination buffer height.
    /// - Returns: A `RenderedRGBA` container wrapping bytes, dimensions, and `CGImage`, or `nil` on failure.
    func render(
        _ source: CIImage,
        width: Int,
        height: Int
    ) -> RenderedRGBA? {
        let target = CGSize(width: width, height: height)
        let targetAspect = target.width / target.height
        let sourceAspect = source.extent.width / source.extent.height
        let crop: CGRect
        if sourceAspect > targetAspect {
            let cropWidth = source.extent.height * targetAspect
            crop = CGRect(
                x: source.extent.midX - cropWidth / 2,
                y: source.extent.minY,
                width: cropWidth,
                height: source.extent.height
            )
        } else {
            let cropHeight = source.extent.width / targetAspect
            crop = CGRect(
                x: source.extent.minX,
                y: source.extent.midY - cropHeight / 2,
                width: source.extent.width,
                height: cropHeight
            )
        }

        let translated = source
            .cropped(to: crop)
            .transformed(
                by: CGAffineTransform(
                    translationX: -crop.minX,
                    y: -crop.minY
                )
            )
        let scale = target.width / crop.width
        let scaled = translated.transformed(
            by: CGAffineTransform(scaleX: scale, y: scale),
            highQualityDownsample: scale < 1
        )
        let bounds = CGRect(origin: .zero, size: target)
        let sRGB = CGColorSpace(name: CGColorSpace.sRGB)

        guard
            let cgImage = context.createCGImage(
                scaled,
                from: bounds,
                format: .RGBA8,
                colorSpace: sRGB
            ),
            let providerData = cgImage.dataProvider?.data
        else {
            return nil
        }

        return RenderedRGBA(
            width: cgImage.width,
            height: cgImage.height,
            bytes: Array(providerData as Data),
            bytesPerRow: cgImage.bytesPerRow,
            cgImage: cgImage
        )
    }

    /// Converts rendered RGBA pixel bytes into a `PixelPlane` data structure containing CIELAB points and FNV-1a fingerprint.
    ///
    /// - Parameters:
    ///   - image: The rendered 8-bit RGBA image buffer.
    ///   - sourceSize: Native unscaled image dimensions.
    /// - Returns: A materialized `PixelPlane` ready for weighting and clustering.
    /// - Throws: `JoiError.pixelExtractionFailed` if pixel data is incomplete.
    func makePixelPlane(
        _ image: RenderedRGBA,
        sourceSize: CGSize
    ) throws -> PixelPlane {
        let count = image.width * image.height
        guard
            count > 0,
            image.bytes.count >= image.bytesPerRow * image.height
        else {
            throw JoiError.pixelExtractionFailed
        }

        var points = [LABPoint]()
        var alpha = [Float]()
        points.reserveCapacity(count)
        alpha.reserveCapacity(count)
        var fingerprint = fnvOffsetBasis

        for y in 0..<image.height {
            let rowStart = y * image.bytesPerRow
            for x in 0..<image.width {
                let byteIndex = rowStart + x * 4
                let alphaValue = Float(image.bytes[byteIndex + 3]) / 255
                if alphaValue >= 0.999 {
                    points.append(
                        rgb8ToLAB(
                            red: image.bytes[byteIndex],
                            green: image.bytes[byteIndex + 1],
                            blue: image.bytes[byteIndex + 2]
                        )
                    )
                } else {
                    let divisor = max(alphaValue, 1 / 255)
                    let red = clamp(
                        Float(image.bytes[byteIndex]) / 255 / divisor,
                        minimum: 0,
                        maximum: 1
                    )
                    let green = clamp(
                        Float(image.bytes[byteIndex + 1]) / 255 / divisor,
                        minimum: 0,
                        maximum: 1
                    )
                    let blue = clamp(
                        Float(image.bytes[byteIndex + 2]) / 255 / divisor,
                        minimum: 0,
                        maximum: 1
                    )
                    points.append(
                        rgbToLAB(
                            red: red,
                            green: green,
                            blue: blue
                        )
                    )
                }
                alpha.append(alphaValue < 0.02 ? 0 : alphaValue)

                fingerprint = fnvMix(
                    fingerprint,
                    image.bytes[byteIndex]
                )
                fingerprint = fnvMix(
                    fingerprint,
                    image.bytes[byteIndex + 1]
                )
                fingerprint = fnvMix(
                    fingerprint,
                    image.bytes[byteIndex + 2]
                )
                fingerprint = fnvMix(
                    fingerprint,
                    image.bytes[byteIndex + 3]
                )
            }
        }

        return PixelPlane(
            width: image.width,
            height: image.height,
            sourceWidth: Int(sourceSize.width),
            sourceHeight: Int(sourceSize.height),
            points: points,
            alpha: alpha,
            fingerprint: fingerprint
        )
    }

    /// Decodes raw 8-bit RGBA bytes into an array of normalized `[0, 1]` RGB floats with alpha unpremultiplication.
    ///
    /// - Parameter image: The rendered RGBA image buffer.
    /// - Returns: Interleaved float array `[r0, g0, b0, r1, g1, b1, ...]`.
    /// - Throws: `JoiError.pixelExtractionFailed` if buffer size is insufficient.
    func decodedRGB(_ image: RenderedRGBA) throws -> [Float] {
        guard
            image.bytes.count >= image.bytesPerRow * image.height
        else {
            throw JoiError.pixelExtractionFailed
        }

        var result = [Float]()
        result.reserveCapacity(image.width * image.height * 3)
        for y in 0..<image.height {
            let rowStart = y * image.bytesPerRow
            for x in 0..<image.width {
                let index = rowStart + x * 4
                let alpha = Float(image.bytes[index + 3]) / 255
                let divisor = max(alpha, 1 / 255)
                result.append(
                    clamp(
                        Float(image.bytes[index]) / 255 / divisor,
                        minimum: 0,
                        maximum: 1
                    )
                )
                result.append(
                    clamp(
                        Float(image.bytes[index + 1]) / 255 / divisor,
                        minimum: 0,
                        maximum: 1
                    )
                )
                result.append(
                    clamp(
                        Float(image.bytes[index + 2]) / 255 / divisor,
                        minimum: 0,
                        maximum: 1
                    )
                )
            }
        }
        return result
    }
}



