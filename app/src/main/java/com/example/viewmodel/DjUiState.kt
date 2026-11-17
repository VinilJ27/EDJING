package com.example.viewmodel

import com.example.audio.AudioRouteState
import com.example.audio.EffectType
import com.example.audio.WaveformBucket
import com.example.data.CuePointEntity
import com.example.data.PlaylistEntity
import com.example.data.RecordingEntity
import com.example.data.SamplerPadEntity
import com.example.data.TrackEntity
import com.example.data.UserSettingsEntity

enum class DeckPanelMode(val label: String) {
    VINYL("VINYL"),
    EQ("EQ"),
    FX("FX"),
    LOOP("LOOP"),
    CUES("CUES"),
    SAMPLER("SAMPLER")
}

enum class LibrarySortMode(val label: String) {
    RECENT("Recent"),
    TITLE("Title"),
    BPM("BPM"),
    KEY("Key")
}

enum class LibraryFilterTab(val label: String) {
    ALL("All Tracks"),
    RECENT("Recently Played"),
    HARMONIC("Harmonic Match"),
    PLAYLISTS("Playlists")
}

data class DeckUiState(
    val deckIndex: Int,
    val track: TrackEntity? = null,
    val waveform: List<WaveformBucket> = emptyList(),
    val cursorMs: Long = 0L,
    val durationMs: Long = 0L,
    val totalFrames: Long = 0L,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val baseBpm: Float = 128.0f,
    val effectiveBpm: Float = 128.0f,
    val realtimeBpm: Float = 0.0f,
    val bpmConfidence: Float = 0.0f,
    val bpmFromLiveStream: Boolean = false,
    val pitchOffset: Float = 0.0f,
    val keyLock: Boolean = true,
    val syncEnabled: Boolean = false,
    val slipMode: Boolean = false,
    val quantize: Boolean = true,
    val loopActive: Boolean = false,
    val loopBeats: Int = 0,
    val loopStartMs: Long = 0L,
    val loopEndMs: Long = 0L,
    val manualLoopInMs: Long? = null,
    val volume: Float = 0.85f,
    val trimGain: Float = 1.0f,
    val eqLowDb: Float = 0.0f,
    val eqMidDb: Float = 0.0f,
    val eqHighDb: Float = 0.0f,
    val filterVal: Float = 0.0f,
    val activeEffect: EffectType = EffectType.NONE,
    val fxWetDry: Float = 0.55f,
    val fxParam: Float = 0.50f,
    val hotCues: Map<Int, CuePointEntity> = emptyMap(),
    val mainCueMs: Long = 0L,
    val peakLeft: Float = 0f,
    val peakRight: Float = 0f,
    val panelMode: DeckPanelMode = DeckPanelMode.VINYL,
    val isScratching: Boolean = false,
    val scratchAngleDeg: Float = 0f,
    val pitchBend: Float = 0f
)

data class MixerUiState(
    val crossfader: Float = 0.0f,
    val crossfaderCurve: Int = 1,
    val masterVolume: Float = 0.95f,
    val samplerVolume: Float = 0.90f,
    val masterDeckIndex: Int = 0,
    val masterPeakLeft: Float = 0f,
    val masterPeakRight: Float = 0f
)

data class AudioDiagnosticsUiState(
    val sampleRate: Int = 48000,
    val framesPerBurst: Int = 192,
    val bufferSizeFrames: Int = 384,
    val isLowLatency: Boolean = true,
    val audioApi: String = "AAudio",
    val routeState: AudioRouteState = AudioRouteState()
)

data class RecordingUiState(
    val isRecording: Boolean = false,
    val recordedMs: Long = 0L,
    val recordings: List<RecordingEntity> = emptyList(),
    val isExporting: Boolean = false,
    val mp3Supported: Boolean = false,
    val previewingId: Long? = null,
    val statusMessage: String? = null
)

data class LibraryUiState(
    val tracks: List<TrackEntity> = emptyList(),
    val recentTracks: List<TrackEntity> = emptyList(),
    val playlists: List<PlaylistEntity> = emptyList(),
    val selectedPlaylistId: Long? = null,
    val selectedPlaylistTracks: List<TrackEntity> = emptyList(),
    val searchQuery: String = "",
    val sortMode: LibrarySortMode = LibrarySortMode.RECENT,
    val filterTab: LibraryFilterTab = LibraryFilterTab.ALL,
    val isImporting: Boolean = false,
    val importStatus: String? = null
)

