import CoreImage
import Metal
import UIKit
import Vision

// MARK: - Deterministic K-Means

extension JoiColorAnalysis {
    /// Executes deterministic weighted K-Means clustering in CIELAB color space.
    ///
    /// The algorithm operates on pre-quantized color samples rather than raw pixels to guarantee speed and numerical stability.
    /// Centroid updates converge in up to 8 iterations, with an early exit if centroid movement falls below 0.75 ΔE.
    ///
    /// - Parameters:
    ///   - plane: The extracted pixel plane containing CIELAB coordinates and alpha channels.
    ///   - weightMap: Spatial, saliency, and text weighting maps for every sample.
    ///   - requestedCount: The target number of clusters requested based on detail assessment.
    /// - Returns: A `KMeansOutput` structure containing merged clusters, identity clusters, and iteration metrics.
    func deterministicKMeans(
        plane: PixelPlane,
        weightMap: WeightMap,
        clusterCount requestedCount: Int
    ) -> KMeansOutput {
        let samples = quantizedSamples(
            plane: plane,
            weightMap: weightMap
        )
        guard !samples.isEmpty else {
            return KMeansOutput(
                clusters: [],
                identityClusters: [],
                iterations: 0,
                quantizedSampleCount: 0,
                fittingSampleCount: 0
            )
        }
        let identityClusters = makeIdentityClusters(from: samples)

        let fittingSamples = clusteringRepresentatives(
            from: samples,
            maximumCount: 1_024
        )
        let count = min(requestedCount, fittingSamples.count)
        var generator = SplitMix64(seed: plane.fingerprint)
        var centroids = deterministicSeeds(
            samples: fittingSamples,
            count: count,
            generator: &generator
        )
        var assignments = [Int](
            repeating: 0,
            count: fittingSamples.count
        )
        var iterations = 0

        // Perform up to 8 iterations over quantized samples. Centroid movement below 0.75 ΔE
        // produces no perceptible difference in the final palette output.
        for iteration in 0..<8 {
            iterations = iteration + 1
            var sumL = [Float](repeating: 0, count: centroids.count)
            var sumA = [Float](repeating: 0, count: centroids.count)
            var sumB = [Float](repeating: 0, count: centroids.count)
            var sumWeight = [Float](repeating: 0, count: centroids.count)

            for index in fittingSamples.indices {
                let sample = fittingSamples[index]
                let bestIndex = nearestCentroid(
                    to: sample.point,
                    centroids: centroids
                )
                assignments[index] = bestIndex
                let weight = sample.centroidWeight
                sumL[bestIndex] += sample.point.l * weight
                sumA[bestIndex] += sample.point.a * weight
                sumB[bestIndex] += sample.point.b * weight
                sumWeight[bestIndex] += weight
            }

            var maximumMovementSquared: Float = 0
            for centroidIndex in centroids.indices {
                let replacement: LABPoint
                if sumWeight[centroidIndex] > 0 {
                    replacement = LABPoint(
                        l: sumL[centroidIndex] / sumWeight[centroidIndex],
                        a: sumA[centroidIndex] / sumWeight[centroidIndex],
                        b: sumB[centroidIndex] / sumWeight[centroidIndex]
                    )
                } else {
                    replacement = farthestSample(
                        samples: fittingSamples,
                        centroids: centroids,
                        excluding: centroidIndex
                    )
                }
                maximumMovementSquared = max(
                    maximumMovementSquared,
                    centroids[centroidIndex]
                        .squaredDistance(to: replacement)
                )
                centroids[centroidIndex] = replacement
            }

            if maximumMovementSquared < 0.75 * 0.75 {
                break
            }
        }

        // Final assignment pass across all quantized samples ensures cluster metrics reflect finalized centroids.
        assignments = [Int](repeating: 0, count: samples.count)
        for index in samples.indices {
            assignments[index] = nearestCentroid(
                to: samples[index].point,
                centroids: centroids
            )
        }

        let clusters = buildClusters(
            samples: samples,
            centroids: centroids,
            assignments: assignments
        )
        return KMeansOutput(
            clusters: mergeAndSortClusters(clusters),
            identityClusters: identityClusters,
            iterations: iterations,
            quantizedSampleCount: samples.count,
            fittingSampleCount: fittingSamples.count
        )
    }

