package com.revcore.android.core.protocol

/**
 * Process-wide single-flight marker used to keep ECU identification from being
 * started concurrently by adapter connection, ignition recovery, and manual UI.
 */
class SingleFlightGate {
    private val lock = Any()
    private var running = false

    val isRunning: Boolean
        get() = synchronized(lock) { running }

    fun tryEnter(): Boolean = synchronized(lock) {
        if (running) {
            false
        } else {
            running = true
            true
        }
    }

    fun exit() {
        synchronized(lock) { running = false }
    }

    fun reset() {
        synchronized(lock) { running = false }
    }
}
