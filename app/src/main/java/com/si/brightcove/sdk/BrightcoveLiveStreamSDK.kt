package com.si.brightcove.sdk

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.brightcove.player.edge.Catalog
import com.brightcove.player.event.EventEmitter
import com.brightcove.player.view.BrightcoveExoPlayerVideoView
import com.si.brightcove.sdk.analytics.AnalyticsManager
import com.si.brightcove.sdk.config.ConfigurationManager
import com.si.brightcove.sdk.config.SDKConfig
import com.si.brightcove.sdk.config.StreamConfigData
import com.si.brightcove.sdk.model.EventType as SdkEventType
import com.si.brightcove.sdk.model.Environment as SdkEnvironment
import com.si.brightcove.sdk.ui.Logger
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Listener interface for configuration changes.
 */
interface ConfigurationChangeListener {
    /**
     * Called when configuration is updated from API.
     * @param newConfig The new configuration data
     */
    fun onConfigurationChanged(newConfig: StreamConfigData)
}

/**
 * Main SDK initialization class.
 * Must be initialized once per app lifecycle before using LiveStreamScreen.
 */
object BrightcoveLiveStreamSDK {

    private var isInitialized = false
    private var sdkConfig: SDKConfig? = null
    @SuppressLint("StaticFieldLeak")
    private var analyticsManager: AnalyticsManager? = null
    private var standaloneEventEmitter: EventEmitter? = null

    // Periodic config updates
    private var configUpdateHandler: Handler? = null
    private var configUpdateRunnable: Runnable? = null
    private var isPeriodicUpdatesEnabled = false

    // Configuration change listeners
    private val configChangeListeners = CopyOnWriteArrayList<ConfigurationChangeListener>()


    /**
     * Initialize the SDK with required parameters.
     *
     * @param context Application context
     * @param eventType Required event type (MOBILE/CAMERA)
     * @param environment Required environment (PROD/NON_PROD)
     * @param locales Required locales identifier for configuration
     * @param state Configuration behavior toggle: true = existing behavior, false = config-driven behavior
     * @param debug Optional debug flag (default: false)
     * @throws IllegalStateException if SDK is already initialized
     */
    @JvmStatic
    fun initialize(
        context: Context,
        eventType: SdkEventType,
        environment: SdkEnvironment,
        locales: String,
        debug: Boolean
    ) {
        initialize(
            context = context,
            eventType = eventType,
            environment = environment,
            locales = locales,
            debug = debug,
            autoRetryOnError = true,
            maxRetryAttempts = 3,
            retryBackoffMultiplier = 2.0
        )
    }
    
