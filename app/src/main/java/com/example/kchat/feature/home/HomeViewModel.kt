package com.example.kchat.feature.home

import androidx.lifecycle.ViewModel
import com.example.kchat.model.Message
import com.example.kchat.model.MessagePreviewCalculator
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.database.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor() : ViewModel() {

    private val db = Firebase.database

    private val _channels = MutableStateFlow<List<HomeChannel>>(emptyList())
    val channels = _channels.asStateFlow()

    private val _deletedChannelIds = MutableStateFlow<Set<String>>(emptySet())
    val deletedChannelIds = _deletedChannelIds.asStateFlow()

    // Listener & channel tracking to prevent duplicate registrations and resource leaks
    private var deletedGroupsRef: DatabaseReference? = null
    private var deletedGroupsListener: ValueEventListener? = null

    private var channelsRef: DatabaseReference? = null
    private var channelsListener: ValueEventListener? = null

    private val messageListeners = mutableMapOf<String, ValueEventListener>()
    private val membershipListeners = mutableMapOf<String, ValueEventListener>()
    private val isMemberMap = mutableMapOf<String, Boolean>()
    private val channelMap = mutableMapOf<String, HomeChannel>()

    init {
        listenForDeletedChannels()
        listenForChannels()
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
                        set.add(it.key ?: "")
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
            val isDeleted = deletedSet.contains(channelId)
            val isCreator = channel.creatorUid == currentUid
            val isMember = isMemberMap[channelId] == true

            if (isDeleted) {
                detachMessageListener(channelId)
            } else if (isCreator || isMember) {
                attachMessageListenerIfActive(channelId, channel.name, channel.creatorUid)
            }
        }
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
                    _channels.value = channelMap.values.toList().sortedByDescending { it.lastTime ?: 0 }
                }

                snapshot.children.forEach { data ->
                    val channelId = data.key ?: return@forEach
                    val channelName = data.getValue(String::class.java) ?: ""

                    // Subscribe user to FCM topic for group messages to ensure notifications arrive instantly
                    subscribeForNotification(channelId)

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
        val existing = channelMap[channelId]
        if (existing == null) {
            channelMap[channelId] = HomeChannel(
                id = channelId,
                name = channelName,
                lastMessage = null,
                lastTime = null,
                lastSenderName = null,
                unreadCount = 0,
                creatorUid = creatorUid
            )
            _channels.value = channelMap.values.toList().sortedByDescending { it.lastTime ?: 0 }
        } else if (existing.name != channelName || existing.creatorUid != creatorUid) {
            channelMap[channelId] = existing.copy(name = channelName, creatorUid = creatorUid)
            _channels.value = channelMap.values.toList().sortedByDescending { it.lastTime ?: 0 }
        }

        checkMembershipAndSyncListener(channelId, channelName, creatorUid)
    }

    private fun checkMembershipAndSyncListener(
        channelId: String,
        channelName: String,
        creatorUid: String?
    ) {
        val currentUid = Firebase.auth.currentUser?.uid ?: return

        val isDeleted = _deletedChannelIds.value.contains(channelId)
        if (isDeleted) {
            detachMessageListener(channelId)
            return
        }

        val isCreator = creatorUid != null && creatorUid == currentUid
        if (isCreator) {
            attachMessageListenerIfActive(channelId, channelName, creatorUid)
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
                }

                override fun onCancelled(error: DatabaseError) {
                    detachMessageListener(channelId)
                }
            }
            membershipListeners[channelId] = membershipListener
            userMembershipRef.addValueEventListener(membershipListener)
        } else {
            if (isMemberMap[channelId] == true && !isDeleted) {
                attachMessageListenerIfActive(channelId, channelName, creatorUid)
            }
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

    private fun subscribeForNotification(channelId: String) {
        val topic = if (channelId.contains("_")) {
            "direct_$channelId"
        } else {
            "group_$channelId"
        }
        com.google.firebase.messaging.FirebaseMessaging.getInstance()
            .subscribeToTopic(topic)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    android.util.Log.d("HomeViewModel", "Subscribed to FCM topic: $topic")
                } else {
                    android.util.Log.e("HomeViewModel", "Failed to subscribe to FCM topic: $topic", task.exception)
                }
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
                val messages = snapshot.children.mapNotNull { child ->
                    val msg = child.getValue(Message::class.java)
                    val key = child.key
                    if (msg != null && msg.id.isEmpty() && key != null) {
                        msg.copy(id = key)
                    } else {
                        msg
                    }
                }

                val summary = MessagePreviewCalculator.calculate(
                    messages = messages,
                    currentUserId = currentUid,
                    deletedMessageIds = emptySet(),
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
                    creatorUid = currentCreatorUid
                )

                channelMap[channelId] = updatedChannel
                _channels.value = channelMap.values.toList().sortedByDescending { it.lastTime ?: 0 }
            }

            override fun onCancelled(error: DatabaseError) {
                messageListeners.remove(channelId)
            }
        }

        messageListeners[channelId] = listener
        db.getReference("messages").child(channelId).addValueEventListener(listener)
    }

    fun addChannel(name: String) {
        val currentUser = Firebase.auth.currentUser ?: return
        val key = db.getReference("channel").push().key ?: return

        db.getReference("channel")
            .child(key)
            .setValue(name)

        db.getReference("channel_creator")
            .child(key)
            .setValue(currentUser.uid)
    }

    fun deleteGroup(channelId: String) {
        val currentUid = Firebase.auth.currentUser?.uid

        detachMessageListener(channelId)
        if (currentUid != null) {
            membershipListeners.remove(channelId)?.let {
                db.getReference("channels").child(channelId).child("users").child(currentUid).removeEventListener(it)
            }
        }
        isMemberMap.remove(channelId)
        channelMap.remove(channelId)
        _channels.value = channelMap.values.toList().sortedByDescending { it.lastTime ?: 0 }

        db.getReference("messages").child(channelId).removeValue()
        db.getReference("channel").child(channelId).removeValue()
        db.getReference("channel_creator").child(channelId).removeValue()
    }

    fun leaveGroup(channelId: String) {
        val currentUid = Firebase.auth.currentUser?.uid ?: return
        db.getReference("deleted_groups").child(currentUid).child(channelId).setValue(true)
    }

    fun rejoinGroup(channelId: String) {
        val currentUid = Firebase.auth.currentUser?.uid ?: return
        db.getReference("deleted_groups").child(currentUid).child(channelId).removeValue()
    }

    override fun onCleared() {
        super.onCleared()
        val currentUid = Firebase.auth.currentUser?.uid

        // 1. Remove deleted_groups listener
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
