package com.example.model.scopes

import org.junit.Assert.assertNotEquals
import org.junit.Test

class ScopeDataStateTest {
    @Test
    fun separatelyAnalyzedScopeArraysProduceDistinctFlowValues() {
        val previous = ScopeData()
        val next = previous.copy(
            histogramR = previous.histogramR.clone(),
            histogramG = previous.histogramG.clone(),
            histogramB = previous.histogramB.clone(),
            histogramLuma = previous.histogramLuma.clone(),
            waveformLuma = previous.waveformLuma.clone(),
            paradeR = previous.paradeR.clone(),
            paradeG = previous.paradeG.clone(),
            paradeB = previous.paradeB.clone(),
            vectorscope = previous.vectorscope.clone()
        )

        assertNotEquals(previous, next)
    }
}
