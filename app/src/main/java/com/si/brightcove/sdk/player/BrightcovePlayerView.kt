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
    Logger.d(">>> BrightcovePlayerView composable called <<<", "BrightcoveSDK")
    val video by viewModel.video.collectAsState()
    val context = LocalContext.current
    Logger.d("BrightcovePlayerView - video state: ${video?.id ?: "null"}", "BrightcoveSDK")
    
    AndroidView(
        factory = { ctx ->
            Logger.d("=== Creating BrightcoveExoPlayerVideoView ===", "BrightcoveSDK")
            val playerView = BrightcoveExoPlayerVideoView(ctx)
            Logger.d("Player view created: ${playerView.javaClass.name}")
            
            val frameLayout = FrameLayout(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                addView(playerView)
            }
            Logger.d("Player view added to FrameLayout")
            
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
                        Logger.d("FullScreenController constructors: ${constructors.map { it.parameterTypes.joinToString { it.simpleName } }}", "BrightcoveSDK")
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
                                Logger.d("Constructor ${params.map { it.simpleName }} failed: ${e.message}", "BrightcoveSDK")
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
                                    Logger.w("BrightcoveSDK", "Could not set FullScreenController via reflection field in any class")
                                }
                            }
                        }
                    } else {
                        Logger.w("BrightcoveSDK", "Could not create FullScreenController with any known constructor.")
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
                Logger.w("Error initializing FullScreenController: ${e.message}", e, "BrightcoveSDK")
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
                Logger.e("Exception getting EventEmitter via property: ${e.message}", e, "BrightcoveSDK")
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
                    Logger.e("Error inspecting class: ${e.message}", "BrightcoveSDK")
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
                        Logger.w("BrightcoveSDK", "EventEmitter is null - will retry")
                        false
                    }
                } catch (e: Exception) {
                    Logger.e("Exception accessing EventEmitter: ${e.javaClass.simpleName} - ${e.message}", e, "BrightcoveSDK")
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
            val playerView = frameLayout.tag as? BrightcoveExoPlayerVideoView
            
            // Update controls visibility on each recomposition
            playerView?.let { 
                applyControlsVisibility(it, showControls)
                enforceControlsVisibility(it, showControls, frameLayout)
            }
            
            // Note: Controls visibility in BrightcoveExoPlayerVideoView is managed internally
            // The showControls parameter is kept for API compatibility but may not directly
            // control the native Brightcove controls.
            
            // Update player view reference in ViewModel only if EventEmitter is available
            val eventEmitterReady = try {
                playerView?.eventEmitter != null
            } catch (e: Exception) {
                false
            }
            viewModel.setPlayerView(playerView, eventEmitterReady = eventEmitterReady)
            
            // Only update video if player view is available and properly initialized
            video?.let { video ->
                playerView?.let { view ->
                    // Check if view is visible and has dimensions before adding video
                    val isVisible = view.visibility == android.view.View.VISIBLE
                    val hasDimensions = view.width > 0 && view.height > 0
                    
                    Logger.d("Attempting to add video - visible: $isVisible, hasDimensions: $hasDimensions, width: ${view.width}, height: ${view.height}")
                    
                    if (isVisible && hasDimensions) {
                        // View is visible and has dimensions, try to add video
                        try {
                            Logger.d("Adding video to player: ${video.id}")
                            view.add(video)
                            view.start()
                            Logger.d("Video added and started successfully")
                        } catch (e: Exception) {
                            Logger.w("Error adding video: ${e.message}, will retry", "BrightcoveSDK")
                            // Retry after a shorter delay (reduced from 2000ms for faster loading)
                            frameLayout.postDelayed({
                                try {
                                    Logger.d("Retrying to add video after delay")
                                    view.add(video)
                                    view.start()
                                    Logger.d("Video added successfully on retry")
                                } catch (ex: Exception) {
                                    Logger.w("Error adding video on retry: ${ex.message}", "BrightcoveSDK")
                                }
                            }, 500) // Reduced from 2000ms to 500ms
                        }
                    } else {
                        // View not ready yet, wait for it to be laid out
                        Logger.d("Player view not ready (visible=$isVisible, hasDimensions=$hasDimensions), waiting for layout")
                        
                        val viewTreeObserver = view.viewTreeObserver
                        viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                            override fun onGlobalLayout() {
                                view.viewTreeObserver.removeOnGlobalLayoutListener(this)
                                
                                // Wait for MediaPlayback to initialize - try multiple times with increasing delays
                                var retryCount = 0
                                val maxRetries = 20 // Reduced to 20 attempts (about 4 seconds total) for faster loading
                                
                                fun tryAddVideo() {
                                    try {
                                        Logger.d("Attempting to add video (attempt ${retryCount + 1}/$maxRetries)")
                                        view.add(video)
                                        view.start()
                                        Logger.d("Video added and started successfully!")
                                        
                                        // Set up event listeners
                                        setupPlayerEvents(view, viewModel)
                                    } catch (e: Exception) {
                                        // MediaPlayback not ready yet, retry with increasing delays
                                        if (retryCount < maxRetries) {
                                            retryCount++
                                            val delay = (retryCount * 200).toLong() // 200ms, 400ms, 600ms, etc. (reduced from 500ms)
                                            Logger.d("Error adding video: ${e.message}, retrying in ${delay}ms (attempt $retryCount/$maxRetries)")
                                            frameLayout.postDelayed({ tryAddVideo() }, delay)
                                        } else {
                                            Logger.e("Failed to add video after $maxRetries attempts: ${e.message}", "BrightcoveSDK")
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
        Logger.w("BrightcoveSDK", "Error trying to disable fullscreen button: ${e.message}")
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
                Logger.e("Exception accessing EventEmitter on attempt $attempts", e, "BrightcoveSDK")
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
                Logger.e("BrightcoveSDK", "EventEmitter not available after $maxAttempts attempts! Setting player view anyway - Catalog operations will fail until EventEmitter is available")
                // Set player view anyway - the ViewModel will keep retrying
                viewModel.setPlayerView(playerView, eventEmitterReady = false)
            }
        }
    }
    
    frameLayout.post(checkEventEmitter)
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

    // Playback started
    eventEmitter.on(com.brightcove.player.event.EventType.PLAY) { viewModel.emitPlayerEvent(PlayerEvent.PlaybackStarted) }

    // Playback paused
    eventEmitter.on(com.brightcove.player.event.EventType.PAUSE) { viewModel.emitPlayerEvent(PlayerEvent.PlaybackPaused) }

    // Video ready to play (replaces VIDEO_LOADED)
    eventEmitter.on(com.brightcove.player.event.EventType.READY_TO_PLAY) { viewModel.emitPlayerEvent(PlayerEvent.VideoLoaded) }

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