    /**
     * Initialize the SDK with advanced configuration options.
     *
     * @param context Application context
     * @param eventType Required event type (MOBILE/CAMERA)
     * @param environment Required environment (PROD/NON_PROD)
     * @param locales Required locales identifier for configuration
     * @param state Configuration behavior toggle: true = existing behavior, false = config-driven behavior
     * @param debug Optional debug flag (default: false)
     * @param autoRetryOnError Whether to automatically retry on errors (default: true)
     * @param maxRetryAttempts Maximum number of retry attempts (default: 3)
     * @param retryBackoffMultiplier Multiplier for exponential backoff (default: 2.0)
     * @throws IllegalStateException if SDK is already initialized
     */
    @JvmStatic
    fun initialize(
        context: Context,
        eventType: SdkEventType,
        environment: SdkEnvironment,
        locales: String,
        debug: Boolean = false,
        autoRetryOnError: Boolean = true,
        maxRetryAttempts: Int = 3,
        retryBackoffMultiplier: Double = 2.0
    ) {
        if (isInitialized) {
            throw IllegalStateException("BrightcoveLiveStreamSDK is already initialized. Initialize only once per app lifecycle.")
        }

        val appContext = context.applicationContext

        // Load configuration if state=false (config-driven behavior)
        var configVideoId = ""
        var configState = ""
        var configMediaType = com.si.brightcove.sdk.model.MediaType.IMAGE
        var configMediaUrl = ""
        var configMediaTitle = ""
        var configMediaLoop = true
        var configIntervals = emptyMap<String, Int>()

            val configManager = ConfigurationManager.getInstance()
//            val apiUrl = "https://raw.githubusercontent.com/si-nitesh-kukreja/si-brightcove-sdk/refs/heads/master/app/src/main/assets/stream_config.json"
            val apiUrl = "https://squirrel-prepared-marlin.ngrok-free.app/stream_config.json"

            // Try API first, fallback to local assets if API fails
            var configResult = configManager.loadConfigurationFromApi(apiUrl, debug)
            if (configResult.isFailure && debug) {
                Logger.w("API configuration loading failed, falling back to local assets")
                configResult = configManager.loadConfiguration(appContext, debug)
            }

            if (configResult.isSuccess) {
                val streamConfigResult = configManager.getConfiguration(eventType, environment, locales)
                if (streamConfigResult.isSuccess) {
                    val streamConfig = streamConfigResult.getOrThrow()
                    configVideoId = streamConfig.videoId
                    configState = streamConfig.state
                    configMediaType = streamConfig.mediaType
                    configMediaUrl = streamConfig.mediaUrl
                    configMediaTitle = streamConfig.mediaTitle
                    configMediaLoop = streamConfig.mediaLoop
                    configIntervals = streamConfig.intervals

                    if (debug) {
                        Logger.d("Loaded configuration: videoId=$configVideoId, state=$configState, mediaType=$configMediaType, intervals=$configIntervals")
                    }
                } else {
                    if (debug) {
                        Logger.w("Failed to get configuration for locales '$locales': ${streamConfigResult.exceptionOrNull()?.message}")
                    }
                    // Continue with default values
                }
            } else {
                if (debug) {
                    Logger.w("Failed to load configuration: ${configResult.exceptionOrNull()?.message}")
                }
                // Continue with default values
            }


        sdkConfig = SDKConfig(
            eventType = eventType,
            environment = environment,
            debug = debug,
            autoRetryOnError = autoRetryOnError,
            maxRetryAttempts = maxRetryAttempts,
            retryBackoffMultiplier = retryBackoffMultiplier,
            locales = locales,
            configVideoId = configVideoId,
            configState = configState,
            configMediaType = configMediaType,
            configMediaUrl = configMediaUrl,
            configMediaTitle = configMediaTitle,
            configMediaLoop = configMediaLoop,
            configIntervals = configIntervals
        )
        
        analyticsManager = AnalyticsManager(appContext, debug)
        
        // Create a standalone EventEmitter for Catalog operations
        // This is needed because BrightcoveExoPlayerVideoView doesn't expose eventEmitter property
        // EventEmitter is an interface, so we need to find the implementation class
        try {
            // Try to find and instantiate the EventEmitter implementation class
            val eventEmitterClass = try {
                Class.forName("com.brightcove.player.event.EventEmitterImpl")
            } catch (e: ClassNotFoundException) {
                try {
                    Class.forName("com.brightcove.player.event.DefaultEventEmitter")
                } catch (e2: ClassNotFoundException) {
                    null
                }
            }
            
            if (eventEmitterClass != null) {
                val constructor = eventEmitterClass.getDeclaredConstructor()
                constructor.isAccessible = true
                standaloneEventEmitter = constructor.newInstance() as EventEmitter
                if (debug) {
                    Logger.d("Standalone EventEmitter created successfully using ${eventEmitterClass.simpleName}")
                }
            } else {
                if (debug) {
                    Logger.w("Could not find EventEmitter implementation class - will try to get from player view when available")
                }
            }
        } catch (e: Exception) {
            if (debug) {
                Logger.e("Failed to create standalone EventEmitter: ${e.message}", e)
            }
        }
        
        isInitialized = true

        // Start periodic config updates
        startPeriodicConfigUpdates(context, debug)
    }

    /**
     * Update the SDK configuration with new values from API.
     * This is called when configuration changes dynamically.
     */
    internal fun updateSdkConfiguration(newConfigData: StreamConfigData) {
        // Recreate SDK config with updated values
        val currentConfig = sdkConfig
        if (currentConfig != null) {
            sdkConfig = SDKConfig(
                eventType = currentConfig.eventType,
                environment = currentConfig.environment,
                debug = currentConfig.debug,
                autoRetryOnError = currentConfig.autoRetryOnError,
                maxRetryAttempts = currentConfig.maxRetryAttempts,
                retryBackoffMultiplier = currentConfig.retryBackoffMultiplier,
                locales = currentConfig.locales,
                configVideoId = newConfigData.videoId,
                configState = newConfigData.state,
                configMediaType = newConfigData.mediaType,
                configMediaUrl = newConfigData.mediaUrl,
                configMediaTitle = newConfigData.mediaTitle,
                configMediaLoop = newConfigData.mediaLoop,
                configIntervals = newConfigData.intervals
            )

            if (currentConfig.debug) {
                Logger.d("SDK configuration updated with new values")
            }
        }
    }
    
