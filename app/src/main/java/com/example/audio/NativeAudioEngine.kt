package com.example.audio

enum class EffectType(val id: Int, val displayName: String) {
    NONE(0, "OFF"),
    ECHO(1, "ECHO"),
    REVERB(2, "REVERB"),
    FLANGER(3, "FLANGER"),
    PHASER(4, "PHASER"),
    DELAY(5, "DELAY"),
    FILTER(6, "FILTER"),
    BRAKE(7, "BRAKE"),
    REVERSE(8, "REVERSE"),
    BITCRUSHER(9, "CRUSH")
}

data class EngineTelemetry(
    val deckACursorFrame: Double = 0.0,
    val deckATotalFrames: Long = 0L,
    val deckAPlaying: Boolean = false,
    val deckAEffectiveBpm: Float = 128f,
    val deckAPeakLeft: Float = 0f,
    val deckAPeakRight: Float = 0f,

    val deckBCursorFrame: Double = 0.0,
    val deckBTotalFrames: Long = 0L,
    val deckBPlaying: Boolean = false,
    val deckBEffectiveBpm: Float = 128f,
    val deckBPeakLeft: Float = 0f,
    val deckBPeakRight: Float = 0f,

    val crossfader: Float = 0f,
    val masterPeakLeft: Float = 0f,
    val masterPeakRight: Float = 0f,
    val isRecording: Boolean = false,
    val recordedMs: Long = 0L,

    val sampleRate: Int = 48000,
    val framesPerBurst: Int = 192,
    val bufferSizeFrames: Int = 384,
    val isLowLatency: Boolean = true,
    val audioApi: String = "AAudio"
)

data class RealtimeBpmResult(
    val detectedBpm: Float = 0f,
    val confidence: Float = 0f,
    val beatPhase: Float = 0f,
    val fromLiveStream: Boolean = false,
    val analyzedHops: Int = 0
)

object NativeAudioEngine {

    private var libraryLoaded = false

    init {
        libraryLoaded = try {
            System.loadLibrary("pulsemix_audio")
            true
        } catch (_: UnsatisfiedLinkError) {
            false
        }
    }

    fun isNativeAvailable(): Boolean = libraryLoaded

    fun startEngine(): Boolean = if (libraryLoaded) nativeStartEngine() else false
    fun stopEngine() { if (libraryLoaded) nativeStopEngine() }

    fun loadDeckTrack(
        deckIndex: Int,
        pcmData: FloatArray,
        totalFrames: Int,
        baseBpm: Float,
        firstBeatFrame: Long
    ) {
        if (libraryLoaded) nativeLoadDeckTrack(deckIndex, pcmData, totalFrames, baseBpm, firstBeatFrame)
    }

    fun unloadDeckTrack(deckIndex: Int) {
        if (libraryLoaded) nativeUnloadDeckTrack(deckIndex)
    }

    fun setDeckPlaying(deckIndex: Int, playing: Boolean) {
        if (libraryLoaded) nativeSetDeckPlaying(deckIndex, playing)
    }

    fun setDeckCuePreview(deckIndex: Int, previewing: Boolean) {
        if (libraryLoaded) nativeSetDeckCuePreview(deckIndex, previewing)
    }

    fun seekDeck(deckIndex: Int, frame: Long) {
        if (libraryLoaded) nativeSeekDeck(deckIndex, frame)
    }

    fun setDeckPitch(deckIndex: Int, pitchOffset: Float) {
        if (libraryLoaded) nativeSetDeckPitch(deckIndex, pitchOffset)
    }

    fun setDeckKeyLock(deckIndex: Int, enabled: Boolean) {
        if (libraryLoaded) nativeSetDeckKeyLock(deckIndex, enabled)
    }

    fun setDeckSlipMode(deckIndex: Int, enabled: Boolean) {
        if (libraryLoaded) nativeSetDeckSlipMode(deckIndex, enabled)
    }

    fun setDeckSync(deckIndex: Int, enabled: Boolean, snapToMaster: Boolean) {
        if (libraryLoaded) nativeSetDeckSync(deckIndex, enabled, snapToMaster)
    }

