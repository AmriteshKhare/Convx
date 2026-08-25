import CoreImage
import Metal
import UIKit
import Vision

// MARK: - Mesh

extension JoiColorAnalysis {
    /// Selects up to 6 authentic, distinct artwork colors in CIELCh space for multi-stop mesh gradient rendering.
    ///
    /// Preserves genuine observed pixel medoids from prominent hue families rather than computing synthetic averages.
    /// Dedicated slots are reserved for high-contrast secondary identity colors and supported neutral families.
    ///
    /// - Parameters:
    ///   - clusters: All clustered color candidates.
    ///   - roles: Semantic color roles previously selected for the image.
    ///   - statistics: Luminance tone statistics for the image.
    ///   - style: The presentation styling profile.
    /// - Returns: An array of up to 6 `LChColor` instances representing mesh color sources.
    func makeMeshSourceColors(
        clusters: [Cluster],
        roles: RoleSelection,
        statistics: ToneStatistics,
        style: JoiColorStyleProfile
    ) -> [LChColor] {
        let allClusters = presentationClusters(
            atmosphere: clusters,
            identities: roles.identityClusters
        )
        let accentID = roles.accent?.stableID
        let atmosphereClusters = allClusters
            .filter {
                $0.populationShare >= 0.006
                    || $0.weightedShare >= 0.010
                    || (
                        hasIdentityEvidence($0)
                            && (
                                $0.populationShare >= 0.003
                                    || $0.meanSaliency >= 0.55
                                    || $0.stableID == accentID
                            )
                    )
            }
            .sorted { first, second in
                let firstScore = meshScore(first)
                let secondScore = meshScore(second)
                if abs(firstScore - secondScore) > 0.000_001 {
                    return firstScore > secondScore
                }
                return first.stableID < second.stableID
            }
        let chromaticClusters = atmosphereClusters.filter {
            $0.chroma >= 12
        }
        let base = roles.background.color.toLCh()

        guard chromaticClusters.contains(where: { cluster in
            hasIdentityEvidence(cluster)
        }) else {
            return neutralMesh(
                clusters: atmosphereClusters,
                base: base,
                tone: statistics.tone
            )
        }

        let families = makeMeshHueFamilies(
            clusters: chromaticClusters
        )
        guard let strongestFamily = families.first else {
            return neutralMesh(
                clusters: atmosphereClusters,
                base: base,
                tone: statistics.tone
            )
        }

        let populationFloor = max(
            0.008,
            strongestFamily.populationSupport * 0.07
        )
        let weightedFloor = max(
            0.010,
            strongestFamily.weightedSupport * 0.07
        )
        var selectedFamilies = families.filter {
            $0.populationSupport >= populationFloor
                || $0.weightedSupport >= weightedFloor
                || $0.members.contains(where: {
                    $0.stableID == accentID
                })
        }
        if selectedFamilies.isEmpty {
            selectedFamilies = [strongestFamily]
        }

        func contrastFamilyScore(_ family: MeshHueFamily) -> Float {
            let difference = Float(
                family.representative.color.deltaE(
                    to: strongestFamily.representative.color,
                    using: .ciede2000
                )
            )
            let differenceUtility = smoothStep(
                edge0: 16,
                edge1: 48,
                value: difference
            )
            let chromaUtility = smoothStep(
                edge0: 14,
                edge1: 48,
                value: family.representative.chroma
            )
            let support = smoothStep(
                edge0: 0.003,
                edge1: 0.05,
                value: family.populationSupport
            )
            return differenceUtility * 0.56
                + chromaUtility * 0.28
                + support * 0.12
                + family.representative.meanSaliency * 0.04
        }

        // Preserve a high-contrast authentic identity color that clearly diverges from the dominant hue as the primary secondary mesh source.
        let contrastFamily = families.dropFirst()
            .filter { family in
                family.members.contains(where: {
                    hasIdentityEvidence($0)
                })
                    && family.representative.color.deltaE(
                        to: strongestFamily.representative.color,
                        using: .ciede2000
                    ) >= 16
            }
            .max {
                contrastFamilyScore($0) < contrastFamilyScore($1)
            }
        if let contrastFamily {
            selectedFamilies.removeAll {
                $0.representative.stableID
                    == contrastFamily.representative.stableID
            }
            selectedFamilies.insert(
                contrastFamily,
                at: min(1, selectedFamilies.count)
            )
        }

        // Broadly supported black, white, or gray neutral regions are genuine artwork elements. Reserve a slot
        // to prevent them from being displaced by minor tonal variations of the same chromatic family.
        let supportedNeutral = atmosphereClusters
            .filter({
                $0.chroma < 10
                    && (
                        $0.populationShare >= 0.10
                            || $0.weightedShare >= 0.12
                    )
            })
            .max(by: {
                meshScore($0) < meshScore($1)
            })
        let chromaticColorLimit = supportedNeutral == nil ? 6 : 5
        var colors: [LChColor] = []

        func appendIfDistinct(
            _ source: LChColor,
            minimumDelta: CGFloat,
            minimumChromaRetention: CGFloat
        ) {
            let candidate = meshColor(
                source: source,
                style: style
            )
            let intendedChroma = source.c < 8
                ? 0
                : min(84, source.c * style.meshChroma)
            guard intendedChroma < 18
                || candidate.c / intendedChroma >= minimumChromaRetention
            else {
                return
            }
            let isDistinct = colors.allSatisfy {
                $0.toLAB().deltaE(
                    to: candidate.toLAB(),
                    using: .cie94
                ) >= minimumDelta
            }
            if isDistinct {
                colors.append(candidate)
            }
        }

        // Every hue family is represented directly by its strongest medoid, avoiding muddy intermediate averages.
        for family in selectedFamilies {
            appendIfDistinct(
                family.representative.color.toLCh(),
                minimumDelta: 8,
                minimumChromaRetention: 0
            )
            if colors.count == chromaticColorLimit {
                break
            }
        }

        if let neutral = supportedNeutral {
            appendIfDistinct(
                neutral.color.toLCh().withChroma(0),
                minimumDelta: 5,
                minimumChromaRetention: 0
            )
        }

        // Leverage authentic intra-family tonal and chroma variations before generating synthetic variants.
        if colors.count < 6 {
            for family in selectedFamilies {
                let members = family.members.sorted { first, second in
                    let firstScore = meshScore(first)
                    let secondScore = meshScore(second)
                    if abs(firstScore - secondScore) > 0.000_001 {
                        return firstScore > secondScore
                    }
                    return first.stableID < second.stableID
                }
                for member in members
                where member.stableID
                    != family.representative.stableID {
                    appendIfDistinct(
                        member.color.toLCh(),
                        minimumDelta: 8,
                        minimumChromaRetention: 0
                    )
                    if colors.count == 6 {
                        break
                    }
                }
                if colors.count == 6 {
                    break
                }
            }
        }

        // Mesh gradient rendering frameworks require at least 3 control points. For single-family artworks,
        // generate controlled lightness and chroma variations along the observed hue axis.
        let identityFamily = selectedFamilies[0]
        let identity = identityFamily.representative.color.toLCh()
        let observedLightness = identityFamily.members.map {
            CGFloat($0.point.l)
        }
        let lowerObserved = observedLightness.min() ?? identity.l
        let upperObserved = observedLightness.max() ?? identity.l
        let observedSpan = upperObserved - lowerObserved
        let lowerLightness = observedSpan >= 8
            ? lowerObserved
            : lightnessToward(identity.l, target: 0, fraction: 0.10)
        let upperLightness = observedSpan >= 8
            ? upperObserved
            : lightnessToward(identity.l, target: 100, fraction: 0.12)
        let variants = [
            LChColor(
                l: lowerLightness,
                c: identity.c * 0.94,
                h: identity.h
            ),
            LChColor(
                l: upperLightness,
                c: identity.c * 1.04,
                h: identity.h
            ),
            LChColor(
                l: (lowerLightness + upperLightness) / 2,
                c: identity.c,
                h: identity.h
            )
        ]
        for variant in variants where colors.count < 3 {
            appendIfDistinct(
                variant,
                minimumDelta: 6,
                minimumChromaRetention: 0.72
            )
        }
        let fallbackChromaScales: [CGFloat] = [0.82, 1, 1.12]
        while colors.count < 3 {
            let scale = fallbackChromaScales[colors.count]
            colors.append(
                meshColor(
                    source: LChColor(
                        l: identity.l,
                        c: identity.c * scale,
                        h: identity.h
                    ),
                    style: style
                )
            )
        }

        return Array(colors.prefix(6))
    }

