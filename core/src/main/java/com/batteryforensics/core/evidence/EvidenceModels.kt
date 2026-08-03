package com.batteryforensics.core.evidence

import kotlinx.serialization.Serializable

/**
 * Epistemic strength of a finding. Never mix levels in a single claim.
 * Measured ★★★★★ · Derived ★★★★☆ · Inferred ★★★☆☆ · Speculative ★☆☆☆☆
 */
@Serializable
enum class ConfidenceLevel {
    MEASURED,
    DERIVED,
    INFERRED,
    SPECULATIVE,
}

@Serializable
data class Confidence(
    val scorePercent: Int,
    val level: ConfidenceLevel,
) {
    init {
        require(scorePercent in 0..100) { "Confidence must be 0..100, was $scorePercent" }
    }

    val starsLabel: String
        get() = when (level) {
            ConfidenceLevel.MEASURED -> "★★★★★ Measured"
            ConfidenceLevel.DERIVED -> "★★★★☆ Derived"
            ConfidenceLevel.INFERRED -> "★★★☆☆ Inferred"
            ConfidenceLevel.SPECULATIVE -> "★☆☆☆☆ Speculative"
        }
}

@Serializable
data class Evidence(
    val id: String,
    val description: String,
    val metricKey: String,
    val observedValue: String,
    val threshold: String? = null,
    val confidenceLevel: ConfidenceLevel,
)

@Serializable
data class SupportingMetric(
    val key: String,
    val label: String,
    val value: String,
    val unit: String? = null,
)

@Serializable
enum class DiagnosticCategory {
    BATTERY_CHEMISTRY,
    THERMAL,
    CHARGING,
    DISPLAY,
    NETWORK,
    MODEM,
    DOZE,
    WAKE_LOCKS,
    ALARM_MANAGER,
    JOBS,
    FOREGROUND_SERVICES,
    APP_STANDBY,
    SENSORS,
    STORAGE,
    HISTORICAL_REGRESSION,
    STANDBY,
}

@Serializable
data class Diagnosis(
    val id: String,
    val title: String,
    val category: DiagnosticCategory,
    val explanation: String,
    val confidence: Confidence,
    val evidence: List<Evidence>,
    val supportingMetrics: List<SupportingMetric>,
    val counterEvidence: List<Evidence>,
    val recommendedActions: List<String>,
    val probabilityPercent: Int,
) {
    init {
        require(probabilityPercent in 0..100)
    }
}
