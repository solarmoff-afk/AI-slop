package com.rotokey.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ShaderEffectLayer(
    val id: Long,
    var name: String,
    var fragmentCode: String,
    var posX: Float = 0.5f,
    var posY: Float = 0.5f,
    var scale: Float = 0.45f,
    var rotationRad: Float = 0f
)

data class ShaderPreset(
    val name: String,
    val code: String
)

object PresetStorage {

    private const val PREFS_NAME = "rotokey_shader_presets"
    private const val KEY_PRESETS = "presets_json"
    private const val KEY_SEEDED = "seeded_defaults"

    val DEFAULT_ORB_SHADER: String = listOf(
        "precision mediump float;",
        "uniform sampler2D uTexture;",
        "uniform vec2 uResolution;",
        "uniform float uTime;",
        "uniform float uFrame;",
        "uniform vec2 uPos;",
        "uniform vec2 uSize;",
        "uniform float uRotation;",
        "uniform mat3 uInvHomography;",
        "varying vec2 vTexCoord;",
        "",
        "vec2 getEffectUv() {",
        "    vec3 p = uInvHomography * vec3(vTexCoord, 1.0);",
        "    vec2 uv = p.xy / max(abs(p.z), 0.0001);",
        "    vec2 d = uv - uPos;",
        "    d.x *= uResolution.x / uResolution.y;",
        "    float c = cos(-uRotation), s = sin(-uRotation);",
        "    d = mat2(c, -s, s, c) * d;",
        "    return d / max(uSize, vec2(0.01));",
        "}",
        "",
        "void main() {",
        "    vec4 video = texture2D(uTexture, vTexCoord);",
        "    vec2 e = getEffectUv();",
        "    float dist = length(e);",
        "    float pulse = 0.75 + 0.25 * sin(uTime * 6.0);",
        "    float glow = exp(-dist * 5.0) * pulse;",
        "    vec3 col = vec3(0.2, 0.85, 1.0) * glow;",
        "    gl_FragColor = vec4(clamp(video.rgb + col, 0.0, 1.0), video.a);",
        "}"
    ).joinToString("\n")

    private val DEFAULT_RING_SHADER: String = listOf(
        "precision mediump float;",
        "uniform sampler2D uTexture;",
        "uniform vec2 uResolution;",
        "uniform float uTime;",
        "uniform float uFrame;",
        "uniform vec2 uPos;",
        "uniform vec2 uSize;",
        "uniform float uRotation;",
        "uniform mat3 uInvHomography;",
        "varying vec2 vTexCoord;",
        "",
        "vec2 getEffectUv() {",
        "    vec3 p = uInvHomography * vec3(vTexCoord, 1.0);",
        "    vec2 uv = p.xy / max(abs(p.z), 0.0001);",
        "    vec2 d = uv - uPos;",
        "    d.x *= uResolution.x / uResolution.y;",
        "    float c = cos(-uRotation), s = sin(-uRotation);",
        "    d = mat2(c, -s, s, c) * d;",
        "    return d / max(uSize, vec2(0.01));",
        "}",
        "",
        "void main() {",
        "    vec4 video = texture2D(uTexture, vTexCoord);",
        "    vec2 e = getEffectUv();",
        "    float r = length(e);",
        "    float a = atan(e.y, e.x) + uTime * 3.0;",
        "    float ring = smoothstep(0.06, 0.0, abs(r - 0.38));",
        "    float arc = 0.5 + 0.5 * sin(a * 3.0);",
        "    vec3 neon = mix(vec3(1.0, 0.2, 0.7), vec3(0.2, 1.0, 0.8), arc);",
        "    gl_FragColor = vec4(clamp(video.rgb + neon * ring * 1.4, 0.0, 1.0), video.a);",
        "}"
    ).joinToString("\n")

    private val DEFAULT_WAVE_SHADER: String = listOf(
        "precision mediump float;",
        "uniform sampler2D uTexture;",
        "uniform vec2 uResolution;",
        "uniform float uTime;",
        "uniform float uFrame;",
        "uniform vec2 uPos;",
        "uniform vec2 uSize;",
        "uniform float uRotation;",
        "uniform mat3 uInvHomography;",
        "varying vec2 vTexCoord;",
        "",
        "vec2 getEffectUv() {",
        "    vec3 p = uInvHomography * vec3(vTexCoord, 1.0);",
        "    vec2 uv = p.xy / max(abs(p.z), 0.0001);",
        "    vec2 d = uv - uPos;",
        "    d.x *= uResolution.x / uResolution.y;",
        "    float c = cos(-uRotation), s = sin(-uRotation);",
        "    d = mat2(c, -s, s, c) * d;",
        "    return d / max(uSize, vec2(0.01));",
        "}",
        "",
        "void main() {",
        "    vec2 e = getEffectUv();",
        "    float dist = length(e);",
        "    float mask = smoothstep(0.55, 0.0, dist);",
        "    vec2 offset = normalize(e + vec2(0.0001)) * sin(dist * 24.0 - uTime * 10.0) * 0.02 * mask;",
        "    vec4 video = texture2D(uTexture, clamp(vTexCoord + offset, 0.0, 1.0));",
        "    gl_FragColor = video;",
        "}"
    ).joinToString("\n")

    fun loadPresets(context: Context): List<ShaderPreset> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_SEEDED, false)) {
            val defaults = listOf(
                ShaderPreset("Светящийся шар", DEFAULT_ORB_SHADER),
                ShaderPreset("Неоновое кольцо", DEFAULT_RING_SHADER),
                ShaderPreset("Волна искажения", DEFAULT_WAVE_SHADER)
            )
            saveAll(prefs, defaults)
            prefs.edit().putBoolean(KEY_SEEDED, true).apply()
            return defaults
        }

        val raw = prefs.getString(KEY_PRESETS, "[]") ?: "[]"
        val arr = JSONArray(raw)
        val out = ArrayList<ShaderPreset>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            out.add(ShaderPreset(obj.getString("name"), obj.getString("code")))
        }
        return out
    }

    fun savePreset(context: Context, name: String, code: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = loadPresets(context).toMutableList()
        val idx = current.indexOfFirst { it.name.equals(name, ignoreCase = true) }
        if (idx >= 0) {
            current[idx] = ShaderPreset(name, code)
        } else {
            current.add(0, ShaderPreset(name, code))
        }
        saveAll(prefs, current)
    }

    private fun saveAll(prefs: android.content.SharedPreferences, list: List<ShaderPreset>) {
        val arr = JSONArray()
        for (p in list) {
            arr.put(JSONObject().apply {
                put("name", p.name)
                put("code", p.code)
            })
        }
        prefs.edit().putString(KEY_PRESETS, arr.toString()).apply()
    }
}
