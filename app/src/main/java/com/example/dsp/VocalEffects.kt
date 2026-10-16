package com.example.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * High-performance DSP Vocal Effects Suite for AutoTune Studio Pro.
 * All algorithms are zero-allocation during the real-time audio loop.
 */

// ==========================================
// 1. STUDIO COMPRESSOR
// ==========================================
class StudioCompressor(
    private val sampleRate: Int = 44100
) {
    var isEnabled: Boolean = true
    var thresholdDb: Float = -18.0f // -40dB to 0dB
    var ratio: Float = 4.0f         // 1:1 to 20:1
    var attackMs: Float = 15.0f     // 1ms to 100ms
    var releaseMs: Float = 120.0f   // 20ms to 500ms
    var makeupGainDb: Float = 3.0f   // 0dB to +18dB

    private var envelope: Float = 0.0f
    private var attackCoeff: Float = 0.0f
    private var releaseCoeff: Float = 0.0f

    init {
        updateCoefficients()
    }

    fun updateCoefficients() {
        attackCoeff = exp(-1.0f / (sampleRate * (attackMs / 1000.0f)))
        releaseCoeff = exp(-1.0f / (sampleRate * (releaseMs / 1000.0f)))
    }

    fun process(samples: FloatArray, length: Int) {
        if (!isEnabled) return

        val makeupLinear = 10.0f.pow(makeupGainDb / 20.0f)

        for (i in 0 until length) {
            val input = samples[i]
            val absInput = kotlin.math.abs(input)

            // Envelope follower
            envelope = if (absInput > envelope) {
                attackCoeff * envelope + (1.0f - attackCoeff) * absInput
            } else {
                releaseCoeff * envelope + (1.0f - releaseCoeff) * absInput
            }

            // dB calculation with safety clamp
            val envDb = if (envelope > 0.00001f) 20.0f * log10(envelope) else -100.0f

            // Gain computer
            var gainDb = 0.0f
            if (envDb > thresholdDb) {
                gainDb = (thresholdDb + (envDb - thresholdDb) / ratio) - envDb
            }

            val gainLinear = 10.0f.pow(gainDb / 20.0f) * makeupLinear
            samples[i] = input * gainLinear
        }
    }
}