    /// Translates source mesh colors into theme-adjusted, gamut-fitted sRGB `CGColor` gradient stops.
    ///
    /// - Parameters:
    ///   - sources: The unadjusted source LCh colors.
    ///   - background: The theme's background color in LCh.
    ///   - isDark: `true` for dark appearance themes, `false` for light themes.
    /// - Returns: Array of displayable sRGB `CGColor` instances.
    func makeSchemeMeshColors(
        sources: [LChColor],
        background: LChColor,
        isDark: Bool
    ) -> [CGColor] {
        guard !sources.isEmpty else {
            return [background.toCGColor()]
        }

        let minimumSourceLightness = sources.map(\.l).min()
            ?? background.l
        let maximumSourceLightness = sources.map(\.l).max()
            ?? background.l
        let sourceSpan = maximumSourceLightness
            - minimumSourceLightness
        let lowerLightness = lightnessToward(
            background.l,
            target: 0,
            fraction: isDark ? 0.28 : 0.23
        )
        let upperLightness = lightnessToward(
            background.l,
            target: 100,
            fraction: isDark ? 0.25 : 0.18
        )
        let maximumSourceChroma = sources.map(\.c).max() ?? 0
        let lightNearWhite = isDark
            ? Float(0)
            : smoothStep(
                edge0: 82,
                edge1: 92,
                value: Float(background.l)
            )
        let restrainedPalette = 1 - smoothStep(
            edge0: 22,
            edge1: 42,
            value: Float(maximumSourceChroma)
        )
        let lightChromaCompensation = 1
            + CGFloat(lightNearWhite * restrainedPalette) * 0.12
        let themeChromaScale: CGFloat = isDark
            ? 0.94
            : 0.85 * lightChromaCompensation
        let themeChromaLimit = max(
            isDark ? 44 : 40,
            background.c * 1.20 + 18
        )
        let sourceCount = sources.count

        return sources.enumerated().map { index, source in
            let tonalPosition: CGFloat
            if sourceSpan >= 6 {
                tonalPosition = clamp(
                    (source.l - minimumSourceLightness) / sourceSpan,
                    minimum: 0,
                    maximum: 1
                )
            } else if sourceCount > 1 {
                tonalPosition = CGFloat(index)
                    / CGFloat(sourceCount - 1)
            } else {
                tonalPosition = 0.5
            }

            let lightness = lowerLightness
                + (upperLightness - lowerLightness) * tonalPosition
            let tonalDistance = abs(tonalPosition - 0.5) * 2
            let chromaVariation = 0.90 + tonalDistance * 0.14
            let hueSeparation: Float
            if background.c < 8 {
                hueSeparation = source.c >= 12 ? 1 : 0
            } else {
                hueSeparation = smoothStep(
                    edge0: 18,
                    edge1: 72,
                    value: Float(
                        hueDistance(source.h, background.h)
                    )
                )
            }
            let identityChromaScale = 1
                + CGFloat(hueSeparation) * 0.18
            let identityChromaLimit = themeChromaLimit
                + CGFloat(hueSeparation) * 6
            let chroma = source.c < 8
                ? 0
                : min(
                    source.c
                        * themeChromaScale
                        * chromaVariation
                        * identityChromaScale,
                    identityChromaLimit
                )
            return fitToSRGBGamut(
                LChColor(
                    l: lightness,
                    c: chroma,
                    h: source.h
                )
            ).toCGColor()
        }
    }

