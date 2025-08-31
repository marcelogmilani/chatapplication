package com.marcos.chatapplication.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.marcos.chatapplication.domain.contracts.ChatRepository
import com.marcos.chatapplication.domain.model.ConversationWithDetails
import com.marcos.chatapplication.domain.model.Message
import com.marcos.chatapplication.domain.model.MessageStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = true,
    val conversationDetails: ConversationWithDetails? = null,
    val errorMessage: String? = null,
    val searchQuery: String = "",
    val filteredMessages: List<Message> = emptyList()
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val conversationId: String = checkNotNull(savedStateHandle["conversationId"])

    private val _searchQuery = MutableStateFlow("")

    val uiState: StateFlow<ChatUiState> = combine(
        chatRepository.getMessages(conversationId).onEach { messages ->
            val hasUnreadMessages = messages.any {
                it.senderId != Firebase.auth.currentUser?.uid && it.status != MessageStatus.READ
            }
            if (hasUnreadMessages) {
                chatRepository.markMessagesAsRead(conversationId)
            }
        },
        chatRepository.getConversationDetails(conversationId),_searchQuery
    ) { messages, details, query ->
        val filteredMessages = if (query.isBlank()) {
            messages
        } else {
            messages.filter { it.text.contains(query, ignoreCase = true) }
        }
        ChatUiState(
            messages = messages,
            conversationDetails = details,
            isLoading = false,
            searchQuery = query,
            filteredMessages = filteredMessages
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

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onPinMessage(message: Message) {
        viewModelScope.launch {
            val currentPinnedId = uiState.value.conversationDetails?.conversation?.pinnedMessageId
            val messageToPin = if (currentPinnedId == message.id) null else message
            chatRepository.pinMessage(conversationId, messageToPin)
        }
    }
}