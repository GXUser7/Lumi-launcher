package com.yumi.launcher.scene

import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.Shader
import kotlin.math.sqrt

/**
 * The Ultra sky, drawn on the GPU by three Skia runtime shaders instead of the flat gradient, the
 * square stars and the block clouds of the other levels:
 *
 * - [SKY] — the sky itself: a gradient by the height of each pixel's ray, the sun's glow by day;
 *   by night thousands of stars of different brightness and colour, twinkling, the Milky Way with
 *   its dark dust lanes, the glow of the air at the horizon.
 * - [CLOUDS] — soft clouds: a sea of them under the island, lit from above as a surface so the sun
 *   picks out every billow, fading into haze with distance; and fair-weather clouds overhead with
 *   dark bellies and bright edges toward the sun. By night the moon lights both, dimly.
 * - [MIST] — fog drifting over the cloud sea in front of the island, after the island is drawn.
 *
 * Every pixel works out its own ray from the camera, so the sky turns and tilts with the camera like
 * the rest of the scene. Null from [create] when the shaders do not compile — the caller then draws
 * the sky the way the other levels do.
 */
internal class UltraSky private constructor(sky: RuntimeEffect, clouds: RuntimeEffect, mist: RuntimeEffect) {
    private val skyBuilder = RuntimeShaderBuilder(sky)
    private val cloudBuilder = RuntimeShaderBuilder(clouds)
    private val mistBuilder = RuntimeShaderBuilder(mist)
    private val paint = Paint()
    private var shaders = ArrayList<Shader>(3)

    /** The camera and the light, set once per frame before drawing. */
    class Frame {
        val eye = FloatArray(3)
        val fwd = FloatArray(3)
        val right = FloatArray(3)
        val up = FloatArray(3)
        var focal = 1f
        var cx = 0f
        var cy = 0f
        var time = 0f
        var night = 0f
        val zenith = FloatArray(3)
        val mid = FloatArray(3)
        val horizon = FloatArray(3)
        val fog = FloatArray(3)
        val sun = FloatArray(3)
        val moon = FloatArray(3)
    }

    val frame = Frame()

    fun drawSky(canvas: Canvas, w: Float, h: Float) = draw(canvas, w, h, skyBuilder)

    fun drawClouds(canvas: Canvas, w: Float, h: Float) = draw(canvas, w, h, cloudBuilder)

    /**
     * Fog in front of the island. The island has no depth buffer to hide fog behind it, so only
     * the fog nearer than the island can be drawn over it: [front] is how far that reaches along a
     * ray, and 0 — the camera among the buildings — draws none.
     */
    fun drawMist(canvas: Canvas, w: Float, h: Float, front: Float) {
        if (front <= 1f) return
        mistBuilder.uniform("uFront", front)
        draw(canvas, w, h, mistBuilder)
    }

    /** Frees last frame's shaders; Skia keeps them alive only as long as something refers to them. */
    fun endFrame() {
        shaders.forEach { it.close() }
        shaders.clear()
    }

    private fun draw(canvas: Canvas, w: Float, h: Float, builder: RuntimeShaderBuilder) {
        val f = frame
        builder.uniform("uEye", f.eye[0], f.eye[1], f.eye[2])
        builder.uniform("uFwd", f.fwd[0], f.fwd[1], f.fwd[2])
        builder.uniform("uRight", f.right[0], f.right[1], f.right[2])
        builder.uniform("uUp", f.up[0], f.up[1], f.up[2])
        builder.uniform("uCenter", f.cx, f.cy)
        builder.uniform("uFocal", f.focal)
        builder.uniform("uTime", f.time)
        builder.uniform("uNight", f.night)
        builder.uniform("uZenith", f.zenith[0], f.zenith[1], f.zenith[2])
        builder.uniform("uMid", f.mid[0], f.mid[1], f.mid[2])
        builder.uniform("uHorizon", f.horizon[0], f.horizon[1], f.horizon[2])
        builder.uniform("uFog", f.fog[0], f.fog[1], f.fog[2])
        builder.uniform("uSun", f.sun[0], f.sun[1], f.sun[2])
        builder.uniform("uMoon", f.moon[0], f.moon[1], f.moon[2])
        val shader = builder.makeShader(null)
        shaders += shader
        paint.shader = shader
        canvas.drawRect(Rect.makeWH(w, h), paint)
    }

