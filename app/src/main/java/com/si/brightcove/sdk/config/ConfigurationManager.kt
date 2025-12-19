package com.si.brightcove.sdk.config

import android.content.Context
import com.si.brightcove.sdk.model.Environment
import com.si.brightcove.sdk.model.EventType as SdkEventType
import com.si.brightcove.sdk.model.MediaType
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonSyntaxException
import java.io.IOException
import java.lang.reflect.Type
import com.si.brightcove.sdk.ui.Logger

/**
 * Manages loading and accessing stream configuration from JSON/API.
 */
class ConfigurationManager private constructor() {

    private var configuration: StreamConfiguration? = null
    private var isLoaded = false

    companion object {
        @Volatile
        private var instance: ConfigurationManager? = null

        fun getInstance(): ConfigurationManager {
            return instance ?: synchronized(this) {
                instance ?: ConfigurationManager().also { instance = it }
            }
        }
    }

    /**
     * Load configuration from JSON asset file.
     * This method should be called once during SDK initialization.
     */
    fun loadConfiguration(context: Context, debug: Boolean = false): Result<StreamConfiguration> {
        return try {
            if (isLoaded && configuration != null) {
                Result.success(configuration!!)
            } else {
                val jsonString = loadJsonFromAssets(context, "stream_config.json")
                val config = parseConfiguration(jsonString)
                configuration = config
                isLoaded = true
                Result.success(config)
            }
        } catch (e: Exception) {
            if (debug) {
                Logger.e("Failed to load configuration", e, "ConfigurationManager")
            }
            Result.failure(e)
        }
    }

    /**
     * Load configuration from API (future implementation).
     * This will replace the JSON loading in the future.
     */
    fun loadConfigurationFromApi(apiUrl: String, debug: Boolean = false): Result<StreamConfiguration> {
        // TODO: Implement API loading logic
        // For now, return failure to indicate not implemented
        return Result.failure(NotImplementedError("API configuration loading not yet implemented"))
    }

    /**
     * Get configuration for specific parameters.
     */
    fun getConfiguration(
        eventType: SdkEventType,
        environment: Environment,
        locale: String
    ): Result<StreamConfigData> {
        val config = configuration ?: return Result.failure(IllegalStateException("Configuration not loaded"))

        return try {
            val eventConfig = when (eventType) {
                SdkEventType.camera -> config.camera
                SdkEventType.mobile -> config.mobile
            }

            val envConfig = when (environment) {
                Environment.prod -> eventConfig.prod
                Environment.nonProd -> eventConfig.nonProd
                    ?: return Result.failure(IllegalArgumentException("Non-prod configuration not found for ${eventType.name}"))
            }

            val videoId = envConfig.videoId
                ?: return Result.failure(IllegalArgumentException("VideoId not found in configuration for ${environment.name}"))
            
            val state = envConfig.state
                ?: return Result.failure(IllegalArgumentException("State not found in configuration for ${environment.name}"))
            
            val languages = envConfig.languages
                ?: return Result.failure(IllegalArgumentException("Languages not found in configuration for ${environment.name}"))
            
            val localeConfig = languages[locale]
                ?: return Result.failure(IllegalArgumentException("Locale '$locale' not found in configuration"))

            val result = StreamConfigData(
                videoId = videoId,
                state = state,
                mediaType = localeConfig.mediaType,
                mediaUrl = localeConfig.mediaUrl,
                mediaTitle = localeConfig.mediaTitle,
                mediaLoop = localeConfig.mediaLoop
            )

            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Check if configuration is loaded.
     */
    fun isConfigurationLoaded(): Boolean = isLoaded

    /**
     * Reset configuration (useful for testing or re-initialization).
     */
    fun reset() {
        configuration = null
        isLoaded = false
    }

    private fun loadJsonFromAssets(context: Context, fileName: String): String {
        return try {
            context.assets.open(fileName).bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            throw IOException("Failed to load JSON file '$fileName' from assets", e)
        }
    }

    private fun parseConfiguration(jsonString: String): StreamConfiguration {
        return try {
            val gson = GsonBuilder()
                .registerTypeAdapter(MediaType::class.java, MediaTypeDeserializer())
                .create()
            gson.fromJson(jsonString, StreamConfiguration::class.java)
        } catch (e: JsonSyntaxException) {
            throw JsonSyntaxException("Failed to parse JSON configuration", e)
        }
    }

    /**
     * Custom deserializer for MediaType enum to handle lowercase JSON values.
     */
    private class MediaTypeDeserializer : JsonDeserializer<MediaType> {
        override fun deserialize(
            json: JsonElement?,
            typeOfT: Type?,
            context: JsonDeserializationContext?
        ): MediaType {
            val value = json?.asString?.lowercase() ?: return MediaType.IMAGE
            
            return when (value) {
                "image" -> MediaType.IMAGE
                "video" -> MediaType.VIDEO
                else -> MediaType.IMAGE // Default fallback
            }
        }
    }
}

/**
 * Data class containing the resolved configuration values for a specific context.
 */
data class StreamConfigData(
    val videoId: String,
    val state: String,
    val mediaType: MediaType,
    val mediaUrl: String,
    val mediaTitle: String,
    val mediaLoop: Boolean
)
