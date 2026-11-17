package com.revcore.android.data.ecu

import android.content.Context
import android.util.Log
import org.json.JSONArray
import java.io.InputStreamReader
import java.util.Locale

/**
 * Identity matching for ECM records.
 *
 * ECU identification responses carry a short ECM ID. Reference databases may store
 * the same ID with one or two extra revision bytes. Matching is therefore limited to
 * exact normalized identity or a one/two byte prefix relation, and only accepted when
 * exactly one record matches. Anything looser is a guess and must not identify an ECU.
 */
object EcmIdentityMatcher {
    fun normalize(value: String): String =
        value.uppercase(Locale.ROOT).filter { it.isLetterOrDigit() }
    /**
     * No prefix/suffix bytes are ever stripped before comparison. An earlier version
     * removed leading "00" from long IDs, but no evidence established that those
     * bytes are padding. Both sides are now compared exactly after formatting
     * normalization.
     */
    fun canonicalEcmId(value: String): String = normalize(value)

    /**
     * Exact, or a bounded prefix/suffix relation used only to label incomplete
     * identities. The shorter value must be at least 8 characters and the length
     * difference at most 2, matching the documented 4-byte/5-byte response fields.
     */
    fun relation(query: String, candidate: String): IdentityRelation? {
        val q = normalize(query)
        val c = normalize(candidate)
        if (q.isEmpty() || c.isEmpty()) return null
        if (q == c) return IdentityRelation.EXACT
        val shorter = minOf(q.length, c.length)
        val longer = maxOf(q.length, c.length)
        if (shorter < 8 || longer - shorter > 2) return null
        return when {
            q.startsWith(c) || c.startsWith(q) -> IdentityRelation.PREFIX
            q.endsWith(c) || c.endsWith(q) -> IdentityRelation.SUFFIX
            else -> null
        }
    }

    fun ecmIdMatches(query: String, candidate: String): Boolean =
        relation(query, candidate) != null

    fun partNumberMatches(query: String, candidate: String): Boolean {
        val q = normalize(query)
        val c = normalize(candidate)
        return q.isNotEmpty() && q == c
    }
}

enum class EcmIssueSeverity { ERROR, WARNING, INFO }

/**
 * A database conflict that must stay visible instead of being "fixed" by picking
 * one row. Records are never dropped because of an issue: exact matching already
 * reports several candidates as AMBIGUOUS.
 */
data class EcmDataIssue(
    val code: String,
    val severity: EcmIssueSeverity,
    val modelIds: List<String>,
    val detail: String
)

/**
 * Schema, type and consistency checks for the merged catalog. The reference file
 * `ecm_database_indonesia_le250_filtered.json` is read-only evidence; this
 * validator never rewrites a record.
 */
