package com.batteryforensics.ruleengine

import com.batteryforensics.core.evidence.Diagnosis

/**
 * Runs all registered rules and returns ranked root causes.
 * Guesses are never promoted above their labeled confidence level.
 */
class RuleEngine(
    private val rules: Set<ForensicRule>,
) {
    fun evaluate(context: RuleContext): List<Diagnosis> =
        rules.mapNotNull { rule ->
            rule.evaluate(context)?.takeIf { it.triggered }?.diagnosis
        }.sortedWith(
            compareByDescending<Diagnosis> { it.probabilityPercent }
                .thenByDescending { it.confidence.scorePercent },
        )

    companion object {
        fun withRules(vararg rules: ForensicRule): RuleEngine =
            RuleEngine(rules.toSet())
    }
}
