#include "AudioEngine.h"
#include <android/log.h>
#include <algorithm>
#include <chrono>
#include <cmath>
#include <cstring>

#define LOG_TAG "PulseMixAudio"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace pulsemix {

namespace {
constexpr float PI_F = 3.14159265358979323846f;
constexpr float TWO_PI_F = 6.28318530717958647692f;

inline float clampf(float v, float lo, float hi) {
    return std::max(lo, std::min(hi, v));
}

inline float softLimit(float x) {
    if (x > 1.0f || x < -1.0f) {
        return std::tanh(x);
    }
    return x;
}

// Deterministic pseudo-random for synthesized drum noise (clap/snare/hat)
inline float nextNoise(uint32_t& state) {
    state = state * 1664525u + 1013904223u;
    return (static_cast<float>(state & 0x00FFFFFF) / 8388608.0f) - 1.0f;
}
} // namespace

AudioEngine::AudioEngine() {
    recRingBuf_.assign(REC_RING_FRAMES * 2, 0.0f);
    initDefaultSamplerPads();
}

AudioEngine::~AudioEngine() {
    stopRecording();
    stop();
}

bool AudioEngine::start() {
    if (stream_ && stream_->getState() == oboe::StreamState::Started) {
        return true;
    }

    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(oboe::SharingMode::Shared)
        ->setFormat(oboe::AudioFormat::Float)
        ->setChannelCount(oboe::ChannelCount::Stereo)
        ->setSampleRate(SAMPLE_RATE)
        ->setUsage(oboe::Usage::Media)
        ->setContentType(oboe::ContentType::Music)
        ->setDataCallback(this)
        ->setErrorCallback(this);

    oboe::Result result = builder.openStream(stream_);
    if (result != oboe::Result::OK || !stream_) {
        LOGE("Failed to open Oboe stream: %s", oboe::convertToText(result));
        return false;
    }

    const int sr = stream_->getSampleRate();
    const int burst = stream_->getFramesPerBurst();
    stream_->setBufferSizeInFrames(burst * 2);

    streamSampleRate_.store(sr, std::memory_order_relaxed);
    streamFramesPerBurst_.store(burst, std::memory_order_relaxed);
    streamBufferSizeFrames_.store(stream_->getBufferSizeInFrames(), std::memory_order_relaxed);
    streamLowLatency_.store(stream_->getPerformanceMode() == oboe::PerformanceMode::LowLatency, std::memory_order_relaxed);
    streamAudioApi_.store(stream_->getAudioApi() == oboe::AudioApi::AAudio ? 1 : 2, std::memory_order_relaxed);

    deckA_.setSampleRate(static_cast<float>(sr));
    deckB_.setSampleRate(static_cast<float>(sr));

    result = stream_->requestStart();
    if (result != oboe::Result::OK) {
        LOGE("Failed to start Oboe stream: %s", oboe::convertToText(result));
        stream_->close();
        stream_.reset();
        return false;
    }

    LOGI("Oboe stream started: SR=%d, burst=%d, API=%s",
         sr, burst, stream_->getAudioApi() == oboe::AudioApi::AAudio ? "AAudio" : "OpenSLES");
    return true;
}

void AudioEngine::stop() {
    if (stream_) {
        stream_->stop();
        stream_->close();
        stream_.reset();
    }
}

void AudioEngine::onErrorAfterClose(oboe::AudioStream* /*oboeStream*/, oboe::Result error) {
    LOGI("Oboe stream error after close (%s), restarting stream", oboe::convertToText(error));
    if (error == oboe::Result::ErrorDisconnected) {
        stream_.reset();
        start();
    }
}

void AudioEngine::setCrossfader(float value) {
    autoFadeActive_.store(false, std::memory_order_relaxed);
    crossfader_.store(clampf(value, -1.0f, 1.0f), std::memory_order_relaxed);
}

void AudioEngine::setCrossfaderCurve(int curve) {
    crossfaderCurve_.store(std::max(0, std::min(2, curve)), std::memory_order_relaxed);
}

void AudioEngine::triggerAutoCrossfade(int targetDeck, float durationSeconds) {
    const float current = crossfader_.load(std::memory_order_relaxed);
    const float target = (targetDeck == 0) ? -1.0f : 1.0f;
    const float sr = static_cast<float>(streamSampleRate_.load(std::memory_order_relaxed));
    const float totalFrames = std::max(1000.0f, durationSeconds * sr);
    const float step = (target - current) / totalFrames;

    autoFadeTarget_.store(target, std::memory_order_relaxed);
    autoFadeStepPerFrame_.store(step, std::memory_order_relaxed);
    autoFadeActive_.store(true, std::memory_order_release);
}

