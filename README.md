# ❄️ Snoufly - The Ultimate High-Fidelity Music Experience

[![Android API](https://img.shields.io/badge/API-24%2B-brightgreen.svg)](https://android-arsenal.com/api?level=24)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-blue.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Compose-Material3-purple.svg)](https://developer.android.com/jetpack/compose)
[![Media3](https://img.shields.io/badge/Media3-v1.5.1-orange.svg)](https://developer.android.com/guide/topics/media/media3)

**Snoufly** is a state-of-the-art music player for Android, meticulously engineered for audiophiles and power users. Built from the ground up using **Jetpack Compose** and **Media3**, it combines a sleek, minimalist aesthetic with a high-performance audio engine.

---

## 🚀 Key Features

### 🎧 Advanced Audio Engine
- **Media3 & ExoPlayer Integration**: Industry-standard playback stability and background service management.
- **Real-time DSP**: In-app **10-band Equalizer** (via Android FX) with millibel-precision control.
- **Dynamic Playback Control**: Adjust **Playback Speed** (0.5x - 2.0x) and **Pitch** without affecting tempo, powered by Sonic audio processor.
- **Gapless Playback**: Seamless transitions between tracks for an uninterrupted listening experience.

### 📜 Synchronized Lyrics (LRC)
- **Automatic Scrolling**: Supports standard `.lrc` files with high-precision synchronization.
- **Manual Mapping**: Ability to manually pick and link `.lrc` files to specific songs with **Persistent URI Access**, ensuring they load even after device reboots.

### 📁 Smart Library Management
- **Intelligent Scanning**: Uses `MediaStore` with advanced filtering to exclude short audio clips (notifications, voice notes) based on user-defined minimum duration.
- **Metadata Editor**: Edit Title, Artist, and Album tags directly. Changes are stored in a reactive **DataStore** layer, preserving original files while allowing app-level customization.
- **Instant Search**: Blazing-fast fuzzy search across titles, artists, and albums.

### 🛡️ Data & Privacy
- **Backup & Restore**: Export your entire configuration (favorites, custom tags, EQ presets) to a portable JSON file.
- **No Analytics**: Your music habits stay on your device. Period.

---

## 🏗 Architecture & Tech Stack

Snoufly follows **Clean Architecture** principles combined with the **MVVM (Model-View-ViewModel)** pattern for a robust and testable codebase.

### Technical Layers:
- **UI Layer**: 100% Jetpack Compose using Material 3 Design System. Implements a single-activity architecture with a sophisticated navigation graph.
- **Domain/Business Layer**: State management via `StateFlow` and `SharedFlow`. Complex state transformations using `combine` operators to merge library data with user preferences.
- **Service Layer**: A dedicated `MediaSessionService` (`MusicService`) that encapsulates the `ExoPlayer` instance, ensuring playback persistence and system-wide media controls.
- **Persistence Layer**: `Jetpack DataStore (Preferences)` for reactive settings and `GSON` for complex object serialization in backups.

### Core Dependencies:
- **Media3 (ExoPlayer, Session, UI)**: v1.5.1
- **Compose Material 3**: Latest BOM
- **Navigation Compose**: Type-safe navigation implementation
- **Coil**: Optimized image loading with cross-fade and hardware-accelerated bitmap decoding.
- **Kotlin Coroutines**: For non-blocking I/O operations and high-performance concurrency.

---

## 🛠 Development & Extension Guide

### Architecture Rules for Contributors:
1. **Unidirectional Data Flow (UDF)**: UI elements must never modify state directly. All actions go through ViewModels.
2. **Resource Safety**: Always use `WindowInsets` for edge-to-edge layouts.
3. **Media Control**: Interact with playback *only* via the `MediaController` exposed by the `PlaybackViewModel`.
4. **Persistent Scoped Storage**: When accessing external files (like lyrics), use `contentResolver.takePersistableUriPermission` to maintain access across sessions.

### AI Prompting Guide (For IA Developers):
When asking an AI to update Snoufly, use this context:
> "Analyze Snoufly's Media3 implementation in `MusicService.kt` and `PlaybackViewModel.kt`. I want to add [FEATURE] ensuring it syncs with the existing `SettingsManager` DataStore and follows the reactive StateFlow pattern used in `MainViewModel`."

---

## 🗺 Roadmap
- [ ] **Folder View**: Navigate music via physical storage folders.
- [ ] **Sleep Timer**: Schedule playback stop.
- [ ] **Visualizer**: Real-time frequency spectrum visualization.
- [ ] **Android Auto Support**: Full integration for car head units.

---

## 📜 License
Copyright © 2024 Snoufly Team. Built with ❤️ for the Android Community.
