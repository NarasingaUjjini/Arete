package com.trailmap.gps.util

import android.util.Log

/**
 * Local-only logging. Never log precise coordinates by default.
 * Can be disabled entirely from settings later.
 */
object AppLog {
    @Volatile
    var enabled: Boolean = false

    fun d(tag: String, message: String) {
        if (enabled) Log.d("Arete/$tag", message)
    }

    fun w(tag: String, message: String, error: Throwable? = null) {
        if (enabled) Log.w("Arete/$tag", message, error)
    }

    fun e(tag: String, message: String, error: Throwable? = null) {
        if (enabled) Log.e("Arete/$tag", message, error)
    }
}
