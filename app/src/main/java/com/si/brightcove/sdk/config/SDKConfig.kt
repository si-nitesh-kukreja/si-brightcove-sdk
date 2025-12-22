package com.si.brightcove.sdk.config

import com.si.brightcove.sdk.model.MediaType

/**
 * Internal SDK configuration data class.
 */
internal data class SDKConfig(
    val eventType: com.si.brightcove.sdk.model.EventType,
    val environment: com.si.brightcove.sdk.model.Environment,
    val debug: Boolean = false,
    val autoRetryOnError: Boolean = true,
    val maxRetryAttempts: Int = 3,
    val retryBackoffMultiplier: Double = 2.0,
    // New configuration parameters
    val locales: String = "en",
    val configVideoId: String = "",
    val configState: String = "",
    val configMediaType: MediaType = MediaType.IMAGE,
    val configMediaUrl: String = "",
    val configMediaTitle: String = "",
    val configMediaLoop: Boolean,
    val configIntervals: Map<String, Int> = emptyMap() // polling intervals in seconds
) {
    // Hardcoded credentials
    private val hardcodedAccountId = "6415818918001"
    private val hardcodedPolicyKey = "BCpkADawqM3ikTFBoFaNzjghiJjf1GzxYQ0kOqZTl_VBJfjxqdil2A0wfqN_tDj8CSJNwPPsz9EQX8unZYHCkXq6nq5THsJ9ShpPuWoKaCCTuymjtweUk4DWqbdBNgF5ZOeG1DDeoaMQzJIoVa92V09iU5ET5R2meoG3FQ"
    /**
     * Get Brightcove Account ID (hardcoded value).
     */
    fun getBrightcoveAccountId(): String = hardcodedAccountId

    /**
     * Get Brightcove Policy Key (hardcoded value).
     */
    fun getBrightcovePolicyKey(): String = hardcodedPolicyKey

    /**
     * Get the effective video ID (from config if available, otherwise empty).
     */
    fun getEffectiveVideoId(): String = configVideoId.ifBlank { "" }

    /**
     * Get the effective polling interval based on config state and useConfig flag.
     */
    fun getEffectivePollingInterval(): Long {
        return if (configIntervals.isNotEmpty()) {
            // Use intervals from config (convert seconds to milliseconds)
            val intervalSeconds = when (configState.lowercase()) {
                "prelive" -> configIntervals["preLive"] ?: configIntervals["postLive"]
                "postlive" -> configIntervals["postLive"] ?: configIntervals["preLive"]
                "live" -> configIntervals["live"]
                else -> null
            }
            intervalSeconds?.let { it * 1000L } ?: 30_000L // 30 seconds fallback
        } else {
            30_000L // 30 seconds default
        }
    }
}

