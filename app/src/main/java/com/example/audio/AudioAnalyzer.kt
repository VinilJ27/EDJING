package com.example.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class WaveformBucket(
    val low: Float,
    val mid: Float,
    val high: Float,
    val peak: Float
)

data class TrackAnalysisResult(
    val bpm: Float,
    val firstBeatMs: Long,
    val musicalKey: String,
    val camelotKey: String,
    val waveform: List<WaveformBucket>
)

object AudioAnalyzer {

    private val MAJOR_KEYS = arrayOf("C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")
    private val MINOR_KEYS = arrayOf("Cm", "C#m", "Dm", "Ebm", "Em", "Fm", "F#m", "Gm", "G#m", "Am", "Bbm", "Bm")
    private val MAJOR_CAMELOT = arrayOf("8B", "3B", "10B", "5B", "12B", "7B", "2B", "9B", "4B", "11B", "6B", "1B")
    private val MINOR_CAMELOT = arrayOf("5A", "12A", "7A", "2A", "9A", "4A", "11A", "6A", "1A", "8A", "3A", "10A")

    // Krumhansl-Schmuckler key profiles
    private val MAJOR_PROFILE = floatArrayOf(6.35f, 2.23f, 3.48f, 2.33f, 4.38f, 4.09f, 2.52f, 5.19f, 2.39f, 3.66f, 2.29f, 2.88f)
    private val MINOR_PROFILE = floatArrayOf(6.33f, 2.68f, 3.52f, 5.38f, 2.60f, 3.53f, 2.54f, 4.75f, 3.98f, 2.69f, 3.34f, 3.17f)

    fun analyze(pcm48kStereo: FloatArray, sampleRate: Int = 48000): TrackAnalysisResult {
        val totalFrames = pcm48kStereo.size / 2
        if (totalFrames < sampleRate) {
            return TrackAnalysisResult(
                bpm = 128.0f,
                firstBeatMs = 0L,
                musicalKey = "Am",
                camelotKey = "8A",
                waveform = generateWaveform(pcm48kStereo, 360)
            )
        }

        val (bpm, firstBeatMs) = detectBpmAndBeatGrid(pcm48kStereo, sampleRate)
        val (musicalKey, camelotKey) = detectMusicalKey(pcm48kStereo, sampleRate)
        val waveform = generateWaveform(pcm48kStereo, 420)

        return TrackAnalysisResult(
            bpm = bpm,
            firstBeatMs = firstBeatMs,
            musicalKey = musicalKey,
            camelotKey = camelotKey,
            waveform = waveform
        )
    }

    /**
     * Multi-band onset envelope -> autocorrelation tempo estimation -> comb-filter downbeat offset.
     */
    fun detectBpmAndBeatGrid(pcm48kStereo: FloatArray, sampleRate: Int = 48000): Pair<Float, Long> {
        val totalFrames = pcm48kStereo.size / 2
        val hopFrames = sampleRate / 100 // 10ms hop = 100 Hz onset envelope
        val maxAnalyzeFrames = min(totalFrames, sampleRate * 75) // analyze up to 75 seconds
        val numHops = maxAnalyzeFrames / hopFrames
        if (numHops < 200) return 128.0f to 0L

        val onset = FloatArray(numHops)
        var prevLow = 0f
        var prevMid = 0f
        var lpState = 0f

        for (h in 0 until numHops) {
            val start = h * hopFrames
            var lowEnergy = 0f
            var fullEnergy = 0f
            for (i in 0 until hopFrames) {
                val idx = (start + i) * 2
                val mono = 0.5f * (pcm48kStereo[idx] + pcm48kStereo[idx + 1])
                lpState = lpState * 0.94f + mono * 0.06f
                lowEnergy += lpState * lpState
                fullEnergy += mono * mono
            }
            val dLow = max(0f, lowEnergy - prevLow)
            val dMid = max(0f, fullEnergy - prevMid)
            onset[h] = dLow * 2.2f + dMid
            prevLow = lowEnergy
            prevMid = fullEnergy
        }

        // Autocorrelation across BPM range 68..184 (100 Hz hop -> period = 6000 / bpm)
        var bestBpm = 128f
        var bestScore = -1f
        val bpmCandidates = FloatArray(233) // 68.0 to 184.0 in 0.5 BPM steps
        for (k in bpmCandidates.indices) {
            val bpm = 68.0f + k * 0.5f
            val lag = (6000.0f / bpm).roundToInt()
            if (lag <= 0 || lag >= numHops / 2) continue
            var sum = 0f
            for (i in 0 until numHops - lag) {
                sum += onset[i] * onset[i + lag]
                if (i + lag * 2 < numHops) {
                    sum += 0.5f * onset[i] * onset[i + lag * 2]
                }
            }
            // Mild tempo prior favoring classic DJ range 95..148 BPM to prevent half/double-time errors
            val prior = if (bpm in 96f..148f) 1.18f else 0.92f
            val score = sum * prior
            if (score > bestScore) {
                bestScore = score
                bestBpm = bpm
            }
        }

        // Refine BPM around bestBpm in 0.1 BPM steps
        var refinedBpm = bestBpm
        var refinedScore = -1f
        val startBpm = max(65f, bestBpm - 1.0f)
        val endBpm = min(190f, bestBpm + 1.0f)
        var b = startBpm
        while (b <= endBpm) {
            val lagFloat = 6000.0f / b
            var score = 0f
            var pos = 0f
            var count = 0
            while (pos.roundToInt() < numHops && count < 64) {
                val p = pos.roundToInt()
                score += onset[p]
                pos += lagFloat
                count++
            }
            if (score > refinedScore) {
                refinedScore = score
                refinedBpm = b
            }
            b += 0.1f
        }

        // Find first downbeat offset (in 10ms hops) by sliding a beat-grid comb over the first period
        val beatPeriodHops = 6000.0f / refinedBpm
        val maxPhaseHops = beatPeriodHops.toInt().coerceAtLeast(1)
        var bestPhaseHop = 0
        var bestPhaseScore = -1f
        for (phase in 0 until maxPhaseHops) {
            var combScore = 0f
            var p = phase.toFloat()
            var beatsChecked = 0
            while (p.roundToInt() < numHops && beatsChecked < 48) {
                val idx = p.roundToInt()
                val localMax = max(
                    onset[idx],
                    max(
                        if (idx > 0) onset[idx - 1] * 0.7f else 0f,
                        if (idx + 1 < numHops) onset[idx + 1] * 0.7f else 0f
                    )
                )
                combScore += localMax
                p += beatPeriodHops
                beatsChecked++
            }
            if (combScore > bestPhaseScore) {
                bestPhaseScore = combScore
                bestPhaseHop = phase
            }
        }

        val firstBeatMs = (bestPhaseHop * 10L)
        val roundedBpm = (refinedBpm * 10f).roundToInt() / 10f
        return roundedBpm to firstBeatMs
    }