void AudioEngine::setMasterVolume(float vol) {
    masterVolume_.store(clampf(vol, 0.0f, 1.2f), std::memory_order_relaxed);
}

void AudioEngine::setSamplerMasterVolume(float vol) {
    samplerMasterVolume_.store(clampf(vol, 0.0f, 1.2f), std::memory_order_relaxed);
}

void AudioEngine::setMasterDeck(int index) {
    masterDeck_.store(index == 1 ? 1 : 0, std::memory_order_relaxed);
}

void AudioEngine::computeCrossfaderGains(float xf, int curve, float& gainA, float& gainB) const {
    // norm in [0, 1]: 0 = full A, 1 = full B
    const float norm = clampf((xf + 1.0f) * 0.5f, 0.0f, 1.0f);
    if (curve == 0) {
        // Smooth linear blend (continuous 0..1 crossfade without step discontinuities)
        gainA = 1.0f - norm;
        gainB = norm;
    } else if (curve == 2) {
        // Sharp Scratch Cut
        gainA = (norm < 0.92f) ? 1.0f : clampf((1.0f - norm) / 0.08f, 0.0f, 1.0f);
        gainB = (norm > 0.08f) ? 1.0f : clampf(norm / 0.08f, 0.0f, 1.0f);
    } else {
        // Constant Power (Equal Power)
        gainA = std::cos(norm * PI_F * 0.5f);
        gainB = std::sin(norm * PI_F * 0.5f);
    }
}

void AudioEngine::computeAutoSyncRate(
    const DeckProcessor& master,
    const DeckProcessor& slave,
    double& outRate
) const {
    const float masterBpm = master.getBaseBpm();
    const float slaveBpm = slave.getBaseBpm();
    if (masterBpm <= 1.0f || slaveBpm <= 1.0f) {
        outRate = 1.0 + static_cast<double>(slave.getPitchOffset());
        return;
    }

    const double masterEffectiveBpm = static_cast<double>(master.getEffectiveBpm());
    double baseRate = masterEffectiveBpm / static_cast<double>(slaveBpm);

    // Phase-Locked Loop (PLL) alignment on the actual audio timeline
    if (master.isPlaying() && slave.isPlaying() && !master.isScratching() && !slave.isScratching()) {
        const double sr = static_cast<double>(streamSampleRate_.load(std::memory_order_relaxed));
        const double framesPerBeatM = (sr * 60.0) / static_cast<double>(masterBpm);
        const double framesPerBeatS = (sr * 60.0) / static_cast<double>(slaveBpm);

        const double beatPosM = (master.getCursorFrame() - static_cast<double>(master.getFirstBeatFrame())) / framesPerBeatM;
        const double beatPosS = (slave.getCursorFrame() - static_cast<double>(slave.getFirstBeatFrame())) / framesPerBeatS;

        double phaseM = beatPosM - std::floor(beatPosM);
        double phaseS = beatPosS - std::floor(beatPosS);
        if (phaseM < 0.0) phaseM += 1.0;
        if (phaseS < 0.0) phaseS += 1.0;

        double phaseErr = phaseM - phaseS;
        if (phaseErr > 0.5) phaseErr -= 1.0;
        if (phaseErr < -0.5) phaseErr += 1.0;

        const double correction = std::max(-0.06, std::min(0.06, phaseErr * 0.14));
        baseRate *= (1.0 + correction);
    }

    outRate = std::max(0.25, std::min(2.5, baseRate));
}

