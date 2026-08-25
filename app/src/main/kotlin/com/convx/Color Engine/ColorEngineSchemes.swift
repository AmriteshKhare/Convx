import CoreImage
import Metal
import UIKit
import Vision

// MARK: - Schemes and Palette

extension JoiColorAnalysis {
    /// Assembles a complete `JoiColorScheme` for a specified appearance (dark or light).
    ///
    /// Constructs a WCAG-compliant 4-level typography hierarchy, interactive accent, surface containers, borders,
    /// shadows, status tints, destructive colors, atmospheric linear gradients, and multi-stop mesh stops.
    ///
    /// - Parameters:
    ///   - roles: Assigned semantic color roles.
    ///   - background: Base background color in CIELCh.
    ///   - meshColors: Pre-computed displayable mesh gradient stops.
    ///   - isDark: `true` for dark appearance schemes; `false` for light schemes.
    ///   - style: The presentation styling profile.
    /// - Returns: A fully populated `JoiColorScheme`.
    func makeScheme(
        roles: RoleSelection,
        background: LChColor,
        meshColors: [CGColor],
        isDark: Bool,
        style: JoiColorStyleProfile
    ) -> JoiColorScheme {
        let backgroundLAB = background.toLAB()

        let sourceForeground = foregroundSource(
            roles: roles,
            background: background
        )
        let text = makeTextHierarchy(
            source: sourceForeground,
            background: backgroundLAB,
            isDark: isDark,
            style: style
        )

        let accent = accentColor(
            roles: roles,
            background: background,
            isDark: isDark,
            style: style
        )
        let contrastingPole: CGFloat = isDark ? 100 : 0
        let surface = fitToSRGBGamut(
            background
                .withLightness(
                    lightnessToward(
                        background.l,
                        target: contrastingPole,
                        fraction: isDark ? 0.09 : 0.07
                    )
                )
                .withChroma(
                    background.c * 0.82 * style.surfaceChroma
                )
        )
        let border = fitToSRGBGamut(
            background
                .withLightness(
                    lightnessToward(
                        background.l,
                        target: contrastingPole,
                        fraction: isDark ? 0.20 : 0.16
                    )
                )
                .withChroma(background.c * 0.62)
        )
        let shadow = fitToSRGBGamut(
            background
                .withLightness(
                    lightnessToward(
                        background.l,
                        target: 0,
                        fraction: isDark ? 0.58 : 0.72
                    )
                )
                .withChroma(background.c * 0.28)
        )
        let tint = fitToSRGBGamut(
            accent
                .withLightness(
                    lightnessToward(
                        accent.l,
                        target: contrastingPole,
                        fraction: 0.08
                    )
                )
                .withChroma(accent.c * 0.86)
        )
        let destructive = LChColor(
            l: isDark ? 58 : 44,
            c: 58,
            h: 28
        )
        let gradientTop = fitToSRGBGamut(
            background
                .withLightness(
                    lightnessToward(
                        background.l,
                        target: isDark ? 0 : 100,
                        fraction: 0.055
                    )
                )
                .withChroma(background.c * 1.02)
        )
        let gradientBottom = fitToSRGBGamut(
            background
                .withLightness(
                    lightnessToward(
                        background.l,
                        target: contrastingPole,
                        fraction: 0.085
                    )
                )
                .withChroma(background.c * 0.94)
        )

        return JoiColorScheme(
            background: background.toCGColor(),
            primaryText: text[0].toCGColor(),
            secondaryText: text[1].toCGColor(),
            tertiaryText: text[2].toCGColor(),
            quaternaryText: text[3].toCGColor(),
            accent: accent.toCGColor(),
            surface: surface.toCGColor(),
            border: border.toCGColor(),
            shadow: shadow.toCGColor(),
            tint: tint.toCGColor(),
            destructive: destructive.toCGColor(),
            backgroundGradientTop: gradientTop.toCGColor(),
            backgroundGradientBottom: gradientBottom.toCGColor(),
            textGradientTop: text[0].toCGColor(),
            textGradientBottom: text[1].toCGColor(),
            meshColors: meshColors
        )
    }

