package com.hashmusic.app.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.hashmusic.app.data.repository.MusicRepository
import com.hashmusic.app.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(
    application: Application,
    private val repository: MusicRepository
) : AndroidViewModel(application) {
    
    @OptIn(UnstableApi::class)
    private val player: ExoPlayer by lazy {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .setAllowCrossProtocolRedirects(true)

        val mediaSourceFactory = DefaultMediaSourceFactory(httpDataSourceFactory)

        ExoPlayer.Builder(application)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlayingChanged: Boolean) {
                        _isPlaying.value = isPlayingChanged
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY && duration > 0) {
                            val current = _currentSong.value
                            if (current != null && current.duration <= 0) {
                                _currentSong.value = current.copy(duration = duration / 1000)
                            }
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.e("MainViewModel", "ExoPlayer Error: ${error.errorCodeName}", error)
                        _isPlaying.value = false
                    }
                })
            }
    }

    private val _recommendedSongs = MutableStateFlow<List<Song>>(emptyList())
    val recommendedSongs: StateFlow<List<Song>> = _recommendedSongs.asStateFlow()

    private val _isLoadingRecommended = MutableStateFlow(false)
    val isLoadingRecommended: StateFlow<Boolean> = _isLoadingRecommended.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    private val _searchResults = MutableStateFlow<List<Song>>(emptyList())
    val searchResults: StateFlow<List<Song>> = _searchResults.asStateFlow()
    
    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()
    
    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    
    val likedSongs = repository.getLikedSongs()
    
    init {
        loadRecommendations()
    }

    fun loadRecommendations() {
        viewModelScope.launch {
            _isLoadingRecommended.value = true
            try {
                _recommendedSongs.value = repository.getRecommendedSongs()
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error loading recommendations", e)
            } finally {
                _isLoadingRecommended.value = false
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.length >= 2) {
            search(query)
        } else {
            _searchResults.value = emptyList()
        }
    }
    
    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = emptyList()
    }
    
    private fun search(query: String) {
        viewModelScope.launch {
            _isSearching.value = true
            try {
                _searchResults.value = repository.searchSongs(query)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Search error", e)
            } finally {
                _isSearching.value = false
            }
        }
    }
    
    fun playSong(song: Song) {
        _currentSong.value = song
        _isPlaying.value = true
        viewModelScope.launch {
            try {
                val streamUrl = repository.getStreamUrl(song.videoId)
                if (!streamUrl.isNullOrEmpty()) {
                    Log.d("MainViewModel", "Playing stream URL: $streamUrl")
                    val mediaItem = MediaItem.Builder()
                        .setUri(streamUrl)
                        .setMediaId(song.videoId)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(song.title)
                                .setArtist(song.artist)
                                .setArtworkUri(android.net.Uri.parse(song.albumArt))
                                .build()
                        )
                        .build()
                    
                    player.setMediaItem(mediaItem)
                    player.prepare()
                    player.play()
                } else {
                    Log.e("MainViewModel", "Failed to get stream URL for ${song.videoId}")
                    _isPlaying.value = false
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error playing song", e)
                _isPlaying.value = false
            }
        }
    }
    
    fun togglePlayPause() {
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_ENDED) {
                player.seekTo(0)
            }
            player.play()
        }
    }
    
    fun toggleLike(song: Song) {
        viewModelScope.launch {
            val isLiked = repository.isSongLiked(song.id)
            repository.toggleLike(song, !isLiked)
        }
    }

    fun loadLikedSongsFromRemote() {
        // Reserved for future remote liked songs syncing
    }

    override fun onCleared() {
        super.onCleared()
        player.release()
    }
}

class MainViewModelFactory(
    private val application: Application,
    private val repository: MusicRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}