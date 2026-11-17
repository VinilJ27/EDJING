package com.example.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.media.MediaPlayer
import android.net.Uri
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioAnalyzer
import com.example.audio.AudioDecoder
import com.example.audio.AudioExporter
import com.example.audio.AudioFocusManager
import com.example.audio.AudioPlaybackService
import com.example.audio.BpmAnalysisService
import com.example.audio.EffectType
import com.example.audio.NativeAudioEngine
import com.example.audio.StarterTrackGenerator
import com.example.data.CuePointEntity
import com.example.data.DjRepository
import com.example.data.PulseMixDatabase
import com.example.data.RecordingEntity
import com.example.data.SamplerPadEntity
import com.example.data.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

@OptIn(ExperimentalCoroutinesApi::class)
class DjViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val db = PulseMixDatabase.getInstance(appContext)
    private val repository = DjRepository(db)

    private val _uiState = MutableStateFlow(DjUiState())
    val uiState: StateFlow<DjUiState> = _uiState.asStateFlow()

    private val selectedPlaylistIdFlow = MutableStateFlow<Long?>(null)
    private var activeRecordingFile: File? = null
    private var recordingStartBpmSummary: String = "128 BPM"
    private var previewPlayer: MediaPlayer? = null
    private var isDucked = false
    @Volatile
    private var isCrossfaderDragging = false
    @Volatile
    private var lastManualCrossfaderNs = 0L

    private var bpmService: BpmAnalysisService? = null
    private var bpmServiceBound = false
    private var bpmObserverJob: kotlinx.coroutines.Job? = null
    private val bpmServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val local = (service as? BpmAnalysisService.LocalBinder)?.getService() ?: return
            bpmService = local
            bpmServiceBound = true
            bpmObserverJob?.cancel()
            bpmObserverJob = viewModelScope.launch {
                local.bpmState.collectLatest { bpmState ->
                    _uiState.update { state ->
                        state.copy(
                            deckA = state.deckA.copy(
                                realtimeBpm = if (bpmState.deckA.detectedBpm > 1f) bpmState.deckA.detectedBpm else state.deckA.realtimeBpm,
                                bpmConfidence = bpmState.deckA.confidence,
                                bpmFromLiveStream = bpmState.deckA.isLiveStream
                            ),
                            deckB = state.deckB.copy(
                                realtimeBpm = if (bpmState.deckB.detectedBpm > 1f) bpmState.deckB.detectedBpm else state.deckB.realtimeBpm,
                                bpmConfidence = bpmState.deckB.confidence,
                                bpmFromLiveStream = bpmState.deckB.isLiveStream
                            )
                        )
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bpmObserverJob?.cancel()
            bpmService = null
            bpmServiceBound = false
        }
    }

    private val focusManager = AudioFocusManager(
        context = appContext,
        onPauseRequested = {
            NativeAudioEngine.setDeckPlaying(0, false)
            NativeAudioEngine.setDeckPlaying(1, false)
            updateForegroundService()
        },
        onDuckChanged = { duck ->
            isDucked = duck
            val mult = if (duck) 0.35f else 1.0f
            NativeAudioEngine.setMasterVolume(_uiState.value.mixer.masterVolume * mult)
        }
    )

    init {
        focusManager.register()
        NativeAudioEngine.startEngine()
        _uiState.update {
            it.copy(
                recording = it.recording.copy(mp3Supported = AudioExporter.isMp3EncoderSupported())
            )
        }

        try {
            BpmAnalysisService.bind(appContext, bpmServiceConnection)
        } catch (_: Exception) {}

        observeDatabase()
        observeAudioRoutes()
        bootstrapStarterContent()
        startTelemetryLoop()
    }

    private fun observeDatabase() {
        viewModelScope.launch {
            repository.allTracks.collectLatest { tracks ->
                _uiState.update { it.copy(library = it.library.copy(tracks = tracks)) }
            }
        }
        viewModelScope.launch {
            repository.recentlyPlayed.collectLatest { recent ->
                _uiState.update { it.copy(library = it.library.copy(recentTracks = recent)) }
            }
        }
        viewModelScope.launch {
            repository.allPlaylists.collectLatest { playlists ->
                _uiState.update { it.copy(library = it.library.copy(playlists = playlists)) }
            }
        }
        viewModelScope.launch {
            selectedPlaylistIdFlow.flatMapLatest { pid ->
                if (pid == null) flowOf(emptyList()) else repository.observePlaylistTracks(pid)
            }.collectLatest { pTracks ->
                _uiState.update { it.copy(library = it.library.copy(selectedPlaylistTracks = pTracks)) }
            }
        }
        viewModelScope.launch {
            repository.allRecordings.collectLatest { recs ->
                _uiState.update { it.copy(recording = it.recording.copy(recordings = recs)) }
            }
        }
        viewModelScope.launch {
            repository.samplerPads.collectLatest { pads ->
                _uiState.update { it.copy(samplerPads = pads) }
                pads.forEach { pad ->
                    NativeAudioEngine.setSamplerPadParams(pad.padIndex, pad.volume, pad.pitch)
                }
            }
        }
        viewModelScope.launch {
            repository.settings.collectLatest { settings ->
                _uiState.update {
                    it.copy(
                        settings = settings,
                        mixer = it.mixer.copy(crossfaderCurve = settings.crossfaderCurve)
                    )
                }
                NativeAudioEngine.setCrossfaderCurve(settings.crossfaderCurve)
            }
        }
    }

    private fun observeAudioRoutes() {
        viewModelScope.launch {
            focusManager.routeState.collectLatest { route ->
                _uiState.update {
                    it.copy(diagnostics = it.diagnostics.copy(routeState = route))
                }
            }
        }
    }

    private fun bootstrapStarterContent() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.ensureDefaultsInitialized()
            if (repository.getTrackCount() == 0) {
                val starters = StarterTrackGenerator.generateStarterTracks(appContext)
                val inserted = starters.map { track ->
                    val id = repository.insertTrack(track)
                    track.copy(id = id)
                }
                if (inserted.isNotEmpty()) {
                    loadTrackToDeckInternal(0, inserted[0])
                }
                if (inserted.size > 1) {
                    loadTrackToDeckInternal(1, inserted[1])
                }
            } else {
                // Auto-load first two tracks if decks are empty
                delay(150)
                val tracks = _uiState.value.library.tracks
                if (_uiState.value.deckA.track == null && tracks.isNotEmpty()) {
                    loadTrackToDeckInternal(0, tracks.first())
                }
                if (_uiState.value.deckB.track == null && tracks.size > 1) {
                    loadTrackToDeckInternal(1, tracks[1])
                }
            }

            // Reload any custom sampler pad URIs
            _uiState.value.samplerPads.forEach { pad ->
                val uriStr = pad.customUri ?: return@forEach
                try {
                    val decoded = AudioDecoder.decodeUri(appContext, Uri.parse(uriStr))
                    NativeAudioEngine.loadSamplerPad(pad.padIndex, decoded.pcm48kStereo, decoded.totalFrames)
                } catch (_: Exception) {}
            }
        }
    }

    private fun startTelemetryLoop() {
        viewModelScope.launch {
            while (isActive) {
                if (!NativeAudioEngine.isNativeAvailable()) {
                    delay(100L)
                    continue
                }
                val t = NativeAudioEngine.pollTelemetry()
                val sr = t.sampleRate.coerceAtLeast(1)
                val allowTelemetryCrossfader = !isCrossfaderDragging &&
                    (System.nanoTime() - lastManualCrossfaderNs > 80_000_000L)
                _uiState.update { state ->
                    val aCursorMs = ((t.deckACursorFrame * 1000.0) / sr).roundToLong()
                    val bCursorMs = ((t.deckBCursorFrame * 1000.0) / sr).roundToLong()

                    val aPrevPlaying = state.deckA.isPlaying
                    val bPrevPlaying = state.deckB.isPlaying

                    val updatedA = state.deckA.copy(
                        cursorMs = aCursorMs,
                        totalFrames = t.deckATotalFrames,
                        isPlaying = t.deckAPlaying,
                        effectiveBpm = if (t.deckAEffectiveBpm > 1f) t.deckAEffectiveBpm else state.deckA.baseBpm,
                        peakLeft = t.deckAPeakLeft,
                        peakRight = t.deckAPeakRight
                    )
                    val updatedB = state.deckB.copy(
                        cursorMs = bCursorMs,
                        totalFrames = t.deckBTotalFrames,
                        isPlaying = t.deckBPlaying,
                        effectiveBpm = if (t.deckBEffectiveBpm > 1f) t.deckBEffectiveBpm else state.deckB.baseBpm,
                        peakLeft = t.deckBPeakLeft,
                        peakRight = t.deckBPeakRight
                    )
                    if (aPrevPlaying != t.deckAPlaying || bPrevPlaying != t.deckBPlaying) {
                        updateForegroundService(updatedA, updatedB)
                    }
                    state.copy(
                        deckA = updatedA,
                        deckB = updatedB,
                        mixer = state.mixer.copy(
                            crossfader = if (allowTelemetryCrossfader) t.crossfader else state.mixer.crossfader,
                            masterPeakLeft = t.masterPeakLeft,
                            masterPeakRight = t.masterPeakRight
                        ),
                        recording = state.recording.copy(
                            isRecording = t.isRecording,
                            recordedMs = t.recordedMs
                        ),
                        diagnostics = state.diagnostics.copy(
                            sampleRate = t.sampleRate,
                            framesPerBurst = t.framesPerBurst,
                            bufferSizeFrames = t.bufferSizeFrames,
                            isLowLatency = t.isLowLatency,
                            audioApi = t.audioApi
                        )
                    )
                }
                delay(16L)
            }
        }
    }

    private fun updateForegroundService(
        a: DeckUiState = _uiState.value.deckA,
        b: DeckUiState = _uiState.value.deckB
    ) {
        val anyPlaying = a.isPlaying || b.isPlaying
        AudioPlaybackService.syncService(
            appContext,
            a.track?.title ?: "Empty",
            b.track?.title ?: "Empty",
            anyPlaying
        )
    }

    fun loadTrackToDeck(deckIndex: Int, track: TrackEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            loadTrackToDeckInternal(deckIndex, track)
        }
    }

    private suspend fun loadTrackToDeckInternal(deckIndex: Int, track: TrackEntity) {
        updateDeck(deckIndex) { it.copy(isLoading = true) }
        try {
            val decoded = AudioDecoder.decodeUri(appContext, Uri.parse(track.uriString))
            val firstBeatFrame = (track.firstBeatMs * AudioDecoder.TARGET_SAMPLE_RATE) / 1000L
            NativeAudioEngine.loadDeckTrack(
                deckIndex = deckIndex,
                pcmData = decoded.pcm48kStereo,
                totalFrames = decoded.totalFrames,
                baseBpm = track.bpm,
                firstBeatFrame = firstBeatFrame
            )
            val cues = repository.getCuesForTrack(track.id)
            val hotCuesMap = cues.filter { it.slotIndex >= 0 }.associateBy { it.slotIndex }
            val mainCue = cues.firstOrNull { it.slotIndex == -1 }?.positionMs ?: track.firstBeatMs
            val waveform = AudioAnalyzer.deserializeWaveform(track.waveformSerialized)

            repository.markTrackPlayed(track.id)

            updateDeck(deckIndex) {
                it.copy(
                    track = track,
                    waveform = waveform,
                    cursorMs = 0L,
                    durationMs = decoded.durationMs,
                    totalFrames = decoded.totalFrames.toLong(),
                    isPlaying = false,
                    isLoading = false,
                    baseBpm = track.bpm,
                    effectiveBpm = track.bpm,
                    realtimeBpm = 0f,
                    bpmConfidence = 0f,
                    bpmFromLiveStream = false,
                    pitchOffset = 0f,
                    syncEnabled = false,
                    loopActive = false,
                    loopBeats = 0,
                    hotCues = hotCuesMap,
                    mainCueMs = mainCue
                )
            }
            NativeAudioEngine.setDeckPitch(deckIndex, 0f)
            NativeAudioEngine.setDeckSync(deckIndex, enabled = false, snapToMaster = false)
            NativeAudioEngine.setDeckKeyLock(deckIndex, _uiState.value.settings.keyLockDefault)
            bpmService?.resetDeckAnalysis(deckIndex)
            if (NativeAudioEngine.isNativeAvailable()) {
                bpmService?.analyzeDeckNow(deckIndex)
            }
        } catch (e: Exception) {
            updateDeck(deckIndex) { it.copy(isLoading = false) }
            showMessage("Failed to load ${track.title}: ${e.localizedMessage ?: "Unsupported format"}")
        }
    }

    fun importAudioUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(library = it.library.copy(isImporting = true, importStatus = "Analyzing ${uris.size} track(s)..."))
            }
            var importedCount = 0
            for ((index, uri) in uris.withIndex()) {
                try {
                    try {
                        appContext.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: Exception) {}

                    val uriStr = uri.toString()
                    if (repository.findTrackByUri(uriStr) != null) continue

                    _uiState.update {
                        it.copy(library = it.library.copy(importStatus = "Decoding & detecting BPM (${index + 1}/${uris.size})..."))
                    }
                    val decoded = AudioDecoder.decodeUri(appContext, uri)
                    val analysis = AudioAnalyzer.analyze(decoded.pcm48kStereo, AudioDecoder.TARGET_SAMPLE_RATE)

                    val track = TrackEntity(
                        uriString = uriStr,
                        title = decoded.title,
                        artist = decoded.artist,
                        album = decoded.album,
                        durationMs = decoded.durationMs,
                        bpm = analysis.bpm,
                        firstBeatMs = analysis.firstBeatMs,
                        musicalKey = analysis.musicalKey,
                        camelotKey = analysis.camelotKey,
                        waveformSerialized = AudioAnalyzer.serializeWaveform(analysis.waveform),
                        albumArtPath = decoded.albumArtPath,
                        isBuiltIn = false
                    )
                    repository.insertTrack(track)
                    importedCount++
                } catch (_: Exception) {
                    // Continue remaining files
                }
            }
            _uiState.update {
                it.copy(
                    library = it.library.copy(
                        isImporting = false,
                        importStatus = if (importedCount > 0) "Imported $importedCount track(s)" else "No new tracks imported"
                    )
                )
            }
        }
    }

    fun togglePlay(deckIndex: Int) {
        val deck = getDeck(deckIndex)
        if (deck.track == null) {
            showMessage("Load a track to Deck ${if (deckIndex == 0) "A" else "B"} first")
            return
        }
        val nextPlaying = !deck.isPlaying
        if (nextPlaying) {
            focusManager.requestFocus()
            NativeAudioEngine.startEngine()
        }
        NativeAudioEngine.setDeckPlaying(deckIndex, nextPlaying)
        updateDeck(deckIndex) { it.copy(isPlaying = nextPlaying) }
        updateForegroundService()
    }

    fun setCuePreview(deckIndex: Int, pressed: Boolean) {
        val deck = getDeck(deckIndex)
        if (deck.track == null) return
        if (pressed) {
            focusManager.requestFocus()
            NativeAudioEngine.startEngine()
            if (!deck.isPlaying) {
                seekDeckMs(deckIndex, deck.mainCueMs)
                NativeAudioEngine.setDeckCuePreview(deckIndex, true)
            } else {
                // If already playing, Cue returns to cue point and pauses
                NativeAudioEngine.setDeckPlaying(deckIndex, false)
                seekDeckMs(deckIndex, deck.mainCueMs)
            }
        } else {
            NativeAudioEngine.setDeckCuePreview(deckIndex, false)
            if (!deck.isPlaying) {
                seekDeckMs(deckIndex, deck.mainCueMs)
            }
        }
    }

    fun setMainCueHere(deckIndex: Int) {
        val deck = getDeck(deckIndex)
        val track = deck.track ?: return
        val cueMs = quantizeMs(deck, deck.cursorMs)
        updateDeck(deckIndex) { it.copy(mainCueMs = cueMs) }
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveCuePoint(track.id, -1, cueMs, "CUE", 0xFFFFFFFF)
        }
        showMessage("Cue point set at ${formatMs(cueMs)}")
    }

    fun triggerHotCue(deckIndex: Int, slotIndex: Int) {
        val deck = getDeck(deckIndex)
        val track = deck.track ?: return
        val existing = deck.hotCues[slotIndex]
        if (existing != null) {
            seekDeckMs(deckIndex, existing.positionMs)
            if (!deck.isPlaying) {
                focusManager.requestFocus()
                NativeAudioEngine.setDeckPlaying(deckIndex, true)
            }
        } else {
            val posMs = quantizeMs(deck, deck.cursorMs)
            val colors = longArrayOf(0xFF00E5FF, 0xFFFFB300, 0xFF22E584, 0xFFFF5252)
            val cue = CuePointEntity(
                trackId = track.id,
                slotIndex = slotIndex,
                positionMs = posMs,
                label = "HOT ${slotIndex + 1}",
                colorHex = colors[slotIndex % colors.size]
            )
            updateDeck(deckIndex) { it.copy(hotCues = it.hotCues + (slotIndex to cue)) }
            viewModelScope.launch(Dispatchers.IO) {
                repository.saveCuePoint(track.id, slotIndex, posMs, cue.label, cue.colorHex)
            }
        }
    }

    fun clearHotCue(deckIndex: Int, slotIndex: Int) {
        val deck = getDeck(deckIndex)
        val track = deck.track ?: return
        updateDeck(deckIndex) { it.copy(hotCues = it.hotCues - slotIndex) }
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteCuePoint(track.id, slotIndex)
        }
    }

    fun seekDeckFraction(deckIndex: Int, fraction: Float) {
        val deck = getDeck(deckIndex)
        if (deck.durationMs <= 0L) return
        val targetMs = (fraction.coerceIn(0f, 1f) * deck.durationMs).toLong()
        seekDeckMs(deckIndex, targetMs)
    }

    fun seekDeckMs(deckIndex: Int, targetMs: Long) {
        val deck = getDeck(deckIndex)
        val clampedMs = targetMs.coerceIn(0L, deck.durationMs.coerceAtLeast(0L))
        val frame = (clampedMs * AudioDecoder.TARGET_SAMPLE_RATE) / 1000L
        NativeAudioEngine.seekDeck(deckIndex, frame)
        updateDeck(deckIndex) { it.copy(cursorMs = clampedMs) }
    }

    fun toggleSync(deckIndex: Int) {
        val deck = getDeck(deckIndex)
        val nextSync = !deck.syncEnabled
        val otherIndex = if (deckIndex == 0) 1 else 0

        if (nextSync) {
            // Ensure the other deck is Master so Auto-Sync aligns this deck to it
            setMasterDeck(otherIndex)
            val master = getDeck(otherIndex)
            val ratio = if (deck.baseBpm > 1f) (master.effectiveBpm / deck.baseBpm) - 1f else 0f
            NativeAudioEngine.setDeckSync(deckIndex, enabled = true, snapToMaster = true)
            updateDeck(deckIndex) {
                it.copy(
                    syncEnabled = true,
                    pitchOffset = ratio.coerceIn(-0.5f, 0.5f)
                )
            }
        } else {
            NativeAudioEngine.setDeckSync(deckIndex, enabled = false, snapToMaster = false)
            NativeAudioEngine.setDeckPitch(deckIndex, deck.pitchOffset)
            updateDeck(deckIndex) { it.copy(syncEnabled = false) }
        }
    }

    fun setMasterDeck(deckIndex: Int) {
        NativeAudioEngine.setMasterDeck(deckIndex)
        _uiState.update { it.copy(mixer = it.mixer.copy(masterDeckIndex = deckIndex)) }
        // Master deck cannot simultaneously be synced to slave
        if (getDeck(deckIndex).syncEnabled) {
            NativeAudioEngine.setDeckSync(deckIndex, enabled = false, snapToMaster = false)
            updateDeck(deckIndex) { it.copy(syncEnabled = false) }
        }
    }

    fun setPitchOffset(deckIndex: Int, offset: Float) {
        val maxRange = _uiState.value.settings.pitchRangePercent / 100f
        val clamped = offset.coerceIn(-maxRange, maxRange)
        NativeAudioEngine.setDeckSync(deckIndex, enabled = false, snapToMaster = false)
        NativeAudioEngine.setDeckPitch(deckIndex, clamped)
        updateDeck(deckIndex) {
            val nextEff = it.baseBpm * (1f + clamped)
            it.copy(
                pitchOffset = clamped,
                syncEnabled = false,
                effectiveBpm = nextEff,
                realtimeBpm = if (it.realtimeBpm > 1f) nextEff else it.realtimeBpm
            )
        }
    }

    fun setManualBpm(deckIndex: Int, targetBpm: Float) {
        val clampedBpm = targetBpm.coerceIn(40f, 240f)
        val deck = getDeck(deckIndex)
        if (deck.track != null && deck.baseBpm > 1f) {
            val desiredOffset = ((clampedBpm / deck.baseBpm) - 1f).coerceIn(-0.50f, 0.50f)
            val neededRange = when {
                abs(desiredOffset) > 0.16f -> 32
                abs(desiredOffset) > 0.08f -> 16
                else -> _uiState.value.settings.pitchRangePercent
            }
            if (neededRange > _uiState.value.settings.pitchRangePercent) {
                updateSettings(pitchRangePercent = neededRange)
            }
            val effectiveMax = (maxOf(neededRange, _uiState.value.settings.pitchRangePercent) / 100f)
            val appliedOffset = desiredOffset.coerceIn(-effectiveMax, effectiveMax)
            val appliedBpm = deck.baseBpm * (1f + appliedOffset)
            NativeAudioEngine.setDeckSync(deckIndex, enabled = false, snapToMaster = false)
            NativeAudioEngine.setDeckPitch(deckIndex, appliedOffset)
            updateDeck(deckIndex) {
                it.copy(
                    pitchOffset = appliedOffset,
                    syncEnabled = false,
                    effectiveBpm = appliedBpm,
                    realtimeBpm = appliedBpm
                )
            }
        } else {
            NativeAudioEngine.setDeckBaseBpm(deckIndex, clampedBpm)
            updateDeck(deckIndex) {
                it.copy(
                    baseBpm = clampedBpm,
                    effectiveBpm = clampedBpm,
                    realtimeBpm = clampedBpm
                )
            }
        }
    }

    fun resetPitch(deckIndex: Int) {
        setPitchOffset(deckIndex, 0f)
    }

    fun cyclePitchRange() {
        val current = _uiState.value.settings.pitchRangePercent
        val next = when (current) {
            8 -> 16
            16 -> 32
            else -> 8
        }
        updateSettings(pitchRangePercent = next)
    }

    fun toggleKeyLock(deckIndex: Int) {
        val next = !getDeck(deckIndex).keyLock
        NativeAudioEngine.setDeckKeyLock(deckIndex, next)
        updateDeck(deckIndex) { it.copy(keyLock = next) }
    }

    fun toggleSlipMode(deckIndex: Int) {
        val next = !getDeck(deckIndex).slipMode
        NativeAudioEngine.setDeckSlipMode(deckIndex, next)
        updateDeck(deckIndex) { it.copy(slipMode = next) }
    }

    fun toggleQuantize(deckIndex: Int) {
        val next = !getDeck(deckIndex).quantize
        updateDeck(deckIndex) { it.copy(quantize = next) }
    }

    fun onJogScratchStart(deckIndex: Int) {
        NativeAudioEngine.setDeckPitchBend(deckIndex, 0f)
        NativeAudioEngine.setDeckScratch(deckIndex, scratching = true, velocity = 0f)
        updateDeck(deckIndex) { it.copy(isScratching = true, pitchBend = 0f) }
    }

    fun onJogScratchDelta(deckIndex: Int, angleDeltaDeg: Float, deltaTimeMs: Long = 16L) {
        // 360 degrees = 1.8 seconds of vinyl rotation at 33.3 RPM (1.333s at 45 RPM)
        val secondsPerRev = if (_uiState.value.settings.vinylRpm == 45) 1.333f else 1.8f
        val dtSec = deltaTimeMs.coerceIn(2L, 64L) / 1000f
        val revPerSec = (angleDeltaDeg / 360f) / dtSec
        val velocity = (revPerSec * secondsPerRev).coerceIn(-6.0f, 6.0f)
        NativeAudioEngine.setDeckScratch(deckIndex, scratching = true, velocity = velocity)
        if (angleDeltaDeg != 0f) {
            updateDeck(deckIndex) {
                it.copy(scratchAngleDeg = (it.scratchAngleDeg + angleDeltaDeg) % 360f)
            }
        }
    }

    fun onJogScratchEnd(deckIndex: Int) {
        NativeAudioEngine.setDeckScratch(deckIndex, scratching = false, velocity = 0f)
        updateDeck(deckIndex) { it.copy(isScratching = false) }
    }

    fun onPitchBend(deckIndex: Int, bend: Float) {
        val clamped = bend.coerceIn(-0.08f, 0.08f)
        NativeAudioEngine.setDeckPitchBend(deckIndex, clamped)
        updateDeck(deckIndex) { it.copy(pitchBend = clamped) }
    }

    fun toggleBeatLoop(deckIndex: Int, beats: Int) {
        val deck = getDeck(deckIndex)
        if (deck.track == null || deck.baseBpm <= 1f) return
        if (deck.loopActive && deck.loopBeats == beats) {
            exitLoop(deckIndex)
            return
        }
        val startMs = quantizeMs(deck, deck.cursorMs)
        val beatMs = (60_000.0 / deck.baseBpm).toDouble()
        val endMs = (startMs + beatMs * beats).roundToLong().coerceAtMost(deck.durationMs)
        val startFrame = (startMs * AudioDecoder.TARGET_SAMPLE_RATE) / 1000L
        val endFrame = (endMs * AudioDecoder.TARGET_SAMPLE_RATE) / 1000L
        if (endFrame > startFrame) {
            NativeAudioEngine.setDeckLoop(deckIndex, true, startFrame, endFrame)
            updateDeck(deckIndex) {
                it.copy(loopActive = true, loopBeats = beats, loopStartMs = startMs, loopEndMs = endMs)
            }
        }
    }

    fun setManualLoopIn(deckIndex: Int) {
        val deck = getDeck(deckIndex)
        val inMs = quantizeMs(deck, deck.cursorMs)
        updateDeck(deckIndex) { it.copy(manualLoopInMs = inMs) }
    }

    fun setManualLoopOut(deckIndex: Int) {
        val deck = getDeck(deckIndex)
        val inMs = deck.manualLoopInMs ?: return
        val outMs = quantizeMs(deck, deck.cursorMs)
        if (outMs <= inMs + 80L) return
        val startFrame = (inMs * AudioDecoder.TARGET_SAMPLE_RATE) / 1000L
        val endFrame = (outMs * AudioDecoder.TARGET_SAMPLE_RATE) / 1000L
        NativeAudioEngine.setDeckLoop(deckIndex, true, startFrame, endFrame)
        updateDeck(deckIndex) {
            it.copy(loopActive = true, loopBeats = 0, loopStartMs = inMs, loopEndMs = outMs)
        }
    }

    fun exitLoop(deckIndex: Int) {
        NativeAudioEngine.setDeckLoop(deckIndex, false, 0L, 0L)
        updateDeck(deckIndex) { it.copy(loopActive = false, loopBeats = 0) }
    }

    fun beatJump(deckIndex: Int, beats: Int) {
        val deck = getDeck(deckIndex)
        if (deck.baseBpm <= 1f || deck.durationMs <= 0L) return
        val beatMs = 60_000.0 / deck.baseBpm
        val targetMs = (deck.cursorMs + (beatMs * beats).roundToLong()).coerceIn(0L, deck.durationMs)
        seekDeckMs(deckIndex, targetMs)
    }

    fun setDeckVolume(deckIndex: Int, volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        NativeAudioEngine.setDeckVolume(deckIndex, v)
        updateDeck(deckIndex) { it.copy(volume = v) }
    }

    fun setDeckTrim(deckIndex: Int, trim: Float) {
        val v = trim.coerceIn(0f, 2f)
        NativeAudioEngine.setDeckTrim(deckIndex, v)
        updateDeck(deckIndex) { it.copy(trimGain = v) }
    }

    fun setDeckEq(deckIndex: Int, lowDb: Float, midDb: Float, highDb: Float) {
        NativeAudioEngine.setDeckEq(deckIndex, lowDb, midDb, highDb)
        updateDeck(deckIndex) { it.copy(eqLowDb = lowDb, eqMidDb = midDb, eqHighDb = highDb) }
    }

    fun setDeckFilter(deckIndex: Int, filterVal: Float) {
        val v = filterVal.coerceIn(-1f, 1f)
        NativeAudioEngine.setDeckFilter(deckIndex, v)
        updateDeck(deckIndex) { it.copy(filterVal = v) }
    }

    fun setDeckEffect(deckIndex: Int, effect: EffectType, wetDry: Float, param: Float) {
        NativeAudioEngine.setDeckEffect(deckIndex, effect, wetDry, param)
        updateDeck(deckIndex) { it.copy(activeEffect = effect, fxWetDry = wetDry, fxParam = param) }
    }

    fun setDeckPanelMode(deckIndex: Int, mode: DeckPanelMode) {
        updateDeck(deckIndex) { it.copy(panelMode = mode) }
    }

    fun setCrossfaderDragging(dragging: Boolean) {
        isCrossfaderDragging = dragging
        if (dragging) {
            lastManualCrossfaderNs = System.nanoTime()
        }
    }

    fun setCrossfader(value: Float) {
        val v = value.coerceIn(-1f, 1f)
        lastManualCrossfaderNs = System.nanoTime()
        NativeAudioEngine.setCrossfader(v)
        _uiState.update { it.copy(mixer = it.mixer.copy(crossfader = v)) }
    }

    fun resetCrossfader() {
        setCrossfader(0f)
    }

    fun cycleCrossfaderCurve() {
        val nextCurve = (_uiState.value.mixer.crossfaderCurve + 1) % 3
        updateSettings(crossfaderCurve = nextCurve)
    }

    fun triggerAutoCrossfade() {
        isCrossfaderDragging = false
        lastManualCrossfaderNs = 0L
        val currentXf = _uiState.value.mixer.crossfader
        val targetDeck = if (currentXf < 0f) 1 else 0
        val duration = _uiState.value.settings.autoCrossfadeSeconds
        if (!getDeck(targetDeck).isPlaying && getDeck(targetDeck).track != null) {
            togglePlay(targetDeck)
        }
        NativeAudioEngine.triggerAutoCrossfade(targetDeck, duration)
    }

    fun setMasterVolume(vol: Float) {
        val v = vol.coerceIn(0f, 1.2f)
        val mult = if (isDucked) 0.35f else 1f
        NativeAudioEngine.setMasterVolume(v * mult)
        _uiState.update { it.copy(mixer = it.mixer.copy(masterVolume = v)) }
    }

    fun setSamplerMasterVolume(vol: Float) {
        val v = vol.coerceIn(0f, 1.2f)
        NativeAudioEngine.setSamplerMasterVolume(v)
        _uiState.update { it.copy(mixer = it.mixer.copy(samplerVolume = v)) }
    }

    fun triggerSamplerPad(padIndex: Int) {
        focusManager.requestFocus()
        NativeAudioEngine.startEngine()
        NativeAudioEngine.triggerSamplerPad(padIndex)
    }

    fun updateSamplerPadParams(pad: SamplerPadEntity) {
        NativeAudioEngine.setSamplerPadParams(pad.padIndex, pad.volume, pad.pitch)
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateSamplerPad(pad)
        }
    }

    fun loadCustomSamplerPad(padIndex: Int, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                try {
                    appContext.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {}
                val decoded = AudioDecoder.decodeUri(appContext, uri)
                // Trim custom one-shot samples to max 10 seconds to keep sampler crisp
                val maxFrames = minOf(decoded.totalFrames, AudioDecoder.TARGET_SAMPLE_RATE * 10)
                val trimmed = if (decoded.pcm48kStereo.size > maxFrames * 2) {
                    decoded.pcm48kStereo.copyOf(maxFrames * 2)
                } else {
                    decoded.pcm48kStereo
                }
                NativeAudioEngine.loadSamplerPad(padIndex, trimmed, maxFrames)
                val existing = _uiState.value.samplerPads.firstOrNull { it.padIndex == padIndex }
                val updated = (existing ?: SamplerPadEntity(padIndex, "PAD ${padIndex + 1}", null, 0.9f, 1f, 0xFF00E5FF))
                    .copy(
                        label = decoded.title.take(10).uppercase(Locale.US),
                        customUri = uri.toString()
                    )
                repository.updateSamplerPad(updated)
                showMessage("Loaded '${decoded.title}' to Pad ${padIndex + 1}")
            } catch (_: Exception) {
                showMessage("Could not load sample audio file")
            }
        }
    }

    fun toggleRecording() {
        if (_uiState.value.recording.isRecording) {
            stopRecordingAndSave()
        } else {
            val dir = File(appContext.filesDir, "recordings").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val wavFile = File(dir, "PulseMix_$stamp.wav")
            activeRecordingFile = wavFile
            val bpmA = _uiState.value.deckA.effectiveBpm.toInt()
            val bpmB = _uiState.value.deckB.effectiveBpm.toInt()
            recordingStartBpmSummary = "$bpmA / $bpmB BPM"
            NativeAudioEngine.startEngine()
            val started = NativeAudioEngine.startRecording(wavFile.absolutePath)
            if (started) {
                showMessage("Recording master mix...")
            } else {
                showMessage("Unable to start recording")
            }
        }
    }

    private fun stopRecordingAndSave() {
        val durMs = _uiState.value.recording.recordedMs
        NativeAudioEngine.stopRecording()
        val file = activeRecordingFile
        activeRecordingFile = null
        if (file != null && file.exists() && file.length() > 44L) {
            viewModelScope.launch(Dispatchers.IO) {
                val count = _uiState.value.recording.recordings.size + 1
                val entity = RecordingEntity(
                    title = "Live Set #$count",
                    filePath = file.absolutePath,
                    format = "WAV",
                    durationMs = durMs.coerceAtLeast(500L),
                    fileSizeBytes = file.length(),
                    bpmSummary = recordingStartBpmSummary
                )
                repository.insertRecording(entity)
                showMessage("Saved recording '${entity.title}' (${formatMs(entity.durationMs)})")
            }
        }
    }

    fun exportRecordingToSafWav(recording: RecordingEntity, targetUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(recording = it.recording.copy(isExporting = true)) }
            val ok = AudioExporter.exportWavToUri(appContext, File(recording.filePath), targetUri)
            _uiState.update {
                it.copy(
                    recording = it.recording.copy(
                        isExporting = false,
                        statusMessage = if (ok) "Exported WAV successfully" else "WAV export failed"
                    )
                )
            }
        }
    }

    fun transcodeAndSaveM4a(recording: RecordingEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(recording = it.recording.copy(isExporting = true)) }
            val src = File(recording.filePath)
            val m4aFile = File(src.parentFile, src.nameWithoutExtension + ".m4a")
            val ok = AudioExporter.transcodeWavToM4a(src, m4aFile)
            if (ok) {
                repository.insertRecording(
                    RecordingEntity(
                        title = "${recording.title} (AAC)",
                        filePath = m4aFile.absolutePath,
                        format = "M4A",
                        durationMs = recording.durationMs,
                        fileSizeBytes = m4aFile.length(),
                        bpmSummary = recording.bpmSummary
                    )
                )
            }
            _uiState.update {
                it.copy(
                    recording = it.recording.copy(
                        isExporting = false,
                        statusMessage = if (ok) "Exported compressed AAC/M4A copy" else "M4A encoding failed"
                    )
                )
            }
        }
    }

    fun shareRecording(recording: RecordingEntity) {
        val file = File(recording.filePath)
        if (!file.exists()) return
        val mime = if (recording.format == "M4A") "audio/mp4" else "audio/wav"
        AudioExporter.shareRecording(appContext, file, mime)
    }

    fun deleteRecording(recording: RecordingEntity) {
        if (_uiState.value.recording.previewingId == recording.id) {
            stopPreviewRecording()
        }
        viewModelScope.launch(Dispatchers.IO) {
            try { File(recording.filePath).delete() } catch (_: Exception) {}
            repository.deleteRecording(recording)
        }
    }

    fun togglePreviewRecording(recording: RecordingEntity) {
        if (_uiState.value.recording.previewingId == recording.id) {
            stopPreviewRecording()
            return
        }
        stopPreviewRecording()
        try {
            val mp = MediaPlayer().apply {
                setDataSource(recording.filePath)
                setOnCompletionListener {
                    _uiState.update { it.copy(recording = it.recording.copy(previewingId = null)) }
                }
                prepare()
                start()
            }
            previewPlayer = mp
            _uiState.update { it.copy(recording = it.recording.copy(previewingId = recording.id)) }
        } catch (_: Exception) {
            showMessage("Unable to play recording")
        }
    }

    private fun stopPreviewRecording() {
        try {
            previewPlayer?.stop()
            previewPlayer?.release()
        } catch (_: Exception) {}
        previewPlayer = null
        _uiState.update { it.copy(recording = it.recording.copy(previewingId = null)) }
    }

    // Library filtering & playlists
    fun setSearchQuery(q: String) {
        _uiState.update { it.copy(library = it.library.copy(searchQuery = q)) }
    }

    fun setSortMode(mode: LibrarySortMode) {
        _uiState.update { it.copy(library = it.library.copy(sortMode = mode)) }
    }

    fun setFilterTab(tab: LibraryFilterTab) {
        _uiState.update { it.copy(library = it.library.copy(filterTab = tab)) }
    }

    fun selectPlaylist(playlistId: Long?) {
        selectedPlaylistIdFlow.value = playlistId
        _uiState.update { it.copy(library = it.library.copy(selectedPlaylistId = playlistId)) }
    }

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val id = repository.createPlaylist(name.trim())
            selectPlaylist(id)
        }
    }

    fun addTrackToPlaylist(playlistId: Long, trackId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.addTrackToPlaylist(playlistId, trackId)
            showMessage("Added to playlist")
        }
    }

    fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.removeTrackFromPlaylist(playlistId, trackId)
        }
    }

    fun deleteTrack(track: TrackEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteTrack(track.id)
        }
    }

    // Drag & Drop helper
    fun setActiveDragTrack(track: TrackEntity?) {
        _uiState.update { it.copy(activeDragTrack = track) }
    }

    // Settings & Tutorials
    fun updateSettings(
        crossfaderCurve: Int? = null,
        autoCrossfadeSeconds: Float? = null,
        pitchRangePercent: Int? = null,
        keyLockDefault: Boolean? = null,
        quantizeDefault: Boolean? = null,
        vinylRpm: Int? = null
    ) {
        if (crossfaderCurve != null) {
            val c = crossfaderCurve.coerceIn(0, 2)
            NativeAudioEngine.setCrossfaderCurve(c)
            _uiState.update {
                it.copy(
                    mixer = it.mixer.copy(crossfaderCurve = c),
                    settings = it.settings.copy(crossfaderCurve = c)
                )
            }
        }
        if (pitchRangePercent != null) {
            val maxRange = pitchRangePercent / 100f
            for (idx in 0..1) {
                val curPitch = getDeck(idx).pitchOffset
                val clamped = curPitch.coerceIn(-maxRange, maxRange)
                if (clamped != curPitch) {
                    NativeAudioEngine.setDeckPitch(idx, clamped)
                    updateDeck(idx) {
                        it.copy(
                            pitchOffset = clamped,
                            effectiveBpm = it.baseBpm * (1f + clamped)
                        )
                    }
                }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateSettings { cur ->
                cur.copy(
                    crossfaderCurve = crossfaderCurve ?: cur.crossfaderCurve,
                    autoCrossfadeSeconds = autoCrossfadeSeconds ?: cur.autoCrossfadeSeconds,
                    pitchRangePercent = pitchRangePercent ?: cur.pitchRangePercent,
                    keyLockDefault = keyLockDefault ?: cur.keyLockDefault,
                    quantizeDefault = quantizeDefault ?: cur.quantizeDefault,
                    vinylRpm = vinylRpm ?: cur.vinylRpm
                )
            }
        }
    }

    fun toggleTutorialComplete(lessonId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateSettings { cur ->
                val set = cur.completedTutorialIds.split(',').filter { it.isNotBlank() }.toMutableSet()
                if (!set.add(lessonId)) set.remove(lessonId)
                cur.copy(completedTutorialIds = set.joinToString(","))
            }
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    private fun showMessage(msg: String) {
        _uiState.update { it.copy(snackbarMessage = msg) }
    }

    private fun getDeck(deckIndex: Int): DeckUiState =
        if (deckIndex == 0) _uiState.value.deckA else _uiState.value.deckB

    private fun updateDeck(deckIndex: Int, transform: (DeckUiState) -> DeckUiState) {
        _uiState.update { state ->
            if (deckIndex == 0) state.copy(deckA = transform(state.deckA))
            else state.copy(deckB = transform(state.deckB))
        }
    }

    private fun quantizeMs(deck: DeckUiState, rawMs: Long): Long {
        if (!deck.quantize || deck.baseBpm <= 1f) return rawMs
        val track = deck.track ?: return rawMs
        val beatMs = 60_000.0 / deck.baseBpm
        val rel = (rawMs - track.firstBeatMs).toDouble()
        val nearestBeat = floor(rel / beatMs + 0.5)
        return (track.firstBeatMs + (nearestBeat * beatMs).roundToLong()).coerceIn(0L, deck.durationMs)
    }

    override fun onCleared() {
        super.onCleared()
        bpmObserverJob?.cancel()
        if (bpmServiceBound) {
            try {
                appContext.unbindService(bpmServiceConnection)
            } catch (_: Exception) {}
            bpmServiceBound = false
        }
        stopPreviewRecording()
        if (_uiState.value.recording.isRecording) {
            NativeAudioEngine.stopRecording()
        }
        focusManager.unregister()
        AudioPlaybackService.syncService(appContext, "", "", false)
        NativeAudioEngine.stopEngine()
    }
}

fun formatMs(ms: Long): String {
    val totalSec = (ms.coerceAtLeast(0L) / 1000L).toInt()
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format(Locale.US, "%02d:%02d", min, sec)
}

fun formatPitch(pitchOffset: Float): String {
    val pct = pitchOffset * 100f
    return if (abs(pct) < 0.05f) "0.0%" else String.format(Locale.US, "%+.1f%%", pct)
}
