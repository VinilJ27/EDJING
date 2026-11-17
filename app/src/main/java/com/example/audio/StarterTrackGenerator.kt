package com.example.audio

import android.content.Context
import android.net.Uri
import com.example.data.TrackEntity
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Generates real 48kHz stereo PCM WAV starter DJ tracks on first launch
 * so both decks have authentic beat-gridded audio ready to mix, scratch, EQ, and sync immediately.
 */
object StarterTrackGenerator {

    private const val SR = 48000

    data class TrackSpec(
        val fileName: String,
        val title: String,
        val artist: String,
        val album: String,
        val bpm: Float,
        val musicalKey: String,
        val camelotKey: String,
        val bars: Int,
        val rootFreqHz: Float,
        val patternStyle: Int
    )

    private val STARTER_SPECS = listOf(
        TrackSpec(
            fileName = "neon_horizon_128.wav",
            title = "Neon Horizon",
            artist = "PulseMix Collective",
            album = "Club Session Vol. 1",
            bpm = 128.0f,
            musicalKey = "Am",
            camelotKey = "8A",
            bars = 16,
            rootFreqHz = 55.0f, // A1
            patternStyle = 0
        ),
        TrackSpec(
            fileName = "midnight_circuit_124.wav",
            title = "Midnight Circuit",
            artist = "Kairo & Voss",
            album = "Deep Afterhours",
            bpm = 124.0f,
            musicalKey = "Cmaj",
            camelotKey = "8B",
            bars = 16,
            rootFreqHz = 65.41f, // C2
            patternStyle = 1
        ),
        TrackSpec(
            fileName = "sub_pressure_140.wav",
            title = "Sub Pressure",
            artist = "Apex Modular",
            album = "Warehouse 04",
            bpm = 140.0f,
            musicalKey = "Dm",
            camelotKey = "7A",
            bars = 16,
            rootFreqHz = 73.42f, // D2
            patternStyle = 2
        ),
        TrackSpec(
            fileName = "velvet_break_102.wav",
            title = "Velvet Break",
            artist = "Solstice Beats",
            album = "Crate Classics",
            bpm = 102.0f,
            musicalKey = "Em",
            camelotKey = "9A",
            bars = 16,
            rootFreqHz = 82.41f, // E2
            patternStyle = 3
        )
    )

    fun generateStarterTracks(context: Context): List<TrackEntity> {
        val dir = File(context.filesDir, "starter_tracks").apply { mkdirs() }
        val entities = ArrayList<TrackEntity>(STARTER_SPECS.size)

        for (spec in STARTER_SPECS) {
            val outFile = File(dir, spec.fileName)
            val pcm = synthesizeGroove(spec)
            if (!outFile.exists() || outFile.length() < 1024L) {
                writeWavFile(outFile, pcm, SR)
            }
            val waveform = AudioAnalyzer.generateWaveform(pcm, 420)
            val frames = pcm.size / 2
            val durationMs = (frames * 1000L) / SR

            entities.add(
                TrackEntity(
                    uriString = Uri.fromFile(outFile).toString(),
                    title = spec.title,
                    artist = spec.artist,
                    album = spec.album,
                    durationMs = durationMs,
                    bpm = spec.bpm,
                    firstBeatMs = 0L,
                    musicalKey = spec.musicalKey,
                    camelotKey = spec.camelotKey,
                    waveformSerialized = AudioAnalyzer.serializeWaveform(waveform),
                    albumArtPath = null,
                    isBuiltIn = true
                )
            )
        }
        return entities
    }