    /// Quantizes continuous CIELAB pixel values into discrete spatial-color bins.
    ///
    /// Uses a bin step of 4 for lightness (L) and 8 for opponent channels (a, b). This aggregates imperceptibly close
    /// pixels into single weighted samples without merging distinct hue families.
    ///
    /// - Parameters:
    ///   - plane: The raw pixel plane.
    ///   - weightMap: The weight map containing spatial, text, and saliency weights.
    /// - Returns: An array of quantized color samples with accumulated weights and representative medoids.
    func quantizedSamples(
        plane: PixelPlane,
        weightMap: WeightMap
    ) -> [QuantizedColorSample] {
        let lightnessStep: Float = 4
        let opponentStep: Float = 8
        let opponentBinCount = 32
        var accumulators: [Int: QuantizedColorAccumulator] = [:]
        accumulators.reserveCapacity(min(1_024, plane.count))

        for index in 0..<plane.count {
            let alpha = plane.alpha[index]
            guard alpha > 0 else {
                continue
            }
            let effectiveAlpha = alpha
                * weightMap.colorInfluence[index]

            let point = plane.points[index]
            let lightnessBin = clamp(
                Int(point.l / lightnessStep),
                minimum: 0,
                maximum: 25
            )
            let aBin = clamp(
                Int((point.a + 128) / opponentStep),
                minimum: 0,
                maximum: opponentBinCount - 1
            )
            let bBin = clamp(
                Int((point.b + 128) / opponentStep),
                minimum: 0,
                maximum: opponentBinCount - 1
            )
            let stableID = (lightnessBin * opponentBinCount + aBin)
                * opponentBinCount
                + bBin

            var accumulator = accumulators[stableID]
                ?? QuantizedColorAccumulator()
            accumulator.add(
                point: point,
                alpha: effectiveAlpha,
                background: weightMap.background[index],
                textMembership: weightMap.textMembership[index],
                saliency: weightMap.saliency[index],
                sampleIndex: index
            )
            accumulators[stableID] = accumulator
        }

        return accumulators.keys.sorted().compactMap { stableID in
            guard
                let accumulator = accumulators[stableID],
                accumulator.centroidWeight > 0
            else {
                return nil
            }
            return QuantizedColorSample(
                point: LABPoint(
                    l: accumulator.sumL / accumulator.centroidWeight,
                    a: accumulator.sumA / accumulator.centroidWeight,
                    b: accumulator.sumB / accumulator.centroidWeight
                ),
                representativePoint: accumulator.representativePoint,
                centroidWeight: accumulator.centroidWeight,
                backgroundWeight: accumulator.backgroundWeight,
                population: accumulator.population,
                textPopulation: accumulator.textPopulation,
                saliencyPopulation: accumulator.saliencyPopulation,
                count: accumulator.count,
                stableID: stableID
            )
        }
    }

    /// Selects a balanced subset of quantized samples to serve as inputs during K-Means fitting.
    ///
    /// Splits the budget between broad atmospheric colors (5/8) and high-chroma/saliency identity colors (3/8).
    ///
    /// - Parameters:
    ///   - samples: The full array of quantized color samples.
    ///   - maximumCount: The upper limit on fitting samples (typically 1,024).
    /// - Returns: A capped array of representative samples.
    func clusteringRepresentatives(
        from samples: [QuantizedColorSample],
        maximumCount: Int
    ) -> [QuantizedColorSample] {
        guard samples.count > maximumCount else {
            return samples
        }

        let atmosphereCount = maximumCount * 5 / 8
        let identityCount = maximumCount - atmosphereCount
        let totalBackground = max(
            samples.reduce(Float(0)) {
                $0 + $1.backgroundWeight
            },
            Float.ulpOfOne
        )
        let totalPopulation = max(
            samples.reduce(Float(0)) {
                $0 + $1.population
            },
            Float.ulpOfOne
        )

        let atmosphere = samples.sorted { first, second in
            let firstScore = first.backgroundWeight / totalBackground * 0.68
                + first.population / totalPopulation * 0.32
            let secondScore = second.backgroundWeight / totalBackground * 0.68
                + second.population / totalPopulation * 0.32
            if abs(firstScore - secondScore) > 0.000_001 {
                return firstScore > secondScore
            }
            return first.stableID < second.stableID
        }
        let identity = samples.sorted { first, second in
            let firstScore = representativeIdentityScore(
                first,
                totalPopulation: totalPopulation
            )
            let secondScore = representativeIdentityScore(
                second,
                totalPopulation: totalPopulation
            )
            if abs(firstScore - secondScore) > 0.000_001 {
                return firstScore > secondScore
            }
            return first.stableID < second.stableID
        }

        var selected: [Int: QuantizedColorSample] = [:]
        selected.reserveCapacity(maximumCount)
        for sample in atmosphere.prefix(atmosphereCount) {
            selected[sample.stableID] = sample
        }
        for sample in identity.prefix(identityCount) {
            selected[sample.stableID] = sample
        }

        if selected.count < maximumCount {
            for sample in identity where selected.count < maximumCount {
                selected[sample.stableID] = sample
            }
        }

        return selected.keys.sorted().compactMap {
            selected[$0]
        }
    }

