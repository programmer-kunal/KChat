package com.example.kchat.model

data class Channel(
    val id: String,
    val name: String,
    val createdAt: Long= System.currentTimeMillis(),
    val lastMessage: String? = null,
    val lastMessageTime: Long? = null


) {
}