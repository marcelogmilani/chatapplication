package com.marcos.chatapplication.data.repository

import android.util.Log
import androidx.compose.ui.text.style.TextDecoration.Companion.combine
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import com.marcos.chatapplication.domain.contracts.ChatRepository
import com.marcos.chatapplication.domain.model.Conversation
import com.marcos.chatapplication.domain.model.ConversationWithDetails
import com.marcos.chatapplication.domain.model.Message
import com.marcos.chatapplication.domain.model.MessageStatus
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

    override suspend fun createGroupConversation(groupName: String, participantIds: List<String>): Result<String> {
        val currentUserId = firebaseAuth.currentUser?.uid
        if (currentUserId == null) {
            return Result.failure(Exception("Utilizador não autenticado."))
        }

        // Garante que o criador do grupo também está na lista de participantes
        val allParticipantIds = (participantIds + currentUserId).distinct()

        return try {
            val newConversation = mapOf(
                "participants" to allParticipantIds,
                "isGroup" to true,
                "groupName" to groupName,
                "lastMessage" to "Grupo criado.",
                "lastMessageTimestamp" to FieldValue.serverTimestamp()
            )

            val newDocRef = firestore.collection("conversations").add(newConversation).await()
            Result.success(newDocRef.id)
        } catch (e: Exception) {
            Log.e("ChatRepoImpl", "Erro ao criar grupo", e)
            Result.failure(e)
        }
    }

    override suspend fun markMessagesAsRead(conversationId: String) {
        val currentUserId = firebaseAuth.currentUser?.uid ?: return

        try {
            // Passo 1: Obter a conversa para descobrir quem é o outro participante
            val conversationDoc = firestore.collection("conversations")
                .document(conversationId).get().await()
            val participants = conversationDoc.toObject(Conversation::class.java)?.participants
            val otherUserId = participants?.firstOrNull { it != currentUserId }

            // Se não houver outro utilizador, não há nada a fazer
            if (otherUserId == null) {
                Log.d("ChatRepoImpl", "Outro utilizador não encontrado, não é possível marcar como lido.")
                return
            }

            // Passo 2: Buscar apenas as mensagens enviadas pelo OUTRO utilizador
            // que ainda não foram lidas.
            val messagesToUpdateQuery = firestore.collection("conversations")
                .document(conversationId)
                .collection("messages")
                // Condição 1: Mensagens do outro utilizador
                .whereEqualTo("senderId", otherUserId)
                // Condição 2: Status diferente de "Lido" (usando 'in' para mais flexibilidade)
                .whereIn("status", listOf(MessageStatus.SENT, MessageStatus.DELIVERED))
                .get()
                .await()

            if (messagesToUpdateQuery.isEmpty) {
                Log.d("ChatRepoImpl", "Nenhuma mensagem nova para marcar como lida.")
                return // Nada a atualizar
            }

            // Cria uma operação em lote (batch) para atualizar todos os documentos de uma só vez
            val batch = firestore.batch()
            for (document in messagesToUpdateQuery.documents) {
                batch.update(document.reference, "status", MessageStatus.READ)
            }
            batch.commit().await()
            Log.d("ChatRepoImpl", "${messagesToUpdateQuery.size()} mensagens marcadas como lidas.")

        } catch (e: Exception) {
            Log.e("ChatRepoImpl", "Erro ao marcar mensagens como lidas", e)
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

    // Dentro de ChatRepositoryImpl.kt

    override fun getUserConversations(): Flow<List<ConversationWithDetails>> {
        val currentUserId = firebaseAuth.currentUser?.uid ?: return flowOf(emptyList())

        val conversationsFlow: Flow<List<Conversation>> = callbackFlow {
            val query = firestore.collection("conversations")
                .whereArrayContains("participants", currentUserId)
                .orderBy("lastMessageTimestamp", Query.Direction.DESCENDING)

            val listener = query.addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                if (snapshot != null) {
                    // ALTERAÇÃO AQUI: Usar o mapeamento manual
                    val convos = snapshot.documents.mapNotNull { doc ->
                        documentToConversation(doc) // Usando a nossa nova função
                    }
                    trySend(convos)
                }
            }
            awaitClose { listener.remove() }
        }

        // 2. Transforma o fluxo de conversas no fluxo de detalhes
        return conversationsFlow.flatMapLatest { conversations ->
            if (conversations.isEmpty()) {
                return@flatMapLatest flowOf(emptyList())
            }

            // Mapeia cada conversa para um fluxo de detalhes
            val detailedFlows = conversations.map { conversation ->
                if (conversation.isGroup) {
                    // SE FOR UM GRUPO, o otherParticipant é nulo.
                    // A UI irá usar o conversation.groupName.
                    flowOf(ConversationWithDetails(conversation, null))
                } else {
                    // SE FOR INDIVIDUAL, encontra o outro utilizador e busca os seus detalhes.
                    val otherId = conversation.participants.firstOrNull { it != currentUserId } ?: ""
                    getUserFlow(otherId).map { user ->
                        ConversationWithDetails(conversation, user)
                    }
                }
            }
            // Combina todos os fluxos num só
            combine(detailedFlows) { details -> details.toList() }
        }
    }

    private fun documentToConversation(doc: DocumentSnapshot): Conversation? {
        return try {
            Conversation(
                id = doc.id,
                participants = doc.get("participants") as? List<String> ?: emptyList(),
                lastMessage = doc.getString("lastMessage"),
                lastMessageTimestamp = doc.getDate("lastMessageTimestamp"),
                isGroup = doc.getBoolean("isGroup") ?: false,
                groupName = doc.getString("groupName")
            )
        } catch (e: Exception) {
            Log.e("ChatRepoImpl", "Erro ao mapear o documento da conversa manualmente", e)
            null
        }
    }

    override fun getConversationDetails(conversationId: String): Flow<ConversationWithDetails?> {
        val currentUserId = firebaseAuth.currentUser?.uid ?: ""

        return firestore.collection("conversations").document(conversationId)
            .snapshots()
            .map { snapshot ->
                // ALTERAÇÃO AQUI: Substituir .toObject() pelo nosso mapeamento manual
                if (snapshot.exists()) {
                    documentToConversation(snapshot)
                } else {
                    null
                }
            }
            .flatMapLatest { conversation ->
                if (conversation == null) {
                    flowOf(null)
                } else {
                    if (conversation.isGroup) {
                        // Para grupos, o otherParticipant é nulo
                        flowOf(ConversationWithDetails(conversation, null))
                    } else {
                        // Para chats individuais, busca os detalhes do outro utilizador
                        val otherId = conversation.participants.firstOrNull { it != currentUserId } ?: ""
                        getUserFlow(otherId).map { user ->
                            ConversationWithDetails(conversation, user)
                        }
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