// ==========================================
// 2. 3-BAND PARAMETRIC EQUALIZER (BIQUAD)
// ==========================================
class StudioEqualizer(
    private val sampleRate: Int = 44100
) {
    var isEnabled: Boolean = true
    var lowGainDb: Float = 0.0f  // -12dB to +12dB (Low Shelf @ 200 Hz)
    var midGainDb: Float = 0.0f  // -12dB to +12dB (Peaking @ 2.5 kHz, Vocal Clarity)
    var highGainDb: Float = 0.0f // -12dB to +12dB (High Shelf @ 8.0 kHz, Air/Shine)

    private class Biquad {
        var b0 = 1.0f; var b1 = 0.0f; var b2 = 0.0f
        var a1 = 0.0f; var a2 = 0.0f
        var x1 = 0.0f; var x2 = 0.0f
        var y1 = 0.0f; var y2 = 0.0f

        fun process(inSample: Float): Float {
            val out = b0 * inSample + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = inSample
            y2 = y1
            y1 = out
            return out
        }

        fun reset() {
            x1 = 0.0f; x2 = 0.0f; y1 = 0.0f; y2 = 0.0f
        }
    }

    private val lowShelf = Biquad()
    private val midPeak = Biquad()
    private val highShelf = Biquad()

    init {
        updateFilters()
    }

    fun updateFilters() {
        computeLowShelf(lowGainDb, 200.0f, lowShelf)
        computePeaking(midGainDb, 2500.0f, 1.0f, midPeak)
        computeHighShelf(highGainDb, 8000.0f, highShelf)
    }

    private fun computeLowShelf(gainDb: Float, freq: Float, f: Biquad) {
        val a = 10.0f.pow(gainDb / 40.0f)
        val w0 = 2.0 * PI * (freq / sampleRate)
        val cosW = cos(w0).toFloat()
        val sinW = sin(w0).toFloat()
        val alpha = sinW / 2.0f * sqrt(2.0f)
        val twoSqrtAAlpha = 2.0f * sqrt(a) * alpha

        val a0 = (a + 1.0f) + (a - 1.0f) * cosW + twoSqrtAAlpha
        f.b0 = (a * ((a + 1.0f) - (a - 1.0f) * cosW + twoSqrtAAlpha)) / a0
        f.b1 = (2.0f * a * ((a - 1.0f) - (a + 1.0f) * cosW)) / a0
        f.b2 = (a * ((a + 1.0f) - (a - 1.0f) * cosW - twoSqrtAAlpha)) / a0
        f.a1 = (-2.0f * ((a - 1.0f) + (a + 1.0f) * cosW)) / a0
        f.a2 = ((a + 1.0f) + (a - 1.0f) * cosW - twoSqrtAAlpha) / a0
    }

    private fun computePeaking(gainDb: Float, freq: Float, q: Float, f: Biquad) {
        val a = 10.0f.pow(gainDb / 40.0f)
        val w0 = 2.0 * PI * (freq / sampleRate)
        val cosW = cos(w0).toFloat()
        val sinW = sin(w0).toFloat()
        val alpha = (sinW / (2.0f * q))

        val a0 = 1.0f + alpha / a
        f.b0 = (1.0f + alpha * a) / a0
        f.b1 = (-2.0f * cosW) / a0
        f.b2 = (1.0f - alpha * a) / a0
        f.a1 = (-2.0f * cosW) / a0
        f.a2 = (1.0f - alpha / a) / a0
    }

    private fun computeHighShelf(gainDb: Float, freq: Float, f: Biquad) {
        val a = 10.0f.pow(gainDb / 40.0f)
        val w0 = 2.0 * PI * (freq / sampleRate)
        val cosW = cos(w0).toFloat()
        val sinW = sin(w0).toFloat()
        val alpha = sinW / 2.0f * sqrt(2.0f)
        val twoSqrtAAlpha = 2.0f * sqrt(a) * alpha

        val a0 = (a + 1.0f) - (a - 1.0f) * cosW + twoSqrtAAlpha
        f.b0 = (a * ((a + 1.0f) + (a - 1.0f) * cosW + twoSqrtAAlpha)) / a0
        f.b1 = (-2.0f * a * ((a - 1.0f) + (a + 1.0f) * cosW)) / a0
        f.b2 = (a * ((a + 1.0f) + (a - 1.0f) * cosW - twoSqrtAAlpha)) / a0
        f.a1 = (2.0f * ((a - 1.0f) - (a + 1.0f) * cosW)) / a0
        f.a2 = ((a + 1.0f) - (a - 1.0f) * cosW - twoSqrtAAlpha) / a0
    }

    fun process(samples: FloatArray, length: Int) {
        if (!isEnabled) return
        for (i in 0 until length) {
            val s0 = samples[i]
            val s1 = lowShelf.process(s0)
            val s2 = midPeak.process(s1)
            val s3 = highShelf.process(s2)
            samples[i] = s3
        }
    }
}

// ==========================================
// 3. STUDIO REVERB (SCHROEDER / FREEVERB)
// ==========================================
class StudioReverb(
    sampleRate: Int = 44100
) {
    var isEnabled: Boolean = true
    var roomSize: Float = 0.65f // 0.0 to 0.98
    var damping: Float = 0.35f  // 0.0 to 1.0
    var wetMix: Float = 0.30f   // 0.0 to 1.0

    // Comb filters with prime delays
    private class Comb(size: Int) {
        val buffer = FloatArray(size)
        var bufIdx = 0
        var filterStore = 0.0f

        fun process(input: Float, feedback: Float, damp: Float): Float {
            val output = buffer[bufIdx]
            filterStore = output * (1.0f - damp) + filterStore * damp
            buffer[bufIdx] = input + filterStore * feedback
            if (++bufIdx >= buffer.size) bufIdx = 0
            return output
        }
    }

    // Allpass diffusers
    private class Allpass(size: Int) {
        val buffer = FloatArray(size)
        var bufIdx = 0

        fun process(input: Float): Float {
            val bufOut = buffer[bufIdx]
            val output = -input + bufOut
            buffer[bufIdx] = input + (bufOut * 0.5f)
            if (++bufIdx >= buffer.size) bufIdx = 0
            return output
        }
    }

    private val combs = arrayOf(
        Comb(1116), Comb(1188), Comb(1277), Comb(1356),
        Comb(1422), Comb(1491), Comb(1557), Comb(1617)
    )
    private val allpasses = arrayOf(
        Allpass(556), Allpass(441), Allpass(341), Allpass(225)
    )

    fun process(samples: FloatArray, length: Int) {
        if (!isEnabled || wetMix <= 0.01f) return

        val feedback = (0.7f + roomSize * 0.28f).coerceIn(0.0f, 0.98f)
        val damp = damping.coerceIn(0.0f, 0.95f)
        val dryGain = 1.0f - (wetMix * 0.5f)
        val wetGain = wetMix * 0.35f

        for (i in 0 until length) {
            val dry = samples[i]
            var combSum = 0.0f

            for (c in combs) {
                combSum += c.process(dry, feedback, damp)
            }

            var diff = combSum
            for (ap in allpasses) {
                diff = ap.process(diff)
            }

            samples[i] = (dry * dryGain) + (diff * wetGain)
        }
    }
}

