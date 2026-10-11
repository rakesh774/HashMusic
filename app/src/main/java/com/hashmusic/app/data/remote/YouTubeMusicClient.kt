package com.hashmusic.app.data.remote

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
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
    
    private val contextMap = mapOf(
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
                    "context" to contextMap,
                    "query" to query,
                    "params" to "EgWKAQIIAWoQEAMQBBAJEAoQBRAREBAQFQ=="
                )
                
                val requestBody = gson.toJson(body)
                    .toRequestBody("application/json".toMediaType())
                
                val requestBuilder = Request.Builder()
                    .url("$BASE_URL/search?key=$API_KEY")
                    .post(requestBody)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                
                if (cookies.isNotEmpty()) {
                    requestBuilder.header("Cookie", cookies)
                }
                
                val request = requestBuilder.build()
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: return@withContext emptyList()
                
                parseSearchResponse(responseBody)
            } catch (e: Exception) {
                Log.e("YouTubeMusicClient", "Search error", e)
                emptyList()
            }
        }
    }
    
    private fun parseSearchResponse(jsonString: String): List<Song> {
        val songs = mutableListOf<Song>()
        try {
            val root = JsonParser.parseString(jsonString).asJsonObject
            val contentsObj = root.getAsJsonObject("contents") ?: return songs
            
            val sectionListRenderer = when {
                contentsObj.has("tabbedSearchResultsRenderer") -> {
                    contentsObj.getAsJsonObject("tabbedSearchResultsRenderer")
                        ?.getAsJsonArray("tabs")?.get(0)?.asJsonObject
                        ?.getAsJsonObject("tabRenderer")
                        ?.getAsJsonObject("content")
                        ?.getAsJsonObject("sectionListRenderer")
                }
                contentsObj.has("twoColumnSearchResultsRenderer") -> {
                    contentsObj.getAsJsonObject("twoColumnSearchResultsRenderer")
                        ?.getAsJsonObject("primaryContents")
                        ?.getAsJsonObject("sectionListRenderer")
                }
                contentsObj.has("sectionListRenderer") -> {
                    contentsObj.getAsJsonObject("sectionListRenderer")
                }
                else -> null
            } ?: return songs

            val sections = sectionListRenderer.getAsJsonArray("contents") ?: return songs
            
            for (sectionElement in sections) {
                val sectionObj = sectionElement.asJsonObject ?: continue
                
                val items = when {
                    sectionObj.has("musicShelfRenderer") -> {
                        sectionObj.getAsJsonObject("musicShelfRenderer")?.getAsJsonArray("contents")
                    }
                    sectionObj.has("musicCardShelfRenderer") -> {
                        sectionObj.getAsJsonObject("musicCardShelfRenderer")?.getAsJsonArray("contents")
                    }
                    sectionObj.has("itemSectionRenderer") -> {
                        sectionObj.getAsJsonObject("itemSectionRenderer")?.getAsJsonArray("contents")
                    }
                    else -> null
                } ?: continue

                for (itemElement in items) {
                    val itemObj = itemElement.asJsonObject ?: continue
                    val musicItem = itemObj.getAsJsonObject("musicResponsiveListItemRenderer") 
                        ?: itemObj.getAsJsonObject("playlistPanelVideoRenderer")
                        ?: continue

                    val flexColumns = musicItem.getAsJsonArray("flexColumns") ?: continue
                    if (flexColumns.size() < 1) continue

                    // Title
                    val flex0 = flexColumns[0].asJsonObject
                        ?.getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
                        ?.getAsJsonObject("text")
                    val title = flex0?.getAsJsonArray("runs")
                        ?.get(0)?.asJsonObject?.get("text")?.asString ?: continue

                    // Artist & Album & Duration
                    var artist = "Unknown Artist"
                    var durationText = "0:00"

                    if (flexColumns.size() > 1) {
                        val flex1Runs = flexColumns[1].asJsonObject
                            ?.getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
                            ?.getAsJsonObject("text")?.getAsJsonArray("runs")

                        if (flex1Runs != null && flex1Runs.size() > 0) {
                            val runsList = mutableListOf<String>()
                            for (r in flex1Runs) {
                                val txt = r.asJsonObject?.get("text")?.asString
                                if (!txt.isNullOrEmpty() && txt != " • ") {
                                    runsList.add(txt)
                                }
                            }
                            if (runsList.isNotEmpty()) {
                                artist = runsList.first()
                            }
                        }
                    }

                    if (flexColumns.size() > 2) {
                        val flex2Runs = flexColumns[2].asJsonObject
                            ?.getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
                            ?.getAsJsonObject("text")?.getAsJsonArray("runs")
                        if (flex2Runs != null && flex2Runs.size() > 0) {
                            durationText = flex2Runs[0].asJsonObject?.get("text")?.asString ?: "0:00"
                        }
                    }

                    // Video ID extraction
                    var videoId = musicItem.getAsJsonObject("playlistItemData")?.get("videoId")?.asString

                    if (videoId.isNullOrEmpty()) {
                        videoId = musicItem.getAsJsonObject("navigationEndpoint")
                            ?.getAsJsonObject("watchEndpoint")?.get("videoId")?.asString
                    }

                    if (videoId.isNullOrEmpty()) {
                        val menuItems = musicItem.getAsJsonObject("menu")
                            ?.getAsJsonObject("menuRenderer")
                            ?.getAsJsonArray("items")
                        if (menuItems != null) {
                            for (menuItem in menuItems) {
                                val watchEndpoint = menuItem.asJsonObject
                                    ?.getAsJsonObject("menuNavigationItemRenderer")
                                    ?.getAsJsonObject("navigationEndpoint")
                                    ?.getAsJsonObject("watchEndpoint")
                                if (watchEndpoint != null && watchEndpoint.has("videoId")) {
                                    videoId = watchEndpoint.get("videoId").asString
                                    break
                                }
                            }
                        }
                    }

                    if (videoId.isNullOrEmpty()) continue

                    // Thumbnail extraction
                    val thumbnails = musicItem.getAsJsonObject("thumbnail")
                        ?.getAsJsonObject("musicThumbnailRenderer")
                        ?.getAsJsonObject("thumbnail")
                        ?.getAsJsonArray("thumbnails")
                    val thumbnail = thumbnails?.lastOrNull()
                        ?.asJsonObject?.get("url")?.asString ?: ""

                    songs.add(
                        Song(
                            id = videoId,
                            title = title,
                            artist = artist,
                            albumArt = thumbnail,
                            duration = parseDuration(durationText),
                            videoId = videoId
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("YouTubeMusicClient", "Parse error", e)
        }
        return songs
    }
    
    suspend fun getStreamUrl(videoId: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val body = mapOf(
                    "context" to contextMap,
                    "videoId" to videoId,
                    "contentCheckOk" to true,
                    "racyCheckOk" to true
                )
                
                val requestBody = gson.toJson(body)
                    .toRequestBody("application/json".toMediaType())
                
                val requestBuilder = Request.Builder()
                    .url("$BASE_URL/player?key=$API_KEY")
                    .post(requestBody)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                
                if (cookies.isNotEmpty()) {
                    requestBuilder.header("Cookie", cookies)
                }

                val request = requestBuilder.build()
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: return@withContext null
                
                parseStreamUrl(responseBody)
            } catch (e: Exception) {
                Log.e("YouTubeMusicClient", "Stream URL error", e)
                null
            }
        }
    }
    
    private fun parseStreamUrl(jsonString: String): String? {
        try {
            val root = JsonParser.parseString(jsonString).asJsonObject
            val streamingData = root.getAsJsonObject("streamingData") ?: return null
            
            var bestUrl: String? = null
            var highestBitrate = 0
            
            val formats = streamingData.getAsJsonArray("adaptiveFormats") ?: JsonArray()
            for (format in formats) {
                val obj = format.asJsonObject
                val mimeType = obj.get("mimeType")?.asString ?: ""
                if (mimeType.startsWith("audio/")) {
                    val bitrate = obj.get("bitrate")?.asInt ?: 0
                    val url = obj.get("url")?.asString
                    if (url != null && bitrate > highestBitrate) {
                        highestBitrate = bitrate
                        bestUrl = url
                    }
                }
            }

            if (bestUrl == null) {
                val directFormats = streamingData.getAsJsonArray("formats") ?: JsonArray()
                for (format in directFormats) {
                    val obj = format.asJsonObject
                    val url = obj.get("url")?.asString
                    if (url != null) {
                        bestUrl = url
                        break
                    }
                }
            }

            return bestUrl
        } catch (e: Exception) {
            Log.e("YouTubeMusicClient", "Stream parse error", e)
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