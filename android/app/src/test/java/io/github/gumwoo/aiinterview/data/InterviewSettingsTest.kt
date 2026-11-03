package io.github.gumwoo.aiinterview.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InterviewSettingsTest {
    @Test
    fun `accepts https and emulator address only`() {
        assertTrue(InterviewSettings.isValidServerUrl("https://pc.tailnet.ts.net"))
        assertTrue(InterviewSettings.isValidServerUrl("http://10.0.2.2:18080"))
        assertFalse(InterviewSettings.isValidServerUrl("http://pc.tailnet.ts.net"))
        assertFalse(InterviewSettings.isValidServerUrl("https://"))
        assertFalse(InterviewSettings.isValidServerUrl(""))
    }

    @Test
    fun `converts review percent to ratio`() {
        assertEquals(0.4, InterviewSettings().reviewRatio, 0.0)
        assertEquals(0.0, InterviewSettings(reviewPercent = 0).reviewRatio, 0.0)
        assertEquals(0.6, InterviewSettings(reviewPercent = 60).reviewRatio, 0.0)
    }

    @Test
    fun `parses comma separated topics`() {
        assertEquals(listOf("Kubernetes", "AWS"), InterviewSettings.parseTopics(" Kubernetes, ,AWS ,"))
    }
}