data class TutorialLesson(
    val id: String,
    val title: String,
    val level: String,
    val durationLabel: String,
    val description: String,
    val targetPanelMode: DeckPanelMode,
    val steps: List<String>,
    val accentColorHex: Long
)

data class DjUiState(
    val deckA: DeckUiState = DeckUiState(deckIndex = 0),
    val deckB: DeckUiState = DeckUiState(deckIndex = 1),
    val mixer: MixerUiState = MixerUiState(),
    val diagnostics: AudioDiagnosticsUiState = AudioDiagnosticsUiState(),
    val recording: RecordingUiState = RecordingUiState(),
    val library: LibraryUiState = LibraryUiState(),
    val samplerPads: List<SamplerPadEntity> = emptyList(),
    val settings: UserSettingsEntity = UserSettingsEntity(),
    val tutorials: List<TutorialLesson> = defaultTutorials(),
    val activeDragTrack: TrackEntity? = null,
    val snackbarMessage: String? = null
)

fun defaultTutorials(): List<TutorialLesson> = listOf(
    TutorialLesson(
        id = "first_mix",
        title = "Make your first DJ Mix",
        level = "Basic",
        durationLabel = "3 min",
        description = "Blend Deck A into Deck B using the crossfader and volume sliders.",
        targetPanelMode = DeckPanelMode.VINYL,
        steps = listOf(
            "Press Play on Deck A and watch the cyan waveform scroll.",
            "Tap SYNC on Deck B so its BPM and beat grid lock to Deck A.",
            "Press Play on Deck B, then smoothly slide the Crossfader from left to right."
        ),
        accentColorHex = 0xFF00E5FF
    ),
    TutorialLesson(
        id = "create_loops",
        title = "How to create loops?",
        level = "Basic",
        durationLabel = "2 min",
        description = "Capture seamless 1 to 32 beat loops quantized to the beat grid.",
        targetPanelMode = DeckPanelMode.LOOP,
        steps = listOf(
            "Open the LOOP panel on Deck A or Deck B.",
            "Tap 4 or 8 beats while a track is playing to lock a quantized beat loop.",
            "Use Beat Jump (-1 / +1) or Manual IN/OUT for custom phrasing."
        ),
        accentColorHex = 0xFFFFB300
    ),
    TutorialLesson(
        id = "use_sampler",
        title = "How to use sampler?",
        level = "Basic",
        durationLabel = "2 min",
        description = "Layer drum hits, risers, and custom one-shots live over your mix.",
        targetPanelMode = DeckPanelMode.SAMPLER,
        steps = listOf(
            "Open the SAMPLER tab on the console or switch to the FX & Sampler screen.",
            "Tap Kick, Snare, Clap, or Hi-Hat pads in time with the master BPM.",
            "Long-press or tap the folder icon in FX & Sampler to load your own audio sample."
        ),
        accentColorHex = 0xFF22E584
    ),
    TutorialLesson(
        id = "eq_filters",
        title = "EQ & Filter Transitions",
        level = "Intermediate",
        durationLabel = "4 min",
        description = "Swap basslines cleanly without muddy low-end clashes.",
        targetPanelMode = DeckPanelMode.EQ,
        steps = listOf(
            "Open the EQ tab on both decks.",
            "Lower the LOW slider on the incoming deck before bringing up its volume.",
            "Swap LOWs on a downbeat and sweep the FILTER knob for dramatic builds."
        ),
        accentColorHex = 0xFFA855F7
    ),
    TutorialLesson(
        id = "scratch_cues",
        title = "Scratching & Hot Cues",
        level = "Pro",
        durationLabel = "4 min",
        description = "Touch-drag the vinyl platter and trigger instant vocal/beat cues.",
        targetPanelMode = DeckPanelMode.CUES,
        steps = listOf(
            "Open CUES and tap Pad 1–4 at key drop moments to save Hot Cues.",
            "Switch back to VINYL and drag your finger back and forth across the platter.",
            "Enable SLIP mode so the track continues silently underneath your scratch."
        ),
        accentColorHex = 0xFFFF5252
    )
)