    companion object {
        fun create(): UltraSky? = try {
            UltraSky(RuntimeEffect.makeForShader(SKY), RuntimeEffect.makeForShader(CLOUDS), RuntimeEffect.makeForShader(MIST))
        } catch (e: Exception) {
            System.err.println("Небо «Ультра» недоступно: ${e.message}")
            null
        }

        fun normalized(x: Float, y: Float, z: Float, out: FloatArray) {
            val l = sqrt(x * x + y * y + z * z)
            out[0] = x / l; out[1] = y / l; out[2] = z / l
        }

        /** What every pass shares: the uniforms of the frame, the ray of a pixel, noise. */
        private const val COMMON = """
uniform float3 uEye;
uniform float3 uFwd;
uniform float3 uRight;
uniform float3 uUp;
uniform float2 uCenter;
uniform float uFocal;
uniform float uTime;
uniform float uNight;
uniform float3 uZenith;
uniform float3 uMid;
uniform float3 uHorizon;
uniform float3 uFog;
uniform float3 uSun;
uniform float3 uMoon;

float3 rayOf(float2 p) {
    return normalize(uFwd * uFocal + uRight * (p.x - uCenter.x) - uUp * (p.y - uCenter.y));
}

float hash12(float2 p) {
    float3 p3 = fract(float3(p.x, p.y, p.x) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float hash13(float3 p) {
    float3 p3 = fract(p * 0.1031);
    p3 += dot(p3, p3.zyx + 31.32);
    return fract((p3.x + p3.y) * p3.z);
}

float3 hash33(float3 p) {
    float3 p3 = fract(p * float3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yxz + 33.33);
    return fract((p3.xxy + p3.yxx) * p3.zyx);
}

float noise2(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    float a = hash12(i);
    float b = hash12(i + float2(1.0, 0.0));
    float c = hash12(i + float2(0.0, 1.0));
    float d = hash12(i + float2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float noise3(float3 p) {
    float3 i = floor(p);
    float3 f = fract(p);
    float3 u = f * f * (3.0 - 2.0 * f);
    float n000 = hash13(i);
    float n100 = hash13(i + float3(1.0, 0.0, 0.0));
    float n010 = hash13(i + float3(0.0, 1.0, 0.0));
    float n110 = hash13(i + float3(1.0, 1.0, 0.0));
    float n001 = hash13(i + float3(0.0, 0.0, 1.0));
    float n101 = hash13(i + float3(1.0, 0.0, 1.0));
    float n011 = hash13(i + float3(0.0, 1.0, 1.0));
    float n111 = hash13(i + float3(1.0, 1.0, 1.0));
    return mix(mix(mix(n000, n100, u.x), mix(n010, n110, u.x), u.y), mix(mix(n001, n101, u.x), mix(n011, n111, u.x), u.y), u.z);
}

float fbm(float2 p) {
    float s = 0.0;
    float a = 0.5;
    for (int i = 0; i < 5; i++) {
        s += a * noise2(p);
        p = float2(1.6 * p.x + 1.2 * p.y, -1.2 * p.x + 1.6 * p.y);
        a *= 0.5;
    }
    return s / 0.96875;
}

float fbm4(float2 p) {
    float s = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        s += a * noise2(p);
        p = float2(1.6 * p.x + 1.2 * p.y, -1.2 * p.x + 1.6 * p.y);
        a *= 0.5;
    }
    return s / 0.9375;
}

float fbm3(float3 p) {
    float s = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        s += a * noise3(p);
        p = p * 2.03 + float3(1.7, 9.2, 3.1);
        a *= 0.5;
    }
    return s / 0.9375;
}
"""

        /** Gradient, sun glow, stars, the Milky Way, airglow. */
        private const val SKY = COMMON + """
// One layer of stars: at most one per cell of a grid laid round the sky, each a point of light
// one pixel across, of its own brightness and colour, twinkling on its own beat.
float3 stars(float3 d, float scale, float chance, float gain) {
    float3 q = d * scale;
    float3 c = floor(q);
    float h = hash13(c);
    if (h > chance) return float3(0.0);
    float3 at = 0.15 + 0.7 * hash33(c + 17.0);
    float3 off = fract(q) - at;
    // One pixel, in cells: the star's glow is about that wide whatever the layer's grid.
    float px = scale / uFocal;
    float r2 = dot(off, off) / (px * px);
    float k = hash13(c + 5.0);
    float magnitude = gain * (0.25 + 1.6 * k * k * k * k);
    float twinkle = 0.72 + 0.28 * sin(uTime * (1.5 + 3.0 * k) + h * 60.0);
    float3 tint = mix(float3(1.0, 0.78, 0.6), float3(0.72, 0.82, 1.0), hash13(c + 9.0));
    tint = mix(tint, float3(1.0), 0.45);
    return tint * magnitude * twinkle * exp(-r2 * 0.9);
}

half4 main(float2 p) {
    float3 d = rayOf(p);
    float y = d.y;
    float3 col = mix(uHorizon, uMid, smoothstep(-0.03, 0.22, y));
    col = mix(col, uZenith, smoothstep(0.22, 0.9, y));
    float day = 1.0 - uNight;

    // The sun's glow: a wide warm bloom and a tighter bright one, and the whole sky a touch
    // brighter round the horizon, where the air is thickest.
    // The glow sits round the sun's disc, which is drawn low over the horizon at the sun's bearing.
    float s = max(dot(d, normalize(float3(uSun.x, 0.1, uSun.z))), 0.0);
    col += day * (float3(1.0, 0.82, 0.58) * pow(s, 6.0) * 0.28 + float3(1.0, 0.96, 0.88) * pow(s, 48.0) * 0.45);
    col += day * float3(0.92, 0.96, 1.0) * 0.10 * exp(-abs(y) * 10.0);

    if (uNight > 0.01) {
        float3 n = float3(0.0);
        float up = smoothstep(-0.02, 0.18, y);
        // The Milky Way: a band tilted across the sky, knotted with brighter clouds of stars and
        // cut by dark lanes of dust.
        float3 axis = normalize(float3(0.83, 0.35, -0.56));
        float across = dot(d, axis);
        float band = exp(-across * across / 0.028);
        float glow = fbm3(d * 5.0 + float3(3.0, 1.0, 7.0));
        float dust = smoothstep(0.48, 0.72, fbm3(d * 11.0 + float3(11.0, 4.0, 2.0)));
        float core = exp(-across * across / 0.006);
        float3 milky = mix(float3(0.42, 0.5, 0.78), float3(0.95, 0.84, 0.7), glow * 0.8 + core * 0.4);
        n += milky * band * (0.10 + 0.36 * glow * glow + 0.22 * core) * (1.0 - 0.8 * dust * band);
        // Stars: a dense faint layer — thicker in the Milky Way — a medium one and a few bright.
        n += stars(d, 230.0, 0.42 + 0.4 * band, 0.55 * (1.0 - 0.6 * dust * band));
        n += stars(d, 110.0, 0.30, 0.8);
        n += stars(d, 46.0, 0.16, 1.25);
        n *= up;
        // Airglow: a faint green-blue band over the horizon.
        n += float3(0.03, 0.07, 0.08) * exp(-abs(y - 0.06) * 14.0);
        // The moon's halo.
        float m = max(dot(d, normalize(float3(uMoon.x, 0.07, uMoon.z))), 0.0);
        n += float3(0.55, 0.66, 0.9) * (pow(m, 12.0) * 0.10 + pow(m, 160.0) * 0.25);
        col += n * uNight;
    }
    return half4(half3(col), 1.0);
}
"""

        /** The cloud sea under the island and the clouds overhead, with the haze they fade into. */
        private const val CLOUDS = COMMON + """
const float SEA = -30.0;
const float HIGH = 95.0;

// The light of the moment: the sun by day, the moon by night, and the sky's own fill.
float3 lightDir() { return normalize(mix(uSun, uMoon, uNight)); }
float3 lightColor() { return mix(float3(1.0, 0.96, 0.88), float3(0.36, 0.44, 0.66), uNight); }
float3 skyFill() { return mix(float3(0.58, 0.69, 0.86), float3(0.06, 0.085, 0.15), uNight); }

// The sky just above the horizon, as the sky pass paints it there, so the haze meets it seamlessly.
float3 horizonSky(float3 d) {
    float day = 1.0 - uNight;
    float s = max(dot(normalize(float3(d.x, 0.0, d.z)), normalize(float3(uSun.x, 0.1, uSun.z))), 0.0);
    return uHorizon + day * (float3(1.0, 0.82, 0.58) * pow(s, 6.0) * 0.28 + float3(0.92, 0.96, 1.0) * 0.10)
        + uNight * float3(0.03, 0.07, 0.08) * 0.43;
}

// The billows' height at q: noise cut and rounded off at the top like cumulus heads.
float billow(float2 q) {
    float n = fbm(q);
    return sqrt(smoothstep(0.22, 0.88, n));
}

// The cloud sea seen from above: a height field of billows, lit as a surface, shadowing itself.
half4 sea(float3 d) {
    float t = (SEA - uEye.y) / d.y;
    float2 pos = uEye.xz + d.xz * t;
    float2 q = pos * 0.015 + float2(uTime * 0.008, uTime * 0.003);
    float2 w = float2(fbm4(q * 0.35 + float2(1.7, 9.2) + uTime * 0.002), fbm4(q * 0.35 + float2(8.3, 2.8)));
    float2 base = q + 0.85 * w;
    // Farther away the billows blur into each other: the step for the slope grows with distance.
    float e = 0.035 + t * 0.00045;
    float h = billow(base);
    float hx = billow(base + float2(e, 0.0));
    float hz = billow(base + float2(0.0, e));
    // Heights of about 40 blocks over the noise's unit: steep enough for the sun to model each billow.
    float k = 0.015 * 42.0 / e;
    float3 normal = normalize(float3((h - hx) * k, 1.0, (h - hz) * k));
    float3 light = lightDir();
    float lit = clamp(dot(normal, light), 0.0, 1.0);
    // Shadow from the billows between this point and the light.
    float2 toward = normalize(light.xz + 0.0001);
    float blocker = billow(base + toward * 0.16);
    float shadow = 1.0 - 0.55 * smoothstep(0.0, 0.35, blocker - h - 0.05);
    // Deep between the billows the cloud is thicker and dimmer; the tops catch the light.
    float3 col = lightColor() * (0.2 + 0.92 * lit * shadow) * mix(0.58, 1.04, h) + skyFill() * mix(0.28, 0.5, h);
    // Now and then the sea thins out: breaks where the eye looks down into bluer depth.
    float breaks = smoothstep(0.34, 0.5, fbm4(pos * 0.0035 + float2(5.3, 1.1)));
    col = mix(mix(uFog, skyFill(), 0.5) * mix(0.72, 0.9, uNight), col, mix(1.0, smoothstep(0.02, 0.35, h), 1.0 - breaks));
    // Light through the thin edges when looking toward the light.
    float forward = max(dot(d, light), 0.0);
    col += lightColor() * pow(forward, 6.0) * (1.0 - h) * 0.3;
    // Distance: the billows sink into the haze, and at the horizon they are the sky's own colour.
    float haze = 1.0 - exp(-t * 0.0017);
    col = mix(col, mix(uFog, horizonSky(d), 0.6), clamp(haze, 0.0, 1.0));
    col = mix(col, horizonSky(d), smoothstep(-0.06, -0.004, d.y));
    return half4(half3(col), 1.0);
}

// Clouds overhead, seen from below: dark bellies, sunlit edges, gone into the haze near the horizon.
half4 high(float3 d) {
    float t = (HIGH - uEye.y) / d.y;
    float2 pos = uEye.xz + d.xz * t;
    float2 q = pos * 0.0065 + float2(uTime * 0.006, -uTime * 0.002);
    float2 w = float2(fbm4(q * 0.6 + float2(4.1, 1.3)), fbm4(q * 0.6 + float2(2.2, 7.9)));
    float2 base = q + 0.9 * w;
    float n = fbm(base);
    float cover = smoothstep(0.46, 0.74, n);
    if (cover <= 0.001) return half4(0.0);
    float3 light = lightDir();
    // How much cloud is between this point and the light: the sunward side is thinner and bright.
    float toLight = fbm(base + normalize(light.xz + 0.0001) * 0.09);
    float shade = clamp(0.64 + (n - toLight) * 3.4, 0.22, 1.1);
    float3 col = mix(skyFill() * 0.92, lightColor() * 1.02, shade);
    float forward = max(dot(d, light), 0.0);
    col += lightColor() * pow(forward, 8.0) * (1.0 - cover) * 0.9;
    float fade = smoothstep(0.015, 0.22, d.y);
    col = mix(horizonSky(d), col, fade);
    float alpha = cover * fade * mix(0.96, 0.8, uNight);
    return half4(half3(col * alpha), half(alpha));
}

half4 main(float2 p) {
    float3 d = rayOf(p);
    if (d.y < -0.0005 && uEye.y > SEA) return sea(d);
    if (d.y > 0.0005 && uEye.y < HIGH) return high(d);
    return half4(0.0);
}
"""

        /** Fog drifting in a layer over the cloud sea, only as far as the island. */
        private const val MIST = COMMON + """
uniform float uFront;
const float TOP = 0.0;
const float BOTTOM = -18.0;

half4 main(float2 p) {
    float3 d = rayOf(p);
    if (d.y > -0.01 || uEye.y < TOP) return half4(0.0);
    float t0 = (TOP - uEye.y) / d.y;
    float t1 = min((BOTTOM - uEye.y) / d.y, uFront);
    if (t1 <= t0) return half4(0.0);
    float acc = 0.0;
    for (int i = 0; i < 3; i++) {
        float t = mix(t0, t1, (float(i) + 0.5) / 3.0);
        float3 at = uEye + d * t;
        float2 q = at.xz * 0.045 + float2(uTime * 0.03, uTime * 0.011) + at.y * 0.05;
        acc += smoothstep(0.42, 0.85, fbm4(q));
    }
    acc = acc / 3.0 * clamp((t1 - t0) / 16.0, 0.0, 1.0);
    float alpha = acc * mix(0.5, 0.4, uNight);
    float3 col = mix(float3(0.93, 0.96, 1.0), float3(0.12, 0.17, 0.27), uNight);
    col = mix(col, uFog, 0.35);
    return half4(half3(col * alpha), half(alpha));
}
"""
    }
}
