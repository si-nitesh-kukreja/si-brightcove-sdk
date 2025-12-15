package com.si.brightcove.sdk.model

/**
 * Represents player events that can be observed by parent apps.
 */
sealed class PlayerEvent {
    /**
     * Playback has started.
     */
    object PlaybackStarted : PlayerEvent()
    
    /**
     * Playback has been paused.
     */
    object PlaybackPaused : PlayerEvent()
    
    /**
     * Playback has been resumed.
     */
    object PlaybackResumed : PlayerEvent()
    
    /**
     * Player is buffering.
     */
    object Buffering : PlayerEvent()
    
    /**
     * Buffering has completed.
     */
    object BufferingComplete : PlayerEvent()
    
    /**
     * A playback error occurred.
     * 
     * @param errorMessage Error message
     * @param errorCode Error code
     */
    data class PlaybackError(
        val errorMessage: String,
        val errorCode: SDKError
    ) : PlayerEvent()
    
    /**
     * Video has been loaded and is ready to play.
     */
    object VideoLoaded : PlayerEvent()
    
    /**
     * Playback has completed (for VOD, not applicable for live streams typically).
     */
    object PlaybackCompleted : PlayerEvent()
}

