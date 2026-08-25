import CoreImage
import Metal
import UIKit
import Vision

// MARK: - Semantic Color Roles

extension JoiColorAnalysis {
    /// Selects primary semantic color roles (background, secondary, foreground typography, and accent) from clustered evidence.
    ///
    /// - Parameters:
    ///   - clusters: Atmospheric clusters produced by K-Means fitting.
    ///   - identityClusters: Preserved fine-grained semantic identity clusters.
    /// - Returns: A `RoleSelection` structure containing assigned semantic roles.
    func selectRoles(
        clusters: [Cluster],
        identityClusters: [Cluster]
    ) -> RoleSelection {
        let largestPopulation = clusters.map(\.populationShare).max() ?? 1
        let supportFloor = max(0.025, largestPopulation * 0.09)
        let eligibleBackgrounds = clusters.filter {
            $0.populationShare >= supportFloor
        }
        let backgroundCandidates = eligibleBackgrounds.isEmpty
            ? clusters
            : eligibleBackgrounds
        let background = backgroundCandidates.max { first, second in
            backgroundScore(first) < backgroundScore(second)
        } ?? clusters[0]

        let remaining = clusters.filter {
            $0.stableID != background.stableID
        }
        let secondary = remaining.max {
            $0.weightedShare < $1.weightedShare
        } ?? background
        let semanticCandidates = artworkClusters(
            atmosphere: remaining,
            identities: identityClusters
        )
        .filter { candidate in
            candidate.stableID != background.stableID
                && candidate.color.deltaE(
                    to: background.color,
                    using: .cie94
                ) >= 5
        }
        let textCandidates = semanticCandidates.filter {
            hasDetectedTextEvidence($0)
        }
        let foregroundCandidates = textCandidates.isEmpty
            ? remaining.filter {
                hasIdentityEvidence($0)
            }
            : textCandidates
        let foreground = foregroundCandidates
            .max { first, second in
                foregroundScore(
                    first,
                    background: background
                ) < foregroundScore(
                    second,
                    background: background
                )
            }
            .flatMap { candidate in
                let score = foregroundScore(
                    candidate,
                    background: background
                )
                let threshold: Float = hasDetectedTextEvidence(candidate)
                    ? 0.34
                    : 0.29
                return score >= threshold
                    ? candidate
                    : nil
            }

        let accentCandidates = semanticCandidates.filter {
            hasIdentityEvidence($0)
        }
        let distinctAccentCandidates: [Cluster]
        if let foreground {
            let candidates = accentCandidates.filter {
                $0.color.deltaE(
                    to: foreground.color,
                    using: .cie94
                ) >= 10
            }
            distinctAccentCandidates = candidates.isEmpty
                ? accentCandidates
                : candidates
        } else {
            distinctAccentCandidates = accentCandidates
        }
        let backgroundLCh = background.color.toLCh()
        let hueDistinctAccentCandidates: [Cluster] = backgroundLCh.c < 12
            ? []
            : distinctAccentCandidates.filter {
                let source = $0.color.toLCh()
                return source.c >= 14
                    && hueDistance(source.h, backgroundLCh.h) >= 28
            }
        let preferredAccentCandidates = hueDistinctAccentCandidates.isEmpty
            ? distinctAccentCandidates
            : hueDistinctAccentCandidates
        let accent = preferredAccentCandidates
            .max { first, second in
                accentScore(
                    first,
                    background: background
                ) < accentScore(
                    second,
                    background: background
                )
            }

        return RoleSelection(
            background: background,
            secondary: secondary,
            foreground: foreground,
            accent: accent,
            identityClusters: identityClusters
        )
    }

    /// Merges atmospheric and identity clusters, filtering out duplicates within 3.5 CIE94 ΔE.
    ///
    /// - Parameters:
    ///   - atmosphere: Atmospheric clusters.
    ///   - identities: Discovered identity clusters.
    /// - Returns: Unified array of deduplicated artwork clusters.
    func artworkClusters(
        atmosphere: [Cluster],
        identities: [Cluster]
    ) -> [Cluster] {
        var result = identities
        result.reserveCapacity(identities.count + atmosphere.count)

        for cluster in atmosphere {
            let isAlreadyRepresented = result.contains { candidate in
                candidate.stableID == cluster.stableID
                    || candidate.color.deltaE(
                        to: cluster.color,
                        using: .cie94
                    ) < 3.5
            }
            if !isAlreadyRepresented {
                result.append(cluster)
            }
        }
        return result
    }

    /// Combines atmospheric clusters with unique identity clusters for presentation in public palettes and mesh gradients.
    ///
    /// - Parameters:
    ///   - atmosphere: Atmospheric clusters.
    ///   - identities: Identity clusters.
    /// - Returns: Presentation clusters deduplicated at 4.5 CIE94 ΔE threshold.
    func presentationClusters(
        atmosphere: [Cluster],
        identities: [Cluster]
    ) -> [Cluster] {
        var result = atmosphere
        result.reserveCapacity(atmosphere.count + identities.count)

        for cluster in identities {
            let isAlreadyRepresented = result.contains { candidate in
                candidate.color.deltaE(
                    to: cluster.color,
                    using: .cie94
                ) < 4.5
            }
            if !isAlreadyRepresented {
                result.append(cluster)
            }
        }
        return result
    }

