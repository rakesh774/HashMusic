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

class YouTubeMusicClient {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    
    private val API_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
    private val BASE_URL = "https://music.youtube.com/youtubei/v1"
    
    private val context = mapOf(
        "client" to mapOf(
            "clientName" to "WEB_REMIX",
            "clientVersion" to "1.20240101.01.00",
            "hl" to "en",
            "gl" to "US"
        )
    )
    
    // Cookies captured from WebView login
    private var cookies: String = ""
    
    fun setCookies(cookieString: String) {
        cookies = cookieString
    }
    
    suspend fun searchSongs(query: String): List<Song> {
        return withContext(Dispatchers.IO) {
            try {
                val body = mapOf(
                    "context" to context,
                    "query" to query,
                    "params" to "EgWKAQIIAWoQEAMQBBAJEAoQBRAREBAQFQ=="
                )
                
                val requestBody = gson.toJson(body)
                    .toRequestBody("application/json".toMediaType())
                
                val requestBuilder = Request.Builder()
                    .url("$BASE_URL/search?key=$API_KEY")
                    .post(requestBody)
                    .header("Cookie", cookies)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                
                val request = requestBuilder.build()
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: return@withContext emptyList()
                
                parseSearchResponse(responseBody)
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }
    
    private fun parseSearchResponse(jsonString: String): List<Song> {
        val songs = mutableListOf<Song>()
        try {
            val root = JsonParser.parseString(jsonString).asJsonObject
            val contents = root.getAsJsonObject("contents")
                ?.getAsJsonObject("sectionListRenderer")
                ?.getAsJsonArray("contents") ?: return songs
            
            for (section in contents) {
                val items = section.asJsonObject
                    ?.getAsJsonObject("musicShelfRenderer")
                    ?.getAsJsonArray("contents") ?: continue
                
                for (item in items) {
                    val musicItem = item.asJsonObject
                        ?.getAsJsonObject("musicResponsiveListItemRenderer") ?: continue
                    val flexColumns = musicItem.getAsJsonArray("flexColumns") ?: continue
                    if (flexColumns.size() < 2) continue
                    
                    val title = flexColumns[0].asJsonObject
                        .getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
                        .getAsJsonObject("text").getAsJsonArray("runs")
                        ?.get(0)?.asJsonObject?.get("text")?.asString ?: continue
                    
                    val artist = flexColumns[1].asJsonObject
                        .getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
                        .getAsJsonObject("text").getAsJsonArray("runs")
                        ?.mapNotNull { it.asJsonObject?.get("text")?.asString }
                        ?.joinToString(", ") ?: "Unknown Artist"
                    
                    val videoId = musicItem.getAsJsonObject("playlistItemData")
                        ?.get("videoId")?.asString ?: continue
                    
                    val thumbnails = musicItem.getAsJsonObject("thumbnail")
                        ?.getAsJsonObject("musicThumbnailRenderer")
                        ?.getAsJsonObject("thumbnail")
                        ?.getAsJsonArray("thumbnails")
                    val thumbnail = thumbnails?.lastOrNull()
                        ?.asJsonObject?.get("url")?.asString ?: ""
                    
                    val durationText = if (flexColumns.size() > 2) {
                        flexColumns[2].asJsonObject
                            .getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
                            .getAsJsonObject("text").getAsJsonArray("runs")
                            ?.get(0)?.asJsonObject?.get("text")?.asString ?: "0:00"
                    } else "0:00"
                    
                    songs.add(Song(
                        id = videoId, title = title, artist = artist,
                        albumArt = thumbnail, duration = parseDuration(durationText),
                        videoId = videoId
                    ))
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return songs
    }
    
    suspend fun getStreamUrl(videoId: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val body = mapOf(
                    "context" to context,
                    "videoId" to videoId,
                    "contentCheckOk" to true,
                    "racyCheckOk" to true
                )
                
                val requestBody = gson.toJson(body)
                    .toRequestBody("application/json".toMediaType())
                
                val request = Request.Builder()
                    .url("$BASE_URL/player?key=$API_KEY")
                    .post(requestBody)
                    .header("Cookie", cookies)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()
                
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: return@withContext null
                
                parseStreamUrl(responseBody)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
    
    private fun parseStreamUrl(jsonString: String): String? {
        try {
            val root = JsonParser.parseString(jsonString).asJsonObject
            val formats = root.getAsJsonObject("streamingData")
                ?.getAsJsonArray("adaptiveFormats") ?: return null
            
            var bestUrl: String? = null
            var highestBitrate = 0
            
            for (format in formats) {
                val obj = format.asJsonObject
                val mimeType = obj.get("mimeType")?.asString ?: ""
                if (mimeType.startsWith("audio/")) {
                    val bitrate = obj.get("bitrate")?.asInt ?: 0
                    if (bitrate > highestBitrate) {
                        highestBitrate = bitrate
                        bestUrl = obj.get("url")?.asString
                    }
                }
            }
            return bestUrl
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
    
    private fun parseDuration(durationText: String): Long {
        val parts = durationText.split(":")
        return when (parts.size) {
            2 -> (parts[0].toLongOrNull() ?: 0) * 60 + (parts[1].toLongOrNull() ?: 0)
            3 -> (parts[0].toLongOrNull() ?: 0) * 3600 + (parts[1].toLongOrNull() ?: 0) * 60 + (parts[2].toLongOrNull() ?: 0)
            else -> 0
        }
    }
}