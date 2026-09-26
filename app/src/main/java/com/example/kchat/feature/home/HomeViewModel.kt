package com.example.kchat.feature.home

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kchat.SupabaseStorageUtils
import com.example.kchat.model.Message
import com.example.kchat.model.MessagePreviewCalculator
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.database.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor() : ViewModel() {

    companion object {
        const val WORLD_CHAT_ID = "worldchat"
        const val WORLD_CHAT_NAME = "World Chat"
    }

    private val db = Firebase.database

    private val _channels = MutableStateFlow<List<HomeChannel>>(emptyList())
    val channels = _channels.asStateFlow()

    private val _deletedChannelIds = MutableStateFlow<Set<String>>(emptySet())
    val deletedChannelIds = _deletedChannelIds.asStateFlow()

    // Listener & channel tracking to prevent duplicate registrations and resource leaks
    private var deletedGroupsRef: DatabaseReference? = null
    private var deletedGroupsListener: ValueEventListener? = null

    private var deletedMessagesRef: DatabaseReference? = null
    private var deletedMessagesListener: ValueEventListener? = null
    private val deletedMessagesMap = mutableMapOf<String, MutableSet<String>>()
    private val cachedChannelMessages = mutableMapOf<String, List<Message>>()

    private var channelImagesRef: DatabaseReference? = null
    private var channelImagesListener: ValueEventListener? = null
    private val channelImagesMap = mutableMapOf<String, String>()

    private var channelsRef: DatabaseReference? = null
    private var channelsListener: ValueEventListener? = null

    private val messageListeners = mutableMapOf<String, ValueEventListener>()
    private val membershipListeners = mutableMapOf<String, ValueEventListener>()
    private val isMemberMap = mutableMapOf<String, Boolean>()
    private val channelMap = mutableMapOf<String, HomeChannel>()

    init {
        ensureWorldChat()
        listenForDeletedChannels()
        listenForDeletedMessages()
        listenForChannelImages()
        listenForChannels()
    }

    private fun emitSortedChannels() {
        val currentUid = Firebase.auth.currentUser?.uid
        val visibleChannels = channelMap.values.filter { channel ->
            if (channel.id == WORLD_CHAT_ID) {
                true
            } else {
                val isDeleted = _deletedChannelIds.value.contains(channel.id)
                val isCreator = currentUid != null && channel.creatorUid == currentUid
                val isMember = isMemberMap[channel.id] == true || isCreator
                !isDeleted && isMember
            }
        }
        _channels.value = visibleChannels.sortedWith(
            compareByDescending<HomeChannel> { it.id == WORLD_CHAT_ID }
                .thenByDescending { it.lastTime ?: 0L }
        )
    }

    /**
     * Ensures the global World Chat group exists idempotently in Firebase Realtime Database
     * and guarantees that the current user is added as a member.
     */
    private fun ensureWorldChat() {
        val currentUid = Firebase.auth.currentUser?.uid ?: return
        val worldChatChannelRef = db.getReference("channel").child(WORLD_CHAT_ID)
        worldChatChannelRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    worldChatChannelRef.setValue(WORLD_CHAT_NAME)
                    db.getReference("channel_creator").child(WORLD_CHAT_ID).setValue(currentUid)
                }
                // Automatically add current user as member of World Chat
                db.getReference("channels")
                    .child(WORLD_CHAT_ID)
                    .child("users")
                    .child(currentUid)
                    .setValue(true)
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun listenForDeletedChannels() {
        val currentUid = Firebase.auth.currentUser?.uid ?: return
        val ref = db.getReference("deleted_groups").child(currentUid)
        deletedGroupsRef = ref

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val set = mutableSetOf<String>()
                snapshot.children.forEach {
                    if (it.getValue(Boolean::class.java) == true) {
                        val key = it.key ?: ""
                        if (key != WORLD_CHAT_ID) {
                            set.add(key)
                        }
                    }
                }
                _deletedChannelIds.value = set
                reconcileDeletedChannels(set)
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        deletedGroupsListener = listener
        ref.addValueEventListener(listener)
    }

    private fun reconcileDeletedChannels(deletedSet: Set<String>) {
        val currentUid = Firebase.auth.currentUser?.uid ?: return
        for ((channelId, channel) in channelMap) {
            val isDeleted = deletedSet.contains(channelId) && channelId != WORLD_CHAT_ID
            val isCreator = channel.creatorUid == currentUid
            val isMember = (isMemberMap[channelId] == true) || (channelId == WORLD_CHAT_ID)

            if (isDeleted) {
                detachMessageListener(channelId)
            } else if (isCreator || isMember) {
                attachMessageListenerIfActive(channelId, channel.name, channel.creatorUid)
            }
        }
        emitSortedChannels()
    }

    private fun listenForDeletedMessages() {
        val currentUid = Firebase.auth.currentUser?.uid ?: return
        val ref = db.getReference("deleted_messages").child(currentUid)
        deletedMessagesRef = ref

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                deletedMessagesMap.clear()
                snapshot.children.forEach { channelSnap ->
                    val channelId = channelSnap.key ?: return@forEach
                    val set = mutableSetOf<String>()
                    channelSnap.children.forEach { msgSnap ->
                        if (msgSnap.getValue(Boolean::class.java) == true || msgSnap.exists()) {
                            msgSnap.key?.let { set.add(it) }
                        }
                    }
                    deletedMessagesMap[channelId] = set
                }
                recalculateAllPreviews()
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        deletedMessagesListener = listener
        ref.addValueEventListener(listener)
    }

    private fun recalculateAllPreviews() {
        val currentUid = Firebase.auth.currentUser?.uid ?: return
        var anyChanged = false

        for ((channelId, channel) in channelMap) {
            val messages = cachedChannelMessages[channelId] ?: emptyList()
            val deletedIds = deletedMessagesMap[channelId] ?: emptySet()

            val summary = MessagePreviewCalculator.calculate(
                messages = messages,
                currentUserId = currentUid,
                deletedMessageIds = deletedIds,
                photoLabel = "📷 Photo"
            )

            if (channel.lastMessage != summary.lastMessage ||
                channel.lastTime != summary.lastTime ||
                channel.lastSenderName != summary.lastSenderName ||
                channel.unreadCount != summary.unreadCount
            ) {
                channelMap[channelId] = channel.copy(
                    lastMessage = summary.lastMessage,
                    lastTime = summary.lastTime,
                    lastSenderName = summary.lastSenderName,
                    unreadCount = summary.unreadCount
                )
                anyChanged = true
            }
        }

        if (anyChanged) {
            emitSortedChannels()
        }
    }

    private fun listenForChannelImages() {
        val ref = db.getReference("channel_image")
        channelImagesRef = ref

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                channelImagesMap.clear()
                snapshot.children.forEach { child ->
                    val cId = child.key ?: return@forEach
                    val url = child.getValue(String::class.java)
                    if (!url.isNullOrEmpty()) {
                        channelImagesMap[cId] = url
                    }
                }
                var anyChanged = false
                for ((id, channel) in channelMap) {
                    val currentImg = channelImagesMap[id]
                    if (channel.imageUrl != currentImg) {
                        channelMap[id] = channel.copy(imageUrl = currentImg)
                        anyChanged = true
                    }
                }
                if (anyChanged) {
                    emitSortedChannels()
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        channelImagesListener = listener
        ref.addValueEventListener(listener)
    }

    private fun listenForChannels() {
        val ref = db.getReference("channel")
        channelsRef = ref

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val currentChannelIds = snapshot.children.mapNotNull { it.key }.toSet()
                val currentUid = Firebase.auth.currentUser?.uid

                // Remove listeners and cached data for channels that are no longer present
                val removedChannelIds = channelMap.keys - currentChannelIds
                for (removedId in removedChannelIds) {
                    detachMessageListener(removedId)
                    if (currentUid != null) {
                        membershipListeners.remove(removedId)?.let { memListener ->
                            db.getReference("channels").child(removedId).child("users").child(currentUid).removeEventListener(memListener)
                        }
                    }
                    isMemberMap.remove(removedId)
                    channelMap.remove(removedId)
                }
                if (removedChannelIds.isNotEmpty()) {
                    emitSortedChannels()
                }

                snapshot.children.forEach { data ->
                    val channelId = data.key ?: return@forEach
                    val channelName = data.getValue(String::class.java) ?: ""

                    // Fetch creator UID from channel_creator node
                    db.getReference("channel_creator").child(channelId)
                        .addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(creatorSnapshot: DataSnapshot) {
                                val creatorUid = creatorSnapshot.getValue(String::class.java)
                                setupOrUpdateChannel(channelId, channelName, creatorUid)
                            }
                            override fun onCancelled(error: DatabaseError) {
                                setupOrUpdateChannel(channelId, channelName, null)
                            }
                        })
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        channelsListener = listener
        ref.addValueEventListener(listener)
    }

    private fun setupOrUpdateChannel(
        channelId: String,
        channelName: String,
        creatorUid: String?
    ) {
        val imgUrl = channelImagesMap[channelId]
        val existing = channelMap[channelId]
        if (existing == null) {
            channelMap[channelId] = HomeChannel(
                id = channelId,
                name = channelName,
                lastMessage = null,
                lastTime = null,
                lastSenderName = null,
                unreadCount = 0,
                creatorUid = creatorUid,
                imageUrl = imgUrl
            )
            emitSortedChannels()
        } else if (existing.name != channelName || existing.creatorUid != creatorUid || existing.imageUrl != imgUrl) {
            channelMap[channelId] = existing.copy(name = channelName, creatorUid = creatorUid, imageUrl = imgUrl)
            emitSortedChannels()
        }

        checkMembershipAndSyncListener(channelId, channelName, creatorUid)
    }

    private fun checkMembershipAndSyncListener(
        channelId: String,
        channelName: String,
        creatorUid: String?
    ) {
        val currentUid = Firebase.auth.currentUser?.uid ?: return

        if (channelId == WORLD_CHAT_ID) {
            attachMessageListenerIfActive(channelId, channelName, creatorUid)
            return
        }

        val isDeleted = _deletedChannelIds.value.contains(channelId)
        if (isDeleted) {
            detachMessageListener(channelId)
            emitSortedChannels()
            return
        }

        val isCreator = creatorUid != null && creatorUid == currentUid
        if (isCreator) {
            isMemberMap[channelId] = true
            attachMessageListenerIfActive(channelId, channelName, creatorUid)
            emitSortedChannels()
            return
        }

        // For non-creators, attach membership listener to channels/{channelId}/users/{currentUid}
        if (!membershipListeners.containsKey(channelId)) {
            val userMembershipRef = db.getReference("channels")
                .child(channelId)
                .child("users")
                .child(currentUid)

            val membershipListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val isMember = snapshot.exists()
                    isMemberMap[channelId] = isMember

                    val stillDeleted = _deletedChannelIds.value.contains(channelId)
                    val activeCreator = channelMap[channelId]?.creatorUid ?: creatorUid
                    val isNowCreator = activeCreator != null && activeCreator == currentUid

                    if ((isMember || isNowCreator) && !stillDeleted) {
                        attachMessageListenerIfActive(
                            channelId = channelId,
                            channelName = channelMap[channelId]?.name ?: channelName,
                            creatorUid = activeCreator
                        )
                    } else {
                        detachMessageListener(channelId)
                    }
                    emitSortedChannels()
                }

                override fun onCancelled(error: DatabaseError) {
                    isMemberMap[channelId] = false
                    detachMessageListener(channelId)
                    emitSortedChannels()
                }
            }
            membershipListeners[channelId] = membershipListener
            userMembershipRef.addValueEventListener(membershipListener)
        } else {
            if (isMemberMap[channelId] == true && !isDeleted) {
                attachMessageListenerIfActive(channelId, channelName, creatorUid)
            }
            emitSortedChannels()
        }
    }

    private fun attachMessageListenerIfActive(
        channelId: String,
        channelName: String,
        creatorUid: String?
    ) {
        if (messageListeners.containsKey(channelId)) return
        listenForLastMessage(channelId, channelName, creatorUid)
    }

    private fun detachMessageListener(channelId: String) {
        val listener = messageListeners.remove(channelId)
        if (listener != null) {
            db.getReference("messages").child(channelId).removeEventListener(listener)
        }
    }

    private fun listenForLastMessage(
        channelId: String,
        channelName: String,
        creatorUid: String?
    ) {
        val currentUid = Firebase.auth.currentUser?.uid ?: return

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val deletedIds = deletedMessagesMap[channelId] ?: emptySet()
                val messages = snapshot.children.mapNotNull { child ->
                    val msg = child.getValue(Message::class.java) ?: return@mapNotNull null
                    if (channelId != WORLD_CHAT_ID && currentUid.isNotEmpty() && msg.senderId.isNotEmpty() && msg.senderId != currentUid && msg.deliveredBy?.get(currentUid) != true) {
                        child.ref.child("deliveredBy").child(currentUid).setValue(true)
                    }
                    val key = child.key
                    val effectiveId = if (msg.id.isNotEmpty()) msg.id else (key ?: "")
                    if ((effectiveId.isNotEmpty() && deletedIds.contains(effectiveId)) ||
                        (key != null && deletedIds.contains(key))) {
                        deletedMessagesMap.getOrPut(channelId) { mutableSetOf() }.apply {
                            if (effectiveId.isNotEmpty()) add(effectiveId)
                            if (key != null) add(key)
                        }
                    }
                    if (msg.id.isEmpty() && key != null) {
                        msg.copy(id = key)
                    } else {
                        msg
                    }
                }

                cachedChannelMessages[channelId] = messages
                val updatedDeletedIds = deletedMessagesMap[channelId] ?: emptySet()

                val summary = MessagePreviewCalculator.calculate(
                    messages = messages,
                    currentUserId = currentUid,
                    deletedMessageIds = updatedDeletedIds,
                    photoLabel = "📷 Photo"
                )

                val currentCreatorUid = channelMap[channelId]?.creatorUid ?: creatorUid
                val currentName = channelMap[channelId]?.name ?: channelName

                val updatedChannel = HomeChannel(
                    id = channelId,
                    name = currentName,
                    lastMessage = summary.lastMessage,
                    lastTime = summary.lastTime,
                    lastSenderName = summary.lastSenderName,
                    unreadCount = summary.unreadCount,
                    creatorUid = currentCreatorUid,
                    imageUrl = channelImagesMap[channelId] ?: channelMap[channelId]?.imageUrl
                )

                channelMap[channelId] = updatedChannel
                emitSortedChannels()
            }

            override fun onCancelled(error: DatabaseError) {
                messageListeners.remove(channelId)
            }
        }

        messageListeners[channelId] = listener
        db.getReference("messages").child(channelId).addValueEventListener(listener)
    }

    /**
     * Creates a custom group with the creator and selected friends only.
     * Optionally uploads and sets a group photo if provided.
     */
    fun createGroup(
        name: String,
        memberFriendUids: List<String>,
        imageUri: Uri? = null,
        context: Context? = null
    ) {
        val currentUser = Firebase.auth.currentUser ?: return
        val key = db.getReference("channel").push().key ?: return

        val updates = mutableMapOf<String, Any>()
        updates["/channel/$key"] = name
        updates["/channel_creator/$key"] = currentUser.uid
        updates["/channels/$key/users/${currentUser.uid}"] = true
        memberFriendUids.forEach { friendUid ->
            if (friendUid.isNotBlank()) {
                updates["/channels/$key/users/$friendUid"] = true
            }
        }

        if (imageUri != null && context != null) {
            viewModelScope.launch {
                try {
                    val storageUtils = SupabaseStorageUtils(context)
                    val publicUrl = storageUtils.uploadImage(imageUri)
                    if (!publicUrl.isNullOrEmpty()) {
                        updates["/channel_image/$key"] = publicUrl
                    }
                } catch (e: Exception) {
                    // Gracefully proceed
                }
                db.reference.updateChildren(updates)
            }
        } else {
            db.reference.updateChildren(updates)
        }
    }

    fun addChannel(name: String) {
        createGroup(name, emptyList())
    }

    /**
     * Deletes a group safely without destroying historical message data.
     * World Chat is strictly protected and cannot be deleted.
     */
    fun deleteGroup(channelId: String) {
        if (channelId == WORLD_CHAT_ID) return
        val currentUid = Firebase.auth.currentUser?.uid ?: return

        detachMessageListener(channelId)
        membershipListeners.remove(channelId)?.let {
            db.getReference("channels").child(channelId).child("users").child(currentUid).removeEventListener(it)
        }
        isMemberMap.remove(channelId)
        channelMap.remove(channelId)
        emitSortedChannels()

        // Non-destructive deletion: remove channel nodes only, preserving messages/$channelId
        db.getReference("channel").child(channelId).removeValue()
        db.getReference("channel_creator").child(channelId).removeValue()
        db.getReference("channels").child(channelId).removeValue()
    }

    /**
     * Leaves a group by marking it in deleted_groups.
     * World Chat cannot be left.
     */
    fun leaveGroup(channelId: String) {
        if (channelId == WORLD_CHAT_ID) return
        val currentUid = Firebase.auth.currentUser?.uid ?: return
        db.getReference("deleted_groups").child(currentUid).child(channelId).setValue(true)
    }

    fun rejoinGroup(channelId: String) {
        val currentUid = Firebase.auth.currentUser?.uid ?: return
        db.getReference("deleted_groups").child(currentUid).child(channelId).removeValue()
    }

    /**
     * Clears all messages for the current user in a group channel (including World Chat).
     * Immediately resets local channel preview state and marks all cached messages as deleted
     * to eliminate race conditions, then writes the deletion flags atomically to Firebase.
     */
    fun clearChat(channelId: String) {
        val currentUid = Firebase.auth.currentUser?.uid ?: return

        // 1. Immediately reset preview locally so the UI updates instantly without any flicker/delay
        val existing = channelMap[channelId]
        if (existing != null) {
            channelMap[channelId] = existing.copy(
                lastMessage = null,
                lastTime = null,
                lastSenderName = null,
                unreadCount = 0
            )
            emitSortedChannels()
        }

        // 2. Mark all currently cached messages as deleted locally immediately
        // This guarantees any in-flight message event or calculation treats them as deleted
        val currentCached = cachedChannelMessages[channelId] ?: emptyList()
        val set = deletedMessagesMap.getOrPut(channelId) { mutableSetOf() }
        currentCached.forEach { msg ->
            if (msg.id.isNotEmpty()) set.add(msg.id)
        }

        // 3. Atomically update Firebase deleted_messages for both message ID and push key
        db.reference.child("messages").child(channelId).get().addOnSuccessListener { snapshot ->
            val updates = mutableMapOf<String, Any>()
            snapshot.children.forEach { child ->
                val key = child.key
                val msgId = child.child("id").getValue(String::class.java)
                if (!key.isNullOrEmpty()) {
                    updates["/deleted_messages/$currentUid/$channelId/$key"] = true
                    set.add(key)
                }
                if (!msgId.isNullOrEmpty()) {
                    updates["/deleted_messages/$currentUid/$channelId/$msgId"] = true
                    set.add(msgId)
                }
            }
            if (updates.isNotEmpty()) {
                db.reference.updateChildren(updates).addOnSuccessListener {
                    recalculateAllPreviews()
                }
            } else {
                recalculateAllPreviews()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        val currentUid = Firebase.auth.currentUser?.uid

        // 1. Remove deleted_messages listener
        deletedMessagesRef?.let { ref ->
            deletedMessagesListener?.let { listener ->
                ref.removeEventListener(listener)
            }
        }
        deletedMessagesListener = null
        deletedMessagesRef = null
        deletedMessagesMap.clear()
        cachedChannelMessages.clear()

        // 2. Remove deleted_groups listener
        deletedGroupsRef?.let { ref ->
            deletedGroupsListener?.let { listener ->
                ref.removeEventListener(listener)
            }
        }
        deletedGroupsListener = null
        deletedGroupsRef = null

        // 2. Remove channel listener
        channelsRef?.let { ref ->
            channelsListener?.let { listener ->
                ref.removeEventListener(listener)
            }
        }
        channelsListener = null
        channelsRef = null

        // Remove channel images listener
        channelImagesRef?.let { ref ->
            channelImagesListener?.let { listener ->
                ref.removeEventListener(listener)
            }
        }
        channelImagesListener = null
        channelImagesRef = null
        channelImagesMap.clear()

        // 3. Remove all tracked messages/{channelId} listeners
        for ((channelId, listener) in messageListeners) {
            db.getReference("messages").child(channelId).removeEventListener(listener)
        }
        messageListeners.clear()

        // 4. Remove all membership listeners
        if (currentUid != null) {
            for ((channelId, listener) in membershipListeners) {
                db.getReference("channels").child(channelId).child("users").child(currentUid).removeEventListener(listener)
            }
        }
        membershipListeners.clear()
        isMemberMap.clear()
        channelMap.clear()
    }
}
