package com.marcos.chatapplication.domain.model

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Conversation(
    val id: String = "",
    val participants: List<String> = emptyList(),
    val lastMessage: String? = null,
    @ServerTimestamp val lastMessageTimestamp: Date? = null
)

data class ConversationWithDetails(
    val conversation: Conversation,
    val otherParticipant: User?
)

data class Message(
    val id: String = "",
    val senderId: String = "",
    val text: String = "",
    @ServerTimestamp val timestamp: Date? = null
)