    /// Partitions candidate clusters into distinct hue families (within a 24° angular threshold).
    ///
    /// - Parameter clusters: The chromatic clusters to partition.
    /// - Returns: An array of `MeshHueFamily` models sorted by composite support.
    func makeMeshHueFamilies(
        clusters: [Cluster]
    ) -> [MeshHueFamily] {
        var families: [MeshHueFamily] = []

        for cluster in clusters {
            let source = cluster.color.toLCh()
            if let index = families.firstIndex(where: {
                hueDistance(
                    source.h,
                    $0.representative.color.toLCh().h
                ) <= 24
            }) {
                families[index].members.append(cluster)
                families[index].weightedSupport += cluster.weightedShare
                families[index].populationSupport
                    += cluster.populationShare
            } else {
                families.append(
                    MeshHueFamily(
                        representative: cluster,
                        members: [cluster],
                        weightedSupport: cluster.weightedShare,
                        populationSupport: cluster.populationShare
                    )
                )
            }
        }

        return families.sorted { first, second in
            let firstSupport = meshFamilySupport(first)
            let secondSupport = meshFamilySupport(second)
            if abs(firstSupport - secondSupport) > 0.000_001 {
                return firstSupport > secondSupport
            }
            return first.representative.stableID
                < second.representative.stableID
        }
    }

