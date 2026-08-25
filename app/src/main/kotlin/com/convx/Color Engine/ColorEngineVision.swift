import CoreImage
import Metal
import UIKit
import Vision

// MARK: - Vision

extension JoiColorAnalysis {
    /// Executes asynchronous Apple Vision framework feature detection requests on the rendered image buffer.
    ///
    /// Evaluates attention-based saliency, text bounding boxes, human face rectangles, and optional objectness saliency depending on `mode`.
    ///
    /// - Parameters:
    ///   - renderedImage: The downsampled, sRGB-rendered pixel buffer.
    ///   - mode: Vision fidelity mode (`.disabled`, `.balanced`, or `.detailed`).
    /// - Returns: Extracted `VisionData` or `nil` if Vision is disabled or encounters an unrecoverable failure.
    func runVision(
        renderedImage: RenderedRGBA,
        mode: JoiColorVisionMode
    ) async -> VisionData? {
        guard mode != .disabled else {
            return nil
        }

        let handler = ImageRequestHandler(renderedImage.cgImage)
        let attentionRequest =
            GenerateAttentionBasedSaliencyImageRequest()
        var textRequest = DetectTextRectanglesRequest()
        textRequest.reportCharacterBoxes = mode == .detailed
        let faceRequest = DetectFaceRectanglesRequest()
        let attention: Vision.SaliencyImageObservation
        let objectness: Vision.SaliencyImageObservation?
        let text: [Vision.TextObservation]
        let faces: [Vision.FaceObservation]

        do {
            if mode == .detailed {
                let objectnessRequest =
                    GenerateObjectnessBasedSaliencyImageRequest()
                let results = try await handler.perform(
                    attentionRequest,
                    textRequest,
                    objectnessRequest,
                    faceRequest
                )
                attention = results.0
                text = results.1
                objectness = results.2
                faces = results.3
            } else {
                let results = try await handler.perform(
                    attentionRequest,
                    textRequest,
                    faceRequest
                )
                attention = results.0
                text = results.1
                objectness = nil
                faces = results.2
            }
        } catch {
            return nil
        }

        var saliencyObservations = [attention]
        if let objectness {
            saliencyObservations.append(objectness)
        }
        let saliency = makeSaliencyMap(
            observations: saliencyObservations,
            width: renderedImage.width,
            height: renderedImage.height
        )
        let textRegions = text.flatMap { observation -> [CGRect] in
            guard
                mode == .detailed,
                let characterBoxes = observation.characterBoxes,
                !characterBoxes.isEmpty
            else {
                return [observation.boundingBox.cgRect]
            }
            return characterBoxes.map(\.boundingBox.cgRect)
        }
        let faceRegions = faces.map(\.boundingBox.cgRect)
        let salientRegions = saliencyObservations.flatMap { observation in
            observation.salientObjects.map(\.boundingBox.cgRect)
        }

        return VisionData(
            saliencyMap: saliency,
            textRegions: textRegions,
            faceRegions: faceRegions,
            salientRegions: salientRegions
        )
    }

    /// Renders Vision framework saliency heatmaps into a normalized Float grid matching the pixel plane dimensions.
    ///
    /// - Parameters:
    ///   - observations: Saliency observations produced by Vision requests.
    ///   - width: Destination grid width in pixels.
    ///   - height: Destination grid height in pixels.
    /// - Returns: A 1D float array of normalized saliency weights in range `0...1`.
    func makeSaliencyMap(
        observations: [Vision.SaliencyImageObservation],
        width: Int,
        height: Int
    ) -> [Float] {
        guard !observations.isEmpty else {
            return [Float](repeating: 0.5, count: width * height)
        }

        var combined = [Float](repeating: 0, count: width * height)
        var renderedCount = 0

        for observation in observations {
            guard let heatMap = try? observation.heatMap.cgImage else {
                continue
            }
            let image = CIImage(cgImage: heatMap)
            guard let rendered = render(image, width: width, height: height),
                  let rgb = try? decodedRGB(rendered)
            else {
                continue
            }

            renderedCount += 1
            for index in 0..<(width * height) {
                let value = (
                    rgb[index * 3]
                        + rgb[index * 3 + 1]
                        + rgb[index * 3 + 2]
                ) / 3
                combined[index] = max(combined[index], value)
            }
        }

        guard renderedCount > 0 else {
            return [Float](repeating: 0.5, count: width * height)
        }
        return normalized(combined, fallback: 0.5)
    }
}



