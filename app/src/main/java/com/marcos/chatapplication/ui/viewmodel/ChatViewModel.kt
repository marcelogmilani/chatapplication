package com.marcos.chatapplication.ui.viewmodel

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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
// import kotlinx.coroutines.flow.update // Removido se não for usado para _uiState
import kotlinx.coroutines.launch
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
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val conversationId: String = checkNotNull(savedStateHandle["conversationId"])

    private val _searchQuery = MutableStateFlow("")
    // Para erros de UI que não vêm do combine, você pode usar um MutableStateFlow separado
    // private val _userActionError = MutableStateFlow<String?>(null)
    // E então combiná-lo no uiState ou observá-lo separadamente na UI.

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
        // , _userActionError // Se você adicionar um flow de erro separado
    ) { messages, details, participantsMap, query /*, userActionError */ ->
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
            // Priorizar erro de ação do usuário, senão erro do combine, senão o existente
            errorMessage = /* userActionError ?: */ uiState.value.errorMessage
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

        // Verificar se o usuário ainda é participante do grupo
        if (!isUserParticipant(conversationId)) {
            Log.e("ChatViewModel", "Usuário não é mais participante do grupo")
            // Você pode mostrar um erro para o usuário aqui
            return
        }

        viewModelScope.launch {
            Log.d("ChatViewModel", "Attempting to send message: $text in conversation: $conversationId")
            val result = chatRepository.sendMessage(conversationId, text.trim())
            if (result.isFailure) {
                val error = result.exceptionOrNull()
                Log.e("ChatViewModel", "Failed to send message: ${error?.message}", error)
                // _userActionError.value = "Erro ao enviar mensagem: ${error?.message}"
                // A UI observaria _userActionError ou ele seria combinado no uiState.
                // Por ora, apenas logando, como estava antes.
                Log.e("ChatViewModel", "Error UI Update: Failed to send message: ${error?.message}")
            } else {
                Log.d("ChatViewModel", "Message sent successfully")
                // _userActionError.value = null // Limpar erro se bem-sucedido
            }
        }
    }

    // ATUALIZADO: sendImageMessage agora aceita 'caption' opcional
    fun sendImageMessage(uri: Uri, convId: String, caption: String? = null) {

        if (!isUserParticipant(convId)) {
            Log.e("ChatViewModel", "Usuário não é mais participante do grupo")
            return
        }

        Log.d("ChatViewModel", "sendImageMessage called with URI: $uri, for conversation: $convId, caption: $caption")
        viewModelScope.launch {
            try {
                Log.d("ChatViewModel", "Coroutine for sendImageMessage STARTED for $convId")
                // Passa a legenda para o repositório
                val result = chatRepository.sendImageMessage(convId, uri, caption)
                if (result.isSuccess) {
                    Log.d("ChatViewModel", "Image message sent successfully for conversation $convId")
                    // _userActionError.value = null
                } else {
                    val error = result.exceptionOrNull()
                    Log.e("ChatViewModel", "ChatRepository reported failure for $convId: ${error?.message}", error)
                    // _userActionError.value = "Erro ao enviar imagem: ${error?.message}"
                    Log.e("ChatViewModel", "Error UI Update: ChatRepository reported failure for $convId: ${error?.message}")
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "EXCEPTION in sendImageMessage coroutine for $convId: ${e.message}", e)
                // _userActionError.value = "Erro crítico ao enviar imagem: ${e.message}"
                Log.e("ChatViewModel", "Error UI Update: EXCEPTION in sendImageMessage coroutine for $convId: ${e.message}")
            } finally {
                Log.d("ChatViewModel", "Coroutine for sendImageMessage FINISHED for $convId")
            }
        }
    }


    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    // ATUALIZADO: onPinMessage agora aceita Message? (anulável) para consistência
    fun onPinMessage(message: Message?) {
        // conversationId já é non-null devido ao checkNotNull na inicialização.
        viewModelScope.launch {
            Log.d("ChatViewModel", "onPinMessage chamada. Mensagem para fixar/desafixar: ${message?.id ?: "DESAFIXAR (mensagem nula)"} na conversa $conversationId")
            try {
                // Se a UI já passa null para desafixar, podemos passar `message` diretamente.
                // A lógica original de toggle era:
                // val currentPinnedId = uiState.value.conversationDetails?.conversation?.pinnedMessageId
                // val messageToPin: Message? = if (message != null && currentPinnedId == message.id) null else message
                // Vamos usar a simplificada, assumindo que a UI envia null para desafixar (como no PinnedMessageBar)
                // ou a mensagem a ser fixada (long press no MessageBubble).
                val result = chatRepository.pinMessage(conversationId, message)

                if (result.isSuccess) {
                    Log.d("ChatViewModel", "Operação de Fixar/Desafixar bem-sucedida para conversa $conversationId")
                    // _userActionError.value = null
                } else {
                    val error = result.exceptionOrNull()
                    Log.e("ChatViewModel", "Falha ao fixar/desafixar mensagem para conversa $conversationId: ${error?.message}", error)
                    // _userActionError.value = "Erro ao fixar/desafixar: ${error?.message}"
                    Log.e("ChatViewModel", "Error UI Update: Falha ao fixar/desafixar: ${error?.message}")
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Exceção durante fixar/desafixar mensagem para conversa $conversationId: ${e.message}", e)
                // _userActionError.value = "Erro crítico ao fixar/desafixar: ${e.message}"
                Log.e("ChatViewModel", "Error UI Update: Exceção ao fixar/desafixar: ${e.message}")
            }
        }
    }

    private val _groupActionState = MutableStateFlow<GroupActionState>(GroupActionState.Idle)
    val groupActionState: StateFlow<GroupActionState> = _groupActionState.asStateFlow()

    fun updateGroupName(conversationId: String, newName: String) {
        viewModelScope.launch {
            _groupActionState.value = GroupActionState.Loading
            val result = chatRepository.updateGroupName(conversationId, newName)
            if (result.isSuccess) {
                _groupActionState.value = GroupActionState.Success("Nome do grupo atualizado")
            } else {
                val errorMessage = result.exceptionOrNull()?.message ?: "Erro ao atualizar nome"
                _groupActionState.value = GroupActionState.Error(errorMessage)
            }
        }
    }

    fun addParticipantsToGroup(conversationId: String, userIds: List<String>) {
        viewModelScope.launch {
            _groupActionState.value = GroupActionState.Loading
            val result = chatRepository.addParticipantsToGroup(conversationId, userIds)
            if (result.isSuccess) {
                _groupActionState.value = GroupActionState.Success("Participantes adicionados")
            } else {
                val errorMessage = result.exceptionOrNull()?.message ?: "Erro ao adicionar participantes"
                _groupActionState.value = GroupActionState.Error(errorMessage)
            }
        }
    }

    fun removeParticipantFromGroup(conversationId: String, userId: String) {
        viewModelScope.launch {
            _groupActionState.value = GroupActionState.Loading
            val result = chatRepository.removeParticipantFromGroup(conversationId, userId)
            if (result.isSuccess) {
                _groupActionState.value = GroupActionState.Success("Participante removido")
            } else {
                val errorMessage = result.exceptionOrNull()?.message ?: "Erro ao remover participante"
                _groupActionState.value = GroupActionState.Error(errorMessage)
            }
        }
    }

    fun clearGroupActionState() {
        _groupActionState.value = GroupActionState.Idle
    }

    suspend fun getAvailableUsers(conversationId: String): List<User> {
        return try {
            // Obter todos os usuários disponíveis
            val allUsers = chatRepository.getAvailableUsers().getOrElse { emptyList() }

            // Obter participantes atuais do grupo
            val conversationDetails = chatRepository.getConversationDetails(conversationId).first()
            val currentParticipants = conversationDetails?.conversation?.participants ?: emptyList()

            // Filtrar usuários que não estão no grupo e ordenar por nome
            allUsers
                .filter { user -> user.uid !in currentParticipants }
                .sortedBy { it.username?.lowercase() } // Ordem alfabética case-insensitive
        } catch (e: Exception) {
            Log.e("ChatViewModel", "Error getting available users: ${e.message}", e)
            emptyList()
        }
    }

    fun loadConversationDetails(conversationId: String) {
        viewModelScope.launch {
            // Esta função já existe no repositório através do getConversationDetails
            // O Flow já está sendo observado no uiState
        }
    }

    fun resetGroupActionState() {
        _groupActionState.value = GroupActionState.Idle
    }

    fun isUserParticipant(conversationId: String, userId: String? = null): Boolean {
        val currentUserId = userId ?: Firebase.auth.currentUser?.uid
        val participants = uiState.value.conversationDetails?.conversation?.participants ?: emptyList()
        return currentUserId in participants
    }

    fun updateGroupImage(conversationId: String, imageUri: Uri) {
        viewModelScope.launch {
            _groupActionState.value = GroupActionState.Loading
            val result = chatRepository.updateGroupImage(conversationId, imageUri)
            if (result.isSuccess) {
                _groupActionState.value = GroupActionState.Success("Imagem do grupo atualizada")
            } else {
                val errorMessage = result.exceptionOrNull()?.message ?: "Erro ao atualizar imagem"
                _groupActionState.value = GroupActionState.Error(errorMessage)
            }
        }
    }


}


sealed class GroupActionState {
    object Idle : GroupActionState()
    object Loading : GroupActionState()
    data class Success(val message: String) : GroupActionState()
    data class Error(val message: String) : GroupActionState()
}