    /// Evaluates the total support score for a mesh hue family combining weighted background and raw population shares.
    ///
    /// - Parameter family: The hue family to score.
    /// - Returns: Composite support score.
    func meshFamilySupport(_ family: MeshHueFamily) -> Float {
        family.weightedSupport * 0.56
            + family.populationSupport * 0.44
    }

    /// Calculates the shortest angular distance between two hue angles on the 360° color circle.
    ///
    /// - Parameters:
    ///   - first: First hue angle in degrees.
    ///   - second: Second hue angle in degrees.
    /// - Returns: Shortest angular difference in range `0...180`.
    func hueDistance(_ first: CGFloat, _ second: CGFloat) -> CGFloat {
        let direct = abs(first - second)
            .truncatingRemainder(dividingBy: 360)
        return min(direct, 360 - direct)
    }

    /// Applies style-specific chroma scaling and clamps lightness and gamut bounds for a single mesh color.
    ///
    /// - Parameters:
    ///   - source: The input LCh color.
    ///   - style: The presentation style profile.
    /// - Returns: The adjusted in-gamut `LChColor`.
    func meshColor(
        source: LChColor,
        style: JoiColorStyleProfile
    ) -> LChColor {
        let chroma = source.c < 8
            ? 0
            : min(84, source.c * style.meshChroma)
        return fitToSRGBGamut(
            LChColor(
                l: clamp(source.l, minimum: 2, maximum: 98),
                c: chroma,
                h: source.h
            )
        )
    }

    /// Computes the fitness score of a cluster for inclusion in mesh gradient generation.
    ///
    /// - Parameter cluster: The candidate cluster.
    /// - Returns: Fitness score weighting spatial share, population share, chroma, and saliency.
    func meshScore(_ cluster: Cluster) -> Float {
        let chromaUtility = smoothStep(
            edge0: 8,
            edge1: 42,
            value: cluster.chroma
        )
        let populationSupport = smoothStep(
            edge0: 0.008,
            edge1: 0.18,
            value: cluster.populationShare
        )
        return cluster.weightedShare * 0.42
            + populationSupport * 0.30
            + chromaUtility * populationSupport * 0.18
            + cluster.meanSaliency * 0.10
    }

    /// Generates monochromatic lightness stops for artworks lacking chromatic identity colors.
    ///
    /// - Parameters:
    ///   - clusters: Available neutral clusters.
    ///   - base: Background LCh color.
    ///   - tone: Perceptual image tone (.dark or .light).
    /// - Returns: Array of neutral achromatic `LChColor` stops.
    func neutralMesh(
        clusters: [Cluster],
        base: LChColor,
        tone: ImageTone
    ) -> [LChColor] {
        let neutralClusters = clusters
            .filter { $0.chroma < 10 }
            .sorted { first, second in
                let firstScore = meshScore(first)
                let secondScore = meshScore(second)
                if abs(firstScore - secondScore) > 0.000_001 {
                    return firstScore > secondScore
                }
                return first.stableID < second.stableID
            }
        var colors: [LChColor] = []

        for cluster in neutralClusters {
            let candidate = LChColor(
                l: CGFloat(cluster.point.l),
                c: 0,
                h: 0
            )
            if colors.allSatisfy({ abs($0.l - candidate.l) >= 7 }) {
                colors.append(candidate)
            }
            if colors.count == 3 {
                break
            }
        }

        let tonalBase = colors.first?.l ?? base.l
        let firstTarget = lightnessToward(
            tonalBase,
            target: tone == .dark ? 100 : 0,
            fraction: 0.16
        )
        let secondTarget = lightnessToward(
            tonalBase,
            target: tone == .dark ? 0 : 100,
            fraction: 0.16
        )
        for lightness in [firstTarget, secondTarget] where colors.count < 3 {
            let candidate = LChColor(
                l: lightness,
                c: 0,
                h: 0
            )
            if colors.allSatisfy({ abs($0.l - candidate.l) >= 5 }) {
                colors.append(candidate)
            }
        }
        while colors.count < 3 {
            let fraction = CGFloat(colors.count + 1) / 4
            colors.append(
                LChColor(
                    l: lightnessToward(
                        tonalBase,
                        target: tone == .dark ? 100 : 0,
                        fraction: fraction * 0.24
                    ),
                    c: 0,
                    h: 0
                )
            )
        }
        return colors
    }
}

