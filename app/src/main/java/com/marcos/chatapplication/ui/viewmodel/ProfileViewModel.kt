package com.marcos.chatapplication.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcos.chatapplication.domain.contracts.AuthRepository
import com.marcos.chatapplication.domain.model.User
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.tasks.await

// Estado da UI para a tela de perfil
data class ProfileUiState(
    val user: User? = null,
    val isUploadingProfilePicture: Boolean = false,
    val profileUploadError: String? = null,
    val isLoadingUser: Boolean = true // Adicionado para rastrear o carregamento inicial do usuário
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val storage: FirebaseStorage, // Injetar FirebaseStorage
    private val firestore: FirebaseFirestore // Injetar FirebaseFirestore
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Observa o estado de autenticação para obter o usuário
            authRepository.getAuthState().collectLatest { authState ->
                _uiState.value = _uiState.value.copy(
                    user = authState.user,
                    isLoadingUser = authState.isInitialLoading
                )
            }
        }
    }

    fun uploadProfilePicture(uri: Uri) {
        viewModelScope.launch {
            val currentUser = _uiState.value.user
            // Verifica se o usuário está logado e se o UID não está vazio
            if (currentUser == null || currentUser.uid.isEmpty()) {
                _uiState.value = _uiState.value.copy(profileUploadError = "Usuário não autenticado ou UID inválido.")
                return@launch
            }

            _uiState.value = _uiState.value.copy(isUploadingProfilePicture = true, profileUploadError = null)

            try {
                // Define o caminho no Firebase Storage
                // Usar um nome de arquivo consistente como "profile.jpg" pode simplificar,
                // ou um nome único se você quiser manter histórico (mas geralmente não é necessário para foto de perfil)
                val storageRef = storage.reference.child("profile_pictures/${currentUser.uid}/profile.jpg")

                // Faz o upload do arquivo
                val uploadTask = storageRef.putFile(uri).await()

                // Obtém a URL de download da imagem
                val downloadUrl = uploadTask.storage.downloadUrl.await().toString()

                // Atualiza o campo 'profilePictureUrl' no documento do usuário no Firestore
                firestore.collection("users").document(currentUser.uid)
                    .update("profilePictureUrl", downloadUrl)
                    .await()

                // Atualiza o estado da UI com a nova URL da imagem e para o indicador de loading
                // A atualização do _authState (via listener no init) já deve pegar a mudança do Firestore,
                // mas para reflexo imediato na UI, podemos atualizar o user no uiState aqui também.
                // No entanto, é mais robusto confiar no listener do authState para manter a consistência.
                // A linha abaixo pode ser removida se o listener do authState for rápido o suficiente.
                // _uiState.value = _uiState.value.copy(
                // user = currentUser.copy(profilePictureUrl = downloadUrl),
                // isUploadingProfilePicture = false
                // )
                // Se o listener do authRepository.getAuthState() for rápido, a UI será atualizada por ele.
                // Apenas resetamos o estado de upload.
                _uiState.value = _uiState.value.copy(isUploadingProfilePicture = false)

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isUploadingProfilePicture = false,
                    profileUploadError = "Falha no upload: ${e.localizedMessage ?: "Erro desconhecido"}"
                )
            }
        }
    }


    fun clearProfileUploadError() {
        _uiState.value = _uiState.value.copy(profileUploadError = null)
    }

    // Método para fazer signOut
    fun signOut() {
        authRepository.signOut()

    }
}
