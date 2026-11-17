package com.revcore.android.core.comm

import com.revcore.android.core.protocol.CommandOutcome
import com.revcore.android.core.usb.UsbTransport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class CommLifecycle {
    DISCONNECTED,
    ADAPTER_OPEN,
    LINE_READY,
    INITIALIZING,
    IDENTIFYING,
    ECU_READY,
    MONITORING,
    SUSPECTED_LOSS,
    RECOVERY_PROBE,
    DISCONNECTING;

    val isOperational: Boolean get() = this == ECU_READY || this == MONITORING
}

data class SessionSnapshot(
    val lifecycle: CommLifecycle,
    val family: HondaEcuFamily,
    val familyEvidence: FamilyEvidence,
    val consecutiveFailures: Int,
    val lastFailure: String?
)

/**
 * Owns the connection lifecycle and the single wire gate.
 *
 * All requests (identification, live data, DTC, recovery probes) pass through
 * [transact]; the internal mutex makes overlapping transactions impossible even
 * if a caller forgets the higher-level engine lock.
 */
class EcuSessionManager(
    private val wire: KlineWireEngine,
    private val failureThreshold: Int = 2
) {
    private val gate = Mutex()

    @Volatile
    private var transport: UsbTransport? = null

    private val _lifecycle = MutableStateFlow(CommLifecycle.DISCONNECTED)
    val lifecycle: StateFlow<CommLifecycle> = _lifecycle.asStateFlow()

    private val _family = MutableStateFlow(HondaEcuFamily.UNKNOWN)
    val family: StateFlow<HondaEcuFamily> = _family.asStateFlow()

    private val _familyEvidence = MutableStateFlow(FamilyEvidence.NONE)
    val familyEvidence: StateFlow<FamilyEvidence> = _familyEvidence.asStateFlow()

    private val _lastFailure = MutableStateFlow<String?>(null)
    val lastFailure: StateFlow<String?> = _lastFailure.asStateFlow()

    var consecutiveFailures: Int = 0
        private set

    val activeTransport: UsbTransport?
        get() = transport

    fun attach(transport: UsbTransport) {
        this.transport = transport
        consecutiveFailures = 0
        _lastFailure.value = null
        _lifecycle.value = CommLifecycle.ADAPTER_OPEN
        wire.reset()
    }

    fun detach() {
        _lifecycle.value = CommLifecycle.DISCONNECTING
        transport = null
        consecutiveFailures = 0
        _lastFailure.value = null
        wire.reset()
        _lifecycle.value = CommLifecycle.DISCONNECTED
        _family.value = HondaEcuFamily.UNKNOWN
        _familyEvidence.value = FamilyEvidence.NONE
    }

    fun markLineReady() {
        if (transport != null && !_lifecycle.value.isOperational) {
            _lifecycle.value = CommLifecycle.LINE_READY
        }
    }

    fun beginInitialization() {
        if (transport != null) _lifecycle.value = CommLifecycle.INITIALIZING
    }

    fun beginIdentification() {
        if (transport != null) _lifecycle.value = CommLifecycle.IDENTIFYING
    }

    fun markIdentified(verified: Boolean) {
        if (transport == null) return
        if (!_lifecycle.value.isOperational) _lifecycle.value = CommLifecycle.ECU_READY
        if (verified && _familyEvidence.value.status == EvidenceStatus.UNRESOLVED) {
            _familyEvidence.value = FamilyEvidence.IDENTIFIER_DB
        }
    }

    fun beginMonitoring() {
        if (transport != null) _lifecycle.value = CommLifecycle.MONITORING
    }

    fun beginRecovery() {
        if (transport != null) _lifecycle.value = CommLifecycle.RECOVERY_PROBE
    }

    fun markAlive() {
        consecutiveFailures = 0
        _lastFailure.value = null
        if (transport != null && !_lifecycle.value.isOperational) {
            _lifecycle.value = CommLifecycle.ECU_READY
        }
    }

    fun markFailure(reason: String) {
        consecutiveFailures++
        _lastFailure.value = reason
        if (consecutiveFailures >= failureThreshold &&
            (_lifecycle.value == CommLifecycle.ECU_READY || _lifecycle.value == CommLifecycle.MONITORING)
        ) {
            _lifecycle.value = CommLifecycle.SUSPECTED_LOSS
        }
    }

    suspend fun transact(
        request: ByteArray,
        config: KlineWireConfig,
        expectedTransport: UsbTransport? = null
    ): CommandOutcome {
        return gate.withLock {
            val active = transport
            if (active == null || !active.isOpen || (expectedTransport != null && expectedTransport !== active)) {
                val reason = "Adapter USB belum siap untuk transaksi"
                markFailure(reason)
                return@withLock CommandOutcome.Failure(reason, ByteArray(0))
            }

            when (val outcome = wire.transceive(active, request, config)) {
                is CommandOutcome.Frame -> {
                    markAlive()
                    observeFrame(outcome.bytes)
                    outcome
                }
                is CommandOutcome.Failure -> {
                    markFailure(outcome.reason)
                    outcome
                }
            }
        }
    }

    private fun observeFrame(frame: ByteArray) {
        if (frame.isEmpty()) return
        val resolved = EcuFamilyResolver.fromResponseHeader(frame[0].toInt() and 0xFF)
        if (resolved != null && _familyEvidence.value != FamilyEvidence.IDENTIFIER_DB) {
            _family.value = resolved.first
            _familyEvidence.value = resolved.second
        }
    }

    fun snapshot(): SessionSnapshot = SessionSnapshot(
        lifecycle = _lifecycle.value,
        family = _family.value,
        familyEvidence = _familyEvidence.value,
        consecutiveFailures = consecutiveFailures,
        lastFailure = _lastFailure.value
    )
}
