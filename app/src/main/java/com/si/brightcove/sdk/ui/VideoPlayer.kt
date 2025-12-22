package com.si.brightcove.sdk.ui

import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.DefaultRenderersFactory
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.video.VideoSize
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.PlaybackException

/**
 * Simple ExoPlayer composable for video preview in pre-live state.
 * This handles basic video playback with optional looping.
 *
 * @param videoUrl URL of the video to play
 * @param loop Whether to loop the video playback
 * @param modifier Modifier for the composable
 */
@Composable
fun VideoPlayer(
    videoUrl: String,
    loop: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Create ExoPlayer instance optimized for performance
    val exoPlayer = remember {
        // Force software decoding by setting system property for ExoPlayer
        try {
            // Set properties to force software decoding
            System.setProperty("media.stagefright.legacyencoder", "true")
            System.setProperty("media.stagefright.legacydecoder", "true")
            Logger.d("Forced software decoding properties set")
        } catch (e: Exception) {
            Logger.w("Could not set software decoding properties", e)
        }

        val renderersFactory = DefaultRenderersFactory(context).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
            setEnableDecoderFallback(true)
        }

        // Configure track selector with aggressive resolution limiting for compatibility
        val trackSelector = DefaultTrackSelector(context).apply {
            val parameters = buildUponParameters()
                .setMaxVideoSize(1280, 720) // More conservative: limit to 720p to avoid 4K codec issues
                .setViewportSizeToPhysicalDisplaySize(context, true) // Adaptive to screen size
                .setForceLowestBitrate(false) // Allow quality selection
                .build()
            setParameters(parameters)
        }

        // Configure load control optimized for software decoding (very conservative buffers)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                500,  // min buffer - very small for software decoding
                1500, // max buffer - reduced for CPU efficiency
                250,  // buffer for playback - minimal for smooth playback
                500   // buffer after rebuffer - fast recovery
            )
            .setPrioritizeTimeOverSizeThresholds(true) // Prioritize smooth playback
            .setBackBuffer(0, false) // Disable back buffer to save memory
            .build()

        ExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .build()
            .apply {
                val mediaItem = MediaItem.fromUri(Uri.parse(videoUrl))
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = true
                repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF

                // Set video scaling mode optimized for software decoding performance
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT

                // Add error listener for codec issues
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        Logger.e("ExoPlayer error: ${error.message}", error)
                        when (error.errorCode) {
                            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> {
                                Logger.w("Hardware decoder failed - software fallback should handle this")
                            }
                            PlaybackException.ERROR_CODE_DECODING_FAILED -> {
                                Logger.e("Decoding failed - video format not supported by hardware or software decoder")
                            }
                            else -> {
                                Logger.e("Other playback error: ${error.errorCode}")
                            }
                        }
                    }

                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        Logger.d("Video size: ${videoSize.width}x${videoSize.height}")
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_BUFFERING -> {
                                Logger.d("Buffering...")
                            }
                            Player.STATE_READY -> {
                                Logger.d("Ready to play")
                            }
                            Player.STATE_ENDED -> {
                                Logger.d("Playback ended")
                            }
                        }
                    }
                })
            }
    }

    // Handle lifecycle events to pause/resume playback
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    exoPlayer.pause()
                }
                Lifecycle.Event.ON_RESUME -> {
                    exoPlayer.play()
                }
                Lifecycle.Event.ON_DESTROY -> {
                    exoPlayer.release()
                }
                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    // AndroidView to wrap PlayerView
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false // Hide controls for preview

                // Reduce buffering indicator for better performance with software decoding
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)

                // Configure video scaling for performance
                resizeMode = com.google.android.exoplayer2.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT

                // Set background color to black for better video display
                setBackgroundColor(android.graphics.Color.BLACK)

                // Set shutter background to black
                setShutterBackgroundColor(android.graphics.Color.BLACK)

                // Performance optimizations for software decoding
                setKeepContentOnPlayerReset(false) // Reduce memory usage
                useArtwork = false // Disable artwork for better performance
            }
        },
        update = { playerView ->
            playerView.player = exoPlayer
        },
        modifier = modifier
    )
}