    /// Derives the base LCh background color, optionally applying subtle chromatic tinting if the dominant cluster is achromatic.
    ///
    /// - Parameters:
    ///   - roles: Assigned semantic roles.
    ///   - chromaRetention: Multiplier attenuating background chroma in visually diverse images.
    /// - Returns: Base `LChColor` for background palette generation.
    func backgroundSource(
        roles: RoleSelection,
        chromaRetention: CGFloat
    ) -> LChColor {
        let background = roles.background.color.toLCh()
        guard background.c < 12 else {
            return background.withChroma(
                max(12, background.c * chromaRetention)
            )
        }

        guard let identity = backgroundIdentity(roles: roles) else {
            return background.withChroma(0)
        }
        let influence = backgroundIdentityInfluence(identity)
        let source = identity.color.toLCh()
        let preservesSmallVividIdentity = source.c >= 32
            && (
                identity.populationShare >= 0.004_5
                    || identity.weightedShare >= 0.007
            )
        guard influence >= 0.18 || preservesSmallVividIdentity else {
            return background.withChroma(0)
        }

        // Hue is derived directly from the observed medoid. Chroma scales proportionally with chromatic support.
        let retainedChroma = max(
            background.c,
            source.c * influence
        )
            * chromaRetention
        let chroma = preservesSmallVividIdentity
            ? max(12, retainedChroma)
            : retainedChroma
        guard chroma >= 12 else {
            return background.withChroma(0)
        }
        return LChColor(
            l: background.l,
            c: chroma,
            h: source.h
        )
    }

    /// Identifies the best secondary or accent identity cluster capable of providing atmospheric tint to neutral backgrounds.
    ///
    /// - Parameter roles: Assigned semantic roles.
    /// - Returns: Best identity cluster candidate, or `nil` if none qualify.
    func backgroundIdentity(
        roles: RoleSelection
    ) -> Cluster? {
        var candidates = [roles.secondary]
        if let accent = roles.accent,
           accent.stableID != roles.secondary.stableID {
            candidates.append(accent)
        }
        return candidates
            .filter { cluster in
                let strongChroma = smoothStep(
                    edge0: 24,
                    edge1: 48,
                    value: cluster.chroma
                )
                let populationFloor = 0.008
                    - strongChroma * 0.003_5
                let weightedFloor = 0.012
                    - strongChroma * 0.005
                let salientPopulationFloor = 0.004_5
                    - strongChroma * 0.001_5
                return hasIdentityEvidence(cluster)
                    && (
                        cluster.populationShare >= populationFloor
                            || cluster.weightedShare >= weightedFloor
                            || (
                                cluster.populationShare
                                    >= salientPopulationFloor
                                    && cluster.meanSaliency >= 0.58
                            )
                    )
            }
            .max { first, second in
                backgroundIdentityScore(
                    first,
                    background: roles.background
                ) < backgroundIdentityScore(
                    second,
                    background: roles.background
                )
            }
    }

    /// Computes the tint influence fraction `0...0.72` of an identity color on neutral backgrounds.
    ///
    /// - Parameter identity: The identity cluster.
    /// - Returns: Influence coefficient in range `0...0.72`.
    func backgroundIdentityInfluence(
        _ identity: Cluster
    ) -> CGFloat {
        let populationSupport = smoothStep(
            edge0: 0.008,
            edge1: 0.20,
            value: identity.populationShare
        )
        let atmosphereSupport = smoothStep(
            edge0: 0.012,
            edge1: 0.22,
            value: identity.weightedShare
        )
        let saliencySupport = smoothStep(
            edge0: 0.58,
            edge1: 0.88,
            value: identity.meanSaliency
        )
        let influence = clamp(
            CGFloat(populationSupport) * 0.52
                + CGFloat(atmosphereSupport) * 0.38
                + CGFloat(saliencySupport) * 0.10,
            minimum: 0,
            maximum: 0.72
        )
        return influence
    }

    /// Evaluates suitability of an identity cluster for subtle background tinting.
    ///
    /// - Parameters:
    ///   - cluster: Candidate identity cluster.
    ///   - background: The selected background cluster.
    /// - Returns: Suitability score.
    func backgroundIdentityScore(
        _ cluster: Cluster,
        background: Cluster
    ) -> Float {
        let chromaEvidence = smoothStep(
            edge0: 14,
            edge1: 44,
            value: cluster.chroma
        )
        let tonalAffinity = 1 - smoothStep(
            edge0: 12,
            edge1: 65,
            value: abs(cluster.point.l - background.point.l)
        )
        return cluster.weightedShare * 0.42
            + cluster.populationShare * 0.36
            + chromaEvidence * 0.10
            + cluster.meanSaliency * 0.06
            + tonalAffinity * 0.08
            - cluster.textAffinity * 0.08
    }