void AudioEngine::initDefaultSamplerPads() {
    uint32_t rng = 0x12345678u;
    const int sr = SAMPLE_RATE;

    // 0: Punchy 909 Kick (0.45s)
    {
        const int frames = static_cast<int>(sr * 0.45f);
        auto& pcm = samplerPads_[0].pcm;
        pcm.resize(frames * 2);
        double phase = 0.0;
        for (int i = 0; i < frames; ++i) {
            const float t = static_cast<float>(i) / sr;
            const float freq = 46.0f + 135.0f * std::exp(-t * 28.0f);
            const float env = std::exp(-t * 8.5f);
            phase += TWO_PI_F * freq / sr;
            const float s = std::tanh(std::sin(static_cast<float>(phase)) * 1.6f) * env * 0.9f;
            pcm[i * 2] = s;
            pcm[i * 2 + 1] = s;
        }
        samplerPads_[0].totalFrames = frames;
    }

    // 1: Crisp Snare (0.32s)
    {
        const int frames = static_cast<int>(sr * 0.32f);
        auto& pcm = samplerPads_[1].pcm;
        pcm.resize(frames * 2);
        double phase = 0.0;
        for (int i = 0; i < frames; ++i) {
            const float t = static_cast<float>(i) / sr;
            const float bodyFreq = 185.0f + 90.0f * std::exp(-t * 35.0f);
            phase += TWO_PI_F * bodyFreq / sr;
            const float body = std::sin(static_cast<float>(phase)) * std::exp(-t * 18.0f) * 0.55f;
            const float noise = nextNoise(rng) * std::exp(-t * 13.0f) * 0.65f;
            const float s = (body + noise) * 0.85f;
            pcm[i * 2] = s;
            pcm[i * 2 + 1] = s;
        }
        samplerPads_[1].totalFrames = frames;
    }

    // 2: Club Clap (0.35s)
    {
        const int frames = static_cast<int>(sr * 0.35f);
        auto& pcm = samplerPads_[2].pcm;
        pcm.resize(frames * 2);
        float lp = 0.0f;
        for (int i = 0; i < frames; ++i) {
            const float t = static_cast<float>(i) / sr;
            float env = 0.0f;
            if (t < 0.035f) {
                const float burst = std::fmod(t, 0.011f) / 0.011f;
                env = (1.0f - burst);
            } else {
                env = std::exp(-(t - 0.035f) * 14.0f);
            }
            const float n = nextNoise(rng);
            lp = lp * 0.55f + n * 0.45f;
            const float s = (n - lp * 0.6f) * env * 0.9f;
            pcm[i * 2] = s;
            pcm[i * 2 + 1] = s;
        }
        samplerPads_[2].totalFrames = frames;
    }

    // 3: Tight Closed Hi-Hat (0.12s)
    {
        const int frames = static_cast<int>(sr * 0.12f);
        auto& pcm = samplerPads_[3].pcm;
        pcm.resize(frames * 2);
        float prev = 0.0f;
        for (int i = 0; i < frames; ++i) {
            const float t = static_cast<float>(i) / sr;
            const float env = std::exp(-t * 42.0f);
            const float n = nextNoise(rng);
            const float hp = n - prev;
            prev = n * 0.92f;
            const float s = hp * env * 0.55f;
            pcm[i * 2] = s;
            pcm[i * 2 + 1] = s;
        }
        samplerPads_[3].totalFrames = frames;
    }

    // 4: Rimshot / Perc (0.15s)
    {
        const int frames = static_cast<int>(sr * 0.15f);
        auto& pcm = samplerPads_[4].pcm;
        pcm.resize(frames * 2);
        for (int i = 0; i < frames; ++i) {
            const float t = static_cast<float>(i) / sr;
            const float env = std::exp(-t * 36.0f);
            const float s = (std::sin(TWO_PI_F * 480.0f * t) * 0.6f + std::sin(TWO_PI_F * 1650.0f * t) * 0.4f) * env * 0.8f;
            pcm[i * 2] = s;
            pcm[i * 2 + 1] = s;
        }
        samplerPads_[4].totalFrames = frames;
    }

    // 5: Crash Cymbal (1.1s)
    {
        const int frames = static_cast<int>(sr * 1.1f);
        auto& pcm = samplerPads_[5].pcm;
        pcm.resize(frames * 2);
        float prev = 0.0f;
        for (int i = 0; i < frames; ++i) {
            const float t = static_cast<float>(i) / sr;
            const float env = std::exp(-t * 3.5f);
            const float n = nextNoise(rng);
            const float hp = n - prev;
            prev = n * 0.85f;
            const float metallic = std::sin(TWO_PI_F * 587.0f * t) * std::sin(TWO_PI_F * 845.0f * t) * 0.25f;
            const float s = (hp * 0.65f + metallic) * env * 0.55f;
            pcm[i * 2] = s;
            pcm[i * 2 + 1] = s;
        }
        samplerPads_[5].totalFrames = frames;
    }

    // 6: 808 Sub Drop (0.95s)
    {
        const int frames = static_cast<int>(sr * 0.95f);
        auto& pcm = samplerPads_[6].pcm;
        pcm.resize(frames * 2);
        double phase = 0.0;
        for (int i = 0; i < frames; ++i) {
            const float t = static_cast<float>(i) / sr;
            const float freq = 95.0f * std::exp(-t * 2.2f) + 32.0f;
            phase += TWO_PI_F * freq / sr;
            const float env = std::min(1.0f, t * 80.0f) * std::exp(-t * 2.4f);
            const float s = std::sin(static_cast<float>(phase)) * env * 0.85f;
            pcm[i * 2] = s;
            pcm[i * 2 + 1] = s;
        }
        samplerPads_[6].totalFrames = frames;
    }

    // 7: DJ Horn / Riser Sweep (0.85s)
    {
        const int frames = static_cast<int>(sr * 0.85f);
        auto& pcm = samplerPads_[7].pcm;
        pcm.resize(frames * 2);
        double p1 = 0.0, p2 = 0.0;
        for (int i = 0; i < frames; ++i) {
            const float t = static_cast<float>(i) / sr;
            const float f = 440.0f + 220.0f * std::sin(TWO_PI_F * 3.5f * t);
            p1 += TWO_PI_F * f / sr;
            p2 += TWO_PI_F * (f * 1.498f) / sr;
            const float env = std::min(1.0f, t * 40.0f) * std::exp(-t * 2.6f);
            const float s = std::tanh((std::sin(static_cast<float>(p1)) + std::sin(static_cast<float>(p2))) * 0.9f) * env * 0.65f;
            pcm[i * 2] = s;
            pcm[i * 2 + 1] = s;
        }
        samplerPads_[7].totalFrames = frames;
    }
}

