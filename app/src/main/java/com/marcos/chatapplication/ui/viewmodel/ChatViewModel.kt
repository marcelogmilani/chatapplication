package com.marcos.chatapplication.ui.viewmodel

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
import javax.inject.Inject

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = true,
    val participantsDetails: Map<String, User> = emptyMap(),
    val conversationDetails: ConversationWithDetails? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val conversationId: String = checkNotNull(savedStateHandle["conversationId"])

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
        }
    ) { messages, details, participantsMap ->
        ChatUiState(
            messages = messages,
            conversationDetails = details,
            participantsDetails = participantsMap,
            isLoading = false
        )
    }.catch { e ->
        emit(ChatUiState(errorMessage = e.message, isLoading = false))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ChatUiState(isLoading = true)
    )

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        viewModelScope.launch {
            chatRepository.sendMessage(conversationId, text.trim())
        }
    }
}