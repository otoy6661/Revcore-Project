package com.revcore.android.core.comm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parser tests use a synthetic profile only. No built-in Honda live-data table
 * is shipped, so these tests exercise the generic offset/scale/range machinery
 * without asserting anything about a real ECU.
 */
class LiveDataProfileParserTest {

    private fun b(value: Int): Byte = value.toByte()

    private fun sample(): LiveTableProfile = SampleLiveData.profile

    @Test
    fun builtInHondaCatalogIsEmpty() {
        assertTrue(HondaLiveDataProfiles.tables.isEmpty())
        assertTrue(HondaLiveDataProfiles.primaryTables.isEmpty())
        assertTrue(HondaLiveDataProfiles.extendedTables.isEmpty())
        assertNull(HondaLiveDataProfiles.forTable("7A"))
        assertNull(HondaLiveDataProfiles.forTable("FF"))
        assertNull(HondaLiveDataProfiles.baseline)
    }

    @Test
    fun sampleRequestIsFiveBytesWithValidChecksum() {
        val frame = sample().request
        assertEquals(5, frame.size)
        assertEquals(0x05, frame[1].toInt() and 0xFF)
        assertEquals(0x71, frame[2].toInt() and 0xFF)
        assertEquals("7A", "%02X".format(frame[3].toInt() and 0xFF))
        assertEquals(
            frame.last().toInt() and 0xFF,
            (0x100 - (frame.dropLast(1).sumOf { it.toInt() and 0xFF } and 0xFF)) and 0xFF
        )
        assertEquals(17, sample().minFrameSize)
    }

    @Test
    fun sampleIsReferenceMaterialNotAnEcuClaim() {
        assertEquals(ProfileVerification.REFERENCE, sample().verification)
        assertTrue(sample().provenance.contains("synthetic", ignoreCase = true))
    }

    @Test
    fun sampleMapsEveryDeclaredField() {
        val result = LiveDataFrameParser.parse(SampleLiveData.payload, sample())

        assertTrue(result.dataValid)
        assertEquals(3000.0, result.values.getValue(LiveSensor.RPM), 0.001)
        assertEquals(128.0, result.values.getValue(LiveSensor.TPS_ADC), 0.001)
        assertEquals(128 * 100.0 / 255.0, result.values.getValue(LiveSensor.TPS_PERCENT), 0.001)
        assertEquals(128 * 5.0 / 255.0, result.values.getValue(LiveSensor.TPS_VOLTAGE), 0.001)
        assertEquals(64.0, result.values.getValue(LiveSensor.TPS_DEGREES), 0.001)
        assertEquals(40.0, result.values.getValue(LiveSensor.ECT), 0.001)
        assertEquals(24.0, result.values.getValue(LiveSensor.IAT), 0.001)
        assertEquals(100.0, result.values.getValue(LiveSensor.MAP), 0.001)
        assertEquals(12.0, result.values.getValue(LiveSensor.BATTERY), 0.001)
        assertEquals(60.0, result.values.getValue(LiveSensor.SPEED), 0.001)
        assertEquals(2.0, result.values.getValue(LiveSensor.INJECTOR), 0.001)
        assertTrue(result.rejected.isEmpty())
        assertEquals(11, result.available.size)
    }

    @Test
    fun alternateOffsetIsUsedOnlyWhenPrimaryIsFf() {
        val fallback = SampleLiveData.payload.copyOf()
        fallback[4] = b(0xFF)
        fallback[10] = b(90)
        val fromFallback = LiveDataFrameParser.parse(fallback, sample())
        assertEquals(50.0, fromFallback.values.getValue(LiveSensor.ECT), 0.001)
        assertEquals(LiveParameterState.VALID, fromFallback.states[LiveSensor.ECT])

        val missing = SampleLiveData.payload.copyOf()
        missing[4] = b(0xFF)
        missing[10] = b(0xFF)
        val rejected = LiveDataFrameParser.parse(missing, sample())
        assertTrue(LiveSensor.ECT !in rejected.values)
        assertEquals(LiveParameterState.INVALID_RESPONSE, rejected.states[LiveSensor.ECT])
        assertTrue(rejected.rejected.containsKey(LiveSensor.ECT))
    }