void AudioEngine::loadSamplerPad(int padIndex, const float* interleavedPcm, int64_t numFrames) {
    if (padIndex < 0 || padIndex >= NUM_SAMPLER_PADS || !interleavedPcm || numFrames <= 0) return;
    auto& pad = samplerPads_[padIndex];
    pad.active.store(false, std::memory_order_release);
    pad.pcm.assign(interleavedPcm, interleavedPcm + numFrames * 2);
    pad.totalFrames = numFrames;
    pad.cursor.store(0.0, std::memory_order_relaxed);
}

void AudioEngine::triggerSamplerPad(int padIndex) {
    if (padIndex < 0 || padIndex >= NUM_SAMPLER_PADS) return;
    samplerPads_[padIndex].retrigger.store(true, std::memory_order_release);
}

void AudioEngine::setSamplerPadVolume(int padIndex, float volume) {
    if (padIndex < 0 || padIndex >= NUM_SAMPLER_PADS) return;
    samplerPads_[padIndex].volume.store(clampf(volume, 0.0f, 1.5f), std::memory_order_relaxed);
}

void AudioEngine::setSamplerPadPitch(int padIndex, float pitch) {
    if (padIndex < 0 || padIndex >= NUM_SAMPLER_PADS) return;
    samplerPads_[padIndex].pitch.store(clampf(pitch, 0.5f, 2.0f), std::memory_order_relaxed);
}

void AudioEngine::writeWavHeader(FILE* f, uint32_t dataBytes) {
    const uint32_t sampleRate = static_cast<uint32_t>(streamSampleRate_.load(std::memory_order_relaxed));
    const uint16_t channels = 2;
    const uint16_t bitsPerSample = 16;
    const uint32_t byteRate = sampleRate * channels * (bitsPerSample / 8);
    const uint16_t blockAlign = channels * (bitsPerSample / 8);
    const uint32_t chunkSize = 36 + dataBytes;
    const uint32_t subchunk1Size = 16;
    const uint16_t audioFormat = 1; // PCM

    fseek(f, 0, SEEK_SET);
    fwrite("RIFF", 1, 4, f);
    fwrite(&chunkSize, 4, 1, f);
    fwrite("WAVE", 1, 4, f);
    fwrite("fmt ", 1, 4, f);
    fwrite(&subchunk1Size, 4, 1, f);
    fwrite(&audioFormat, 2, 1, f);
    fwrite(&channels, 2, 1, f);
    fwrite(&sampleRate, 4, 1, f);
    fwrite(&byteRate, 4, 1, f);
    fwrite(&blockAlign, 2, 1, f);
    fwrite(&bitsPerSample, 2, 1, f);
    fwrite("data", 1, 4, f);
    fwrite(&dataBytes, 4, 1, f);
}

