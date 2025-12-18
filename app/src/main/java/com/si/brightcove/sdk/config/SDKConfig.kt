package com.si.brightcove.sdk.config

import com.si.brightcove.sdk.model.MediaType

/**
 * Internal SDK configuration data class.
 */
internal data class SDKConfig(
    val accountId: String,
    val policyKey: String,
    val videoId: String,
    val eventType: com.si.brightcove.sdk.model.EventType,
    val environment: com.si.brightcove.sdk.model.Environment,
    val debug: Boolean = false,
    val pollingIntervalMs: Long = 1_000, // Default 1 seconds (reduced for faster loading)
    val autoRetryOnError: Boolean = true,
    val maxRetryAttempts: Int = 3,
    val retryBackoffMultiplier: Double = 2.0,
    // New configuration parameters
    val locale: String = "en",
    val useConfig: Boolean, // state parameter: true = existing behavior, false = config-driven
    val configVideoId: String = "",
    val configState: String = "",
    val configMediaType: MediaType = MediaType.IMAGE,
    val configMediaUrl: String = "",
    val configMediaTitle: String = "",
    val configMediaLoop: Boolean
) {
    /**
     * Get Brightcove Account ID (alias for accountId).
     */
    fun getBrightcoveAccountId(): String = accountId

    /**
     * Get Brightcove Policy Key (alias for policyKey).
     */
    fun getBrightcovePolicyKey(): String = policyKey

    /**
     * Get the effective video ID (from config if available, otherwise fallback).
     */
    fun getEffectiveVideoId(): String = if (useConfig && configVideoId.isNotBlank()) configVideoId else videoId

    /**
     * Get the effective polling interval based on config state and useConfig flag.
     */
    fun getEffectivePollingInterval(): Long {
        return if (useConfig) {
            when (configState.lowercase()) {
                "prelive","postlive"-> 10_000L // 30 seconds for pre-live
                "live" -> 15_000L    // 1 minute for live
                else -> pollingIntervalMs
            }
        } else {
            pollingIntervalMs
        }
    }
}

