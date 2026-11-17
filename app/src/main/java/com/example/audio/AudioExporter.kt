package com.example.audio

import android.content.Context
import android.content.Intent
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AudioExporter {

    fun isMp3EncoderSupported(): Boolean {
        val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        return list.codecInfos.any { info ->
            info.isEncoder && info.supportedTypes.any { it.equals("audio/mpeg", ignoreCase = true) }
        }
    }

    fun exportWavToUri(context: Context, sourceWav: File, targetUri: Uri): Boolean {
        return try {
            context.contentResolver.openOutputStream(targetUri)?.use { out ->
                FileInputStream(sourceWav).use { input ->
                    input.copyTo(out)
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Transcodes a 48kHz 16-bit stereo WAV recording to compressed M4A (AAC-LC 256kbps)
     * using hardware/platform MediaCodec + MediaMuxer.
     */
    fun transcodeWavToM4a(sourceWav: File, destM4a: File): Boolean {
        return try {
            val wavBytes = sourceWav.readBytes()
            if (wavBytes.size <= 44) return false

            val bb = ByteBuffer.wrap(wavBytes).order(ByteOrder.LITTLE_ENDIAN)
            val sampleRate = bb.getInt(24).coerceAtLeast(44100)
            val channels = bb.getShort(22).toInt().coerceIn(1, 2)
            val pcmStart = 44
            val pcmLength = wavBytes.size - pcmStart

            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 256_000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }

            val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            val muxer = MediaMuxer(destM4a.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var trackIndex = -1
            var muxerStarted = false

            val info = MediaCodec.BufferInfo()
            var offset = 0
            var inputDone = false
            var outputDone = false
            val bytesPerFrame = channels * 2
            var totalFramesSent = 0L

            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)!!
                        inBuf.clear()
                        val remaining = pcmLength - offset
                        if (remaining <= 0) {
                            codec.queueInputBuffer(
                                inIdx, 0, 0,
                                (totalFramesSent * 1_000_000L) / sampleRate,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            val toCopy = minOf(remaining, inBuf.capacity())
                            inBuf.put(wavBytes, pcmStart + offset, toCopy)
                            val ptsUs = (totalFramesSent * 1_000_000L) / sampleRate
                            codec.queueInputBuffer(inIdx, 0, toCopy, ptsUs, 0)
                            offset += toCopy
                            totalFramesSent += toCopy / bytesPerFrame
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (!muxerStarted) {
                            trackIndex = muxer.addTrack(codec.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                    }
                    outIdx >= 0 -> {
                        val outBuf = codec.getOutputBuffer(outIdx)
                        if (outBuf != null && (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && info.size > 0 && muxerStarted) {
                            outBuf.position(info.offset)
                            outBuf.limit(info.offset + info.size)
                            muxer.writeSampleData(trackIndex, outBuf, info)
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                }
            }

            codec.stop()
            codec.release()
            if (muxerStarted) muxer.stop()
            muxer.release()
            destM4a.exists() && destM4a.length() > 0L
        } catch (_: Exception) {
            false
        }
    }

    fun shareRecording(context: Context, file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Share DJ Mix").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
