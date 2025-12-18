package com.si.brightcove.sdk.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.brightcove.player.edge.Catalog
import com.brightcove.player.edge.VideoListener
import com.brightcove.player.model.Video
import com.brightcove.player.view.BrightcoveExoPlayerVideoView
import com.si.brightcove.sdk.BrightcoveLiveStreamSDK
import com.si.brightcove.sdk.model.LiveStreamState
import com.si.brightcove.sdk.model.SDKError
import com.si.brightcove.sdk.model.PlayerEvent
import com.si.brightcove.sdk.network.NetworkMonitor
import kotlin.math.pow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.lang.Math.pow
import java.net.UnknownHostException
import java.util.*
import kotlin.math.pow

/**
 * ViewModel for managing live stream state and Brightcove video playback.
 */
class LiveStreamViewModel(application: Application) : AndroidViewModel(application) {
    
    private val networkMonitor = NetworkMonitor(application)
    
    // Store reference to player view for Catalog creation
    @SuppressLint("StaticFieldLeak")
    private var playerView: BrightcoveExoPlayerVideoView? = null
    
    private val _streamState = MutableStateFlow<LiveStreamState>(LiveStreamState.Loading)
    val streamState: StateFlow<LiveStreamState> = _streamState.asStateFlow()
    
    private val _showOverlay = MutableStateFlow(false)
    val showOverlay: StateFlow<Boolean> = _showOverlay.asStateFlow()
    
    private val _video = MutableStateFlow<Video?>(null)
    val video: StateFlow<Video?> = _video.asStateFlow()
    
    private val _playerEvent = MutableStateFlow<PlayerEvent?>(null)
    val playerEvent: StateFlow<PlayerEvent?> = _playerEvent.asStateFlow()
    
    private var retryAttempts = 0
    private var isRetrying = false
    
    /**
     * Set the player view reference for Catalog operations.
     * This should only be called when EventEmitter is confirmed available.
     * 
     * @param view BrightcoveExoPlayerVideoView instance
     * @param eventEmitterReady Whether the EventEmitter has been confirmed available (default: true)
     */
    fun setPlayerView(view: BrightcoveExoPlayerVideoView?, eventEmitterReady: Boolean = true) {
        val wasNull = playerView == null
        playerView = view
        
        if (view != null && wasNull) {
            if (eventEmitterReady) {
                // EventEmitter is confirmed ready, check stream status immediately
                if (BrightcoveLiveStreamSDK.getConfig().debug) {
                    android.util.Log.d("BrightcoveSDK", "Player view set with EventEmitter ready, checking stream status")
                }
                checkLiveStreamStatus()
            } else {
                // EventEmitter not ready yet, wait for it
                if (BrightcoveLiveStreamSDK.getConfig().debug) {
                    android.util.Log.d("BrightcoveSDK", "Player view set but EventEmitter not ready, waiting...")
                }
                viewModelScope.launch {
                    waitForEventEmitterAndCheck()
                }
            }
        } else if (view != null && _streamState.value is LiveStreamState.Loading && eventEmitterReady) {
            // Player view available and in loading state - check status
            checkLiveStreamStatus()
        }
    }
    
    /**
     * Wait for EventEmitter to be available, then check stream status.
     */
    private suspend fun waitForEventEmitterAndCheck() {
        var attempts = 0
        val maxAttempts = 10 // Try for up to 1 second (20 * 50ms) - reduced for faster loading
        
        while (attempts < maxAttempts) {
            val eventEmitter = playerView?.eventEmitter
            if (eventEmitter != null) {
                if (BrightcoveLiveStreamSDK.getConfig().debug) {
                    android.util.Log.d("BrightcoveSDK", "EventEmitter is now available, checking stream status")
                }
                checkLiveStreamStatus()
                return
            }
            
            attempts++
            delay(50) // Wait 50ms before checking again (reduced from 100ms for faster loading)
            
            if (BrightcoveLiveStreamSDK.getConfig().debug && attempts % 5 == 0) {
                android.util.Log.d("BrightcoveSDK", "Waiting for EventEmitter... attempt $attempts/$maxAttempts")
            }
        }
        
        // If EventEmitter still not available after max attempts, try checking anyway
        if (BrightcoveLiveStreamSDK.getConfig().debug) {
            android.util.Log.w("BrightcoveSDK", "EventEmitter still not available after ${maxAttempts} attempts, checking anyway")
        }
        checkLiveStreamStatus()
    }
    