    /**
     * Get the SDK configuration.
     * @throws IllegalStateException if SDK is not initialized
     */
    internal fun getConfig(): SDKConfig {
        return sdkConfig ?: throw IllegalStateException(
            "BrightcoveLiveStreamSDK is not initialized. Call initialize() first."
        )
    }
    
    /**
     * Get the analytics manager.
     */
    internal fun getAnalyticsManager(): AnalyticsManager? {
        return analyticsManager
    }
    
    /**
     * Check if SDK is initialized.
     */
    @JvmStatic
    fun isInitialized(): Boolean = isInitialized

    /**
     * Stop periodic configuration updates.
     * Useful when app is going to background or for cleanup.
     */
    @JvmStatic
    fun stopPeriodicUpdates() {
        stopPeriodicConfigUpdates()
    }

    /**
     * Manually trigger a configuration update from API.
     * @param debug Enable debug logging for this update
     */
    @JvmStatic
    fun updateConfigurationNow(debug: Boolean = false) {
        if (isInitialized) {
            updateConfigurationFromApi(debug)
        }
    }

    /**
     * Register a listener to be notified when configuration changes.
     * @param listener The listener to register
     */
    @JvmStatic
    fun addConfigurationChangeListener(listener: ConfigurationChangeListener) {
        if (!configChangeListeners.contains(listener)) {
            configChangeListeners.add(listener)
        }
    }

    /**
     * Unregister a configuration change listener.
     * @param listener The listener to remove
     */
    @JvmStatic
    fun removeConfigurationChangeListener(listener: ConfigurationChangeListener) {
        configChangeListeners.remove(listener)
    }

    /**
     * Remove all configuration change listeners.
     */
    @JvmStatic
    fun clearConfigurationChangeListeners() {
        configChangeListeners.clear()
    }
    
    /**
     * Get the standalone EventEmitter instance.
     * This can be used to set on player views to initialize MediaPlayback.
     */
    internal fun getStandaloneEventEmitter(): EventEmitter? = standaloneEventEmitter
    
    /**
     * Resolve credentials based on event type/environment with optional overrides.
     */
    
    /**
     * Create a Brightcove Catalog instance for video retrieval using the player view's EventEmitter.
     * 
     * @param brightcoveVideoView BrightcoveExoPlayerVideoView instance (can be null)
     * @return Catalog instance or null if player view is not available
     */
    internal fun getBrightcoveEmitter(
        brightcoveVideoView: BrightcoveExoPlayerVideoView?
    ): Catalog? {
        val config = getConfig()
        val accountId = config.getBrightcoveAccountId()
        val policyKey = config.getBrightcovePolicyKey()

        
        // Try to get EventEmitter from player view first (if available)
        var eventEmitter: EventEmitter? = null
        try {
            eventEmitter = brightcoveVideoView?.eventEmitter
        } catch (e: Exception) {
            if (config.debug) {
                Logger.d("Could not access EventEmitter from player view: ${e.message}")
            }
        }
        
        // If not available from player view, use standalone EventEmitter
        if (eventEmitter == null) {
            eventEmitter = standaloneEventEmitter
            if (config.debug && eventEmitter != null) {
                Logger.d("Using standalone EventEmitter for Catalog creation")
            }
        }
        
        if (eventEmitter == null) {
            if (config.debug) {
                Logger.e("No EventEmitter available - neither from player view nor standalone")
            }
            return null
        }
        
        if (config.debug) {
            Logger.d("Creating Catalog with AccountID: $")
        }

        Logger.d("AccountID $accountId")
        Logger.d("PolicyKey $policyKey")

        return try {
            Catalog.Builder(eventEmitter, accountId)
                .setBaseURL(Catalog.DEFAULT_EDGE_BASE_URL)
                .setPolicy(policyKey)
                .build()
        } catch (e: Exception) {
            if (config.debug) {
                Logger.e("Failed to create Catalog", e)
            }
            null
        }
    }

