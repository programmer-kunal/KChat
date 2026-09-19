package com.example.kchat.feature.chat
data class DirectChatItem(
    val uid: String,
    val name: String,
    val imageUrl: String?,
    val lastMessage: String? = null,
    val lastTime: Long = 0L,
    val unreadCount: Int = 0
)

