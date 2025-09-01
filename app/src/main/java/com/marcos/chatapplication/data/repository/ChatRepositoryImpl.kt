package com.marcos.chatapplication.data.repository

import android.net.Uri
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots // Certifique-se que esta é usada ou remova
import com.google.firebase.storage.FirebaseStorage
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ChatRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth,
    private val storage: FirebaseStorage
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

        val allParticipantIds = (participantIds + currentUserId).distinct()

        return try {
            // Firestore lida com ServerTimestamp diretamente no map
            val newConversationData = mapOf(
                "participants" to allParticipantIds,
                "isGroup" to true,
                "groupName" to groupName,
                "lastMessage" to "Grupo criado.",
                "lastMessageTimestamp" to FieldValue.serverTimestamp()
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
                Log.d("ChatRepoImpl", "Outro utilizador não encontrado, não é possível marcar como lido. Participants: $participants")
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
                text = messageText,
                timestamp = null,
                status = MessageStatus.SENT,
                fileName = originalFileName
            )
            Log.d("ChatRepoImpl", "Objeto Message criado: $newMessage")
            val lastMessageTextForConversation = if (!caption.isNullOrBlank()) {
                "${MessageType.IMAGE_LABEL}${if (caption.isNotEmpty()) ": $caption" else ""}"
            } else {
                "${MessageType.IMAGE_LABEL}: ${originalFileName.take(20)}${if(originalFileName.length > 20) "..." else ""}"
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

    // MÉTODO ATUALIZADO
    override suspend fun sendVideoMessage(
        conversationId: String,
        videoUri: Uri,
        caption: String?,
        videoThumbnailBytes: ByteArray?, // NOVO PARÂMETRO
        videoDuration: Long?             // NOVO PARÂMETRO
    ): Result<Unit> {
        val currentUserId = firebaseAuth.currentUser?.uid
        if (currentUserId == null) {
            Log.e("ChatRepoImpl", "User not logged in, cannot send video message.")
            return Result.failure(Exception("User not logged in."))
        }

        return try {
            // 1. Upload do vídeo principal
            val videoFileId = UUID.randomUUID().toString() // Usar como base para nomes
            val originalFileName = videoUri.lastPathSegment ?: videoFileId
            val fileExtension = originalFileName.substringAfterLast('.', "")
            val fullVideoFileNameInStorage = if (fileExtension.isNotEmpty()) "$videoFileId.$fileExtension" else videoFileId
            val videoStoragePath = "videos/$conversationId/$fullVideoFileNameInStorage"
            val videoStorageRef = storage.reference.child(videoStoragePath)

            Log.d("ChatRepoImpl", "Starting video upload to: $videoStoragePath")
            videoStorageRef.putFile(videoUri).await()
            val videoDownloadUrl = videoStorageRef.downloadUrl.await().toString()
            Log.d("ChatRepoImpl", "Video upload complete. URL: $videoDownloadUrl")

            // 2. Upload da miniatura do vídeo (se existir)
            var thumbnailDownloadUrl: String? = null
            if (videoThumbnailBytes != null) {
                val thumbnailFileName = "${videoFileId}_thumb.jpg" // Nome para a miniatura
                val thumbnailStoragePath = "video_thumbnails/$conversationId/$thumbnailFileName" // Pasta separada para miniaturas
                val thumbnailStorageRef = storage.reference.child(thumbnailStoragePath)
                try {
                    Log.d("ChatRepoImpl", "Starting video thumbnail upload to: $thumbnailStoragePath")
                    thumbnailStorageRef.putBytes(videoThumbnailBytes).await()
                    thumbnailDownloadUrl = thumbnailStorageRef.downloadUrl.await().toString()
                    Log.d("ChatRepoImpl", "Video thumbnail upload complete. URL: $thumbnailDownloadUrl")
                } catch (thumbEx: Exception) {
                    Log.e("ChatRepoImpl", "Error uploading video thumbnail for $videoFileId", thumbEx)
                    // O envio do vídeo não falha se a miniatura falhar, thumbnailDownloadUrl permanecerá null
                }
            } else {
                Log.d("ChatRepoImpl", "No thumbnail bytes provided for video $videoFileId, skipping thumbnail upload.")
            }


            // 3. Preparar e salvar a mensagem no Firestore
            val conversationRef = firestore.collection("conversations").document(conversationId)
            val messageRef = conversationRef.collection("messages").document()

            val messageText = if (!caption.isNullOrBlank()) caption else MessageType.VIDEO_LABEL

            val newMessage = Message(
                id = messageRef.id,
                senderId = currentUserId,
                type = MessageType.VIDEO,
                mediaUrl = videoDownloadUrl,        // URL do vídeo principal
                thumbnailUrl = thumbnailDownloadUrl, // URL da miniatura (pode ser null)
                text = messageText,
                timestamp = null,
                status = MessageStatus.SENT,
                fileName = originalFileName,
                duration = videoDuration            // Duração do vídeo (pode ser null)
            )
            Log.d("ChatRepoImpl", "Video Message object created: $newMessage")

            val lastMessageTextForConversation = if (!caption.isNullOrBlank()) {
                "${MessageType.VIDEO_LABEL}: $caption"
            } else {
                "${MessageType.VIDEO_LABEL}: ${originalFileName.take(25)}${if(originalFileName.length > 25) "..." else ""}"
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
            Log.d("ChatRepoImpl", "Batch commit for video message (with metadata) and lastMessage update successful.")

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("ChatRepoImpl", "Error sending video message", e)
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
                    val convos = snapshot.documents.mapNotNull { doc -> documentToConversation(doc) }
                    trySend(convos)
                } else {
                    trySend(emptyList())
                }
            }
            awaitClose { listener.remove() }
        }

        return conversationsFlow.flatMapLatest { conversations ->
            if (conversations.isEmpty()) return@flatMapLatest flowOf(emptyList())
            val detailedFlows: List<Flow<ConversationWithDetails>> = conversations.map { conversation ->
                if (conversation.isGroup) {
                    flowOf(ConversationWithDetails(conversation, null))
                } else {
                    val otherId = conversation.participants.firstOrNull { it != currentUserId } ?: ""
                    getUserFlow(otherId).map { user -> ConversationWithDetails(conversation, user) }
                }
            }
            if (detailedFlows.isEmpty()) flowOf(emptyList()) else combine(detailedFlows) { it.toList() }
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
                pinnedMessageId = doc.getString("pinnedMessageId"),
                pinnedMessageText = doc.getString("pinnedMessageText"),
                pinnedMessageSenderId = doc.getString("pinnedMessageSenderId")
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
            .map { snapshot -> if (snapshot.exists()) documentToConversation(snapshot) else null }
            .flatMapLatest { conversation ->
                if (conversation == null) flowOf(null)
                else {
                    if (conversation.isGroup) flowOf(ConversationWithDetails(conversation, null))
                    else {
                        val otherId = conversation.participants.firstOrNull { it != currentUserId } ?: ""
                        if (otherId.isBlank()) flowOf(ConversationWithDetails(conversation, null))
                        else getUserFlow(otherId).map { user -> ConversationWithDetails(conversation, user) }
                    }
                }
            }
    }

    private fun getUserFlow(userId: String): Flow<User?> = callbackFlow {
        if (userId.isBlank()) {
            trySend(null);
            close(); return@callbackFlow
        }
        val docRef = firestore.collection("users").document(userId)
        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w("ChatRepoImpl", "getUserFlow listener error for userId: $userId", error)
                trySend(null);
                close(error); return@addSnapshotListener
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
                conversationRef.update(mapOf("pinnedMessageId" to null, "pinnedMessageText" to null, "pinnedMessageSenderId" to null)).await()
                Log.d("PinMessageDebug", "Mensagem desafixada com SUCESSO.")
            } else {
                Log.d("PinMessageDebug", "A tentar fixar a mensagem '${message.text}' na conversa $conversationId")
                conversationRef.update(mapOf("pinnedMessageId" to message.id, "pinnedMessageText" to message.text, "pinnedMessageSenderId" to message.senderId)).await()
                Log.d("PinMessageDebug", "Mensagem fixada com SUCESSO.")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("PinMessageDebug", "ERRO ao tentar fixar/desafixar mensagem", e)
            Result.failure(e)
        }
    }
}