bool AudioEngine::startRecording(const std::string& wavFilePath) {
    if (isRecording_.load(std::memory_order_relaxed)) return false;
    recFile_ = fopen(wavFilePath.c_str(), "wb");
    if (!recFile_) {
        LOGE("Failed to open recording file: %s", wavFilePath.c_str());
        return false;
    }
    writeWavHeader(recFile_, 0);
    recWriteFrame_.store(0, std::memory_order_relaxed);
    recReadFrame_.store(0, std::memory_order_relaxed);
    recordedFrames_.store(0, std::memory_order_relaxed);
    isRecording_.store(true, std::memory_order_release);
    recThreadRunning_.store(true, std::memory_order_release);
    recWriterThread_ = std::thread(&AudioEngine::recordingLoop, this);
    return true;
}

void AudioEngine::stopRecording() {
    if (!isRecording_.exchange(false, std::memory_order_acq_rel) && !recThreadRunning_.load()) {
        return;
    }
    recThreadRunning_.store(false, std::memory_order_release);
    if (recWriterThread_.joinable()) {
        recWriterThread_.join();
    }
    if (recFile_) {
        const uint32_t dataBytes = static_cast<uint32_t>(recordedFrames_.load() * 2 * sizeof(int16_t));
        writeWavHeader(recFile_, dataBytes);
        fclose(recFile_);
        recFile_ = nullptr;
    }
}

int64_t AudioEngine::getRecordedMs() const {
    const int sr = std::max(1, streamSampleRate_.load(std::memory_order_relaxed));
    return (recordedFrames_.load(std::memory_order_relaxed) * 1000LL) / sr;
}

void AudioEngine::recordingLoop() {
    std::vector<int16_t> pcm16Chunk(4096 * 2);
    while (recThreadRunning_.load(std::memory_order_acquire) ||
           recReadFrame_.load(std::memory_order_acquire) < recWriteFrame_.load(std::memory_order_acquire)) {
        const uint64_t r = recReadFrame_.load(std::memory_order_relaxed);
        const uint64_t w = recWriteFrame_.load(std::memory_order_acquire);
        const uint64_t avail = (w >= r) ? (w - r) : 0;
        if (avail == 0) {
            std::this_thread::sleep_for(std::chrono::milliseconds(8));
            continue;
        }
        const uint64_t toWrite = std::min<uint64_t>(avail, 4096);
        for (uint64_t i = 0; i < toWrite; ++i) {
            const uint64_t idx = ((r + i) % REC_RING_FRAMES) * 2;
            const float fL = clampf(recRingBuf_[idx], -1.0f, 1.0f);
            const float fR = clampf(recRingBuf_[idx + 1], -1.0f, 1.0f);
            pcm16Chunk[i * 2] = static_cast<int16_t>(fL * 32767.0f);
            pcm16Chunk[i * 2 + 1] = static_cast<int16_t>(fR * 32767.0f);
        }
        if (recFile_) {
            fwrite(pcm16Chunk.data(), sizeof(int16_t) * 2, toWrite, recFile_);
            recordedFrames_.fetch_add(static_cast<int64_t>(toWrite), std::memory_order_relaxed);
        }
        recReadFrame_.store(r + toWrite, std::memory_order_release);
    }
}

