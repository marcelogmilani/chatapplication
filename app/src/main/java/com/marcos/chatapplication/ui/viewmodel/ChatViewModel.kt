package com.marcos.chatapplication.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcos.chatapplication.domain.contracts.ChatRepository
import com.marcos.chatapplication.domain.model.ConversationWithDetails
import com.marcos.chatapplication.domain.model.Message
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = true,
    val conversationDetails: ConversationWithDetails? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val conversationId: String = checkNotNull(savedStateHandle["conversationId"])

    val uiState: StateFlow<ChatUiState> = combine(
        chatRepository.getMessages(conversationId),
        chatRepository.getConversationDetails(conversationId)
    ) { messages, details ->
        ChatUiState(
            messages = messages,
            conversationDetails = details,
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