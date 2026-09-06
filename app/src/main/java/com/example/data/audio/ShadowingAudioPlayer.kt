package com.example.data.audio

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class ShadowingAudioPlayer(private val context: Context) {

    private var exoPlayer: ExoPlayer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackProgress = MutableStateFlow(0f) // 0.0 to 1.0 within the segment
    val playbackProgress: StateFlow<Float> = _playbackProgress.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private var loopStartMs: Long = 0L
    private var loopEndMs: Long = 0L
    private var isLoopEnabled: Boolean = false
    private var playbackSpeed: Float = 1.0f

    private var progressJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    init {
        mainHandler.post {
            initializePlayer()
        }
    }

    private fun initializePlayer() {
        if (exoPlayer != null) return
        try {
            exoPlayer = ExoPlayer.Builder(context).build().apply {
                repeatMode = Player.REPEAT_MODE_OFF
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        _isPlaying.value = playing
                        if (playing) {
                            startProgressTracking()
                        } else {
                            stopProgressTracking()
                        }
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_ENDED) {
                            if (isLoopEnabled) {
                                seekTo(loopStartMs)
                                play()
                            } else {
                                _isPlaying.value = false
                                stopProgressTracking()
                            }
                        }
                    }
                })
            }
            Log.d("ShadowingAudioPlayer", "ExoPlayer initialized successfully")
        } catch (e: Exception) {
            Log.e("ShadowingAudioPlayer", "Failed to initialize ExoPlayer", e)
        }
    }

    fun prepare(audioPath: String) {
        mainHandler.post {
            initializePlayer()
            exoPlayer?.let { player ->
                try {
                    player.stop()
                    val mediaItem = MediaItem.fromUri("file://$audioPath")
                    player.setMediaItem(mediaItem)
                    player.prepare()
                    player.playbackParameters = PlaybackParameters(playbackSpeed)
                    Log.d("ShadowingAudioPlayer", "Prepared media from: $audioPath")
                } catch (e: Exception) {
                    Log.e("ShadowingAudioPlayer", "Error preparing audio source", e)
                }
            }
        }
    }

    fun setLoopRange(startTimeMs: Long, endTimeMs: Long, enableLoop: Boolean = true) {
        loopStartMs = startTimeMs
        loopEndMs = endTimeMs
        isLoopEnabled = enableLoop
        Log.d("ShadowingAudioPlayer", "Loop set to: $startTimeMs ms -> $endTimeMs ms, loopEnabled: $enableLoop")
        
        mainHandler.post {
            exoPlayer?.let { player ->
                val current = player.currentPosition
                if (current < startTimeMs || current > endTimeMs) {
                    player.seekTo(startTimeMs)
                }
            }
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        playbackSpeed = speed
        mainHandler.post {
            exoPlayer?.playbackParameters = PlaybackParameters(speed)
        }
    }

    fun play() {
        mainHandler.post {
            initializePlayer()
            exoPlayer?.let { player ->
                if (isLoopEnabled && (player.currentPosition < loopStartMs || player.currentPosition >= loopEndMs)) {
                    player.seekTo(loopStartMs)
                }
                player.play()
            }
        }
    }

    fun pause() {
        mainHandler.post {
            exoPlayer?.pause()
        }
    }

    fun stop() {
        mainHandler.post {
            exoPlayer?.stop()
            _isPlaying.value = false
            stopProgressTracking()
        }
    }

    fun seekTo(positionMs: Long) {
        mainHandler.post {
            exoPlayer?.seekTo(positionMs)
        }
    }

    fun release() {
        mainHandler.post {
            stopProgressTracking()
            exoPlayer?.release()
            exoPlayer = null
            Log.d("ShadowingAudioPlayer", "ExoPlayer released")
        }
    }

    private fun startProgressTracking() {
        progressJob?.cancel()
        progressJob = coroutineScope.launch {
            while (true) {
                exoPlayer?.let { player ->
                    val currentPos = player.currentPosition
                    _currentPositionMs.value = currentPos
                    
                    if (isLoopEnabled && loopEndMs > loopStartMs) {
                        val duration = (loopEndMs - loopStartMs).toFloat()
                        val progress = if (duration > 0) {
                            ((currentPos - loopStartMs).toFloat() / duration).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                        _playbackProgress.value = progress

                        // Loop trigger checking
                        if (currentPos >= loopEndMs) {
                            player.seekTo(loopStartMs)
                        }
                    } else {
                        val duration = player.duration
                        val progress = if (duration > 0) {
                            (currentPos.toFloat() / duration).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                        _playbackProgress.value = progress
                    }
                }
                delay(30) // Update progress around 33fps for smooth visualization
            }
        }
    }

    private fun stopProgressTracking() {
        progressJob?.cancel()
        progressJob = null
    }
}