oboe::DataCallbackResult AudioEngine::onAudioReady(
    oboe::AudioStream* /*oboeStream*/,
    void* audioData,
    int32_t numFrames
) {
    float* out = static_cast<float*>(audioData);
    int remaining = numFrames;
    int offset = 0;

    while (remaining > 0) {
        const int chunk = std::min(remaining, MAX_AUDIO_FRAMES);

        const int masterIdx = masterDeck_.load(std::memory_order_relaxed);
        double syncRateA = 1.0, syncRateB = 1.0;
        bool useSyncA = false, useSyncB = false;

        if (masterIdx == 0 && deckB_.isSyncEnabled()) {
            computeAutoSyncRate(deckA_, deckB_, syncRateB);
            useSyncB = true;
        } else if (masterIdx == 1 && deckA_.isSyncEnabled()) {
            computeAutoSyncRate(deckB_, deckA_, syncRateA);
            useSyncA = true;
        }

        deckA_.process(mixBufA_.data(), chunk, syncRateA, useSyncA);
        deckB_.process(mixBufB_.data(), chunk, syncRateB, useSyncB);

        const float masterVol = masterVolume_.load(std::memory_order_relaxed);
        const float samplerVol = samplerMasterVolume_.load(std::memory_order_relaxed);
        const int curve = crossfaderCurve_.load(std::memory_order_relaxed);
        float xf = crossfader_.load(std::memory_order_relaxed);
        const bool autoFade = autoFadeActive_.load(std::memory_order_acquire);
        const float fadeTarget = autoFadeTarget_.load(std::memory_order_relaxed);
        const float fadeStep = autoFadeStepPerFrame_.load(std::memory_order_relaxed);

        // Handle pad retriggers
        for (int p = 0; p < NUM_SAMPLER_PADS; ++p) {
            if (samplerPads_[p].retrigger.exchange(false, std::memory_order_acq_rel)) {
                samplerPads_[p].cursor.store(0.0, std::memory_order_relaxed);
                samplerPads_[p].active.store(true, std::memory_order_relaxed);
            }
        }

        float peakL = 0.0f;
        float peakR = 0.0f;
        const bool rec = isRecording_.load(std::memory_order_acquire);
        uint64_t recW = recWriteFrame_.load(std::memory_order_relaxed);
        const uint64_t recR = recReadFrame_.load(std::memory_order_acquire);

        for (int i = 0; i < chunk; ++i) {
            if (autoFade) {
                xf += fadeStep;
                if ((fadeStep >= 0.0f && xf >= fadeTarget) || (fadeStep < 0.0f && xf <= fadeTarget)) {
                    xf = fadeTarget;
                    autoFadeActive_.store(false, std::memory_order_release);
                }
                smoothedCrossfader_ = xf;
            } else {
                // ~2 ms per-sample slew prevents zipper noise on fast slider drags while keeping scratch cuts crisp
                const float diff = xf - smoothedCrossfader_;
                if (std::abs(diff) < 1e-5f) {
                    smoothedCrossfader_ = xf;
                } else {
                    smoothedCrossfader_ += diff * 0.02f;
                }
            }

            float gainA = 1.0f, gainB = 1.0f;
            computeCrossfaderGains(smoothedCrossfader_, curve, gainA, gainB);

            float sumL = mixBufA_[i * 2] * gainA + mixBufB_[i * 2] * gainB;
            float sumR = mixBufA_[i * 2 + 1] * gainA + mixBufB_[i * 2 + 1] * gainB;

            // Mix active sampler pads
            for (int p = 0; p < NUM_SAMPLER_PADS; ++p) {
                auto& pad = samplerPads_[p];
                if (pad.active.load(std::memory_order_relaxed) && pad.totalFrames > 1) {
                    double cur = pad.cursor.load(std::memory_order_relaxed);
                    const int64_t idx = static_cast<int64_t>(cur);
                    if (idx < pad.totalFrames - 1) {
                        const float frac = static_cast<float>(cur - static_cast<double>(idx));
                        const float pv = pad.volume.load(std::memory_order_relaxed) * samplerVol;
                        const float l = (pad.pcm[idx * 2] + (pad.pcm[(idx + 1) * 2] - pad.pcm[idx * 2]) * frac) * pv;
                        const float r = (pad.pcm[idx * 2 + 1] + (pad.pcm[(idx + 1) * 2 + 1] - pad.pcm[idx * 2 + 1]) * frac) * pv;
                        sumL += l;
                        sumR += r;
                        cur += static_cast<double>(pad.pitch.load(std::memory_order_relaxed));
                        pad.cursor.store(cur, std::memory_order_relaxed);
                    } else {
                        pad.active.store(false, std::memory_order_relaxed);
                    }
                }
            }

            sumL = softLimit(sumL * masterVol);
            sumR = softLimit(sumR * masterVol);

            out[(offset + i) * 2] = sumL;
            out[(offset + i) * 2 + 1] = sumR;

            peakL = std::max(peakL, std::abs(sumL));
            peakR = std::max(peakR, std::abs(sumR));

            if (rec && (recW - recR) < static_cast<uint64_t>(REC_RING_FRAMES - 1)) {
                const uint64_t ringIdx = (recW % REC_RING_FRAMES) * 2;
                recRingBuf_[ringIdx] = sumL;
                recRingBuf_[ringIdx + 1] = sumR;
                ++recW;
            }
        }

        if (autoFade) {
            crossfader_.store(xf, std::memory_order_relaxed);
        }
        if (rec) {
            recWriteFrame_.store(recW, std::memory_order_release);
        }

        masterPeakL_.store(peakL, std::memory_order_relaxed);
        masterPeakR_.store(peakR, std::memory_order_relaxed);

        offset += chunk;
        remaining -= chunk;
    }

    return oboe::DataCallbackResult::Continue;
}

} // namespace pulsemix
