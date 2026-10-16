package com.example.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AudioDecoder {

    /**
     * Decodes an audio file (MP3, M4A, WAV, etc.) from Uri into a standard 16-bit 44.1kHz mono/stereo WAV in app cache.
     */
    fun decodeToWav(context: Context, audioUri: Uri, outputWavFile: File): Boolean {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null

        try {
            extractor.setDataSource(context, audioUri, null)

            var audioTrackIndex = -1
            var format: MediaFormat? = null
            var mime: String? = null

            for (i in 0 until extractor.trackCount) {
                val trackFormat = extractor.getTrackFormat(i)
                val trackMime = trackFormat.getString(MediaFormat.KEY_MIME) ?: ""
                if (trackMime.startsWith("audio/")) {
                    audioTrackIndex = i
                    format = trackFormat
                    mime = trackMime
                    break
                }
            }

            if (audioTrackIndex < 0 || format == null || mime == null) {
                return false
            }

            extractor.selectTrack(audioTrackIndex)
            val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
            val channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 1

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val tempPcmFile = File(outputWavFile.parentFile, "${outputWavFile.nameWithoutExtension}.raw")
            var totalPcmBytes = 0L

            FileOutputStream(tempPcmFile).use { rawOut ->
                val bufferInfo = MediaCodec.BufferInfo()
                var isEOS = false

                while (!isEOS) {
                    val inIndex = codec.dequeueInputBuffer(10000L)
                    if (inIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inIndex)
                        if (inputBuffer != null) {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                isEOS = true
                            } else {
                                codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }

                    var outIndex = codec.dequeueOutputBuffer(bufferInfo, 10000L)
                    while (outIndex >= 0) {
                        val outBuffer = codec.getOutputBuffer(outIndex)
                        if (outBuffer != null && bufferInfo.size > 0) {
                            val chunk = ByteArray(bufferInfo.size)
                            outBuffer.position(bufferInfo.offset)
                            outBuffer.get(chunk, 0, bufferInfo.size)
                            rawOut.write(chunk)
                            totalPcmBytes += bufferInfo.size
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            break
                        }
                        outIndex = codec.dequeueOutputBuffer(bufferInfo, 0L)
                    }
                }
            }

            // Write final WAV file with header
            val rawBytes = tempPcmFile.readBytes()
            tempPcmFile.delete()

            FileOutputStream(outputWavFile).use { wavOut ->
                WavAudioUtil.writeWavHeader(wavOut, sampleRate, channelCount, rawBytes.size.toLong())
                wavOut.write(rawBytes)
            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        } finally {
            try {
                codec?.stop()
                codec?.release()
                extractor.release()
            } catch (ignored: Exception) {}
        }
    }
}
