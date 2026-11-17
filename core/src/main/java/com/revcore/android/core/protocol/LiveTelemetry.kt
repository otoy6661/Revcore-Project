package com.revcore.android.core.protocol

import com.revcore.android.core.comm.LiveParameterState
import com.revcore.android.core.comm.LiveSensor
import com.revcore.android.data.ecu.TempSensorType

/**
 * Live-data availability is separate from adapter/ECU/session state: values may
 * be unavailable because no profile exists, no reply arrived, or the payload was
 * rejected. STALE means the last valid values are no longer current.
 */
enum class LiveDataState {
    UNAVAILABLE,
    WAITING_RESPONSE,
    ACTIVE,
    STALE,
    UNSUPPORTED_PROFILE,
    INVALID_PAYLOAD
}

data class LiveTelemetry(
    val timestamp: Long = System.currentTimeMillis(),
    val engineRpm: Int = 0,
    val tpsPercent: Float = 0.0f,
    val tpsVoltage: Float = 0.0f,
    val tpsAdc: Int = 0,
    val engineTempC: Int = 0,
    val intakeAirTempC: Int = 0,
    val mapKpa: Int = 0,
    val batteryVoltage: Float = 0.0f,
    val o2Voltage: Float = 0.0f,
    val injectorPulseMs: Float = 0.0f,
    val ignitionAdvanceDeg: Float = 0.0f,
    val vehicleSpeedKmh: Int = 0,
    val gearPosition: String = "N",
    val milActive: Boolean = false,
    val tempSensorType: TempSensorType = TempSensorType.ECT,
    /**
     * True when at least one parameter in the frame decoded and passed its range
     * check. RPM is not required: key-on/engine-off frames remain usable.
     */
    val isDataValid: Boolean = false,
    val lastUpdatedMs: Long = 0L,
    val lastReadAttemptMs: Long = 0L,
    val isRpmAvailable: Boolean = false,
    val isTpsAvailable: Boolean = false,
    val isTempAvailable: Boolean = false,
    val isIatAvailable: Boolean = false,
    val isMapAvailable: Boolean = false,
    val isBattAvailable: Boolean = false,
    val isInjectorAvailable: Boolean = false,
    val isIgnitionAvailable: Boolean = false,
    val isSpeedAvailable: Boolean = false,
    val isO2Available: Boolean = false,
    val activeTableHex: String = "",
    /** Per-parameter state; the only source the UI should use to hide values. */
    val parameterStates: Map<LiveSensor, LiveParameterState> = emptyMap()
) {
    val isEngineSpinning: Boolean
        get() = isDataValid && isRpmAvailable && engineRpm > 0

    fun stateOf(sensor: LiveSensor): LiveParameterState =
        parameterStates[sensor] ?: LiveParameterState.UNSUPPORTED

    fun isValid(sensor: LiveSensor): Boolean = stateOf(sensor) == LiveParameterState.VALID
}