    /// Computes chroma retention factor `0...1` to prevent overly saturated monochrome overlays when multiple competing hue families exist.
    ///
    /// - Parameters:
    ///   - clusters: All clustered color candidates.
    ///   - roles: Assigned semantic roles.
    /// - Returns: Chroma retention multiplier in range `0...1`.
    func backgroundChromaRetention(
        clusters: [Cluster],
        roles: RoleSelection
    ) -> CGFloat {
        let background = roles.background.color.toLCh()
        let source: LChColor
        if background.c >= 12 {
            source = background
        } else if let identity = backgroundIdentity(roles: roles) {
            source = identity.color.toLCh()
        } else {
            return 1
        }

        let chromaticClusters = clusters.filter {
            $0.chroma >= 12
                && (
                    $0.populationShare >= 0.006
                        || $0.weightedShare >= 0.010
                )
        }
        let families = makeMeshHueFamilies(clusters: chromaticClusters)
        guard let sourceFamily = families.min(by: {
            hueDistance(
                $0.representative.color.toLCh().h,
                source.h
            ) < hueDistance(
                $1.representative.color.toLCh().h,
                source.h
            )
        })
        else {
            return 1
        }

        let sourceHue = sourceFamily.representative.color.toLCh().h
        let competitor = families
            .filter({
                hueDistance(
                    $0.representative.color.toLCh().h,
                    sourceHue
                ) >= 32
            })
            .max(by: {
                meshFamilySupport($0) < meshFamilySupport($1)
            })
        let chromaticDiversity: Float
        if let competitor {
            let supportRatio = clamp(
                meshFamilySupport(competitor)
                    / max(meshFamilySupport(sourceFamily), Float.ulpOfOne),
                minimum: 0,
                maximum: 1
            )
            let coDominance = smoothStep(
                edge0: 0.45,
                edge1: 0.90,
                value: supportRatio
            )
            let hueSeparation = smoothStep(
                edge0: 32,
                edge1: 88,
                value: Float(
                    hueDistance(
                        competitor.representative.color.toLCh().h,
                        sourceHue
                    )
                )
            )
            chromaticDiversity = coDominance * hueSeparation
        } else {
            chromaticDiversity = 0
        }

        let neutralClusters = clusters.filter {
            $0.chroma < 10
                && (
                    $0.populationShare >= 0.006
                        || $0.weightedShare >= 0.010
                )
        }
        let neutralSupport = neutralClusters.reduce(Float(0)) {
            $0 + $1.weightedShare * 0.56
                + $1.populationShare * 0.44
        }
        let neutralSupportRatio = clamp(
            neutralSupport
                / max(meshFamilySupport(sourceFamily), Float.ulpOfOne),
            minimum: 0,
            maximum: 1
        )
        let neutralCoDominance = smoothStep(
            edge0: 0.30,
            edge1: 0.80,
            value: neutralSupportRatio
        )
        let neutralDifference: Float
        if let neutral = neutralClusters.max(by: {
            meshScore($0) < meshScore($1)
        }) {
            neutralDifference = smoothStep(
                edge0: 10,
                edge1: 35,
                value: Float(
                    neutral.color.deltaE(
                        to: sourceFamily.representative.color,
                        using: .ciede2000
                    )
                )
            )
        } else {
            neutralDifference = 0
        }
        let neutralDiversity = neutralCoDominance * neutralDifference

        // Attenuate background chroma when competing distinct hue families or significant neutral areas exist,
        // letting the mesh gradient showcase multi-hue artwork variety without an overwhelming solid tint.
        let attenuation = max(
            chromaticDiversity * 0.38,
            neutralDiversity * 0.44
        )
        return CGFloat(
            1 - attenuation
        )
    }

