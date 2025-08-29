package com.marcos.chatapplication.data.repository

import android.util.Log
import androidx.compose.ui.text.style.TextDecoration.Companion.combine
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import com.marcos.chatapplication.domain.contracts.ChatRepository
import com.marcos.chatapplication.domain.model.Conversation
import com.marcos.chatapplication.domain.model.ConversationWithDetails
import com.marcos.chatapplication.domain.model.Message
import com.marcos.chatapplication.domain.model.User
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class ChatRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth
) : ChatRepository {

    override suspend fun createOrGetConversation(targetUserId: String): Result<String> {
        val currentUserId = firebaseAuth.currentUser?.uid
        if (currentUserId == null) {
            return Result.failure(Exception("Utilizador não autenticado."))
        }

        val participants = listOf(currentUserId, targetUserId).sorted()

        return try {

            val existingConversation = firestore.collection("conversations")
                .whereEqualTo("participants", participants)
                .limit(1)
                .get()
                .await()

            if (!existingConversation.isEmpty) {
                val conversationId = existingConversation.documents.first().id
                Result.success(conversationId)
            } else {

                val newConversation = Conversation(
                    participants = participants,
                    lastMessage = "Nenhuma mensagem ainda.",
                    lastMessageTimestamp = null
                )

                val newDocRef = firestore.collection("conversations").add(newConversation).await()
                Result.success(newDocRef.id)
            }
        } catch (e: Exception) {
            Log.e("ChatRepoImpl", "Erro ao criar ou obter conversa", e)
            Result.failure(e)
        }
    }

    override fun getMessages(conversationId: String): Flow<List<Message>> = callbackFlow {
        val messagesCollection = firestore.collection("conversations")
            .document(conversationId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)

        val listener = messagesCollection.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val messages = snapshot.toObjects(Message::class.java).mapIndexed { index, message ->
                    message.copy(id = snapshot.documents[index].id)
                }
                trySend(messages)
            }
        }

        awaitClose { listener.remove() }
    }

    override suspend fun sendMessage(conversationId: String, text: String): Result<Unit> {
        val currentUserId = firebaseAuth.currentUser?.uid
        if (currentUserId == null) {
            return Result.failure(Exception("User not logged in."))
        }

        return try {
            val conversationRef = firestore.collection("conversations").document(conversationId)
            val messageRef = conversationRef.collection("messages").document()

            val newMessage = Message(
                id = messageRef.id,
                senderId = currentUserId,
                text = text,
                timestamp = null
            )


            firestore.batch().apply {
                set(messageRef, newMessage)

                update(conversationRef, mapOf(
                    "lastMessage" to text,
                    "lastMessageTimestamp" to FieldValue.serverTimestamp()
                ))
            }.commit().await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("ChatRepoImpl", "Error sending message", e)
            Result.failure(e)
        }
    }

    override fun getUserConversations(): Flow<List<ConversationWithDetails>> = callbackFlow<List<Conversation>> {
        val currentUserId = firebaseAuth.currentUser?.uid
        if (currentUserId == null) {
            trySend(emptyList()).isSuccess
            close()
            return@callbackFlow
        }

        val query = firestore.collection("conversations")
            .whereArrayContains("participants", currentUserId)
            .orderBy("lastMessageTimestamp", Query.Direction.DESCENDING)

        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val conversations = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Conversation::class.java)?.copy(id = doc.id)
                }
                trySend(conversations).isSuccess
            }
        }
        awaitClose { listener.remove() }
    }.flatMapLatest { conversations ->
        val currentUserId = firebaseAuth.currentUser?.uid ?: ""
        val userFlows = conversations.map { conversation ->
            val otherParticipantId = conversation.participants.firstOrNull { it != currentUserId } ?: ""
            getUserFlow(otherParticipantId).map { user ->
                ConversationWithDetails(conversation, user)
            }
        }

        if (userFlows.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(userFlows) { details -> details.toList() }
        }
    }

    override fun getConversationDetails(conversationId: String): Flow<ConversationWithDetails?> {
        val currentUserId = firebaseAuth.currentUser?.uid ?: ""

        // 1. Observa o documento da conversa
        return firestore.collection("conversations").document(conversationId)
            .snapshots() // .snapshots() cria um Flow que emite a cada atualização
            .map { snapshot ->
                snapshot.toObject(Conversation::class.java)?.copy(id = snapshot.id)
            }
            .flatMapLatest { conversation ->
                if (conversation == null) {
                    // Se a conversa for nula (ex: eliminada), emite nulo
                    flowOf(null)
                } else {
                    // 2. Encontra o ID do outro participante
                    val otherId = conversation.participants.firstOrNull { it != currentUserId } ?: ""
                    // 3. Observa os detalhes do outro utilizador
                    getUserFlow(otherId).map { user ->
                        // 4. Combina tudo no nosso modelo de detalhes
                        ConversationWithDetails(conversation, user)
                    }
                }
            }
    }

    private fun getUserFlow(userId: String): Flow<User?> = callbackFlow {
        if (userId.isBlank()) {
            trySend(null).isSuccess
            close()
            return@callbackFlow
        }
        val docRef = firestore.collection("users").document(userId)
        val listener = docRef.addSnapshotListener { snapshot, _ ->
            trySend(snapshot?.toObject(User::class.java)).isSuccess
        }
        awaitClose { listener.remove() }
    }
}