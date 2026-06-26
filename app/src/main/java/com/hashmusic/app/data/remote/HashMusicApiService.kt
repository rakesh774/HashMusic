package com.hashmusic.app.data.remote

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.hashmusic.app.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class HashMusicApiService {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    private val BASE_URL = "https://hashmusic-backend.onrender.com/api"
    private var sessionId: String? = null
    
    fun setSession(sessionId: String) {
        this.sessionId = sessionId
    }
    
    suspend fun searchSongs(query: String): List<Song> {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$BASE_URL/search?q=$query")
                    .header("X-Session-ID", sessionId ?: "")
                    .build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext emptyList()
                parseSongList(body)
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }
    
    suspend fun getStreamUrl(videoId: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$BASE_URL/stream/$videoId")
                    .header("X-Session-ID", sessionId ?: "")
                    .build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext null
                val root = JsonParser.parseString(body).asJsonObject
                root.get("url")?.asString
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
    
    suspend fun getLikedSongs(): List<Song> {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$BASE_URL/liked")
                    .header("X-Session-ID", sessionId ?: "")
                    .build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext emptyList()
                parseSongList(body)
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }
    
    private fun parseSongList(jsonString: String): List<Song> {
        val songs = mutableListOf<Song>()
        try {
            val root = JsonParser.parseString(jsonString).asJsonObject
            val songsArray = root.getAsJsonArray("songs") ?: return songs
            for (item in songsArray) {
                val song = item.asJsonObject
                songs.add(
                    Song(
                        id = song.get("id")?.asString ?: "",
                        title = song.get("title")?.asString ?: "Unknown",
                        artist = song.get("artist")?.asString ?: "Unknown",
                        albumArt = song.get("albumArt")?.asString ?: "",
                        duration = song.get("duration")?.asLong ?: 0,
                        videoId = song.get("videoId")?.asString ?: "",
                        album = song.get("album")?.asString ?: ""
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return songs
    }
}