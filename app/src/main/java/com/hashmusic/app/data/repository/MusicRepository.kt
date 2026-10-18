package com.hashmusic.app.data.repository

import com.hashmusic.app.data.local.AppDatabase
import com.hashmusic.app.data.local.SongEntity
import com.hashmusic.app.data.remote.NewPipeStreamExtractor
import com.hashmusic.app.data.remote.YouTubeMusicClient
import com.hashmusic.app.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MusicRepository(
    private val api: YouTubeMusicClient,
    private val db: AppDatabase
) {
    private val newPipeExtractor = NewPipeStreamExtractor()

    fun setCookies(cookieString: String) {
        api.setCookies(cookieString)
    }
    
    suspend fun getRecommendedSongs(): List<Song> = api.getRecommendedSongs()

    suspend fun searchSongs(query: String): List<Song> = api.searchSongs(query)
    
    suspend fun getStreamUrl(videoId: String): String? {
        return newPipeExtractor.getAudioStreamUrl(videoId)
    }
    
    fun getLikedSongs() = db.songDao().getLikedSongs()
    fun getDownloadedSongs() = db.songDao().getDownloadedSongs()
    
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