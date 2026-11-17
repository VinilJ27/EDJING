package com.example.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

data class DecodedAudio(
    val pcm48kStereo: FloatArray,
    val totalFrames: Int,
    val durationMs: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtPath: String?
)

object AudioDecoder {

    const val TARGET_SAMPLE_RATE = 48000

    fun decodeUri(context: Context, uri: Uri): DecodedAudio {
        val fileName = resolveDisplayName(context, uri) ?: "Imported Track"
        val retriever = MediaMetadataRetriever()
        var title = fileName.substringBeforeLast('.')
        var artist = "Unknown Artist"
        var album = "Local Library"
        var durationMs = 0L
        var artPath: String? = null

        try {
            if (uri.scheme == "file" && uri.path != null) {
                retriever.setDataSource(uri.path)
            } else {
                retriever.setDataSource(context, uri)
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }?.let { title = it }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }?.let { artist = it }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.takeIf { it.isNotBlank() }?.let { album = it }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let { durationMs = it }
            retriever.embeddedPicture?.let { picBytes ->
                val artDir = File(context.filesDir, "album_art").apply { mkdirs() }
                val artFile = File(artDir, "${uri.toString().hashCode().toUInt()}.jpg")
                FileOutputStream(artFile).use { it.write(picBytes) }
                artPath = artFile.absolutePath
            }
        } catch (_: Exception) {
            // Fallback to filename metadata
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }

        // Fast-path direct WAV decoder if it's a PCM WAV file
        if (fileName.endsWith(".wav", ignoreCase = true) || uri.toString().endsWith(".wav", ignoreCase = true)) {
            val wavDecoded = tryDecodeWav(context, uri)
            if (wavDecoded != null) {
                val (pcm, sr, ch) = wavDecoded
                val resampled = convertTo48kStereo(pcm, sr, ch)
                val frames = resampled.size / 2
                val computedMs = (frames * 1000L) / TARGET_SAMPLE_RATE
                return DecodedAudio(
                    pcm48kStereo = resampled,
                    totalFrames = frames,
                    durationMs = if (durationMs > 0) durationMs else computedMs,
                    title = title,
                    artist = artist,
                    album = album,
                    albumArtPath = artPath
                )
            }
        }

