package com.hashmusic.app.data.repository

import com.hashmusic.app.data.local.AppDatabase
import com.hashmusic.app.data.local.SongEntity
import com.hashmusic.app.data.remote.HashMusicApiService
import com.hashmusic.app.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MusicRepository(
    private val api: HashMusicApiService,
    private val db: AppDatabase
) {
    fun setSession(sessionId: String) {
        api.setSession(sessionId)
    }
    
    suspend fun searchSongs(query: String): List<Song> = api.searchSongs(query)
    
    suspend fun getStreamUrl(videoId: String): String? = api.getStreamUrl(videoId)
    
    fun getLikedSongs() = db.songDao().getLikedSongs()
    
    fun getDownloadedSongs() = db.songDao().getDownloadedSongs()
    
    suspend fun getRemoteLikedSongs(): List<Song> = api.getLikedSongs()
    
    suspend fun toggleLike(song: Song, isLiked: Boolean) {
        withContext(Dispatchers.IO) {
            val entity = SongEntity(
                id = song.id, title = song.title, artist = song.artist,
                albumArt = song.albumArt, duration = song.duration,
                videoId = song.videoId, album = song.album, isLiked = isLiked
            )
            db.songDao().insertSong(entity)
        }
    }
    
    suspend fun isSongLiked(songId: String): Boolean {
        return withContext(Dispatchers.IO) {
            db.songDao().getSongById(songId)?.isLiked ?: false
        }
    }
}