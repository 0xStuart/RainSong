package com.stuartjohnson.rainsong.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiServiceClientTest {
    @Test
    fun normalizeAddsHttpAndStripsSlash() {
        assertEquals("http://192.168.1.9:8765", AiServiceClient.normalizeBase("192.168.1.9:8765/"))
        assertEquals("https://rainsong.example.com", AiServiceClient.normalizeBase("https://rainsong.example.com/"))
        assertNull(AiServiceClient.normalizeBase("  "))
    }
}