    /// Computes a priority score for selecting identity candidates during representative downsampling.
    ///
    /// - Parameters:
    ///   - sample: The candidate quantized color sample.
    ///   - totalPopulation: Total accumulated pixel population across all samples.
    /// - Returns: A normalized priority score weighting chroma, population share, text affinity, and saliency.
    func representativeIdentityScore(
        _ sample: QuantizedColorSample,
        totalPopulation: Float
    ) -> Float {
        let population = max(sample.population, Float.ulpOfOne)
        let populationShare = population / totalPopulation
        let chromaSupport = smoothStep(
            edge0: 8,
            edge1: 42,
            value: sample.point.chroma
        )
        let populationSupport = smoothStep(
            edge0: 0.000_5,
            edge1: 0.018,
            value: populationShare
        )
        let textAffinity = sample.textPopulation / population
        let meanSaliency = sample.saliencyPopulation / population
        return chromaSupport * 0.38
            + populationSupport * 0.28
            + textAffinity * 0.20
            + meanSaliency * 0.14
    }

    /// Creates fine-grained semantic evidence clusters from quantized samples.
    ///
    /// Global K-Means works well for broad backgrounds, but small typography or contrasting logos can be absorbed
    /// into large background clusters. This method forms a dedicated semantic evidence pool preserving true pixel medoids.
    ///
    /// - Parameter samples: The quantized color samples.
    /// - Returns: An array of up to 24 merged identity clusters.
    func makeIdentityClusters(
        from samples: [QuantizedColorSample]
    ) -> [Cluster] {
        let totalBackground = max(
            samples.reduce(Float(0)) {
                $0 + $1.backgroundWeight
            },
            Float.ulpOfOne
        )
        let totalPopulation = max(
            samples.reduce(Float(0)) {
                $0 + $1.population
            },
            Float.ulpOfOne
        )

        let candidates = samples.compactMap { sample -> Cluster? in
            let population = max(sample.population, Float.ulpOfOne)
            let populationShare = population / totalPopulation
            let textAffinity = sample.textPopulation / population
            let meanSaliency = sample.saliencyPopulation / population
            let chroma = sample.representativePoint.chroma
            let chromaticEvidence = chroma >= 10
                && (
                    populationShare >= 0.001_5
                        || textAffinity >= 0.16
                        || (
                            populationShare >= 0.000_8
                                && meanSaliency >= 0.55
                        )
                )
            let neutralTextEvidence = chroma < 10
                && populationShare >= 0.000_6
                && textAffinity >= 0.42

            guard chromaticEvidence || neutralTextEvidence else {
                return nil
            }
            return Cluster(
                point: sample.representativePoint,
                weightedShare: sample.backgroundWeight / totalBackground,
                populationShare: populationShare,
                textAffinity: textAffinity,
                meanSaliency: meanSaliency,
                count: sample.count,
                stableID: sample.stableID
            )
        }
        .sorted { first, second in
            let firstScore = semanticEvidenceScore(first)
            let secondScore = semanticEvidenceScore(second)
            if abs(firstScore - secondScore) > 0.000_001 {
                return firstScore > secondScore
            }
            return first.stableID < second.stableID
        }

        return Array(
            mergeIdentityClusters(
                Array(candidates.prefix(96))
            ).prefix(24)
        )
    }