    /**
     * Radix-2 FFT Chromagram + Krumhansl-Schmuckler key detection with Camelot code.
     */
    fun detectMusicalKey(pcm48kStereo: FloatArray, sampleRate: Int = 48000): Pair<String, String> {
        val fftSize = 4096
        val totalFrames = pcm48kStereo.size / 2
        if (totalFrames < fftSize) return "Am" to "8A"

        val chroma = FloatArray(12)
        val real = FloatArray(fftSize)
        val imag = FloatArray(fftSize)
        val window = FloatArray(fftSize) { i ->
            (0.5 * (1.0 - cos(2.0 * PI * i / fftSize))).toFloat()
        }

        val numSlices = 20
        val step = max(fftSize, (totalFrames - fftSize) / numSlices)
        var frame = 0
        var sliceCount = 0
        while (frame + fftSize <= totalFrames && sliceCount < numSlices) {
            for (i in 0 until fftSize) {
                val idx = (frame + i) * 2
                real[i] = 0.5f * (pcm48kStereo[idx] + pcm48kStereo[idx + 1]) * window[i]
                imag[i] = 0f
            }
            fft(real, imag)

            // Map FFT bins (55 Hz .. 2000 Hz) to 12 pitch classes
            val minBin = (55f * fftSize / sampleRate).toInt().coerceAtLeast(1)
            val maxBin = (2000f * fftSize / sampleRate).toInt().coerceAtMost(fftSize / 2 - 1)
            for (bin in minBin..maxBin) {
                val freq = bin.toFloat() * sampleRate / fftSize
                val mag = sqrt(real[bin] * real[bin] + imag[bin] * imag[bin])
                val midi = 69.0 + 12.0 * (ln(freq / 440.0) / ln(2.0))
                val pc = ((midi.roundToInt() % 12) + 12) % 12
                chroma[pc] += mag
            }
            frame += step
            sliceCount++
        }

        // Correlate against major and minor profiles
        var bestScore = Float.NEGATIVE_INFINITY
        var bestKey = "Am"
        var bestCamelot = "8A"

        for (tonic in 0 until 12) {
            var majCorr = 0f
            var minCorr = 0f
            for (i in 0 until 12) {
                val c = chroma[(tonic + i) % 12]
                majCorr += c * MAJOR_PROFILE[i]
                minCorr += c * MINOR_PROFILE[i]
            }
            if (majCorr > bestScore) {
                bestScore = majCorr
                bestKey = MAJOR_KEYS[tonic]
                bestCamelot = MAJOR_CAMELOT[tonic]
            }
            if (minCorr > bestScore) {
                bestScore = minCorr
                bestKey = MINOR_KEYS[tonic]
                bestCamelot = MINOR_CAMELOT[tonic]
            }
        }
        return bestKey to bestCamelot
    }

