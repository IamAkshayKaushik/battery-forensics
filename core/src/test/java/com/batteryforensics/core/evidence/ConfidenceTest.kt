package com.batteryforensics.core.evidence

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ConfidenceTest {
    @Test
    fun starsLabel_matchesLevel() {
        assertThat(Confidence(95, ConfidenceLevel.MEASURED).starsLabel).contains("Measured")
        assertThat(Confidence(80, ConfidenceLevel.DERIVED).starsLabel).contains("Derived")
        assertThat(Confidence(60, ConfidenceLevel.INFERRED).starsLabel).contains("Inferred")
        assertThat(Confidence(20, ConfidenceLevel.SPECULATIVE).starsLabel).contains("Speculative")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOutOfRange() {
        Confidence(120, ConfidenceLevel.MEASURED)
    }
}