    @Test
    fun shortPayloadMarksMissingFieldsWithoutZeroing() {
        val payload = SampleLiveData.payload.copyOfRange(0, 4)
        val result = LiveDataFrameParser.parse(payload, sample())

        assertTrue(result.dataValid)
        assertEquals(3000.0, result.values.getValue(LiveSensor.RPM), 0.001)
        assertTrue(LiveSensor.MAP !in result.available)
        assertTrue(LiveSensor.BATTERY !in result.available)
        assertTrue(LiveSensor.INJECTOR !in result.available)
        assertTrue(LiveSensor.MAP !in result.values)
        assertEquals(LiveParameterState.NOT_RESPONDING, result.states[LiveSensor.MAP])
    }

    @Test
    fun implausibleValueIsRejectedNotClamped() {
        val payload = SampleLiveData.payload.copyOf()
        payload[4] = b(0xFE) // 214 C after the -40 bias
        val result = LiveDataFrameParser.parse(payload, sample())

        assertTrue(result.dataValid)
        assertTrue(LiveSensor.ECT !in result.available)
        assertEquals(LiveParameterState.INVALID_RESPONSE, result.states[LiveSensor.ECT])
        assertTrue(result.rejected.containsKey(LiveSensor.ECT))
    }

    @Test
    fun ffIsOnlyMissingForFieldsWithAnAlternateOffset() {
        val payload = SampleLiveData.payload.copyOf()
        payload[2] = b(0xFF) // TPS has no alternate
        val result = LiveDataFrameParser.parse(payload, sample())

        assertEquals(255.0, result.values.getValue(LiveSensor.TPS_ADC), 0.001)
        assertEquals(100.0, result.values.getValue(LiveSensor.TPS_PERCENT), 0.001)
        assertEquals(5.0, result.values.getValue(LiveSensor.TPS_VOLTAGE), 0.001)
        assertEquals(LiveParameterState.VALID, result.states[LiveSensor.TPS_ADC])
    }

    @Test
    fun littleEndianAndSignedFieldsAreHonoured() {
        val profile = LiveTableProfile(
            id = "sample.encoding",
            tableId = "7B",
            request = byteArrayOf(0x72, 0x05, 0x71, 0x7B, b(0x9D)),
            payloadStart = 4,
            minPayload = 4,
            requiredSensors = emptySet(),
            fields = listOf(
                LiveFieldSpec(
                    sensor = LiveSensor.RPM, offset = 0, length = 2,
                    endian = FieldEndian.LITTLE, validMin = 0.0, validMax = 20_000.0
                ),
                LiveFieldSpec(
                    sensor = LiveSensor.IGNITION_ADVANCE, offset = 2, length = 2,
                    signed = true, scale = 0.5, validMin = -300.0, validMax = 300.0
                )
            ),
            verification = ProfileVerification.REFERENCE,
            provenance = "synthetic encoding test"
        )
        val result = LiveDataFrameParser.parse(byteArrayOf(b(0x34), b(0x12), b(0xFE), b(0x0C)), profile)

        assertEquals(0x1234.toDouble(), result.values.getValue(LiveSensor.RPM), 0.001)
        assertEquals(-250.0, result.values.getValue(LiveSensor.IGNITION_ADVANCE), 0.001)
    }