// ==========================================
// 4. STUDIO ECHO / DELAY
// ==========================================
class StudioDelay(
    private val sampleRate: Int = 44100
) {
    var isEnabled: Boolean = true
    var delayTimeMs: Float = 250.0f // 50ms to 800ms
    var feedback: Float = 0.35f     // 0.0 to 0.85
    var wetMix: Float = 0.25f       // 0.0 to 1.0

    private val maxDelaySamples = (sampleRate * 1.0).toInt() // up to 1 second
    private val delayBuffer = FloatArray(maxDelaySamples)
    private var writeIdx = 0
    private var filterLp = 0.0f

    fun process(samples: FloatArray, length: Int) {
        if (!isEnabled || wetMix <= 0.01f) return

        val delaySamples = (sampleRate * (delayTimeMs / 1000.0f)).toInt().coerceIn(1, maxDelaySamples - 1)
        val fb = feedback.coerceIn(0.0f, 0.85f)
        val dryGain = 1.0f - (wetMix * 0.4f)
        val wetGain = wetMix

        for (i in 0 until length) {
            val dry = samples[i]

            var readIdx = writeIdx - delaySamples
            if (readIdx < 0) readIdx += maxDelaySamples

            val delayedSample = delayBuffer[readIdx]

            // Warm low-pass damping on feedback path
            filterLp = filterLp * 0.3f + delayedSample * 0.7f

            delayBuffer[writeIdx] = dry + (filterLp * fb)
            if (++writeIdx >= maxDelaySamples) writeIdx = 0

            samples[i] = (dry * dryGain) + (delayedSample * wetGain)
        }
    }
}

// ==========================================
// 5. VOCAL DOUBLER / CHORUS
// ==========================================
class VocalDoubler(
    private val sampleRate: Int = 44100
) {
    var isEnabled: Boolean = false
    var width: Float = 0.60f       // 0.0 to 1.0
    var detuneCents: Float = 8.0f  // 2 to 20 cents

    private val delayBufSize = (sampleRate * 0.06).toInt() // 60ms buffer
    private val delayBuffer = FloatArray(delayBufSize)
    private var writeIdx = 0
    private var lfoPhase = 0.0

    fun process(samples: FloatArray, length: Int) {
        if (!isEnabled || width <= 0.02f) return

        val baseDelay = (sampleRate * 0.018).toInt() // ~18ms base
        val modDepth = (sampleRate * 0.003 * (detuneCents / 10.0f)).toInt().coerceAtLeast(1)
        val lfoRate = 2.0 * PI * 0.8 / sampleRate // 0.8 Hz LFO

        for (i in 0 until length) {
            val dry = samples[i]
            delayBuffer[writeIdx] = dry

            val lfo = sin(lfoPhase).toFloat()
            lfoPhase += lfoRate
            if (lfoPhase > 2.0 * PI) lfoPhase -= 2.0 * PI

            val curDelay = baseDelay + (lfo * modDepth).toInt()
            var readIdx = writeIdx - curDelay
            while (readIdx < 0) readIdx += delayBufSize

            val delayed = delayBuffer[readIdx]
            // Doubled output: dry + width * delayed
            samples[i] = dry * (1.0f - width * 0.25f) + (delayed * width * 0.7f)

            if (++writeIdx >= delayBufSize) writeIdx = 0
        }
    }
}

// ==========================================
// 6. MASTER SOFT LIMITER
// ==========================================
object SoftLimiter {
    fun process(samples: FloatArray, length: Int) {
        for (i in 0 until length) {
            val x = samples[i]
            if (kotlin.math.abs(x) > 0.90f) {
                samples[i] = tanh(x)
            }
        }
    }
}