    /// Computes paired dark and light background colors in CIELCh space matching the image's native tone.
    ///
    /// - Parameters:
    ///   - clusters: Clustered color candidates.
    ///   - roles: Assigned semantic roles.
    ///   - statistics: Luminance tone statistics.
    ///   - style: Presentation style profile.
    /// - Returns: A `BackgroundPair` containing native and counterpart background colors.
    func makeBackgroundPair(
        clusters: [Cluster],
        roles: RoleSelection,
        statistics: ToneStatistics,
        style: JoiColorStyleProfile
    ) -> BackgroundPair {
        let chromaRetention = backgroundChromaRetention(
            clusters: clusters,
            roles: roles
        )
        let source = backgroundSource(
            roles: roles,
            chromaRetention: chromaRetention
        )
        let counterpartSource: LChColor
        if source.c >= 12 {
            counterpartSource = source
        } else if let identity = backgroundIdentity(roles: roles) {
            let identityColor = identity.color.toLCh()
            let identityInfluence = max(
                0.34,
                backgroundIdentityInfluence(identity)
            )
            counterpartSource = LChColor(
                l: source.l,
                c: max(
                    12,
                    identityColor.c
                        * identityInfluence
                        * chromaRetention
                ),
                h: identityColor.h
            )
        } else {
            counterpartSource = source
        }

        let sourceLightness = source.l * 0.78
            + CGFloat(statistics.weightedMedian) * 0.22
        let nativeLightness: CGFloat
        let counterpartLightness: CGFloat

        if statistics.tone == .dark {
            let nativeCandidate = clamp(
                sourceLightness,
                minimum: 7,
                maximum: 36
            )
            nativeLightness = refinedDarkLightness(nativeCandidate)
            counterpartLightness = clamp(
                50 + (50 - nativeCandidate) * 0.64,
                minimum: 64,
                maximum: 92
            )
        } else {
            nativeLightness = clamp(
                sourceLightness,
                minimum: 64,
                maximum: 92
            )
            counterpartLightness = refinedDarkLightness(
                clamp(
                    50 - (nativeLightness - 50) * 0.64,
                    minimum: 8,
                    maximum: 36
                )
            )
        }

        let nativeSource = statistics.tone == .light
            && source.c < 12
            && counterpartSource.c >= 12
            ? counterpartSource
            : source
        let native = backgroundColor(
            source: nativeSource,
            lightness: nativeLightness,
            chromaScale: style.backgroundChroma
        )
        let counterpart = backgroundColor(
            source: counterpartSource,
            lightness: counterpartLightness,
            chromaScale: style.backgroundChroma * 0.94
        )

        if statistics.tone == .dark {
            return BackgroundPair(
                source: source,
                dark: native,
                light: refinedLightBackgroundChroma(counterpart)
            )
        }
        return BackgroundPair(
            source: source,
            dark: counterpart,
            light: refinedLightBackgroundChroma(native)
        )
    }

    /// Compresses dark background lightness values above 22 to prevent dark theme backgrounds from appearing washed out.
    ///
    /// - Parameter source: Candidate lightness value.
    /// - Returns: Refined dark theme lightness.
    func refinedDarkLightness(_ source: CGFloat) -> CGFloat {
        let compressionStart: CGFloat = 22
        guard source > compressionStart else {
            return source
        }
        return compressionStart
            + (source - compressionStart) * 0.50
    }

    /// Creates an in-gamut `LChColor` background given lightness and chroma scaling.
    ///
    /// - Parameters:
    ///   - source: Source LCh color.
    ///   - lightness: Target lightness `0...100`.
    ///   - chromaScale: Chroma scaling multiplier.
    /// - Returns: In-gamut `LChColor`.
    func backgroundColor(
        source: LChColor,
        lightness: CGFloat,
        chromaScale: CGFloat
    ) -> LChColor {
        let sourceChroma = source.c < 12 ? 0 : source.c
        return fitToSRGBGamut(
            LChColor(
                l: lightness,
                c: min(82, sourceChroma * chromaScale),
                h: source.h
            )
        )
    }

    /// Refines light background chroma near near-white lightness levels (L >= 82).
    ///
    /// - Parameter color: Candidate light background color.
    /// - Returns: In-gamut refined `LChColor`.
    func refinedLightBackgroundChroma(_ color: LChColor) -> LChColor {
        guard color.c >= 8 else {
            return color
        }
        let nearWhite = smoothStep(
            edge0: 82,
            edge1: 92,
            value: Float(color.l)
        )
        let restrainedChroma = 1 - smoothStep(
            edge0: 16,
            edge1: 36,
            value: Float(color.c)
        )
        let chromaScale = 1
            + CGFloat(nearWhite * restrainedChroma) * 0.12
        return fitToSRGBGamut(
            color.withChroma(
                min(82, color.c * chromaScale)
            )
        )
    }

