package com.stuartjohnson.rainsong.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrangementTest {
    @Test
    fun parseFencedJson() {
        val raw = """
            ```json
            {"title":"Drizzle etude","mood":"soft","rootHz":196,"brightness":0.4,"density":0.2,"prompt":"quiet rain pad"}
            ```
        """.trimIndent()
        val parsed = Arrangement.parseJson(raw, Arrangement.fromIntensity(RainPatternSummary(0f, 0f, 0)))
        assertEquals("Drizzle etude", parsed.title)
        assertEquals(196f, parsed.rootHz, 0.01f)
        assertEquals(Arrangement.Source.MuseSpark, parsed.source)
    }

    @Test
    fun intensityFallbackDownpour() {
        val a = Arrangement.fromIntensity(RainPatternSummary(12f, 40000f, 1))
        assertEquals("Downpour", a.title)
        assertTrue(a.density > 0.5f)
        assertEquals(Arrangement.Source.LocalFallback, a.source)
    }
}
