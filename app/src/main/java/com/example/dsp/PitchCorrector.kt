package com.example.dsp

import kotlin.math.PI
import kotlin.math.sin

/**
 * Real-time DSP Pitch Shifter and Auto-Tune Corrector.
 * Uses dual-modulated delay lines with smooth cross-fading window (zero allocation).
 */
class PitchCorrector(
    private val sampleRate: Int = 44100,
    private val windowSize: Int = 2048 // ~46ms window at 44.1kHz
) {
    // Circular delay buffer
    private val delayBuffer = FloatArray(windowSize * 2)
    private var writeIndex = 0

    // Two read heads with 180-degree phase offset
    private var readPhaseA = 0.0
    private var readPhaseB = windowSize / 2.0

    // Smooth filtered pitch ratio
    private var currentPitchRatio = 1.0
    private var targetPitchRatio = 1.0

    // Pre-calculated Hann window for cross-fading
    private val windowA = FloatArray(windowSize)
    private val windowB = FloatArray(windowSize)

    init {
        for (i in 0 until windowSize) {
            val phase = (i.toDouble() / windowSize) * 2.0 * PI
            // Half-cosine window
            val w = (0.5 * (1.0 - kotlin.math.cos(phase))).toFloat()
            windowA[i] = w
        }
    }

    /**
     * Updates target pitch ratio from musical scale snap.
     * @param targetRatio target frequency / detected frequency
     * @param speedCorrectionSpeed 0.0f (slow/natural glide) to 1.0f (instant robotic snap)
     */
    fun setTargetRatio(targetRatio: Double, speedCorrectionSpeed: Float) {
        val clampedRatio = targetRatio.coerceIn(0.5, 2.0) // +/- 1 octave max
        this.targetPitchRatio = clampedRatio
        // Fast snap: speed closer to 1.0 has minimal inertia
        val inertia = (1.0f - speedCorrectionSpeed.coerceIn(0.0f, 1.0f)) * 0.92f
        if (speedCorrectionSpeed >= 0.95f) {
            currentPitchRatio = targetPitchRatio
        } else {
            currentPitchRatio = (currentPitchRatio * inertia) + (targetPitchRatio * (1.0 - inertia))
        }
    }

    fun reset() {
        delayBuffer.fill(0.0f)
        writeIndex = 0
        readPhaseA = 0.0
        readPhaseB = windowSize / 2.0
        currentPitchRatio = 1.0
        targetPitchRatio = 1.0
    }

    /**
     * Processes audio block in-place with zero memory allocation.
     */
    fun process(buffer: FloatArray, length: Int) {
        val bufferLen = delayBuffer.size
        val halfWindow = windowSize / 2.0

        for (i in 0 until length) {
            val inSample = buffer[i]

            // Write into circular buffer
            delayBuffer[writeIndex] = inSample

            // When pitch ratio is 1.0 (no shift), output directly for pristine clarity
            if (kotlin.math.abs(currentPitchRatio - 1.0) < 0.005) {
                // writeIndex stays moving, read stays in sync
                writeIndex = (writeIndex + 1) % bufferLen
                continue
            }

            // Dual delay read positions
            val delayA = readPhaseA
            val delayB = readPhaseB

            // Interpolated read from delayBuffer
            val outA = readInterpolated(writeIndex - delayA, bufferLen)
            val outB = readInterpolated(writeIndex - delayB, bufferLen)

            // Cross-fading triangular / sine weighting
            val normPhaseA = (readPhaseA / windowSize).coerceIn(0.0, 1.0)
            val weightA = sin(normPhaseA * PI).toFloat()
            val normPhaseB = (readPhaseB / windowSize).coerceIn(0.0, 1.0)
            val weightB = sin(normPhaseB * PI).toFloat()

            val normSum = (weightA + weightB).coerceAtLeast(0.0001f)
            val mixedOut = (outA * weightA + outB * weightB) / normSum

            buffer[i] = mixedOut

            // Advance phases: slope is (1.0 - ratio)
            val rate = 1.0 - currentPitchRatio
            readPhaseA += rate
            readPhaseB += rate

            // Wrap phases inside window
            while (readPhaseA < 0.0) readPhaseA += windowSize
            while (readPhaseA >= windowSize) readPhaseA -= windowSize

            while (readPhaseB < 0.0) readPhaseB += windowSize
            while (readPhaseB >= windowSize) readPhaseB -= windowSize

            writeIndex = (writeIndex + 1) % bufferLen
        }
    }

    private fun readInterpolated(rawPos: Double, bufLen: Int): Float {
        var pos = rawPos
        while (pos < 0.0) pos += bufLen
        while (pos >= bufLen) pos -= bufLen

        val idx0 = pos.toInt() % bufLen
        val idx1 = (idx0 + 1) % bufLen
        val frac = (pos - idx0).toFloat()

        val s0 = delayBuffer[idx0]
        val s1 = delayBuffer[idx1]
        return s0 + frac * (s1 - s0)
    }
}
