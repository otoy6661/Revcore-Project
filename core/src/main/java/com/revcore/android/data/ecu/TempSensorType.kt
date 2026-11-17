package com.revcore.android.data.ecu

enum class TempSensorType(
    val code: String,
    val labelEn: String,
    val labelId: String,
    val normalRange: String
) {
    ECT("ECT", "Engine Coolant Temp (Radiator)", "Suhu Air Radiator (Pendingin Cairan)", "80°C - 105°C"),
    EOT("EOT", "Engine Oil Temp (Air-Cooled)", "Suhu Oli Mesin (Pendingin Udara)", "90°C - 120°C")
}
