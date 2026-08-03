package com.batteryforensics.diagnostics

import com.batteryforensics.core.evidence.Diagnosis
import com.batteryforensics.core.model.MonitoringSample
import com.batteryforensics.ruleengine.RuleContext
import com.batteryforensics.ruleengine.RuleEngine
import com.batteryforensics.ruleengine.rules.DefaultRules

/**
 * Orchestrates root-cause ranking from monitoring samples via the rule engine.
 */
class DiagnosticsEngine(
    private val ruleEngine: RuleEngine = RuleEngine(DefaultRules.all()),
) {
    fun investigate(samples: List<MonitoringSample>, nowEpochMs: Long = System.currentTimeMillis()): InvestigationResult {
        val diagnoses = ruleEngine.evaluate(RuleContext(samples = samples, nowEpochMs = nowEpochMs))
        return InvestigationResult(
            diagnoses = diagnoses,
            sampleCount = samples.size,
            evaluatedAtEpochMs = nowEpochMs,
        )
    }
}

data class InvestigationResult(
    val diagnoses: List<Diagnosis>,
    val sampleCount: Int,
    val evaluatedAtEpochMs: Long,
)
