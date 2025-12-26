package com.si.brightcove.sdk.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.brightcove.player.event.Event
import com.brightcove.player.event.EventEmitter
import com.brightcove.player.event.EventListener
import com.brightcove.player.model.Video
import com.brightcove.player.view.BrightcoveExoPlayerVideoView
import com.si.brightcove.sdk.BrightcoveLiveStreamSDK
import com.si.brightcove.sdk.model.PlayerEvent
import com.si.brightcove.sdk.model.SDKError
import com.si.brightcove.sdk.ui.Logger
import com.si.brightcove.sdk.viewmodel.LiveStreamViewModel
import java.lang.reflect.Method
import java.util.Locale

/**
 * Compose wrapper for Brightcove player using AndroidView.
 * This encapsulates the Brightcove player implementation.
 * 
 * @param viewModel The LiveStreamViewModel instance
 * @param showControls Whether to show native Brightcove controls (default: true)
 * @param modifier Modifier for the composable
 */
@Composable
fun BrightcovePlayerView(
    viewModel: LiveStreamViewModel,
    showControls: Boolean = true,
    modifier: Modifier = Modifier
) {
    Logger.d(">>> BrightcovePlayerView composable called <<<")
    val video by viewModel.video.collectAsState()
    val context = LocalContext.current
    Logger.d("BrightcovePlayerView - video state: ${video?.id ?: "null"}")
    
    AndroidView(
        factory = { ctx ->
            Logger.d("=== Creating BrightcoveExoPlayerVideoView ===")
            val playerView = BrightcoveExoPlayerVideoView(ctx)
            Logger.d("Player view created: ${playerView.javaClass.name}")
            
            val frameLayout = FrameLayout(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                // Make player view fill the entire FrameLayout
                playerView.layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                addView(playerView)
            }
            Logger.d("Player view added to FrameLayout")
            
            // Configure video scaling to fill screen
            try {
                // Try to access the underlying ExoPlayer and set scaling mode
                val getExoPlayerMethod = playerView.javaClass.getMethod("getExoPlayer")
                val exoPlayer = getExoPlayerMethod.invoke(playerView)
                if (exoPlayer != null) {
                    val exoPlayerClass = exoPlayer.javaClass
                    val setVideoScalingModeMethod = exoPlayerClass.getMethod(
                        "setVideoScalingMode",
                        Int::class.java
                    )
                    // Use SCALE_TO_FIT_WITH_CROPPING to fill screen (crops if needed)
                    // This is equivalent to C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING = 1
                    setVideoScalingModeMethod.invoke(exoPlayer, 1)
                    Logger.d("Successfully set video scaling mode to fill screen")
                }
            } catch (e: Exception) {
                Logger.w("Could not set video scaling mode via ExoPlayer: ${e.message}")
                // Try alternative method - set via reflection on BrightcoveExoPlayerVideoView
                try {
                    val setVideoScalingModeMethod = playerView.javaClass.getMethod(
                        "setVideoScalingMode",
                        Int::class.java
                    )
                    setVideoScalingModeMethod.invoke(playerView, 1)
                    Logger.d("Successfully set video scaling mode via BrightcoveExoPlayerVideoView")
                } catch (e2: Exception) {
                    Logger.w("Could not set video scaling mode: ${e2.message}")
                }
            }
            
            // Try to configure the player view's aspect ratio/resize mode
            try {
                // Look for methods like setResizeMode, setAspectRatio, etc.
                val resizeMethods = listOf("setResizeMode", "setAspectRatioMode", "setVideoAspectRatio")
                for (methodName in resizeMethods) {
                    try {
                        val method = playerView.javaClass.getMethod(methodName, Int::class.java)
                        // Try ZOOM mode (fills screen, may crop) - value 4
                        // Or FIT mode - value 0
                        method.invoke(playerView, 4) // ZOOM mode
                        Logger.d("Successfully set resize mode via $methodName")
                        break
                    } catch (e: Exception) {
                        // Try next method
                    }
                }
            } catch (e: Exception) {
                Logger.w("Could not set resize mode: ${e.message}")
            }
            
            // Try to set a FullScreenController when controls are shown (needed for zoom/fullscreen button)
            if (showControls) {
                setupFullscreenController(playerView, ctx)
            }
            
            // Apply controls visibility (show/hide native media controller) and enforce with retries
            applyControlsVisibility(playerView, showControls)
            enforceControlsVisibility(playerView, showControls, frameLayout)
            
            // Try to set EventEmitter on player view to initialize MediaPlayback
            // According to Brightcove docs, setEventEmitter initializes inner components
            try {
                val standaloneEmitter = BrightcoveLiveStreamSDK.getStandaloneEventEmitter()
                if (standaloneEmitter != null) {
                    // Try to find and call setEventEmitter method
                    try {
                        val setEventEmitterMethod = playerView.javaClass.getMethod("setEventEmitter", EventEmitter::class.java)
                        setEventEmitterMethod.invoke(playerView, standaloneEmitter)
                        Logger.d("Successfully set EventEmitter on player view")
                    } catch (e: NoSuchMethodException) {
                        Logger.d("setEventEmitter method not found, trying alternative methods")
                        // Try alternative method names
                        val alternativeMethods = listOf("setEmitter", "initializeEventEmitter", "initEventEmitter")
                        for (methodName in alternativeMethods) {
                            try {
                                val method = playerView.javaClass.getMethod(methodName, EventEmitter::class.java)
                                method.invoke(playerView, standaloneEmitter)
                                Logger.d("Successfully called $methodName")
                                break
                            } catch (e: Exception) {
                                // Try next method
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Logger.w("Could not set EventEmitter on player view: ${e.message}")
            }
            
            // Initialize FullScreenController to prevent NullPointerException when media controller initializes
            // Try multiple constructor signatures to find the correct one
            try {
                val fullScreenControllerClass = try {
                    Class.forName("com.brightcove.player.controller.FullScreenController")
                } catch (e: ClassNotFoundException) {
                    Logger.w("FullScreenController class not found: ${e.message}")
                    null
                }
                
                if (fullScreenControllerClass != null) {
                    // Log all available constructors for debugging
                    if (BrightcoveLiveStreamSDK.getConfig().debug) {
                        val constructors = fullScreenControllerClass.declaredConstructors
                        Logger.d("FullScreenController constructors: ${constructors.map { it.parameterTypes.joinToString { it.simpleName } }}")
                    }
                    
                    var fullScreenController: Any? = null
                    
                    // Try different constructor signatures
                    val constructorSignatures = listOf(
                        // Try BaseVideoView/BrightcoveExoPlayerVideoView only (most likely based on docs)
                        arrayOf(BrightcoveExoPlayerVideoView::class.java),
                        // Try BaseVideoView class (parent class)
                        arrayOf(Class.forName("com.brightcove.player.view.BaseVideoView")),
                        // Try Activity, BrightcoveExoPlayerVideoView
                        arrayOf(android.app.Activity::class.java, BrightcoveExoPlayerVideoView::class.java),
                        // Try Context only
                        arrayOf(android.content.Context::class.java),
                        // Try Activity only
                        arrayOf(android.app.Activity::class.java),
                        // Try no-arg
                        arrayOf()
                    )
                    
                    for (params in constructorSignatures) {
                        try {
                            val constructor = fullScreenControllerClass.getDeclaredConstructor(*params)
                            constructor.isAccessible = true
                            
                            // Create instance with appropriate arguments
                            fullScreenController = when (params.size) {
                                0 -> constructor.newInstance()
                                1 -> {
                                    when {
                                        // Try BrightcoveExoPlayerVideoView
                                        params[0] == BrightcoveExoPlayerVideoView::class.java -> {
                                            constructor.newInstance(playerView)
                                        }
                                        // Try BaseVideoView
                                        params[0].name == "com.brightcove.player.view.BaseVideoView" -> {
                                            constructor.newInstance(playerView)
                                        }
                                        // Try Activity
                                        params[0] == android.app.Activity::class.java -> {
                                            val activity = if (ctx is android.app.Activity) ctx else null
                                            if (activity != null) {
                                                constructor.newInstance(activity)
                                            } else {
                                                null
                                            }
                                        }
                                        // Try Context
                                        else -> {
                                            constructor.newInstance(ctx)
                                        }
                                    }
                                }
                                2 -> {
                                    if (params[0] == android.app.Activity::class.java) {
                                        val activity = if (ctx is android.app.Activity) ctx else null
                                        if (activity != null) {
                                            constructor.newInstance(activity, playerView)
                                        } else {
                                            null
                                        }
                                    } else {
                                        constructor.newInstance(ctx, playerView)
                                    }
                                }
                                else -> null
                            }
                            
                            if (fullScreenController != null) {
                                Logger.d("Successfully created FullScreenController with constructor: ${params.map { it.simpleName }}")
                                break
                            }
                        } catch (e: Exception) {
                            // Try next constructor
                            if (BrightcoveLiveStreamSDK.getConfig().debug) {
                                Logger.d("Constructor ${params.map { it.simpleName }} failed: ${e.message}")
                            }
                        }
                    }
                    
                    if (fullScreenController != null) {
                        // Set it on the player view
                        try {
                            val setFullScreenControllerMethod = playerView.javaClass.getMethod(
                                "setFullScreenController",
                                fullScreenControllerClass
                            )
                            setFullScreenControllerMethod.invoke(playerView, fullScreenController)
                            Logger.d("Successfully set FullScreenController on player view")
                        } catch (e: NoSuchMethodException) {
                            // Try alternative method name
                            try {
                                val method = playerView.javaClass.getMethod(
                                    "setFullscreenController",
                                    fullScreenControllerClass
                                )
                                method.invoke(playerView, fullScreenController)
                                Logger.d("Successfully set FullScreenController via setFullscreenController")
                            } catch (e2: Exception) {
                                // Try setting via reflection on field (check both this class and parent BaseVideoView)
                                var fieldSet = false
                                var currentClass: Class<*>? = playerView.javaClass
                                
                                // Try to find field in this class and parent classes
                                while (currentClass != null && !fieldSet) {
                                    try {
                                        val field = currentClass.getDeclaredField("fullScreenController")
                                        field.isAccessible = true
                                        field.set(playerView, fullScreenController)
                                        Logger.d("Successfully set FullScreenController via reflection field in ${currentClass.simpleName}")
                                        fieldSet = true
                                    } catch (e: NoSuchFieldException) {
                                        // Try lowercase field name
                                        try {
                                            val field = currentClass.getDeclaredField("fullscreenController")
                                            field.isAccessible = true
                                            field.set(playerView, fullScreenController)
                                            Logger.d("Successfully set FullScreenController via reflection field (lowercase) in ${currentClass.simpleName}")
                                            fieldSet = true
                                        } catch (e2: Exception) {
                                            // Try parent class
                                            currentClass = currentClass.superclass
                                        }
                                    } catch (e: Exception) {
                                        // Try parent class
                                        currentClass = currentClass?.superclass
                                    }
                                }
                                
                                if (!fieldSet) {
                                    Logger.w("Could not set FullScreenController via reflection field in any class")
                                }
                            }
                        }
                    } else {
                        Logger.w("Could not create FullScreenController with any known constructor.")
                        // Do NOT disable fullscreen when controls are meant to be shown
                        if (!showControls) {
                            tryDisableFullscreenButton(playerView, ctx)
                        }
                    }
                } else {
                    // FullScreenController class not found; only disable if controls are meant to be hidden
                    if (!showControls) {
                        tryDisableFullscreenButton(playerView, ctx)
                    }
                }
            } catch (e: Exception) {
                Logger.w("Error initializing FullScreenController: ${e.message}", e)
                // Only disable fullscreen if controls should be hidden
                if (!showControls) {
                    tryDisableFullscreenButton(playerView, ctx)
                }
            }
            
            // Store player view reference for updates
            frameLayout.tag = playerView
            
            // Try to access EventEmitter immediately after creation
            Logger.d("Testing EventEmitter access immediately after creation...")
            
            // Try direct property access
            var immediateEmitter: EventEmitter? = null
            try {
                immediateEmitter = playerView.eventEmitter
                Logger.d("Direct property access: ${if (immediateEmitter != null) "AVAILABLE" else "NULL"}")
            } catch (e: Exception) {
                Logger.e("Exception getting EventEmitter via property: ${e.message}", e)
            }
            
            // Try reflection to check if property exists
            if (immediateEmitter == null) {
                try {
                    val eventEmitterField = playerView.javaClass.getDeclaredField("eventEmitter")
                    eventEmitterField.isAccessible = true
                    immediateEmitter = eventEmitterField.get(playerView) as? EventEmitter
                    Logger.d("Reflection access: ${if (immediateEmitter != null) "AVAILABLE" else "NULL"}")
                } catch (e: Exception) {
                    Logger.d("Reflection failed: ${e.message}")
                }
            }
            
            // Try getter method
            if (immediateEmitter == null) {
                try {
                    val getEventEmitterMethod = playerView.javaClass.getMethod("getEventEmitter")
                    immediateEmitter = getEventEmitterMethod.invoke(playerView) as? EventEmitter
                    Logger.d("Getter method access: ${if (immediateEmitter != null) "AVAILABLE" else "NULL"}")
                } catch (e: Exception) {
                    Logger.d("Getter method failed: ${e.message}")
                }
            }
            
            // Log all available methods/properties for debugging
            if (immediateEmitter == null && BrightcoveLiveStreamSDK.getConfig().debug) {
                try {
                    val methods = playerView.javaClass.declaredMethods.filter { 
                        it.name.contains("event", ignoreCase = true) || 
                        it.name.contains("emitter", ignoreCase = true) ||
                        it.returnType.simpleName.contains("EventEmitter")
                    }
                    Logger.d("Found ${methods.size} EventEmitter-related methods: ${methods.map { it.name }}")
                    
                    val fields = playerView.javaClass.declaredFields.filter { 
                        it.name.contains("event", ignoreCase = true) || 
                        it.name.contains("emitter", ignoreCase = true) ||
                        it.type.simpleName.contains("EventEmitter")
                    }
                    Logger.d("Found ${fields.size} EventEmitter-related fields: ${fields.map { it.name }}")
                } catch (e: Exception) {
                    Logger.e("Error inspecting class: ${e.message}")
                }
            }
            
            Logger.d("Final immediate EventEmitter check: ${if (immediateEmitter != null) "AVAILABLE" else "NULL"}")
            
            // Function to set up player once EventEmitter is confirmed available
            fun setupPlayerOnceReady(): Boolean {
                return try {
                    Logger.d("Attempting to access EventEmitter - view class: ${playerView.javaClass.simpleName}, attached: ${playerView.isAttachedToWindow}, visibility: ${playerView.visibility}")
                    val eventEmitter = playerView.eventEmitter
                    Logger.d("EventEmitter access result: ${if (eventEmitter != null) "SUCCESS" else "NULL"}")
                    
                    if (eventEmitter != null) {
                        Logger.d("EventEmitter confirmed available, setting up player")
                        setupPlayerEvents(playerView, viewModel)
                        // Only notify ViewModel after EventEmitter is confirmed available
                        viewModel.setPlayerView(playerView, eventEmitterReady = true)
                        true
                    } else {
                        Logger.w("EventEmitter is null - will retry")
                        false
                    }
                } catch (e: Exception) {
                    Logger.e("Exception accessing EventEmitter: ${e.javaClass.simpleName} - ${e.message}", e)
                    false
                }
            }
            
            // If EventEmitter was available immediately, use it
            if (immediateEmitter != null) {
                Logger.d("EventEmitter available immediately! Setting up player")
                setupPlayerEvents(playerView, viewModel)
                viewModel.setPlayerView(playerView, eventEmitterReady = true)
            } else {
                // Try immediately - EventEmitter should be available right after creation
                Logger.d("EventEmitter not immediately available, trying setupPlayerOnceReady()")
                if (!setupPlayerOnceReady()) {
                    Logger.d("EventEmitter not immediately available, waiting for view attachment")
                
                // Wait for view to be attached to window
                frameLayout.post {
                    if (playerView.isAttachedToWindow) {
                        // View is attached, try again
                        if (!setupPlayerOnceReady()) {
                            // Still not available, use retry mechanism
                            waitForEventEmitterWithRetry(playerView, frameLayout, viewModel)
                        }
                    } else {
                        // Wait for attachment
                        playerView.addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
                            override fun onViewAttachedToWindow(v: android.view.View) {
                                playerView.removeOnAttachStateChangeListener(this)
                                frameLayout.postDelayed({
                                    if (!setupPlayerOnceReady()) {
                                        // Still not available after attachment, use retry
                                        waitForEventEmitterWithRetry(playerView, frameLayout, viewModel)
                                    }
                                }, 200) // Give it a bit more time after attachment
                            }
                            
                            override fun onViewDetachedFromWindow(v: android.view.View) {
                                // Not needed
                            }
                        })
                    }
                }
                }
            }
            
            // Don't set video in factory - wait for update block when player is initialized
            // Setting video too early causes NullPointerException
            
            frameLayout
        },
        update = { frameLayout ->
            // Ensure FrameLayout and player view maintain full screen size
            val view = frameLayout as? FrameLayout
            view?.let {
                it.layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                // Ensure player view fills the FrameLayout
                val playerView = it.tag as? BrightcoveExoPlayerVideoView
                    ?: (it.getChildAt(0) as? BrightcoveExoPlayerVideoView)
                playerView?.let { pv ->
                    pv.layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    
                    // Configure video scaling to fill screen
                    configureVideoScaling(pv)
                    
                    // Update controls visibility on each recomposition
                    applyControlsVisibility(pv, showControls)
                    enforceControlsVisibility(pv, showControls, frameLayout)
                    
                    // Update player view reference in ViewModel only if EventEmitter is available
                    val eventEmitterReady = try {
                        pv.eventEmitter != null
                    } catch (e: Exception) {
                        false
                    }
                    viewModel.setPlayerView(pv, eventEmitterReady = eventEmitterReady)
                    
                    // Only update video if player view is available and properly initialized
                    video?.let { video ->
                        // Check if view is visible and has dimensions before adding video
                        val isVisible = pv.visibility == android.view.View.VISIBLE
                        val hasDimensions = pv.width > 0 && pv.height > 0
                        
                        Logger.d("Attempting to add video - visible: $isVisible, hasDimensions: $hasDimensions, width: ${pv.width}, height: ${pv.height}")
                        
                        if (isVisible && hasDimensions) {
                            // View is visible and has dimensions, try to add video
                            try {
                                Logger.d("Adding video to player: ${video.id}")
                                // Configure video scaling before adding video
                                configureVideoScaling(pv)
                                pv.add(video)
                                pv.start()
                                // Configure again after starting to ensure it's applied
                                frameLayout.postDelayed({
                                    configureVideoScaling(pv)
                                    logVideoDimensions(pv, video) // Log dimensions after video starts
                                }, 100)
                                Logger.d("Video added and started successfully")
                            } catch (e: Exception) {
                                Logger.w("Error adding video: ${e.message}, will retry")
                                // Retry after a shorter delay (reduced from 2000ms for faster loading)
                                frameLayout.postDelayed({
                                    try {
                                        Logger.d("Retrying to add video after delay")
                                        pv.add(video)
                                        pv.start()
                                        Logger.d("Video added successfully on retry")
                                    } catch (ex: Exception) {
                                        Logger.w("Error adding video on retry: ${ex.message}")
                                    }
                                }, 500) // Reduced from 2000ms to 500ms
                            }
                        } else {
                            // View not ready yet, wait for it to be laid out
                            Logger.d("Player view not ready (visible=$isVisible, hasDimensions=$hasDimensions), waiting for layout")
                            
                            val viewTreeObserver = pv.viewTreeObserver
                            viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                                override fun onGlobalLayout() {
                                    pv.viewTreeObserver.removeOnGlobalLayoutListener(this)
                                    
                                    // Wait for MediaPlayback to initialize - try multiple times with increasing delays
                                    var retryCount = 0
                                    val maxRetries = 20 // Reduced to 20 attempts (about 4 seconds total) for faster loading
                                    
                                    fun tryAddVideo() {
                                        try {
                                        Logger.d("Attempting to add video (attempt ${retryCount + 1}/$maxRetries)")
                                        pv.add(video)
                                        pv.start()
                                        Logger.d("Video added and started successfully!")
                                        
                                        // Log video dimensions after successful addition
                                        frameLayout.postDelayed({
                                            logVideoDimensions(pv)
                                        }, 200)
                                        
                                        // Set up event listeners
                                        setupPlayerEvents(pv, viewModel)
                                        } catch (e: Exception) {
                                            // MediaPlayback not ready yet, retry with increasing delays
                                            if (retryCount < maxRetries) {
                                                retryCount++
                                                val delay = (retryCount * 200).toLong() // 200ms, 400ms, 600ms, etc. (reduced from 500ms)
                                                Logger.d("Error adding video: ${e.message}, retrying in ${delay}ms (attempt $retryCount/$maxRetries)")
                                                frameLayout.postDelayed({ tryAddVideo() }, delay)
                                            } else {
                                                Logger.e("Failed to add video after $maxRetries attempts: ${e.message}")
                                            }
                                        }
                                    }
                                    
                                    // Start trying immediately (reduced from 500ms delay)
                                    frameLayout.postDelayed({ tryAddVideo() }, 100)
                                }
                            })
                        }
                    }
                }
            }
        },
        modifier = modifier
    )
    
    // Cleanup on dispose
    DisposableEffect(video) {
        onDispose {
            // Player cleanup is handled by Brightcove SDK lifecycle
            // The BrightcoveExoPlayerVideoView will handle resource cleanup
        }
    }
}

