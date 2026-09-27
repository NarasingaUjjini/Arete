package com.trailmap.gps.offline

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-level network switch for Trip Pack airplane tests.
 * When forced offline, tile transforms and pack downloads must not hit the network.
 */
object NetworkGate {
    private val _forcedOffline = MutableStateFlow(false)
    val forcedOffline: StateFlow<Boolean> = _forcedOffline.asStateFlow()

    fun setForcedOffline(value: Boolean) {
        _forcedOffline.value = value
    }

    fun allowNetwork(): Boolean = !_forcedOffline.value
}