    @Test
    fun keyOnWithZeroRpmKeepsOtherParametersValid() {
        val payload = ByteArray(SampleLiveData.profile.minPayload)
        val result = LiveDataFrameParser.parse(payload, sample())

        assertTrue(result.dataValid)
        assertEquals(LiveParameterState.VALID, result.states[LiveSensor.RPM])
        assertEquals(0.0, result.values.getValue(LiveSensor.RPM), 0.001)
        assertEquals(LiveParameterState.VALID, result.states[LiveSensor.TPS_ADC])
        assertEquals(LiveParameterState.VALID, result.states[LiveSensor.ECT])
    }

    @Test
    fun noSingleSensorGatesTheFrame() {
        val payload = SampleLiveData.payload.copyOf()
        payload[0] = b(0xFF)
        payload[1] = b(0xFF) // RPM 65535, outside 0..20000
        val result = LiveDataFrameParser.parse(payload, sample())

        assertTrue(result.dataValid)
        assertTrue(LiveSensor.RPM !in result.available)
        assertEquals(LiveParameterState.INVALID_RESPONSE, result.states[LiveSensor.RPM])
        assertEquals(LiveParameterState.VALID, result.states[LiveSensor.ECT])
    }

    @Test
    fun unknownProfileHasNoImplicitFallback() {
        val empty = LiveTableProfile(
            id = "test.empty",
            tableId = "99",
            request = ByteArray(0),
            payloadStart = 0,
            minPayload = 0,
            requiredSensors = emptySet(),
            fields = emptyList(),
            verification = ProfileVerification.HYPOTHESIS,
            provenance = "test"
        )
        val result = LiveDataFrameParser.parse(byteArrayOf(1, 2, 3), empty)
        assertTrue(result.values.isEmpty())
        assertTrue(result.available.isEmpty())
        assertFalse(result.dataValid)
        assertNull(result.values[LiveSensor.RPM])
    }

    @Test
    fun afrIsReportedUnsupportedWithoutOverridingParsedValues() {
        val states = mapOf(
            LiveSensor.RPM to LiveParameterState.VALID,
            LiveSensor.O2 to LiveParameterState.VALID
        )
        val merged = LiveParameterStates.withNotPublished(states)

        assertEquals(LiveParameterState.UNSUPPORTED, merged[LiveSensor.AFR])
        assertEquals(LiveParameterState.VALID, merged[LiveSensor.RPM])
        assertEquals(LiveParameterState.VALID, merged[LiveSensor.O2])
        assertTrue(LiveSensor.AFR !in LiveParameterStates.validSensors(merged))
        assertTrue(LiveParameterStates.withNotPublished(emptyMap()).isEmpty())
    }

    @Test
    fun noSampleFieldDerivesAfrOrRawO2() {
        assertTrue(sample().fields.none { it.sensor == LiveSensor.AFR })
        assertTrue(sample().fields.none { it.sensor == LiveSensor.O2_RAW })
        assertTrue(HondaLiveDataProfiles.tables.flatMap { it.fields }.none { it.sensor == LiveSensor.AFR })
    }

    @Test
    fun koeoMarksOnlyEngineRunningSensorsAndKeepsOthersValid() {
        val states = mapOf(
            LiveSensor.RPM to LiveParameterState.VALID,
            LiveSensor.TPS_PERCENT to LiveParameterState.VALID,
            LiveSensor.O2 to LiveParameterState.VALID
        )
        val engineOnly = setOf(LiveSensor.O2)

        val koeo = LiveParameterStates.withEngineRunning(states, engineRunning = false, engineOnly)
        assertEquals(LiveParameterState.NOT_AVAILABLE_IN_KOEO, koeo[LiveSensor.O2])
        assertEquals(LiveParameterState.VALID, koeo[LiveSensor.TPS_PERCENT])
        assertEquals(
            setOf(LiveSensor.RPM, LiveSensor.TPS_PERCENT),
            LiveParameterStates.validSensors(koeo)
        )

        val running = LiveParameterStates.withEngineRunning(states, engineRunning = true, engineOnly)
        assertEquals(LiveParameterState.VALID, running[LiveSensor.O2])
    }