    fun setDeckBaseBpm(deckIndex: Int, bpm: Float) {
        if (libraryLoaded) nativeSetDeckBaseBpm(deckIndex, bpm)
    }

    fun analyzeDeckRealtimeBpm(deckIndex: Int): RealtimeBpmResult {
        if (!libraryLoaded) return RealtimeBpmResult()
        val raw = nativeAnalyzeDeckRealtimeBpm(deckIndex)
        if (raw.size < 5) return RealtimeBpmResult()
        return RealtimeBpmResult(
            detectedBpm = raw[0],
            confidence = raw[1],
            beatPhase = raw[2],
            fromLiveStream = raw[3] > 0.5f,
            analyzedHops = raw[4].toInt()
        )
    }

    fun setDeckPitchBend(deckIndex: Int, bend: Float) {
        if (libraryLoaded) nativeSetDeckPitchBend(deckIndex, bend)
    }

    fun setDeckScratch(deckIndex: Int, scratching: Boolean, velocity: Float) {
        if (libraryLoaded) nativeSetDeckScratch(deckIndex, scratching, velocity)
    }

    fun setDeckLoop(deckIndex: Int, active: Boolean, startFrame: Long, endFrame: Long) {
        if (libraryLoaded) nativeSetDeckLoop(deckIndex, active, startFrame, endFrame)
    }

    fun setDeckVolume(deckIndex: Int, volume: Float) {
        if (libraryLoaded) nativeSetDeckVolume(deckIndex, volume)
    }

    fun setDeckTrim(deckIndex: Int, trim: Float) {
        if (libraryLoaded) nativeSetDeckTrim(deckIndex, trim)
    }

    fun setDeckEq(deckIndex: Int, lowDb: Float, midDb: Float, highDb: Float) {
        if (libraryLoaded) nativeSetDeckEq(deckIndex, lowDb, midDb, highDb)
    }

    fun setDeckFilter(deckIndex: Int, filterVal: Float) {
        if (libraryLoaded) nativeSetDeckFilter(deckIndex, filterVal)
    }

    fun setDeckEffect(deckIndex: Int, effectType: EffectType, wetDry: Float, param: Float) {
        if (libraryLoaded) nativeSetDeckEffect(deckIndex, effectType.id, wetDry, param)
    }

    fun setCrossfader(value: Float) {
        if (libraryLoaded) nativeSetCrossfader(value)
    }

    fun setCrossfaderCurve(curve: Int) {
        if (libraryLoaded) nativeSetCrossfaderCurve(curve)
    }

    fun triggerAutoCrossfade(targetDeck: Int, durationSeconds: Float) {
        if (libraryLoaded) nativeTriggerAutoCrossfade(targetDeck, durationSeconds)
    }

    fun setMasterVolume(volume: Float) {
        if (libraryLoaded) nativeSetMasterVolume(volume)
    }

    fun setSamplerMasterVolume(volume: Float) {
        if (libraryLoaded) nativeSetSamplerMasterVolume(volume)
    }

    fun setMasterDeck(deckIndex: Int) {
        if (libraryLoaded) nativeSetMasterDeck(deckIndex)
    }

    fun loadSamplerPad(padIndex: Int, pcmData: FloatArray, numFrames: Int) {
        if (libraryLoaded) nativeLoadSamplerPad(padIndex, pcmData, numFrames)
    }

    fun triggerSamplerPad(padIndex: Int) {
        if (libraryLoaded) nativeTriggerSamplerPad(padIndex)
    }

    fun setSamplerPadParams(padIndex: Int, volume: Float, pitch: Float) {
        if (libraryLoaded) nativeSetSamplerPadParams(padIndex, volume, pitch)
    }

    fun startRecording(wavPath: String): Boolean {
        return if (libraryLoaded) nativeStartRecording(wavPath) else false
    }

    fun stopRecording() {
        if (libraryLoaded) nativeStopRecording()
    }