    /// Merges perceptually similar identity candidates using CIE94 color difference.
    ///
    /// Candidates within 5.5 ΔE are combined, preserving the representative point of the higher-scoring cluster.
    ///
    /// - Parameter candidates: The unmerged identity candidate clusters.
    /// - Returns: A deduplicated, sorted array of merged identity clusters.
    func mergeIdentityClusters(
        _ candidates: [Cluster]
    ) -> [Cluster] {
        var merged: [Cluster] = []
        merged.reserveCapacity(min(candidates.count, 24))

        for candidate in candidates {
            guard let index = merged.firstIndex(where: {
                $0.color.deltaE(
                    to: candidate.color,
                    using: .cie94
                ) < 5.5
            }) else {
                merged.append(candidate)
                continue
            }

            let existing = merged[index]
            let totalPopulation = existing.populationShare
                + candidate.populationShare
            let existingRatio = existing.populationShare / max(
                totalPopulation,
                Float.ulpOfOne
            )
            let candidateRatio = 1 - existingRatio
            let existingScore = semanticEvidenceScore(existing)
            let candidateScore = semanticEvidenceScore(candidate)
            let representative: Cluster
            if candidateScore > existingScore + 0.000_001 {
                representative = candidate
            } else if existingScore > candidateScore + 0.000_001 {
                representative = existing
            } else {
                representative = existing.stableID < candidate.stableID
                    ? existing
                    : candidate
            }

            merged[index] = Cluster(
                point: representative.point,
                weightedShare: existing.weightedShare
                    + candidate.weightedShare,
                populationShare: totalPopulation,
                textAffinity: existing.textAffinity * existingRatio
                    + candidate.textAffinity * candidateRatio,
                meanSaliency: existing.meanSaliency * existingRatio
                    + candidate.meanSaliency * candidateRatio,
                count: existing.count + candidate.count,
                stableID: representative.stableID
            )
        }

        return merged.sorted { first, second in
            let firstScore = semanticEvidenceScore(first)
            let secondScore = semanticEvidenceScore(second)
            if abs(firstScore - secondScore) > 0.000_001 {
                return firstScore > secondScore
            }
            return first.stableID < second.stableID
        }
    }

    /// Computes a semantic utility score evaluating how strongly a cluster represents an intentional artwork element.
    ///
    /// - Parameter cluster: The candidate cluster to evaluate.
    /// - Returns: A composite score weighting text support, chroma, population share, saliency, and atmospheric share.
    func semanticEvidenceScore(_ cluster: Cluster) -> Float {
        let chromaSupport = smoothStep(
            edge0: 8,
            edge1: 44,
            value: cluster.chroma
        )
        let populationSupport = smoothStep(
            edge0: 0.000_5,
            edge1: 0.045,
            value: cluster.populationShare
        )
        let textSupport = smoothStep(
            edge0: 0.12,
            edge1: 0.68,
            value: cluster.textAffinity
        )
        let atmosphereSupport = smoothStep(
            edge0: 0.001,
            edge1: 0.08,
            value: cluster.weightedShare
        )
        return textSupport * 0.30
            + chromaSupport * 0.25
            + populationSupport * 0.24
            + cluster.meanSaliency * 0.13
            + atmosphereSupport * 0.08
    }

    /// Initializes deterministic initial centroids using k-means++ distance-weighted probability sampling.
    ///
    /// - Parameters:
    ///   - samples: The fitting samples available for centroid seeding.
    ///   - count: The target number of centroids to initialize.
    ///   - generator: The deterministic SplitMix64 PRNG instance.
    /// - Returns: An array of initial CIELAB centroid points.
    func deterministicSeeds(
        samples: [QuantizedColorSample],
        count: Int,
        generator: inout SplitMix64
    ) -> [LABPoint] {
        let firstIndex = weightedSelection(
            scores: samples.map(\.centroidWeight),
            generator: &generator
        )
        var centroids = [samples[firstIndex].point]
        var minimumDistances = [Float](
            repeating: .greatestFiniteMagnitude,
            count: samples.count
        )

        while centroids.count < count {
            let latest = centroids[centroids.count - 1]
            var scores = [Float](repeating: 0, count: samples.count)
            var totalScore: Float = 0

            for index in samples.indices {
                let distance = samples[index].point.squaredDistance(
                    to: latest
                )
                minimumDistances[index] = min(
                    minimumDistances[index],
                    distance
                )
                let score = minimumDistances[index]
                    * samples[index].centroidWeight
                scores[index] = score
                totalScore += score
            }

            guard totalScore > Float.ulpOfOne else {
                break
            }
            let selected = weightedSelection(
                scores: scores,
                generator: &generator
            )
            centroids.append(samples[selected].point)
        }

        return centroids
    }

    /// Selects a sample index with probability proportional to its relative score.
    ///
    /// - Parameters:
    ///   - scores: Array of non-negative selection weights.
    ///   - generator: PRNG source for uniform random numbers.
    /// - Returns: The selected index.
    func weightedSelection(
        scores: [Float],
        generator: inout SplitMix64
    ) -> Int {
        var total: Float = 0
        for score in scores {
            total += max(0, score)
        }
        guard total > Float.ulpOfOne else {
            return 0
        }

        let target = Float(generator.nextUnitDouble()) * total
        var cumulative: Float = 0
        for index in scores.indices {
            cumulative += max(0, scores[index])
            if cumulative >= target {
                return index
            }
        }
        return scores.count - 1
    }

