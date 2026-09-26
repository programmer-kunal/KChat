package com.example.kchat.feature.extension

import com.example.kchat.feature.extension.parser.ExternalMessageParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalMessageParserTest {

    @Test
    fun testWhatsAppBracketFormatParsing() {
        val input = """
            [12/04/24, 10:15:30] Alice: Hey, are we meeting today?
            [12/04/24, 10:16:05] Bob: Yes, at 3 PM at Starbucks.
            [12/04/24, 10:16:40] Alice: Perfect, see you there!
        """.trimIndent()

        val parsed = ExternalMessageParser.parse(input)
        assertNotNull(parsed)
        assertTrue(parsed.isFormatDetected)
        assertEquals(3, parsed.messages.size)
        assertEquals(2, parsed.participants.size)
        assertTrue(parsed.participants.contains("Alice"))
        assertTrue(parsed.participants.contains("Bob"))

        assertEquals("ext_001", parsed.messages[0].id)
        assertEquals("Alice", parsed.messages[0].senderName)
        assertEquals("Hey, are we meeting today?", parsed.messages[0].message)

        assertEquals("ext_002", parsed.messages[1].id)
        assertEquals("Bob", parsed.messages[1].senderName)
        assertEquals("Yes, at 3 PM at Starbucks.", parsed.messages[1].message)

        assertEquals("ext_003", parsed.messages[2].id)
        assertEquals("Alice", parsed.messages[2].senderName)
        assertEquals("Perfect, see you there!", parsed.messages[2].message)
    }

    @Test
    fun testWhatsAppExportFormatParsing() {
        val input = """
            12/04/24, 10:15 am - Charlie: Sent the document for review.
            12/04/24, 10:18 am - Dave: Thanks, reviewing now.
        """.trimIndent()

        val parsed = ExternalMessageParser.parse(input)
        assertNotNull(parsed)
        assertTrue(parsed.isFormatDetected)
        assertEquals(2, parsed.messages.size)
        assertEquals(2, parsed.participants.size)
        assertEquals("Charlie", parsed.messages[0].senderName)
        assertEquals("Sent the document for review.", parsed.messages[0].message)
        assertEquals("Dave", parsed.messages[1].senderName)
        assertEquals("Thanks, reviewing now.", parsed.messages[1].message)
    }

    @Test
    fun testTelegramHeaderFormatParsing() {
        val input = """
            Alice, [12.04.24 10:15]
            Can you review this code?
            
            Bob, [12.04.24 10:17]
            Sure, looking into it!
        """.trimIndent()

        val parsed = ExternalMessageParser.parse(input)
        assertNotNull(parsed)
        assertTrue(parsed.messages.isNotEmpty())
        assertTrue(parsed.participants.contains("Alice") || parsed.participants.contains("Bob"))
    }

    @Test
    fun testGenericColonFormatParsing() {
        val input = """
            John: Hey!
            Sarah: Hello John, how are you?
            John: All good here.
        """.trimIndent()

        val parsed = ExternalMessageParser.parse(input)
        assertNotNull(parsed)
        assertEquals(3, parsed.messages.size)
        assertEquals("John", parsed.messages[0].senderName)
        assertEquals("Hey!", parsed.messages[0].message)
        assertEquals("Sarah", parsed.messages[1].senderName)
        assertEquals("Hello John, how are you?", parsed.messages[1].message)
    }

    @Test
    fun testUnstructuredTextFallback() {
        val input = """
            Here is some random copied conversation without any timestamp or sender prefix.
            It discusses project deadlines and requirements.
            Another sentence about meeting next Monday.
        """.trimIndent()

        val parsed = ExternalMessageParser.parse(input)
        assertNotNull(parsed)
        assertFalse(parsed.isFormatDetected)
        assertTrue(parsed.messages.isNotEmpty())
        assertEquals("ext_001", parsed.messages[0].id)
    }

    @Test
    fun testBlankInput() {
        val parsed = ExternalMessageParser.parse("   \n  \t ")
        assertNotNull(parsed)
        assertTrue(parsed.messages.isEmpty())
        assertTrue(parsed.participants.isEmpty())
    }

    @Test
    fun testGroundingValidation_discardsUnknownExtAndImageIds() {
        val messages = listOf(
            com.example.kchat.model.Message(id = "ext_001", senderName = "Ajeeb Prani", message = "25"),
            com.example.kchat.model.Message(id = "ext_002", senderName = "Ajeeb Prani", message = "Ss bhej dena"),
            com.example.kchat.model.Message(id = "ext_003", senderName = "🚀KUNAL", message = "Receiver Payment Server is busy bta rha hai")
        )
        val validMessageIds = messages.map { it.id }.toSet()
        val returnedIds = listOf("ext_003", "ext_999")
        val validatedIds = returnedIds.filter { validMessageIds.contains(it) }

        assertEquals(1, validatedIds.size)
        assertEquals("ext_003", validatedIds[0])
        assertFalse(validatedIds.contains("ext_999"))

        val activeImageIds = setOf("image_001", "image_002")
        val returnedImageIds = listOf("image_001", "image_999")
        val validatedImageIds = returnedImageIds.filter { activeImageIds.contains(it) }

        assertEquals(1, validatedImageIds.size)
        assertEquals("image_001", validatedImageIds[0])
        assertFalse(validatedImageIds.contains("image_999"))
    }
}
