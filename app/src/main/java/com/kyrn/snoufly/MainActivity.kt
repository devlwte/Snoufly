package com.kyrn.snoufly

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kyrn.snoufly.data.MusicRepository
import com.kyrn.snoufly.data.SettingsManager
import com.kyrn.snoufly.data.ThemeMode
import com.kyrn.snoufly.playback.PlaybackViewModel
import com.kyrn.snoufly.ui.MainViewModel
import com.kyrn.snoufly.ui.MainViewModelFactory
import com.kyrn.snoufly.ui.Screen
import com.kyrn.snoufly.ui.components.MiniPlayer
import com.kyrn.snoufly.ui.components.SongItem
import com.kyrn.snoufly.ui.navItems
import com.kyrn.snoufly.ui.screens.*
import com.kyrn.snoufly.ui.theme.SnouflyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val repository = MusicRepository(applicationContext)
        val settingsManager = SettingsManager(applicationContext)
        
        setContent {
            val context = LocalContext.current
            val mainViewModel: MainViewModel = viewModel(
                factory = MainViewModelFactory(repository, settingsManager)
            )
            val playbackViewModel: PlaybackViewModel = viewModel()
            
            val navController = rememberNavController()
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination

            val themeMode by mainViewModel.themeMode.collectAsState()
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            // Gestión de permisos
            var hasPermission by remember {
                mutableStateOf(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
                    } else {
                        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                    }
                )
            }

            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                hasPermission = isGranted
                if (isGranted) mainViewModel.loadSongs()
            }

            LaunchedEffect(hasPermission) {
                if (!hasPermission) {
                    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
                    permissionLauncher.launch(permission)
                } else {
                    mainViewModel.loadSongs()
                    playbackViewModel.initController(context)
                }
            }

            SnouflyTheme(darkTheme = darkTheme) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        // Ocultar barras en el reproductor a pantalla completa
                        if (currentDestination?.route != Screen.Player.route) {
                            Column {
                                val currentMediaItem by playbackViewModel.currentMediaItem.collectAsState()
                                val isPlaying by playbackViewModel.isPlaying.collectAsState()
                                
                                if (currentMediaItem != null) {
                                    MiniPlayer(
                                        currentMediaItem = currentMediaItem,
                                        isPlaying = isPlaying,
                                        onPlayPause = { playbackViewModel.togglePlayPause() },
                                        onClick = { navController.navigate(Screen.Player.route) }
                                    )
                                }
                                
                                NavigationBar(windowInsets = WindowInsets.navigationBars) {
                                    navItems.forEach { screen ->
                                        val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                                        NavigationBarItem(
                                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                                            label = { Text(screen.title) },
                                            selected = selected,
                                            onClick = {
                                                if (screen.route == Screen.Library.route) {
                                                    // RESET TOTAL: Si es Inicio, limpiar todo el backstack hasta la raíz
                                                    navController.popBackStack(navController.graph.findStartDestination().id, false)
                                                } else {
                                                    navController.navigate(screen.route) {
                                                        popUpTo(navController.graph.findStartDestination().id) {
                                                            saveState = true
                                                        }
                                                        launchSingleTop = true
                                                        restoreState = true
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    // Uso de padding solo inferior para evitar márgenes superiores dobles
                    Box(modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())) {
                        NavHost(
                            navController = navController, 
                            startDestination = Screen.Library.route,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            composable(Screen.Library.route) {
                                LibraryScreen(
                                    viewModel = mainViewModel,
                                    onSongClick = { index ->
                                        val songs = mainViewModel.songs.value
                                        playbackViewModel.playSongs(songs, index)
                                        mainViewModel.addToRecentlyPlayed(songs[index].id)
                                        navController.navigate(Screen.Player.route)
                                    },
                                    onSettingsClick = { navController.navigate(Screen.Settings.route) },
                                    onSeeAllListenAgain = { navController.navigate("listen_again") }
                                )
                            }
                            composable("listen_again") {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    val listenAgain by mainViewModel.listenAgain.collectAsState()
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        Text(
                                            "Listen Again", 
                                            style = MaterialTheme.typography.headlineMedium,
                                            modifier = Modifier.padding(16.dp),
                                            fontWeight = FontWeight.Bold
                                        )
                                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                                            items(listenAgain) { song ->
                                                SongItem(
                                                    song = song,
                                                    isFavorite = mainViewModel.isFavorite(song.id),
                                                    onToggleFavorite = { mainViewModel.toggleFavorite(song.id) },
                                                    onClick = {
                                                        val index = mainViewModel.songs.value.indexOf(song)
                                                        if (index != -1) {
                                                            playbackViewModel.playSongs(mainViewModel.songs.value, index)
                                                            navController.navigate(Screen.Player.route)
                                                        }
                                                    },
                                                    onEditClick = {},
                                                    onSelectLrcClick = {}
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            composable(Screen.Favorites.route) {
                                FavoritesScreen(
                                    viewModel = mainViewModel,
                                    onSongClick = { index ->
                                        val songs = mainViewModel.favorites.value
                                        playbackViewModel.playSongs(songs, index)
                                        navController.navigate(Screen.Player.route)
                                    }
                                )
                            }
                            composable(Screen.Player.route) {
                                NowPlayingScreen(
                                    viewModel = playbackViewModel,
                                    mainViewModel = mainViewModel,
                                    onBackClick = { navController.popBackStack() }
                                )
                            }
                            composable(Screen.Settings.route) {
                                SettingsScreen(
                                    viewModel = mainViewModel,
                                    onEqualizerClick = { navController.navigate("equalizer") }
                                )
                            }
                            composable("equalizer") {
                                EqualizerScreen(
                                    mainViewModel = mainViewModel,
                                    playbackViewModel = playbackViewModel,
                                    onBackClick = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
