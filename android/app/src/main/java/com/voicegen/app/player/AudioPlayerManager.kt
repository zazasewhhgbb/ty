package com.voicegen.app.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

data class PlaybackState(
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val currentFile: String? = null,
)

/**
 * Thin wrapper around ExoPlayer. ExoPlayer streams from disk/network rather
 * than loading the whole file into memory, which is what spec sections 19
 * and 32 ask for with large generated audiobooks.
 */
class AudioPlayerManager(context: Context) {
    private val player = ExoPlayer.Builder(context).build()

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.value = _state.value.copy(isPlaying = isPlaying)
            }
        })
    }

    fun playFile(file: File) {
        player.setMediaItem(MediaItem.fromUri(file.toURI().toString()))
        player.prepare()
        player.play()
        _state.value = _state.value.copy(currentFile = file.absolutePath, isPlaying = true)
    }

    fun pause() = player.pause()
    fun resume() = player.play()
    fun seekTo(positionMs: Long) = player.seekTo(positionMs)

    fun currentPositionMs(): Long = player.currentPosition
    fun durationMs(): Long = player.duration.coerceAtLeast(0)

    fun release() = player.release()
}
