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
                        eventType = EventType.camera,
                        environment = Environment.nonProd,
                        modifier = Modifier.fillMaxSize(),
                        locale = "en"
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
                android.util.Log.d("BrightcoveSDK", "State: Loading stream...")
            }
            is LiveStreamState.PreLive -> {
                android.util.Log.d("BrightcoveSDK", "State: Pre-Live - Scheduled: ${state.mediaTitle}")
            }
            is LiveStreamState.Live -> {
                android.util.Log.d("BrightcoveSDK", "State: Live - Title: ${state.title} Description : ${state.description}")
            }
            is LiveStreamState.Error -> {
                android.util.Log.e("BrightcoveSDK", "State: Error - ${state.errorMessage} (${state.errorCode})")
            }
        }
    }
    
    /**
     * Handle errors.
     */
    private fun handleError(errorMessage: String, errorCode: SDKError) {
        android.util.Log.e("BrightcoveSDK", "Error: $errorMessage (Code: $errorCode)")
        
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
                android.util.Log.d("BrightcoveSDK", "Player Event: Playback Started")
            }
            is PlayerEvent.PlaybackPaused -> {
                android.util.Log.d("BrightcoveSDK", "Player Event: Playback Paused")
            }
            is PlayerEvent.PlaybackResumed -> {
                android.util.Log.d("BrightcoveSDK", "Player Event: Playback Resumed")
            }
            is PlayerEvent.Buffering -> {
                android.util.Log.d("BrightcoveSDK", "Player Event: Buffering")
            }
            is PlayerEvent.BufferingComplete -> {
                android.util.Log.d("BrightcoveSDK", "Player Event: Buffering Complete")
            }
            is PlayerEvent.VideoLoaded -> {
                android.util.Log.d("BrightcoveSDK", "Player Event: Video Loaded")
            }
            is PlayerEvent.PlaybackCompleted -> {
                android.util.Log.d("BrightcoveSDK", "Player Event: Playback Completed")
            }
            is PlayerEvent.PlaybackError -> {
                android.util.Log.e("BrightcoveSDK", "Player Event: Playback Error - ${event.errorMessage}")
            }
        }
    }
}

