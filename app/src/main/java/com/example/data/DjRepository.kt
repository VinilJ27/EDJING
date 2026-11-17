package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DjRepository(private val db: PulseMixDatabase) {

    val allTracks: Flow<List<TrackEntity>> = db.trackDao().observeAllTracks()
    val recentlyPlayed: Flow<List<TrackEntity>> = db.trackDao().observeRecentlyPlayed()
    val allPlaylists: Flow<List<PlaylistEntity>> = db.playlistDao().observeAllPlaylists()
    val allRecordings: Flow<List<RecordingEntity>> = db.recordingDao().observeAllRecordings()
    val samplerPads: Flow<List<SamplerPadEntity>> = db.samplerPadDao().observePads()
    val settings: Flow<UserSettingsEntity> = db.settingsDao().observeSettings()
        .map { it ?: UserSettingsEntity() }

    fun observePlaylistTracks(playlistId: Long): Flow<List<TrackEntity>> =
        db.playlistDao().observeTracksForPlaylist(playlistId)

    suspend fun getTrackCount(): Int = db.trackDao().getTrackCount()

    suspend fun findTrackByUri(uri: String): TrackEntity? = db.trackDao().findByUri(uri)

    suspend fun insertTrack(track: TrackEntity): Long = db.trackDao().insertTrack(track)

    suspend fun markTrackPlayed(trackId: Long) = db.trackDao().markPlayed(trackId)

    suspend fun deleteTrack(trackId: Long) = db.trackDao().deleteTrackById(trackId)

    suspend fun createPlaylist(name: String): Long =
        db.playlistDao().insertPlaylist(PlaylistEntity(name = name))

    suspend fun deletePlaylist(playlistId: Long) = db.playlistDao().deletePlaylist(playlistId)

    suspend fun addTrackToPlaylist(playlistId: Long, trackId: Long, position: Int = 0) =
        db.playlistDao().addTrackToPlaylist(PlaylistTrackCrossRef(playlistId, trackId, position))

    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) =
        db.playlistDao().removeTrackFromPlaylist(playlistId, trackId)

    suspend fun getCuesForTrack(trackId: Long): List<CuePointEntity> =
        db.cuePointDao().getCuesForTrack(trackId)

    suspend fun saveCuePoint(
        trackId: Long,
        slotIndex: Int,
        positionMs: Long,
        label: String,
        colorHex: Long
    ) {
        db.cuePointDao().deleteCueBySlot(trackId, slotIndex)
        db.cuePointDao().insertCue(
            CuePointEntity(
                trackId = trackId,
                slotIndex = slotIndex,
                positionMs = positionMs,
                label = label,
                colorHex = colorHex
            )
        )
    }

    suspend fun deleteCuePoint(trackId: Long, slotIndex: Int) =
        db.cuePointDao().deleteCueBySlot(trackId, slotIndex)

    suspend fun insertRecording(recording: RecordingEntity): Long =
        db.recordingDao().insertRecording(recording)

    suspend fun deleteRecording(recording: RecordingEntity) =
        db.recordingDao().deleteRecording(recording)

    suspend fun ensureDefaultsInitialized() {
        if (db.settingsDao().getSettings() == null) {
            db.settingsDao().saveSettings(UserSettingsEntity())
        }
        if (db.samplerPadDao().getPadCount() == 0) {
            val defaults = listOf(
                SamplerPadEntity(0, "KICK", null, 0.95f, 1.0f, 0xFF00E5FF),
                SamplerPadEntity(1, "SNARE", null, 0.90f, 1.0f, 0xFFFF5252),
                SamplerPadEntity(2, "CLAP", null, 0.90f, 1.0f, 0xFF3B82F6),
                SamplerPadEntity(3, "HI-HAT", null, 0.85f, 1.0f, 0xFFA855F7),
                SamplerPadEntity(4, "RIM", null, 0.85f, 1.0f, 0xFF22E584),
                SamplerPadEntity(5, "CRASH", null, 0.80f, 1.0f, 0xFFFFAB00),
                SamplerPadEntity(6, "SUB DROP", null, 0.95f, 1.0f, 0xFFEC4899),
                SamplerPadEntity(7, "HORN", null, 0.85f, 1.0f, 0xFFFF6D00)
            )
            db.samplerPadDao().insertPads(defaults)
        }
    }

    suspend fun updateSettings(transform: (UserSettingsEntity) -> UserSettingsEntity) {
        val current = db.settingsDao().getSettings() ?: UserSettingsEntity()
        db.settingsDao().saveSettings(transform(current))
    }

    suspend fun updateSamplerPad(pad: SamplerPadEntity) {
        db.samplerPadDao().insertPad(pad)
    }
}
