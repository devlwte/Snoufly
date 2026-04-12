package com.kyrn.snoufly.playback

import android.content.Intent
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.kyrn.snoufly.data.SettingsManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MusicService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var settingsManager: SettingsManager

    override fun onCreate() {
        super.onCreate()
        settingsManager = SettingsManager(applicationContext)
        
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        mediaSession = MediaSession.Builder(this, player).build()

        // Inicialización inmediata si ya hay un ID de sesión
        val initialSessionId = player.audioSessionId
        if (initialSessionId != C.AUDIO_SESSION_ID_UNSET && initialSessionId != 0) {
            relinkHifiEngine(initialSessionId)
        }

        // MONITOR HI-FI v5.1: Aplicación instantánea de parámetros
        serviceScope.launch {
            combine(
                settingsManager.eqEnabledFlow,
                settingsManager.playbackSpeedFlow,
                settingsManager.playbackPitchFlow
            ) { enabled, speed, pitch -> Triple(enabled, speed, pitch) }
            .collect { (enabled, speed, pitch) ->
                val playerInstance = mediaSession?.player ?: return@collect
                try {
                    if (enabled) {
                        val safeSpeed = speed.coerceIn(0.1f, 2.0f)
                        val safePitch = pitch.coerceIn(0.1f, 2.0f)
                        playerInstance.playbackParameters = PlaybackParameters(safeSpeed, safePitch)
                        applyHifiEffects(true)
                    } else {
                        playerInstance.playbackParameters = PlaybackParameters.DEFAULT
                        applyHifiEffects(false)
                    }
                } catch (e: Exception) { }
            }
        }

        // Sincronización de bandas en tiempo real
        serviceScope.launch {
            settingsManager.eqBandsFlow.collect { bands ->
                applyBandsProfessional(bands)
            }
        }

        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (audioSessionId != C.AUDIO_SESSION_ID_UNSET && audioSessionId != 0) {
                    relinkHifiEngine(audioSessionId)
                }
            }
        })
    }

    private fun relinkHifiEngine(audioSessionId: Int) {
        try {
            equalizer?.release()
            bassBoost?.release()
            loudnessEnhancer?.release()

            // Usamos prioridad 0 para máxima compatibilidad
            equalizer = Equalizer(0, audioSessionId)
            bassBoost = BassBoost(0, audioSessionId)
            loudnessEnhancer = LoudnessEnhancer(audioSessionId)

            serviceScope.launch {
                val enabled = settingsManager.eqEnabledFlow.first()
                val bands = settingsManager.eqBandsFlow.first()
                applyHifiEffects(enabled)
                applyBandsProfessional(bands)
            }
        } catch (e: Exception) { }
    }

    private fun applyHifiEffects(enabled: Boolean) {
        try {
            equalizer?.enabled = enabled
            bassBoost?.enabled = enabled
            loudnessEnhancer?.enabled = enabled
            if (enabled) {
                // Graves y ganancia perceptiva
                bassBoost?.setStrength(850.toShort()) 
                loudnessEnhancer?.setTargetGain(600) // ~6dB de boost
            }
        } catch (e: Exception) {}
    }

    private fun applyBandsProfessional(bands: List<Int>) {
        val eq = equalizer ?: return
        val safeBands = if (bands.isEmpty()) List(5) { 0 } else bands
        try {
            val numBands = eq.numberOfBands.toInt()
            for (i in 0 until numBands) {
                val level = if (i < safeBands.size) safeBands[i] else 0
                val clampedLevel = level.coerceIn(-1500, 1500)
                eq.setBandLevel(i.toShort(), clampedLevel.toShort())
            }
        } catch (e: Exception) {}
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player != null && (!player.playWhenReady || player.mediaItemCount == 0)) stopSelf()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        equalizer?.release()
        bassBoost?.release()
        loudnessEnhancer?.release()
        mediaSession?.run { player.release(); release() }
        super.onDestroy()
    }
}
