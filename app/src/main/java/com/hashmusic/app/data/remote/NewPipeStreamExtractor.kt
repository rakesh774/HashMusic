package com.hashmusic.app.data.remote

import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NewPipeStreamExtractor {

    init {
        // Initialize NewPipe with our custom downloader
        NewPipe.init(NewPipeDownloader())
    }

    suspend fun getAudioStreamUrl(videoId: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://www.youtube.com/watch?v=$videoId"
                val extractor = ServiceList.YouTube.getStreamExtractor(url)
                extractor.fetchPage()

                val audioStreams = extractor.audioStreams
                // Get the best audio stream (highest bitrate)
                val bestAudio = audioStreams.maxByOrNull { it.averageBitrate }
                bestAudio?.content
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}