    /// Finds the index of the nearest centroid to a target CIELAB point using squared Euclidean distance.
    ///
    /// - Parameters:
    ///   - point: The target CIELAB point.
    ///   - centroids: The array of candidate centroids.
    /// - Returns: The index of the closest centroid.
    func nearestCentroid(
        to point: LABPoint,
        centroids: [LABPoint]
    ) -> Int {
        var bestIndex = 0
        var bestDistance = point.squaredDistance(to: centroids[0])
        if centroids.count > 1 {
            for index in 1..<centroids.count {
                let distance = point.squaredDistance(to: centroids[index])
                if distance < bestDistance {
                    bestDistance = distance
                    bestIndex = index
                }
            }
        }
        return bestIndex
    }

    /// Identifies the sample point furthest from all active centroids except the excluded centroid.
    ///
    /// Used as a replacement strategy when a centroid becomes orphaned during an iteration.
    ///
    /// - Parameters:
    ///   - samples: Available quantized color samples.
    ///   - centroids: Current active centroids.
    ///   - excludedCentroid: Centroid index to exclude from the minimum distance calculation.
    /// - Returns: The CIELAB coordinates of the furthest sample.
    func farthestSample(
        samples: [QuantizedColorSample],
        centroids: [LABPoint],
        excluding excludedCentroid: Int
    ) -> LABPoint {
        var bestIndex = 0
        var bestDistance: Float = -1

        for index in samples.indices {
            let point = samples[index].point
            var minimumDistance = Float.greatestFiniteMagnitude
            for centroidIndex in centroids.indices {
                guard centroidIndex != excludedCentroid else {
                    continue
                }
                minimumDistance = min(
                    minimumDistance,
                    point.squaredDistance(to: centroids[centroidIndex])
                )
            }
            let supportedDistance = minimumDistance
                * samples[index].centroidWeight
            if supportedDistance > bestDistance {
                bestDistance = supportedDistance
                bestIndex = index
            }
        }
        return samples[bestIndex].point
    }

    /// Builds structured `Cluster` instances from sample assignments and selects authentic pixel medoids.
    ///
    /// Mathematical centroids can produce desaturated intermediate hues not present in the artwork.
    /// This method maps each cluster to a nearby quantized medoid with real pixel support, keeping all semantic roles
    /// anchored to observed artwork colors.
    ///
    /// - Parameters:
    ///   - samples: All quantized color samples.
    ///   - centroids: Converged centroid points.
    ///   - assignments: Mapping of each sample to its assigned centroid index.
    /// - Returns: An array of materialized `Cluster` models.
    func buildClusters(
        samples: [QuantizedColorSample],
        centroids: [LABPoint],
        assignments: [Int]
    ) -> [Cluster] {
        var weightedPopulation = [Float](
            repeating: 0,
            count: centroids.count
        )
        var rawPopulation = [Float](
            repeating: 0,
            count: centroids.count
        )
        var textPopulation = [Float](
            repeating: 0,
            count: centroids.count
        )
        var saliencyPopulation = [Float](
            repeating: 0,
            count: centroids.count
        )
        var counts = [Int](repeating: 0, count: centroids.count)

        for index in samples.indices {
            let clusterIndex = assignments[index]
            let sample = samples[index]
            weightedPopulation[clusterIndex] += sample.backgroundWeight
            rawPopulation[clusterIndex] += sample.population
            textPopulation[clusterIndex] += sample.textPopulation
            saliencyPopulation[clusterIndex] += sample.saliencyPopulation
            counts[clusterIndex] += sample.count
        }

        let totalWeight = max(
            weightedPopulation.reduce(0, +),
            Float.ulpOfOne
        )
        let totalPopulation = max(
            rawPopulation.reduce(0, +),
            Float.ulpOfOne
        )
        var representativePoints = centroids
        var representativeIDs = [Int](
            repeating: .max,
            count: centroids.count
        )
        var representativeScores = [Float](
            repeating: -Float.greatestFiniteMagnitude,
            count: centroids.count
        )

        for sampleIndex in samples.indices {
            let clusterIndex = assignments[sampleIndex]
            let sample = samples[sampleIndex]
            let clusterWeight = max(
                weightedPopulation[clusterIndex],
                Float.ulpOfOne
            )
            let supportedPopulation = max(
                sample.backgroundWeight,
                sample.population * 0.25
            )
            let support = supportedPopulation / clusterWeight
            let centrality = 1 / (
                1
                    + sample.point.squaredDistance(
                        to: centroids[clusterIndex]
                    ) / 576
            )
            let supportScore = smoothStep(
                edge0: 0.000_5,
                edge1: 0.08,
                value: support
            )
            let score = centrality * 0.72 + supportScore * 0.28
            let currentScore = representativeScores[clusterIndex]
            let currentID = representativeIDs[clusterIndex]
            if score > currentScore + 0.000_001
                || (
                    abs(score - currentScore) <= 0.000_001
                        && sample.stableID < currentID
                ) {
                representativeScores[clusterIndex] = score
                representativePoints[clusterIndex] = sample.point
                representativeIDs[clusterIndex] = sample.stableID
            }
        }

        return centroids.indices.compactMap { index in
            guard counts[index] > 0 else {
                return nil
            }
            let raw = max(rawPopulation[index], Float.ulpOfOne)
            return Cluster(
                point: representativePoints[index],
                weightedShare: weightedPopulation[index] / totalWeight,
                populationShare: rawPopulation[index] / totalPopulation,
                textAffinity: textPopulation[index] / raw,
                meanSaliency: saliencyPopulation[index] / raw,
                count: counts[index],
                stableID: representativeIDs[index]
            )
        }
    }