/**
 * Attempt to set up the Brightcove FullScreenController so the fullscreen/zoom button works.
 */
private fun setupFullscreenController(
    playerView: BrightcoveExoPlayerVideoView,
    ctx: android.content.Context
) {
    try {
        val fullScreenControllerClass = try {
            Class.forName("com.brightcove.player.controller.FullScreenController")
        } catch (_: ClassNotFoundException) {
            null
        } ?: return

        val activity = ctx as? android.app.Activity

        // Preferred constructors (ordered)
        val ctorParams = listOf(
            arrayOf(android.app.Activity::class.java, BrightcoveExoPlayerVideoView::class.java),
            arrayOf(android.app.Activity::class.java),
            arrayOf(android.content.Context::class.java, BrightcoveExoPlayerVideoView::class.java),
            arrayOf(android.content.Context::class.java),
            arrayOf(BrightcoveExoPlayerVideoView::class.java),
            emptyArray<Class<*>>()
        )

        var fullScreenController: Any? = null
        for (params in ctorParams) {
            try {
                val ctor = fullScreenControllerClass.getDeclaredConstructor(*params)
                ctor.isAccessible = true
                fullScreenController = when (params.size) {
                    0 -> ctor.newInstance()
                    1 -> when (params[0]) {
                        android.app.Activity::class.java -> activity?.let { ctor.newInstance(it) }
                        android.content.Context::class.java -> ctor.newInstance(ctx)
                        BrightcoveExoPlayerVideoView::class.java -> ctor.newInstance(playerView)
                        else -> null
                    }
                    2 -> {
                        if (params[0] == android.app.Activity::class.java && activity != null) {
                            if (params[1] == BrightcoveExoPlayerVideoView::class.java) {
                                ctor.newInstance(activity, playerView)
                            } else {
                                ctor.newInstance(activity, playerView)
                            }
                        } else if (params[0] == android.content.Context::class.java) {
                            ctor.newInstance(ctx, playerView)
                        } else {
                            null
                        }
                    }
                    else -> null
                }
                if (fullScreenController != null) break
            } catch (_: Exception) {
                // try next
            }
        }

        if (fullScreenController != null) {
            val setter = listOf(
                runCatching { playerView.javaClass.getMethod("setFullScreenController", fullScreenControllerClass) }.getOrNull(),
                runCatching { playerView.javaClass.getMethod("setFullscreenController", fullScreenControllerClass) }.getOrNull()
            ).firstOrNull { it != null }
            if (setter != null) {
                setter.invoke(playerView, fullScreenController)
                Logger.d("Fullscreen controller set (${fullScreenController.javaClass.simpleName})")
                return
            }

            // Fallback: try field injection
            var current: Class<*>? = playerView.javaClass
            while (current != null) {
                try {
                    val field = runCatching { current.getDeclaredField("fullScreenController") }.getOrNull()
                        ?: runCatching { current.getDeclaredField("fullscreenController") }.getOrNull()
                    if (field != null) {
                        field.isAccessible = true
                        field.set(playerView, fullScreenController)
                        Logger.d("Fullscreen controller set via field on ${current.simpleName}")
                        return
                    }
                } catch (_: Exception) { }
                current = current.superclass
            }
        }
    } catch (e: Exception) {
        Logger.d("Could not set fullscreen controller: ${e.message}")
    }
}

