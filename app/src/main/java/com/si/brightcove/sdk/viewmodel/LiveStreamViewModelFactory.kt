package com.si.brightcove.sdk.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * Factory for creating LiveStreamViewModel instances.
 */
class LiveStreamViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LiveStreamViewModel::class.java)) {
            return LiveStreamViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

