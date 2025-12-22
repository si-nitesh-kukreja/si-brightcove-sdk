package com.si.brightcove.sdk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.key
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.*
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
import com.si.brightcove.sdk.ConfigurationChangeListener
import com.si.brightcove.sdk.config.StreamConfigData
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
import androidx.compose.runtime.key
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
    showPlayerControls: Boolean = false,
    modifier: Modifier
) {
    // Initialize logger with debug setting
    Logger.isDebugEnabled = debug

    val context = LocalContext.current
    val errorRetryText = "Retry"
    val showCloseButton = false
    val loadingText = "Loading..."

    // Ensure SDK is initialized before creating the ViewModel to avoid race conditions.
    if (!BrightcoveLiveStreamSDK.isInitialized()) {
        BrightcoveLiveStreamSDK.initialize(
            context = context,
            eventType = eventType,
            environment = environment,
            locale = locale,
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

    // Register for configuration changes
    DisposableEffect(Unit) {
        val configListener = object : ConfigurationChangeListener {
            override fun onConfigurationChanged(newConfig: StreamConfigData) {
                if (debug) {
                    Logger.d("LiveStreamScreen: Configuration changed - videoId: ${newConfig.videoId}, state: ${newConfig.state}")
                }

                // Update ViewModel with new configuration
                viewModel.updateConfiguration(newConfig)

                // The ViewModel will handle updating the stream state based on new configuration
                // The UI will automatically update through the streamState StateFlow
            }
        }

        BrightcoveLiveStreamSDK.addConfigurationChangeListener(configListener)

        onDispose {
            BrightcoveLiveStreamSDK.removeConfigurationChangeListener(configListener)
        }
    }
    
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
                title = currentState.title,
                description = currentState.description,
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

    // Debug logging for media type changes
    if (BuildConfig.DEBUG) {
        Logger.d("PreLiveContent: Displaying mediaType=$mediaType, mediaUrl=$mediaUrl, mediaTitle=$mediaTitle")
    }

    // Force recomposition when media type changes by using it as part of the key
    // This ensures proper cleanup of video resources when switching to image
    // Monitor media type changes for debugging
    LaunchedEffect(mediaType) {
        if (BuildConfig.DEBUG) {
            Logger.d("PreLiveContent: Switching to mediaType=$mediaType, url=$mediaUrl")
        }
    }

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
                // Video player that fills the screen - only active when mediaType is VIDEO
                if (mediaType == MediaType.VIDEO) {
                    VideoPlayer(
                        videoUrl = mediaUrl,
                        loop = mediaLoop,
                        modifier = Modifier.fillMaxSize()
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
    loadingText: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F0F23), // Dark blue-black
                        Color(0xFF1A1A2E), // Slightly lighter blue-black
                        Color(0xFF0F0F23)  // Back to dark
                    )
                )
            )
    ) {
        // Animated background particles effect
        AnimatedParticlesBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Enhanced loading indicator with pulse animation
            LoadingIndicatorWithPulse(loadingText)
        }
    }
}

@Composable
private fun AnimatedParticlesBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "particles")

    val particleOffset1 = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "particle1"
    )

    val particleOffset2 = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "particle2"
    )

    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        val particleSize = 2.dp.toPx()

        // Draw floating particles
        drawCircle(
            color = Color.White.copy(alpha = 0.1f),
            radius = particleSize,
            center = Offset(particleOffset1.value % size.width, size.height * 0.3f)
        )

        drawCircle(
            color = Color(0xFF00D4FF).copy(alpha = 0.15f),
            radius = particleSize * 1.5f,
            center = Offset(particleOffset2.value % size.width, size.height * 0.7f)
        )

        drawCircle(
            color = Color.White.copy(alpha = 0.08f),
            radius = particleSize * 0.8f,
            center = Offset((particleOffset1.value * 0.7f) % size.width, size.height * 0.5f)
        )
    }
}

