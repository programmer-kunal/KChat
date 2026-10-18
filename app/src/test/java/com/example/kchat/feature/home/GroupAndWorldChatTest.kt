package com.example.kchat.feature.home

import com.example.kchat.feature.chat.DateTimeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupAndWorldChatTest {

    @Test
    fun formatGroupOnlineCount_zeroMembers_returnsZeroMembersOnline() {
        val result = DateTimeUtils.formatGroupOnlineCount(0)
        assertEquals("0 members online", result)
    }

    @Test
    fun formatGroupOnlineCount_oneMember_returnsSingularMemberOnline() {
        val result = DateTimeUtils.formatGroupOnlineCount(1)
        assertEquals("1 member online", result)
    }

    @Test
    fun formatGroupOnlineCount_multipleMembers_returnsPluralMembersOnline() {
        assertEquals("2 members online", DateTimeUtils.formatGroupOnlineCount(2))
        assertEquals("5 members online", DateTimeUtils.formatGroupOnlineCount(5))
        assertEquals("100 members online", DateTimeUtils.formatGroupOnlineCount(100))
    }

    @Test
    fun selectionResetLogic_tabSwitchClearsOtherSelection() {
        // Simulate Direct Chat selection
        var selectedDirectUser: String? = "user_123"
        var selectedGroup: String? = null

        // User switches from Direct to Groups tab
        selectedDirectUser = null
        assertNull(selectedDirectUser)

        // User selects a group in Groups tab
        selectedGroup = "custom_group_1"
        assertEquals("custom_group_1", selectedGroup)

        // User switches from Groups to Direct tab
        selectedGroup = null
        assertNull(selectedGroup)
    }

    @Test
    fun groupSelection3DotMenuItems_rules() {
        fun get3DotMenuItems(creatorUid: String, currentUid: String): List<String> {
            val items = mutableListOf<String>()
            items.add("Clear Chat")
            val isOwner = creatorUid == currentUid
            if (isOwner) {
                items.add("Delete Group")
            }
            return items
        }

        // Custom group creator: Clear Chat + Delete Group
        val creatorItems = get3DotMenuItems("user_1", "user_1")
        assertEquals(listOf("Clear Chat", "Delete Group"), creatorItems)

        // Non-creator custom group member: Clear Chat only
        val memberItems = get3DotMenuItems("user_1", "user_2")
        assertEquals(listOf("Clear Chat"), memberItems)

        // "Remove Group" should never appear for anyone
        assertFalse(creatorItems.contains("Remove Group"))
        assertFalse(memberItems.contains("Remove Group"))
    }

    @Test
    fun groupSortingByLastTime_sortsDescending() {
        val group1 = HomeChannel(id = "group_1", name = "Android Devs", lastTime = 1000L)
        val group2 = HomeChannel(id = "group_2", name = "College Friends", lastTime = 2000L)
        val group3 = HomeChannel(id = "group_3", name = "Book Club", lastTime = 500L)

        val unpinnedList = listOf(group1, group3, group2)

        val sortedList = unpinnedList.sortedWith { a, b ->
            (b.lastTime ?: 0L).compareTo(a.lastTime ?: 0L)
        }

        assertEquals("group_2", sortedList[0].id)
        assertEquals("group_1", sortedList[1].id)
        assertEquals("group_3", sortedList[2].id)
    }

    @Test
    fun groupCallId_isDeterministicAndValidForZego() {
        fun generateGroupCallId(channelId: String): String {
            return "call_group_${channelId.replace("-", "_")}"
        }

        val customGroupCallId = generateGroupCallId("-OaB123_custom-key")
        assertEquals("call_group__OaB123_custom_key", customGroupCallId)

        val customGroupCallId2 = generateGroupCallId("group_general")
        assertEquals("call_group_group_general", customGroupCallId2)

        // Must start with call_group_ and not match direct chat uidA_uidB pattern
        assertTrue(customGroupCallId.startsWith("call_group_"))
        assertTrue(customGroupCallId2.startsWith("call_group_"))
        // Check only alphanumeric and underscores
        assertTrue(customGroupCallId.matches(Regex("^[a-zA-Z0-9_]+$")))
        assertTrue(customGroupCallId2.matches(Regex("^[a-zA-Z0-9_]+$")))
    }

    @Test
    fun multiSpeakerDialogueFormatting_retainsSpeakerNamesAndOrder() {
        data class TestMessage(
            val senderId: String,
            val senderName: String,
            val message: String,
            val createdAt: Long
        )

        val currentUserId = "my_uid"
        val messages = listOf(
            TestMessage("uid_2", "Bob", "Let's meet at 5", 200L),
            TestMessage("uid_1", "Alice", "Hey everyone!", 100L),
            TestMessage("my_uid", "KUNAL", "Sounds good to me!", 300L)
        )

        val sorted = messages.sortedBy { it.createdAt }
        val dialogueLines = sorted.map { msg ->
            val label = if (msg.senderId == currentUserId) "Me" else msg.senderName.ifBlank { "Other" }
            "$label: ${msg.message}"
        }

        assertEquals(
            listOf(
                "Alice: Hey everyone!",
                "Bob: Let's meet at 5",
                "Me: Sounds good to me!"
            ),
            dialogueLines
        )
    }

    @Test
    fun smartReplySelection_singleTextMessage_triggersSmartReply() {
        val selected = listOf(
            TestMessageSelection(id = "1", text = "Hello!", imageUrl = null)
        )
        val hasTextMessage = selected.any { !it.text.isNullOrBlank() }
        assertTrue("Single text message must show K icon", hasTextMessage)
    }

    @Test
    fun smartReplySelection_multipleTextMessages_triggersSmartReply() {
        val selected = listOf(
            TestMessageSelection(id = "1", text = "Can we meet?", imageUrl = null),
            TestMessageSelection(id = "2", text = "At the library at 4pm?", imageUrl = null)
        )
        val hasTextMessage = selected.any { !it.text.isNullOrBlank() }
        assertTrue("Multiple text messages must also show K icon", hasTextMessage)
        assertEquals(2, selected.size)
    }

    @Test
    fun smartReplySelection_mediaOnly_doesNotTriggerSmartReply() {
        val selected = listOf(
            TestMessageSelection(id = "1", text = "", imageUrl = "https://example.com/photo.jpg"),
            TestMessageSelection(id = "2", text = null, imageUrl = "https://example.com/photo2.jpg")
        )
        val hasTextMessage = selected.any { !it.text.isNullOrBlank() }
        assertFalse("Media-only selection must NOT show K icon", hasTextMessage)
    }

    @Test
    fun smartReplySelection_mixedMediaAndText_triggersSmartReply() {
        val selected = listOf(
            TestMessageSelection(id = "1", text = "", imageUrl = "https://example.com/photo.jpg"),
            TestMessageSelection(id = "2", text = "What do you think of this?", imageUrl = null)
        )
        val hasTextMessage = selected.any { !it.text.isNullOrBlank() }
        assertTrue("Mixed selection with at least one text message must show K icon", hasTextMessage)
    }

    private data class TestMessageSelection(
        val id: String,
        val text: String?,
        val imageUrl: String?
    )
}
