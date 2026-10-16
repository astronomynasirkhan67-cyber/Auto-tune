package com.example.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Process
import android.util.Log
import com.example.dsp.MusicalScale
import com.example.dsp.PitchCorrector
import com.example.dsp.PitchDetector
import com.example.dsp.ScaleType
import com.example.dsp.SoftLimiter
import com.example.dsp.StudioCompressor
import com.example.dsp.StudioDelay
import com.example.dsp.StudioEqualizer
import com.example.dsp.StudioPreset
import com.example.dsp.StudioReverb
import com.example.dsp.VocalDoubler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.pow
import kotlin.math.sqrt

enum class RecordingState {
    IDLE,
    RECORDING,
    PAUSED,
    STOPPED
}

data class PitchFeedback(
    val detectedPitchHz: Double = 0.0,
    val targetPitchHz: Double = 0.0,
    val noteName: String = "--",
    val centsDiff: Double = 0.0,
    val confidence: Float = 0.0f
)

data class LiveAudioMetrics(
    val inputRmsDb: Float = -100.0f,
    val peakAmplitude: Float = 0.0f,
    val waveformPoints: List<Float> = emptyList(),
    val recordingDurationMs: Long = 0L
)

class LiveAudioEngine(
    private val context: Context
) {
    companion object {
        private const val TAG = "LiveAudioEngine"
        const val SAMPLE_RATE = 44100
        const val CHUNK_SIZE = 1024 // ~23.2ms latency buffer
    }

    // Audio DSP Modules
    val pitchDetector = PitchDetector(SAMPLE_RATE, CHUNK_SIZE)
    val pitchCorrector = PitchCorrector(SAMPLE_RATE, 2048)
    val compressor = StudioCompressor(SAMPLE_RATE)
    val equalizer = StudioEqualizer(SAMPLE_RATE)
    val reverb = StudioReverb(SAMPLE_RATE)
    val delay = StudioDelay(SAMPLE_RATE)
    val doubler = VocalDoubler(SAMPLE_RATE)

    // AutoTune Settings
    @Volatile var isAutoTuneEnabled: Boolean = true
    @Volatile var isHardAutoTune: Boolean = true
    @Volatile var rootKeyIndex: Int = 0 // 0 = C
    @Volatile var scaleType: ScaleType = ScaleType.MAJOR
    @Volatile var correctionStrength: Float = 1.0f // 0.0 to 1.0
    @Volatile var pitchSpeed: Float = 0.95f        // 0.0 (slow) to 1.0 (instant snap)
    @Volatile var manualPitchShiftSemitones: Int = 0

    // Volume & Monitor controls
    @Volatile var vocalVolume: Float = 1.0f
    @Volatile var beatVolume: Float = 0.85f
    @Volatile var masterVolume: Float = 1.0f
    @Volatile var isLiveMonitorEnabled: Boolean = false // Default off to prevent speaker feedback

    // Recording and Thread State
    private val isAudioRunning = AtomicBoolean(false)
    private var audioProcessingThread: Thread? = null

    // Background Beat Player
    private var beatMediaPlayer: MediaPlayer? = null
    private var currentBeatFile: File? = null

    // Recording State
    private val _recordingState = MutableStateFlow(RecordingState.IDLE)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    private val _pitchFeedback = MutableStateFlow(PitchFeedback())
    val pitchFeedback: StateFlow<PitchFeedback> = _pitchFeedback.asStateFlow()

    private val _audioMetrics = MutableStateFlow(LiveAudioMetrics())
    val audioMetrics: StateFlow<LiveAudioMetrics> = _audioMetrics.asStateFlow()

    // Recording File Streams
    private var wetRecordingFile: File? = null
    private var dryRecordingFile: File? = null
    private var wetOutputStream: FileOutputStream? = null
    private var dryOutputStream: FileOutputStream? = null
    private var recordedPcmBytes = 0L

    private var recordingStartTime = 0L
    private var recordingElapsedTime = 0L
    private var isRecordingPaused = false

    // Waveform Ring Buffer for UI
    private val waveformBuffer = FloatArray(32)
    private var waveformCounter = 0

    // Pre-allocated buffers for zero-allocation audio loop
    private val inputShorts = ShortArray(CHUNK_SIZE)
    private val floatBuffer = FloatArray(CHUNK_SIZE)
    private val outputShorts = ShortArray(CHUNK_SIZE)

    fun applyPreset(preset: StudioPreset) {
        isHardAutoTune = preset.isHardAutoTune
        correctionStrength = preset.correctionStrength
        pitchSpeed = preset.pitchSpeed

        reverb.isEnabled = preset.reverbEnabled
        reverb.roomSize = preset.reverbRoomSize
        reverb.wetMix = preset.reverbWet

        delay.isEnabled = preset.delayEnabled
        delay.delayTimeMs = preset.delayTimeMs
        delay.wetMix = preset.delayWet

        compressor.isEnabled = preset.compressorEnabled
        compressor.thresholdDb = preset.compressorThresholdDb
        compressor.ratio = preset.compressorRatio
        compressor.updateCoefficients()

        equalizer.lowGainDb = preset.eqLowGainDb
        equalizer.midGainDb = preset.eqMidGainDb
        equalizer.highGainDb = preset.eqHighGainDb
        equalizer.updateFilters()

        doubler.isEnabled = preset.doublerEnabled
        doubler.width = preset.doublerWidth

        manualPitchShiftSemitones = preset.manualPitchShiftSemitones
    }

    @SuppressLint("MissingPermission")
    fun startEngine(): Boolean {
        if (isAudioRunning.get()) return true

        val minRecBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val recBufSize = maxOf(minRecBuf, CHUNK_SIZE * 4)

        val minTrackBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val trackBufSize = maxOf(minTrackBuf, CHUNK_SIZE * 4)

        var audioRecord: AudioRecord? = null
        var audioTrack: AudioTrack? = null

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                recBufSize
            )

            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                // Fallback to MIC if VOICE_RECOGNITION isn't available
                audioRecord.release()
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    recBufSize
                )
            }

            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setFlags(AudioAttributes.FLAG_LOW_LATENCY)
                .build()

            val format = AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build()

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(trackBufSize)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()

            if (audioRecord.state != AudioRecord.STATE_INITIALIZED || audioTrack.state != AudioTrack.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord or AudioTrack failed to initialize")
                audioRecord.release()
                audioTrack.release()
                return false
            }

            audioRecord.startRecording()
            audioTrack.play()

            isAudioRunning.set(true)

            // Start dedicated audio processing thread
            val thread = Thread({
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                runAudioProcessingLoop(audioRecord, audioTrack)
            }, "LiveAudioDSPThread")

            audioProcessingThread = thread
            thread.start()
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting live audio engine", e)
            try { audioRecord?.release() } catch (ignored: Exception) {}
            try { audioTrack?.release() } catch (ignored: Exception) {}
            return false
        }
    }

    fun stopEngine() {
        isAudioRunning.set(false)
        audioProcessingThread?.interrupt()
        try {
            audioProcessingThread?.join(500)
        } catch (ignored: Exception) {}
        audioProcessingThread = null

        stopRecording()
        stopBeat()
    }

    private fun runAudioProcessingLoop(audioRecord: AudioRecord, audioTrack: AudioTrack) {
        var uiThrottleCounter = 0

        while (isAudioRunning.get()) {
            val samplesRead = audioRecord.read(inputShorts, 0, CHUNK_SIZE)
            if (samplesRead <= 0) continue

            // 1. Convert Short PCM to Normalized Float (-1.0f to 1.0f)
            var sumSquares = 0.0f
            var peak = 0.0f

            for (i in 0 until samplesRead) {
                val s = inputShorts[i] / 32768.0f
                floatBuffer[i] = s
                val absS = kotlin.math.abs(s)
                if (absS > peak) peak = absS
                sumSquares += s * s
            }

            val rms = sqrt(sumSquares / samplesRead)
            val rmsDb = if (rms > 0.0001f) 20.0f * kotlin.math.log10(rms) else -100.0f

            // 2. Save dry input if currently recording and not paused
            if (_recordingState.value == RecordingState.RECORDING && !isRecordingPaused) {
                val dryStream = dryOutputStream
                if (dryStream != null) {
                    val byteBuf = java.nio.ByteBuffer.allocate(samplesRead * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                    for (i in 0 until samplesRead) {
                        byteBuf.putShort(inputShorts[i])
                    }
                    dryStream.write(byteBuf.array())
                }
            }

            // 3. Real-time Pitch Detection & Correction
            var detectedHz = 0.0
            var targetHz = 0.0
            var noteName = "--"
            var centsDiff = 0.0
            var confidence = 0.0f

            if (isAutoTuneEnabled) {
                detectedHz = pitchDetector.detectPitch(floatBuffer, samplesRead)
                confidence = pitchDetector.lastConfidence

                if (detectedHz > 40.0 && confidence > 0.40f) {
                    val snap = MusicalScale.snapPitchToScale(
                        frequency = detectedHz,
                        rootIndex = rootKeyIndex,
                        scaleType = scaleType,
                        correctionStrength = correctionStrength,
                        isHardAutoTune = isHardAutoTune
                    )
                    targetHz = snap.targetFreq
                    noteName = snap.noteName
                    centsDiff = snap.centsDiff

                    var ratio = snap.pitchRatio
                    // Optional manual pitch shift (semitones)
                    if (manualPitchShiftSemitones != 0) {
                        ratio *= 2.0.pow(manualPitchShiftSemitones / 12.0)
                    }

                    pitchCorrector.setTargetRatio(ratio, pitchSpeed)
                } else {
                    // Unvoiced / silence: unity ratio to preserve crisp speech consonants
                    var ratio = 1.0
                    if (manualPitchShiftSemitones != 0) {
                        ratio = 2.0.pow(manualPitchShiftSemitones / 12.0)
                    }
                    pitchCorrector.setTargetRatio(ratio, 1.0f)
                }

                // Apply pitch correction DSP
                pitchCorrector.process(floatBuffer, samplesRead)
            }

            // 4. Studio Vocal Effects DSP
            compressor.process(floatBuffer, samplesRead)
            equalizer.process(floatBuffer, samplesRead)
            doubler.process(floatBuffer, samplesRead)
            delay.process(floatBuffer, samplesRead)
            reverb.process(floatBuffer, samplesRead)
            SoftLimiter.process(floatBuffer, samplesRead)

            // 5. Convert processed Float back to Short PCM
            for (i in 0 until samplesRead) {
                val sample = floatBuffer[i]
                outputShorts[i] = (sample.coerceIn(-1.0f, 1.0f) * 32767.0f).toInt().toShort()
            }

            // 6. Save wet processed output if recording
            if (_recordingState.value == RecordingState.RECORDING && !isRecordingPaused) {
                val wetStream = wetOutputStream
                if (wetStream != null) {
                    val byteBuf = java.nio.ByteBuffer.allocate(samplesRead * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                    for (i in 0 until samplesRead) {
                        byteBuf.putShort(outputShorts[i])
                    }
                    val bytes = byteBuf.array()
                    wetStream.write(bytes)
                    recordedPcmBytes += bytes.size
                }
            }

            // 7. Live Monitor playback (headphones/speakers)
            if (isLiveMonitorEnabled) {
                // Apply vocal volume fader
                if (kotlin.math.abs(vocalVolume - 1.0f) > 0.01f) {
                    for (i in 0 until samplesRead) {
                        outputShorts[i] = (outputShorts[i] * vocalVolume).toInt().coerceIn(-32768, 32767).toShort()
                    }
                }
                audioTrack.write(outputShorts, 0, samplesRead)
            }

            // 8. Update UI feedback at ~20-30 FPS (every 2nd loop)
            if (++uiThrottleCounter % 2 == 0) {
                waveformBuffer[waveformCounter % waveformBuffer.size] = peak
                waveformCounter++

                val currentDuration = if (_recordingState.value == RecordingState.RECORDING && !isRecordingPaused) {
                    recordingElapsedTime + (System.currentTimeMillis() - recordingStartTime)
                } else {
                    recordingElapsedTime
                }

                _audioMetrics.value = LiveAudioMetrics(
                    inputRmsDb = rmsDb,
                    peakAmplitude = peak,
                    waveformPoints = waveformBuffer.toList(),
                    recordingDurationMs = currentDuration
                )

                _pitchFeedback.value = PitchFeedback(
                    detectedPitchHz = detectedHz,
                    targetPitchHz = targetHz,
                    noteName = noteName,
                    centsDiff = centsDiff,
                    confidence = confidence
                )
            }
        }

        // Cleanup audio devices
        try {
            audioRecord.stop()
            audioRecord.release()
        } catch (ignored: Exception) {}

        try {
            audioTrack.stop()
            audioTrack.release()
        } catch (ignored: Exception) {}
    }

    // ==========================================
    // RECORDING CONTROL
    // ==========================================

    fun startRecording(beatFile: File? = null): Boolean {
        if (!isAudioRunning.get()) {
            if (!startEngine()) return false
        }

        val recDir = File(context.filesDir, "recordings").apply { mkdirs() }
        val timestamp = System.currentTimeMillis()
        wetRecordingFile = File(recDir, "vocal_wet_$timestamp.wav")
        dryRecordingFile = File(recDir, "vocal_dry_$timestamp.wav")

        try {
            val wetFos = FileOutputStream(wetRecordingFile!!)
            val dryFos = FileOutputStream(dryRecordingFile!!)

            // Write initial placeholder 44-byte WAV headers
            WavAudioUtil.writeWavHeader(wetFos, SAMPLE_RATE, 1, 0)
            WavAudioUtil.writeWavHeader(dryFos, SAMPLE_RATE, 1, 0)

            wetOutputStream = wetFos
            dryOutputStream = dryFos
            recordedPcmBytes = 0L

            recordingStartTime = System.currentTimeMillis()
            recordingElapsedTime = 0L
            isRecordingPaused = false
            _recordingState.value = RecordingState.RECORDING

            // If a background beat is selected, start it synchronously
            if (beatFile != null && beatFile.exists()) {
                playBeat(beatFile, loop = true)
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording", e)
            return false
        }
    }

    fun pauseRecording() {
        if (_recordingState.value == RecordingState.RECORDING && !isRecordingPaused) {
            recordingElapsedTime += System.currentTimeMillis() - recordingStartTime
            isRecordingPaused = true
            _recordingState.value = RecordingState.PAUSED
            beatMediaPlayer?.pause()
        }
    }

    fun resumeRecording() {
        if (_recordingState.value == RecordingState.PAUSED) {
            recordingStartTime = System.currentTimeMillis()
            isRecordingPaused = false
            _recordingState.value = RecordingState.RECORDING
            beatMediaPlayer?.start()
        }
    }

    fun stopRecording(): Pair<File?, File?> {
        if (_recordingState.value == RecordingState.IDLE) return Pair(null, null)

        _recordingState.value = RecordingState.STOPPED
        isRecordingPaused = false

        try {
            wetOutputStream?.flush()
            wetOutputStream?.close()
            wetOutputStream = null

            dryOutputStream?.flush()
            dryOutputStream?.close()
            dryOutputStream = null

            // Update WAV headers with actual sizes
            wetRecordingFile?.let { file ->
                if (file.exists()) {
                    val pcmBytes = file.length() - 44
                    WavAudioUtil.updateWavHeaderSizes(file, pcmBytes)
                }
            }

            dryRecordingFile?.let { file ->
                if (file.exists()) {
                    val pcmBytes = file.length() - 44
                    WavAudioUtil.updateWavHeaderSizes(file, pcmBytes)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error closing recording files", e)
        }

        stopBeat()
        val result = Pair(wetRecordingFile, dryRecordingFile)
        _recordingState.value = RecordingState.IDLE
        return result
    }

    // ==========================================
    // BEAT AUDIO PLAYER
    // ==========================================

    fun playBeat(beatFile: File, loop: Boolean = true) {
        stopBeat()
        currentBeatFile = beatFile
        try {
            beatMediaPlayer = MediaPlayer().apply {
                setDataSource(beatFile.absolutePath)
                isLooping = loop
                setVolume(beatVolume, beatVolume)
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing beat", e)
        }
    }

    fun pauseBeat() {
        beatMediaPlayer?.pause()
    }

    fun resumeBeat() {
        beatMediaPlayer?.start()
    }

    fun stopBeat() {
        try {
            beatMediaPlayer?.stop()
            beatMediaPlayer?.release()
        } catch (ignored: Exception) {}
        beatMediaPlayer = null
    }

    fun isBeatPlaying(): Boolean {
        return try {
            beatMediaPlayer?.isPlaying == true
        } catch (e: Exception) {
            false
        }
    }

    fun setBeatPlayerVolume(volume: Float) {
        beatVolume = volume.coerceIn(0.0f, 1.0f)
        try {
            beatMediaPlayer?.setVolume(beatVolume, beatVolume)
        } catch (ignored: Exception) {}
    }
}
