package com.marcos.chatapplication.domain.contracts

import android.net.Uri
import com.marcos.chatapplication.domain.model.Conversation
import com.marcos.chatapplication.domain.model.ConversationWithDetails
import com.marcos.chatapplication.domain.model.Message
import com.marcos.chatapplication.domain.model.User
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun getUserConversations(): Flow<List<ConversationWithDetails>>
    fun getMessages(conversationId: String): Flow<List<Message>>
    suspend fun sendMessage(conversationId: String, text: String): Result<Unit>
    suspend fun createOrGetConversation(targetUserId: String): Result<String>
    fun getConversationDetails(conversationId: String): Flow<ConversationWithDetails?>
    suspend fun markMessagesAsRead(conversationId: String)

    suspend fun createGroupConversation(
        groupName: String,
        participantIds: List<String>
    ): Result<String>

    suspend fun pinMessage(conversationId: String, message: Message?): Result<Unit>

    suspend fun sendImageMessage(
        conversationId: String,
        imageUri: Uri,
        caption: String? = null
    ): Result<Unit>
    suspend fun updateGroupName(conversationId: String, newName: String): Result<Unit>
    suspend fun addParticipantsToGroup(conversationId: String, userIds: List<String>): Result<Unit>
    suspend fun removeParticipantFromGroup(conversationId: String, userId: String): Result<Unit>
    suspend fun getGroupDetails(conversationId: String): Result<Conversation>

    suspend fun getAvailableUsers(): Result<List<User>>

    suspend fun updateGroupImage(conversationId: String, imageUri: Uri): Result<String>
    suspend fun updateGroupImage(conversationId: String, imageUrl: String): Result<Unit>

    suspend fun sendVideoMessage(
        conversationId: String,
        videoUri: Uri,
        caption: String? = null,
        videoThumbnailBytes: ByteArray?, // Bytes da miniatura extraída
        videoDuration: Long?             // Duração do vídeo em milissegundos
    ): Result<Unit>
}