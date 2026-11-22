package com.idex.android.audio

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

/**
 * "Anime Pokédex" voice processor.
 *
 * The TV-series Pokédex (Dexter) sounds like a calm male voice passed through a
 * small speaker: narrow band, slightly metallic, with a faint robotic shimmer.
 * We approximate that offline, without any copyrighted voice samples:
 *
 *   system TTS (lower pitch) → band-pass (small speaker) → ring-mod shimmer
 *   → short comb (metal body) → soft saturation → normalize
 *
 * All parameters live in [VoiceStyle] so presets can be tuned by ear.
 */
data class VoiceStyle(
    val id: String,
    /** TextToSpeech.setPitch — <1 = deeper. */
    val ttsPitch: Float,
    /** TextToSpeech.setSpeechRate. */
    val ttsRate: Float,
    val highPassHz: Double,
    val lowPassHz: Double,
    /** Ring-modulator carrier frequency; 0 disables. */
    val ringHz: Double,
    /** 0..1 wet amount of ring modulation. */
    val ringMix: Double,
    /** Comb delay in ms; 0 disables. */
    val combMs: Double,
    val combFeedback: Double,
    val combMix: Double,
    /** tanh drive; 1 = nearly clean. */
    val drive: Double,
) {
    val isClean get() = ringMix == 0.0 && combMix == 0.0 && highPassHz <= 0 && lowPassHz <= 0 && drive <= 1.0

    companion object {
        /** Default: calm, deep, small-speaker, light robotic shimmer. */
        val ANIME_DEX = VoiceStyle(
            id = "anime", ttsPitch = 0.78f, ttsRate = 1.0f,
            highPassHz = 220.0, lowPassHz = 3600.0,
            ringHz = 42.0, ringMix = 0.28,
            combMs = 6.5, combFeedback = 0.35, combMix = 0.30,
            drive = 1.6,
        )

        /** Stronger robot (closer to Rotom-style gadget). */
        val ROBOT = VoiceStyle(
            id = "robot", ttsPitch = 0.9f, ttsRate = 1.05f,
            highPassHz = 300.0, lowPassHz = 3200.0,
            ringHz = 70.0, ringMix = 0.5,
            combMs = 4.0, combFeedback = 0.5, combMix = 0.45,
            drive = 2.2,
        )

        /** Plain system voice. */
        val NATURAL = VoiceStyle(
            id = "natural", ttsPitch = 1f, ttsRate = 1f,
            highPassHz = 0.0, lowPassHz = 0.0, ringHz = 0.0, ringMix = 0.0,
            combMs = 0.0, combFeedback = 0.0, combMix = 0.0, drive = 1.0,
        )

        val ALL = listOf(ANIME_DEX, ROBOT, NATURAL)
        fun byId(id: String?) = ALL.firstOrNull { it.id == id } ?: ANIME_DEX
    }
}

/** Mono PCM16 buffer. */
class Pcm(val samples: ShortArray, val sampleRate: Int)

object RoboVoice {
    private const val MAX_WAV_BYTES = 32 * 1024 * 1024

    fun process(input: Pcm, style: VoiceStyle): Pcm {
        if (style.isClean || input.samples.isEmpty() || input.sampleRate <= 0) return input
        val sr = input.sampleRate.toDouble()
        val x = DoubleArray(input.samples.size) { input.samples[it] / 32768.0 }

        // 1. Small-speaker band: 2nd-order HP + LP (applied twice = 4th order slope).
        if (style.highPassHz > 0) repeat(2) { Biquad.highPass(style.highPassHz, sr).run(x) }
        if (style.lowPassHz > 0) repeat(2) { Biquad.lowPass(style.lowPassHz, sr).run(x) }

        // 2. Ring modulation shimmer.
        if (style.ringHz > 0 && style.ringMix > 0) {
            val w = 2 * PI * style.ringHz / sr
            for (i in x.indices) {
                val carrier = sin(w * i)
                x[i] = x[i] * (1 - style.ringMix) + x[i] * carrier * style.ringMix
            }
        }

        // 3. Feedback comb → metallic body.
        if (style.combMs > 0 && style.combMix > 0) {
            val d = (style.combMs / 1000.0 * sr).toInt().coerceAtLeast(1)
            val buf = DoubleArray(d)
            var idx = 0
            for (i in x.indices) {
                val delayed = buf[idx]
                buf[idx] = x[i] + delayed * style.combFeedback
                idx = (idx + 1) % d
                x[i] = x[i] * (1 - style.combMix) + delayed * style.combMix
            }
        }

        // 4. Soft saturation.
        if (style.drive > 1.0) {
            val norm = tanh(style.drive)
            for (i in x.indices) x[i] = tanh(x[i] * style.drive) / norm
        }

        // 5. Normalize to -1 dBFS.
        var peak = 1e-9
        for (v in x) peak = maxOf(peak, abs(v))
        val gain = 0.89 / peak
        val out = ShortArray(x.size) { (x[it] * gain * 32767).toInt().coerceIn(-32768, 32767).toShort() }
        return Pcm(out, input.sampleRate)
    }

