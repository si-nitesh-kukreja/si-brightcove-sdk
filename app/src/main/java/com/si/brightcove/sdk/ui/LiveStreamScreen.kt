package com.si.brightcove.sdk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.ImageLoader
import coil.request.SuccessResult
import android.graphics.drawable.BitmapDrawable
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelProvider
import com.si.brightcove.sdk.BrightcoveLiveStreamSDK
import com.si.brightcove.sdk.viewmodel.LiveStreamViewModelFactory
import com.si.brightcove.sdk.model.EventType
import com.si.brightcove.sdk.model.Environment
import com.si.brightcove.sdk.BuildConfig
import com.si.brightcove.sdk.model.LiveStreamState
import com.si.brightcove.sdk.model.MediaType
import com.si.brightcove.sdk.model.PlayerEvent
import com.si.brightcove.sdk.model.SDKError
import com.si.brightcove.sdk.player.BrightcovePlayerView
import com.si.brightcove.sdk.viewmodel.LiveStreamViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rememberDominantColor

/**
 * Main public UI entry point for the Live Streaming SDK.
 *
 * This is the single composable that parent apps should use to display live streaming.
 *
 * @param onClose Optional callback when user taps close/back button
 * @param onStateChanged Optional callback when the stream state changes
 * @param onError Optional callback when an error occurs
 * @param onPlayerEvent Optional callback for player events (playback, buffering, etc.)
 * @param preLiveImageUrl URL for the preview image shown before stream starts
 * @param preLiveScheduledTime Date/time when the live stream is scheduled to start
 * @param eventType Required event type (MOBILE/CAMERA)
 * @param environment Required environment (PROD/NON_PROD)
 * @param locale Required locale identifier (e.g., "en", "hi", "it") for configuration
 * @param state Configuration behavior toggle: true = existing behavior, false = config-driven behavior
 * @param accountId Optional override for Brightcove Account ID
 * @param policyKey Optional override for Brightcove Policy Key
 * @param videoId Optional Brightcove Video ID for the stream
 * @param liveTitle Title shown during live stream
 * @param liveDescription Description shown during live stream
 * @param showCloseButton Whether to show the close/back button (default: true)
 * @param showPlayerControls Whether to show native Brightcove player controls (default: true)
 * @param errorRetryText Custom text for retry button in error state (default: "Retry")
 * @param loadingText Custom text for loading indicator (default: "Loading...")
 * @param debug Optional debug flag (default: BuildConfig.DEBUG)
 * @param modifier Modifier for the composable
 */
@Composable
fun LiveStreamScreen(
    eventType: EventType,
    environment: Environment,
    locale: String,
    debug: Boolean = BuildConfig.DEBUG,
    onClose: (() -> Unit)? = null,
    onStateChanged: ((LiveStreamState) -> Unit)? = null,
    onError: ((String, SDKError) -> Unit)? = null,
    onPlayerEvent: ((PlayerEvent) -> Unit)? = null,
    liveTitle: String = "",
    liveDescription: String = "",
    showPlayerControls: Boolean = false,
    modifier: Modifier
) {
    val context = LocalContext.current
    val accountId: String? = null
    val policyKey: String? = null
    val videoId = ""
    val errorRetryText = "Retry"
    val showCloseButton = false
    val loadingText = "Loading..."
    val state = false
    
    // Ensure SDK is initialized before creating the ViewModel to avoid race conditions.
    if (!BrightcoveLiveStreamSDK.isInitialized()) {
        BrightcoveLiveStreamSDK.initialize(
            context = context,
            eventType = eventType,
            environment = environment,
            locale = locale,
            state = state,
            videoId = videoId,
            accountId = accountId,
            policyKey = policyKey,
            debug = debug
        )
    }
    
    val viewModel: LiveStreamViewModel = viewModel(
        factory = LiveStreamViewModelFactory(
            context.applicationContext as android.app.Application
        )
    )
    val streamState by viewModel.streamState.collectAsState()
    val showOverlay by viewModel.showOverlay.collectAsState()
    val playerEvent by viewModel.playerEvent.collectAsState()
    
    // Track pageview analytics and notify parent app of state changes
    LaunchedEffect(streamState) {
        val currentState = streamState
        when (currentState) {
            is LiveStreamState.PreLive -> {
                BrightcoveLiveStreamSDK.getAnalyticsManager()?.trackPageView("pre_live_screen")
            }
            is LiveStreamState.Live -> {
                BrightcoveLiveStreamSDK.getAnalyticsManager()?.trackPageView("live_stream_screen")
            }
            is LiveStreamState.Error -> {
                BrightcoveLiveStreamSDK.getAnalyticsManager()?.trackPageView("error_screen")
                onError?.invoke(currentState.errorMessage, currentState.errorCode)
            }
            is LiveStreamState.Loading -> {
                // Loading state - no analytics needed
            }
        }
        // Notify parent app of state change (optional callback)
        onStateChanged?.invoke(currentState)
    }
    
    // Handle player events
    LaunchedEffect(playerEvent) {
        playerEvent?.let { event ->
            onPlayerEvent?.invoke(event)
        }
    }
    
    // Note: We don't need to create player view early anymore since we use standalone EventEmitter
    // for Catalog creation. The player view will be created when Live state is reached.
    
    val currentState = streamState
    when (currentState) {
        is LiveStreamState.Loading -> {
            LoadingContent(
                liveTitle = liveTitle,
                liveDescription = liveDescription,
                loadingText = loadingText,
                modifier = modifier
            )
        }
        is LiveStreamState.PreLive -> {
            PreLiveContent(
                mediaType = currentState.mediaType,
                mediaUrl = currentState.mediaUrl,
                mediaTitle = currentState.mediaTitle,
                mediaLoop = currentState.mediaLoop,
                modifier = modifier
            )
        }
        is LiveStreamState.Live -> {
            LiveStreamContent(
                viewModel = viewModel,
                title = liveTitle.ifEmpty { currentState.title },
                description = liveDescription.ifEmpty { currentState.description },
                showOverlay = showOverlay,
                showPlayerControls = showPlayerControls,
                onTap = { viewModel.toggleOverlay() },
                onClose = onClose,
                showCloseButton = showCloseButton,
                modifier = modifier
            )
        }
        is LiveStreamState.Error -> {
            ErrorContent(
                errorMessage = currentState.errorMessage,
                errorCode = currentState.errorCode,
                retryable = currentState.retryable,
                retryText = errorRetryText,
                onRetry = { viewModel.retry() },
                modifier = modifier
            )
        }
    }
}



