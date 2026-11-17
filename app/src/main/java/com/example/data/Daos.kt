package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks ORDER BY dateAdded DESC")
    fun observeAllTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE lastPlayedAt > 0 ORDER BY lastPlayedAt DESC LIMIT 20")
    fun observeRecentlyPlayed(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE uriString = :uri LIMIT 1")
    suspend fun findByUri(uri: String): TrackEntity?

    @Query("SELECT COUNT(*) FROM tracks")
    suspend fun getTrackCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: TrackEntity): Long

    @Update
    suspend fun updateTrack(track: TrackEntity)

    @Query("UPDATE tracks SET lastPlayedAt = :timestamp, playCount = playCount + 1 WHERE id = :trackId")
    suspend fun markPlayed(trackId: Long, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM tracks WHERE id = :trackId")
    suspend fun deleteTrackById(trackId: Long)
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun observeAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query(
        """
        SELECT tracks.* FROM tracks
        INNER JOIN playlist_tracks ON tracks.id = playlist_tracks.trackId
        WHERE playlist_tracks.playlistId = :playlistId
        ORDER BY playlist_tracks.position ASC
        """
    )
    fun observeTracksForPlaylist(playlistId: Long): Flow<List<TrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addTrackToPlaylist(ref: PlaylistTrackCrossRef)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long)
}

@Dao
interface CuePointDao {
    @Query("SELECT * FROM cue_points WHERE trackId = :trackId ORDER BY slotIndex ASC")
    fun observeCuesForTrack(trackId: Long): Flow<List<CuePointEntity>>

    @Query("SELECT * FROM cue_points WHERE trackId = :trackId ORDER BY slotIndex ASC")
    suspend fun getCuesForTrack(trackId: Long): List<CuePointEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCue(cue: CuePointEntity): Long

    @Query("DELETE FROM cue_points WHERE trackId = :trackId AND slotIndex = :slotIndex")
    suspend fun deleteCueBySlot(trackId: Long, slotIndex: Int)
}

@Dao
interface RecordingDao {
    @Query("SELECT * FROM recordings ORDER BY createdAt DESC")
    fun observeAllRecordings(): Flow<List<RecordingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecording(recording: RecordingEntity): Long

    @Delete
    suspend fun deleteRecording(recording: RecordingEntity)
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM user_settings WHERE id = 1")
    fun observeSettings(): Flow<UserSettingsEntity?>

    @Query("SELECT * FROM user_settings WHERE id = 1")
    suspend fun getSettings(): UserSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: UserSettingsEntity)
}

@Dao
interface SamplerPadDao {
    @Query("SELECT * FROM sampler_pads ORDER BY padIndex ASC")
    fun observePads(): Flow<List<SamplerPadEntity>>

    @Query("SELECT COUNT(*) FROM sampler_pads")
    suspend fun getPadCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPad(pad: SamplerPadEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPads(pads: List<SamplerPadEntity>)
}