        // General MediaExtractor + MediaCodec decoder (MP3, FLAC, AAC, M4A, OGG, WAV)
        val extractor = MediaExtractor()
        try {
            if (uri.scheme == "file" && uri.path != null) {
                extractor.setDataSource(uri.path!!)
            } else {
                extractor.setDataSource(context, uri, null)
            }

            var audioTrackIdx = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIdx = i
                    format = f
                    break
                }
            }
            require(audioTrackIdx >= 0 && format != null) { "No audio track found in $uri" }
            extractor.selectTrack(audioTrackIdx)

            val mime = format.getString(MediaFormat.KEY_MIME)!!
            var sourceSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var sourceChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            val decodedChunks = ArrayList<ShortArray>()
            var totalShorts = 0
            var inputDone = false
            var outputDone = false
            // Cap max decode length to 12 minutes at 48k stereo (~69M floats max) to guard OOM
            val maxSamplesPerChannel = TARGET_SAMPLE_RATE * 60 * 12

            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)!!
                        val sampleSize = extractor.readSampleData(inBuf, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val outFmt = codec.outputFormat
                    if (outFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        sourceSampleRate = outFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                    if (outFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        sourceChannels = outFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                } else if (outIdx >= 0) {
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true
                    }
                    val outBuf = codec.getOutputBuffer(outIdx)
                    if (outBuf != null && bufferInfo.size > 0) {
                        outBuf.position(bufferInfo.offset)
                        outBuf.limit(bufferInfo.offset + bufferInfo.size)
                        val shortCount = bufferInfo.size / 2
                        val shorts = ShortArray(shortCount)
                        outBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
                        decodedChunks.add(shorts)
                        totalShorts += shortCount
                        if (totalShorts / sourceChannels >= maxSamplesPerChannel) {
                            outputDone = true
                        }
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                }
            }

            codec.stop()
            codec.release()

            val floatRaw = FloatArray(totalShorts)
            var pos = 0
            for (chunk in decodedChunks) {
                for (s in chunk) {
                    floatRaw[pos++] = s / 32768f
                }
            }

            val stereo48k = convertTo48kStereo(floatRaw, sourceSampleRate, sourceChannels)
            val frames = stereo48k.size / 2
            val computedMs = (frames * 1000L) / TARGET_SAMPLE_RATE

            return DecodedAudio(
                pcm48kStereo = stereo48k,
                totalFrames = frames,
                durationMs = if (durationMs > 0) durationMs else computedMs,
                title = title,
                artist = artist,
                album = album,
                albumArtPath = artPath
            )
        } finally {
            try { extractor.release() } catch (_: Exception) {}
        }
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String? {
        if (uri.scheme == "file") return File(uri.path ?: "").name
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (_: Exception) {
            uri.lastPathSegment
        }
    }

    private fun tryDecodeWav(context: Context, uri: Uri): Triple<FloatArray, Int, Int>? {
        val stream: InputStream = if (uri.scheme == "file" && uri.path != null) {
            File(uri.path!!).inputStream()
        } else {
            context.contentResolver.openInputStream(uri) ?: return null
        }

        return stream.use { input ->
            val bytes = readAllBytes(input)
            if (bytes.size < 44) return null
            val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            if (bytes[0] != 'R'.code.toByte() || bytes[1] != 'I'.code.toByte() ||
                bytes[2] != 'F'.code.toByte() || bytes[3] != 'F'.code.toByte()
            ) return null

            var offset = 12
            var audioFormat = 1
            var channels = 2
            var sampleRate = 48000
            var bitsPerSample = 16
            var dataOffset = -1
            var dataSize = 0

            while (offset + 8 <= bytes.size) {
                val id = String(bytes, offset, 4, Charsets.US_ASCII)
                val chunkSize = bb.getInt(offset + 4)
                val payloadStart = offset + 8
                if (id == "fmt " && payloadStart + 16 <= bytes.size) {
                    audioFormat = bb.getShort(payloadStart).toInt() and 0xFFFF
                    channels = bb.getShort(payloadStart + 2).toInt() and 0xFFFF
                    sampleRate = bb.getInt(payloadStart + 4)
                    bitsPerSample = bb.getShort(payloadStart + 14).toInt() and 0xFFFF
                } else if (id == "data") {
                    dataOffset = payloadStart
                    dataSize = min(chunkSize, bytes.size - payloadStart)
                    break
                }
                offset += 8 + chunkSize + (chunkSize and 1)
            }

            if (dataOffset < 0 || dataSize <= 0 || audioFormat != 1 || bitsPerSample != 16) {
                return null
            }
            val sampleCount = dataSize / 2
            val out = FloatArray(sampleCount)
            var idx = dataOffset
            for (i in 0 until sampleCount) {
                out[i] = bb.getShort(idx) / 32768f
                idx += 2
            }
            Triple(out, sampleRate, channels)
        }
    }

    private fun readAllBytes(input: InputStream): ByteArray {
        val baos = ByteArrayOutputStream()
        val buf = ByteArray(16384)
        while (true) {
            val n = input.read(buf)
            if (n <= 0) break
            baos.write(buf, 0, n)
        }
        return baos.toByteArray()
    }

    fun convertTo48kStereo(input: FloatArray, srcRate: Int, srcChannels: Int): FloatArray {
        if (srcRate <= 0 || srcChannels <= 0 || input.isEmpty()) return FloatArray(0)
        val srcFrames = input.size / srcChannels
        if (srcFrames <= 1) return FloatArray(0)

        if (srcRate == TARGET_SAMPLE_RATE && srcChannels == 2) {
            return input
        }

        val dstFrames = ((srcFrames.toDouble() * TARGET_SAMPLE_RATE) / srcRate.toDouble()).toInt().coerceAtLeast(1)
        val out = FloatArray(dstFrames * 2)
        val ratio = srcRate.toDouble() / TARGET_SAMPLE_RATE.toDouble()

        for (i in 0 until dstFrames) {
            val srcPos = i * ratio
            val f0 = srcPos.toInt().coerceIn(0, srcFrames - 1)
            val f1 = min(srcFrames - 1, f0 + 1)
            val frac = (srcPos - f0).toFloat()

            val l0: Float
            val r0: Float
            val l1: Float
            val r1: Float

            if (srcChannels == 1) {
                l0 = input[f0]
                r0 = l0
                l1 = input[f1]
                r1 = l1
            } else {
                l0 = input[f0 * srcChannels]
                r0 = input[f0 * srcChannels + 1]
                l1 = input[f1 * srcChannels]
                r1 = input[f1 * srcChannels + 1]
            }

            out[i * 2] = l0 + (l1 - l0) * frac
            out[i * 2 + 1] = r0 + (r1 - r0) * frac
        }
        return out
    }
}
