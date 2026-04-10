package com.kyrn.snoufly.playback

import android.content.Intent
import android.media.audiofx.Equalizer
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.kyrn.snoufly.data.MusicRepository
import com.kyrn.snoufly.data.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MusicService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var equalizer: Equalizer? = null
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

        // Sistema inteligente de control de audio
        serviceScope.launch {
            combine(
                settingsManager.eqEnabledFlow,
                settingsManager.playbackSpeedFlow,
                settingsManager.playbackPitchFlow
            ) { enabled, speed, pitch ->
                Triple(enabled, speed, pitch)
            }.collect { (enabled, speed, pitch) ->
                if (enabled) {
                    // Aplicar valores con protección de rango (Clamping) para evitar bugs de audio
                    val safeSpeed = speed.coerceIn(0.5f, 2.0f)
                    val safePitch = pitch.coerceIn(0.5f, 2.0f)
                    player.playbackParameters = PlaybackParameters(safeSpeed, safePitch)
                    equalizer?.enabled = true
                } else {
                    // Si el motor está desactivado, resetear a valores normales inmediatamente
                    player.playbackParameters = PlaybackParameters(1.0f, 1.0f)
                    equalizer?.enabled = false
                }
            }
        }

        // Observar bandas del ecualizador
        serviceScope.launch {
            settingsManager.eqBandsFlow.collect { bands ->
                applyBands(bands)
            }
        }

        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                    initEqualizer(audioSessionId)
                }
            }
        })
    }

    private fun initEqualizer(audioSessionId: Int) {
        try {
            equalizer?.release()
            equalizer = Equalizer(0, audioSessionId).apply {
                serviceScope.launch {
                    val enabled = settingsManager.eqEnabledFlow.first()
                    this@apply.enabled = enabled
                    val bands = settingsManager.eqBandsFlow.first()
                    applyBands(bands)
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun applyBands(bands: List<Int>) {
        val eq = equalizer ?: return
        if (bands.isEmpty()) return
        try {
            val numBands = eq.numberOfBands.toInt()
            for (i in 0 until numBands) {
                if (i < bands.size) {
                    eq.setBandLevel(i.toShort(), bands[i].toShort())
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player != null && (!player.playWhenReady || player.mediaItemCount == 0)) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        equalizer?.release()
        equalizer = null
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
