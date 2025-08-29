package com.marcos.chatapplication.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcos.chatapplication.domain.contracts.ChatRepository
import com.marcos.chatapplication.domain.contracts.UserRepository
import com.marcos.chatapplication.domain.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UserSearchUiState(
    val query: String = "",
    val searchResults: List<User> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class UserSearchViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(UserSearchUiState())
    val uiState = _uiState.asStateFlow()

    private val _navigateToChat = Channel<String>()
    val navigateToChat = _navigateToChat.receiveAsFlow()

    fun onUserSelected(targetUserId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = chatRepository.createOrGetConversation(targetUserId)
            result.onSuccess { conversationId ->
                _uiState.update { it.copy(isLoading = false) }
                _navigateToChat.send(conversationId)
            }.onFailure { exception ->
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Não foi possível iniciar a conversa.")
                }
            }
        }
    }

    fun onQueryChange(newQuery: String) {
        _uiState.update { it.copy(query = newQuery) }
        searchUsers(newQuery)
    }

    private fun searchUsers(query: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = userRepository.searchUsersByUsername(query)
            result.onSuccess { users ->
                _uiState.update { it.copy(searchResults = users, isLoading = false) }
            }.onFailure { exception ->
                _uiState.update { it.copy(errorMessage = exception.message, isLoading = false) }
            }
        }
    }
}