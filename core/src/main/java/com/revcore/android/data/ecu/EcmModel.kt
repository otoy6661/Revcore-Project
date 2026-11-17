package com.revcore.android.data.ecu

data class EcmModel(
    val id: String,
    val partNumber: String,
    val ecmId: String = "",
    val startOffset: Int = 0,
    val bikeName: String = "Belum Teridentifikasi",
    val yearRange: String = "",
    val manufacturer: String = "Belum terverifikasi",
    val mcuType: String = "Belum terverifikasi",
    val flashSizeKb: Int = 0,
    val stockRevLimit: Int = 0,
    val maxSafeRevLimit: Int = 0,
    val recommendedRevLimit: Int = 0,
    val revLimitSource: String = "unverified",
    val tempSensorType: TempSensorType = TempSensorType.ECT,
    val checksumType: ChecksumType = ChecksumType.KEIHIN_16BIT_ADD,
    val checksumOffset: Int = 0,
    val revLimiterOffset: Int = 0,
    val fuelMapOffset: Int = 0,
    val fuelMapRows: Int = 0,
    val fuelMapCols: Int = 0,
    val ignitionMapOffset: Int = 0,
    val ignitionMapRows: Int = 0,
    val ignitionMapCols: Int = 0,
    val idleTargetOffset: Int = 0,
    val isVerifiedDiagnostic: Boolean = false,
    val isVerifiedRemap: Boolean = false,
    val category: String = "Universal",
    val statusNotes: String = "Data referensi; verifikasi hardware belum dilakukan.",
    val source: String = "",
    val defaultBaudRate: Int = 10400,
    val country: String = "",
    val displacementCc: Double = 0.0,
    val calibrationId: String = "",
    val pinout: String = "",
    val protocol: String = ""
) {
    val modelName: String get() = bikeName
    val flashSizeBytes: Int get() = flashSizeKb * 1024
    val year: String get() = yearRange.ifEmpty { "Tidak tersedia" }
    val hasRecommendedRevLimit: Boolean get() = recommendedRevLimit > 0

    /**
     * Active catalog membership needs positive evidence: Indonesia, a source
     * displacement of 1..250 cc, and no larger displacement in the model name.
     * Records without region/displacement evidence stay out of the picker while
     * remaining available for ECU identification.
     */
    val isActiveCatalogEligible: Boolean
        get() {
            if (!country.equals("Indonesia", ignoreCase = true)) return false
            if (displacementCc <= 0.0 || displacementCc > 250.0) return false
            val tokens = Regex("\\d{2,4}").findAll(bikeName)
                .mapNotNull { it.value.toIntOrNull() }
                .filter { it in 50..2000 }
            return tokens.none { it > 250 }
        }
}