object EcmDatabaseValidator {
    fun validate(models: List<EcmModel>): List<EcmDataIssue> {
        val issues = mutableListOf<EcmDataIssue>()

        fun grouped(
            code: String,
            severity: EcmIssueSeverity,
            describe: String,
            keyOf: (EcmModel) -> String
        ) {
            models.groupBy { keyOf(it).trim().uppercase(Locale.ROOT) }
                .filter { (key, group) -> key.isNotEmpty() && group.size > 1 }
                .toSortedMap()
                .forEach { (key, group) ->
                    issues += EcmDataIssue(
                        code = code,
                        severity = severity,
                        modelIds = group.map { it.id },
                        detail = "$describe '$key' dipakai oleh ${group.size} record; " +
                            "kandidat tidak dipilih otomatis."
                    )
                }
        }

        val blank = models.filter {
            it.id.isBlank() || it.partNumber.isBlank() || it.ecmId.isBlank()
        }
        if (blank.isNotEmpty()) {
            issues += EcmDataIssue(
                code = "BLANK_IDENTITY",
                severity = EcmIssueSeverity.ERROR,
                modelIds = blank.map { it.id.ifBlank { "(tanpa id)" } },
                detail = "${blank.size} record tidak memiliki id, part number, atau ECM ID lengkap."
            )
        }

        val limitConflicts = models.filter {
            it.maxSafeRevLimit > 0 &&
                (it.stockRevLimit > it.maxSafeRevLimit ||
                    it.recommendedRevLimit > it.maxSafeRevLimit)
        }
        if (limitConflicts.isNotEmpty()) {
            issues += EcmDataIssue(
                code = "REV_LIMIT_ORDER_CONFLICT",
                severity = EcmIssueSeverity.WARNING,
                modelIds = limitConflicts.map { it.id },
                detail = "${limitConflicts.size} record memiliki stock/recommended limit di atas " +
                    "max safe limit; nilai tidak diubah dan tidak dipakai untuk rekomendasi."
            )
        }

        grouped(
            code = "DUPLICATE_ID",
            severity = EcmIssueSeverity.ERROR,
            describe = "ID database",
            keyOf = { it.id }
        )
        grouped(
            code = "DUPLICATE_PART_NUMBER",
            severity = EcmIssueSeverity.WARNING,
            describe = "Part number",
            keyOf = { it.partNumber }
        )
        grouped(
            code = "DUPLICATE_ECM_ID",
            severity = EcmIssueSeverity.WARNING,
            describe = "ECM ID",
            keyOf = { it.ecmId }
        )

        val outOfScope = models.filter {
            it.country.equals("Indonesia", ignoreCase = true) &&
                (it.displacementCc <= 0.0 || it.displacementCc > 250.0)
        }
        if (outOfScope.isNotEmpty()) {
            issues += EcmDataIssue(
                code = "DISPLACEMENT_OUT_OF_SCOPE",
                severity = EcmIssueSeverity.INFO,
                modelIds = outOfScope.map { it.id },
                detail = "${outOfScope.size} record Indonesia berada di luar rentang 1..250 cc " +
                    "dan tidak masuk katalog aktif; record tetap disimpan."
            )
        }

        return issues
    }
}

object EcmDatabase {
    private const val TAG = "EcmDatabase"
    private var dynamicModels: List<EcmModel>? = null

    /** Issues found while loading the merged catalog; records are never dropped. */
    @Volatile
    private var validationIssues: List<EcmDataIssue> = emptyList()

    fun dataIssues(): List<EcmDataIssue> = validationIssues

    /** Test-only model override; never set by production code. */
    @Volatile
    private var modelsForTest: List<EcmModel>? = null

    internal fun useModelsForTest(models: List<EcmModel>) {
        modelsForTest = models
    }

    internal fun clearModelsForTest() {
        modelsForTest = null
    }

    /**
     * How an ECU identifier was matched. [confidence] is evidence strength, not a
     * probability; only exact identity fields ever reach the highest values.
     */
    enum class MatchMethod(val confidence: Int) {
        DB_ID(100),
        PART_NUMBER(95),
        ECM_ID_EXACT(90),
        ECM_ID_PREFIX(70)
    }

    data class EcuMatch(val model: EcmModel, val method: MatchMethod)

    @get:JvmName("modelsList")
    val allModels: List<EcmModel> get() = getAllModels()

    fun init(context: Context) {
        loadFromAssets(context)
    }

    /**
     * Last-resort fallback if the asset cannot be read. Setiap record di sini
     * adalah data contoh sintetis; tidak ada identitas kendaraan nyata, offset
     * ROM, atau batas RPM yang diklaim.
     */
    val MODELS: List<EcmModel> = listOf(
        EcmModel(
            id = "SAMPLE-ECM-001",
            partNumber = "00000-SAMPLE-001",
            ecmId = "0100000001",
            bikeName = "Contoh Matic 110 (sintetis)",
            yearRange = "2099",
            manufacturer = "Contoh",
            mcuType = "Belum terverifikasi",
            category = "Matic",
            statusNotes = "Data contoh sintetis untuk pengujian. Bukan identitas kendaraan nyata.",
            source = "sample"
        ),
        EcmModel(
            id = "SAMPLE-ECM-002",
            partNumber = "00000-SAMPLE-002",
            ecmId = "0100000002",
            bikeName = "Contoh Bebek 125 (sintetis)",
            yearRange = "2099",
            manufacturer = "Contoh",
            mcuType = "Belum terverifikasi",
            category = "Bebek",
            statusNotes = "Data contoh sintetis untuk pengujian. Bukan identitas kendaraan nyata.",
            source = "sample"
        ),
        EcmModel(
            id = "SAMPLE-ECM-003",
            partNumber = "00000-SAMPLE-003",
            ecmId = "0100000003",
            bikeName = "Contoh Sport 150 (sintetis)",
            yearRange = "2099",
            manufacturer = "Contoh",
            mcuType = "Belum terverifikasi",
            category = "Sport",
            statusNotes = "Data contoh sintetis untuk pengujian. Bukan identitas kendaraan nyata.",
            source = "sample"
        )
    )

