package com.si.brightcove.sdk.config

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
    val retryBackoffMultiplier: Double = 2.0
) {
    /**
     * Get Brightcove Account ID (alias for accountId).
     */
    fun getBrightcoveAccountId(): String = accountId
    
    /**
     * Get Brightcove Policy Key (alias for policyKey).
     */
    fun getBrightcovePolicyKey(): String = policyKey
}

