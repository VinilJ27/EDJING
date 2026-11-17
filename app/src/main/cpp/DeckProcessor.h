#pragma once

#include "DspEffects.h"
#include "RealTimeBpmDetector.h"
#include "TimeStretcher.h"
#include <array>
#include <atomic>
#include <cstdint>
#include <mutex>
#include <vector>

namespace pulsemix {

struct TrackBuffer {
    std::vector<float> pcm; // stereo interleaved at 48kHz
    int64_t totalFrames = 0;
    float baseBpm = 128.0f;
    int64_t firstBeatFrame = 0;
};

class DeckProcessor {
public:
    static constexpr int MAX_CHUNK_FRAMES = 1024;

    DeckProcessor();
    ~DeckProcessor();

    void setSampleRate(float sampleRate);

    // Control-thread safe track replacement (zero allocation in audio thread)
    void loadTrack(TrackBuffer* newBuffer);
    void unloadTrack();
    void reclaimRetired();

    // Transport & timeline
    void setPlaying(bool playing);
    void setCuePreview(bool previewing);
    void seekToFrame(int64_t frame);
    void setSlipMode(bool enabled);

    // Tempo & Sync
    void setPitchOffset(float offset); // e.g., -0.16f to +0.16f
    void setKeyLock(bool enabled);
    void setSyncEnabled(bool enabled);
    void setPitchBend(float bend); // momentary nudge -0.08 to +0.08

    // Scratch / Jog Wheel
    void setScratchState(bool scratching, float velocity);

    // Loops
    void setLoop(bool active, int64_t startFrame, int64_t endFrame);

    // Mixer & EQ & Filter
    void setChannelVolume(float vol);
    void setTrimGain(float gain);
    void setEq(float lowDb, float midDb, float highDb);
    void setFilter(float filterVal); // -1.0 LPF -> 0.0 OFF -> +1.0 HPF

    // Effects
    void setEffect(EffectType type, float wetDry, float param);

    // Audio thread processing
    void process(
        float* outStereo,
        int numFrames,
        double externalSyncRate,
        bool useExternalSyncRate
    );

    // Snap cursor phase when Sync is engaged
    void snapToPhase(double targetBeatPhase);

    // Getters for Sync & UI telemetry (lock-free atomic reads)
    bool isPlaying() const { return isPlaying_.load(std::memory_order_relaxed); }
    bool isCuePreview() const { return cuePreview_.load(std::memory_order_relaxed); }
    bool isSyncEnabled() const { return syncEnabled_.load(std::memory_order_relaxed); }
    bool isLoopActive() const { return loopActive_.load(std::memory_order_relaxed); }
    bool isKeyLock() const { return keyLock_.load(std::memory_order_relaxed); }
    bool isScratching() const { return isScratching_.load(std::memory_order_relaxed); }

    double getCursorFrame() const { return cursorAtomic_.load(std::memory_order_relaxed); }
    int64_t getTotalFrames() const { return totalFramesAtomic_.load(std::memory_order_relaxed); }
    float getBaseBpm() const { return baseBpmAtomic_.load(std::memory_order_relaxed); }
    void setBaseBpm(float bpm);
    int64_t getFirstBeatFrame() const { return firstBeatAtomic_.load(std::memory_order_relaxed); }
    float getPitchOffset() const { return pitchOffset_.load(std::memory_order_relaxed); }
    float getEffectiveBpm() const;
    RealtimeBpmResult analyzeRealtimeBpm() const;
    float getPeakLeft() const { return peakLeft_.load(std::memory_order_relaxed); }
    float getPeakRight() const { return peakRight_.load(std::memory_order_relaxed); }

private:
    float sampleRate_ = 48000.0f;

    // Wait-free track buffer swapping
    TrackBuffer* currentTrack_ = nullptr;
    std::atomic<TrackBuffer*> pendingTrack_{nullptr};
    std::atomic<TrackBuffer*> retiredTrack_{nullptr};
    std::atomic<bool> unloadRequested_{false};

    // Transport & timeline state
    std::atomic<bool> isPlaying_{false};
    std::atomic<bool> cuePreview_{false};
    std::atomic<bool> slipMode_{false};
    std::atomic<int64_t> seekTarget_{-1};

    double cursor_ = 0.0;
    double slipCursor_ = 0.0;
    bool wasScratching_ = false;
    bool wasSlipTrigger_ = false;

    // Tempo & Sync
    std::atomic<float> pitchOffset_{0.0f};
    std::atomic<float> pitchBend_{0.0f};
    std::atomic<bool> keyLock_{true};
    std::atomic<bool> syncEnabled_{false};

    // Scratching
    std::atomic<bool> isScratching_{false};
    std::atomic<float> scratchVelocity_{0.0f};
    float smoothedScratchVel_ = 0.0f;

    // Loop
    std::atomic<bool> loopActive_{false};
    std::atomic<int64_t> loopStartFrame_{0};
    std::atomic<int64_t> loopEndFrame_{0};

    // Mixer & EQ
    std::atomic<float> channelVolume_{0.85f};
    std::atomic<float> trimGain_{1.0f};
    std::atomic<float> eqLowDb_{0.0f};
    std::atomic<float> eqMidDb_{0.0f};
    std::atomic<float> eqHighDb_{0.0f};
    std::atomic<float> filterVal_{0.0f};

    float lastLowDb_ = 0.0f;
    float lastMidDb_ = 0.0f;
    float lastHighDb_ = 0.0f;
    float lastFilterVal_ = 0.0f;

    StereoBiquad eqLowFilter_;
    StereoBiquad eqMidFilter_;
    StereoBiquad eqHighFilter_;
    StereoBiquad comboFilter_;
    bool comboFilterActive_ = false;

    // FX
    std::atomic<int> fxType_{0};
    std::atomic<float> fxWetDry_{0.5f};
    std::atomic<float> fxParam_{0.5f};
    DeckEffectsChain fxChain_;

    // Time stretcher
    TimeStretcher timeStretcher_;

    // Telemetry atomics
    std::atomic<double> cursorAtomic_{0.0};
    std::atomic<int64_t> totalFramesAtomic_{0};
    std::atomic<float> baseBpmAtomic_{128.0f};
    std::atomic<int64_t> firstBeatAtomic_{0};
    std::atomic<double> activeRateAtomic_{1.0};
    std::atomic<float> peakLeft_{0.0f};
    std::atomic<float> peakRight_{0.0f};

    // Real-time BPM detector (lock-free audio push + control/service-thread analysis)
    RealTimeBpmDetector bpmDetector_;
    mutable std::mutex analysisMutex_;
    std::vector<float> trackOnsetEnvelope_;

    // Pre-allocated chunk buffer (zero allocations in audio callback)
    std::array<float, MAX_CHUNK_FRAMES * 2> chunkBuf_{};

    void adoptPendingTrack();
    void updateEqAndFilterCoeffs();
};

} // namespace pulsemix