    fun loadFromAssets(context: Context): List<EcmModel> {
        if (dynamicModels != null) return dynamicModels!!
        try {
            val stream = context.assets.open("ecm_database.json")
            val reader = InputStreamReader(stream)
            val jsonString = try {
                reader.readText()
            } finally {
                reader.close()
            }

            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<EcmModel>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.optString("id", "")
                val partNo = obj.optString("partNumber", "")
                val ecmId = obj.optString("ecmId", "")
                val bikeName = obj.optString("bikeName", "Belum Teridentifikasi")
                val yearRange = obj.optString("yearRange", "")
                val mfg = obj.optString("manufacturer", "Belum terverifikasi")
                val mcu = obj.optString("mcuType", "Belum terverifikasi")
                val flashSize = obj.optInt("flashSizeKb", 0).coerceAtLeast(0)
                val stockLim = obj.optInt("stockRevLimit", 0).coerceAtLeast(0)
                val maxLim = obj.optInt("maxSafeRevLimit", 0).coerceAtLeast(0)
                val recommendedLim = obj.optInt("recommendedRevLimit", 0).coerceAtLeast(0)
                val revLimitSource = obj.optString("revLimitSource", "unverified")
                val tempStr = obj.optString("tempSensorType", "ECT")
                val tempType = if (tempStr.equals("EOT", ignoreCase = true)) TempSensorType.EOT else TempSensorType.ECT
                val csStr = obj.optString("checksumType", "KEIHIN_16BIT_ADD")
                val csType = try {
                    ChecksumType.valueOf(csStr)
                } catch (e: Exception) {
                    if (mfg.contains("Shindengen", ignoreCase = true)) ChecksumType.SHINDENGEN_CRC16 else ChecksumType.KEIHIN_16BIT_ADD
                }

                fun parseOffset(key: String, defaultVal: Int = 0): Int {
                    val s = obj.optString(key, "")
                    return if (s.startsWith("0x", ignoreCase = true)) {
                        try { Integer.parseInt(s.substring(2), 16) } catch (e: Exception) { defaultVal }
                    } else {
                        obj.optInt(key, defaultVal)
                    }
                }

                list.add(
                    EcmModel(
                        id = id,
                        partNumber = partNo,
                        ecmId = ecmId,
                        startOffset = parseOffset("startOffset"),
                        bikeName = bikeName,
                        yearRange = yearRange,
                        manufacturer = mfg,
                        mcuType = mcu,
                        flashSizeKb = flashSize,
                        stockRevLimit = stockLim,
                        maxSafeRevLimit = maxLim,
                        recommendedRevLimit = recommendedLim,
                        revLimitSource = revLimitSource,
                        tempSensorType = tempType,
                        checksumType = csType,
                        checksumOffset = parseOffset("checksumOffset"),
                        revLimiterOffset = parseOffset("revLimiterOffset"),
                        fuelMapOffset = parseOffset("fuelMapOffset"),
                        fuelMapRows = obj.optInt("fuelMapRows", 0).coerceAtLeast(0),
                        fuelMapCols = obj.optInt("fuelMapCols", 0).coerceAtLeast(0),
                        ignitionMapOffset = parseOffset("ignitionMapOffset"),
                        ignitionMapRows = obj.optInt("ignitionMapRows", 0).coerceAtLeast(0),
                        ignitionMapCols = obj.optInt("ignitionMapCols", 0).coerceAtLeast(0),
                        idleTargetOffset = parseOffset("idleTargetOffset"),
                        isVerifiedDiagnostic = obj.optBoolean("isVerifiedDiagnostic", false),
                        isVerifiedRemap = obj.optBoolean("isVerifiedRemap", false),
                        category = obj.optString("category", "Universal"),
                        statusNotes = obj.optString("statusNotes", "Data referensi; verifikasi hardware belum dilakukan."),
                        source = obj.optString("source", "asset"),
                        defaultBaudRate = obj.optInt("defaultBaudRate", 10400),
                        country = obj.optString("country", ""),
                        displacementCc = obj.optDouble("displacementCc", 0.0),
                        calibrationId = obj.optString("calibrationId", ""),
                        pinout = obj.optString("pinout", ""),
                        protocol = obj.optString("protocol", "")
                    )
                )
            }
            if (list.isNotEmpty()) {
                dynamicModels = list
                val issues = EcmDatabaseValidator.validate(list)
                validationIssues = issues
                issues.filter { it.severity != EcmIssueSeverity.INFO }.forEach {
                    Log.w(TAG, "${it.code}: ${it.detail}")
                }
                Log.i(TAG, "Loaded ${list.size} ECM models from assets; ${issues.size} data issues recorded")
                return list
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load ecm_database.json from assets", e)
        }
        dynamicModels = MODELS
        return MODELS
    }

