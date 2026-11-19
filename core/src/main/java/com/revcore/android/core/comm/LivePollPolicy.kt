package com.revcore.android.core.comm

/**
 * Poll pacing for Live Monitor. The interval is derived from the measured
 * transaction duration instead of a copied constant, and repeated failures back
 * off to a bounded stop. [MIN_INTERVAL_MS] keeps the existing 150 ms baseline
 * floor; it is not derived from any public reference implementation.
 */
object LivePollPolicy {
    const val MIN_INTERVAL_MS = 150L
    const val MAX_INTERVAL_MS = 1_000L
    const val MAX_CONSECUTIVE_FAILURES = 4
    private const val BASE_BACKOFF_MS = 300L
    private const val MAX_BACKOFF_MS = 3_000L

    fun nextDelayMs(transactionMs: Long, consecutiveFailures: Int): Long {
        if (consecutiveFailures > 0) {
            val shift = (consecutiveFailures - 1).coerceIn(0, 4)
            return (BASE_BACKOFF_MS shl shift).coerceAtMost(MAX_BACKOFF_MS)
        }
        return (transactionMs + 50L).coerceIn(MIN_INTERVAL_MS, MAX_INTERVAL_MS)
    }

    fun shouldStop(consecutiveFailures: Int): Boolean =
        consecutiveFailures >= MAX_CONSECUTIVE_FAILURES
}
