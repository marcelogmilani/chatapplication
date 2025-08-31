package com.marcos.chatapplication.domain.model

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

// ... (Conversation e ConversationWithDetails permanecem os mesmos) ...
data class Conversation(
    val id: String = "",
    val participants: List<String> = emptyList(),
    val lastMessage: String? = null,
    @ServerTimestamp val lastMessageTimestamp: Date? = null,
    val pinnedMessageId: String? = null,
    val pinnedMessageText: String? = null,
    val pinnedMessageSenderId: String? = null,
    val isGroup: Boolean = false,
    val groupName: String? = null,
    val localName: String? = null
)

data class ConversationWithDetails(
    val conversation: Conversation,
    val otherParticipant: User?
)

object MessageStatus {
    const val SENT = "SENT"
    const val DELIVERED = "DELIVERED"
    const val READ = "READ"
}

object MessageType {
    const val TEXT = "TEXT"
    const val IMAGE = "IMAGE"
    const val VIDEO = "VIDEO" // Mantendo para futuras implementações
    const val AUDIO = "AUDIO" // Mantendo para futuras implementações
    const val FILE = "FILE"   // Mantendo para futuras implementações
    const val IMAGE_LABEL = "📷 Imagem" // NOVO LABEL ADICIONADO
}

data class Message(
    val id: String = "",
    val senderId: String = "",
    val text: String = "",
    @ServerTimestamp val timestamp: Date? = null,
    val status: String = MessageStatus.SENT,
    val type: String = MessageType.TEXT,
    val mediaUrl: String? = null,
    val fileName: String? = null,
    val fileSize: Long? = null
)