    /// Selects the source LCh color from which the 4-tier typography hierarchy is derived.
    ///
    /// - Parameters:
    ///   - roles: Semantic role selection.
    ///   - background: The selected background color in LCh.
    /// - Returns: Source `LChColor` for typography generation.
    func foregroundSource(
        roles: RoleSelection,
        background: LChColor
    ) -> LChColor {
        let foreground = roles.foreground?.color.toLCh()

        if let accentCluster = roles.accent {
            let accent = accentCluster.color.toLCh()
            let backgroundDifference = accentCluster.color.deltaE(
                to: roles.background.color,
                using: .ciede2000
            )
            let backgroundRole = roles.background.color.toLCh()
            let accentIsExpressive = accent.c >= 16
                && backgroundDifference >= 12
            let foregroundIsNeutral = (foreground?.c ?? 0) < 12
            let accentHasStrongerIdentity = foreground.map {
                accent.c >= $0.c + 12
            } ?? true
            let accentIsHueDistinct = hueDistance(
                accent.h,
                backgroundRole.h
            ) >= 28 && backgroundRole.c >= 12
            let foregroundRepeatsBackgroundHue = foreground.map {
                backgroundRole.c >= 12
                    && $0.c >= 12
                    && hueDistance($0.h, backgroundRole.h) < 24
            } ?? false

            // Prefer vibrant accent hue for typography when foreground text in the artwork is neutral or lacks hue separation.
            if accentIsExpressive
                && (
                    foregroundIsNeutral
                        || accentHasStrongerIdentity
                        || (
                            accentIsHueDistinct
                                && foregroundRepeatsBackgroundHue
                        )
                ) {
                return accent
            }
        }

        if let foreground {
            return foreground
        }

        if let accent = roles.accent {
            return accent.color.toLCh()
        }
        if background.c >= 8 {
            return background
        }
        return LChColor(l: 50, c: 0, h: 0)
    }

    /// Generates a 4-level typography hierarchy (primary, secondary, tertiary, quaternary) in LCh space.
    ///
    /// Enforces minimum WCAG 2.1 relative luminance contrast ratios (4.5:1 for primary, 3.5:1 for secondary, 3.0:1 for tertiary, 2.5:1 for quaternary)
    /// against the background color.
    ///
    /// - Parameters:
    ///   - source: Source typography LCh color.
    ///   - background: Background `LABColor`.
    ///   - isDark: `true` for dark themes, `false` for light themes.
    ///   - style: The presentation styling profile.
    /// - Returns: Array of 4 `LChColor` models representing `[primary, secondary, tertiary, quaternary]`.
    func makeTextHierarchy(
        source: LChColor,
        background: LABColor,
        isDark: Bool,
        style: JoiColorStyleProfile
    ) -> [LChColor] {
        let baseLightness: [CGFloat]
        if isDark {
            let boost = clamp(
                (20 - background.l) / 20,
                minimum: 0,
                maximum: 1
            ) * 7
            baseLightness = [
                82 + boost * 0.45 - style.textLightnessInset,
                72 + boost * 0.58 - style.textLightnessInset * 0.55,
                62 + boost * 0.42 - style.textLightnessInset * 0.25,
                52 + boost * 0.24
            ]
        } else {
            let pull = clamp(
                (background.l - 88) / 10,
                minimum: 0,
                maximum: 1
            ) * 6
            baseLightness = [
                18 - pull * 0.45 + style.textLightnessInset,
                29 - pull * 0.58 + style.textLightnessInset * 0.55,
                40 - pull * 0.42 + style.textLightnessInset * 0.25,
                51 - pull * 0.24
            ]
        }

        let chromaRatios: [CGFloat] = [1.05, 0.82, 0.60, 0.40]
        let chromaCaps: [CGFloat] = [68, 50, 36, 24]
        let contrastTargets: [CGFloat] = [4.5, 3.5, 3, 2.5]
        var result: [LChColor] = []

        for index in 0..<4 {
            var candidate = LChColor(
                l: clamp(
                    baseLightness[index],
                    minimum: 3,
                    maximum: 97
                ),
                c: min(
                    source.c
                        * chromaRatios[index]
                        * style.textChroma,
                    chromaCaps[index]
                ),
                h: source.h
            )
            candidate = enforceContrast(
                background: background,
                candidate: candidate,
                minimumRatio: contrastTargets[index],
                isDark: isDark
            )
            result.append(fitToSRGBGamut(candidate))
        }
        return result
    }