    private fun synthesizeGroove(spec: TrackSpec): FloatArray {
        val beatsPerBar = 4
        val totalBeats = spec.bars * beatsPerBar
        val beatSec = 60.0 / spec.bpm
        val totalFrames = (totalBeats * beatSec * SR).toInt()
        val pcm = FloatArray(totalFrames * 2)

        val stepsPerBeat = 4 // 16th-note grid
        val stepSec = beatSec / stepsPerBeat
        val stepFrames = (stepSec * SR).toInt()
        var rng = 0x9E3779B9u.toInt()

        val minorScale = floatArrayOf(1f, 1.1892f, 1.3348f, 1.4983f, 1.7818f, 2f)
        val totalSteps = totalBeats * stepsPerBeat

        for (step in 0 until totalSteps) {
            val stepStart = step * stepFrames
            val stepInBar = step % 16
            val barIdx = step / 16

            val hasKick = when (spec.patternStyle) {
                3 -> stepInBar == 0 || stepInBar == 6 || stepInBar == 8 || stepInBar == 14
                else -> stepInBar % 4 == 0
            }
            val hasClap = stepInBar == 4 || stepInBar == 12
            val hasHat = when (spec.patternStyle) {
                2 -> stepInBar % 2 == 1 || stepInBar % 4 == 2
                else -> stepInBar % 4 == 2
            }
            val hasBass = when (spec.patternStyle) {
                0 -> stepInBar % 2 == 1 || stepInBar == 14
                1 -> stepInBar == 0 || stepInBar == 3 || stepInBar == 6 || stepInBar == 10 || stepInBar == 12
                2 -> stepInBar % 2 == 0
                else -> stepInBar == 0 || stepInBar == 6 || stepInBar == 10
            }
            val hasArp = (barIdx >= 2) && (stepInBar % 2 == 0)

            // Mix Kick
            if (hasKick) {
                val len = (0.28 * SR).toInt()
                var phase = 0.0
                for (i in 0 until len) {
                    val idx = stepStart + i
                    if (idx >= totalFrames) break
                    val t = i.toFloat() / SR
                    val f = 45f + 130f * exp(-t * 32f)
                    phase += 2.0 * PI * f / SR
                    val s = tanh(sin(phase).toFloat() * 1.4f) * exp(-t * 9f) * 0.52f
                    pcm[idx * 2] += s
                    pcm[idx * 2 + 1] += s
                }
            }

            // Mix Snare/Clap
            if (hasClap) {
                val len = (0.18 * SR).toInt()
                for (i in 0 until len) {
                    val idx = stepStart + i
                    if (idx >= totalFrames) break
                    val t = i.toFloat() / SR
                    rng = rng * 1664525 + 1013904223
                    val noise = ((rng ushr 8) and 0xFFFF) / 32768f - 1f
                    val env = exp(-t * 20f)
                    val tone = sin(2.0 * PI * 190.0 * t).toFloat() * exp(-t * 28f) * 0.3f
                    val s = (noise * 0.35f * env + tone) * 0.85f
                    pcm[idx * 2] += s * 0.95f
                    pcm[idx * 2 + 1] += s * 1.05f
                }
            }

            // Mix Hi-hat
            if (hasHat) {
                val len = (0.09 * SR).toInt()
                var prevN = 0f
                for (i in 0 until len) {
                    val idx = stepStart + i
                    if (idx >= totalFrames) break
                    val t = i.toFloat() / SR
                    rng = rng * 1664525 + 1013904223
                    val n = ((rng ushr 8) and 0xFFFF) / 32768f - 1f
                    val hp = n - prevN
                    prevN = n * 0.9f
                    val s = hp * exp(-t * 38f) * 0.16f
                    pcm[idx * 2] += s * 0.85f
                    pcm[idx * 2 + 1] += s * 1.15f
                }
            }

            // Mix Bassline
            if (hasBass) {
                val noteMult = minorScale[(barIdx + stepInBar / 4) % 4]
                val bassFreq = spec.rootFreqHz * noteMult
                val len = (stepFrames * 0.92).toInt()
                for (i in 0 until len) {
                    val idx = stepStart + i
                    if (idx >= totalFrames) break
                    val t = i.toFloat() / SR
                    val env = minOf(1f, t * 90f) * exp(-t * 5.5f)
                    val sub = sin(2.0 * PI * bassFreq * t).toFloat()
                    val harm = sin(2.0 * PI * bassFreq * 2.0 * t).toFloat() * 0.35f
                    val s = tanh((sub + harm) * 1.3f) * env * 0.28f
                    pcm[idx * 2] += s
                    pcm[idx * 2 + 1] += s
                }
            }

            // Mix Stereo Synth Pad / Pluck Arp
            if (hasArp) {
                val arpMult = minorScale[(step / 2) % minorScale.size] * 4f
                val freq = spec.rootFreqHz * arpMult
                val len = (stepFrames * 1.3).toInt()
                for (i in 0 until len) {
                    val idx = stepStart + i
                    if (idx >= totalFrames) break
                    val t = i.toFloat() / SR
                    val env = minOf(1f, t * 120f) * exp(-t * 6.8f)
                    val sL = sin(2.0 * PI * freq * t).toFloat() * env * 0.12f
                    val sR = sin(2.0 * PI * (freq * 1.004f) * t).toFloat() * env * 0.12f
                    pcm[idx * 2] += sL
                    pcm[idx * 2 + 1] += sR
                }
            }
        }

        // Gentle mastering normalization
        for (i in pcm.indices) {
            pcm[i] = tanh(pcm[i] * 0.9f)
        }
        return pcm
    }

    private fun writeWavFile(file: File, stereoPcm: FloatArray, sampleRate: Int) {
        val dataBytes = stereoPcm.size * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt(36 + dataBytes)
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16)
            putShort(1)
            putShort(2)
            putInt(sampleRate)
            putInt(sampleRate * 4)
            putShort(4)
            putShort(16)
            put("data".toByteArray())
            putInt(dataBytes)
        }.array()

        BufferedOutputStream(FileOutputStream(file), 65536).use { out ->
            out.write(header)
            val chunk = ByteArray(8192)
            val bb = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN)
            var idx = 0
            while (idx < stereoPcm.size) {
                bb.clear()
                var count = 0
                while (idx < stereoPcm.size && count < chunk.size / 2) {
                    val v = (stereoPcm[idx++].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                    bb.putShort(v)
                    count++
                }
                out.write(chunk, 0, count * 2)
            }
        }
    }
}
