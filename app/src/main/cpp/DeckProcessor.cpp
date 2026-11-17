#include "DeckProcessor.h"
#include <algorithm>
#include <cmath>

namespace pulsemix {

namespace {
inline float clampf(float v, float lo, float hi) {
    return std::max(lo, std::min(hi, v));
}

inline void sampleCatmullRom(
    const float* pcm,
    int64_t totalFrames,
    double pos,
    float& outL,
    float& outR
) {
    if (!pcm || totalFrames <= 1 || pos < 0.0 || pos >= static_cast<double>(totalFrames - 1)) {
        outL = 0.0f;
        outR = 0.0f;
        return;
    }
    const int64_t i1 = static_cast<int64_t>(pos);
    const float t = static_cast<float>(pos - static_cast<double>(i1));
    const int64_t i0 = std::max<int64_t>(0, i1 - 1);
    const int64_t i2 = std::min(totalFrames - 1, i1 + 1);
    const int64_t i3 = std::min(totalFrames - 1, i1 + 2);

    const float t2 = t * t;
    const float t3 = t2 * t;

    auto catmull = [t, t2, t3](float y0, float y1, float y2, float y3) -> float {
        const float a0 = -0.5f * y0 + 1.5f * y1 - 1.5f * y2 + 0.5f * y3;
        const float a1 = y0 - 2.5f * y1 + 2.0f * y2 - 0.5f * y3;
        const float a2 = -0.5f * y0 + 0.5f * y2;
        const float a3 = y1;
        return a0 * t3 + a1 * t2 + a2 * t + a3;
    };

    outL = catmull(pcm[i0 * 2], pcm[i1 * 2], pcm[i2 * 2], pcm[i3 * 2]);
    outR = catmull(pcm[i0 * 2 + 1], pcm[i1 * 2 + 1], pcm[i2 * 2 + 1], pcm[i3 * 2 + 1]);
}
} // namespace

DeckProcessor::DeckProcessor() {
    eqLowFilter_.setIdentity();
    eqMidFilter_.setIdentity();
    eqHighFilter_.setIdentity();
    comboFilter_.setIdentity();
}

DeckProcessor::~DeckProcessor() {
    reclaimRetired();
    delete pendingTrack_.exchange(nullptr);
    delete currentTrack_;
    currentTrack_ = nullptr;
}

void DeckProcessor::setSampleRate(float sampleRate) {
    sampleRate_ = std::max(22050.0f, sampleRate);
    bpmDetector_.setSampleRate(sampleRate_);
    fxChain_.setSampleRate(sampleRate_);
    lastLowDb_ = 999.0f; // force recompute
    updateEqAndFilterCoeffs();
}

void DeckProcessor::loadTrack(TrackBuffer* newBuffer) {
    if (newBuffer) {
        auto envelope = RealTimeBpmDetector::buildTrackOnsetEnvelope(
            newBuffer->pcm.data(),
            newBuffer->totalFrames,
            sampleRate_
        );
        {
            std::lock_guard<std::mutex> lock(analysisMutex_);
            trackOnsetEnvelope_ = std::move(envelope);
        }
        totalFramesAtomic_.store(newBuffer->totalFrames, std::memory_order_relaxed);
        baseBpmAtomic_.store(newBuffer->baseBpm, std::memory_order_relaxed);
        firstBeatAtomic_.store(newBuffer->firstBeatFrame, std::memory_order_relaxed);
    } else {
        std::lock_guard<std::mutex> lock(analysisMutex_);
        trackOnsetEnvelope_.clear();
    }
    TrackBuffer* prevPending = pendingTrack_.exchange(newBuffer, std::memory_order_acq_rel);
    if (prevPending) {
        delete prevPending;
    }
    reclaimRetired();
}

void DeckProcessor::unloadTrack() {
    isPlaying_.store(false, std::memory_order_relaxed);
    cuePreview_.store(false, std::memory_order_relaxed);
    unloadRequested_.store(true, std::memory_order_release);
    totalFramesAtomic_.store(0, std::memory_order_relaxed);
    cursorAtomic_.store(0.0, std::memory_order_relaxed);
    {
        std::lock_guard<std::mutex> lock(analysisMutex_);
        trackOnsetEnvelope_.clear();
    }
}

void DeckProcessor::reclaimRetired() {
    TrackBuffer* old = retiredTrack_.exchange(nullptr, std::memory_order_acq_rel);
    delete old;
}

void DeckProcessor::adoptPendingTrack() {
    if (unloadRequested_.exchange(false, std::memory_order_acq_rel)) {
        TrackBuffer* old = currentTrack_;
        currentTrack_ = nullptr;
        TrackBuffer* stale = retiredTrack_.exchange(old, std::memory_order_acq_rel);
        // Note: stale would only exist if control thread hasn't reclaimed yet; avoid delete on audio thread
        (void)stale;
        cursor_ = 0.0;
        slipCursor_ = 0.0;
        cursorAtomic_.store(0.0, std::memory_order_relaxed);
        timeStretcher_.reset(0.0);
        bpmDetector_.reset();
        fxChain_.reset();
    }

    TrackBuffer* incoming = pendingTrack_.exchange(nullptr, std::memory_order_acq_rel);
    if (incoming) {
        TrackBuffer* old = currentTrack_;
        currentTrack_ = incoming;
        retiredTrack_.store(old, std::memory_order_release);
        cursor_ = 0.0;
        slipCursor_ = 0.0;
        cursorAtomic_.store(0.0, std::memory_order_relaxed);
        totalFramesAtomic_.store(incoming->totalFrames, std::memory_order_relaxed);
        baseBpmAtomic_.store(incoming->baseBpm, std::memory_order_relaxed);
        firstBeatAtomic_.store(incoming->firstBeatFrame, std::memory_order_relaxed);
        loopActive_.store(false, std::memory_order_relaxed);
        timeStretcher_.reset(0.0);
        bpmDetector_.reset();
        fxChain_.reset();
        fxChain_.setBpm(incoming->baseBpm);
    }
}

void DeckProcessor::setPlaying(bool playing) {
    isPlaying_.store(playing, std::memory_order_relaxed);
}

void DeckProcessor::setCuePreview(bool previewing) {
    cuePreview_.store(previewing, std::memory_order_relaxed);
}

void DeckProcessor::seekToFrame(int64_t frame) {
    seekTarget_.store(frame, std::memory_order_release);
}

void DeckProcessor::setSlipMode(bool enabled) {
    slipMode_.store(enabled, std::memory_order_relaxed);
}

void DeckProcessor::setPitchOffset(float offset) {
    pitchOffset_.store(clampf(offset, -0.50f, 0.50f), std::memory_order_relaxed);
}

void DeckProcessor::setKeyLock(bool enabled) {
    keyLock_.store(enabled, std::memory_order_relaxed);
}

void DeckProcessor::setSyncEnabled(bool enabled) {
    syncEnabled_.store(enabled, std::memory_order_relaxed);
}

void DeckProcessor::setPitchBend(float bend) {
    pitchBend_.store(clampf(bend, -0.10f, 0.10f), std::memory_order_relaxed);
}

void DeckProcessor::setScratchState(bool scratching, float velocity) {
    isScratching_.store(scratching, std::memory_order_relaxed);
    scratchVelocity_.store(clampf(velocity, -6.0f, 6.0f), std::memory_order_relaxed);
}

void DeckProcessor::setLoop(bool active, int64_t startFrame, int64_t endFrame) {
    loopStartFrame_.store(std::max<int64_t>(0, startFrame), std::memory_order_relaxed);
    loopEndFrame_.store(std::max<int64_t>(0, endFrame), std::memory_order_relaxed);
    loopActive_.store(active && (endFrame > startFrame), std::memory_order_release);
}

void DeckProcessor::setChannelVolume(float vol) {
    channelVolume_.store(clampf(vol, 0.0f, 1.0f), std::memory_order_relaxed);
}

void DeckProcessor::setTrimGain(float gain) {
    trimGain_.store(clampf(gain, 0.0f, 2.0f), std::memory_order_relaxed);
}

void DeckProcessor::setEq(float lowDb, float midDb, float highDb) {
    eqLowDb_.store(clampf(lowDb, -24.0f, 12.0f), std::memory_order_relaxed);
    eqMidDb_.store(clampf(midDb, -24.0f, 12.0f), std::memory_order_relaxed);
    eqHighDb_.store(clampf(highDb, -24.0f, 12.0f), std::memory_order_relaxed);
}

void DeckProcessor::setFilter(float filterVal) {
    filterVal_.store(clampf(filterVal, -1.0f, 1.0f), std::memory_order_relaxed);
}

void DeckProcessor::setEffect(EffectType type, float wetDry, float param) {
    fxType_.store(static_cast<int>(type), std::memory_order_relaxed);
    fxWetDry_.store(clampf(wetDry, 0.0f, 1.0f), std::memory_order_relaxed);
    fxParam_.store(clampf(param, 0.0f, 1.0f), std::memory_order_relaxed);
}

void DeckProcessor::setBaseBpm(float bpm) {
    baseBpmAtomic_.store(clampf(bpm, 40.0f, 240.0f), std::memory_order_relaxed);
}

float DeckProcessor::getEffectiveBpm() const {
    const float base = baseBpmAtomic_.load(std::memory_order_relaxed);
    const float pitch = pitchOffset_.load(std::memory_order_relaxed);
    return base * (1.0f + pitch);
}

RealtimeBpmResult DeckProcessor::analyzeRealtimeBpm() const {
    std::lock_guard<std::mutex> lock(analysisMutex_);
    return bpmDetector_.analyze(
        trackOnsetEnvelope_,
        cursorAtomic_.load(std::memory_order_relaxed),
        activeRateAtomic_.load(std::memory_order_relaxed),
        baseBpmAtomic_.load(std::memory_order_relaxed),
        firstBeatAtomic_.load(std::memory_order_relaxed)
    );
}

void DeckProcessor::snapToPhase(double targetBeatPhase) {
    if (!currentTrack_ || currentTrack_->totalFrames <= 0 || currentTrack_->baseBpm <= 1.0f) return;
    const double framesPerBeat = (static_cast<double>(sampleRate_) * 60.0) / static_cast<double>(currentTrack_->baseBpm);
    const double rel = cursor_ - static_cast<double>(currentTrack_->firstBeatFrame);
    const double currentBeatIndex = std::floor(rel / framesPerBeat);
    double currentPhase = (rel / framesPerBeat) - currentBeatIndex;
    if (currentPhase < 0.0) currentPhase += 1.0;

    double deltaPhase = targetBeatPhase - currentPhase;
    if (deltaPhase > 0.5) deltaPhase -= 1.0;
    if (deltaPhase < -0.5) deltaPhase += 1.0;

    double newCursor = cursor_ + deltaPhase * framesPerBeat;
    newCursor = std::max(0.0, std::min(static_cast<double>(currentTrack_->totalFrames - 1), newCursor));
    seekTarget_.store(static_cast<int64_t>(newCursor), std::memory_order_release);
}

void DeckProcessor::updateEqAndFilterCoeffs() {
    const float low = eqLowDb_.load(std::memory_order_relaxed);
    const float mid = eqMidDb_.load(std::memory_order_relaxed);
    const float high = eqHighDb_.load(std::memory_order_relaxed);
    const float filt = filterVal_.load(std::memory_order_relaxed);

    if (std::abs(low - lastLowDb_) > 0.05f) {
        eqLowFilter_.setLowShelf(sampleRate_, 220.0f, low, 0.85f);
        lastLowDb_ = low;
    }
    if (std::abs(mid - lastMidDb_) > 0.05f) {
        eqMidFilter_.setPeaking(sampleRate_, 1000.0f, mid, 0.85f);
        lastMidDb_ = mid;
    }
    if (std::abs(high - lastHighDb_) > 0.05f) {
        eqHighFilter_.setHighShelf(sampleRate_, 4200.0f, high, 0.85f);
        lastHighDb_ = high;
    }
    if (std::abs(filt - lastFilterVal_) > 0.005f) {
        lastFilterVal_ = filt;
        if (std::abs(filt) < 0.03f) {
            comboFilterActive_ = false;
            comboFilter_.setIdentity();
        } else if (filt < 0.0f) {
            // LPF: -0.03 -> 18000 Hz, -1.0 -> 140 Hz (exponential sweep)
            comboFilterActive_ = true;
            const float norm = clampf(-filt, 0.0f, 1.0f);
            const float freq = 18000.0f * std::pow(140.0f / 18000.0f, norm);
            comboFilter_.setLowPass(sampleRate_, freq, 0.95f);
        } else {
            // HPF: +0.03 -> 30 Hz, +1.0 -> 6500 Hz
            comboFilterActive_ = true;
            const float norm = clampf(filt, 0.0f, 1.0f);
            const float freq = 30.0f * std::pow(6500.0f / 30.0f, norm);
            comboFilter_.setHighPass(sampleRate_, freq, 0.95f);
        }
    }
}

void DeckProcessor::process(
    float* outStereo,
    int numFrames,
    double externalSyncRate,
    bool useExternalSyncRate
) {
    adoptPendingTrack();
    updateEqAndFilterCoeffs();

    const EffectType fxType = static_cast<EffectType>(fxType_.load(std::memory_order_relaxed));
    const float fxWet = fxWetDry_.load(std::memory_order_relaxed);
    const float fxParam = fxParam_.load(std::memory_order_relaxed);
    fxChain_.setEffect(fxType, fxWet, fxParam);

    const int64_t seek = seekTarget_.exchange(-1, std::memory_order_acq_rel);
    if (seek >= 0 && currentTrack_ && currentTrack_->totalFrames > 0) {
        cursor_ = clampf(static_cast<float>(seek), 0.0f, static_cast<float>(currentTrack_->totalFrames - 1));
        slipCursor_ = cursor_;
        timeStretcher_.reset(cursor_);
    }

    if (!currentTrack_ || currentTrack_->totalFrames <= 1) {
        std::fill(outStereo, outStereo + numFrames * 2, 0.0f);
        peakLeft_.store(0.0f, std::memory_order_relaxed);
        peakRight_.store(0.0f, std::memory_order_relaxed);
        return;
    }

    const bool playing = isPlaying_.load(std::memory_order_relaxed) || cuePreview_.load(std::memory_order_relaxed);
    const bool scratching = isScratching_.load(std::memory_order_relaxed);
    const bool slip = slipMode_.load(std::memory_order_relaxed);
    const bool loopActive = loopActive_.load(std::memory_order_acquire);
    const int64_t loopStart = loopStartFrame_.load(std::memory_order_relaxed);
    const int64_t loopEnd = loopEndFrame_.load(std::memory_order_relaxed);

    // Slip mode tracking: advance ghost cursor while scratching or looping
    const bool slipTrigger = slip && (scratching || loopActive || fxType == EffectType::REVERSE);
    if (slipTrigger && !wasSlipTrigger_) {
        slipCursor_ = cursor_;
    } else if (!slipTrigger && wasSlipTrigger_ && playing) {
        cursor_ = clampf(static_cast<float>(slipCursor_), 0.0f, static_cast<float>(currentTrack_->totalFrames - 1));
        timeStretcher_.reset(cursor_);
    }
    wasSlipTrigger_ = slipTrigger;

    if (!playing && !scratching) {
        std::fill(outStereo, outStereo + numFrames * 2, 0.0f);
        peakLeft_.store(0.0f, std::memory_order_relaxed);
        peakRight_.store(0.0f, std::memory_order_relaxed);
        cursorAtomic_.store(cursor_, std::memory_order_relaxed);
        return;
    }

    const float pitchOff = pitchOffset_.load(std::memory_order_relaxed);
    const float bend = pitchBend_.load(std::memory_order_relaxed);
    double rate = useExternalSyncRate ? externalSyncRate : (1.0 + static_cast<double>(pitchOff));
    rate += static_cast<double>(bend);
    rate = std::max(0.25, std::min(2.5, rate));
    activeRateAtomic_.store(rate, std::memory_order_relaxed);

    fxChain_.setBpm(currentTrack_->baseBpm * static_cast<float>(rate));

    const float* pcm = currentTrack_->pcm.data();
    const int64_t totalFrames = currentTrack_->totalFrames;
    const bool useKeyLock = keyLock_.load(std::memory_order_relaxed);
    const bool reverseFx = fxChain_.isReverseEffectActive();

    float maxL = 0.0f;
    float maxR = 0.0f;

    // Reset time-stretcher cleanly when releasing from scratch back to playback
    if (wasScratching_ && !scratching) {
        timeStretcher_.reset(cursor_);
        smoothedScratchVel_ = 0.0f;
    }

    int processed = 0;
    while (processed < numFrames) {
        const int chunk = std::min(MAX_CHUNK_FRAMES, numFrames - processed);
        float* buf = chunkBuf_.data();

        if (scratching) {
            const float targetVel = scratchVelocity_.load(std::memory_order_relaxed);
            if (!wasScratching_ && processed == 0) {
                smoothedScratchVel_ = targetVel;
            } else {
                smoothedScratchVel_ = smoothedScratchVel_ * 0.65f + targetVel * 0.35f;
            }
            for (int i = 0; i < chunk; ++i) {
                float l = 0.0f, r = 0.0f;
                sampleCatmullRom(pcm, totalFrames, cursor_, l, r);
                buf[i * 2] = l;
                buf[i * 2 + 1] = r;
                cursor_ += static_cast<double>(smoothedScratchVel_);
                if (cursor_ < 0.0) cursor_ = 0.0;
                if (cursor_ >= static_cast<double>(totalFrames - 1)) cursor_ = static_cast<double>(totalFrames - 1);
            }
            if (slipTrigger) {
                slipCursor_ += rate * static_cast<double>(chunk);
            }
        } else {
            const float brakeMult = fxChain_.getBrakeSpeedMultiplier();
            const double effectiveDir = reverseFx ? -1.0 : 1.0;
            const double signedRate = rate * static_cast<double>(brakeMult) * effectiveDir;

            if (useKeyLock && !reverseFx && brakeMult > 0.98f && std::abs(rate - 1.0) > 0.002) {
                timeStretcher_.process(
                    pcm,
                    totalFrames,
                    cursor_,
                    rate,
                    loopActive,
                    loopStart,
                    loopEnd,
                    buf,
                    chunk
                );
            } else {
                for (int i = 0; i < chunk; ++i) {
                    float l = 0.0f, r = 0.0f;
                    sampleCatmullRom(pcm, totalFrames, cursor_, l, r);
                    buf[i * 2] = l;
                    buf[i * 2 + 1] = r;

                    cursor_ += signedRate;
                    if (loopActive && loopEnd > loopStart) {
                        const double loopLen = static_cast<double>(loopEnd - loopStart);
                        if (cursor_ >= static_cast<double>(loopEnd)) {
                            cursor_ -= loopLen;
                        } else if (cursor_ < static_cast<double>(loopStart)) {
                            cursor_ += loopLen;
                        }
                    } else {
                        if (cursor_ >= static_cast<double>(totalFrames - 1)) {
                            cursor_ = static_cast<double>(totalFrames - 1);
                            isPlaying_.store(false, std::memory_order_relaxed);
                        } else if (cursor_ < 0.0) {
                            cursor_ = 0.0;
                        }
                    }
                }
            }
            if (slipTrigger) {
                slipCursor_ += rate * static_cast<double>(chunk);
                if (slipCursor_ >= static_cast<double>(totalFrames - 1)) {
                    slipCursor_ = static_cast<double>(totalFrames - 1);
                }
            }
        }

        // Feed pre-fader stretched deck audio into lock-free real-time Oboe BPM detector
        if (!scratching) {
            bpmDetector_.pushFrames(buf, chunk);
        }

        // Apply FX chain (pre-fader insert)
        fxChain_.processStereo(buf, chunk);

        // Apply Trim + 3-band EQ + Combo Filter + Channel Volume
        const float chGain = channelVolume_.load(std::memory_order_relaxed) * trimGain_.load(std::memory_order_relaxed);
        for (int i = 0; i < chunk; ++i) {
            float l = buf[i * 2];
            float r = buf[i * 2 + 1];

            eqLowFilter_.process(l, r);
            eqMidFilter_.process(l, r);
            eqHighFilter_.process(l, r);
            if (comboFilterActive_) {
                comboFilter_.process(l, r);
            }

            l *= chGain;
            r *= chGain;

            maxL = std::max(maxL, std::abs(l));
            maxR = std::max(maxR, std::abs(r));

            outStereo[(processed + i) * 2] = l;
            outStereo[(processed + i) * 2 + 1] = r;
        }

        processed += chunk;
    }

    wasScratching_ = scratching;
    cursorAtomic_.store(cursor_, std::memory_order_relaxed);
    peakLeft_.store(maxL, std::memory_order_relaxed);
    peakRight_.store(maxR, std::memory_order_relaxed);
}

} // namespace pulsemix
