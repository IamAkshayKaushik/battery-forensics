package com.batteryforensics.ruleengine

import com.batteryforensics.core.evidence.Diagnosis
import com.batteryforensics.core.model.MonitoringSample

/**
 * Forensic rule contract. Every rule must produce evidence, confidence,
 * explanation, and recommendations — never opaque scores alone.
 */
interface ForensicRule {
    val id: String
    val title: String

    fun evaluate(context: RuleContext): RuleEvaluation?
}

data class RuleContext(
    val samples: List<MonitoringSample>,
    val nowEpochMs: Long = System.currentTimeMillis(),
)

data class RuleEvaluation(
    val triggered: Boolean,
    val diagnosis: Diagnosis,
)