    /// Fits an LCh color into the standard sRGB gamut using binary search on chroma while strictly preserving lightness and hue.
    ///
    /// - Parameter color: The target `LChColor`.
    /// - Returns: An in-gamut `LChColor`.
    func fitToSRGBGamut(_ color: LChColor) -> LChColor {
        guard !color.toLAB().isInSRGBGamut else {
            return color
        }

        var lower: CGFloat = 0
        var upper = color.c
        for _ in 0..<14 {
            let midpoint = (lower + upper) / 2
            if color.withChroma(midpoint).toLAB().isInSRGBGamut {
                lower = midpoint
            } else {
                upper = midpoint
            }
        }
        return color.withChroma(lower)
    }

    /// Adjusts candidate lightness via binary search until the WCAG relative luminance contrast against the background meets `minimumRatio`.
    ///
    /// - Parameters:
    ///   - background: Background `LABColor`.
    ///   - candidate: Candidate foreground `LChColor`.
    ///   - minimumRatio: Required WCAG contrast ratio (e.g. 4.5).
    ///   - isDark: `true` if adjusting for dark appearance; `false` for light appearance.
    /// - Returns: Contrast-compliant `LChColor`.
    func enforceContrast(
        background: LABColor,
        candidate: LChColor,
        minimumRatio: CGFloat,
        isDark: Bool
    ) -> LChColor {
        func contrast(_ color: LChColor) -> CGFloat {
            let first = background.relativeLuminance
            let second = color.toLAB().relativeLuminance
            let brighter = max(first, second)
            let darker = min(first, second)
            return (brighter + 0.05) / (darker + 0.05)
        }

        guard contrast(candidate) < minimumRatio else {
            return candidate
        }

        var lower: CGFloat = isDark ? candidate.l : 0
        var upper: CGFloat = isDark ? 100 : candidate.l
        for _ in 0..<18 {
            let midpoint = (lower + upper) / 2
            let test = candidate.withLightness(midpoint)
            if contrast(test) >= minimumRatio {
                if isDark {
                    upper = midpoint
                } else {
                    lower = midpoint
                }
            } else if isDark {
                lower = midpoint
            } else {
                upper = midpoint
            }
        }
        return candidate.withLightness(isDark ? upper : lower)
    }

    /// Constructs an in-gamut, contrast-enforced interactive accent color.
    ///
    /// - Parameters:
    ///   - roles: Semantic role selection.
    ///   - background: The selected background color in LCh.
    ///   - isDark: `true` for dark themes; `false` for light themes.
    ///   - style: The presentation styling profile.
    /// - Returns: Contrast-compliant in-gamut `LChColor` for interactive elements.
    func accentColor(
        roles: RoleSelection,
        background: LChColor,
        isDark: Bool,
        style: JoiColorStyleProfile
    ) -> LChColor {
        let source = roles.accent?.color.toLCh() ?? background
        let hasIndependentAccent = roles.accent != nil
        let minimumLightness = lightnessToward(
            background.l,
            target: isDark ? 100 : 0,
            fraction: hasIndependentAccent ? 0.34 : 0.42
        )
        let preservesObservedSeparation = isDark
            ? source.l >= minimumLightness
            : source.l <= minimumLightness
        var candidate = LChColor(
            l: preservesObservedSeparation
                ? source.l
                : minimumLightness,
            c: min(
                78,
                source.c * style.accentChroma
            ),
            h: source.h
        )
        candidate = enforceContrast(
            background: background.toLAB(),
            candidate: candidate,
            minimumRatio: 2.25,
            isDark: isDark
        )
        return fitToSRGBGamut(candidate)
    }

    /// Linear interpolation helper shifting a lightness coordinate toward a target pole.
    ///
    /// - Parameters:
    ///   - source: Starting lightness `0...100`.
    ///   - target: Target lightness pole (typically `0` or `100`).
    ///   - fraction: Shift fraction `0...1`.
    /// - Returns: Interpolated lightness value.
    func lightnessToward(
        _ source: CGFloat,
        target: CGFloat,
        fraction: CGFloat
    ) -> CGFloat {
        clamp(
            source + (target - source) * fraction,
            minimum: 0,
            maximum: 100
        )
    }
}