    fun getAllModels(): List<EcmModel> {
        return modelsForTest ?: dynamicModels ?: MODELS
    }

    /** Normalized ECM IDs present in the reference catalog. */
    fun knownEcmIds(): Set<String> = getAllModels()
        .asSequence()
        .map { EcmIdentityMatcher.normalize(it.ecmId) }
        .filter { it.isNotEmpty() && EcuIdCanonicalizer.isCanonicalId(it) }
        .toSet()

    /** Complete ECM ID field sizes actually represented by the catalog. */
    fun referenceEcmIdByteLengths(): List<Int> = getAllModels()
        .asSequence()
        .mapNotNull { EcuIdCanonicalizer.canonicalIdByteLength(it.ecmId) }
        .distinct()
        .sortedDescending()
        .toList()
        .ifEmpty { EcuIdCanonicalizer.SUPPORTED_FIELD_BYTES }

    /**
     * Resolves an ECU identity using explicit evidence precedence:
     * exact database id, exact part number, exact ECM ID, then a unique bounded
     * prefix/suffix ECM ID. Several candidates are never collapsed into one.
     */
    fun resolveIdentity(identity: EcuIdentity): IdentityResolution {
        val queries = LinkedHashSet<String>()
        // Canonical fields first: they are complete protocol fields selected from
        // the response, not arbitrary substrings.
        identity.canonicalEcuId?.let { queries.add(it) }
        identity.canonicalCandidates.forEach { queries.add(it) }
        identity.ecmId?.let { queries.add(it) }
        identity.partNumber?.let { queries.add(it) }
        identity.alternateIdentifiers.forEach { queries.add(it) }
        identity.legacyCandidate?.let { queries.add(it) }
        identity.ecuIdRaw?.let { queries.add(it) }
        identity.ecuIdNormalized?.let { queries.add(it) }

        val hasDocumentedShape = identity.canonicalEcuId != null ||
            identity.ecmId != null ||
            identity.partNumber != null ||
            identity.alternateIdentifiers.isNotEmpty()

        if (queries.isEmpty()) {
            return IdentityResolution(
                status = IdentityMatchStatus.NOT_FOUND,
                identity = identity,
                quality = IdentityMatchQuality.UNSUPPORTED,
                explanation = "No readable identifier was present in the response."
            )
        }

        val models = getAllModels()

        // Phase 1: exact identity only. Multiple distinct models sharing the same
        // field value are reported as AMBIGUOUS instead of choosing the first row.
        val exact = mutableListOf<IdentityCandidate>()
        for (query in queries) {
            val normalized = EcmIdentityMatcher.normalize(query)
            if (normalized.isEmpty()) continue
            for (model in models) {
                if (model.id.isNotBlank() && EcmIdentityMatcher.normalize(model.id) == normalized) {
                    exact += IdentityCandidate(model, IdentityField.DB_ID, IdentityRelation.EXACT, query)
                }
                if (model.partNumber.isNotBlank() &&
                    EcmIdentityMatcher.normalize(model.partNumber) == normalized
                ) {
                    exact += IdentityCandidate(model, IdentityField.PART_NUMBER, IdentityRelation.EXACT, query)
                }
                if (model.ecmId.isNotBlank() &&
                    EcmIdentityMatcher.canonicalEcmId(model.ecmId) == normalized
                ) {
                    exact += IdentityCandidate(model, IdentityField.ECM_ID, IdentityRelation.EXACT, query)
                }
            }
        }
        val distinctExact = exact.distinctBy { it.model.id }
        if (distinctExact.size == 1) {
            val candidate = distinctExact.single()
            val verified = candidate.model.isVerifiedDiagnostic || candidate.model.isVerifiedRemap
            return IdentityResolution(
                status = IdentityMatchStatus.EXACT,
                identity = identity,
                model = candidate.model,
                candidates = distinctExact,
                method = candidate.method,
                quality = if (verified) IdentityMatchQuality.VERIFIED else IdentityMatchQuality.EXACT,
                explanation = "${candidate.identityField.name} exact match for '${candidate.matchedValue}'." +
                    if (verified) " Database record is hardware-verified." else
                        " Database record itself is not hardware-verified."
            )
        }
        if (distinctExact.size > 1) {
            return IdentityResolution(
                status = IdentityMatchStatus.AMBIGUOUS,
                identity = identity,
                candidates = distinctExact,
                quality = IdentityMatchQuality.AMBIGUOUS,
                explanation = "The identity matches ${distinctExact.size} database records; " +
                    "no model was selected automatically."
            )
        }

        // Phase 2: bounded incomplete ECM ID (4-byte response against a 5-byte ID).
        val partial = mutableListOf<IdentityCandidate>()
        for (query in queries) {
            val normalized = EcmIdentityMatcher.normalize(query)
            if (normalized.isEmpty()) continue
            for (model in models) {
                if (model.ecmId.isBlank()) continue
                val relation = EcmIdentityMatcher.relation(normalized, model.ecmId) ?: continue
                if (relation == IdentityRelation.EXACT) continue
                partial += IdentityCandidate(model, IdentityField.ECM_ID, relation, query)
            }
        }
        val distinctPartial = partial.distinctBy { it.model.id }
        return when {
            distinctPartial.size == 1 -> {
                val candidate = distinctPartial.single()
                IdentityResolution(
                    status = IdentityMatchStatus.PARTIAL_MATCH,
                    identity = identity,
                    // A partial identity is diagnostic evidence only. Selecting the
                    // candidate model here would turn a truncated response into an
                    // ECU identification, so no model is published.
                    model = null,
                    candidates = distinctPartial,
                    method = MatchMethod.ECM_ID_PREFIX,
                    quality = IdentityMatchQuality.CANDIDATE,
                    explanation = "Unique ${candidate.relation.name.lowercase()} ECM ID match for " +
                        "'${candidate.matchedValue}', but the identity is incomplete because the response " +
                        "did not carry the complete database ID. No model is selected."
                )
            }
            distinctPartial.size > 1 -> IdentityResolution(
                status = IdentityMatchStatus.AMBIGUOUS,
                identity = identity,
                candidates = distinctPartial,
                quality = IdentityMatchQuality.AMBIGUOUS,
                explanation = "The incomplete identity matches ${distinctPartial.size} database records; " +
                    "no model was selected automatically."
            )
            else -> IdentityResolution(
                status = IdentityMatchStatus.NOT_FOUND,
                identity = identity,
                quality = if (hasDocumentedShape) {
                    IdentityMatchQuality.UNKNOWN
                } else {
                    IdentityMatchQuality.UNSUPPORTED
                },
                explanation = "No database record matches '${queries.first()}' exactly or by a bounded " +
                    "ECM ID prefix/suffix."
            )
        }
    }

