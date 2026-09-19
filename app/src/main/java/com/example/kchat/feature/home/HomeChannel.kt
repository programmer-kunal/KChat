package com.example.kchat.feature.home

data class HomeChannel(
    val id: String,
    val name: String,
    val lastMessage: String? = null,
    val lastTime: Long? = null,
    val lastSenderName: String? = null,
    val unreadCount: Int = 0,
    val creatorUid: String? = null
)
