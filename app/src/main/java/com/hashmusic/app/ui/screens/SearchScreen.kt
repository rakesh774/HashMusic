package com.hashmusic.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hashmusic.app.HashMusicApplication
import com.hashmusic.app.ui.viewmodel.MainViewModel
import com.hashmusic.app.ui.viewmodel.MainViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as HashMusicApplication
    val viewModel: MainViewModel = viewModel(
        factory = MainViewModelFactory(app.repository)
    )
    
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.updateSearchQuery(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            placeholder = { Text("Search songs...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.clearSearch() }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true
        )
        
        if (isSearching) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                items(searchResults) { song ->
                    // Simple text display for now
                    Text(
                        text = "${song.title} - ${song.artist}",
                        modifier = Modifier.padding(vertical = 8.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                if (searchResults.isEmpty() && searchQuery.isNotEmpty()) {
                    item {
                        Text(
                            "No results found",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}