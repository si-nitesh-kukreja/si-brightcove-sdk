package com.si.brightcove.sdk.ui

/**
 * Centralized logger for the BrightCove SDK UI components.
 * All logging should go through this class to respect the debug flag.
 */
object Logger {

    private const val TAG = "BrightCoveSDK"

    /**
     * Whether debug logging is enabled.
     * When false, all log calls are no-ops for performance.
     */
    var isDebugEnabled: Boolean = false

    /**
     * Log a debug message.
     */
    fun d(message: String, tag: String = TAG) {
        if (isDebugEnabled) {
            android.util.Log.d(tag, message)
        }
    }

    /**
     * Log a debug message with throwable.
     */
    fun d(message: String, throwable: Throwable, tag: String = TAG) {
        if (isDebugEnabled) {
            android.util.Log.d(tag, message, throwable)
        }
    }

    /**
     * Log an info message.
     */
    fun i(message: String, tag: String = TAG) {
        if (isDebugEnabled) {
            android.util.Log.i(tag, message)
        }
    }

    /**
     * Log an info message with throwable.
     */
    fun i(message: String, throwable: Throwable, tag: String = TAG) {
        if (isDebugEnabled) {
            android.util.Log.i(tag, message, throwable)
        }
    }

    /**
     * Log a warning message.
     */
    fun w(message: String, tag: String = TAG) {
        if (isDebugEnabled) {
            android.util.Log.w(tag, message)
        }
    }

    /**
     * Log a warning message with throwable.
     */
    fun w(message: String, throwable: Throwable, tag: String = TAG) {
        if (isDebugEnabled) {
            android.util.Log.w(tag, message, throwable)
        }
    }

    /**
     * Log an error message.
     */
    fun e(message: String, tag: String = TAG) {
        if (isDebugEnabled) {
            android.util.Log.e(tag, message)
        }
    }

    /**
     * Log an error message with throwable.
     */
    fun e(message: String, throwable: Throwable, tag: String = TAG) {
        if (isDebugEnabled) {
            android.util.Log.e(tag, message, throwable)
        }
    }

    /**
     * Log a verbose message.
     */
    fun v(message: String, tag: String = TAG) {
        if (isDebugEnabled) {
            android.util.Log.v(tag, message)
        }
    }

    /**
     * Log a verbose message with throwable.
     */
    fun v(message: String, throwable: Throwable, tag: String = TAG) {
        if (isDebugEnabled) {
            android.util.Log.v(tag, message, throwable)
        }
    }

    /**
     * Log a wtf (What a Terrible Failure) message.
     */
    fun wtf(message: String, tag: String = TAG) {
        if (isDebugEnabled) {
            android.util.Log.wtf(tag, message)
        }
    }

    /**
     * Log a wtf (What a Terrible Failure) message with throwable.
     */
    fun wtf(message: String, throwable: Throwable, tag: String = TAG) {
        if (isDebugEnabled) {
            android.util.Log.wtf(tag, message, throwable)
        }
    }
}

