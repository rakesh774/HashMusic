package com.hashmusic.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.hashmusic.app.HashMusicApplication
import com.hashmusic.app.ui.components.NowPlayingBar
import com.hashmusic.app.ui.screens.HomeScreen
import com.hashmusic.app.ui.screens.LibraryScreen
import com.hashmusic.app.ui.screens.LoginScreen
import com.hashmusic.app.ui.screens.SearchScreen
import com.hashmusic.app.ui.viewmodel.MainViewModel
import com.hashmusic.app.ui.viewmodel.MainViewModelFactory

sealed class Screen(val route: String, val title: String) {
    object Home : Screen("home", "Home")
    object Search : Screen("search", "Search")
    object Library : Screen("library", "Library")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HashMusicApp() {
    val context = LocalContext.current
    val app = context.applicationContext as HashMusicApplication
    val viewModel: MainViewModel = viewModel(
        factory = MainViewModelFactory(app, app.repository)
    )
    
    var isLoggedIn by remember { mutableStateOf(false) }
    
    if (!isLoggedIn) {
        LoginScreen(onLoginSuccess = { isLoggedIn = true })
    } else {
        // Main app content
        val navController = rememberNavController()
        val screens = listOf(Screen.Home, Screen.Search, Screen.Library)
        val currentSong by viewModel.currentSong.collectAsState()
        val isPlaying by viewModel.isPlaying.collectAsState()
        val likedSongs by viewModel.likedSongs.collectAsState(initial = emptyList())
        
        Scaffold(
            bottomBar = {
                Column {
                    NowPlayingBar(
                        currentSong = currentSong,
                        isPlaying = isPlaying,
                        onPlayPause = { viewModel.togglePlayPause() },
                        onBarClick = { /* Navigate to now playing */ }
                    )
                    NavigationBar {
                        val navBackStackEntry by navController.currentBackStackEntryAsState()
                        val currentDestination = navBackStackEntry?.destination
                        
                        screens.forEach { screen ->
                            NavigationBarItem(
                                icon = {
                                    when (screen) {
                                        Screen.Home -> Icon(Icons.Filled.Home, contentDescription = null)
                                        Screen.Search -> Icon(Icons.Filled.Search, contentDescription = null)
                                        Screen.Library -> Icon(Icons.Filled.LibraryMusic, contentDescription = null)
                                    }
                                },
                                label = { Text(screen.title) },
                                selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                                onClick = {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }
                    }
                }
            }
        ) { paddingValues ->
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.padding(paddingValues)
            ) {
                composable(Screen.Home.route) { HomeScreen() }
                composable(Screen.Search.route) { SearchScreen(viewModel = viewModel) }
                composable(Screen.Library.route) { 
                    LibraryScreen(
                        likedSongs = likedSongs,
                        onSongClick = { entity ->
                            // Convert entity to model for playing
                            val song = com.hashmusic.app.model.Song(
                                id = entity.id,
                                title = entity.title,
                                artist = entity.artist,
                                albumArt = entity.albumArt,
                                duration = entity.duration,
                                videoId = entity.videoId,
                                album = entity.album,
                                isLiked = entity.isLiked,
                                isDownloaded = entity.isDownloaded,
                                localPath = entity.localPath
                            )
                            viewModel.playSong(song)
                        }
                    ) 
                }
            }
        }
    }
}