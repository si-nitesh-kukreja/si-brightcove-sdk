package com.si.brightcove.sdk.model

import java.util.Date

/**
 * Media type for pre-live content.
 */
enum class MediaType {
    IMAGE,
    VIDEO,
}

/**
 * Represents the state of a live stream.
 */
sealed class LiveStreamState {
    /**
     * Loading state: SDK is checking stream availability.
     */
    object Loading : LiveStreamState()

    /**
     * Pre-live state: Stream has not started yet.
     *
     * @param mediaType Type of media to display (image, video, etc.)
     * @param mediaUrl URL for the media content
     * @param mediaTitle Title for the media
     * @param mediaLoop Whether to loop the media
     */
    data class PreLive(
        val mediaType: MediaType = MediaType.IMAGE,
        val mediaUrl: String = "",
        val mediaTitle: String = "",
        val mediaLoop: Boolean = true
    ) : LiveStreamState()

    /**
     * Live state: Stream is currently active.
     *
     * @param title Title of the live stream
     * @param description Short description of the live stream
     */
    data class Live(
        val title: String,
        val description: String
    ) : LiveStreamState()

    /**
     * Error state: An error occurred while loading or playing the stream.
     *
     * @param errorMessage Human-readable error message
     * @param errorCode Error code for programmatic handling
     * @param retryable Whether the error can be retried
     */
    data class Error(
        val errorMessage: String,
        val errorCode: SDKError,
        val retryable: Boolean = true
    ) : LiveStreamState()
}

/**
 * Error codes for different types of errors that can occur.
 */
enum class SDKError {
    NETWORK_ERROR,
    VIDEO_NOT_FOUND,
    PLAYBACK_ERROR,
    INITIALIZATION_ERROR,
    UNKNOWN_ERROR
}