    init {
        // Monitor network connectivity
        viewModelScope.launch {
            networkMonitor.isNetworkAvailable().collectLatest { isAvailable ->
                if (!isAvailable && _streamState.value !is LiveStreamState.Error) {
                    _streamState.value = LiveStreamState.Error(
                        errorMessage = "No internet connection",
                        errorCode = SDKError.NETWORK_ERROR,
                        retryable = true
                    )
                } else if (isAvailable && _streamState.value is LiveStreamState.Error) {
                    // Network restored, retry if in error state
                    if ((_streamState.value as LiveStreamState.Error).retryable) {
                        retry()
                    }
                }
            }
        }
        
        // Check if we should initialize with config state immediately (for config-driven behavior)
        viewModelScope.launch {
            try {
                if (BrightcoveLiveStreamSDK.isInitialized()) {
                    val config = BrightcoveLiveStreamSDK.getConfig()
                    if (!config.useConfig && config.configState.isNotBlank()) {
                        val configStateLower = config.configState.lowercase().trim()
                        if (configStateLower == "prelive") {
                            // Immediately show pre-live content from config
                            _streamState.value = LiveStreamState.PreLive(
                                mediaType = config.configMediaType,
                                mediaUrl = config.configMediaUrl,
                                mediaTitle = config.configMediaTitle,
                                mediaLoop = config.configMediaLoop
                            )
                            if (config.debug) {
                                android.util.Log.d("BrightcoveSDK", "Initialized with preLive state from config")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // SDK not initialized yet, will be handled when setPlayerView is called
                if (BrightcoveLiveStreamSDK.isInitialized() && BrightcoveLiveStreamSDK.getConfig().debug) {
                    android.util.Log.d("BrightcoveSDK", "Could not initialize with config state: ${e.message}")
                }
            }
        }
        
        // Don't check stream status here - wait for player view to be set
        // checkLiveStreamStatus() will be called when playerView is set via setPlayerView()
        startPeriodicCheck()
    }
    
    /**
     * Check if the live stream is available and update state accordingly.
     */
    private fun checkLiveStreamStatus() {
        viewModelScope.launch {
            // Check network availability first
            if (!networkMonitor.isNetworkAvailableNow()) {
                _streamState.value = LiveStreamState.Error(
                    errorMessage = "No internet connection",
                    errorCode = SDKError.NETWORK_ERROR,
                    retryable = true
                )
                return@launch
            }
            
            try {
                val config = BrightcoveLiveStreamSDK.getConfig()
                
                // If using config-driven behavior, check config state first
                if (!config.useConfig && config.configState.isNotBlank()) {
                    val configStateLower = config.configState.lowercase().trim()
                    
                    when (configStateLower) {
                        "prelive","postlive"-> {
                            // Config says pre-live: show configured media immediately
                            if (BrightcoveLiveStreamSDK.getConfig().debug) {
                                android.util.Log.d("BrightcoveSDK", "Config state is preLive - showing configured media")
                            }
                            
                            if (_streamState.value is LiveStreamState.Loading || 
                                _streamState.value is LiveStreamState.PreLive) {
                                _streamState.value = LiveStreamState.PreLive(
                                    mediaType = config.configMediaType,
                                    mediaUrl = config.configMediaUrl,
                                    mediaTitle = config.configMediaTitle,
                                    mediaLoop = config.configMediaLoop
                                )
                            }
                            return@launch // Don't check Brightcove video for preLive state
                        }
                        "live" -> {
                            // Config says live: check Brightcove video
                            if (BrightcoveLiveStreamSDK.getConfig().debug) {
                                android.util.Log.d("BrightcoveSDK", "Config state is live - checking Brightcove video")
                            }
                            // Continue to Brightcove video check below
                        }
//                        "postlive" -> {
//                            // Config says post-live: show error or end state
//                            if (BrightcoveLiveStreamSDK.getConfig().debug) {
//                                android.util.Log.d("BrightcoveSDK", "Config state is postLive")
//                            }
//                            // Handle post-live state if needed
//                            return@launch
//                        }
                        else -> {
                            // Unknown state, continue with normal flow
                            if (BrightcoveLiveStreamSDK.getConfig().debug) {
                                android.util.Log.d("BrightcoveSDK", "Unknown config state: $configStateLower, continuing with normal flow")
                            }
                        }
                    }
                }
                
                // Check Brightcove video (for live state or when not using config)
                val catalog = BrightcoveLiveStreamSDK.getBrightcoveEmitter(playerView)

                if (catalog == null) {
                    // Player view not available yet, wait for it
                    if (BrightcoveLiveStreamSDK.getConfig().debug) {
                        android.util.Log.d("BrightcoveSDK", "Catalog not available - waiting for player view")
                    }
                    // Don't change state - stay in Loading or current state
                    return@launch
                }

                val effectiveVideoId = config.getEffectiveVideoId()
                if (BrightcoveLiveStreamSDK.getConfig().debug) {
                    android.util.Log.d("BrightcoveSDK", "Checking video availability for ID: $effectiveVideoId")
                }

                catalog.findVideoByID(effectiveVideoId, object : VideoListener() {
                    override fun onVideo(video: Video) {
                        if (BrightcoveLiveStreamSDK.getConfig().debug) {
                            android.util.Log.d("BrightcoveSDK", "Video found: ${video.name}, ID: ${video.id}")
                        }
                        
                        _video.value = video
                        retryAttempts = 0 // Reset retry attempts on success
                        
                        // If video is found and available, switch to Live state
                        if (_streamState.value !is LiveStreamState.Live) {
                            _streamState.value = LiveStreamState.Live(
                                title = video.name ?: "Live Stream",
                                description = video.description ?: ""
                            )
                            _playerEvent.value = PlayerEvent.VideoLoaded
                        }
                    }
                    
                    override fun onError(error: String) {
                        retryAttempts = 0 // Reset for video not found (expected before stream starts)
                        
                        if (BrightcoveLiveStreamSDK.getConfig().debug) {
                            android.util.Log.d("BrightcoveSDK", "Video not available: $error")
                        }
                        
                        // Video not found or not available yet, stay in PreLive state
                        // This is expected behavior when stream hasn't started
                        if (_streamState.value is LiveStreamState.Loading) {
                            val config = BrightcoveLiveStreamSDK.getConfig()
                            _streamState.value = LiveStreamState.PreLive(
                                mediaType = config.configMediaType,
                                mediaUrl = config.configMediaUrl,
                                mediaTitle = config.configMediaTitle,
                                mediaLoop = config.configMediaLoop
                            )
                        }
                    }
                })
            } catch (e: Exception) {
                handleError(e)
            }
        }
    }
    
    /**
     * Handle errors with retry logic.
     */
    private fun handleError(e: Exception) {
        val config = BrightcoveLiveStreamSDK.getConfig()
        val errorCode = when (e) {
            is UnknownHostException, is java.net.ConnectException -> SDKError.NETWORK_ERROR
            is IllegalStateException -> SDKError.INITIALIZATION_ERROR
            else -> SDKError.UNKNOWN_ERROR
        }
        
        val errorMessage = when (errorCode) {
            SDKError.NETWORK_ERROR -> "Network error: ${e.message ?: "Unable to connect"}"
            SDKError.INITIALIZATION_ERROR -> "Initialization error: ${e.message ?: "SDK not properly initialized"}"
            else -> "An error occurred: ${e.message ?: "Unknown error"}"
        }
        
        if (config.debug) {
            android.util.Log.e("BrightcoveSDK", errorMessage, e)
        }
        
        val retryable = errorCode == SDKError.NETWORK_ERROR && 
                retryAttempts < config.maxRetryAttempts
        
        _streamState.value = LiveStreamState.Error(
            errorMessage = errorMessage,
            errorCode = errorCode,
            retryable = retryable
        )
        
        // Auto-retry if enabled
        if (config.autoRetryOnError && retryable && !isRetrying) {
            retry()
        }
    }
    
    /**
     * Start periodic checking for live stream availability.
     */
    private fun startPeriodicCheck() {
        viewModelScope.launch {
            while (true) {
                val config = BrightcoveLiveStreamSDK.getConfig()
                val pollingInterval = config.getEffectivePollingInterval()
                delay(pollingInterval)
                android.util.Log.d("BrightcoveSDK", "Polling Interval: $pollingInterval")
                when (val currentState = _streamState.value) {
                    is LiveStreamState.PreLive, is LiveStreamState.Loading -> {
                        checkLiveStreamStatus()
                    }
                    is LiveStreamState.Error -> {
                        // Continue checking if retryable
                        if (currentState.retryable && networkMonitor.isNetworkAvailableNow()) {
                            checkLiveStreamStatus()
                        }
                    }
                    is LiveStreamState.Live -> {
                        // Only stop checking if not using config (state=true)
                        // If using config (state=false), continue checking to update state
                        if (config.useConfig) {
                            break
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Retry loading the stream.
     */
    fun retry() {
        if (isRetrying) return
        
        val config = BrightcoveLiveStreamSDK.getConfig()
        if (retryAttempts >= config.maxRetryAttempts) {
            _streamState.value = LiveStreamState.Error(
                errorMessage = "Maximum retry attempts reached",
                errorCode = SDKError.NETWORK_ERROR,
                retryable = false
            )
            return
        }
        
        isRetrying = true
        retryAttempts++
        
        viewModelScope.launch {
            // Exponential backoff
            val backoffDelay = (1000 * config.retryBackoffMultiplier.pow((retryAttempts - 1).toDouble())).toLong()
            delay(backoffDelay)
            
            checkLiveStreamStatus()
            isRetrying = false
        }
    }
    
    /**
     * Toggle the overlay UI visibility.
     */
    fun toggleOverlay() {
        _showOverlay.value = !_showOverlay.value
    }
    
    /**
     * Get the current video for playback.
     */
    fun getVideo(): Video? = _video.value
    
    /**
     * Emit a player event.
     */
    fun emitPlayerEvent(event: PlayerEvent) {
        _playerEvent.value = event
    }
}

