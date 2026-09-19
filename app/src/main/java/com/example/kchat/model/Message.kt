package com.example.kchat.model

data class Message(
    val id:String="",
    val senderId:String="",
    val message:String?="",
    val createdAt:Long= 0L,
    val senderName:String="",
    val senderImage:String? = null,
    val imageUrl:String? = null,
    val profileImageUrl: String? = null,
    val readBy: Map<String, Boolean>? = null,
    val replyToMessageId: String? = null,
    val replyToSenderName: String? = null,
    val replyToMessageText: String? = null,
    val audioUrl: String? = null,
    val audioDurationMs: Long? = null,
    val videoUrl: String? = null,
    val videoDurationMs: Long? = null,
    val fileUrl: String? = null,
    val fileName: String? = null,
    val fileMimeType: String? = null,
    val fileSizeBytes: Long? = null
)
