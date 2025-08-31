package com.marcos.chatapplication.ui.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import android.provider.ContactsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.marcos.chatapplication.domain.contracts.ChatRepository
import com.marcos.chatapplication.domain.contracts.UserRepository
import com.marcos.chatapplication.domain.model.User
import com.marcos.chatapplication.ui.screens.Contato
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
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
    val uiState: StateFlow<UserSearchUiState> = _uiState.asStateFlow()

    private val _navigateToChat = Channel<String>()
    val navigateToChat = _navigateToChat.receiveAsFlow()

    private val _contatos = MutableStateFlow<List<Contato>>(emptyList())
    val contatos: StateFlow<List<Contato>> = _contatos

    private var todosUsuariosFirebase: List<User> = emptyList()

    fun carregarTodosUsuarios() {
        viewModelScope.launch {
            val result = userRepository.getAllUsers()
            result.onSuccess { users ->
                todosUsuariosFirebase = users
                atualizarResultadosCombinados()
            }
        }
    }

    fun onQueryChange(newQuery: String) {
        _uiState.update { it.copy(query = newQuery) }

        viewModelScope.launch {
            val result = userRepository.searchUsersByUsername(newQuery)
            result.onSuccess { firebaseUsers ->
                val contatosFiltrados = _contatos.value.filter {
                    it.nome.contains(newQuery, ignoreCase = true)
                }.map {
                    User(uid = it.telefone, username = it.nome, profilePictureUrl = null)
                }

                val combinados = firebaseUsers + contatosFiltrados
                _uiState.update { it.copy(searchResults = combinados, isLoading = false) }
            }
        }
    }

    fun onUserSelected(targetUserId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = chatRepository.createOrGetConversation(targetUserId)
            result.onSuccess { conversationId ->
                _uiState.update { it.copy(isLoading = false) }
                _navigateToChat.send(conversationId)
            }.onFailure {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Não foi possível iniciar a conversa.")
                }
            }
        }
    }

    @SuppressLint("Range")
    fun lerContatos(context: Context) {
        val lista = mutableListOf<Contato>()
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            null, null, null, null
        )

        cursor?.use {
            while (it.moveToNext()) {
                val nome = it.getString(it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))
                val telefone = it.getString(it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))
                lista.add(Contato(nome, telefone))
            }
        }

        _contatos.value = lista
        sincronizarComFirebase(lista)
        atualizarResultadosCombinados()
    }

    private fun atualizarResultadosCombinados() {
        val contatosConvertidos = _contatos.value.map {
            User(uid = it.telefone, username = it.nome, profilePictureUrl = null)
        }
        val combinados = todosUsuariosFirebase + contatosConvertidos
        _uiState.update { it.copy(searchResults = combinados) }
    }

    fun sincronizarComFirebase(contatos: List<Contato>) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val ref = FirebaseDatabase.getInstance().getReference("contatos/$uid")

        contatos.forEach {
            ref.push().setValue(it)
        }
    }
}
