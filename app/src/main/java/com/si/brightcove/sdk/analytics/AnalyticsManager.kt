package com.si.brightcove.sdk.analytics

import android.content.Context
import com.si.brightcove.sdk.ui.Logger

/**
 * Analytics manager for tracking pageviews.
 * Only logs basic pageview analytics as per requirements.
 */
internal class AnalyticsManager(
    private val context: Context,
    private val debug: Boolean
) {
    
    /**
     * Track a pageview event.
     * 
     * @param screenName Name of the screen being viewed
     */
    fun trackPageView(screenName: String) {
        Logger.d("Pageview tracked: $screenName", "AnalyticsManager")
        
        // In a real implementation, this would send analytics to your analytics service
        // For now, we'll just log it. Parent apps should not receive this data.
        // Example: Firebase Analytics, Mixpanel, etc.
        // FirebaseAnalytics.getInstance(context).logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle)
    }
    
    companion object {
        private const val TAG = "BrightcoveSDK"
    }
}