    /**
     * Generates frequency-colored waveform buckets (Low / Mid / High energy + overall peak).
     */
    fun generateWaveform(pcm48kStereo: FloatArray, bucketCount: Int = 420): List<WaveformBucket> {
        val totalFrames = pcm48kStereo.size / 2
        if (totalFrames <= 0 || bucketCount <= 0) return emptyList()

        val result = ArrayList<WaveformBucket>(bucketCount)
        val framesPerBucket = max(1, totalFrames / bucketCount)
        val stride = max(1, framesPerBucket / 64)

        var lp = 0f
        var bpPrev = 0f
        var maxPeak = 0.001f

        val rawBuckets = ArrayList<WaveformBucket>(bucketCount)
        for (b in 0 until bucketCount) {
            val start = b * framesPerBucket
            val end = min(totalFrames, start + framesPerBucket)
            var lowSum = 0f
            var midSum = 0f
            var highSum = 0f
            var peak = 0f
            var count = 0

            var i = start
            while (i < end) {
                val m = 0.5f * (pcm48kStereo[i * 2] + pcm48kStereo[i * 2 + 1])
                val absM = abs(m)
                if (absM > peak) peak = absM

                lp = lp * 0.88f + m * 0.12f
                val hp = m - bpPrev
                bpPrev = m * 0.85f + bpPrev * 0.15f
                val mid = m - lp - hp * 0.5f

                lowSum += abs(lp)
                midSum += abs(mid)
                highSum += abs(hp)
                count++
                i += stride
            }
            val inv = 1f / max(1, count)
            val bucket = WaveformBucket(
                low = lowSum * inv,
                mid = midSum * inv,
                high = highSum * inv,
                peak = peak
            )
            maxPeak = max(maxPeak, peak)
            rawBuckets.add(bucket)
        }

        val norm = 1f / maxPeak
        for (b in rawBuckets) {
            result.add(
                WaveformBucket(
                    low = (b.low * norm * 1.8f).coerceIn(0f, 1f),
                    mid = (b.mid * norm * 2.0f).coerceIn(0f, 1f),
                    high = (b.high * norm * 2.2f).coerceIn(0f, 1f),
                    peak = (b.peak * norm).coerceIn(0.04f, 1f)
                )
            )
        }
        return result
    }

    fun serializeWaveform(buckets: List<WaveformBucket>): String {
        if (buckets.isEmpty()) return ""
        val sb = StringBuilder(buckets.size * 12)
        for (i in buckets.indices) {
            val b = buckets[i]
            if (i > 0) sb.append(';')
            sb.append((b.low * 99).roundToInt()).append(',')
                .append((b.mid * 99).roundToInt()).append(',')
                .append((b.high * 99).roundToInt()).append(',')
                .append((b.peak * 99).roundToInt())
        }
        return sb.toString()
    }

    fun deserializeWaveform(serialized: String): List<WaveformBucket> {
        if (serialized.isBlank()) return emptyList()
        return serialized.split(';').mapNotNull { entry ->
            val parts = entry.split(',')
            if (parts.size == 4) {
                WaveformBucket(
                    low = (parts[0].toIntOrNull() ?: 0) / 99f,
                    mid = (parts[1].toIntOrNull() ?: 0) / 99f,
                    high = (parts[2].toIntOrNull() ?: 0) / 99f,
                    peak = (parts[3].toIntOrNull() ?: 10) / 99f
                )
            } else null
        }
    }

    fun areKeysHarmonic(camelotA: String, camelotB: String): Boolean {
        if (camelotA.length < 2 || camelotB.length < 2) return false
        val numA = camelotA.dropLast(1).toIntOrNull() ?: return false
        val letterA = camelotA.last()
        val numB = camelotB.dropLast(1).toIntOrNull() ?: return false
        val letterB = camelotB.last()

        if (numA == numB) return true // Same key or relative major/minor (e.g., 8A <-> 8B)
        if (letterA == letterB) {
            val diff = abs(numA - numB)
            return diff == 1 || diff == 11 // Adjacent on Camelot wheel (12 <-> 1)
        }
        return false
    }

    private fun fft(real: FloatArray, imag: FloatArray) {
        val n = real.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while ((j and bit) != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = real[i]; real[i] = real[j]; real[j] = tr
                val ti = imag[i]; imag[i] = imag[j]; imag[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val half = len / 2
            val angle = -2.0 * PI / len
            val wLenR = cos(angle).toFloat()
            val wLenI = sin(angle).toFloat()
            var i = 0
            while (i < n) {
                var wR = 1f
                var wI = 0f
                for (k in 0 until half) {
                    val uR = real[i + k]
                    val uI = imag[i + k]
                    val vR = real[i + k + half] * wR - imag[i + k + half] * wI
                    val vI = real[i + k + half] * wI + imag[i + k + half] * wR
                    real[i + k] = uR + vR
                    imag[i + k] = uI + vI
                    real[i + k + half] = uR - vR
                    imag[i + k + half] = uI - vI
                    val nextWR = wR * wLenR - wI * wLenI
                    wI = wR * wLenI + wI * wLenR
                    wR = nextWR
                }
                i += len
            }
            len = len shl 1
        }
    }
}
