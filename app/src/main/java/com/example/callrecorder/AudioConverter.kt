package com.example.callrecorder

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.io.FileInputStream

object AudioConverter {

    fun convertPcmToM4a(
        pcmFile: File,
        m4aFile: File,
        sampleRate: Int = 44100,
        channels: Int = 1,
        bitRate: Int = 64000
    ) {
        val mimeType = "audio/mp4a-latm"
        val format = MediaFormat.createAudioFormat(mimeType, sampleRate, channels).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }

        val codec = MediaCodec.createEncoderByType(mimeType)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        val muxer = MediaMuxer(m4aFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var audioTrackIndex = -1
        var muxerStarted = false

        val fis = FileInputStream(pcmFile)
        val bufferInfo = MediaCodec.BufferInfo()
        val inputBuffer = ByteArray(2048)
        var isEos = false

        try {
            while (!isEos || bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM == 0) {
                if (!isEos) {
                    val inputBufIndex = codec.dequeueInputBuffer(10000)
                    if (inputBufIndex >= 0) {
                        val byteBuffer = codec.getInputBuffer(inputBufIndex) ?: continue
                        byteBuffer.clear()
                        val bytesRead = fis.read(inputBuffer)
                        if (bytesRead <= 0) {
                            codec.queueInputBuffer(inputBufIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isEos = true
                        } else {
                            byteBuffer.put(inputBuffer, 0, bytesRead)
                            codec.queueInputBuffer(inputBufIndex, 0, bytesRead, 0, 0)
                        }
                    }
                }

                val outputBufIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
                if (outputBufIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    audioTrackIndex = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    muxerStarted = true
                } else if (outputBufIndex >= 0) {
                    val encodedData = codec.getOutputBuffer(outputBufIndex) ?: continue
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        bufferInfo.size = 0
                    }
                    if (bufferInfo.size != 0 && muxerStarted) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(audioTrackIndex, encodedData, bufferInfo)
                    }
                    codec.releaseOutputBuffer(outputBufIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        } finally {
            fis.close()
            codec.stop()
            codec.release()
            if (muxerStarted) {
                muxer.stop()
                muxer.release()
            }
            pcmFile.delete()
        }
    }
}
