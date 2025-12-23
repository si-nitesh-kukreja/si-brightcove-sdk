package com.si.brightcove.sdk.config

import android.content.Context
import com.si.brightcove.sdk.model.Environment
import com.si.brightcove.sdk.model.EventType as SdkEventType
import com.si.brightcove.sdk.model.MediaType
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonSyntaxException
import java.io.IOException
import java.io.InputStreamReader
import java.lang.reflect.Type
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
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
                Logger.e("Failed to load configuration", e)
            }
            Result.failure(e)
        }
    }

    /**
     * Load configuration from API endpoint.
     * Fetches JSON configuration from the provided URL using background thread.
     */
    fun loadConfigurationFromApi(apiUrl: String, debug: Boolean = false): Result<StreamConfiguration> {
        val executor = Executors.newSingleThreadExecutor()

        val callable = Callable {
            try {
                if (debug) {
                    Logger.d("Loading configuration from API: $apiUrl")
                }
                val url = URL(apiUrl)
                val connection = url.openConnection() as HttpURLConnection

                connection.apply {
                    requestMethod = "GET"
                    connectTimeout = 10_000 // 10 seconds
                    readTimeout = 10_000    // 10 seconds
                    setRequestProperty("Accept", "application/json")
                }

                val responseCode = connection.responseCode
                if (debug) {
                    Logger.d("API Response Code: $responseCode")
                }
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    connection.inputStream.use { inputStream ->
                        InputStreamReader(inputStream).use { reader ->
                            val jsonString = reader.readText()
                            if (debug) {
                                Logger.d("Successfully fetched configuration from API (${jsonString.length} characters)")
                            }
                            Logger.d("JsonString :  $jsonString")
                            val config = parseConfiguration(jsonString)
                            Result.success(config)
                        }
                    }
                } else {
                    val errorMessage = "HTTP error: $responseCode"
                    if (debug) {
                        Logger.e("Failed to fetch configuration from API: $errorMessage")
                    }
                    Result.failure(IOException("HTTP $responseCode: Failed to fetch configuration from $apiUrl"))
                }
            } catch (e: java.io.InterruptedIOException) {
                val errorMessage = "Network request was interrupted"
                if (debug) {
                    Logger.e(errorMessage)
                }
                Result.failure(IOException("Network request was interrupted"))
            } catch (e: Exception) {
                val errorMessage = "Failed to load configuration from API: ${e.javaClass.simpleName} - ${e.message}"
                if (debug) {
                    Logger.e(errorMessage, e)
                }
                Result.failure(IOException("Network error: $errorMessage"))
            }
        }

        val future: Future<Result<StreamConfiguration>> = executor.submit(callable)

        return try {
            // Wait for the result with a timeout
            val result = future.get(15, TimeUnit.SECONDS)
            // Set configuration and loaded state on success
            result.onSuccess { config ->
                configuration = config
                isLoaded = true
                if (debug) {
                    Logger.d("Configuration parsed and loaded successfully")
                }
            }
            result
        } catch (e: java.util.concurrent.TimeoutException) {
            if (debug) {
                Logger.e("Timeout while loading configuration from API")
            }
            // Cancel the task
            future.cancel(true)
            Result.failure(IOException("Network operation timed out after 15 seconds"))
        } catch (e: InterruptedException) {
            if (debug) {
                Logger.e("Thread interrupted while loading configuration from API")
            }
            // Cancel the task
            future.cancel(true)
            Thread.currentThread().interrupt()
            Result.failure(IOException("Network operation was interrupted"))
        } catch (e: Exception) {
            if (debug) {
                Logger.e("Error while loading configuration from API: ${e.message}")
            }
            Result.failure(IOException("Network operation failed: ${e.message}"))
        } finally {
            // Graceful shutdown
            executor.shutdown()
            try {
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    // If still running after 2 seconds, force shutdown
                    executor.shutdownNow()
                }
            } catch (e: InterruptedException) {
                executor.shutdownNow()
                Thread.currentThread().interrupt()
            }
        }
    }

    /**
     * Get configuration for specific parameters.
     */
    fun getConfiguration(
        eventType: SdkEventType,
        environment: Environment,
        locales: String,
        debug: Boolean
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
            
            val localesMap = envConfig.locales
                ?: return Result.failure(IllegalArgumentException("locales not found in configuration for ${environment.name}"))

            val localesConfig = localesMap[locales]
                ?: if (state.lowercase() in listOf("prelive", "postlive") && locales != "en") {
                    // For prelive/postlive states, fallback to 'en' if requested locale not found
                    if (debug) {
                        Logger.d("Locale '$locales' not found for $state state, falling back to 'en'")
                    }
                    localesMap["en"] ?: return Result.failure(IllegalArgumentException("Fallback locale 'en' not found in configuration"))
                } else {
                    return Result.failure(IllegalArgumentException("Locale '$locales' not found in configuration"))
                }

            val intervals = envConfig.intervals ?: emptyMap()

            val result = StreamConfigData(
                videoId = videoId,
                state = state,
                mediaType = localesConfig.mediaType,
                mediaUrl = localesConfig.mediaUrl,
                mediaTitle = localesConfig.mediaTitle,
                mediaLoop = localesConfig.mediaLoop,
                intervals = intervals
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
    val mediaLoop: Boolean,
    val intervals: Map<String, Int> = emptyMap() // polling intervals in seconds: "preLive", "live", "postLive"
)