    /** Minimal RIFF/WAVE reader for TTS output (PCM 8/16-bit, mono or stereo → mono). */
    fun readWav(file: File): Pcm {
        require(file.length() in 45..MAX_WAV_BYTES.toLong()) { "Invalid WAV size" }
        return readWav(file.readBytes())
    }

    /** Parse an in-memory RIFF/WAVE byte buffer with strict bounds and PCM validation. */
    fun readWav(bytes: ByteArray): Pcm {
        require(bytes.size in 45..MAX_WAV_BYTES) { "Invalid WAV size" }
        require(
            String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                String(bytes, 8, 4, Charsets.US_ASCII) == "WAVE",
        ) { "Not a WAV file" }

        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var pos = 12L
        var fmtSeen = false
        var channels = 0
        var sampleRate = 0
        var bits = 0
        var dataStart = -1
        var dataLen = 0

        while (pos + 8L <= bytes.size.toLong()) {
            val posInt = pos.toInt()
            val id = String(bytes, posInt, 4, Charsets.US_ASCII)
            val rawLen = bb.getInt(posInt + 4).toLong() and 0xFFFFFFFFL
            val body = pos + 8L
            when (id) {
                "fmt " -> {
                    require(rawLen >= 16L && body + 16L <= bytes.size.toLong()) { "Truncated WAV fmt chunk" }
                    val bodyInt = body.toInt()
                    val audioFormat = bb.getShort(bodyInt).toInt() and 0xFFFF
                    require(audioFormat == 1) { "Unsupported WAV format tag: $audioFormat" }
                    channels = bb.getShort(bodyInt + 2).toInt() and 0xFFFF
                    sampleRate = bb.getInt(bodyInt + 4)
                    bits = bb.getShort(bodyInt + 14).toInt() and 0xFFFF
                    require(channels in 1..2) { "Unsupported channel count: $channels" }
                    require(sampleRate in 8000..192000) { "Unsupported sample rate: $sampleRate" }
                    require(bits == 8 || bits == 16) { "Unsupported bit depth: $bits" }
                    fmtSeen = true
                }
                "data" -> {
                    require(fmtSeen) { "WAV data chunk before fmt chunk" }
                    dataStart = body.toInt()
                    val available = bytes.size.toLong() - body
                    // Some TTS engines leave data size 0 while streaming; clamp to available bytes.
                    dataLen = if (rawLen == 0L || rawLen > available) available.toInt() else rawLen.toInt()
                    break
                }
            }
            val padded = rawLen + (rawLen and 1L)
            val next = body + padded
            require(next > pos && next <= bytes.size.toLong()) { "Invalid WAV chunk size for '$id'" }
            pos = next
        }

        require(fmtSeen && dataStart >= 0) { "WAV has no valid fmt/data chunks" }
        val bytesPerSample = bits / 8
        val frameBytes = bytesPerSample * channels
        val frames = dataLen / frameBytes
        val out = ShortArray(frames)
        for (f in 0 until frames) {
            var acc = 0
            for (c in 0 until channels) {
                val p = dataStart + f * frameBytes + c * bytesPerSample
                acc += if (bits == 16) bb.getShort(p).toInt() else ((bytes[p].toInt() and 0xFF) - 128) shl 8
            }
            out[f] = (acc / channels).toShort()
        }
        return Pcm(out, sampleRate)
    }

    /** RBJ cookbook biquad, Direct Form I, in place. */
    private class Biquad(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double) {
        fun run(x: DoubleArray) {
            var x1 = 0.0; var x2 = 0.0; var y1 = 0.0; var y2 = 0.0
            for (i in x.indices) {
                val x0 = x[i]
                val y0 = b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
                x2 = x1; x1 = x0; y2 = y1; y1 = y0
                x[i] = y0
            }
        }

        companion object {
            private const val Q = 0.7071

            fun lowPass(f: Double, sr: Double): Biquad {
                val w = 2 * PI * f.coerceAtMost(sr * 0.45) / sr
                val alpha = sin(w) / (2 * Q)
                val c = cos(w)
                val a0 = 1 + alpha
                return Biquad((1 - c) / 2 / a0, (1 - c) / a0, (1 - c) / 2 / a0, -2 * c / a0, (1 - alpha) / a0)
            }

            fun highPass(f: Double, sr: Double): Biquad {
                val w = 2 * PI * f / sr
                val alpha = sin(w) / (2 * Q)
                val c = cos(w)
                val a0 = 1 + alpha
                return Biquad((1 + c) / 2 / a0, -(1 + c) / a0, (1 + c) / 2 / a0, -2 * c / a0, (1 - alpha) / a0)
            }
        }
    }
}
