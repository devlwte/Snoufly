package com.kyrn.snoufly

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
import com.kyrn.snoufly.data.BackupManager
import com.kyrn.snoufly.data.MusicRepository
import com.kyrn.snoufly.data.SettingsManager
import com.kyrn.snoufly.data.ThemeMode
import com.kyrn.snoufly.playback.PlaybackViewModel
import com.kyrn.snoufly.ui.MainViewModel
import com.kyrn.snoufly.ui.MainViewModelFactory
import com.kyrn.snoufly.ui.Screen
import com.kyrn.snoufly.ui.components.EditSongDialog
import com.kyrn.snoufly.ui.components.MiniPlayer
import com.kyrn.snoufly.ui.components.SongItem
import com.kyrn.snoufly.ui.navItems
import com.kyrn.snoufly.ui.screens.*
import com.kyrn.snoufly.ui.theme.SnouflyTheme
import com.kyrn.snoufly.utils.LocalTranslations
import com.kyrn.snoufly.utils.t

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val repository = MusicRepository(applicationContext)
        val settingsManager = SettingsManager(applicationContext)
        val backupManager = BackupManager(applicationContext, settingsManager)
        
        setContent {
            val context = LocalContext.current
            val mainViewModel: MainViewModel = viewModel(
                factory = MainViewModelFactory(application, repository, settingsManager, backupManager)
            )
            val playbackViewModel: PlaybackViewModel = viewModel()
            
            val navController = rememberNavController()
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination

            // Centralizamos la observación de traducciones
            val translations by mainViewModel.translations.collectAsState()

            var globalEditingSong by remember { mutableStateOf<com.kyrn.snoufly.data.Song?>(null) }

            var lastNavTime by remember { mutableLongStateOf(0L) }
            val canNavigate = {
                val now = System.currentTimeMillis()
                if (now - lastNavTime > 500) {
                    lastNavTime = now
                    true
                } else false
            }

            BackHandler(enabled = currentDestination?.route != Screen.Library.route) {
                if (canNavigate()) {
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    } else {
                        navController.navigate(Screen.Library.route) {
                            popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }
            }

            val themeMode by mainViewModel.themeMode.collectAsState()
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

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
                if (isGranted) {
                    mainViewModel.loadSongs()
                    playbackViewModel.initController(context, mainViewModel)
                } else {
                    Toast.makeText(this, "Permission denied. Please grant storage access.", Toast.LENGTH_LONG).show()
                }
            }

            LaunchedEffect(hasPermission) {
                if (!hasPermission) {
                    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Manifest.permission.READ_MEDIA_AUDIO
                    } else {
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    }
                    permissionLauncher.launch(permission)
                } else {
                    mainViewModel.loadSongs()
                    playbackViewModel.initController(context, mainViewModel)
                }
            }

            // Proveedor global para que 't()' funcione en cualquier pantalla
            CompositionLocalProvider(LocalTranslations provides translations) {
                SnouflyTheme(darkTheme = darkTheme) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        bottomBar = {
                            if (currentDestination?.route != Screen.Player.route) {
                                Column {
                                    val currentMediaItem by playbackViewModel.currentMediaItem.collectAsState()
                                    val isPlaying by playbackViewModel.isPlaying.collectAsState()

                                    if (currentMediaItem != null) {
                                        MiniPlayer(
                                            currentMediaItem = currentMediaItem,
                                            isPlaying = isPlaying,
                                            onPlayPause = { playbackViewModel.togglePlayPause() },
                                            onClick = { if (canNavigate()) navController.navigate(Screen.Player.route) }
                                        )
                                    }

                                    NavigationBar(windowInsets = WindowInsets.navigationBars) {
                                        navItems.forEach { screen ->
                                            val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                                            NavigationBarItem(
                                                icon = { Icon(screen.icon, contentDescription = screen.title) },
                                                // Uso de la nueva función t()
                                                label = { 
                                                    val labelKey = when(screen.route) {
                                                        Screen.Library.route -> "tabs.recents" // O una clave común
                                                        else -> "title" 
                                                    }
                                                    Text(t(labelKey, screen.title, section = if(screen.route == Screen.Library.route) "library" else "common")) 
                                                },
                                                selected = selected,
                                                onClick = {
                                                    if (canNavigate()) {
                                                        if (screen.route == Screen.Library.route) {
                                                            if (navController.currentDestination?.route != Screen.Library.route) {
                                                                navController.popBackStack(navController.graph.findStartDestination().id, false)
                                                            }
                                                        } else {
                                                            navController.navigate(screen.route) {
                                                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                                                launchSingleTop = true
                                                                restoreState = true
                                                            }
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
                                            navController.navigate(Screen.Player.route)
                                        },
                                        onSettingsClick = { if (canNavigate()) navController.navigate(Screen.Settings.route) },
                                        onSeeAllListenAgain = { if (canNavigate()) navController.navigate("listen_again") },
                                        onEditSong = { song -> globalEditingSong = song }
                                    )
                                }
                                @OptIn(ExperimentalMaterial3Api::class)
                                composable("listen_again") {
                                    val listenAgainSongs by mainViewModel.listenAgain.collectAsState()
                                    val favoriteIds by mainViewModel.favoriteIds.collectAsState()

                                    Scaffold(
                                        topBar = {
                                            TopAppBar(
                                                title = { Text(t("listen_again", "Listen Again", "library"), fontWeight = FontWeight.Bold) },
                                                navigationIcon = {
                                                    IconButton(onClick = { if (canNavigate()) navController.popBackStack() }) {
                                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                                    }
                                                }
                                            )
                                        }
                                    ) { p ->
                                        if (listenAgainSongs.isEmpty()) {
                                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                                                Text(t("listen_again_sub", "Keep listening!", "library"))
                                            }
                                        } else {
                                            LazyColumn(
                                                modifier = Modifier.fillMaxSize().padding(p),
                                                contentPadding = PaddingValues(bottom = 80.dp)
                                            ) {
                                                items(listenAgainSongs) { song ->
                                                    SongItem(
                                                        song = song,
                                                        isFavorite = favoriteIds.contains(song.id),
                                                        onToggleFavorite = { mainViewModel.toggleFavorite(song.id) },
                                                        onClick = {
                                                            val index = listenAgainSongs.indexOf(song)
                                                            playbackViewModel.playSongs(listenAgainSongs, index)
                                                            navController.navigate(Screen.Player.route)
                                                        },
                                                        onEditClick = { globalEditingSong = song },
                                                        onSelectLrcClick = { /* Manual LRC */ }
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
                                        },
                                        onEditSong = { song -> globalEditingSong = song }
                                    )
                                }
                                composable(Screen.Player.route) {
                                    NowPlayingScreen(
                                        viewModel = playbackViewModel,
                                        mainViewModel = mainViewModel,
                                        onBackClick = { if (canNavigate()) navController.popBackStack() }
                                    )
                                }
                                composable(Screen.Settings.route) {
                                    SettingsScreen(
                                        viewModel = mainViewModel,
                                        onEqualizerClick = { if (canNavigate()) navController.navigate("equalizer") }
                                    )
                                }
                                composable("equalizer") {
                                    EqualizerScreen(
                                        mainViewModel = mainViewModel,
                                        playbackViewModel = playbackViewModel,
                                        onBackClick = { if (canNavigate()) navController.popBackStack() }
                                    )
                                }
                            }
                        }
                    }

                    globalEditingSong?.let { song ->
                        EditSongDialog(
                            song = song,
                            // mainViewModel = mainViewModel,
                            onDismiss = { globalEditingSong = null },
                            onConfirm = { title, artist, album ->
                                mainViewModel.updateSongMetadata(song.id, title, artist, album, null)
                                globalEditingSong = null
                            }
                        )
                    }
                }
            }
        }
    }
}
