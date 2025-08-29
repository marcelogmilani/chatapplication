package com.marcos.chatapplication.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcos.chatapplication.domain.contracts.ChatRepository
import com.marcos.chatapplication.domain.model.ConversationWithDetails
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class HomeUiState(
    val conversations: List<ConversationWithDetails> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    chatRepository: ChatRepository
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = chatRepository.getUserConversations()
        .map { conversations ->
            HomeUiState(conversations = conversations, isLoading = false)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HomeUiState(isLoading = true)
        )
}