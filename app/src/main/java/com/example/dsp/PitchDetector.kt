package com.example.dsp

import kotlin.math.sqrt

/**
 * Real-time zero-allocation pitch detector using Normalized Square Difference Function (NSDF)
 * with parabolic peak interpolation (McLeod Pitch Method / YIN principles).
 */
class PitchDetector(
    private val sampleRate: Int = 44100,
    private val bufferSize: Int = 1024
) {
    // Vocal pitch range: 75 Hz (deep bass) to 1000 Hz (high soprano)
    private val minPeriod = (sampleRate / 1000).coerceAtLeast(20)
    private val maxPeriod = (sampleRate / 75).coerceAtMost(bufferSize / 2)

    // Pre-allocated buffers to prevent any garbage collection in the real-time audio loop
    private val nsdfBuffer = FloatArray(maxPeriod + 2)

    var lastDetectedPitchHz: Double = 0.0
        private set
    var lastConfidence: Float = 0.0f
        private set

    /**
     * Detects fundamental frequency F0 in samples array.
     * @param samples Audio buffer normalized between -1.0f and 1.0f
     * @param length Number of valid samples
     * @return Detected pitch in Hz, or 0.0 if unvoiced/silence
     */
    fun detectPitch(samples: FloatArray, length: Int): Double {
        if (length < maxPeriod * 2) {
            lastDetectedPitchHz = 0.0
            lastConfidence = 0.0f
            return 0.0
        }

        // 1. Calculate RMS to reject background noise / silence
        var sumSquares = 0.0f
        val windowLen = length - maxPeriod
        for (i in 0 until windowLen) {
            val s = samples[i]
            sumSquares += s * s
        }
        val rms = sqrt(sumSquares / windowLen)
        if (rms < 0.008f) { // Silence or very quiet whisper
            lastDetectedPitchHz = 0.0
            lastConfidence = 0.0f
            return 0.0
        }

        // 2. Compute NSDF for tau between minPeriod and maxPeriod
        for (tau in minPeriod..maxPeriod) {
            var acf = 0.0f
            var energy = 0.0f
            for (i in 0 until windowLen) {
                val x1 = samples[i]
                val x2 = samples[i + tau]
                acf += x1 * x2
                energy += (x1 * x1) + (x2 * x2)
            }
            nsdfBuffer[tau] = if (energy > 0.00001f) (2.0f * acf) / energy else 0.0f
        }

        // 3. Peak picking: find maximum positive peak with clarity threshold
        var bestTau = -1
        var maxPeak = 0.45f // Minimum confidence threshold for human voice
        var isAscending = false

        for (tau in (minPeriod + 1)..maxPeriod) {
            val curr = nsdfBuffer[tau]
            val prev = nsdfBuffer[tau - 1]

            if (curr > prev) {
                isAscending = true
            } else if (isAscending && prev > maxPeak) {
                // We found a local maximum at (tau - 1)
                bestTau = tau - 1
                maxPeak = prev
                break // First strong peak corresponding to the fundamental period
            }
        }

        if (bestTau <= 0 || maxPeak < 0.45f) {
            lastDetectedPitchHz = 0.0
            lastConfidence = 0.0f
            return 0.0
        }

        // 4. Parabolic interpolation for sub-sample accuracy
        val alpha = nsdfBuffer[bestTau - 1]
        val beta = nsdfBuffer[bestTau]
        val gamma = nsdfBuffer[bestTau + 1]
        val denominator = 2.0f * (2.0f * beta - alpha - gamma)

        val deltaTau = if (kotlin.math.abs(denominator) > 0.00001f) {
            (gamma - alpha) / denominator
        } else {
            0.0f
        }

        val trueTau = (bestTau + deltaTau).toDouble()
        if (trueTau <= 0.0) {
            lastDetectedPitchHz = 0.0
            lastConfidence = 0.0f
            return 0.0
        }

        val pitchHz = sampleRate.toDouble() / trueTau
        if (pitchHz in 70.0..1100.0) {
            lastDetectedPitchHz = pitchHz
            lastConfidence = maxPeak.coerceIn(0.0f, 1.0f)
            return pitchHz
        }

        lastDetectedPitchHz = 0.0
        lastConfidence = 0.0f
        return 0.0
    }
}