    /**
     * Resolves an ECM ID only when exactly one reference record matches the complete
     * canonical ID. A truncated ID returns null instead of a candidate model.
     */
    fun findByEcmId(ecmId: String): EcmModel? {
        val clean = EcmIdentityMatcher.normalize(ecmId)
        if (clean.isEmpty()) return null
        val identity = EcuIdentity.fromIdentifier(ecmId, "ecm-lookup")
        val resolution = resolveIdentity(identity)
        return if (resolution.status == IdentityMatchStatus.EXACT) {
            resolution.model
        } else {
            null
        }
    }

    fun findByPartNumber(partNo: String): EcmModel? {
        val clean = partNo.trim()
        if (clean.isEmpty()) return null
        val matches = getAllModels().filter { EcmIdentityMatcher.partNumberMatches(clean, it.partNumber) }
        return matches.singleOrNull()
    }

    fun findById(id: String): EcmModel? {
        val clean = id.trim().uppercase(Locale.ROOT)
        return getAllModels().singleOrNull { it.id.uppercase(Locale.ROOT) == clean }
    }

    fun getByCategory(category: String): List<EcmModel> {
        if (category.equals("All", ignoreCase = true)) return getAllModels()
        return getAllModels().filter { it.category.contains(category, ignoreCase = true) }
    }

