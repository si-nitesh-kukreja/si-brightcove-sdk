package com.si.brightcove.demo

import android.app.Application
import com.si.brightcove.sdk.BrightcoveLiveStreamSDK
import com.si.brightcove.sdk.model.EventType
import com.si.brightcove.sdk.model.Environment

/**
 * Demo Application class for testing the Brightcove Live Streaming SDK.
 * 
 * Replace the placeholder values with your actual Brightcove credentials.
 */
class DemoApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // SDK auto-initializes from LiveStreamScreen. No explicit init here.
    }
}

