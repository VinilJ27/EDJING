#include "AudioEngine.h"
#include <jni.h>
#include <memory>
#include <string>

using namespace pulsemix;

namespace {
std::unique_ptr<AudioEngine> gEngine;

AudioEngine* getEngine() {
    if (!gEngine) {
        gEngine = std::make_unique<AudioEngine>();
    }
    return gEngine.get();
}
} // namespace

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_example_audio_NativeAudioEngine_nativeStartEngine(JNIEnv* /*env*/, jobject /*thiz*/) {
    return getEngine()->start() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioEngine_nativeStopEngine(JNIEnv* /*env*/, jobject /*thiz*/) {
    if (gEngine) {
        gEngine->stop();
    }
}

JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioEngine_nativeLoadDeckTrack(
    JNIEnv* env,
    jobject /*thiz*/,
    jint deckIndex,
    jfloatArray pcmData,
    jint totalFrames,
    jfloat baseBpm,
    jlong firstBeatFrame
) {
    auto* engine = getEngine();
    jsize len = env->GetArrayLength(pcmData);
    auto* buf = new TrackBuffer();
    buf->pcm.resize(static_cast<size_t>(len));
    env->GetFloatArrayRegion(pcmData, 0, len, buf->pcm.data());
    buf->totalFrames = static_cast<int64_t>(totalFrames);
    buf->baseBpm = baseBpm;
    buf->firstBeatFrame = static_cast<int64_t>(firstBeatFrame);
    engine->deck(deckIndex).loadTrack(buf);
}

JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioEngine_nativeUnloadDeckTrack(
    JNIEnv* /*env*/,
    jobject /*thiz*/,
    jint deckIndex
) {
    getEngine()->deck(deckIndex).unloadTrack();
}

JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioEngine_nativeSetDeckPlaying(
    JNIEnv* /*env*/,
    jobject /*thiz*/,
    jint deckIndex,
    jboolean playing
) {
    getEngine()->deck(deckIndex).setPlaying(playing == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioEngine_nativeSetDeckCuePreview(
    JNIEnv* /*env*/,
    jobject /*thiz*/,
    jint deckIndex,
    jboolean previewing
) {
    getEngine()->deck(deckIndex).setCuePreview(previewing == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioEngine_nativeSeekDeck(
    JNIEnv* /*env*/,
    jobject /*thiz*/,
    jint deckIndex,
    jlong frame
) {
    getEngine()->deck(deckIndex).seekToFrame(static_cast<int64_t>(frame));
}

JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioEngine_nativeSetDeckPitch(
    JNIEnv* /*env*/,
    jobject /*thiz*/,
    jint deckIndex,
    jfloat pitchOffset
) {
    getEngine()->deck(deckIndex).setPitchOffset(pitchOffset);
}

JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioEngine_nativeSetDeckKeyLock(
    JNIEnv* /*env*/,
    jobject /*thiz*/,
    jint deckIndex,
    jboolean enabled
) {
    getEngine()->deck(deckIndex).setKeyLock(enabled == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioEngine_nativeSetDeckSlipMode(
    JNIEnv* /*env*/,
    jobject /*thiz*/,
    jint deckIndex,
    jboolean enabled
) {
    getEngine()->deck(deckIndex).setSlipMode(enabled == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioEngine_nativeSetDeckSync(
    JNIEnv* /*env*/,
    jobject /*thiz*/,
    jint deckIndex,
    jboolean enabled,
    jboolean snapToMaster
) {
    auto* engine = getEngine();
    auto& targetDeck = engine->deck(deckIndex);
    if (enabled == JNI_TRUE && snapToMaster == JNI_TRUE) {
        const int masterIdx = engine->getMasterDeck();
        if (masterIdx != deckIndex) {
            const auto& master = engine->deck(masterIdx);
            const float mBpm = master.getBaseBpm();
            if (mBpm > 1.0f) {
                const double sr = static_cast<double>(engine->getStreamSampleRate());
                const double fpb = (sr * 60.0) / static_cast<double>(mBpm);
                const double beatPos = (master.getCursorFrame() - static_cast<double>(master.getFirstBeatFrame())) / fpb;
                double phase = beatPos - std::floor(beatPos);
                if (phase < 0.0) phase += 1.0;
                targetDeck.snapToPhase(phase);
            }
        }
    }
    targetDeck.setSyncEnabled(enabled == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioEngine_nativeSetDeckPitchBend(
    JNIEnv* /*env*/,
    jobject /*thiz*/,
    jint deckIndex,
    jfloat bend
) {
    getEngine()->deck(deckIndex).setPitchBend(bend);
}

JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioEngine_nativeSetDeckScratch(
    JNIEnv* /*env*/,
    jobject /*thiz*/,
    jint deckIndex,
    jboolean scratching,
    jfloat velocity
) {
    getEngine()->deck(deckIndex).setScratchState(scratching == JNI_TRUE, velocity);
}

JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioEngine_nativeSetDeckLoop(
    JNIEnv* /*env*/,
    jobject /*thiz*/,
    jint deckIndex,
    jboolean active,
    jlong startFrame,
    jlong endFrame
) {
    getEngine()->deck(deckIndex).setLoop(active == JNI_TRUE, static_cast<int64_t>(startFrame), static_cast<int64_t>(endFrame));
}

JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioEngine_nativeSetDeckVolume(
    JNIEnv* /*env*/,
    jobject /*thiz*/,
    jint deckIndex,
    jfloat volume
) {
    getEngine()->deck(deckIndex).setChannelVolume(volume);
}

JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioEngine_nativeSetDeckTrim(
    JNIEnv* /*env*/,
    jobject /*thiz*/,
    jint deckIndex,
    jfloat trim
) {
    getEngine()->deck(deckIndex).setTrimGain(trim);
}

JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioEngine_nativeSetDeckEq(
    JNIEnv* /*env*/,
    jobject /*thiz*/,
    jint deckIndex,
    jfloat lowDb,
    jfloat midDb,
    jfloat highDb
) {
    getEngine()->deck(deckIndex).setEq(lowDb, midDb, highDb);
}

JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioEngine_nativeSetDeckFilter(
    JNIEnv* /*env*/,
    jobject /*thiz*/,
    jint deckIndex,
    jfloat filterVal
) {
    getEngine()->deck(deckIndex).setFilter(filterVal);
}

JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioEngine_nativeSetDeckEffect(
    JNIEnv* /*env*/,
    jobject /*thiz*/,
    jint deckIndex,
    jint effectType,
    jfloat wetDry,
    jfloat param
) {
    getEngine()->deck(deckIndex).setEffect(static_cast<EffectType>(effectType), wetDry, param);
}

JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioEngine_nativeSetCrossfader(
    JNIEnv* /*env*/,
    jobject /*thiz*/,
    jfloat value
) {
    getEngine()->setCrossfader(value);
}

JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioEngine_nativeSetCrossfaderCurve(
    JNIEnv* /*env*/,
    jobject /*thiz*/,
    jint curve
) {
    getEngine()->setCrossfaderCurve(curve);
}

JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioEngine_nativeTriggerAutoCrossfade(
    JNIEnv* /*env*/,
    jobject /*thiz*/,
    jint targetDeck,
    jfloat durationSeconds
) {
    getEngine()->triggerAutoCrossfade(targetDeck, durationSeconds);
}

JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioEngine_nativeSetMasterVolume(
    JNIEnv* /*env*/,
    jobject /*thiz*/,
    jfloat volume
) {
    getEngine()->setMasterVolume(volume);
}

JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioEngine_nativeSetSamplerMasterVolume(
    JNIEnv* /*env*/,
    jobject /*thiz*/,
    jfloat volume
) {
    getEngine()->setSamplerMasterVolume(volume);
}

JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioEngine_nativeSetMasterDeck(
    JNIEnv* /*env*/,
    jobject /*thiz*/,
    jint deckIndex
) {
    getEngine()->setMasterDeck(deckIndex);
}

JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioEngine_nativeLoadSamplerPad(
    JNIEnv* env,
    jobject /*thiz*/,
    jint padIndex,
    jfloatArray pcmData,
    jint numFrames
) {
    jsize len = env->GetArrayLength(pcmData);
    std::vector<float> buf(static_cast<size_t>(len));
    env->GetFloatArrayRegion(pcmData, 0, len, buf.data());
    getEngine()->loadSamplerPad(padIndex, buf.data(), static_cast<int64_t>(numFrames));
}

JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioEngine_nativeTriggerSamplerPad(
    JNIEnv* /*env*/,
    jobject /*thiz*/,
    jint padIndex
) {
    getEngine()->triggerSamplerPad(padIndex);
}

JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioEngine_nativeSetSamplerPadParams(
    JNIEnv* /*env*/,
    jobject /*thiz*/,
    jint padIndex,
    jfloat volume,
    jfloat pitch
) {
    auto* engine = getEngine();
    engine->setSamplerPadVolume(padIndex, volume);
    engine->setSamplerPadPitch(padIndex, pitch);
}

JNIEXPORT jboolean JNICALL
Java_com_example_audio_NativeAudioEngine_nativeStartRecording(
    JNIEnv* env,
    jobject /*thiz*/,
    jstring wavPath
) {
    const char* pathStr = env->GetStringUTFChars(wavPath, nullptr);
    std::string path(pathStr ? pathStr : "");
    env->ReleaseStringUTFChars(wavPath, pathStr);
    return getEngine()->startRecording(path) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioEngine_nativeStopRecording(JNIEnv* /*env*/, jobject /*thiz*/) {
    getEngine()->stopRecording();
}

JNIEXPORT jfloatArray JNICALL
Java_com_example_audio_NativeAudioEngine_nativePollTelemetry(JNIEnv* env, jobject /*thiz*/) {
    auto* engine = getEngine();
    engine->deck(0).reclaimRetired();
    engine->deck(1).reclaimRetired();

    const auto& dA = engine->deck(0);
    const auto& dB = engine->deck(1);

    float data[22] = {
        static_cast<float>(dA.getCursorFrame()),
        static_cast<float>(dA.getTotalFrames()),
        dA.isPlaying() ? 1.0f : 0.0f,
        dA.getEffectiveBpm(),
        dA.getPeakLeft(),
        dA.getPeakRight(),

        static_cast<float>(dB.getCursorFrame()),
        static_cast<float>(dB.getTotalFrames()),
        dB.isPlaying() ? 1.0f : 0.0f,
        dB.getEffectiveBpm(),
        dB.getPeakLeft(),
        dB.getPeakRight(),

        engine->getCrossfader(),
        engine->getMasterPeakLeft(),
        engine->getMasterPeakRight(),
        engine->isRecording() ? 1.0f : 0.0f,
        static_cast<float>(engine->getRecordedMs()),

        static_cast<float>(engine->getStreamSampleRate()),
        static_cast<float>(engine->getStreamFramesPerBurst()),
        static_cast<float>(engine->getStreamBufferSizeFrames()),
        engine->isStreamLowLatency() ? 1.0f : 0.0f,
        static_cast<float>(engine->getStreamAudioApi())
    };

    jfloatArray arr = env->NewFloatArray(22);
    env->SetFloatArrayRegion(arr, 0, 22, data);
    return arr;
}

JNIEXPORT jfloatArray JNICALL
Java_com_example_audio_NativeAudioEngine_nativeAnalyzeDeckRealtimeBpm(
    JNIEnv* env,
    jobject /*thiz*/,
    jint deckIndex
) {
    auto res = getEngine()->deck(deckIndex).analyzeRealtimeBpm();
    float out[5] = {
        res.detectedBpm,
        res.confidence,
        res.beatPhase,
        res.fromLiveStream ? 1.0f : 0.0f,
        static_cast<float>(res.analyzedHops)
    };
    jfloatArray arr = env->NewFloatArray(5);
    env->SetFloatArrayRegion(arr, 0, 5, out);
    return arr;
}

JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioEngine_nativeSetDeckBaseBpm(
    JNIEnv* /*env*/,
    jobject /*thiz*/,
    jint deckIndex,
    jfloat bpm
) {
    getEngine()->deck(deckIndex).setBaseBpm(bpm);
}

} // extern "C"