    /**
     * Start periodic configuration updates from API.
     * Checks for config updates using the current effective polling interval.
     * This means config updates happen at the same frequency as stream polling.
     */
    private fun startPeriodicConfigUpdates(context: Context, debug: Boolean) {
        if (isPeriodicUpdatesEnabled) return

        configUpdateHandler = Handler(Looper.getMainLooper())
        configUpdateRunnable = object : Runnable {
            override fun run() {
                updateConfigurationFromApi(debug)
                // Schedule next update using current effective polling interval
                val nextInterval = sdkConfig?.getEffectivePollingInterval() ?: (1 * 60 * 1000L) // fallback to 1 minutes
                configUpdateHandler?.postDelayed(this, nextInterval)
            }
        }

        isPeriodicUpdatesEnabled = true
        // Start the first update using current effective polling interval
        val initialInterval = sdkConfig?.getEffectivePollingInterval() ?: (1 * 60 * 1000L) // fallback to 1 minutes
        configUpdateHandler?.postDelayed(configUpdateRunnable!!, initialInterval)

        if (debug) {
            val intervalMinutes = initialInterval / 1000 / 60
            Logger.d("Started periodic configuration updates (every $intervalMinutes minutes based on current polling interval)")
        }
    }

    /**
     * Stop periodic configuration updates.
     */
    private fun stopPeriodicConfigUpdates() {
        configUpdateHandler?.removeCallbacks(configUpdateRunnable!!)
        configUpdateHandler = null
        configUpdateRunnable = null
        isPeriodicUpdatesEnabled = false
    }

    /**
     * Update configuration from API if changes are detected.
     */
    private fun updateConfigurationFromApi(debug: Boolean) {
        try {
            val configManager = ConfigurationManager.getInstance()
//            val apiUrl = "https://raw.githubusercontent.com/si-nitesh-kukreja/si-brightcove-sdk/refs/heads/master/app/src/main/assets/stream_config.json"
            val apiUrl = "https://squirrel-prepared-marlin.ngrok-free.app/stream_config.json"

            // Store current config state before attempting update
            val currentConfigResult = if (configManager.isConfigurationLoaded()) {
                configManager.getConfiguration(
                    sdkConfig?.eventType ?: SdkEventType.mobile,
                    sdkConfig?.environment ?: SdkEnvironment.prod,
                    sdkConfig?.locales ?: "en"
                )
            } else null

            val configResult = configManager.loadConfigurationFromApi(apiUrl, debug)
            if (configResult.isSuccess) {
                // Check if configuration has actually changed by comparing with previous state
                if (currentConfigResult?.isSuccess == true) {
                    val newData = configManager.getConfiguration(
                        sdkConfig?.eventType ?: SdkEventType.mobile,
                        sdkConfig?.environment ?: SdkEnvironment.prod,
                        sdkConfig?.locales ?: "en"
                    ).getOrThrow()

                    onConfigurationUpdated(newData)
//                        // Notify listeners or update UI if needed
                } else {
                    // First time loading config
                    val newData = configManager.getConfiguration(
                        sdkConfig?.eventType ?: SdkEventType.mobile,
                        sdkConfig?.environment ?: SdkEnvironment.prod,
                        sdkConfig?.locales ?: "en"
                    ).getOrThrow()

                    if (debug) {
                        Logger.d("Configuration loaded from API for first time: videoId=${newData.videoId}, state=${newData.state}")
                    }
                    onConfigurationUpdated(newData)
                }
            } else {
                if (debug) {
                    Logger.d("Periodic config update failed: ${configResult.exceptionOrNull()?.message}")
                }
            }
        } catch (e: Exception) {
            if (debug) {
                Logger.e("Error during periodic config update: ${e.message}")
            }
        }
    }


    /**
     * Called when configuration is updated from API.
     * Notifies all registered listeners of the configuration change.
     */
    internal fun onConfigurationUpdated(newConfig: StreamConfigData) {
        if (sdkConfig?.debug == true) {
            Logger.d("Configuration updated: videoId=${newConfig.videoId}, state=${newConfig.state}")
        }

        // Update the SDK configuration with new values
        updateSdkConfiguration(newConfig)

        // Notify all registered listeners
        configChangeListeners.forEach { listener ->
            try {
                listener.onConfigurationChanged(newConfig)
            } catch (e: Exception) {
                if (sdkConfig?.debug == true) {
                    Logger.e("Error notifying configuration listener: ${e.message}")
                }
            }
        }
    }
}

