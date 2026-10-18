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
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.hashmusic.app.HashMusicApplication
import com.hashmusic.app.ui.components.NowPlayingBar
import com.hashmusic.app.ui.screens.HomeScreen
import com.hashmusic.app.ui.screens.LibraryFromApiScreen
import com.hashmusic.app.ui.screens.LibraryScreen
import com.hashmusic.app.ui.screens.LoginScreen
import com.hashmusic.app.ui.screens.NowPlayingScreen
import com.hashmusic.app.ui.screens.SearchScreen
import com.hashmusic.app.ui.viewmodel.MainViewModel
import com.hashmusic.app.ui.viewmodel.MainViewModelFactory

sealed class Screen(val route: String, val title: String) {
    object Home : Screen("home", "Home")
    object Search : Screen("search", "Search")
    object Library : Screen("library", "Library")
    object NowPlaying : Screen("now_playing", "Now Playing")
}

@UnstableApi
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HashMusicApp(mediaController: MediaController?) {
    val context = LocalContext.current
    val app = context.applicationContext as HashMusicApplication
    val viewModel: MainViewModel = viewModel(
        factory = MainViewModelFactory(app, app.repository)
    )
    
    var isLoggedIn by remember { mutableStateOf(false) }
    
    if (!isLoggedIn) {
        LoginScreen(
            onLoginSuccess = { account ->
                viewModel.handleSignInResult(account)
                isLoggedIn = true
            },
            getSignInIntent = { viewModel.getGoogleSignInIntent() }
        )
    } else {
        val navController = rememberNavController()
        val screens = listOf(Screen.Home, Screen.Search, Screen.Library)
        val currentSong by viewModel.currentSong.collectAsState()
        val isPlaying by viewModel.isPlaying.collectAsState()
        
        Scaffold(
            bottomBar = {
                Column {
                    NowPlayingBar(
                        currentSong = currentSong,
                        isPlaying = isPlaying,
                        onPlayPause = { viewModel.togglePlayPause() },
                        onBarClick = { navController.navigate(Screen.NowPlaying.route) }
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
                                        else -> {}
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
                composable(Screen.Home.route) {
                    HomeScreen(
                        viewModel = viewModel,
                        mediaController = mediaController
                    )
                }
                composable(Screen.Search.route) {
                    SearchScreen(
                        viewModel = viewModel,
                        mediaController = mediaController
                    )
                }
                composable(Screen.Library.route) {
                    val likedSongs by viewModel.likedSongs.collectAsState()
                    LibraryFromApiScreen(songs = likedSongs)
                }
                composable(Screen.NowPlaying.route) {
                    val currentSong by viewModel.currentSong.collectAsState()
                    val controller = mediaController
                    if (currentSong != null && controller != null) {
                        NowPlayingScreen(
                            song = currentSong!!,
                            player = controller,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}