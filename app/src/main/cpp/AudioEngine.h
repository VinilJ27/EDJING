#pragma once

#include "DeckProcessor.h"
#include <oboe/Oboe.h>
#include <array>
#include <atomic>
#include <cstdint>
#include <cstdio>
#include <memory>
#include <string>
#include <thread>
#include <vector>

namespace pulsemix {

struct SamplerPad {
    std::atomic<bool> active{false};
    std::atomic<double> cursor{0.0};
    std::atomic<float> volume{0.9f};
    std::atomic<float> pitch{1.0f};
    std::vector<float> pcm; // stereo 48kHz
    int64_t totalFrames = 0;
    std::atomic<bool> retrigger{false};
};

class AudioEngine : public oboe::AudioStreamDataCallback, public oboe::AudioStreamErrorCallback {
public:
    static constexpr int SAMPLE_RATE = 48000;
    static constexpr int NUM_SAMPLER_PADS = 8;
    static constexpr int REC_RING_FRAMES = 48000 * 4; // 4-second lock-free SPSC ring buffer

    AudioEngine();
    ~AudioEngine() override;

    bool start();
    void stop();

    DeckProcessor& deck(int index) { return index == 0 ? deckA_ : deckB_; }

    // Mixer controls
    void setCrossfader(float value); // -1.0 (Deck A) .. +1.0 (Deck B)
    void setCrossfaderCurve(int curve); // 0=Smooth, 1=ConstantPower, 2=SharpCut
    void triggerAutoCrossfade(int targetDeck, float durationSeconds);
    void setMasterVolume(float vol);
    void setSamplerMasterVolume(float vol);
    void setMasterDeck(int index); // 0=A, 1=B

    // Sampler
    void initDefaultSamplerPads();
    void loadSamplerPad(int padIndex, const float* interleavedPcm, int64_t numFrames);
    void triggerSamplerPad(int padIndex);
    void setSamplerPadVolume(int padIndex, float volume);
    void setSamplerPadPitch(int padIndex, float pitch);

    // Master WAV Recording (lock-free SPSC ring -> background writer thread)
    bool startRecording(const std::string& wavFilePath);
    void stopRecording();
    bool isRecording() const { return isRecording_.load(std::memory_order_relaxed); }
    int64_t getRecordedMs() const;

    // Telemetry
    float getCrossfader() const { return crossfader_.load(std::memory_order_relaxed); }
    int getMasterDeck() const { return masterDeck_.load(std::memory_order_relaxed); }
    float getMasterPeakLeft() const { return masterPeakL_.load(std::memory_order_relaxed); }
    float getMasterPeakRight() const { return masterPeakR_.load(std::memory_order_relaxed); }

    // Stream diagnostics
    int getStreamSampleRate() const { return streamSampleRate_.load(std::memory_order_relaxed); }
    int getStreamFramesPerBurst() const { return streamFramesPerBurst_.load(std::memory_order_relaxed); }
    int getStreamBufferSizeFrames() const { return streamBufferSizeFrames_.load(std::memory_order_relaxed); }
    bool isStreamLowLatency() const { return streamLowLatency_.load(std::memory_order_relaxed); }
    int getStreamAudioApi() const { return streamAudioApi_.load(std::memory_order_relaxed); } // 1=AAudio, 2=OpenSLES

    // Oboe callbacks
    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream* oboeStream,
        void* audioData,
        int32_t numFrames
    ) override;

    void onErrorAfterClose(oboe::AudioStream* oboeStream, oboe::Result error) override;

private:
    std::shared_ptr<oboe::AudioStream> stream_;
    DeckProcessor deckA_;
    DeckProcessor deckB_;

    std::atomic<float> crossfader_{0.0f};
    std::atomic<int> crossfaderCurve_{1};
    std::atomic<float> masterVolume_{0.95f};
    std::atomic<float> samplerMasterVolume_{0.90f};
    std::atomic<int> masterDeck_{0};

    // Auto-crossfade & smoothing state (audio thread)
    std::atomic<bool> autoFadeActive_{false};
    std::atomic<float> autoFadeTarget_{-1.0f};
    std::atomic<float> autoFadeStepPerFrame_{0.0f};
    float smoothedCrossfader_{0.0f};

    // Sampler pads
    std::array<SamplerPad, NUM_SAMPLER_PADS> samplerPads_;

    // Pre-allocated mix buffers (zero allocations in audio thread)
    static constexpr int MAX_AUDIO_FRAMES = 4096;
    std::array<float, MAX_AUDIO_FRAMES * 2> mixBufA_{};
    std::array<float, MAX_AUDIO_FRAMES * 2> mixBufB_{};

    // Recording SPSC ring buffer & background writer thread
    std::vector<float> recRingBuf_;
    std::atomic<uint64_t> recWriteFrame_{0};
    std::atomic<uint64_t> recReadFrame_{0};
    std::atomic<bool> isRecording_{false};
    std::atomic<bool> recThreadRunning_{false};
    std::atomic<int64_t> recordedFrames_{0};
    std::thread recWriterThread_;
    FILE* recFile_ = nullptr;

    // Metering & diagnostics
    std::atomic<float> masterPeakL_{0.0f};
    std::atomic<float> masterPeakR_{0.0f};
    std::atomic<int> streamSampleRate_{SAMPLE_RATE};
    std::atomic<int> streamFramesPerBurst_{192};
    std::atomic<int> streamBufferSizeFrames_{384};
    std::atomic<bool> streamLowLatency_{true};
    std::atomic<int> streamAudioApi_{1};

    void computeCrossfaderGains(float xf, int curve, float& gainA, float& gainB) const;
    void computeAutoSyncRate(const DeckProcessor& master, const DeckProcessor& slave, double& outRate) const;
    void writeWavHeader(FILE* f, uint32_t dataBytes);
    void recordingLoop();
};

} // namespace pulsemix
