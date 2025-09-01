package com.marcos.chatapplication.data.repository

import android.net.Uri // ADICIONADO
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.ktx.storage
import com.google.firebase.storage.storage
import com.marcos.chatapplication.domain.contracts.ChatRepository
import com.marcos.chatapplication.domain.model.Conversation
import com.marcos.chatapplication.domain.model.ConversationWithDetails
import com.marcos.chatapplication.domain.model.Message
import com.marcos.chatapplication.domain.model.MessageStatus
import com.marcos.chatapplication.domain.model.MessageType
import com.marcos.chatapplication.domain.model.User
import com.marcos.chatapplication.utils.NotificationUtils
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ChatRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth,
    private val storage: FirebaseStorage, // ADICIONADO
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
                    lastMessageTimestamp = null // Firestore usará @ServerTimestamp na conversão para o objeto Conversation se configurado no modelo
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

        val allParticipantIds = (participantIds + currentUserId).distinct()

        return try {
            // Firestore lida com ServerTimestamp diretamente no map
            val newConversationData = mapOf(
                "participants" to allParticipantIds,
                "isGroup" to true,
                "groupName" to groupName,
                "lastMessage" to "Grupo criado.",
                "lastMessageTimestamp" to FieldValue.serverTimestamp() // Correto para escrita direta de Map
            )
            val newDocRef = firestore.collection("conversations").add(newConversationData).await()
            Result.success(newDocRef.id)
        } catch (e: Exception) {
            Log.e("ChatRepoImpl", "Erro ao criar grupo", e)
            Result.failure(e)
        }
    }

    override suspend fun markMessagesAsRead(conversationId: String) {
        val currentUserId = firebaseAuth.currentUser?.uid ?: return

        try {
            val conversationDoc = firestore.collection("conversations")
                .document(conversationId).get().await()
            // Se Conversation não for usado para ler diretamente aqui, obtenha 'participants' como List<String>?
            val participantsRaw = conversationDoc.get("participants") as? List<*>
            val participants = participantsRaw?.mapNotNull { it as? String }

            val otherUserId = participants?.firstOrNull { it != currentUserId }

            if (otherUserId == null) {
                Log.d("ChatRepoImpl", "Outro utilizador não encontrado ou 'participants' não é uma lista de Strings, não é possível marcar como lido. Participants: $participants")
                return
            }

            val messagesToUpdateQuery = firestore.collection("conversations")
                .document(conversationId)
                .collection("messages")
                .whereEqualTo("senderId", otherUserId)
                .whereIn("status", listOf(MessageStatus.SENT, MessageStatus.DELIVERED))
                .get()
                .await()

            if (messagesToUpdateQuery.isEmpty) {
                Log.d("ChatRepoImpl", "Nenhuma mensagem nova para marcar como lida.")
                return
            }

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
                Log.w("ChatRepoImpl", "getMessages listener error", error)
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val messages = snapshot.toObjects(Message::class.java).mapIndexed { index, message ->
                    message.copy(id = snapshot.documents[index].id) // Garante que o ID do documento seja usado
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
                update(
                    conversationRef, mapOf(
                        "lastMessage" to text,
                        "lastMessageTimestamp" to FieldValue.serverTimestamp()
                    )
                )
            }.commit().await()

            val otherParticipantId = NotificationUtils.getOtherParticipantId(conversationId)
            otherParticipantId?.let {
                NotificationUtils.sendMessageNotification(it, text, conversationId)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("ChatRepoImpl", "Error sending message", e)
            Result.failure(e)
        }
    }

    override suspend fun sendImageMessage(conversationId: String, imageUri: Uri, caption: String?): Result<Unit> {
        val currentUserId = firebaseAuth.currentUser?.uid
        if (currentUserId == null) {
            return Result.failure(Exception("User not logged in."))
        }

        return try {
            val imageFileName = "${UUID.randomUUID()}.jpg"
            val storagePath = "images/$conversationId/$imageFileName"
            val storageRef = storage.reference.child(storagePath)

            Log.d("ChatRepoImpl", "Iniciando upload para: $storagePath")
            storageRef.putFile(imageUri).await()
            Log.d("ChatRepoImpl", "Upload completo.")

            val downloadUrl = storageRef.downloadUrl.await().toString()
            Log.d("ChatRepoImpl", "URL de Download: $downloadUrl")

            val conversationRef = firestore.collection("conversations").document(conversationId)
            val messageRef = conversationRef.collection("messages").document()

            val originalFileName = imageUri.lastPathSegment ?: imageFileName


            val messageText = if (!caption.isNullOrBlank()) {
                caption
            } else {
                MessageType.IMAGE_LABEL
            }

            val newMessage = Message(
                id = messageRef.id,
                senderId = currentUserId,
                type = MessageType.IMAGE,
                mediaUrl = downloadUrl,
                text = messageText, // <<< USA O messageText DETERMINADO ACIMA
                timestamp = null, // Firestore irá preencher com ServerTimestamp
                status = MessageStatus.SENT,
                fileName = originalFileName
                // fileSize pode ser adicionado no futuro se necessário
            )
            Log.d("ChatRepoImpl", "Objeto Message criado: $newMessage")


            // >>> CORREÇÃO AQUI: Determinar o lastMessage para a conversa <<<
            val lastMessageTextForConversation = if (!caption.isNullOrBlank()) {
                "${MessageType.IMAGE_LABEL}${if (caption.isNotEmpty()) ": $caption" else ""}" // Ex: "📷 Imagem: Legenda" ou "📷 Imagem"
            } else {
                // Se não houver legenda, use o nome do arquivo ou um label genérico.
                "${MessageType.IMAGE_LABEL}: ${originalFileName.take(20)}${if(originalFileName.length > 20) "..." else ""}" // Nome do arquivo truncado
            }


            firestore.batch().apply {
                set(messageRef, newMessage)
                update(
                    conversationRef, mapOf(
                        "lastMessage" to lastMessageTextForConversation,
                        "lastMessageTimestamp" to FieldValue.serverTimestamp()
                    )
                )
            }.commit().await()
            Log.d("ChatRepoImpl", "Batch commit para mensagem de imagem e lastMessage bem-sucedido.")

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("ChatRepoImpl", "Erro ao enviar mensagem de imagem", e)
            Result.failure(e)
        }
    }



    override fun getUserConversations(): Flow<List<ConversationWithDetails>> {
        val currentUserId = firebaseAuth.currentUser?.uid ?: return flowOf(emptyList())

        val conversationsFlow: Flow<List<Conversation>> = callbackFlow {
            val query = firestore.collection("conversations")
                .whereArrayContains("participants", currentUserId)
                .orderBy("lastMessageTimestamp", Query.Direction.DESCENDING)

            val listener = query.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("ChatRepoImpl", "getUserConversations listener error", error)
                    close(error); return@addSnapshotListener
                }
                if (snapshot != null) {
                    val convos = snapshot.documents.mapNotNull { doc ->
                        documentToConversation(doc)
                    }
                    trySend(convos)
                } else {
                    trySend(emptyList()) // Envia lista vazia se snapshot for nulo e não houver erro
                }
            }
            awaitClose { listener.remove() }
        }

        return conversationsFlow.flatMapLatest { conversations ->
            if (conversations.isEmpty()) {
                return@flatMapLatest flowOf(emptyList())
            }

            val detailedFlows: List<Flow<ConversationWithDetails>> = conversations.map { conversation ->
                if (conversation.isGroup) {
                    flowOf(ConversationWithDetails(conversation, null)) // Para grupos, otherParticipant é null
                } else {
                    val otherId = conversation.participants.firstOrNull { it != currentUserId } ?: ""
                    getUserFlow(otherId).map { user ->
                        ConversationWithDetails(conversation, user)
                    }
                }
            }
            // kotlinx.coroutines.flow.combine
            if (detailedFlows.isEmpty()) flowOf(emptyList()) else kotlinx.coroutines.flow.combine(detailedFlows) { detailsArray ->
                detailsArray.toList()
            }
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
                groupName = doc.getString("groupName"),
                pinnedMessageId = doc.getString("pinnedMessageId"), // Pode ser null
                pinnedMessageText = doc.getString("pinnedMessageText"), // Pode ser null
                pinnedMessageSenderId = doc.getString("pinnedMessageSenderId") // Adicionado ao modelo, pode ser null
            )
        } catch (e: Exception) {
            Log.e("ChatRepoImpl", "Erro ao mapear o documento da conversa: ${doc.id}", e)
            null
        }
    }

    override fun getConversationDetails(conversationId: String): Flow<ConversationWithDetails?> {
        val currentUserId = firebaseAuth.currentUser?.uid ?: ""

        return firestore.collection("conversations").document(conversationId)
            .snapshots()
            .map { snapshot ->
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
                        flowOf(ConversationWithDetails(conversation, null))
                    } else {
                        val otherId = conversation.participants.firstOrNull { it != currentUserId } ?: ""
                        if (otherId.isBlank()) { // Lida com o caso de não encontrar otherId
                            flowOf(ConversationWithDetails(conversation, null)) // ou algum estado de erro/vazio
                        } else {
                            getUserFlow(otherId).map { user ->
                                ConversationWithDetails(conversation, user)
                            }
                        }
                    }
                }
            }
    }

    private fun getUserFlow(userId: String): Flow<User?> = callbackFlow {
        if (userId.isBlank()) {
            trySend(null) // Envia null imediatamente
            close()       // Fecha o flow
            return@callbackFlow
        }
        val docRef = firestore.collection("users").document(userId)
        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w("ChatRepoImpl", "getUserFlow listener error for userId: $userId", error)
                trySend(null) // Em caso de erro, envia null ou propaga o erro
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.toObject(User::class.java))
        }
        awaitClose { listener.remove() }
    }

    override suspend fun pinMessage(conversationId: String, message: Message?): Result<Unit> {
        return try {
            val conversationRef = firestore.collection("conversations").document(conversationId)

            if (message == null) {
                Log.d("PinMessageDebug", "A tentar desafixar mensagem na conversa $conversationId")
                conversationRef.update(
                    mapOf(
                        "pinnedMessageId" to null,
                        "pinnedMessageText" to null,
                        "pinnedMessageSenderId" to null // Limpa também o senderId
                    )
                ).await()
                Log.d("PinMessageDebug", "Mensagem desafixada com SUCESSO.")
            } else {
                Log.d("PinMessageDebug", "A tentar fixar a mensagem '${message.text}' na conversa $conversationId")
                conversationRef.update(
                    mapOf(
                        "pinnedMessageId" to message.id,
                        "pinnedMessageText" to message.text, // ou message.mediaUrl se for imagem
                        "pinnedMessageSenderId" to message.senderId
                    )
                ).await()
                Log.d("PinMessageDebug", "Mensagem fixada com SUCESSO.")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("PinMessageDebug", "ERRO ao tentar fixar/desafixar mensagem", e)
            Result.failure(e)
        }
    }

    override suspend fun updateGroupName(conversationId: String, newName: String): Result<Unit> {
        return try {
            val currentUserId = firebaseAuth.currentUser?.uid ?:
            return Result.failure(Exception("Usuário não autenticado"))

            // Verifica se a conversa é um grupo e se o usuário é participante
            val conversationDoc = firestore.collection("conversations").document(conversationId).get().await()
            val participants = conversationDoc.get("participants") as? List<String> ?: emptyList()

            if (!participants.contains(currentUserId)) {
                return Result.failure(Exception("Usuário não é participante do grupo"))
            }

            if (conversationDoc.getBoolean("isGroup") != true) {
                return Result.failure(Exception("Esta conversa não é um grupo"))
            }

            firestore.collection("conversations").document(conversationId)
                .update("groupName", newName)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("ChatRepositoryImpl", "Erro ao atualizar nome do grupo", e)
            Result.failure(e)
        }
    }

    override suspend fun addParticipantsToGroup(conversationId: String, userIds: List<String>): Result<Unit> {
        return try {
            val currentUserId = firebaseAuth.currentUser?.uid ?:
            return Result.failure(Exception("Usuário não autenticado"))

            val conversationDoc = firestore.collection("conversations").document(conversationId).get().await()
            val currentParticipants = conversationDoc.get("participants") as? List<String> ?: emptyList()

            if (!currentParticipants.contains(currentUserId)) {
                return Result.failure(Exception("Usuário não é participante do grupo"))
            }

            if (conversationDoc.getBoolean("isGroup") != true) {
                return Result.failure(Exception("Esta conversa não é um grupo"))
            }

            val newParticipants = (currentParticipants + userIds).distinct()

            firestore.collection("conversations").document(conversationId)
                .update("participants", newParticipants)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("ChatRepositoryImpl", "Erro ao adicionar participantes", e)
            Result.failure(e)
        }
    }

    override suspend fun removeParticipantFromGroup(conversationId: String, userId: String): Result<Unit> {
        return try {
            val currentUserId = firebaseAuth.currentUser?.uid ?:
            return Result.failure(Exception("Usuário não autenticado"))

            val conversationDoc = firestore.collection("conversations").document(conversationId).get().await()
            val currentParticipants = conversationDoc.get("participants") as? List<String> ?: emptyList()

            if (!currentParticipants.contains(currentUserId)) {
                return Result.failure(Exception("Usuário não é participante do grupo"))
            }

            if (conversationDoc.getBoolean("isGroup") != true) {
                return Result.failure(Exception("Esta conversa não é um grupo"))
            }

            // Não permite remover a si mesmo (opcional, depende da sua regra de negócio)
            if (userId == currentUserId) {
                return Result.failure(Exception("Não é possível remover a si mesmo do grupo"))
            }

            val newParticipants = currentParticipants.filter { it != userId }

            // Se não houver mais participantes, deleta o grupo (opcional)
            if (newParticipants.isEmpty()) {
                firestore.collection("conversations").document(conversationId).delete().await()
                return Result.success(Unit)
            }

            firestore.collection("conversations").document(conversationId)
                .update("participants", newParticipants)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("ChatRepositoryImpl", "Erro ao remover participante", e)
            Result.failure(e)
        }
    }

    override suspend fun getGroupDetails(conversationId: String): Result<Conversation> {
        return try {
            val doc = firestore.collection("conversations").document(conversationId).get().await()
            val conversation = doc.toObject(Conversation::class.java)?.copy(id = doc.id)
                ?: return Result.failure(Exception("Conversa não encontrada"))

            Result.success(conversation)
        } catch (e: Exception) {
            Log.e("ChatRepositoryImpl", "Erro ao buscar detalhes do grupo", e)
            Result.failure(e)
        }
    }

    override suspend fun getAvailableUsers(): Result<List<User>> {
        return try {
            val currentUserId = firebaseAuth.currentUser?.uid
            if (currentUserId == null) {
                return Result.failure(Exception("Usuário não autenticado"))
            }

            val snapshot = firestore.collection("users").get().await()

            val users = snapshot.documents.mapNotNull { document ->
                document.toObject(User::class.java)?.copy(uid = document.id)
            }.filter { it.uid != currentUserId }

            Result.success(users)
        } catch (e: Exception) {
            Log.e("ChatRepositoryImpl", "Erro ao buscar usuários disponíveis", e)
            Result.failure(e)
        }
    }

    // Na implementação do ChatRepository
    override suspend fun updateGroupImage(conversationId: String, imageUri: Uri): Result<String> {
        return try {
            // Upload da imagem para o storage
            val imageUrl = uploadGroupImage(conversationId, imageUri)
            // Atualizar a conversa com a nova URL
            updateGroupImage(conversationId, imageUrl)
            Result.success(imageUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateGroupImage(conversationId: String, imageUrl: String): Result<Unit> {
        return try {
            firestore.collection("conversations")
                .document(conversationId)
                .update("groupImageUrl", imageUrl)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadGroupImage(conversationId: String, imageUri: Uri): String {
        val storageRef = Firebase.storage.reference
        // Use o caminho "images/" que está permitido nas suas regras
        val imageRef = storageRef.child("images/group_$conversationId/${System.currentTimeMillis()}.jpg")

        return imageRef.putFile(imageUri)
            .await()
            .storage
            .downloadUrl
            .await()
            .toString()
    }
}

