package com.si.brightcove.sdk

import android.annotation.SuppressLint
import android.content.Context
import com.brightcove.player.edge.Catalog
import com.brightcove.player.event.EventEmitter
import com.brightcove.player.view.BrightcoveExoPlayerVideoView
import com.si.brightcove.sdk.analytics.AnalyticsManager
import com.si.brightcove.sdk.config.ConfigurationManager
import com.si.brightcove.sdk.config.SDKConfig
import com.si.brightcove.sdk.model.EventType as SdkEventType
import com.si.brightcove.sdk.model.Environment as SdkEnvironment
import com.si.brightcove.sdk.ui.Logger

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

    // Hardcoded credentials mapping based on eventType/environment
    // Replace these placeholders with real account/policy values.

    private const val VIDEO_ID_MOBILE_PROD = "6384389158112"
    private const val VIDEO_ID_CAMERA_PROD = "6384085229112"
    private const val VIDEO_ID_MOBILE_NON_PROD = "6384389158112"
    private const val VIDEO_ID_CAMERA_NON_PROD = "6384085229112"

    private const val ACCOUNT_ID_MOBILE_PROD = "6415818918001"
    private const val ACCOUNT_ID_CAMERA_PROD = "6415818918001"
    private const val ACCOUNT_ID_MOBILE_NON_PROD = "6415818918001"
    private const val ACCOUNT_ID_CAMERA_NON_PROD = "6415818918001"

    private const val POLICY_KEY_MOBILE_PROD = "BCpkADawqM3ikTFBoFaNzjghiJjf1GzxYQ0kOqZTl_VBJfjxqdil2A0wfqN_tDj8CSJNwPPsz9EQX8unZYHCkXq6nq5THsJ9ShpPuWoKaCCTuymjtweUk4DWqbdBNgF5ZOeG1DDeoaMQzJIoVa92V09iU5ET5R2meoG3FQ"
    private const val POLICY_KEY_CAMERA_PROD = "BCpkADawqM3ikTFBoFaNzjghiJjf1GzxYQ0kOqZTl_VBJfjxqdil2A0wfqN_tDj8CSJNwPPsz9EQX8unZYHCkXq6nq5THsJ9ShpPuWoKaCCTuymjtweUk4DWqbdBNgF5ZOeG1DDeoaMQzJIoVa92V09iU5ET5R2meoG3FQ"
    private const val POLICY_KEY_MOBILE_NON_PROD = "BCpkADawqM3ikTFBoFaNzjghiJjf1GzxYQ0kOqZTl_VBJfjxqdil2A0wfqN_tDj8CSJNwPPsz9EQX8unZYHCkXq6nq5THsJ9ShpPuWoKaCCTuymjtweUk4DWqbdBNgF5ZOeG1DDeoaMQzJIoVa92V09iU5ET5R2meoG3FQ"
    private const val POLICY_KEY_CAMERA_NON_PROD = "BCpkADawqM3ikTFBoFaNzjghiJjf1GzxYQ0kOqZTl_VBJfjxqdil2A0wfqN_tDj8CSJNwPPsz9EQX8unZYHCkXq6nq5THsJ9ShpPuWoKaCCTuymjtweUk4DWqbdBNgF5ZOeG1DDeoaMQzJIoVa92V09iU5ET5R2meoG3FQ"

    /**
     * Initialize the SDK with required parameters.
     *
     * @param context Application context
     * @param eventType Required event type (MOBILE/CAMERA)
     * @param environment Required environment (PROD/NON_PROD)
     * @param locale Required locale identifier for configuration
     * @param state Configuration behavior toggle: true = existing behavior, false = config-driven behavior
     * @param videoId Brightcove Video ID for live stream (optional override, leave empty if not used)
     * @param accountId Optional override for Brightcove Account ID
     * @param policyKey Optional override for Brightcove Policy Key
     * @param debug Optional debug flag (default: false)
     * @throws IllegalStateException if SDK is already initialized
     */
    @JvmStatic
    fun initialize(
        context: Context,
        eventType: SdkEventType,
        environment: SdkEnvironment,
        locale: String,
        state: Boolean,
        videoId: String = "",
        accountId: String? = null,
        policyKey: String? = null,
        debug: Boolean
    ) {
        initialize(
            context = context,
            eventType = eventType,
            environment = environment,
            locale = locale,
            state = state,
            videoId = videoId,
            accountId = accountId,
            policyKey = policyKey,
            debug = debug,
            pollingIntervalMs = 5_000,
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
     * @param locale Required locale identifier for configuration
     * @param state Configuration behavior toggle: true = existing behavior, false = config-driven behavior
     * @param videoId Brightcove Video ID for live stream (optional override, leave empty if not used)
     * @param accountId Optional override for Brightcove Account ID
     * @param policyKey Optional override for Brightcove Policy Key
     * @param debug Optional debug flag (default: false)
     * @param pollingIntervalMs Interval in milliseconds to check for stream availability (default: 5000)
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
        locale: String,
        state: Boolean,
        videoId: String = "",
        accountId: String? = null,
        policyKey: String? = null,
        debug: Boolean = false,
        pollingIntervalMs: Long = 5_000, // Default 5 seconds (reduced for faster loading)
        autoRetryOnError: Boolean = true,
        maxRetryAttempts: Int = 3,
        retryBackoffMultiplier: Double = 2.0
    ) {
        if (isInitialized) {
            throw IllegalStateException("BrightcoveLiveStreamSDK is already initialized. Initialize only once per app lifecycle.")
        }

        val appContext = context.applicationContext

        val resolvedCredentials = resolveCredentials(eventType, environment, accountId, policyKey, videoId)
        val resolvedAccountId = resolvedCredentials.first
        val resolvedPolicyKey = resolvedCredentials.second
        val resolvedVideoId = resolvedCredentials.third

        // Load configuration if state=false (config-driven behavior)
        var configVideoId = ""
        var configState = ""
        var configMediaType = com.si.brightcove.sdk.model.MediaType.IMAGE
        var configMediaUrl = ""
        var configMediaTitle = ""
        var configMediaLoop = true

        if (!state) {
            val configManager = ConfigurationManager.getInstance()
            val configResult = configManager.loadConfiguration(appContext, debug)

            if (configResult.isSuccess) {
                val streamConfigResult = configManager.getConfiguration(eventType, environment, locale)
                if (streamConfigResult.isSuccess) {
                    val streamConfig = streamConfigResult.getOrThrow()
                    configVideoId = streamConfig.videoId
                    configState = streamConfig.state
                    configMediaType = streamConfig.mediaType
                    configMediaUrl = streamConfig.mediaUrl
                    configMediaTitle = streamConfig.mediaTitle
                    configMediaLoop = streamConfig.mediaLoop

                    if (debug) {
                        Logger.d("Loaded configuration: videoId=$configVideoId, state=$configState, mediaType=$configMediaType")
                    }
                } else {
                    if (debug) {
                        Logger.w("Failed to get configuration for locale '$locale': ${streamConfigResult.exceptionOrNull()?.message}")
                    }
                    // Continue with default values
                }
            } else {
                if (debug) {
                    Logger.w("Failed to load configuration: ${configResult.exceptionOrNull()?.message}")
                }
                // Continue with default values
            }
        }

        sdkConfig = SDKConfig(
            accountId = resolvedAccountId,
            policyKey = resolvedPolicyKey,
            videoId = resolvedVideoId,
            eventType = eventType,
            environment = environment,
            debug = debug,
            pollingIntervalMs = pollingIntervalMs,
            autoRetryOnError = autoRetryOnError,
            maxRetryAttempts = maxRetryAttempts,
            retryBackoffMultiplier = retryBackoffMultiplier,
            locale = locale,
            useConfig = state,
            configVideoId = configVideoId,
            configState = configState,
            configMediaType = configMediaType,
            configMediaUrl = configMediaUrl,
            configMediaTitle = configMediaTitle,
            configMediaLoop = configMediaLoop
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
     * Get the standalone EventEmitter instance.
     * This can be used to set on player views to initialize MediaPlayback.
     */
    internal fun getStandaloneEventEmitter(): EventEmitter? = standaloneEventEmitter
    
    /**
     * Resolve credentials based on event type/environment with optional overrides.
     */
    private fun resolveCredentials(
        eventType: SdkEventType,
        environment: SdkEnvironment,
        accountIdOverride: String?,
        policyKeyOverride: String?,
        videoIdOverride: String?
    ): Triple<String, String, String> {
        val mappedAccountId = when {
            eventType == SdkEventType.mobile && environment == SdkEnvironment.prod -> ACCOUNT_ID_MOBILE_PROD
            eventType == SdkEventType.camera && environment == SdkEnvironment.prod -> ACCOUNT_ID_CAMERA_PROD
            eventType == SdkEventType.mobile && environment == SdkEnvironment.nonProd -> ACCOUNT_ID_MOBILE_NON_PROD
            else -> ACCOUNT_ID_CAMERA_NON_PROD
        }
        val mappedPolicyKey = when {
            eventType == SdkEventType.mobile && environment == SdkEnvironment.prod -> POLICY_KEY_MOBILE_PROD
            eventType == SdkEventType.camera && environment == SdkEnvironment.prod -> POLICY_KEY_CAMERA_PROD
            eventType == SdkEventType.mobile && environment == SdkEnvironment.nonProd -> POLICY_KEY_MOBILE_NON_PROD
            else -> POLICY_KEY_CAMERA_NON_PROD
        }
        val mappedVideoId = when {
            eventType == SdkEventType.mobile && environment == SdkEnvironment.prod -> VIDEO_ID_MOBILE_PROD
            eventType == SdkEventType.camera && environment == SdkEnvironment.prod -> VIDEO_ID_CAMERA_PROD
            eventType == SdkEventType.mobile && environment == SdkEnvironment.nonProd -> VIDEO_ID_MOBILE_NON_PROD
            else -> VIDEO_ID_CAMERA_NON_PROD
        }
        
        val finalAccountId = accountIdOverride?.takeIf { it.isNotBlank() } ?: mappedAccountId
        val finalPolicyKey = policyKeyOverride?.takeIf { it.isNotBlank() } ?: mappedPolicyKey
        val finalVideoId = videoIdOverride?.takeIf { it.isNotBlank() } ?: mappedVideoId
        return Triple(finalAccountId, finalPolicyKey, finalVideoId)
    }
    
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
}

