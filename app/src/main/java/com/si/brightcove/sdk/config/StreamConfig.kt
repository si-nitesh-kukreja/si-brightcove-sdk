package com.si.brightcove.sdk.config

import com.si.brightcove.sdk.model.MediaType

/**
 * Configuration for a specific locale in a stream configuration.
 */
data class LocaleConfig(
    val mediaType: MediaType,
    val mediaUrl: String = "",
    val mediaTitle: String = "",
    val mediaLoop: Boolean = true
)

/**
 * Configuration for a specific environment (prod/non-prod) in a stream configuration.
 */
data class EnvironmentConfig(
    val videoId: String? = null,
    val state: String? = null, // "preLive", "live", "postLive"
    val locale: Map<String, LocaleConfig>? = null,
    val intervals: Map<String, Int>? = null // polling intervals in seconds: "preLive", "live", "postLive"
)

/**
 * Configuration for a specific event type (camera/mobile).
 */
data class EventTypeConfig(
    val prod: EnvironmentConfig,
    val nonProd: EnvironmentConfig? = null
)

/**
 * Root configuration object that holds all stream configurations.
 */
data class StreamConfiguration(
    val camera: EventTypeConfig,
    val mobile: EventTypeConfig
)
