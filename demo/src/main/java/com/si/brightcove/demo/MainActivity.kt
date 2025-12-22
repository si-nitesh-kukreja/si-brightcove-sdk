package com.si.brightcove.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.si.brightcove.sdk.model.Environment
import com.si.brightcove.sdk.model.EventType
import com.si.brightcove.sdk.model.LiveStreamState
import com.si.brightcove.sdk.model.PlayerEvent
import com.si.brightcove.sdk.model.SDKError
import com.si.brightcove.sdk.ui.LiveStreamScreen
import com.si.brightcove.sdk.ui.Logger

/**
 * Main Activity for the demo app.
 * Demonstrates how to use the Brightcove Live Streaming SDK.
 */
class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Live Stream Screen - Main SDK entry point
                    LiveStreamScreen(
                        eventType = EventType.mobile,
                        environment = Environment.prod,
                        modifier = Modifier.fillMaxSize(),
                        locale = "kl"
                    )
                }
            }
        }
    }
    
    /**
     * Handle stream state changes.
     */
    private fun handleStateChange(state: LiveStreamState) {
        when (state) {
            is LiveStreamState.Loading -> {
                Logger.d("State: Loading stream...", "DemoApp")
            }
            is LiveStreamState.PreLive -> {
                Logger.d("State: Pre-Live - Scheduled: ${state.mediaTitle}", "DemoApp")
            }
            is LiveStreamState.Live -> {
                Logger.d("State: Live - Title: ${state.title} Description : ${state.description}", "DemoApp")
            }
            is LiveStreamState.Error -> {
                Logger.e("State: Error - ${state.errorMessage} (${state.errorCode})", "DemoApp")
            }
        }
    }
    
    /**
     * Handle errors.
     */
    private fun handleError(errorMessage: String, errorCode: SDKError) {
        Logger.e("Error: $errorMessage (Code: $errorCode)", "DemoApp")
        
        // You can show a toast, snackbar, or custom error UI here
        when (errorCode) {
            SDKError.NETWORK_ERROR -> {
                // Handle network errors
            }
            SDKError.VIDEO_NOT_FOUND -> {
                // Handle video not found
            }
            SDKError.PLAYBACK_ERROR -> {
                // Handle playback errors
            }
            SDKError.INITIALIZATION_ERROR -> {
                // Handle initialization errors
            }
            SDKError.UNKNOWN_ERROR -> {
                // Handle unknown errors
            }
        }
    }
    
    /**
     * Handle player events.
     */
    private fun handlePlayerEvent(event: PlayerEvent) {
        when (event) {
            is PlayerEvent.PlaybackStarted -> {
                Logger.d("Player Event: Playback Started", "DemoApp")
            }
            is PlayerEvent.PlaybackPaused -> {
                Logger.d("Player Event: Playback Paused", "DemoApp")
            }
            is PlayerEvent.PlaybackResumed -> {
                Logger.d("Player Event: Playback Resumed", "DemoApp")
            }
            is PlayerEvent.Buffering -> {
                Logger.d("Player Event: Buffering", "DemoApp")
            }
            is PlayerEvent.BufferingComplete -> {
                Logger.d("Player Event: Buffering Complete", "DemoApp")
            }
            is PlayerEvent.VideoLoaded -> {
                Logger.d("Player Event: Video Loaded", "DemoApp")
            }
            is PlayerEvent.PlaybackCompleted -> {
                Logger.d("Player Event: Playback Completed", "DemoApp")
            }
            is PlayerEvent.PlaybackError -> {
                Logger.e("Player Event: Playback Error - ${event.errorMessage}", "DemoApp")
            }
        }
    }
}

