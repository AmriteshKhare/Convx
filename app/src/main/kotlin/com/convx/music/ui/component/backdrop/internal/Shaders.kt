/*
 * Vendored from Kyant0/backdrop v2.0.0 (io.github.kyant0:backdrop)
 * https://github.com/Kyant0/backdrop — Copyright 2025 Kyant0, Apache License 2.0
 *
 * Vendored so the library ships as source with this app (binary AARs compiled
 * against older Compose broke at runtime) and to add a backdrop resolution
 * scale for cheaper effect rendering. KMP expect/actual declarations were
 * merged into this single Android source set. Package renamed accordingly.
 */
/*
   Copyright 2025 Kyant

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */

package com.convx.music.ui.component.backdrop.internal

import org.intellij.lang.annotations.Language

@Language("AGSL")
private const val RoundedRectSDF = """
float radiusAt(float2 coord, float4 radii) {
    if (coord.x >= 0.0) {
        if (coord.y <= 0.0) return radii.y;
        else return radii.z;
    } else {
        if (coord.y <= 0.0) return radii.x;
        else return radii.w;
    }
}

float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}

float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0));
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}"""

@Language("AGSL")
internal const val RoundedRectRefractionShaderString = """
uniform shader content;

// A/B legacy: `uniform float2 size;` + per-pixel `float2 halfSize = size * 0.5;`
// hoisted to CPU (halfSize/minHalf uniforms below).
uniform float2 halfSize;
uniform float minHalf;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;
uniform int circleMapMode;

$RoundedRectSDF

float circleMap(float x) {
    if (circleMapMode == 1) {
        return x * x;
    }
    return 1.0 - sqrt(1.0 - x * x);
}

half4 main(float2 coord) {
    float2 centeredCoord = (coord + offset) - halfSize;
    float radius = radiusAt(coord, cornerRadii);
    
    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);
    
    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.5, minHalf);
    float2 gradBase = gradSdRoundedRect(centeredCoord, halfSize, gradRadius);
    float2 grad;
    if (depthEffect > 0.5) {
        grad = normalize(gradBase + normalize(centeredCoord));
    } else {
        grad = gradBase;
    }
    // A/B legacy: always-normalize path (paid sqrt even when depthEffect == 0)
    // float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * normalize(centeredCoord));
    
    float2 refractedCoord = coord + d * grad;
    return content.eval(refractedCoord);
}"""

@Language("AGSL")
internal val RoundedRectRefractionWithDispersionShaderString = """
uniform shader content;

uniform float2 halfSize;
uniform float minHalf;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;
uniform float chromaticAberration;
uniform int circleMapMode;

$RoundedRectSDF

float circleMap(float x) {
    if (circleMapMode == 1) {
        return x * x;
    }
    return 1.0 - sqrt(1.0 - x * x);
}

half4 main(float2 coord) {
    float2 centeredCoord = (coord + offset) - halfSize;
    float radius = radiusAt(coord, cornerRadii);
    
    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);
    
    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.5, minHalf);
    float2 gradBase = gradSdRoundedRect(centeredCoord, halfSize, gradRadius);
    float2 grad;
    if (depthEffect > 0.5) {
        grad = normalize(gradBase + normalize(centeredCoord));
    } else {
        grad = gradBase;
    }
    // A/B legacy: always-normalize path (paid sqrt even when depthEffect == 0)
    // float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * normalize(centeredCoord));
    
    float2 refractedCoord = coord + d * grad;
    float dispersionIntensity = chromaticAberration * ((centeredCoord.x * centeredCoord.y) / (halfSize.x * halfSize.y));
    float2 dispersedCoord = d * grad * dispersionIntensity;
    
    half4 refr = content.eval(refractedCoord);
    half4 refrRed = content.eval(refractedCoord + dispersedCoord);
    half4 refrBlue = content.eval(refractedCoord - dispersedCoord);
    return half4(refrRed.r, refr.g, refrBlue.b, refr.a);
    // A/B legacy: 7-tap dispersion (slower, kept for comparison)
    // half4 color = half4(0.0);
    // half4 red = content.eval(refractedCoord + dispersedCoord);
    // color.r += red.r / 3.5;
    // color.a += red.a / 7.0;
    // half4 orange = content.eval(refractedCoord + dispersedCoord * (2.0 / 3.0));
    // color.r += orange.r / 3.5;
    // color.g += orange.g / 7.0;
    // color.a += orange.a / 7.0;
    // half4 yellow = content.eval(refractedCoord + dispersedCoord * (1.0 / 3.0));
    // color.r += yellow.r / 3.5;
    // color.g += yellow.g / 3.5;
    // color.a += yellow.a / 7.0;
    // half4 green = content.eval(refractedCoord);
    // color.g += green.g / 3.5;
    // color.a += green.a / 7.0;
    // half4 cyan = content.eval(refractedCoord - dispersedCoord * (1.0 / 3.0));
    // color.g += cyan.g / 3.5;
    // color.b += cyan.b / 3.0;
    // color.a += cyan.a / 7.0;
    // half4 blue = content.eval(refractedCoord - dispersedCoord * (2.0 / 3.0));
    // color.b += blue.b / 3.0;
    // color.a += blue.a / 7.0;
    // half4 purple = content.eval(refractedCoord - dispersedCoord);
    // color.r += purple.r / 7.0;
    // color.b += purple.b / 3.0;
    // color.a += purple.a / 7.0;
    // return color;
}"""

@Language("AGSL")
internal const val DefaultHighlightShaderString = """
// A/B legacy: `uniform float2 size;` + per-pixel `halfSize = size * 0.5`
// and `uniform float angle;` + per-pixel `cos(angle)/sin(angle)`.
uniform float2 halfSize;
uniform float minHalf;
uniform float4 cornerRadii;
layout(color) uniform half4 color;
uniform float2 lightDir;
uniform float falloff;

$RoundedRectSDF

half4 main(float2 coord) {
    float2 centeredCoord = coord - halfSize;
    float radius = radiusAt(coord, cornerRadii);
    
    float gradRadius = min(radius * 1.5, minHalf);
    float2 grad = gradSdRoundedRect(centeredCoord, halfSize, gradRadius);
    float d = dot(grad, lightDir);
    float intensity = pow(abs(d), falloff);
    return color * intensity;
}"""

@Language("AGSL")
internal const val AmbientHighlightShaderString = """
// A/B legacy: `uniform float2 size;` + per-pixel `halfSize = size * 0.5`
// and `uniform float angle;` + per-pixel `cos(angle)/sin(angle)`.
uniform float2 halfSize;
uniform float minHalf;
uniform float4 cornerRadii;
uniform float2 lightDir;
uniform float falloff;

$RoundedRectSDF

half4 main(float2 coord) {
    float2 centeredCoord = coord - halfSize;
    float radius = radiusAt(coord, cornerRadii);
    
    float gradRadius = min(radius * 1.5, minHalf);
    float2 grad = gradSdRoundedRect(centeredCoord, halfSize, gradRadius);
    float d = dot(grad, lightDir);
    float intensity = pow(abs(d), falloff);
    float t = step(0.0, d);
    return half4(t, t, t, 1.0) * intensity;
}"""