    fun pollTelemetry(): EngineTelemetry {
        if (!libraryLoaded) return EngineTelemetry()
        val raw = nativePollTelemetry()
        if (raw.size < 22) return EngineTelemetry()
        return EngineTelemetry(
            deckACursorFrame = raw[0].toDouble(),
            deckATotalFrames = raw[1].toLong(),
            deckAPlaying = raw[2] > 0.5f,
            deckAEffectiveBpm = raw[3],
            deckAPeakLeft = raw[4],
            deckAPeakRight = raw[5],

            deckBCursorFrame = raw[6].toDouble(),
            deckBTotalFrames = raw[7].toLong(),
            deckBPlaying = raw[8] > 0.5f,
            deckBEffectiveBpm = raw[9],
            deckBPeakLeft = raw[10],
            deckBPeakRight = raw[11],

            crossfader = raw[12],
            masterPeakLeft = raw[13],
            masterPeakRight = raw[14],
            isRecording = raw[15] > 0.5f,
            recordedMs = raw[16].toLong(),

            sampleRate = raw[17].toInt(),
            framesPerBurst = raw[18].toInt(),
            bufferSizeFrames = raw[19].toInt(),
            isLowLatency = raw[20] > 0.5f,
            audioApi = if (raw[21] < 1.5f) "AAudio" else "OpenSL ES"
        )
    }

    private external fun nativeStartEngine(): Boolean
    private external fun nativeStopEngine()
    private external fun nativeLoadDeckTrack(
        deckIndex: Int,
        pcmData: FloatArray,
        totalFrames: Int,
        baseBpm: Float,
        firstBeatFrame: Long
    )
    private external fun nativeUnloadDeckTrack(deckIndex: Int)
    private external fun nativeSetDeckPlaying(deckIndex: Int, playing: Boolean)
    private external fun nativeSetDeckCuePreview(deckIndex: Int, previewing: Boolean)
    private external fun nativeSeekDeck(deckIndex: Int, frame: Long)
    private external fun nativeSetDeckPitch(deckIndex: Int, pitchOffset: Float)
    private external fun nativeSetDeckKeyLock(deckIndex: Int, enabled: Boolean)
    private external fun nativeSetDeckSlipMode(deckIndex: Int, enabled: Boolean)
    private external fun nativeSetDeckSync(deckIndex: Int, enabled: Boolean, snapToMaster: Boolean)
    private external fun nativeSetDeckPitchBend(deckIndex: Int, bend: Float)
    private external fun nativeSetDeckScratch(deckIndex: Int, scratching: Boolean, velocity: Float)
    private external fun nativeSetDeckLoop(deckIndex: Int, active: Boolean, startFrame: Long, endFrame: Long)
    private external fun nativeSetDeckVolume(deckIndex: Int, volume: Float)
    private external fun nativeSetDeckTrim(deckIndex: Int, trim: Float)
    private external fun nativeSetDeckEq(deckIndex: Int, lowDb: Float, midDb: Float, highDb: Float)
    private external fun nativeSetDeckFilter(deckIndex: Int, filterVal: Float)
    private external fun nativeSetDeckEffect(deckIndex: Int, effectType: Int, wetDry: Float, param: Float)
    private external fun nativeSetCrossfader(value: Float)
    private external fun nativeSetCrossfaderCurve(curve: Int)
    private external fun nativeTriggerAutoCrossfade(targetDeck: Int, durationSeconds: Float)
    private external fun nativeSetMasterVolume(volume: Float)
    private external fun nativeSetSamplerMasterVolume(volume: Float)
    private external fun nativeSetMasterDeck(deckIndex: Int)
    private external fun nativeLoadSamplerPad(padIndex: Int, pcmData: FloatArray, numFrames: Int)
    private external fun nativeTriggerSamplerPad(padIndex: Int)
    private external fun nativeSetSamplerPadParams(padIndex: Int, volume: Float, pitch: Float)
    private external fun nativeStartRecording(wavPath: String): Boolean
    private external fun nativeStopRecording()
    private external fun nativePollTelemetry(): FloatArray
    private external fun nativeAnalyzeDeckRealtimeBpm(deckIndex: Int): FloatArray
    private external fun nativeSetDeckBaseBpm(deckIndex: Int, bpm: Float)
}
