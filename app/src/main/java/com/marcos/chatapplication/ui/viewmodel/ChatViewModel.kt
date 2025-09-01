package com.marcos.chatapplication.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.marcos.chatapplication.domain.contracts.ChatRepository
import com.marcos.chatapplication.domain.contracts.UserRepository
import com.marcos.chatapplication.domain.model.ConversationWithDetails
import com.marcos.chatapplication.domain.model.Message
import com.marcos.chatapplication.domain.model.MessageStatus
import com.marcos.chatapplication.domain.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext // NOVA IMPORTAÇÃO
import java.io.ByteArrayOutputStream // NOVA IMPORTAÇÃO
import javax.inject.Inject

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = true,
    val participantsDetails: Map<String, User> = emptyMap(),
    val conversationDetails: ConversationWithDetails? = null,
    val errorMessage: String? = null,
    val searchQuery: String = "",
    val filteredMessages: List<Message> = emptyList()
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val application: Application,
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val conversationId: String = checkNotNull(savedStateHandle["conversationId"])

    private val _searchQuery = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ChatUiState> = combine(
        chatRepository.getMessages(conversationId).onEach { messages ->
            val hasUnreadMessages = messages.any {
                it.senderId != Firebase.auth.currentUser?.uid && it.status != MessageStatus.READ
            }
            if (hasUnreadMessages) {
                chatRepository.markMessagesAsRead(conversationId)
            }
        },
        chatRepository.getConversationDetails(conversationId),
        chatRepository.getConversationDetails(conversationId).flatMapLatest { details ->
            if (details != null) {
                userRepository.getUsersByIds(details.conversation.participants)
                    .map { result -> result.getOrNull()?.associateBy { it.uid } ?: emptyMap() }
            } else {
                flowOf(emptyMap())
            }
        },
        _searchQuery
    ) { messages, details, participantsMap, query ->
        val filteredMessages = if (query.isBlank()) {
            messages
        } else {
            messages.filter { it.text.contains(query, ignoreCase = true) }
        }
        ChatUiState(
            messages = messages,
            conversationDetails = details,
            participantsDetails = participantsMap,
            isLoading = false,
            searchQuery = query,
            filteredMessages = filteredMessages,
            errorMessage = uiState.value.errorMessage
        )
    }.catch { e ->
        Log.e("ChatViewModel", "Error in combine flow: ${e.message}", e)
        emit(ChatUiState(errorMessage = e.message, isLoading = false))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ChatUiState(isLoading = true)
    )

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            Log.d("ChatViewModel", "Attempting to send message: $text in conversation: $conversationId")
            val result = chatRepository.sendMessage(conversationId, text.trim())
            if (result.isFailure) {
                val error = result.exceptionOrNull()
                Log.e("ChatViewModel", "Failed to send message: ${error?.message}", error)
                Log.e("ChatViewModel", "Error UI Update: Failed to send message: ${error?.message}")
            } else {
                Log.d("ChatViewModel", "Message sent successfully")
            }
        }
    }

    fun sendImageMessage(uri: Uri, convId: String, caption: String? = null) {
        Log.d("ChatViewModel", "sendImageMessage called with URI: $uri, for conversation: $convId, caption: $caption")
        viewModelScope.launch {
            try {
                Log.d("ChatViewModel", "Coroutine for sendImageMessage STARTED for $convId")
                val result = chatRepository.sendImageMessage(convId, uri, caption)
                if (result.isSuccess) {
                    Log.d("ChatViewModel", "Image message sent successfully for conversation $convId")
                } else {
                    val error = result.exceptionOrNull()
                    Log.e("ChatViewModel", "ChatRepository reported failure for $convId: ${error?.message}", error)
                    Log.e("ChatViewModel", "Error UI Update: ChatRepository reported failure for $convId: ${error?.message}")
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "EXCEPTION in sendImageMessage coroutine for $convId: ${e.message}", e)
                Log.e("ChatViewModel", "Error UI Update: EXCEPTION in sendImageMessage coroutine for $convId: ${e.message}")
            } finally {
                Log.d("ChatViewModel", "Coroutine for sendImageMessage FINISHED for $convId")
            }
        }
    }

    private suspend fun getVideoMetadata(videoUri: Uri): Pair<ByteArray?, Long?> {
        return withContext(Dispatchers.IO) { // Executar em background thread
            var thumbnailBytes: ByteArray? = null
            var durationMs: Long? = null
            var retriever: MediaMetadataRetriever? = null // Declarar fora do try para o finally
            try {
                retriever = MediaMetadataRetriever()
                retriever.setDataSource(application.applicationContext, videoUri)

                // Extrair miniatura (thumbnail)
                // Usar getFrameAtTime para pegar um frame. Pega o primeiro frame por simplicidade.
                // Outras opções: MediaMetadataRetriever.OPTION_CLOSEST_SYNC, OPTION_CLOSEST
                val bitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (bitmap != null) {
                    val outputStream = ByteArrayOutputStream()
                    // Comprimir para JPEG com qualidade razoável. Ajuste conforme necessário.
                    // PNG seria lossless mas maior. WEBP poderia ser uma opção moderna.
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
                    thumbnailBytes = outputStream.toByteArray()
                    bitmap.recycle() // Importante reciclar o bitmap
                    Log.d("ChatViewModel", "Video thumbnail extracted, size: ${thumbnailBytes?.size} bytes")
                } else {
                    Log.w("ChatViewModel", "Could not extract thumbnail from video: $videoUri")
                }

                // Extrair duração
                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                if (durationStr != null) {
                    durationMs = durationStr.toLongOrNull()
                    Log.d("ChatViewModel", "Video duration extracted: $durationMs ms")
                } else {
                    Log.w("ChatViewModel", "Could not extract duration from video: $videoUri")
                }

            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error extracting video metadata for $videoUri", e)
                // Retorna null para ambos em caso de erro
            } finally {
                try {
                    retriever?.release() // Liberar o retriever
                } catch (e: Exception) {
                    Log.e("ChatViewModel", "Error releasing MediaMetadataRetriever", e)
                }
            }
            Pair(thumbnailBytes, durationMs)
        }
    }


    fun sendVideoMessage(uri: Uri, convId: String, caption: String? = null) {
        Log.d("ChatViewModel", "sendVideoMessage called with URI: $uri, for conversation: $convId, caption: $caption")
        viewModelScope.launch {
            try {
                Log.d("ChatViewModel", "Coroutine for sendVideoMessage STARTED for $convId. Extracting metadata...")

                // 1. Extrair miniatura e duração
                val (thumbnailBytes, durationMs) = getVideoMetadata(uri)

                if (thumbnailBytes == null) {
                    Log.w("ChatViewModel", "Proceeding to send video message without thumbnail for $convId.")
                }
                if (durationMs == null) {
                    Log.w("ChatViewModel", "Proceeding to send video message without duration for $convId.")
                }

                Log.d("ChatViewModel", "Metadata extracted. Thumbnail size: ${thumbnailBytes?.size}, Duration: $durationMs. Calling repository...")

                // 2. Chamar o repositório com os novos dados
                val result = chatRepository.sendVideoMessage(
                    conversationId = convId,
                    videoUri = uri,
                    caption = caption,
                    videoThumbnailBytes = thumbnailBytes, // PASSAR OS BYTES DA MINIATURA
                    videoDuration = durationMs             // PASSAR A DURAÇÃO
                )

                if (result.isSuccess) {
                    Log.d("ChatViewModel", "Video message (with metadata) sent successfully for conversation $convId")
                } else {
                    val error = result.exceptionOrNull()
                    Log.e("ChatViewModel", "ChatRepository reported failure for $convId (video with metadata): ${error?.message}", error)
                    Log.e("ChatViewModel", "Error UI Update: ChatRepository reported failure for $convId (video): ${error?.message}")
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "EXCEPTION in sendVideoMessage coroutine for $convId: ${e.message}", e)
                Log.e("ChatViewModel", "Error UI Update: EXCEPTION in sendVideoMessage coroutine for $convId: ${e.message}")
            } finally {
                Log.d("ChatViewModel", "Coroutine for sendVideoMessage FINISHED for $convId")
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onPinMessage(message: Message?) {
        viewModelScope.launch {
            Log.d("ChatViewModel", "onPinMessage chamada. Mensagem para fixar/desafixar: ${message?.id ?: "DESAFIXAR (mensagem nula)"} na conversa $conversationId")
            try {
                val result = chatRepository.pinMessage(conversationId, message)
                if (result.isSuccess) {
                    Log.d("ChatViewModel", "Operação de Fixar/Desafixar bem-sucedida para conversa $conversationId")
                } else {
                    val error = result.exceptionOrNull()
                    Log.e("ChatViewModel", "Falha ao fixar/desafixar mensagem para conversa $conversationId: ${error?.message}", error)
                    Log.e("ChatViewModel", "Error UI Update: Falha ao fixar/desafixar: ${error?.message}")
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Exceção durante fixar/desafixar mensagem para conversa $conversationId: ${e.message}", e)
                Log.e("ChatViewModel", "Error UI Update: Exceção ao fixar/desafixar: ${e.message}")
            }
        }
    }
}
