package com.hashmusic.app.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.youtube.YouTubeScopes
import com.hashmusic.app.data.remote.PlayerConnection
import com.hashmusic.app.data.remote.YouTubeService
import com.hashmusic.app.data.repository.MusicRepository
import com.hashmusic.app.model.Song
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(
    application: Application,
    private val repository: MusicRepository
) : AndroidViewModel(application) {

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
    
    private val _likedSongs = MutableStateFlow<List<Song>>(emptyList())
    val likedSongs: StateFlow<List<Song>> = _likedSongs.asStateFlow()

    private val youtubeService = YouTubeService(getApplication())
    lateinit var googleSignInClient: GoogleSignInClient

    init {
        loadRecommendations()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestScopes(Scope(YouTubeScopes.YOUTUBE_READONLY))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(getApplication(), gso)
    }

    fun getGoogleSignInIntent() = googleSignInClient.signInIntent

    fun handleSignInResult(account: GoogleSignInAccount?) {
        if (account != null) {
            youtubeService.setAuthToken(account)
            loadLikedSongs()
        }
    }

    private fun loadLikedSongs() {
        viewModelScope.launch {
            val songs = youtubeService.getLikedVideos()
            _likedSongs.value = songs
        }
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
    
    fun playSong(song: Song, controller: MediaController? = null) {
        _currentSong.value = song

        viewModelScope.launch {
            try {
                val streamUrl = repository.getStreamUrl(song.videoId)
                if (streamUrl != null && controller != null) {
                    val mediaItem = MediaItem.Builder()
                        .setUri(streamUrl)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(song.title)
                                .setArtist(song.artist)
                                .setArtworkUri(android.net.Uri.parse(song.albumArt))
                                .build()
                        )
                        .build()

                    controller.setMediaItem(mediaItem)
                    controller.prepare()
                    controller.play()
                    _isPlaying.value = true
                } else {
                    android.widget.Toast.makeText(
                        getApplication(),
                        "Could not play song or controller is null",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun togglePlayPause() {
        PlayerConnection.togglePlayPause()
        _isPlaying.value = PlayerConnection.isPlaying()
    }
    
    fun toggleLike(song: Song) {
        viewModelScope.launch {
            val isLiked = repository.isSongLiked(song.id)
            repository.toggleLike(song, !isLiked)
        }
    }

    fun loadLikedSongsFromRemote() {
        loadLikedSongs()
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