    /// Determines if a cluster demonstrates sufficient statistical affinity with Vision-detected text regions and local edge contrast.
    ///
    /// - Parameter cluster: The cluster to inspect.
    /// - Returns: `true` if text evidence criteria are met; otherwise `false`.
    func hasDetectedTextEvidence(_ cluster: Cluster) -> Bool {
        let requiredAffinity: Float = cluster.chroma < 10
            ? 0.48
            : 0.30
        return cluster.populationShare >= 0.000_6
            && cluster.textAffinity >= requiredAffinity
    }

    /// Evaluates whether a cluster represents an intentional chromatic artwork identity element.
    ///
    /// Requires genuine distance from the neutral axis (Chroma >= 14) and verified population or saliency support,
    /// preventing compression artifacts on grayscale covers from being mistakenly selected as colors.
    ///
    /// - Parameter cluster: The cluster to evaluate.
    /// - Returns: `true` if identity evidence requirements are satisfied; otherwise `false`.
    func hasIdentityEvidence(_ cluster: Cluster) -> Bool {
        guard cluster.chroma >= 14 else {
            return false
        }
        let strongChroma = smoothStep(
            edge0: 20,
            edge1: 42,
            value: cluster.chroma
        )
        let populationRequirement = 0.020 - strongChroma * 0.016_5
        let textRequirement = 0.10 - strongChroma * 0.065
        return cluster.populationShare >= populationRequirement
            || cluster.textAffinity >= textRequirement
            || (
                cluster.populationShare >= populationRequirement * 0.55
                    && cluster.meanSaliency >= 0.58
            )
    }

    /// Computes the suitability score of a cluster for the primary background role.
    ///
    /// Balances weighted spatial presence, raw population, moderate chromatic support, and applies penalties for extreme lightness or excessive saturation.
    ///
    /// - Parameter cluster: Candidate cluster.
    /// - Returns: Background suitability score.
    func backgroundScore(_ cluster: Cluster) -> Float {
        let chromaUtility = smoothStep(
            edge0: 10,
            edge1: 42,
            value: cluster.chroma
        )
        let chromaticSupport = smoothStep(
            edge0: 0.025,
            edge1: 0.18,
            value: cluster.populationShare
        )
        let overlyVividPenalty = max(0, cluster.chroma - 75) / 100
        let darknessTieBreak = (
            1 - clamp(
                cluster.point.l / 100,
                minimum: 0,
                maximum: 1
            )
        ) * 0.012
        let extremeLightnessPenalty: Float
        if cluster.point.l < 3 || cluster.point.l > 98 {
            extremeLightnessPenalty = 0.035
        } else {
            extremeLightnessPenalty = 0
        }
        return cluster.weightedShare * 0.58
            + cluster.populationShare * 0.34
            + chromaUtility * chromaticSupport * 0.04
            + cluster.meanSaliency * 0.02
            + darknessTieBreak
            - overlyVividPenalty * 0.08
            - extremeLightnessPenalty
    }

    /// Computes the suitability score of a cluster for the primary foreground typography role relative to the background.
    ///
    /// - Parameters:
    ///   - cluster: Candidate cluster.
    ///   - background: The selected background cluster.
    /// - Returns: Foreground suitability score.
    func foregroundScore(
        _ cluster: Cluster,
        background: Cluster
    ) -> Float {
        let delta = Float(
            cluster.color.deltaE(
                to: background.color,
                using: .ciede2000
            )
        )
        let difference = smoothStep(edge0: 10, edge1: 42, value: delta)
        let chromaUtility = smoothStep(
            edge0: 8,
            edge1: 42,
            value: cluster.chroma
        )
        let support = smoothStep(
            edge0: 0.006,
            edge1: 0.12,
            value: cluster.populationShare
        )
        let textEvidence = smoothStep(
            edge0: 0.16,
            edge1: 0.72,
            value: cluster.textAffinity
        )
        guard hasDetectedTextEvidence(cluster) else {
            return cluster.textAffinity * 0.35
                + difference * 0.25
                + chromaUtility * 0.24
                + support * 0.10
                + cluster.meanSaliency * 0.06
        }
        return textEvidence * 0.46
            + difference * 0.24
            + chromaUtility * 0.14
            + support * 0.08
            + cluster.meanSaliency * 0.08
    }

    /// Computes the suitability score of a cluster for the interactive accent role relative to the background.
    ///
    /// - Parameters:
    ///   - cluster: Candidate cluster.
    ///   - background: The selected background cluster.
    /// - Returns: Accent suitability score.
    func accentScore(
        _ cluster: Cluster,
        background: Cluster
    ) -> Float {
        let delta = Float(
            cluster.color.deltaE(
                to: background.color,
                using: .ciede2000
            )
        )
        let difference = smoothStep(edge0: 12, edge1: 48, value: delta)
        let chromaUtility = smoothStep(
            edge0: 10,
            edge1: 48,
            value: cluster.chroma
        )
        let support = smoothStep(
            edge0: 0.006,
            edge1: 0.14,
            value: cluster.populationShare
        )
        return difference * 0.32
            + chromaUtility * 0.32
            + support * 0.20
            + cluster.meanSaliency * 0.10
            + cluster.textAffinity * 0.06
    }
}