@Composable
private fun PreLiveContent(
    mediaType: MediaType,
    mediaUrl: String,
    mediaTitle: String,
    mediaLoop: Boolean,
    modifier: Modifier = Modifier
) {

    when (mediaType) {
        MediaType.IMAGE -> {
            // Extract dominant color from image
            val dominantColor = rememberDominantColor(mediaUrl)
            
            // Image display that fills the whole screen
            Box(
                modifier = modifier
                    .background(Color.Black)
                    .fillMaxSize()
            ) {
                // Image that fills the entire screen
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(mediaUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Live stream preview",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop // Fill the entire screen
                )
                
                // Title overlay at bottom center
                if (mediaTitle.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 32.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Text(
                            text = mediaTitle,
                            style = MaterialTheme.typography.headlineMedium,
                            color = dominantColor,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .background(
                                    Color.Black.copy(alpha = 0.5f),
                                    shape = MaterialTheme.shapes.small
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        MediaType.VIDEO -> {
            // Try to extract color from video thumbnail (if available) or use fallback
            // For video, we attempt to extract from thumbnail URL or use a default color
            val dominantColor = rememberDominantColor(mediaUrl)
            
            // Video display that fills the whole screen
            Box(
                modifier = modifier
                    .background(Color.Black)
                    .fillMaxSize()
            ) {
                // TODO: Implement video player for mediaUrl
                // For now, show placeholder that fills the screen
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Gray),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Video Preview\n(Not implemented yet)",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                }

                // Title overlay at bottom center
                if (mediaTitle.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 32.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Text(
                            text = mediaTitle,
                            style = MaterialTheme.typography.headlineMedium,
                            color = dominantColor,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .background(
                                    Color.Black.copy(alpha = 0.5f),
                                    shape = MaterialTheme.shapes.small
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingContent(
    liveTitle: String,
    liveDescription: String,
    loadingText: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color.Black)
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            // Live Title
            if (liveTitle.isNotEmpty()) {
                Text(
                    text = liveTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            
            // Live Description
            if (liveDescription.isNotEmpty()) {
                Text(
                    text = liveDescription,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.padding(bottom = 24.dp)
                )
            }
            
            // Small loader with text
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(24.dp), // Small loader
                    strokeWidth = 2.dp
                )
                Text(
                    text = loadingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun ErrorContent(
    errorMessage: String,
    errorCode: SDKError,
    retryable: Boolean,
    retryText: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color.Black)
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "Error",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            if (retryable) {
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    )
                ) {
                    Text(text = retryText)
                }
            }
        }
    }
}

@Composable
private fun LiveStreamContent(
    viewModel: LiveStreamViewModel,
    title: String,
    description: String,
    showOverlay: Boolean,
    showPlayerControls: Boolean,
    onTap: () -> Unit,
    onClose: (() -> Unit)?,
    showCloseButton: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        // Brightcove Player
        BrightcovePlayerView(
            viewModel = viewModel,
            showControls = showPlayerControls,
            modifier = Modifier
                .fillMaxSize()
                .clickable { onTap() }
        )
        
        // Overlay UI (shown/hidden on tap)
        if (showOverlay) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black.copy(alpha = 0.3f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { onTap() }
                ) {
                    // Header with close button
                    if (showCloseButton && onClose != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onClose) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    // Title and description
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                            .background(
                                Color.Black.copy(alpha = 0.7f),
                                shape = MaterialTheme.shapes.medium
                            )
                            .padding(16.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        
                        if (description.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }
        }
    }
}

