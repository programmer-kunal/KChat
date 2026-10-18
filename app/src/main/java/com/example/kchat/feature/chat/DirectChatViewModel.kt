package com.example.kchat.feature.chat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.kchat.crypto.EncryptedMediaManager
import com.example.kchat.data.cache.LocalDataCache
import com.example.kchat.model.Message
import com.example.kchat.model.MessagePreviewCalculator
import com.example.kchat.model.User
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.database.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class DirectChatViewModel @Inject constructor(
    @ApplicationContext val context: Context
) : ViewModel() {

    private val db = Firebase.database
    private var saveDirectChatsJob: Job? = null
    private val friendshipStartMap = mutableMapOf<String, Long>()

    private val _users = MutableStateFlow<List<DirectChatItem>>(emptyList())
    val users: StateFlow<List<DirectChatItem>> = _users.asStateFlow()

    private val _allUsers = MutableStateFlow<List<User>>(emptyList())
    val allUsers: StateFlow<List<User>> = _allUsers.asStateFlow()

    private val _sentRequests = MutableStateFlow<List<String>>(emptyList())
    val sentRequests: StateFlow<List<String>> = _sentRequests.asStateFlow()

    private val _friendIds = MutableStateFlow<List<String>>(emptyList())
    val friendIds: StateFlow<List<String>> = _friendIds.asStateFlow()

    private val _hiddenChats = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val hiddenChats: StateFlow<Map<String, Boolean>> = _hiddenChats.asStateFlow()

    private val _incomingRequests = MutableStateFlow<Map<String, String>>(emptyMap())
    val incomingRequests: StateFlow<Map<String, String>> = _incomingRequests.asStateFlow()

    private val _deletedMessages = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val deletedMessages: StateFlow<Map<String, Set<String>>> = _deletedMessages.asStateFlow()

    // Persistent Firebase Database listener references for explicit cleanup
    private var deletedMessagesRef: DatabaseReference? = null
    private var deletedMessagesListener: ValueEventListener? = null

    private var chatHiddenRef: DatabaseReference? = null
    private var chatHiddenListener: ValueEventListener? = null

    private var incomingRequestsRef: DatabaseReference? = null
    private var incomingRequestsListener: ValueEventListener? = null

    private var sentRequestsRef: DatabaseReference? = null
    private var sentRequestsListener: ValueEventListener? = null

    private var usersRef: DatabaseReference? = null
    private var usersListener: ValueEventListener? = null

    private var friendsRef: DatabaseReference? = null
    private var friendsListener: ValueEventListener? = null

    // Tracked per-chat message listeners: chatId -> ValueEventListener
    private val messageListeners = mutableMapOf<String, ValueEventListener>()
    private val messageQueries = mutableMapOf<String, com.google.firebase.database.Query>()

    // Internal tracking state
    private var activeUid: String? = null
    private val directChatItemsMap = mutableMapOf<String, DirectChatItem>()
    private val friendChatIdMap = mutableMapOf<String, String>() // friendUid -> chatId
    private val friendProfiles = mutableMapOf<String, Pair<String, String?>>() // friendUid -> Pair(name, imageUrl)
    private val latestMessageSnapshots = mutableMapOf<String, DataSnapshot>() // chatId -> DataSnapshot
    private val inFlightFriends = mutableSetOf<String>()
    private val deletedMessagesMap = mutableMapOf<String, Set<String>>()

    init {
        startListeners()
    }

    private fun updateUsersList() {
        val sorted = directChatItemsMap.values.sortedByDescending { it.lastTime }
        _users.value = sorted
        activeUid?.let { uid ->
            saveDirectChatsJob?.cancel()
            saveDirectChatsJob = viewModelScope.launch {
                delay(500L)
                LocalDataCache.saveDirectChats(uid, sorted)
            }
        }
    }

    fun startListeners() {
        val currentUid = Firebase.auth.currentUser?.uid ?: return
        if (activeUid == currentUid) {
            return
        }

        // If user identity changed, clean up previous listeners before attaching new ones
        if (activeUid != null) {
            cleanupListeners()
        }

        activeUid = currentUid

        // Restore cached direct chats immediately for instant cold-start display
        viewModelScope.launch {
            val cached = LocalDataCache.loadDirectChats(currentUid)
            if (cached.isNotEmpty() && _users.value.isEmpty()) {
                cached.forEach { directChatItemsMap[it.uid] = it }
                _users.value = cached
            }
        }

        listenForDeletedMessages(currentUid)
        listenForChatHidden(currentUid)
        listenForFriends(currentUid)
        listenForIncomingRequests(currentUid)
        listenForUsers(currentUid)
        listenForSentRequests(currentUid)
    }

    private fun getChatId(uid1: String, uid2: String): String {
        return if (uid1 < uid2) "${uid1}_$uid2" else "${uid2}_$uid1"
    }

    // 1. deleted_messages/{currentUid}
    private fun listenForDeletedMessages(uid: String) {
        val ref = db.reference.child("deleted_messages").child(uid)
        deletedMessagesRef = ref

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val map = mutableMapOf<String, Set<String>>()
                snapshot.children.forEach { chatSnap ->
                    val chatId = chatSnap.key ?: return@forEach
                    val set = mutableSetOf<String>()
                    chatSnap.children.forEach { msgSnap ->
                        msgSnap.key?.let { set.add(it) }
                    }
                    map[chatId] = set
                }
                deletedMessagesMap.clear()
                deletedMessagesMap.putAll(map)
                _deletedMessages.value = map

                // Re-evaluate any chats that have cached message snapshots
                viewModelScope.launch(Dispatchers.IO) {
                    var changed = false
                    for ((friendUid, chatId) in friendChatIdMap) {
                        val snap = latestMessageSnapshots[chatId] ?: continue
                        val profile = friendProfiles[friendUid] ?: continue
                        val item = processMessageSnapshot(friendUid, chatId, profile.first, profile.second, snap)
                        directChatItemsMap[friendUid] = item
                        changed = true
                    }
                    if (changed) {
                        withContext(Dispatchers.Main) {
                            updateUsersList()
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        deletedMessagesListener = listener
        ref.addValueEventListener(listener)
    }

    // 2. chat_hidden/{currentUid}
    private fun listenForChatHidden(uid: String) {
        val ref = db.reference.child("chat_hidden").child(uid)
        chatHiddenRef = ref

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val map = mutableMapOf<String, Boolean>()
                snapshot.children.forEach {
                    val chatId = it.key ?: return@forEach
                    val isHidden = it.getValue(Boolean::class.java) ?: false
                    map[chatId] = isHidden
                }
                _hiddenChats.value = map
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        chatHiddenListener = listener
        ref.addValueEventListener(listener)
    }

    // 3. friends/{currentUid} and nested message listeners
    // Step 4B-2: Deduplicated friends listener; Step 4B-4: Reconciled friend & message listeners
    private fun listenForFriends(uid: String) {
        val ref = db.reference.child("friends").child(uid)
        ref.keepSynced(true)
        friendsRef = ref

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val currentFriendIds = mutableSetOf<String>()
                snapshot.children.forEach { child ->
                    val fUid = child.key ?: return@forEach
                    currentFriendIds.add(fUid)
                    val startTime: Long = when (val v = child.value) {
                        is Number -> v.toLong()
                        else -> 0L
                    }
                    friendshipStartMap[fUid] = startTime
                }
                _friendIds.value = currentFriendIds.toList()

                // Step 4B-4 Rule 3: Immediately detach message listener for removed friends
                val removedFriendIds = friendChatIdMap.keys - currentFriendIds
                for (removedUid in removedFriendIds) {
                    friendshipStartMap.remove(removedUid)
                    val chatId = friendChatIdMap.remove(removedUid) ?: getChatId(uid, removedUid)
                    com.google.firebase.messaging.FirebaseMessaging.getInstance().unsubscribeFromTopic("direct_$chatId")
                    val msgListener = messageListeners.remove(chatId)
                    if (msgListener != null) {
                        db.reference.child("messages").child(chatId).removeEventListener(msgListener)
                    }
                    latestMessageSnapshots.remove(chatId)
                    friendProfiles.remove(removedUid)
                    directChatItemsMap.remove(removedUid)
                    inFlightFriends.remove(removedUid)
                }
                if (removedFriendIds.isNotEmpty()) {
                    updateUsersList()
                }

                // Step 4B-4 Rules 1 & 2: Reuse existing listeners, attach exactly one for new friends
                for (friendUid in currentFriendIds) {
                    val chatId = getChatId(uid, friendUid)
                    friendChatIdMap[friendUid] = chatId
                    com.google.firebase.messaging.FirebaseMessaging.getInstance().subscribeToTopic("direct_$chatId")

                    if (messageListeners.containsKey(chatId) || inFlightFriends.contains(friendUid)) {
                        continue
                    }

                    inFlightFriends.add(friendUid)
                    db.reference.child("users").child(friendUid)
                        .addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(userSnap: DataSnapshot) {
                                inFlightFriends.remove(friendUid)
                                if (!friendChatIdMap.containsKey(friendUid)) {
                                    return
                                }

                                val name = userSnap.child("name").getValue(String::class.java) ?: return
                                val imageUrl = userSnap.child("imageUrl").getValue(String::class.java)
                                friendProfiles[friendUid] = Pair(name, imageUrl)

                                if (!directChatItemsMap.containsKey(friendUid)) {
                                    viewModelScope.launch(Dispatchers.IO) {
                                        val existingSnap = latestMessageSnapshots[chatId]
                                        val item = if (existingSnap != null) {
                                            processMessageSnapshot(friendUid, chatId, name, imageUrl, existingSnap)
                                        } else {
                                            DirectChatItem(
                                                uid = friendUid,
                                                name = name,
                                                imageUrl = imageUrl,
                                                lastMessage = null,
                                                lastTime = 0L,
                                                unreadCount = 0
                                            )
                                        }
                                        withContext(Dispatchers.Main) {
                                            directChatItemsMap[friendUid] = item
                                            updateUsersList()
                                        }
                                    }
                                }

                                attachMessageListener(friendUid, chatId, name, imageUrl)
                            }

                            override fun onCancelled(error: DatabaseError) {
                                inFlightFriends.remove(friendUid)
                            }
                        })
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }

        friendsListener = listener
        ref.addValueEventListener(listener)
    }

    private fun attachMessageListener(
        friendUid: String,
        chatId: String,
        name: String,
        imageUrl: String?
    ) {
        if (messageListeners.containsKey(chatId)) {
            return
        }

        val query = db.reference.child("messages").child(chatId).limitToLast(50)
        val msgListener = object : ValueEventListener {
            override fun onDataChange(msgSnap: DataSnapshot) {
                latestMessageSnapshots[chatId] = msgSnap
                viewModelScope.launch(Dispatchers.IO) {
                    val currentProfile = friendProfiles[friendUid]
                    val friendName = currentProfile?.first ?: name
                    val friendImage = currentProfile?.second ?: imageUrl
                    val item = processMessageSnapshot(friendUid, chatId, friendName, friendImage, msgSnap)
                    withContext(Dispatchers.Main) {
                        directChatItemsMap[friendUid] = item
                        updateUsersList()
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }

        messageQueries[chatId] = query
        messageListeners[chatId] = msgListener
        query.addValueEventListener(msgListener)
    }

    private suspend fun processMessageSnapshot(
        friendUid: String,
        chatId: String,
        name: String,
        imageUrl: String?,
        msgSnap: DataSnapshot
    ): DirectChatItem {
        val uid = activeUid ?: ""
        val boundary = friendshipStartMap[friendUid] ?: 0L
        val messages = msgSnap.children.mapNotNull { child ->
            val msg = child.getValue(Message::class.java) ?: return@mapNotNull null
            if (boundary > 0L && msg.createdAt < boundary) {
                return@mapNotNull null
            }
            if (uid.isNotEmpty() && msg.senderId.isNotEmpty() && msg.senderId != uid && msg.deliveredBy?.get(uid) != true) {
                child.ref.child("deliveredBy").child(uid).setValue(true)
                child.ref.child("deliveredAt").child(uid).setValue(System.currentTimeMillis())
            }
            val key = child.key
            if (msg.id.isEmpty() && key != null) {
                msg.copy(id = key)
            } else {
                msg
            }
        }

        val decryptedMessages = messages.map { msg ->
            if (msg.textEncrypted == true) {
                EncryptedMediaManager.decryptTextMessage(context, chatId, msg)
            } else {
                msg
            }
        }

        val summary = MessagePreviewCalculator.calculate(
            messages = decryptedMessages,
            currentUserId = uid,
            deletedMessageIds = deletedMessagesMap[chatId] ?: emptySet(),
            photoLabel = "Photo"
        )

        return DirectChatItem(
            uid = friendUid,
            name = name,
            imageUrl = imageUrl,
            lastMessage = summary.lastMessage,
            lastTime = summary.lastTime ?: 0L,
            unreadCount = summary.unreadCount
        )
    }

    // 4. incoming friend_requests/{currentUid}
    private fun listenForIncomingRequests(uid: String) {
        val ref = db.reference.child("friend_requests").child(uid)
        incomingRequestsRef = ref

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val temp = mutableMapOf<String, String>()
                snapshot.children.forEach {
                    val senderUid = it.key ?: return@forEach
                    val senderName = it.getValue(String::class.java) ?: ""
                    temp[senderUid] = senderName
                }
                _incomingRequests.value = temp
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        incomingRequestsListener = listener
        ref.addValueEventListener(listener)
    }

    // 5. users root listener
    private fun listenForUsers(uid: String) {
        val ref = db.reference.child("users")
        usersRef = ref

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val tempList = mutableListOf<User>()
                snapshot.children.forEach {
                    val userUid = it.key ?: return@forEach
                    if (userUid == uid) return@forEach

                    val name = it.child("name").getValue(String::class.java) ?: return@forEach
                    val email = it.child("email").getValue(String::class.java) ?: ""
                    val imageUrl = it.child("imageUrl").getValue(String::class.java)
                    val about = it.child("about").getValue(String::class.java)

                    tempList.add(
                        User(
                            uid = userUid,
                            name = name,
                            email = email,
                            profileImage = imageUrl,
                            about = about
                        )
                    )
                }
                _allUsers.value = tempList
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        usersListener = listener
        ref.addValueEventListener(listener)
    }

    // 6. friend_requests root listener (sent requests)
    private fun listenForSentRequests(uid: String) {
        val ref = db.reference.child("friend_requests")
        sentRequestsRef = ref

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val temp = mutableListOf<String>()
                snapshot.children.forEach { receiverSnap ->
                    receiverSnap.children.forEach { senderSnap ->
                        if (senderSnap.key == uid) {
                            temp.add(receiverSnap.key ?: "")
                        }
                    }
                }
                _sentRequests.value = temp
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        sentRequestsListener = listener
        ref.addValueEventListener(listener)
    }

    // ================= FRIEND REQUEST ACTIONS =================

    fun sendFriendRequest(receiverUid: String, receiverName: String) {
        val currentUser = Firebase.auth.currentUser ?: return
        val senderUid = currentUser.uid

        db.reference
            .child("friend_requests")
            .child(receiverUid)
            .child(senderUid)
            .setValue(currentUser.displayName ?: "")
    }

    fun acceptFriendRequest(senderUid: String) {
        val currentUid = Firebase.auth.currentUser?.uid ?: return
        val now = System.currentTimeMillis()

        db.reference.child("friends")
            .child(currentUid)
            .child(senderUid)
            .setValue(now)

        db.reference.child("friends")
            .child(senderUid)
            .child(currentUid)
            .setValue(now)

        db.reference.child("friend_requests")
            .child(currentUid)
            .child(senderUid)
            .removeValue()
    }

    fun rejectFriendRequest(senderUid: String) {
        val currentUid = Firebase.auth.currentUser?.uid ?: return

        db.reference
            .child("friend_requests")
            .child(currentUid)
            .child(senderUid)
            .removeValue()
    }

    // ================= LIFECYCLE CLEANUP =================

    private fun cleanupListeners() {
        // 1. deleted_messages
        deletedMessagesRef?.let { ref ->
            deletedMessagesListener?.let { listener ->
                ref.removeEventListener(listener)
            }
        }
        deletedMessagesListener = null
        deletedMessagesRef = null

        // 2. chat_hidden
        chatHiddenRef?.let { ref ->
            chatHiddenListener?.let { listener ->
                ref.removeEventListener(listener)
            }
        }
        chatHiddenListener = null
        chatHiddenRef = null

        // 3. friends primary listener
        friendsRef?.let { ref ->
            friendsListener?.let { listener ->
                ref.removeEventListener(listener)
            }
        }
        friendsListener = null
        friendsRef = null

        // 4. incoming friend_requests
        incomingRequestsRef?.let { ref ->
            incomingRequestsListener?.let { listener ->
                ref.removeEventListener(listener)
            }
        }
        incomingRequestsListener = null
        incomingRequestsRef = null

        // 5. users root
        usersRef?.let { ref ->
            usersListener?.let { listener ->
                ref.removeEventListener(listener)
            }
        }
        usersListener = null
        usersRef = null

        // 6. sent friend_requests root
        sentRequestsRef?.let { ref ->
            sentRequestsListener?.let { listener ->
                ref.removeEventListener(listener)
            }
        }
        sentRequestsListener = null
        sentRequestsRef = null

        // 7. all nested messages/{chatId} listeners
        for ((chatId, listener) in messageListeners) {
            val query = messageQueries[chatId]
            query?.removeEventListener(listener) ?: db.reference.child("messages").child(chatId).removeEventListener(listener)
        }
        messageListeners.clear()
        messageQueries.clear()

        // 8. in-memory tracking state
        directChatItemsMap.clear()
        friendChatIdMap.clear()
        friendProfiles.clear()
        latestMessageSnapshots.clear()
        inFlightFriends.clear()
        deletedMessagesMap.clear()
        activeUid = null
    }

    override fun onCleared() {
        super.onCleared()
        cleanupListeners()
    }
}