/**
 * Enforce hiding controls with several retries to cover post-attach/controller recreation.
 */
private fun enforceControlsVisibility(
    playerView: BrightcoveExoPlayerVideoView,
    showControls: Boolean,
    host: ViewGroup
) {
    if (showControls) return
    // Try multiple times with short delays to catch controller re-creations
    val maxAttempts = 8
    val delayMs = 200L
    fun attempt(count: Int) {
        applyControlsVisibility(playerView, showControls)
        if (count < maxAttempts) {
            host.postDelayed({ attempt(count + 1) }, delayMs)
        }
    }
    host.post { attempt(0) }
}

/**
 * Wait for EventEmitter with retry mechanism.
 */
/**
 * Try to disable the fullscreen button in the media controller to prevent crash.
 * This is a fallback if FullScreenController cannot be created.
 */
private fun tryDisableFullscreenButton(
    playerView: BrightcoveExoPlayerVideoView,
    context: android.content.Context
) {
    try {
        // Try to get the media controller and disable fullscreen button
        val mediaControllerClass = try {
            Class.forName("com.brightcove.player.mediacontroller.BrightcoveMediaController")
        } catch (e: ClassNotFoundException) {
            null
        }
        
        if (mediaControllerClass != null) {
            // Try to get media controller from player view
            try {
                val getMediaControllerMethod = playerView.javaClass.getMethod("getMediaController")
                val mediaController = getMediaControllerMethod.invoke(playerView)
                
                if (mediaController != null) {
                    // Try to disable fullscreen button
                    try {
                        val setFullscreenEnabledMethod = mediaControllerClass.getMethod("setFullscreenEnabled", Boolean::class.java)
                        setFullscreenEnabledMethod.invoke(mediaController, false)
                        Logger.d("Successfully disabled fullscreen button in media controller")
                    } catch (e: Exception) {
                        Logger.d("Could not disable fullscreen via setFullscreenEnabled: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Logger.d("Could not get media controller: ${e.message}")
            }
        }
        
        // Alternative: Try to set an attribute on the player view to disable fullscreen
        // This might be done via XML attributes or programmatically
        try {
            val setFullscreenEnabledMethod = playerView.javaClass.getMethod("setFullscreenEnabled", Boolean::class.java)
            setFullscreenEnabledMethod.invoke(playerView, false)
            Logger.d("Successfully disabled fullscreen on player view")
        } catch (e: Exception) {
            Logger.d("Could not disable fullscreen on player view: ${e.message}")
        }
    } catch (e: Exception) {
        Logger.w("Error trying to disable fullscreen button: ${e.message}")
    }
}

/**
 * Apply visibility to the native Brightcove media controller based on showControls flag.
 */
private fun applyControlsVisibility(
    playerView: BrightcoveExoPlayerVideoView,
    showControls: Boolean
) {
    try {
        // Also try to notify Brightcove via EventEmitter
        val eventEmitter = runCatching { playerView.eventEmitter }.getOrNull()
        // Try to get media controller via public or reflected method
        val mediaController = try {
            val m = playerView.javaClass.getMethod("getMediaController")
            m.invoke(playerView)
        } catch (e: Exception) {
            try {
                val m = playerView.javaClass.getMethod("getBrightcoveMediaController")
                m.invoke(playerView)
            } catch (e2: Exception) {
                null
            }
        }
        if (mediaController is View) {
            if (showControls) {
                eventEmitter?.emit(com.brightcove.player.event.EventType.SHOW_SEEK_CONTROLS)
                eventEmitter?.emit(com.brightcove.player.event.EventType.SHOW_PLAYER_OPTIONS)
                mediaController.visibility = View.VISIBLE
                try {
                    val showMethod = mediaController.javaClass.getMethod("show")
                    showMethod.invoke(mediaController)
                } catch (_: Exception) {
                    // ignore
                }
            } else {
                eventEmitter?.emit(com.brightcove.player.event.EventType.HIDE_SEEK_CONTROLS)
                eventEmitter?.emit(com.brightcove.player.event.EventType.HIDE_PLAYER_OPTIONS)
                try {
                    val hideMethod = mediaController.javaClass.getMethod("hide")
                    hideMethod.invoke(mediaController)
                } catch (_: Exception) {
                    // ignore
                }
                mediaController.visibility = View.GONE
                // As an extra measure, detach the controller view
                (mediaController.parent as? ViewGroup)?.removeView(mediaController)
            }
        } else if (mediaController != null && !showControls) {
            // As a fallback, if controller is not a View but exists, try calling hide()
            eventEmitter?.emit(com.brightcove.player.event.EventType.HIDE_SEEK_CONTROLS)
            eventEmitter?.emit(com.brightcove.player.event.EventType.HIDE_PLAYER_OPTIONS)
            try {
                val hideMethod = mediaController.javaClass.getMethod("hide")
                hideMethod.invoke(mediaController)
            } catch (_: Exception) {
                // ignore
            }
        } else if (!showControls && eventEmitter != null) {
            // Final fallback: request hide via emitter even if controller not found
            eventEmitter.emit(com.brightcove.player.event.EventType.HIDE_SEEK_CONTROLS)
            eventEmitter.emit(com.brightcove.player.event.EventType.HIDE_PLAYER_OPTIONS)
        }

        // Stronger fallback: null out media controller field and remove child views that look like controllers
        if (!showControls) {
            val possibleFields = listOf("mediaController", "brightcoveMediaController", "mMediaController")
            var cleared = false
            for (fieldName in possibleFields) {
                try {
                    val field = playerView.javaClass.getDeclaredField(fieldName)
                    field.isAccessible = true
                    val ctrl = field.get(playerView)
                    if (ctrl is View) {
                        (ctrl.parent as? ViewGroup)?.removeView(ctrl)
                    }
                    field.set(playerView, null)
                    cleared = true
                } catch (_: Exception) {
                }
            }
            // Remove any child that might be controller overlay
            try {
                if (playerView.childCount > 0) {
                    val toRemove = mutableListOf<View>()
                    for (i in 0 until playerView.childCount) {
                        val child = playerView.getChildAt(i)
                        val name = child.javaClass.name.lowercase(Locale.getDefault())
                        if (name.contains("controller") || name.contains("media") || name.contains("controls")) {
                            toRemove.add(child)
                        }
                    }
                    toRemove.forEach { (it.parent as? ViewGroup)?.removeView(it) }
                }
            } catch (_: Exception) {
            }
        }
    } catch (e: Exception) {
        Logger.d("Could not toggle media controller visibility: ${e.message}")
    }
}

private fun waitForEventEmitterWithRetry(
    playerView: BrightcoveExoPlayerVideoView,
    frameLayout: FrameLayout,
    viewModel: LiveStreamViewModel
) {
    var attempts = 0
    val maxAttempts = 10 // Try for up to 1.5 seconds (30 * 50ms) - reduced for faster loading
    
    val checkEventEmitter = object : Runnable {
        override fun run() {
            val eventEmitter = try {
                playerView.eventEmitter
            } catch (e: Exception) {
                Logger.e("Exception accessing EventEmitter on attempt $attempts", e)
                null
            }
            
            if (eventEmitter != null) {
                Logger.d("EventEmitter available after retry (attempt $attempts), setting up")
                setupPlayerEvents(playerView, viewModel)
                viewModel.setPlayerView(playerView, eventEmitterReady = true)
            } else if (attempts < maxAttempts) {
                attempts++
                if (attempts % 10 == 0) {
                    Logger.d("Waiting for EventEmitter... attempt $attempts/$maxAttempts (attached=${playerView.isAttachedToWindow})")
                }
                frameLayout.postDelayed(this, 50) // Check every 50ms (reduced from 100ms for faster loading)
            } else {
                Logger.e("EventEmitter not available after $maxAttempts attempts! Setting player view anyway - Catalog operations will fail until EventEmitter is available")
                // Set player view anyway - the ViewModel will keep retrying
                viewModel.setPlayerView(playerView, eventEmitterReady = false)
            }
        }
    }
    
    frameLayout.post(checkEventEmitter)
}

/**
 * Log video dimensions and aspect ratio for debugging.
 */
private fun logVideoDimensions(playerView: BrightcoveExoPlayerVideoView, video: Video? = null) {
    try {
        Logger.d("=== Starting video dimensions logging ===")
        
        // Try to get video dimensions from Video object if available
        if (video != null) {
            try {
                Logger.d("Trying to get dimensions from Video object")
                val videoProperties = video.properties
                
                // Try to extract dimensions from thumbnail/poster URLs (they contain dimensions like 1280x720)
                var videoWidth = 0
                var videoHeight = 0
                
                // Check posterSources for dimensions
                try {
                    val posterSources = videoProperties?.get("posterSources")
                    if (posterSources != null) {
                        val posterSourcesStr = posterSources.toString()
                        // Look for pattern like 1280x720 in URLs
                        val dimensionPattern = Regex("(\\d+)x(\\d+)")
                        val matches = dimensionPattern.findAll(posterSourcesStr)
                        matches.forEach { match ->
                            val width = match.groupValues[1].toIntOrNull()
                            val height = match.groupValues[2].toIntOrNull()
                            if (width != null && height != null && width > videoWidth && height > videoHeight) {
                                videoWidth = width
                                videoHeight = height
                            }
                        }
                    }
                } catch (e: Exception) {
                    Logger.d("Could not extract dimensions from posterSources: ${e.message}")
                }
                
                // Check thumbnailSources for dimensions
                if (videoWidth == 0 || videoHeight == 0) {
                    try {
                        val thumbnailSources = videoProperties?.get("thumbnailSources")
                        if (thumbnailSources != null) {
                            val thumbnailSourcesStr = thumbnailSources.toString()
                            val dimensionPattern = Regex("(\\d+)x(\\d+)")
                            val matches = dimensionPattern.findAll(thumbnailSourcesStr)
                            matches.forEach { match ->
                                val width = match.groupValues[1].toIntOrNull()
                                val height = match.groupValues[2].toIntOrNull()
                                if (width != null && height != null && width > videoWidth && height > videoHeight) {
                                    videoWidth = width
                                    videoHeight = height
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Logger.d("Could not extract dimensions from thumbnailSources: ${e.message}")
                    }
                }
                
                // Check thumbnail URL
                if (videoWidth == 0 || videoHeight == 0) {
                    try {
                        val thumbnail = videoProperties?.get("thumbnail")?.toString()
                        if (thumbnail != null) {
                            val dimensionPattern = Regex("(\\d+)x(\\d+)")
                            val match = dimensionPattern.find(thumbnail)
                            if (match != null) {
                                val width = match.groupValues[1].toIntOrNull()
                                val height = match.groupValues[2].toIntOrNull()
                                if (width != null && height != null) {
                                    videoWidth = width
                                    videoHeight = height
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Logger.d("Could not extract dimensions from thumbnail: ${e.message}")
                    }
                }
                
                if (videoWidth > 0 && videoHeight > 0) {
                    val aspectRatio = videoWidth.toFloat() / videoHeight.toFloat()
                    Logger.d("=== Brightcove Video Dimensions (from Video thumbnail/poster URLs) ===")
                    Logger.d("Video - width: $videoWidth, height: $videoHeight")
                    Logger.d("Video - aspect ratio: $aspectRatio (${videoWidth}:${videoHeight})")
                    Logger.d("Note: These dimensions are from thumbnail/poster URLs and may not reflect actual video dimensions")
                    // Don't return, continue to try other methods for actual video dimensions
                }
                
                // Check for width/height in properties directly
                videoProperties?.forEach { (key, value) ->
                    if (key.toString().contains("width", ignoreCase = true) ||
                        key.toString().contains("height", ignoreCase = true) ||
                        key.toString().contains("size", ignoreCase = true) ||
                        key.toString().contains("dimension", ignoreCase = true)) {
                        Logger.d("Video property - $key: $value")
                    }
                }
            } catch (e: Exception) {
                Logger.d("Error accessing Video object: ${e.message}")
            }
        }
        
        // Log player view dimensions
        val playerWidth = playerView.width
        val playerHeight = playerView.height
        val playerMeasuredWidth = playerView.measuredWidth
        val playerMeasuredHeight = playerView.measuredHeight
        Logger.d("=== Brightcove Player View Dimensions ===")
        Logger.d("Player View - width: $playerWidth, height: $playerHeight")
        Logger.d("Player View - measuredWidth: $playerMeasuredWidth, measuredHeight: $playerMeasuredHeight")
        
        // Try to get screen dimensions
        val context = playerView.context
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val screenDensity = displayMetrics.density
        Logger.d("Screen - width: $screenWidth, height: $screenHeight, density: $screenDensity")
        
        // Try to access ExoPlayer and get video dimensions
        val methodNames = listOf("getExoPlayer", "getPlayer", "getVideoPlayer", "exoPlayer", "player")
        var exoPlayerFound = false
        
        for (methodName in methodNames) {
            try {
                Logger.d("Trying to access ExoPlayer via method: $methodName")
                val method = playerView.javaClass.getMethod(methodName)
                val exoPlayer = method.invoke(playerView)
                if (exoPlayer != null) {
                    exoPlayerFound = true
                    Logger.d("Successfully accessed ExoPlayer via: $methodName, class: ${exoPlayer.javaClass.name}")
                    val exoPlayerClass = exoPlayer.javaClass
                    
                    // Try to get video size
                    try {
                        Logger.d("Trying to get video size via getVideoSize()")
                        val getVideoSizeMethod = exoPlayerClass.getMethod("getVideoSize")
                        val videoSize = getVideoSizeMethod.invoke(exoPlayer)
                        if (videoSize != null) {
                            Logger.d("VideoSize object found: ${videoSize.javaClass.name}")
                            val videoSizeClass = videoSize.javaClass
                            
                            // Try multiple ways to get width and height
                            var videoWidth = 0
                            var videoHeight = 0
                            
                            // Method 1: Try fields
                            try {
                                val widthField = videoSizeClass.getField("width")
                                val heightField = videoSizeClass.getField("height")
                                videoWidth = widthField.getInt(videoSize)
                                videoHeight = heightField.getInt(videoSize)
                                Logger.d("Got dimensions via fields: ${videoWidth}x${videoHeight}")
                            } catch (e: Exception) {
                                Logger.d("Could not get dimensions via fields: ${e.message}")
                                // Method 2: Try methods
                                try {
                                    val getWidthMethod = videoSizeClass.getMethod("getWidth")
                                    val getHeightMethod = videoSizeClass.getMethod("getHeight")
                                    videoWidth = getWidthMethod.invoke(videoSize) as Int
                                    videoHeight = getHeightMethod.invoke(videoSize) as Int
                                    Logger.d("Got dimensions via methods: ${videoWidth}x${videoHeight}")
                                } catch (e2: Exception) {
                                    Logger.d("Could not get dimensions via methods: ${e2.message}")
                                }
                            }
                            
                            if (videoWidth > 0 && videoHeight > 0) {
                                val aspectRatio = videoWidth.toFloat() / videoHeight.toFloat()
                                
                                Logger.d("=== Brightcove Video Dimensions ===")
                                Logger.d("Video - width: $videoWidth, height: $videoHeight")
                                Logger.d("Video - aspect ratio: $aspectRatio (${videoWidth}:${videoHeight})")
                                Logger.d("Player View aspect ratio: ${if (playerHeight > 0) playerWidth.toFloat() / playerHeight.toFloat() else "N/A"}")
                                Logger.d("Screen aspect ratio: ${screenWidth.toFloat() / screenHeight.toFloat()}")
                                
                                // Determine video orientation
                                val orientation = when {
                                    aspectRatio > 1.2f -> "Landscape"
                                    aspectRatio < 0.8f -> "Portrait"
                                    else -> "Square"
                                }
                                Logger.d("Video orientation: $orientation")
                                
                                return
                            }
                        } else {
                            Logger.d("getVideoSize() returned null")
                        }
                    } catch (e: Exception) {
                        Logger.d("Could not get video size from ExoPlayer: ${e.message}")
                        Logger.d("Exception type: ${e.javaClass.name}")
                    }
                    
                    // Try alternative method - getVideoFormat
                    try {
                        Logger.d("Trying to get video format via getVideoFormat()")
                        val getVideoFormatMethod = exoPlayerClass.getMethod("getVideoFormat")
                        val videoFormat = getVideoFormatMethod.invoke(exoPlayer)
                        if (videoFormat != null) {
                            Logger.d("VideoFormat object found: ${videoFormat.javaClass.name}")
                            val formatClass = videoFormat.javaClass
                            
                            var videoWidth = 0
                            var videoHeight = 0
                            
                            // Try fields
                            try {
                                val widthField = formatClass.getField("width")
                                val heightField = formatClass.getField("height")
                                videoWidth = widthField.getInt(videoFormat)
                                videoHeight = heightField.getInt(videoFormat)
                                Logger.d("Got dimensions from VideoFormat via fields: ${videoWidth}x${videoHeight}")
                            } catch (e: Exception) {
                                Logger.d("Could not get dimensions from VideoFormat via fields: ${e.message}")
                                // Try methods
                                try {
                                    val getWidthMethod = formatClass.getMethod("width")
                                    val getHeightMethod = formatClass.getMethod("height")
                                    videoWidth = getWidthMethod.invoke(videoFormat) as Int
                                    videoHeight = getHeightMethod.invoke(videoFormat) as Int
                                    Logger.d("Got dimensions from VideoFormat via methods: ${videoWidth}x${videoHeight}")
                                } catch (e2: Exception) {
                                    Logger.d("Could not get dimensions from VideoFormat via methods: ${e2.message}")
                                }
                            }
                            
                            if (videoWidth > 0 && videoHeight > 0) {
                                val aspectRatio = videoWidth.toFloat() / videoHeight.toFloat()
                                
                                Logger.d("=== Brightcove Video Dimensions (from VideoFormat) ===")
                                Logger.d("Video - width: $videoWidth, height: $videoHeight")
                                Logger.d("Video - aspect ratio: $aspectRatio (${videoWidth}:${videoHeight})")
                                
                                return
                            }
                        } else {
                            Logger.d("getVideoFormat() returned null")
                        }
                    } catch (e: Exception) {
                        Logger.d("Could not get video format: ${e.message}")
                        Logger.d("Exception type: ${e.javaClass.name}")
                    }
                    
                    // Try to get current track info
                    try {
                        Logger.d("Trying to get current tracks")
                        val getCurrentTracksMethod = exoPlayerClass.getMethod("getCurrentTracks")
                        val tracks = getCurrentTracksMethod.invoke(exoPlayer)
                        if (tracks != null) {
                            Logger.d("Current tracks found: ${tracks.javaClass.name}")
                            // Log available methods on tracks object
                            val tracksClass = tracks.javaClass
                            Logger.d("Tracks class methods: ${tracksClass.declaredMethods.map { it.name }.take(10)}")
                        }
                    } catch (e: Exception) {
                        Logger.d("Could not get current tracks: ${e.message}")
                    }
                } else {
                    Logger.d("Method $methodName returned null")
                }
            } catch (e: Exception) {
                Logger.d("Method $methodName failed: ${e.message}")
                Logger.d("Exception type: ${e.javaClass.name}")
            }
        }
        
        // Try getPlayback() method which was found in available methods
        if (!exoPlayerFound) {
            try {
                Logger.d("Trying getPlayback() method")
                val getPlaybackMethod = playerView.javaClass.getMethod("getPlayback")
                val playback = getPlaybackMethod.invoke(playerView)
                if (playback != null) {
                    Logger.d("getPlayback() returned: ${playback.javaClass.name}")
                    val playbackClass = playback.javaClass
                    
                    // Try to get video dimensions from playback object
                    // Check if it has methods like getVideoWidth, getVideoHeight, getVideoSize, etc.
                    val dimensionMethods = listOf("getVideoWidth", "getVideoHeight", "getVideoSize", "getWidth", "getHeight", "getVideoFormat")
                    for (methodName in dimensionMethods) {
                        try {
                            val method = playbackClass.getMethod(methodName)
                            val result = method.invoke(playback)
                            Logger.d("$methodName() returned: $result (${result?.javaClass?.name})")
                            
                            // If it's a VideoSize object, try to extract dimensions
                            if (result != null) {
                                val resultClass = result.javaClass
                                if (resultClass.name.contains("VideoSize") || resultClass.name.contains("Size")) {
                                    try {
                                        val widthField = resultClass.getField("width")
                                        val heightField = resultClass.getField("height")
                                        val videoWidth = widthField.getInt(result)
                                        val videoHeight = heightField.getInt(result)
                                        val aspectRatio = videoWidth.toFloat() / videoHeight.toFloat()
                                        
                                        Logger.d("=== Brightcove Video Dimensions (from getPlayback) ===")
                                        Logger.d("Video - width: $videoWidth, height: $videoHeight")
                                        Logger.d("Video - aspect ratio: $aspectRatio (${videoWidth}:${videoHeight})")
                                        
                                        return
                                    } catch (e: Exception) {
                                        Logger.d("Could not extract dimensions from $methodName result: ${e.message}")
                                    }
                                } else if (result is Int && methodName.contains("Width")) {
                                    // Try to get height as well
                                    try {
                                        val getHeightMethod = playbackClass.getMethod("getVideoHeight")
                                        val videoHeight = getHeightMethod.invoke(playback) as Int
                                        val videoWidth = result as Int
                                        val aspectRatio = videoWidth.toFloat() / videoHeight.toFloat()
                                        
                                        Logger.d("=== Brightcove Video Dimensions (from getPlayback methods) ===")
                                        Logger.d("Video - width: $videoWidth, height: $videoHeight")
                                        Logger.d("Video - aspect ratio: $aspectRatio (${videoWidth}:${videoHeight})")
                                        
                                        return
                                    } catch (e: Exception) {
                                        Logger.d("Could not get height: ${e.message}")
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // Method doesn't exist, try next
                        }
                    }
                    
                    // Try to access ExoPlayer through playback object
                    val playbackMethodNames = listOf("getExoPlayer", "getPlayer", "exoPlayer", "player", "getVideoPlayer")
                    for (methodName in playbackMethodNames) {
                        try {
                            val method = playbackClass.getMethod(methodName)
                            val exoPlayer = method.invoke(playback)
                            if (exoPlayer != null) {
                                Logger.d("Found ExoPlayer via playback.$methodName: ${exoPlayer.javaClass.name}")
                                val exoPlayerClass = exoPlayer.javaClass
                                
                                // Try to get video size
                                try {
                                    val getVideoSizeMethod = exoPlayerClass.getMethod("getVideoSize")
                                    val videoSize = getVideoSizeMethod.invoke(exoPlayer)
                                    if (videoSize != null) {
                                        val videoSizeClass = videoSize.javaClass
                                        var videoWidth = 0
                                        var videoHeight = 0
                                        
                                        try {
                                            val widthField = videoSizeClass.getField("width")
                                            val heightField = videoSizeClass.getField("height")
                                            videoWidth = widthField.getInt(videoSize)
                                            videoHeight = heightField.getInt(videoSize)
                                        } catch (e: Exception) {
                                            try {
                                                val getWidthMethod = videoSizeClass.getMethod("getWidth")
                                                val getHeightMethod = videoSizeClass.getMethod("getHeight")
                                                videoWidth = getWidthMethod.invoke(videoSize) as Int
                                                videoHeight = getHeightMethod.invoke(videoSize) as Int
                                            } catch (e2: Exception) {
                                                Logger.d("Could not get dimensions: ${e2.message}")
                                            }
                                        }
                                        
                                        if (videoWidth > 0 && videoHeight > 0) {
                                            val aspectRatio = videoWidth.toFloat() / videoHeight.toFloat()
                                            
                                            Logger.d("=== Brightcove Video Dimensions (via playback->ExoPlayer) ===")
                                            Logger.d("Video - width: $videoWidth, height: $videoHeight")
                                            Logger.d("Video - aspect ratio: $aspectRatio (${videoWidth}:${videoHeight})")
                                            
                                            return
                                        }
                                    }
                                } catch (e: Exception) {
                                    Logger.d("Could not get video size from playback ExoPlayer: ${e.message}")
                                }
                            }
                        } catch (e: Exception) {
                            // Method doesn't exist
                        }
                    }
                }
            } catch (e: Exception) {
                Logger.d("Could not access getPlayback(): ${e.message}")
            }
            
            Logger.d("Could not access ExoPlayer via any method")
            // Log all available methods on BrightcoveExoPlayerVideoView
            Logger.d("Available methods on BrightcoveExoPlayerVideoView:")
            playerView.javaClass.declaredMethods.filter { 
                it.name.contains("player", ignoreCase = true) || 
                it.name.contains("video", ignoreCase = true) ||
                it.name.contains("exo", ignoreCase = true) ||
                it.name.contains("get", ignoreCase = true)
            }.take(20).forEach { method ->
                Logger.d("  - ${method.name}(${method.parameterTypes.joinToString { it.simpleName }})")
            }
        }
    } catch (e: Exception) {
        Logger.e("Error logging video dimensions: ${e.message}", e)
    }
}

/**
 * Configure video scaling to fill the screen.
 * This ensures the video fills the entire available space.
 */
private fun configureVideoScaling(playerView: BrightcoveExoPlayerVideoView) {
    // Method 1: Try different method names to access ExoPlayer
    try {
        val methodNames = listOf("getExoPlayer", "getPlayer", "getVideoPlayer", "exoPlayer", "player")
        var exoPlayer: Any? = null
        var exoPlayerMethod: java.lang.reflect.Method? = null
        
        for (methodName in methodNames) {
            try {
                val method = playerView.javaClass.getMethod(methodName)
                exoPlayer = method.invoke(playerView)
                if (exoPlayer != null) {
                    exoPlayerMethod = method
                    Logger.d("Found ExoPlayer via method: $methodName")
                    break
                }
            } catch (e: Exception) {
                // Try next method
            }
        }
        
        if (exoPlayer != null) {
            try {
                val exoPlayerClass = exoPlayer.javaClass
                val setVideoScalingModeMethod = exoPlayerClass.getMethod(
                    "setVideoScalingMode",
                    Int::class.java
                )
                // Use SCALE_TO_FIT_WITH_CROPPING to fill screen (crops if needed) = 1
                setVideoScalingModeMethod.invoke(exoPlayer, 1)
                Logger.d("Successfully set video scaling mode to fill screen via ExoPlayer")
                return
            } catch (e: Exception) {
                Logger.d("Could not set video scaling mode on ExoPlayer: ${e.message}")
            }
        }
    } catch (e: Exception) {
        Logger.d("Could not access ExoPlayer: ${e.message}")
    }
    
    // Method 2: Find and configure internal AspectRatioFrameLayout
    try {
        val aspectRatioFrameLayoutClass = Class.forName("com.google.android.exoplayer2.ui.AspectRatioFrameLayout")
        
        // Recursively find AspectRatioFrameLayout
        fun findAspectRatioFrameLayout(view: View): View? {
            if (aspectRatioFrameLayoutClass.isInstance(view)) {
                return view
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    val child = view.getChildAt(i)
                    val found = findAspectRatioFrameLayout(child)
                    if (found != null) return found
                }
            }
            return null
        }
        
        val aspectRatioFrameLayout = findAspectRatioFrameLayout(playerView)
        if (aspectRatioFrameLayout != null) {
            // Try to get the constant value - it's an int constant, value 4 for ZOOM
            // RESIZE_MODE_ZOOM = 4 (from AspectRatioFrameLayout constants)
            val setResizeModeMethod = aspectRatioFrameLayoutClass.getMethod("setResizeMode", Int::class.java)
            setResizeModeMethod.invoke(aspectRatioFrameLayout, 4) // ZOOM mode = 4
            Logger.d("Successfully set resize mode to ZOOM (4) on AspectRatioFrameLayout")
            return
        }
    } catch (e: Exception) {
        Logger.d("Could not find/configure AspectRatioFrameLayout: ${e.message}")
    }
    
    // Method 3: Find and configure internal PlayerView
    try {
        val playerViewClass = Class.forName("com.google.android.exoplayer2.ui.PlayerView")
        
        // Recursively find PlayerView
        fun findPlayerView(view: View): View? {
            if (playerViewClass.isInstance(view)) {
                return view
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    val child = view.getChildAt(i)
                    val found = findPlayerView(child)
                    if (found != null) return found
                }
            }
            return null
        }
        
        val internalPlayerView = findPlayerView(playerView)
        if (internalPlayerView != null) {
            // RESIZE_MODE_ZOOM = 4
            val setResizeModeMethod = playerViewClass.getMethod("setResizeMode", Int::class.java)
            setResizeModeMethod.invoke(internalPlayerView, 4) // ZOOM mode = 4
            Logger.d("Successfully set resize mode to ZOOM (4) on PlayerView")
            return
        }
    } catch (e: Exception) {
        Logger.d("Could not find/configure PlayerView: ${e.message}")
    }
    
    // Method 4: Try to access ExoPlayer via fields
    try {
        val fieldNames = listOf("exoPlayer", "player", "videoPlayer", "mExoPlayer", "mPlayer")
        for (fieldName in fieldNames) {
            try {
                val field = playerView.javaClass.getDeclaredField(fieldName)
                field.isAccessible = true
                val exoPlayer = field.get(playerView)
                if (exoPlayer != null) {
                    val exoPlayerClass = exoPlayer.javaClass
                    val setVideoScalingModeMethod = exoPlayerClass.getMethod(
                        "setVideoScalingMode",
                        Int::class.java
                    )
                    setVideoScalingModeMethod.invoke(exoPlayer, 1)
                    Logger.d("Successfully set video scaling mode via field: $fieldName")
                    return
                }
            } catch (e: Exception) {
                // Try next field
            }
        }
    } catch (e: Exception) {
        Logger.d("Could not access ExoPlayer via fields: ${e.message}")
    }
    
    // Method 5: Try to configure via reflection on BrightcoveExoPlayerVideoView methods
    try {
        val methodNames = listOf("setResizeMode", "setAspectRatioMode", "setVideoAspectRatio", "setVideoScalingMode")
        for (methodName in methodNames) {
            try {
                val method = playerView.javaClass.getMethod(methodName, Int::class.java)
                // Try ZOOM mode (fills screen, may crop) - value 4
                method.invoke(playerView, 4)
                Logger.d("Successfully set resize/scaling mode via $methodName")
                return
            } catch (e: Exception) {
                // Try next method
            }
        }
    } catch (e: Exception) {
        Logger.d("Could not set resize/scaling mode via methods: ${e.message}")
    }
    
    // Method 6: Try to find any ViewGroup and configure its children
    try {
        fun findAndConfigureViews(view: View, depth: Int = 0): Boolean {
            if (depth > 5) return false // Limit recursion depth
            
            val viewClass = view.javaClass.name
            if (viewClass.contains("AspectRatio") || viewClass.contains("PlayerView") || viewClass.contains("VideoView")) {
                try {
                    val setResizeModeMethod = view.javaClass.getMethod("setResizeMode", Int::class.java)
                    setResizeModeMethod.invoke(view, 4)
                    Logger.d("Successfully set resize mode on view: $viewClass")
                    return true
                } catch (e: Exception) {
                    // Try other methods
                }
            }
            
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    if (findAndConfigureViews(view.getChildAt(i), depth + 1)) {
                        return true
                    }
                }
            }
            return false
        }
        
        if (findAndConfigureViews(playerView)) {
            Logger.d("Successfully configured video scaling via recursive view search")
            return
        }
    } catch (e: Exception) {
        Logger.d("Could not configure via recursive search: ${e.message}")
    }
}

/**
 * Set up Brightcove player event listeners.
 * Note: Since we can't access EventEmitter from player view, we'll set up listeners
 * when we can successfully add a video to the player.
 */
private fun setupPlayerEvents(
    playerView: BrightcoveExoPlayerVideoView,
    viewModel: LiveStreamViewModel
) {
    // Configure video scaling when setting up events
    configureVideoScaling(playerView)
    
    // Try to get EventEmitter - if not available, we'll set up listeners later
    val eventEmitter: EventEmitter? = try {
        playerView.eventEmitter
    } catch (e: Exception) {
        null
    }
    
    if (eventEmitter == null) {
        Logger.d("EventEmitter not available for event listeners - will set up when player is ready")
        return
    }

    // Playback started - reconfigure scaling
    eventEmitter.on(com.brightcove.player.event.EventType.PLAY) { 
        // Log dimensions when playback starts
        playerView.postDelayed({
            logVideoDimensions(playerView)
        }, 500) // Delay to ensure video is playing
        
        configureVideoScaling(playerView)
        viewModel.emitPlayerEvent(PlayerEvent.PlaybackStarted) 
    }
    
    // Video ready - reconfigure scaling when video is ready
    eventEmitter.on(com.brightcove.player.event.EventType.READY_TO_PLAY) { event ->
        // Try to get video from event
        val video = try {
            event.properties?.get("video") as? Video
        } catch (e: Exception) {
            null
        }
        
        // Log video dimensions when ready
        playerView.postDelayed({
            logVideoDimensions(playerView, video)
        }, 300) // Delay to ensure video is loaded
        
        configureVideoScaling(playerView)
        // Also configure after a short delay to ensure it's applied
        playerView.postDelayed({
            configureVideoScaling(playerView)
            logVideoDimensions(playerView, video) // Log again after scaling is applied
        }, 200)
        viewModel.emitPlayerEvent(PlayerEvent.VideoLoaded)
    }
    
    // Try to listen for video dimension changes via Brightcove events
    try {
        // Check if there's a VIDEO_SIZE_CHANGED or similar event
        val videoSizeEventTypes = listOf(
            "VIDEO_SIZE_CHANGED",
            "VIDEO_DIMENSIONS_CHANGED",
            "VIDEO_METADATA_AVAILABLE"
        )
        for (eventTypeName in videoSizeEventTypes) {
            try {
                val eventTypeField = com.brightcove.player.event.EventType::class.java.getField(eventTypeName)
                val eventType = eventTypeField.get(null) as? String
                if (eventType != null) {
                    eventEmitter.on(eventType) { event ->
                        Logger.d("Video size changed event received: $eventTypeName")
                        logVideoDimensions(playerView)
                        // Try to extract dimensions from event properties
                        try {
                            val properties = event.properties
                            properties?.let {
                                Logger.d("Event properties: $it")
                                it.forEach { (key, value) ->
                                    if (key.toString().contains("width", ignoreCase = true) ||
                                        key.toString().contains("height", ignoreCase = true) ||
                                        key.toString().contains("size", ignoreCase = true) ||
                                        key.toString().contains("dimension", ignoreCase = true)) {
                                        Logger.d("Video dimension property - $key: $value")
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Logger.d("Could not read event properties: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                // Event type doesn't exist, try next
            }
        }
    } catch (e: Exception) {
        Logger.d("Could not set up video size event listeners: ${e.message}")
    }

    // Playback paused
    eventEmitter.on(com.brightcove.player.event.EventType.PAUSE) { viewModel.emitPlayerEvent(PlayerEvent.PlaybackPaused) }

    // Buffering started
    eventEmitter.on(com.brightcove.player.event.EventType.BUFFERING_STARTED) { viewModel.emitPlayerEvent(PlayerEvent.Buffering) }

    // Buffering completed
    eventEmitter.on(com.brightcove.player.event.EventType.BUFFERING_COMPLETED) { viewModel.emitPlayerEvent(PlayerEvent.BufferingComplete) }

    // Playback error
    eventEmitter.on(com.brightcove.player.event.EventType.ERROR) { event -> // Get error message from event properties
        val errorMessage = try {
            val errorMsg = event.properties[Event.ERROR_MESSAGE]
            errorMsg as? String ?: (errorMsg?.toString() ?: "Unknown playback error")
        } catch (e: Exception) {
            "Unknown playback error"
        }

        viewModel.emitPlayerEvent(
            PlayerEvent.PlaybackError(
                errorMessage = errorMessage,
                errorCode = SDKError.PLAYBACK_ERROR
            )
        )
    }

    // Playback completed
    eventEmitter.on(com.brightcove.player.event.EventType.COMPLETED) { viewModel.emitPlayerEvent(PlayerEvent.PlaybackCompleted) }

    // Fullscreen enter/exit with guard to avoid duplicate handling
    var isFullscreen = false

    eventEmitter.on(com.brightcove.player.event.EventType.ENTER_FULL_SCREEN) {
        if (isFullscreen) return@on
        isFullscreen = true
        val activity = (playerView.context as? Activity) ?: return@on
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        WindowInsetsControllerCompat(activity.window, playerView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    eventEmitter.on(com.brightcove.player.event.EventType.EXIT_FULL_SCREEN) {
        if (!isFullscreen) return@on
        isFullscreen = false
        val activity = (playerView.context as? Activity) ?: return@on
        WindowCompat.setDecorFitsSystemWindows(activity.window, true)
        WindowInsetsControllerCompat(activity.window, playerView).show(WindowInsetsCompat.Type.systemBars())
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
}