@Composable
private fun LoadingIndicatorWithPulse(loadingText: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")

    val pulseScale = infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ), label = "pulse"
    )

    val glowAlpha = infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ), label = "glow"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Pulsing loading indicator with glow
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(80.dp)
        ) {
            // Glow effect
            CircularProgressIndicator(
                progress = { 1f },
                modifier = Modifier
                    .size(80.dp * pulseScale.value)
                    .alpha(glowAlpha.value),
                color = Color(0xFF00D4FF).copy(alpha = 0.3f),
                strokeWidth = 2.dp,
                trackColor = Color.Transparent
            )

            // Main loading indicator
            CircularProgressIndicator(
                modifier = Modifier.size(60.dp),
                color = Color(0xFF00D4FF),
                strokeWidth = 4.dp,
                trackColor = Color.White.copy(alpha = 0.2f)
            )

            // Inner accent ring
            CircularProgressIndicator(
                modifier = Modifier.size(40.dp),
                color = Color.White,
                strokeWidth = 2.dp,
                trackColor = Color.Transparent
            )
        }

        // Loading text with subtle animation
        Text(
            text = loadingText,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 16.sp,
                letterSpacing = 0.5.sp
            ),
            color = Color.White.copy(alpha = 0.9f),
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.alpha(glowAlpha.value)
        )
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
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF2D1B69), // Deep purple
                        Color(0xFF11998E), // Teal
                        Color(0xFF2D1B69)  // Back to purple
                    )
                )
            )
    ) {
        // Animated error background effect
        ErrorBackgroundEffect()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Error icon with pulse animation
            ErrorIconWithPulse()

            Spacer(modifier = Modifier.height(24.dp))

            // Error title with glow effect
            Text(
                text = "Oops! Something went wrong",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 28.sp,
                    letterSpacing = 0.5.sp
                ),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .shadow(
                        elevation = 8.dp,
                        spotColor = Color(0xFFFF6B6B),
                        ambientColor = Color(0xFFFF6B6B)
                    )
            )

            // Error message with better styling
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp,
                    lineHeight = 22.sp
                ),
                color = Color.White.copy(alpha = 0.85f),
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 8.dp)
            )

            // Error code display (subtle)
            Text(
                text = "Error Code: ${errorCode.name}",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    letterSpacing = 0.5.sp
                ),
                color = Color.White.copy(alpha = 0.6f),
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = if (retryable) 32.dp else 0.dp)
            )

            // Enhanced retry button
            if (retryable) {
                EnhancedRetryButton(retryText, onRetry)
            }
        }
    }
}

@Composable
private fun ErrorBackgroundEffect() {
    val infiniteTransition = rememberInfiniteTransition(label = "error_background")

    val waveOffset = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "wave"
    )

    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        val waveHeight = size.height * 0.1f

        // Draw subtle wave pattern
        for (i in 0..5) {
            val y = size.height * 0.2f + i * size.height * 0.15f
            val offset = (waveOffset.value + i * 200f) % size.width

            // Draw wave-like patterns
            drawCircle(
                color = Color(0xFFFF6B6B).copy(alpha = 0.05f),
                radius = 40f + i * 10f,
                center = Offset(offset, y)
            )
        }
    }
}

@Composable
private fun ErrorIconWithPulse() {
    val infiniteTransition = rememberInfiniteTransition(label = "error_icon")

    val pulseScale = infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ), label = "pulse"
    )

    val rotation = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ), label = "rotation"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(100.dp)
    ) {
        // Outer glow ring
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .size(100.dp * pulseScale.value)
                .alpha(0.3f)
        ) {
            drawCircle(
                color = Color(0xFFFF6B6B),
                radius = size.minDimension / 2
            )
        }

        // Main error icon background
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .size(80.dp)
                .rotate(rotation.value)
        ) {
            drawCircle(
                color = Color(0xFFFF6B6B).copy(alpha = 0.9f),
                radius = size.minDimension / 2
            )
        }

        // Error symbol (X)
        androidx.compose.foundation.Canvas(modifier = Modifier.size(60.dp)) {
            val strokeWidth = 6f
            val halfStroke = strokeWidth / 2

            // Draw X symbol
            drawLine(
                color = Color.White,
                start = Offset(halfStroke, halfStroke),
                end = Offset(size.width - halfStroke, size.height - halfStroke),
                strokeWidth = strokeWidth,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )

            drawLine(
                color = Color.White,
                start = Offset(size.width - halfStroke, halfStroke),
                end = Offset(halfStroke, size.height - halfStroke),
                strokeWidth = strokeWidth,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }
    }
}

@Composable
private fun EnhancedRetryButton(retryText: String, onRetry: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val buttonScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ), label = "button_scale"
    )

    Button(
        onClick = onRetry,
        modifier = Modifier
            .scale(buttonScale)
            .height(56.dp)
            .shadow(
                elevation = 12.dp,
                spotColor = Color(0xFF4ECDC4),
                ambientColor = Color(0xFF4ECDC4)
            ),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF4ECDC4),
            contentColor = Color(0xFF2D1B69)
        ),
        shape = RoundedCornerShape(28.dp),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 8.dp,
            pressedElevation = 4.dp
        ),
        interactionSource = interactionSource
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                tint = Color(0xFF2D1B69)
            )
            Text(
                text = retryText,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            )
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

