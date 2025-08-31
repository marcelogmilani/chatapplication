package com.marcos.chatapplication.domain.contracts

import com.marcos.chatapplication.domain.model.ConversationWithDetails
import com.marcos.chatapplication.domain.model.Message
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun getUserConversations(): Flow<List<ConversationWithDetails>>
    fun getMessages(conversationId: String): Flow<List<Message>>
    suspend fun sendMessage(conversationId: String, text: String): Result<Unit>
    suspend fun createOrGetConversation(targetUserId: String): Result<String>
    fun getConversationDetails(conversationId: String): Flow<ConversationWithDetails?>
    suspend fun markMessagesAsRead(conversationId: String)

    suspend fun createGroupConversation(groupName: String, participantIds: List<String>): Result<String>
    suspend fun pinMessage(conversationId: String, message: Message?): Result<Unit>
}