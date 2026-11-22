package com.idex.android

import com.idex.android.recognition.PokemonScoring
import com.idex.android.recognition.RecognitionException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PokemonScoringTest {

    private val sampleLabels = listOf(
        1 to "bulbasaur",
        4 to "charmander",
        7 to "squirtle",
        25 to "pikachu",
    )

    @Test
    fun acceptsTopCandidateWhenConfidenceAndMarginMeetThreshold() {
        // Logits with clear winner at index 3 (pikachu)
        val logits = doubleArrayOf(0.5, 1.0, 0.2, 4.0)
        val result = PokemonScoring.score(logits, sampleLabels)

        assertNotNull(result.accepted)
        assertEquals(25, result.accepted?.dexId)
        assertEquals("pikachu", result.accepted?.name)
        assertEquals(3, result.candidates.size)
        assertEquals(listOf(25, 4, 1), result.candidates.map { it.dexId })
        assertTrue(result.candidates[0].confidence > 0.25)
        assertEquals(3.0, result.logitMargin, 1e-9)
    }

    @Test
    fun rejectsWhenMarginIsBelowThreshold() {
        // Margin = 0.2 (< 0.5), even though top confidence > 0.25
        val logits = doubleArrayOf(-5.0, 3.0, -5.0, 3.2)
        val result = PokemonScoring.score(logits, sampleLabels)

        assertNull(result.accepted)
        assertEquals(25, result.candidates[0].dexId)
        assertEquals(0.2, result.logitMargin, 1e-9)
    }

    @Test
    fun rejectsWhenConfidenceIsBelowThreshold() {
        // 6 classes where top logit has margin >= 0.5 but softmax < 0.25?
        // Actually if 10 classes share ~0.0 and top is 0.55 -> exp(0.55) / (9 + exp(0.55)) = 1.733 / 10.733 = 0.161 < 0.25
        val manyLabels = (1..12).map { it to "p$it" }
        val logits = DoubleArray(12) { 0.0 }
        logits[5] = 0.55
        val result = PokemonScoring.score(logits, manyLabels)

        assertNull(result.accepted)
        assertEquals(6, result.candidates[0].dexId)
        assertEquals(0.55, result.logitMargin, 1e-9)
        assertTrue(result.candidates[0].confidence < 0.25)
    }

    @Test
    fun handlesSingleClassSafely() {
        val result = PokemonScoring.score(doubleArrayOf(2.0), listOf(25 to "pikachu"))
        assertNotNull(result.accepted)
        assertEquals(1.0, result.candidates[0].confidence, 1e-9)
        assertEquals(Double.POSITIVE_INFINITY, result.logitMargin, 0.0)
    }

    @Test(expected = RecognitionException::class)
    fun throwsOnSizeMismatch() {
        PokemonScoring.score(doubleArrayOf(1.0, 2.0), sampleLabels)
    }

    @Test(expected = RecognitionException::class)
    fun throwsOnNaNLogit() {
        PokemonScoring.score(doubleArrayOf(1.0, Double.NaN, 0.5, 2.0), sampleLabels)
    }

    @Test(expected = RecognitionException::class)
    fun throwsOnInfinityLogit() {
        PokemonScoring.score(doubleArrayOf(1.0, Double.POSITIVE_INFINITY, 0.5, 2.0), sampleLabels)
    }

    @Test(expected = RecognitionException::class)
    fun throwsOnEmptyLabels() {
        PokemonScoring.score(doubleArrayOf(), emptyList())
    }
}
