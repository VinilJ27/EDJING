package com.example.audio

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

data class DeckBpmAnalysis(
    val deckIndex: Int,
    val detectedBpm: Float = 0f,
    val confidence: Float = 0f,
    val beatPhase: Float = 0f,
    val isLiveStream: Boolean = false,
    val analyzedHops: Int = 0
)

data class DualDeckBpmState(
    val deckA: DeckBpmAnalysis = DeckBpmAnalysis(deckIndex = 0),
    val deckB: DeckBpmAnalysis = DeckBpmAnalysis(deckIndex = 1)
)

/**
 * Bound Android Service driven by the C++ Oboe DSP engine (via NativeAudioEngine)
 * that performs continuous real-time BPM analysis for Deck A and Deck B.
 */
class BpmAnalysisService : Service() {

    inner class LocalBinder : Binder() {
        fun getService(): BpmAnalysisService = this@BpmAnalysisService
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitorJob: Job? = null

    private val _bpmState = MutableStateFlow(DualDeckBpmState())
    val bpmState: StateFlow<DualDeckBpmState> = _bpmState.asStateFlow()

    @Volatile
    internal var analyzerProvider: (Int) -> RealtimeBpmResult = { deckIndex ->
        NativeAudioEngine.analyzeDeckRealtimeBpm(deckIndex)
    }

    @Volatile
    internal var nativeAvailableProvider: () -> Boolean = {
        NativeAudioEngine.isNativeAvailable()
    }

    override fun onBind(intent: Intent?): IBinder {
        startMonitoring()
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startMonitoring()
        return START_NOT_STICKY
    }

    override fun onUnbind(intent: Intent?): Boolean {
        stopMonitoring()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        stopMonitoring()
        serviceScope.cancel()
        super.onDestroy()
    }

    fun startMonitoring(intervalMs: Long = 250L) {
        if (monitorJob?.isActive == true) return
        monitorJob = serviceScope.launch {
            while (isActive) {
                if (nativeAvailableProvider()) {
                    analyzeDeckNow(0)
                    analyzeDeckNow(1)
                }
                delay(intervalMs)
            }
        }
    }

    fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
    }

    fun analyzeDeckNow(deckIndex: Int): DeckBpmAnalysis {
        val raw = analyzerProvider(deckIndex)
        val prev = if (deckIndex == 0) _bpmState.value.deckA else _bpmState.value.deckB
        val stabilized = stabilizeBpm(prev.detectedBpm, raw)

        val updated = DeckBpmAnalysis(
            deckIndex = deckIndex,
            detectedBpm = stabilized,
            confidence = raw.confidence,
            beatPhase = raw.beatPhase,
            isLiveStream = raw.fromLiveStream,
            analyzedHops = raw.analyzedHops
        )
        _bpmState.update { state ->
            if (deckIndex == 0) state.copy(deckA = updated) else state.copy(deckB = updated)
        }
        return updated
    }

    fun resetDeckAnalysis(deckIndex: Int) {
        val blank = DeckBpmAnalysis(deckIndex = deckIndex)
        _bpmState.update { state ->
            if (deckIndex == 0) state.copy(deckA = blank) else state.copy(deckB = blank)
        }
    }

    private fun stabilizeBpm(previousBpm: Float, sample: RealtimeBpmResult): Float {
        if (sample.detectedBpm <= 1f) return previousBpm
        if (previousBpm <= 1f || sample.confidence >= 0.7f) {
            return (sample.detectedBpm * 10f).roundToInt() / 10f
        }
        val diff = abs(sample.detectedBpm - previousBpm)
        // Suppress tiny +/- 0.15 BPM frame jitter unless sustained or pitch shifted
        return if (diff < 0.2f) {
            previousBpm
        } else {
            val blended = previousBpm * 0.35f + sample.detectedBpm * 0.65f
            (blended * 10f).roundToInt() / 10f
        }
    }

    companion object {
        fun bind(context: Context, connection: ServiceConnection): Boolean {
            val intent = Intent(context, BpmAnalysisService::class.java)
            return context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }
}
