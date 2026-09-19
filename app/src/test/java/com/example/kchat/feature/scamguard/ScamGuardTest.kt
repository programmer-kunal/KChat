package com.example.kchat.feature.scamguard

import com.example.kchat.model.Message
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ScamGuardTest {

    private val service = GeminiScamGuardService()

    // 1. LOW-risk JSON parsing
    @Test
    fun parseLowRiskJson_returnsLowRiskResult() {
        val json = """
            {
              "riskLevel": "LOW",
              "isSuspicious": false,
              "summary": "Normal friendly conversation with no scam indicators.",
              "indicators": [],
              "recommendedActions": ["No action needed. Continue chatting normally."]
            }
        """.trimIndent()

        val result = service.parseScamGuardResponse(json)
        assertEquals(ScamRiskLevel.LOW, result.riskLevel)
        assertEquals("Low Risk", result.riskLevel.displayName)
        assertFalse(result.isSuspicious)
        assertEquals("Normal friendly conversation with no scam indicators.", result.summary)
        assertTrue(result.indicators.isEmpty())
        assertEquals(1, result.recommendedActions.size)
    }

    // 2. MEDIUM-risk JSON parsing
    @Test
    fun parseMediumRiskJson_returnsMediumRiskResult() {
        val json = """
            {
              "riskLevel": "MEDIUM",
              "isSuspicious": true,
              "summary": "Message contains an unsolicited external link with artificial urgency.",
              "indicators": ["Unfamiliar link http://claim-prize-today.xyz", "Urgent call to action"],
              "recommendedActions": ["Do not click external links from unverified contacts."]
            }
        """.trimIndent()

        val result = service.parseScamGuardResponse(json)
        assertEquals(ScamRiskLevel.MEDIUM, result.riskLevel)
        assertEquals("Potential Risk", result.riskLevel.displayName)
        assertTrue(result.isSuspicious)
        assertEquals(2, result.indicators.size)
        assertEquals("Unfamiliar link http://claim-prize-today.xyz", result.indicators[0])
    }

    // 3. HIGH-risk JSON parsing
    @Test
    fun parseHighRiskJson_returnsHighRiskResult() {
        val json = """
            {
              "riskLevel": "HIGH",
              "isSuspicious": true,
              "summary": "Urgent request demanding a one-time password (OTP) with threats of account closure.",
              "indicators": [
                "Demanding 6-digit OTP code",
                "Impersonating official security team",
                "Threat of immediate account suspension"
              ],
              "recommendedActions": [
                "Never share your OTP with anyone.",
                "Report and block this sender immediately."
              ]
            }
        """.trimIndent()

        val result = service.parseScamGuardResponse(json)
        assertEquals(ScamRiskLevel.HIGH, result.riskLevel)
        assertEquals("High Risk", result.riskLevel.displayName)
        assertTrue(result.isSuspicious)
        assertEquals(3, result.indicators.size)
        assertEquals(2, result.recommendedActions.size)
    }

    // 4. Missing optional fields do not crash and supply defaults
    @Test
    fun parseMissingOptionalFields_doesNotCrash() {
        val json = """
            {
              "riskLevel": "LOW"
            }
        """.trimIndent()

        val result = service.parseScamGuardResponse(json)
        assertEquals(ScamRiskLevel.LOW, result.riskLevel)
        assertFalse(result.isSuspicious)
        assertTrue(result.summary.isNotBlank())
        assertTrue(result.indicators.isEmpty())
        assertTrue(result.recommendedActions.isNotEmpty())
    }

    // 5. Markdown code fences are stripped cleanly
    @Test
    fun parseMarkdownCodeFences_cleansAndParses() {
        val raw = """
            ```json
            {
              "riskLevel": "HIGH",
              "isSuspicious": true,
              "summary": "Deceptive payment link detected.",
              "indicators": ["Fake payment portal link"],
              "recommendedActions": ["Do not submit payment details."]
            }
            ```
        """.trimIndent()

        val result = service.parseScamGuardResponse(raw)
        assertEquals(ScamRiskLevel.HIGH, result.riskLevel)
        assertTrue(result.isSuspicious)
        assertEquals(1, result.indicators.size)
    }

    // 6. Malformed JSON throws exception gracefully for runCatching
    @Test
    fun parseMalformedJson_failsGracefully() {
        try {
            service.parseScamGuardResponse("This is not JSON at all.")
            fail("Expected exception for malformed JSON")
        } catch (e: Exception) {
            // Expected
            assertTrue(e is Exception)
        }
    }

    // 7. Empty selected-message list returns safe LOW risk result without making network call
    @Test
    fun analyzeMessages_emptyList_returnsSafeLowRisk() = runBlocking {
        val result = service.analyzeMessages(emptyList(), "user_123")
        assertTrue(result.isSuccess)
        val scamResult = result.getOrNull()!!
        assertEquals(ScamRiskLevel.LOW, scamResult.riskLevel)
        assertFalse(scamResult.isSuspicious)
        assertEquals("No messages selected for analysis.", scamResult.summary)
    }

    // 8. Multiple selected messages preserve chronological order
    @Test
    fun messageList_preservesChronologicalOrder() {
        val m1 = Message(id = "1", message = "Third message", createdAt = 3000L)
        val m2 = Message(id = "2", message = "First message", createdAt = 1000L)
        val m3 = Message(id = "3", message = "Second message", createdAt = 2000L)

        val selected = listOf(m1, m2, m3)
        val chronological = selected.sortedBy { it.createdAt }

        assertEquals("2", chronological[0].id)
        assertEquals("3", chronological[1].id)
        assertEquals("1", chronological[2].id)
    }

    // 9. Formatted text export contains required sections
    @Test
    fun formattedText_containsExpectedSections() {
        val scamResult = ScamGuardResult(
            riskLevel = ScamRiskLevel.HIGH,
            isSuspicious = true,
            summary = "Suspicious financial scam indicators present.",
            indicators = listOf("Urgent request for money transfer"),
            recommendedActions = listOf("Do not send money or share bank details.")
        )

        val formatted = scamResult.toFormattedText()
        assertTrue(formatted.contains("🛡️ KChat Scam Guard Analysis"))
        assertTrue(formatted.contains("Risk Assessment: High Risk"))
        assertTrue(formatted.contains("Summary:"))
        assertTrue(formatted.contains("Suspicious financial scam indicators present."))
        assertTrue(formatted.contains("Suspicious Indicators:"))
        assertTrue(formatted.contains("• Urgent request for money transfer"))
        assertTrue(formatted.contains("Recommended Actions:"))
        assertTrue(formatted.contains("• Do not send money or share bank details."))
    }
}