    /// Combines clusters that are perceptually indistinguishable (CIE94 ΔE < 7.5) and orders the result deterministically.
    ///
    /// - Parameter input: Unmerged cluster collection.
    /// - Returns: Merged and deterministically sorted clusters.
    func mergeAndSortClusters(_ input: [Cluster]) -> [Cluster] {
        let sorted = stableClusterSort(input)
        var merged: [Cluster] = []

        for cluster in sorted {
            if let index = merged.firstIndex(where: {
                $0.color.deltaE(to: cluster.color, using: .cie94) < 7.5
            }) {
                let existing = merged[index]
                let total = existing.weightedShare + cluster.weightedShare
                let firstRatio = existing.weightedShare / max(
                    total,
                    Float.ulpOfOne
                )
                let secondRatio = 1 - firstRatio
                let representative: Cluster
                if existing.weightedShare > cluster.weightedShare {
                    representative = existing
                } else if cluster.weightedShare > existing.weightedShare {
                    representative = cluster
                } else {
                    representative = existing.stableID < cluster.stableID
                        ? existing
                        : cluster
                }
                merged[index] = Cluster(
                    point: representative.point,
                    weightedShare: total,
                    populationShare: existing.populationShare
                        + cluster.populationShare,
                    textAffinity: existing.textAffinity * firstRatio
                        + cluster.textAffinity * secondRatio,
                    meanSaliency: existing.meanSaliency * firstRatio
                        + cluster.meanSaliency * secondRatio,
                    count: existing.count + cluster.count,
                    stableID: representative.stableID
                )
            } else {
                merged.append(cluster)
            }
        }

        return stableClusterSort(merged)
    }

    /// Sorts clusters with strict deterministic tie-breaking.
    ///
    /// Order of precedence:
    /// 1. Weighted spatial share (descending)
    /// 2. Population share (descending)
    /// 3. Lightness L (ascending)
    /// 4. Opponent coordinate a (ascending)
    /// 5. Opponent coordinate b (ascending)
    /// 6. Stable ID (ascending)
    ///
    /// - Parameter clusters: The input clusters.
    /// - Returns: Deterministically ordered clusters.
    func stableClusterSort(_ clusters: [Cluster]) -> [Cluster] {
        clusters.sorted { first, second in
            if abs(first.weightedShare - second.weightedShare) > 0.000_001 {
                return first.weightedShare > second.weightedShare
            }
            if abs(first.populationShare - second.populationShare) > 0.000_001 {
                return first.populationShare > second.populationShare
            }
            if abs(first.point.l - second.point.l) > 0.000_001 {
                return first.point.l < second.point.l
            }
            if abs(first.point.a - second.point.a) > 0.000_001 {
                return first.point.a < second.point.a
            }
            if abs(first.point.b - second.point.b) > 0.000_001 {
                return first.point.b < second.point.b
            }
            return first.stableID < second.stableID
        }
    }
}



