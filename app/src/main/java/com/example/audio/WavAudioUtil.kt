package com.example.audio

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.tanh

object WavAudioUtil {

    fun writeWavHeader(
        out: FileOutputStream,
        sampleRate: Int,
        channels: Int,
        pcmDataLength: Long
    ) {
        val totalDataLen = pcmDataLength + 36
        val byteRate = sampleRate * channels * 2

        val header = ByteArray(44)
        val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF header
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        buf.putInt(4, totalDataLen.toInt())
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()

        // fmt subchunk
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        buf.putInt(16, 16) // SubChunk1Size = 16 for PCM
        buf.putShort(20, 1.toShort()) // AudioFormat = 1 (PCM)
        buf.putShort(22, channels.toShort())
        buf.putInt(24, sampleRate)
        buf.putInt(28, byteRate)
        buf.putShort(32, (channels * 2).toShort()) // BlockAlign
        buf.putShort(34, 16.toShort()) // BitsPerSample = 16

        // data subchunk
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        buf.putInt(40, pcmDataLength.toInt())

        out.write(header)
    }

    fun updateWavHeaderSizes(file: File, pcmDataLength: Long) {
        RandomAccessFile(file, "rw").use { raf ->
            val totalDataLen = pcmDataLength + 36
            raf.seek(4)
            raf.write(intToLittleEndian(totalDataLen.toInt()))
            raf.seek(40)
            raf.write(intToLittleEndian(pcmDataLength.toInt()))
        }
    }

    private fun intToLittleEndian(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte()
        )
    }

    fun saveShortsToWav(
        shorts: ShortArray,
        outputFile: File,
        sampleRate: Int = 44100,
        channels: Int = 1
    ) {
        val byteBuffer = ByteBuffer.allocate(shorts.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in shorts) {
            byteBuffer.putShort(s)
        }
        val pcmBytes = byteBuffer.array()

        FileOutputStream(outputFile).use { fos ->
            writeWavHeader(fos, sampleRate, channels, pcmBytes.size.toLong())
            fos.write(pcmBytes)
        }
    }

    fun readWavFileToShorts(wavFile: File): ShortArray {
        if (!wavFile.exists() || wavFile.length() < 44) return ShortArray(0)
        FileInputStream(wavFile).use { fis ->
            val header = ByteArray(44)
            fis.read(header)
            val pcmBytes = fis.readBytes()
            val shortBuf = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            val shorts = ShortArray(shortBuf.remaining())
            shortBuf.get(shorts)
            return shorts
        }
    }

    /**
     * Mixes vocal track (mono or stereo) with instrumental beat (mono or stereo)
     * applying independent volume faders, master volume, and master soft-knee saturation
     * to prevent digital clipping.
     * Returns 16-bit stereo PCM Shorts (interleaved L, R, L, R).
     */
    fun mixVocalAndBeatToStereo(
        vocalPcm: ShortArray,
        vocalChannels: Int,
        vocalVolume: Float,
        beatPcm: ShortArray,
        beatChannels: Int,
        beatVolume: Float,
        masterVolume: Float
    ): ShortArray {
        val vocalSamplesCount = if (vocalChannels == 2) vocalPcm.size / 2 else vocalPcm.size
        val beatSamplesCount = if (beatChannels == 2) beatPcm.size / 2 else beatPcm.size
        val maxFrames = maxOf(vocalSamplesCount, beatSamplesCount)

        if (maxFrames == 0) return ShortArray(0)

        val outputStereo = ShortArray(maxFrames * 2)

        for (frame in 0 until maxFrames) {
            // Vocal sample (Float -1.0 to 1.0)
            val vocalL: Float
            val vocalR: Float
            if (frame < vocalSamplesCount) {
                if (vocalChannels == 2) {
                    vocalL = vocalPcm[frame * 2] / 32768.0f
                    vocalR = vocalPcm[frame * 2 + 1] / 32768.0f
                } else {
                    val s = vocalPcm[frame] / 32768.0f
                    vocalL = s
                    vocalR = s
                }
            } else {
                vocalL = 0.0f
                vocalR = 0.0f
            }

            // Beat sample (Float -1.0 to 1.0)
            val beatL: Float
            val beatR: Float
            if (frame < beatSamplesCount) {
                if (beatChannels == 2) {
                    beatL = beatPcm[frame * 2] / 32768.0f
                    beatR = beatPcm[frame * 2 + 1] / 32768.0f
                } else {
                    val s = beatPcm[frame] / 32768.0f
                    beatL = s
                    beatR = s
                }
            } else {
                beatL = 0.0f
                beatR = 0.0f
            }

            // Summer
            var mixL = (vocalL * vocalVolume + beatL * beatVolume) * masterVolume
            var mixR = (vocalR * vocalVolume + beatR * beatVolume) * masterVolume

            // Studio master soft limiter
            if (kotlin.math.abs(mixL) > 0.85f) {
                mixL = tanh(mixL)
            }
            if (kotlin.math.abs(mixR) > 0.85f) {
                mixR = tanh(mixR)
            }

            val shortL = (mixL.coerceIn(-1.0f, 1.0f) * 32767.0f).toInt().toShort()
            val shortR = (mixR.coerceIn(-1.0f, 1.0f) * 32767.0f).toInt().toShort()

            outputStereo[frame * 2] = shortL
            outputStereo[frame * 2 + 1] = shortR
        }

        return outputStereo
    }
}