    /** Records with positive Indonesia + <=250 cc evidence; used by the vehicle picker. */
    fun activeCatalogModels(): List<EcmModel> = getAllModels().filter { it.isActiveCatalogEligible }

    /**
     * Identifies an ECU identifier and records which evidence produced the match.
     * Exact DB id and exact part number outrank ECM ID matches; an ambiguous
     * identifier yields no model.
     */
    fun identify(identifier: String): EcuMatch? {
        val clean = identifier.trim()
        if (clean.isEmpty()) return null
        val resolution = resolveIdentity(EcuIdentity.fromIdentifier(clean, "legacy-identify"))
        val model = resolution.model ?: return null
        val method = resolution.method ?: return null
        return EcuMatch(model, method)
    }

    /**
     * Strict identification for an ECU response identifier. Accepts an exact DB id,
     * an exact part number, or a unique ECM ID identity match. Never falls back to
     * loose substring matching.
     */
    fun findExactModel(identifier: String): EcmModel? {
        return identify(identifier)?.model
    }

    fun createUnverifiedModel(identifier: String, displayName: String? = null): EcmModel {
        val cleanId = identifier.trim()
        return EcmModel(
            id = cleanId.ifEmpty { "UNIDENTIFIED" },
            partNumber = cleanId.ifEmpty { "Unregistered Honda ECM" },
            ecmId = cleanId,
            startOffset = 0,
            bikeName = displayName ?: if (cleanId.isNotEmpty()) {
                "ECM Belum Teridentifikasi ($cleanId)"
            } else {
                "ECM Belum Teridentifikasi"
            },
            yearRange = "",
            manufacturer = "Belum Terverifikasi",
            mcuType = "Belum Teridentifikasi",
            flashSizeKb = 0,
            stockRevLimit = 0,
            maxSafeRevLimit = 0,
            recommendedRevLimit = 0,
            revLimitSource = "unverified",
            tempSensorType = TempSensorType.ECT,
            checksumType = ChecksumType.KEIHIN_16BIT_ADD,
            checksumOffset = 0,
            revLimiterOffset = 0,
            fuelMapOffset = 0,
            fuelMapRows = 0,
            fuelMapCols = 0,
            ignitionMapOffset = 0,
            ignitionMapRows = 0,
            ignitionMapCols = 0,
            idleTargetOffset = 0,
            isVerifiedDiagnostic = false,
            isVerifiedRemap = false,
            category = "Universal",
            statusNotes = if (cleanId.isNotEmpty()) {
                "ID ECM: $cleanId belum terverifikasi di database RevCore. Operasi flash dinonaktifkan."
            } else {
                "ECM merespons di K-Line, tetapi ID belum dapat dibaca. Operasi flash dinonaktifkan."
            },
            source = "unverified",
            defaultBaudRate = 10400
        )
    }

    /**
     * Placeholder shown when one identity matches several records. No candidate is
     * promoted to "the" model; the UI lists every candidate separately.
     */
    fun createAmbiguousModel(
        identity: EcuIdentity,
        candidates: List<IdentityCandidate>
    ): EcmModel {
        val label = identity.ecuIdRaw ?: identity.ecmId ?: "ID TIDAK TERBACA"
        val summary = candidates.joinToString("; ") {
            "${it.model.id}/${it.model.partNumber} via ${it.identityField.name}:${it.relation.name}"
        }
        return createUnverifiedModel(label, "ECM AMBIGU ($label)").copy(
            partNumber = "TIDAK DAPAT DITENTUKAN",
            statusNotes = "Identitas '$label' cocok dengan ${candidates.size} record database. " +
                "Model tidak dipilih otomatis. Kandidat: $summary. Operasi flash dinonaktifkan.",
            source = "ambiguous"
        )
    }

    fun findOrCreateUniversalModel(identifier: String): EcmModel =
        findExactModel(identifier) ?: createUnverifiedModel(identifier)
}