    @Test
    fun staleOnlyTransitionsPreviouslyValidParameters() {
        val states = mapOf(
            LiveSensor.RPM to LiveParameterState.VALID,
            LiveSensor.O2 to LiveParameterState.NOT_AVAILABLE_IN_KOEO,
            LiveSensor.MAP to LiveParameterState.NOT_RESPONDING
        )
        val stale = LiveParameterStates.stale(states)
        assertEquals(LiveParameterState.STALE, stale[LiveSensor.RPM])
        assertEquals(LiveParameterState.NOT_AVAILABLE_IN_KOEO, stale[LiveSensor.O2])
        assertEquals(LiveParameterState.NOT_RESPONDING, stale[LiveSensor.MAP])
        assertTrue(LiveParameterStates.validSensors(stale).isEmpty())
    }
}

/**
 * Synthetic layout used only by tests and documentation. Offsets, scales and
 * table id are invented for readability; they are not a real ECU layout.
 */
private object SampleLiveData {
    private fun b(value: Int): Byte = value.toByte()

    private fun f(
        sensor: LiveSensor,
        offset: Int,
        length: Int = 1,
        scale: Double = 1.0,
        bias: Double = 0.0,
        min: Double,
        max: Double,
        unit: String = "",
        alternateOffsets: List<Int> = emptyList()
    ) = LiveFieldSpec(
        sensor = sensor,
        offset = offset,
        length = length,
        endian = FieldEndian.BIG,
        signed = false,
        scale = scale,
        bias = bias,
        unit = unit,
        validMin = min,
        validMax = max,
        alternateOffsets = alternateOffsets
    )

    val request: ByteArray = byteArrayOf(0x72, 0x05, 0x71, 0x7A, b(0x9E))

    val profile: LiveTableProfile = LiveTableProfile(
        id = "sample.live.7A",
        tableId = "7A",
        request = request,
        payloadStart = 4,
        minPayload = 12,
        requiredSensors = emptySet(),
        fields = listOf(
            f(LiveSensor.RPM, 0, length = 2, min = 0.0, max = 20_000.0, unit = "rpm"),
            f(LiveSensor.TPS_ADC, 2, min = 0.0, max = 255.0, unit = "count"),
            f(LiveSensor.TPS_PERCENT, 2, scale = 100.0 / 255.0, min = 0.0, max = 100.0, unit = "%"),
            f(LiveSensor.TPS_VOLTAGE, 2, scale = 5.0 / 255.0, min = 0.0, max = 5.0, unit = "V"),
            f(LiveSensor.TPS_DEGREES, 3, min = 0.0, max = 255.0, unit = "deg"),
            f(
                LiveSensor.ECT, 4, bias = -40.0, min = -40.0, max = 150.0, unit = "C",
                alternateOffsets = listOf(10)
            ),
            f(LiveSensor.IAT, 5, bias = -40.0, min = -40.0, max = 100.0, unit = "C"),
            f(LiveSensor.MAP, 6, length = 2, min = 0.0, max = 5_000.0, unit = "kPa"),
            f(LiveSensor.BATTERY, 8, scale = 0.05, min = 5.0, max = 20.0, unit = "V"),
            f(LiveSensor.SPEED, 9, min = 0.0, max = 300.0, unit = "km/h"),
            f(LiveSensor.INJECTOR, 11, scale = 0.01, min = 0.0, max = 50.0, unit = "ms")
        ),
        verification = ProfileVerification.REFERENCE,
        provenance = "RevCore synthetic example; not an ECU claim",
        notes = "Synthetic layout for parser tests and documentation."
    )

    val payload: ByteArray = byteArrayOf(
        b(0x0B), b(0xB8),
        b(0x80), b(0x40), b(0x50), b(0x40),
        b(0x00), b(0x64),
        b(0xF0), b(0x3C), b(0x5A), b(0xC8)
    )
}
