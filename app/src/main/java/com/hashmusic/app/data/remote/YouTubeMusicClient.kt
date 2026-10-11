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
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

class YouTubeMusicClient {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    
    private val API_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
    private val BASE_URL = "https://www.youtube.com/youtubei/v1"
    
    // Use TVHTML5_SIMPLY_EMBEDDED_PLAYER to avoid PO Token requirements for playback
    private val context = mapOf(
        "client" to mapOf(
            "clientName" to "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
            "clientVersion" to "2.0",
            "hl" to "en",
            "gl" to "US"
        )
    )

    private val webContextMap = mapOf(
        "client" to mapOf(
            "clientName" to "WEB_REMIX",
            "clientVersion" to "1.20240101.01.00",
            "hl" to "en",
            "gl" to "US"
        )
    )
    
    private var cookies: String = ""
    
    fun setCookies(cookieString: String) {
        cookies = cookieString
    }
    
    suspend fun getRecommendedSongs(): List<Song> {
        val trending = searchSongs("Top Hits")
        return if (trending.isNotEmpty()) trending else searchSongs("Trending Songs")
    }

    suspend fun searchSongs(query: String): List<Song> {
        return withContext(Dispatchers.IO) {
            try {
                val body = mapOf(
                    "context" to webContextMap,
                    "query" to query,
                    "params" to "EgWKAQIIAWoQEAMQBBAJEAoQBRAREBAQFQ=="
                )
                
                val requestBody = gson.toJson(body)
                    .toRequestBody("application/json".toMediaType())
                
                val request = Request.Builder()
                    .url("$BASE_URL/search?key=$API_KEY")
                    .post(requestBody)
                    .header("Cookie", cookies)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()
                
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
        val durationRegex = Regex("""\b\d{1,2}:\d{2}(:\d{2})?\b""")
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

                    // Artist & Duration
                    var artist = "Unknown Artist"
                    var durationText = "0:00"

                    for (col in flexColumns) {
                        val runs = col.asJsonObject
                            ?.getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
                            ?.getAsJsonObject("text")
                            ?.getAsJsonArray("runs") ?: continue

                        for (r in runs) {
                            val txt = r.asJsonObject?.get("text")?.asString?.trim() ?: continue
                            if (txt != " • " && txt != title && !durationRegex.matches(txt)) {
                                if (artist == "Unknown Artist") {
                                    artist = txt
                                }
                            } else if (durationRegex.matches(txt)) {
                                durationText = txt
                            }
                        }
                    }

                    // Video ID
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

                    // Thumbnail
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
            
            // First, check for an error or unplayable status
            val playabilityStatus = root.getAsJsonObject("playabilityStatus")
            val status = playabilityStatus?.get("status")?.asString
            if (status != "OK") {
                val reason = playabilityStatus?.get("reason")?.asString ?: "Unknown error"
                android.util.Log.e("HashMusic", "Playability status: $status - $reason")
                return null
            }
            
            val formats = root.getAsJsonObject("streamingData")
                ?.getAsJsonArray("adaptiveFormats") ?: return null
            
            var bestUrl: String? = null
            var highestBitrate = 0
            
            for (format in formats) {
                val obj = format.asJsonObject
                val mimeType = obj.get("mimeType")?.asString ?: ""
                if (mimeType.startsWith("audio/")) {
                    val bitrate = obj.get("bitrate")?.asInt ?: 0
                    var url = obj.get("url")?.asString
                    
                    if (url.isNullOrEmpty()) {
                        val cipher = obj.get("signatureCipher")?.asString ?: obj.get("cipher")?.asString
                        if (!cipher.isNullOrEmpty()) {
                            url = extractUrlFromCipher(cipher)
                        }
                    }

                    if (!url.isNullOrEmpty() && bitrate > highestBitrate) {
                        highestBitrate = bitrate
                        bestUrl = url
                    }
                }
            }
            return bestUrl
        } catch (e: Exception) {
            android.util.Log.e("HashMusic", "Error parsing stream URL: ${e.message}", e)
            return null
        }
    }

    private fun extractUrlFromCipher(cipherText: String): String? {
        try {
            val params = cipherText.split("&")
            var url: String? = null
            var sig: String? = null
            var sp = "sig"
            
            for (param in params) {
                val parts = param.split("=", limit = 2)
                if (parts.size == 2) {
                    val key = parts[0]
                    val value = URLDecoder.decode(parts[1], "UTF-8")
                    if (key == "url") {
                        url = value
                    } else if (key == "s") {
                        sig = value
                    } else if (key == "sp") {
                        sp = value
                    }
                }
            }
            if (url != null) {
                return if (sig != null) "$url&$sp=$sig" else url
            }
        } catch (e: Exception) {
            Log.e("YouTubeMusicClient", "Cipher decode error", e)
        }
        return null
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