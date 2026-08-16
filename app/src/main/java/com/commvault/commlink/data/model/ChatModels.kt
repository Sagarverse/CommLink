package com.commvault.commlink.data.model

data class ChatMessage(
    val id: String,
    val senderName: String,
    val receiverName: String,
    val text: String,
    val timestamp: Long,
    var status: MessageStatus,
    val isFromMe: Boolean,
    val messageType: MessageType = MessageType.TEXT,
    val fileName: String? = null,
    val fileUri: String? = null,
    val fileSize: Long? = null
)

enum class MessageType {
    TEXT,
    FILE
}

enum class MessageStatus {
    SENDING,
    SENT,
    DELIVERED,
    FAILED
}
