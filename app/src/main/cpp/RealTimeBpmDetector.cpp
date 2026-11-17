#include "RealTimeBpmDetector.h"
#include <algorithm>
#include <cmath>

namespace pulsemix {

namespace {
inline float clampf(float v, float lo, float hi) {
    return std::max(lo, std::min(hi, v));
}
} // namespace

RealTimeBpmDetector::RealTimeBpmDetector() {
    reset();
}

void RealTimeBpmDetector::setSampleRate(float sampleRate) {
    sampleRate_ = std::max(22050.0f, sampleRate);
    hopFrames_ = std::max(64, static_cast<int>(std::round(sampleRate_ / 100.0f)));
}

void RealTimeBpmDetector::reset() {
    lpState_ = 0.0f;
    accLowEnergy_ = 0.0f;
    accFullEnergy_ = 0.0f;
    prevLowEnergy_ = 0.0f;
    prevFullEnergy_ = 0.0f;
    sampleInHop_ = 0;
    onsetRing_.fill(0.0f);
    totalHopsWritten_.store(0, std::memory_order_release);
}

void RealTimeBpmDetector::pushFrames(const float* stereoPcm, int numFrames) {
    if (!stereoPcm || numFrames <= 0) return;

    for (int i = 0; i < numFrames; ++i) {
        const float mono = 0.5f * (stereoPcm[i * 2] + stereoPcm[i * 2 + 1]);
        lpState_ = lpState_ * 0.94f + mono * 0.06f;
        accLowEnergy_ += lpState_ * lpState_;
        accFullEnergy_ += mono * mono;
        ++sampleInHop_;

        if (sampleInHop_ >= hopFrames_) {
            const float dLow = std::max(0.0f, accLowEnergy_ - prevLowEnergy_);
            const float dFull = std::max(0.0f, accFullEnergy_ - prevFullEnergy_);
            const float onsetVal = dLow * 2.2f + dFull;

            prevLowEnergy_ = accLowEnergy_;
            prevFullEnergy_ = accFullEnergy_;
            accLowEnergy_ = 0.0f;
            accFullEnergy_ = 0.0f;
            sampleInHop_ = 0;

            const uint64_t idx = totalHopsWritten_.load(std::memory_order_relaxed);
            onsetRing_[idx % RING_SIZE] = onsetVal;
            totalHopsWritten_.store(idx + 1, std::memory_order_release);
        }
    }
}

std::vector<float> RealTimeBpmDetector::buildTrackOnsetEnvelope(
    const float* stereoPcm,
    int64_t totalFrames,
    float sampleRate
) {
    std::vector<float> onsets;
    if (!stereoPcm || totalFrames <= 0) return onsets;

    const int hopFrames = std::max(64, static_cast<int>(std::round(sampleRate / 100.0f)));
    const int64_t numHops = totalFrames / hopFrames;
    if (numHops <= 0) return onsets;

    onsets.resize(static_cast<size_t>(numHops), 0.0f);
    float lp = 0.0f;
    float prevLow = 0.0f;
    float prevFull = 0.0f;

    for (int64_t h = 0; h < numHops; ++h) {
        const int64_t start = h * hopFrames;
        float lowE = 0.0f;
        float fullE = 0.0f;
        for (int i = 0; i < hopFrames; ++i) {
            const int64_t f = start + i;
            const float mono = 0.5f * (stereoPcm[f * 2] + stereoPcm[f * 2 + 1]);
            lp = lp * 0.94f + mono * 0.06f;
            lowE += lp * lp;
            fullE += mono * mono;
        }
        const float dLow = std::max(0.0f, lowE - prevLow);
        const float dFull = std::max(0.0f, fullE - prevFull);
        onsets[static_cast<size_t>(h)] = dLow * 2.2f + dFull;
        prevLow = lowE;
        prevFull = fullE;
    }
    return onsets;
}

void RealTimeBpmDetector::estimateBpmFromOnsets(
    const float* onsets,
    int count,
    float hopRateHz,
    float& outBpm,
    float& outConfidence
) {
    outBpm = 0.0f;
    outConfidence = 0.0f;
    if (!onsets || count < 180 || hopRateHz <= 1.0f) return;

    float totalEnergy = 0.0f;
    float maxOnset = 0.0f;
    for (int i = 0; i < count; ++i) {
        totalEnergy += onsets[i] * onsets[i];
        maxOnset = std::max(maxOnset, onsets[i]);
    }
    if (maxOnset < 1e-6f || totalEnergy < 1e-8f) {
        return;
    }

    const float hopScale = hopRateHz * 60.0f; // e.g., 6000 at 100 Hz
    float bestBpm = 128.0f;
    float bestScore = -1.0f;
    double scoreSum = 0.0;
    int candidates = 0;

    // Coarse autocorrelation across 68..184 BPM (0.5 BPM step)
    for (int k = 0; k <= 232; ++k) {
        const float bpm = 68.0f + k * 0.5f;
        const int lag = static_cast<int>(std::round(hopScale / bpm));
        if (lag <= 0 || lag >= count / 2) continue;

        float sum = 0.0f;
        for (int i = 0; i < count - lag; ++i) {
            sum += onsets[i] * onsets[i + lag];
            if (i + lag * 2 < count) {
                sum += 0.5f * onsets[i] * onsets[i + lag * 2];
            }
        }
        const float prior = (bpm >= 96.0f && bpm <= 148.0f) ? 1.18f : 0.92f;
        const float score = sum * prior;
        scoreSum += static_cast<double>(score);
        ++candidates;

        if (score > bestScore) {
            bestScore = score;
            bestBpm = bpm;
        }
    }

    if (candidates == 0 || bestScore <= 0.0f) return;

    // Fine comb-filter refinement around bestBpm in 0.1 BPM steps
    float refinedBpm = bestBpm;
    float refinedScore = -1.0f;
    const float startBpm = std::max(65.0f, bestBpm - 1.0f);
    const float endBpm = std::min(190.0f, bestBpm + 1.0f);

    for (float b = startBpm; b <= endBpm + 0.001f; b += 0.1f) {
        const float lagFloat = hopScale / b;
        float score = 0.0f;
        float pos = 0.0f;
        int hits = 0;
        while (static_cast<int>(std::round(pos)) < count && hits < 64) {
            const int p = static_cast<int>(std::round(pos));
            if (p >= 0 && p < count) {
                score += onsets[p];
            }
            pos += lagFloat;
            ++hits;
        }
        if (score > refinedScore) {
            refinedScore = score;
            refinedBpm = b;
        }
    }

    const double meanScore = scoreSum / static_cast<double>(candidates);
    const float ratio = (meanScore > 1e-9)
        ? static_cast<float>(static_cast<double>(bestScore) / meanScore)
        : 1.0f;
    // Typical rhythmic track exhibits peak-to-mean autocorrelation ratio >= 1.35..3.5
    outConfidence = clampf((ratio - 1.05f) / 1.8f, 0.05f, 1.0f);
    outBpm = std::round(refinedBpm * 10.0f) / 10.0f;
}

RealtimeBpmResult RealTimeBpmDetector::analyze(
    const std::vector<float>& trackOnsetEnvelope,
    double cursorFrame,
    double playbackRate,
    float fallbackBaseBpm,
    int64_t firstBeatFrame
) const {
    RealtimeBpmResult result;
    const float hopRateHz = sampleRate_ / static_cast<float>(std::max(1, hopFrames_));

    std::array<float, ANALYSIS_WINDOW_HOPS> window{};
    const uint64_t written = totalHopsWritten_.load(std::memory_order_acquire);

    if (written >= static_cast<uint64_t>(LIVE_MIN_HOPS)) {
        // Leave 128-hop safety margin from audio writer head
        const int usable = static_cast<int>(
            std::min<uint64_t>(written, static_cast<uint64_t>(ANALYSIS_WINDOW_HOPS))
        );
        const uint64_t startHop = written - static_cast<uint64_t>(usable);
        for (int i = 0; i < usable; ++i) {
            window[i] = onsetRing_[(startHop + static_cast<uint64_t>(i)) % RING_SIZE];
        }

        float liveBpm = 0.0f;
        float conf = 0.0f;
        estimateBpmFromOnsets(window.data(), usable, hopRateHz, liveBpm, conf);
        if (liveBpm > 1.0f) {
            result.detectedBpm = liveBpm;
            result.confidence = conf;
            result.fromLiveStream = true;
            result.analyzedHops = usable;
        }
    }

    if (result.detectedBpm <= 1.0f && !trackOnsetEnvelope.empty()) {
        const int totalTrackHops = static_cast<int>(trackOnsetEnvelope.size());
        const int cursorHop = static_cast<int>(cursorFrame / static_cast<double>(hopFrames_));
        const int usable = std::min(totalTrackHops, ANALYSIS_WINDOW_HOPS);
        int startHop = std::max(0, cursorHop - usable / 3);
        if (startHop + usable > totalTrackHops) {
            startHop = std::max(0, totalTrackHops - usable);
        }

        float trackBpm = 0.0f;
        float conf = 0.0f;
        estimateBpmFromOnsets(
            trackOnsetEnvelope.data() + startHop,
            usable,
            hopRateHz,
            trackBpm,
            conf
        );
        if (trackBpm > 1.0f) {
            result.detectedBpm = std::round(trackBpm * static_cast<float>(playbackRate) * 10.0f) / 10.0f;
            result.confidence = conf;
            result.fromLiveStream = false;
            result.analyzedHops = usable;
        }
    }

    if (result.detectedBpm <= 1.0f && fallbackBaseBpm > 1.0f) {
        result.detectedBpm = std::round(fallbackBaseBpm * static_cast<float>(playbackRate) * 10.0f) / 10.0f;
        result.confidence = 0.1f;
        result.fromLiveStream = false;
    }

    const float phaseBpm = (fallbackBaseBpm > 1.0f) ? fallbackBaseBpm : result.detectedBpm;
    if (phaseBpm > 1.0f) {
        const double framesPerBeat = (static_cast<double>(sampleRate_) * 60.0) / static_cast<double>(phaseBpm);
        const double beats = (cursorFrame - static_cast<double>(firstBeatFrame)) / framesPerBeat;
        double phase = beats - std::floor(beats);
        if (phase < 0.0) phase += 1.0;
        result.beatPhase = static_cast<float>(phase);
    }

    return result;
}

} // namespace pulsemix
