package com.example.kchat.feature.chat

import com.example.kchat.model.Message
import com.example.kchat.model.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KChatReceiptTest {

    private val currentUid = "user_me"
    private val friendUid = "user_friend"

    // ================= DIRECT CHAT =================

    @Test
    fun directChat_initialState_returnsSENT() {
        val message = Message(
            id = "msg1",
            senderId = currentUid,
            message = "Hello"
        )
        val status = MessageReceiptHelper.getReceiptStatus(
            message = message,
            currentUid = currentUid,
            isDirectChat = true,
            directOtherUid = friendUid
        )
        assertEquals(MessageReceiptStatus.SENT, status)
    }

    @Test
    fun directChat_deliveredByReceiver_returnsDELIVERED() {
        val message = Message(
            id = "msg1",
            senderId = currentUid,
            message = "Hello",
            deliveredBy = mapOf(friendUid to true)
        )
        val status = MessageReceiptHelper.getReceiptStatus(
            message = message,
            currentUid = currentUid,
            isDirectChat = true,
            directOtherUid = friendUid
        )
        assertEquals(MessageReceiptStatus.DELIVERED, status)
    }

    @Test
    fun directChat_readByReceiver_returnsREAD() {
        val message = Message(
            id = "msg1",
            senderId = currentUid,
            message = "Hello",
            deliveredBy = mapOf(friendUid to true),
            readBy = mapOf(friendUid to true)
        )
        val status = MessageReceiptHelper.getReceiptStatus(
            message = message,
            currentUid = currentUid,
            isDirectChat = true,
            directOtherUid = friendUid
        )
        assertEquals(MessageReceiptStatus.READ, status)
    }

    // ================= CUSTOM GROUP CHAT =================

    @Test
    fun groupChat_initialState_returnsSENT() {
        val groupMembers = setOf(currentUid, "member_1", "member_2", "member_3")
        val message = Message(
            id = "msg1",
            senderId = currentUid,
            message = "Hello group"
        )
        val status = MessageReceiptHelper.getReceiptStatus(
            message = message,
            currentUid = currentUid,
            isDirectChat = false,
            isWorldChat = false,
            groupMemberUids = groupMembers
        )
        assertEquals(MessageReceiptStatus.SENT, status)
    }

    @Test
    fun groupChat_partialDelivery_returnsSENT() {
        val groupMembers = setOf(currentUid, "member_1", "member_2", "member_3")
        val message = Message(
            id = "msg1",
            senderId = currentUid,
            message = "Hello group",
            deliveredBy = mapOf("member_1" to true, "member_2" to true) // member_3 hasn't received
        )
        val status = MessageReceiptHelper.getReceiptStatus(
            message = message,
            currentUid = currentUid,
            isDirectChat = false,
            isWorldChat = false,
            groupMemberUids = groupMembers
        )
        assertEquals(MessageReceiptStatus.SENT, status)
    }

    @Test
    fun groupChat_allOtherMembersDelivered_returnsDELIVERED() {
        val groupMembers = setOf(currentUid, "member_1", "member_2", "member_3")
        val message = Message(
            id = "msg1",
            senderId = currentUid,
            message = "Hello group",
            deliveredBy = mapOf("member_1" to true, "member_2" to true, "member_3" to true)
        )
        val status = MessageReceiptHelper.getReceiptStatus(
            message = message,
            currentUid = currentUid,
            isDirectChat = false,
            isWorldChat = false,
            groupMemberUids = groupMembers
        )
        assertEquals(MessageReceiptStatus.DELIVERED, status)
    }

    @Test
    fun groupChat_partialRead_returnsDELIVERED() {
        val groupMembers = setOf(currentUid, "member_1", "member_2")
        val message = Message(
            id = "msg1",
            senderId = currentUid,
            message = "Hello group",
            deliveredBy = mapOf("member_1" to true, "member_2" to true),
            readBy = mapOf("member_1" to true) // member_2 has not read yet
        )
        val status = MessageReceiptHelper.getReceiptStatus(
            message = message,
            currentUid = currentUid,
            isDirectChat = false,
            isWorldChat = false,
            groupMemberUids = groupMembers
        )
        assertEquals(MessageReceiptStatus.DELIVERED, status)
    }

    @Test
    fun groupChat_allOtherMembersRead_returnsREAD() {
        val groupMembers = setOf(currentUid, "member_1", "member_2")
        val message = Message(
            id = "msg1",
            senderId = currentUid,
            message = "Hello group",
            deliveredBy = mapOf("member_1" to true, "member_2" to true),
            readBy = mapOf("member_1" to true, "member_2" to true)
        )
        val status = MessageReceiptHelper.getReceiptStatus(
            message = message,
            currentUid = currentUid,
            isDirectChat = false,
            isWorldChat = false,
            groupMemberUids = groupMembers
        )
        assertEquals(MessageReceiptStatus.READ, status)
    }

    @Test
    fun groupChat_senderReadDoesNotSatisfyRequirement() {
        val groupMembers = setOf(currentUid, "member_1")
        val message = Message(
            id = "msg1",
            senderId = currentUid,
            message = "Hello group",
            readBy = mapOf(currentUid to true) // Only sender marked read
        )
        val status = MessageReceiptHelper.getReceiptStatus(
            message = message,
            currentUid = currentUid,
            isDirectChat = false,
            isWorldChat = false,
            groupMemberUids = groupMembers
        )
        assertEquals(MessageReceiptStatus.SENT, status)
    }

    @Test
    fun groupChat_removedMemberDoesNotBlockStatus() {
        // member_3 was removed from group, so groupMemberUids only contains member_1 & member_2
        val activeGroupMembers = setOf(currentUid, "member_1", "member_2")
        val message = Message(
            id = "msg1",
            senderId = currentUid,
            message = "Hello group",
            deliveredBy = mapOf("member_1" to true, "member_2" to true),
            readBy = mapOf("member_1" to true, "member_2" to true)
        )
        val status = MessageReceiptHelper.getReceiptStatus(
            message = message,
            currentUid = currentUid,
            isDirectChat = false,
            isWorldChat = false,
            groupMemberUids = activeGroupMembers
        )
        assertEquals(MessageReceiptStatus.READ, status)
    }

    @Test
    fun groupChat_memberJoinedLaterDoesNotBlockOldMessage() {
        val groupMembers = setOf(currentUid, "member_old", "member_new")
        val message = Message(
            id = "msg1",
            senderId = currentUid,
            message = "Hello old group",
            createdAt = 1000L,
            deliveredBy = mapOf("member_old" to true),
            readBy = mapOf("member_old" to true)
        )
        // member_new joined at 2000L, after message was sent
        val joinTimestamps = mapOf("member_new" to 2000L, "member_old" to 500L)
        val status = MessageReceiptHelper.getReceiptStatus(
            message = message,
            currentUid = currentUid,
            isDirectChat = false,
            isWorldChat = false,
            groupMemberUids = groupMembers,
            memberJoinTimestamps = joinTimestamps
        )
        assertEquals(MessageReceiptStatus.READ, status)
    }

    // ================= WORLD CHAT =================

    @Test
    fun worldChat_storedInDatabase_returnsDELIVERED() {
        val message = Message(
            id = "world_msg_1",
            senderId = currentUid,
            message = "Hello world"
        )
        val status = MessageReceiptHelper.getReceiptStatus(
            message = message,
            currentUid = currentUid,
            isWorldChat = true
        )
        assertEquals(MessageReceiptStatus.DELIVERED, status)
    }

    @Test
    fun worldChat_emptyId_returnsSENT() {
        val message = Message(
            id = "",
            senderId = currentUid,
            message = "Sending..."
        )
        val status = MessageReceiptHelper.getReceiptStatus(
            message = message,
            currentUid = currentUid,
            isWorldChat = true
        )
        assertEquals(MessageReceiptStatus.SENT, status)
    }

    @Test
    fun worldChat_doesNotClaimREAD() {
        val message = Message(
            id = "world_msg_1",
            senderId = currentUid,
            message = "Hello world",
            readBy = mapOf("other_participant" to true)
        )
        val status = MessageReceiptHelper.getReceiptStatus(
            message = message,
            currentUid = currentUid,
            isWorldChat = true
        )
        // World chat stays DELIVERED without fake KKK
        assertEquals(MessageReceiptStatus.DELIVERED, status)
    }

    // ================= MESSAGE INFO DATA BUILDER =================

    @Test
    fun buildMessageInfoData_directChat_buildsAccurateRecipientInfo() {
        val message = Message(
            id = "msg1",
            senderId = currentUid,
            senderName = "Me",
            message = "Test direct",
            createdAt = 123456789L,
            deliveredBy = mapOf(friendUid to true),
            readBy = mapOf(friendUid to true)
        )
        val friendUser = User(
            uid = friendUid,
            name = "Friend",
            email = "friend@example.com"
        )
        val infoData = MessageReceiptHelper.buildMessageInfoData(
            message = message,
            currentUid = currentUid,
            isDirectChat = true,
            isWorldChat = false,
            directRecipientUser = friendUser
        )

        assertEquals(MessageReceiptStatus.READ, infoData.overallStatus)
        assertTrue(infoData.isDirectChat)
        assertFalse(infoData.isWorldChat)
        assertEquals("Friend", infoData.directRecipient?.name)
        assertTrue(infoData.directRecipient?.isDelivered == true)
        assertTrue(infoData.directRecipient?.isRead == true)
    }

    @Test
    fun buildMessageInfoData_groupChat_buildsMemberListCorrectly() {
        val message = Message(
            id = "group_msg1",
            senderId = currentUid,
            senderName = "Me",
            message = "Test group",
            createdAt = 123456789L,
            deliveredBy = mapOf("m1" to true, "m2" to true),
            readBy = mapOf("m1" to true)
        )
        val userM1 = User(uid = "m1", name = "Alice", email = "alice@example.com")
        val userM2 = User(uid = "m2", name = "Bob", email = "bob@example.com")

        val infoData = MessageReceiptHelper.buildMessageInfoData(
            message = message,
            currentUid = currentUid,
            isDirectChat = false,
            isWorldChat = false,
            groupMemberUids = setOf(currentUid, "m1", "m2"),
            groupMemberUsers = listOf(userM1, userM2)
        )

        assertEquals(MessageReceiptStatus.DELIVERED, infoData.overallStatus)
        assertEquals(2, infoData.groupRecipients.size)

        val alice = infoData.groupRecipients.first { it.uid == "m1" }
        assertTrue(alice.isDelivered)
        assertTrue(alice.isRead)

        val bob = infoData.groupRecipients.first { it.uid == "m2" }
        assertTrue(bob.isDelivered)
        assertFalse(bob.isRead)
    }

    // ================= RECEIPT COLORS =================

    @Test
    fun receiptColors_matchSpecification() {
        assertEquals(androidx.compose.ui.graphics.Color(0xFF22C55E), MessageReceiptColors.Delivered)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF38BDF8), MessageReceiptColors.Read)
        assertEquals(MessageReceiptColors.Sent, MessageReceiptColors.getColor(MessageReceiptStatus.SENT))
        assertEquals(MessageReceiptColors.Delivered, MessageReceiptColors.getColor(MessageReceiptStatus.DELIVERED))
        assertEquals(MessageReceiptColors.Read, MessageReceiptColors.getColor(MessageReceiptStatus.READ))
    }

    // ================= SELF CHAT =================

    @Test
    fun selfChat_messageReceipt_immediatelyReturnsREAD() {
        val message = Message(
            id = "self_msg1",
            senderId = currentUid,
            message = "Note to self",
            deliveredBy = mapOf(currentUid to true),
            readBy = mapOf(currentUid to true)
        )
        val status = MessageReceiptHelper.getReceiptStatus(
            message = message,
            currentUid = currentUid,
            isSelfChat = true
        )
        assertEquals(MessageReceiptStatus.READ, status)
    }

    @Test
    fun selfChat_messageReceipt_withoutExplicitReadBy_stillReturnsREAD() {
        val message = Message(
            id = "self_msg2",
            senderId = currentUid,
            message = "Another note"
        )
        val status = MessageReceiptHelper.getReceiptStatus(
            message = message,
            currentUid = currentUid,
            isSelfChat = true
        )
        assertEquals(MessageReceiptStatus.READ, status)
    }

    @Test
    fun selfChat_buildMessageInfoData_returnsREADAndIsSelfChatTrue() {
        val message = Message(
            id = "self_msg3",
            senderId = currentUid,
            senderName = "Kunal",
            message = "Hello myself",
            createdAt = 1700000000000L
        )
        val infoData = MessageReceiptHelper.buildMessageInfoData(
            message = message,
            currentUid = currentUid,
            isDirectChat = false,
            isWorldChat = false,
            isSelfChat = true
        )

        assertEquals(MessageReceiptStatus.READ, infoData.overallStatus)
        assertEquals(true, infoData.isSelfChat)
        assertEquals(false, infoData.isDirectChat)
        assertEquals(false, infoData.isWorldChat)
        assertEquals(null, infoData.directRecipient)
        assertEquals(0, infoData.groupRecipients.size)
        assertEquals("self_msg3", infoData.messageId)
        assertEquals("Hello myself", infoData.messageText)
    }
}

