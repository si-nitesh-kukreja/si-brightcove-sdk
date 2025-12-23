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
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    VideoPlayerContent(videoUrl, loop, modifier)
}

@Composable
private fun VideoPlayerContent(
    videoUrl: String,
    loop: Boolean,
    modifier: Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // State to track if video should be shown (hide on errors)
    var showVideo by remember { mutableStateOf(true) }
    
    // State to track if an error occurred (used to trigger state updates)
    var hasError by remember { mutableStateOf(false) }

    // State to track optimal scaling mode based on video aspect ratio
    var optimalScalingMode by remember { mutableStateOf(C.VIDEO_SCALING_MODE_SCALE_TO_FIT) }
    
    // State to track PlayerView resize mode based on video aspect ratio
    var playerViewResizeMode by remember { mutableStateOf(com.google.android.exoplayer2.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT) }

    // Debug logging for creation
    LaunchedEffect(Unit) {
        Logger.d("VideoPlayer: Composable CREATED with videoUrl=$videoUrl, loop=$loop")
    }

    DisposableEffect(Unit) {
        Logger.d("VideoPlayer: Composable entered composition")
        onDispose {
            Logger.d("VideoPlayer: Composable DISPOSED - stopping video for URL: $videoUrl")
        }
    }

    // Handle error state - hide video and stop player
    LaunchedEffect(hasError) {
        if (hasError && showVideo) {
            // Error occurred but showVideo hasn't been updated yet - force update
            showVideo = false
        }
    }
    
    // Don't render anything if video should be hidden due to errors
    if (!showVideo) {
        Logger.d("VideoPlayer: Video hidden due to errors for URL: $videoUrl")
        return
    }

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

                // Set initial scaling mode (will be updated dynamically based on aspect ratio)
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT

                // Add error listener for codec and source issues
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        Logger.e("ExoPlayer error: ${error.message}", error)
                        
                        // Check if it's a source/IO error (network, invalid URL, etc.)
                        val isSourceError = when (error.errorCode) {
                            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
                            PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
                            PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED,
                            PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
                            PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> true
                            else -> {
                                // Check error message for source-related keywords
                                val errorMessage = error.message?.lowercase() ?: ""
                                errorMessage.contains("source") || 
                                errorMessage.contains("network") ||
                                errorMessage.contains("connection") ||
                                errorMessage.contains("timeout") ||
                                errorMessage.contains("http") ||
                                errorMessage.contains("io")
                            }
                        }
                        
                        when (error.errorCode) {
                            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> {
                                Logger.w("Hardware decoder failed - software fallback should handle this")
                            }
                            PlaybackException.ERROR_CODE_DECODING_FAILED -> {
                                Logger.e("Decoding failed - video format not supported by hardware or software decoder")
                                // Stop playback and hide video on decoding errors
                                try {
                                    stop()
                                    clearMediaItems()
                                } catch (e: Exception) {
                                    Logger.w("Error stopping player on decode failure", e)
                                }
                                hasError = true
                                showVideo = false
                            }
                            else -> {
                                if (isSourceError) {
                                    Logger.e("Source/IO error detected (code: ${error.errorCode}) - hiding video player for URL: $videoUrl")
                                    Logger.e("Error details: ${error.message}")
                                    // Stop playback and hide video on source errors
                                    try {
                                        stop()
                                        clearMediaItems()
                                    } catch (e: Exception) {
                                        Logger.w("Error stopping player on source error", e)
                                    }
                                    hasError = true
                                    showVideo = false
                                } else {
                                    Logger.e("Other playback error: ${error.errorCode}, message: ${error.message}")
                                    // For unknown errors, also stop and hide video to prevent crashes
                                    try {
                                        stop()
                                        clearMediaItems()
                                    } catch (e: Exception) {
                                        Logger.w("Error stopping player on unknown error", e)
                                    }
                                    hasError = true
                                    showVideo = false
                                }
                            }
                        }
                    }

                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        Logger.d("Video size: ${videoSize.width}x${videoSize.height}")

                        // Calculate aspect ratio and determine optimal scaling
                        val videoAspectRatio = videoSize.width.toFloat() / videoSize.height.toFloat()

                        // Determine optimal scaling mode and resize mode based on aspect ratio
                        val (scalingMode, resizeMode) = when {
                            // Ultra-wide/landscape videos (21:9, 16:9, etc.)
                            videoAspectRatio > 1.8f -> {
                                Logger.d("Ultra-wide video detected (AR: $videoAspectRatio), using SCALE_TO_FIT_WITH_CROPPING")
                                Pair(C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING, 
                                     com.google.android.exoplayer2.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM)
                            }
                            // Standard landscape (4:3, 16:10, etc.)
                            videoAspectRatio > 1.2f -> {
                                Logger.d("Landscape video detected (AR: $videoAspectRatio), using SCALE_TO_FIT")
                                Pair(C.VIDEO_SCALING_MODE_SCALE_TO_FIT,
                                     com.google.android.exoplayer2.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT)
                            }
                            // Square videos (1:1)
                            videoAspectRatio in 0.9f..1.1f -> {
                                Logger.d("Square video detected (AR: $videoAspectRatio), using SCALE_TO_FIT")
                                Pair(C.VIDEO_SCALING_MODE_SCALE_TO_FIT,
                                     com.google.android.exoplayer2.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT)
                            }
                            // Portrait videos (4:5, 9:16, etc.)
                            videoAspectRatio < 0.8f -> {
                                Logger.d("Portrait video detected (AR: $videoAspectRatio), using SCALE_TO_FIT")
                                Pair(C.VIDEO_SCALING_MODE_SCALE_TO_FIT,
                                     com.google.android.exoplayer2.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT)
                            }
                            // Near-square or slightly rectangular
                            else -> {
                                Logger.d("Standard video detected (AR: $videoAspectRatio), using SCALE_TO_FIT")
                                Pair(C.VIDEO_SCALING_MODE_SCALE_TO_FIT,
                                     com.google.android.exoplayer2.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT)
                            }
                        }

                        // Update state variables (will be applied via LaunchedEffect)
                        optimalScalingMode = scalingMode
                        playerViewResizeMode = resizeMode
                        
                        // Apply the scaling mode immediately to the player instance
                        // Inside apply block, 'this' refers to the ExoPlayer instance
                        videoScalingMode = scalingMode
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

    // Handle video URL changes
    LaunchedEffect(videoUrl) {
        try {
            // Basic URL validation
            if (videoUrl.isBlank()) {
                Logger.e("Video URL is blank, hiding video player")
                showVideo = false
                return@LaunchedEffect
            }

            val uri = Uri.parse(videoUrl)
            if (uri.scheme == null || uri.host == null) {
                Logger.e("Invalid video URL format: $videoUrl, hiding video player")
                showVideo = false
                return@LaunchedEffect
            }

            val mediaItem = MediaItem.fromUri(uri)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
            Logger.d("Video URL changed, updating media item: $videoUrl")
        } catch (e: Exception) {
            Logger.e("Error updating video URL: ${e.message}, hiding video player", e)
            showVideo = false
        }
    }

    // Handle loop parameter changes
    LaunchedEffect(loop) {
        exoPlayer.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }
    
    // Apply scaling mode changes when optimalScalingMode state changes
    LaunchedEffect(optimalScalingMode) {
        exoPlayer.videoScalingMode = optimalScalingMode
        Logger.d("VideoPlayer: Applied scaling mode: $optimalScalingMode")
    }

    // Handle lifecycle events and disposal
    DisposableEffect(Unit) {
        onDispose {
            // Stop and release the video when this composable is disposed
            // This happens when switching states (e.g., from PreLive VIDEO to Live)
            try {
                Logger.d("VideoPlayer disposing - stopping video playback for URL: $videoUrl")
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                Logger.d("VideoPlayer disposed successfully")
            } catch (e: Exception) {
                Logger.e("Error stopping video on dispose: ${e.message}")
            }
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
            // Don't release here since the outer DisposableEffect handles disposal
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

                // Configure video scaling dynamically based on video aspect ratio
                resizeMode = playerViewResizeMode

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
            // Update resize mode dynamically when it changes
            playerView.resizeMode = playerViewResizeMode
        },
        modifier = modifier
    )
}
