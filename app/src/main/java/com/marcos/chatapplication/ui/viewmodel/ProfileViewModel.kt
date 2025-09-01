package com.marcos.chatapplication.ui.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcos.chatapplication.domain.contracts.AuthRepository
import com.marcos.chatapplication.domain.contracts.UserRepository
import com.marcos.chatapplication.domain.model.User
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import kotlinx.coroutines.tasks.await

data class ProfileUiState(
    val user: User? = null,
    val isLoadingUser: Boolean = true,
    val editableUsername: String = "",
    val editableEmail: String = "",
    val editableBirthDate: String = "",
    val editableStatus: String = "", // Este continua representando o userSetStatus na UI
    val showDatePickerDialog: Boolean = false,
    val isUploadingProfilePicture: Boolean = false,
    val profileUploadError: String? = null,
    val isSavingProfile: Boolean = false,
    val profileSaveSuccessMessage: String? = null,
    val profileSaveErrorMessage: String? = null
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val storage: FirebaseStorage,
    private val firestore: FirebaseFirestore // Injeção direta do Firestore pode ser revista se toda lógica estiver no repo
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val utcDateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    init {
        viewModelScope.launch {
            authRepository.getAuthState().collectLatest { authState ->
                _uiState.update { currentState ->
                    val isDifferentUser = currentState.user?.uid != null && currentState.user.uid != authState.user?.uid
                    val shouldResetEditableFields = currentState.editableUsername.isEmpty() ||
                            currentState.editableEmail.isEmpty() ||
                            currentState.editableBirthDate.isEmpty() ||
                            currentState.editableStatus.isEmpty() || // Condição para resetar o status editável
                            isDifferentUser

                    currentState.copy(
                        user = authState.user,
                        isLoadingUser = authState.isInitialLoading,
                        editableUsername = if (shouldResetEditableFields) authState.user?.username ?: "" else currentState.editableUsername,
                        editableEmail = if (shouldResetEditableFields) authState.user?.email ?: "" else currentState.editableEmail,
                        editableBirthDate = if (shouldResetEditableFields) authState.user?.birthDate ?: "" else currentState.editableBirthDate,
                        // ATUALIZADO: Ler de userSetStatus
                        editableStatus = if (shouldResetEditableFields) authState.user?.userSetStatus ?: "" else currentState.editableStatus
                    )
                }
            }
        }
    }

    fun onUsernameChanged(newUsername: String) {
        _uiState.update { it.copy(editableUsername = newUsername) }
    }

    fun onEmailChanged(newEmail: String) {
        _uiState.update { it.copy(editableEmail = newEmail) }
    }

    fun onBirthDateTextChanged(newBirthDate: String) {
        _uiState.update { it.copy(editableBirthDate = newBirthDate) }
    }

    fun onBirthDateClicked() {
        _uiState.update { it.copy(showDatePickerDialog = true) }
    }

    fun onDatePickerDialogDismissed() {
        _uiState.update { it.copy(showDatePickerDialog = false) }
    }

    fun onBirthDateSelected(dateInMillis: Long?) {
        if (dateInMillis != null) {
            val selectedDateString = utcDateFormatter.format(Date(dateInMillis))
            _uiState.update { it.copy(editableBirthDate = selectedDateString) }
        }
        onDatePickerDialogDismissed()
    }

    // Esta função atualiza o editableStatus no UIState, que representa o userSetStatus
    fun onStatusChanged(newUserSetStatus: String) {
        _uiState.update { it.copy(editableStatus = newUserSetStatus) }
    }

    fun saveProfile() {
        val currentUser = _uiState.value.user
        if (currentUser == null || currentUser.uid.isEmpty()) {
            _uiState.update { it.copy(profileSaveErrorMessage = "Usuário não autenticado.") }
            return
        }

        val newUsername = _uiState.value.editableUsername.trim()
        val newEmail = _uiState.value.editableEmail.trim()
        val newBirthDate = _uiState.value.editableBirthDate.trim()
        // newEditableStatus representa o novo userSetStatus que o usuário selecionou
        val newEditableStatus = _uiState.value.editableStatus.trim()

        if (newUsername.isEmpty()) {
            _uiState.update { it.copy(profileSaveErrorMessage = "Nome de usuário não pode estar vazio.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSavingProfile = true, profileSaveErrorMessage = null, profileSaveSuccessMessage = null) }

            // O parâmetro 'newStatus' aqui será o valor que queremos salvar como 'userSetStatus'
            // Precisaremos garantir que UserRepository.updateUserProfile lide com isso corretamente.
            val result = userRepository.updateUserProfile(
                userId = currentUser.uid,
                newUsername = newUsername,
                newEmail = newEmail.ifBlank { null },
                newBirthDate = newBirthDate.ifBlank { null },
                newStatus = newEditableStatus // Este valor deve ser salvo como userSetStatus
            )

            result.onSuccess {
                _uiState.update { currentState ->
                    val updatedUser = currentState.user?.copy(
                        username = newUsername,
                        email = newEmail.ifBlank { currentState.user.email },
                        birthDate = newBirthDate.ifBlank { currentState.user.birthDate },
                        // ATUALIZADO: Atualizar userSetStatus no objeto User local
                        userSetStatus = newEditableStatus.ifBlank { currentState.user.userSetStatus }
                    )
                    currentState.copy(
                        isSavingProfile = false,
                        profileSaveSuccessMessage = "Perfil atualizado com sucesso!",
                        user = updatedUser,
                        editableUsername = newUsername,
                        editableEmail = newEmail,
                        editableBirthDate = newBirthDate,
                        editableStatus = newEditableStatus // Mantém o campo editável em sincronia
                    )
                }
            }.onFailure { exception ->
                _uiState.update {
                    it.copy(
                        isSavingProfile = false,
                        profileSaveErrorMessage = "Falha ao atualizar perfil: ${exception.message}"
                    )
                }
            }
        }
    }

    fun uploadProfilePicture(uri: Uri) {
        viewModelScope.launch {
            val currentUser = _uiState.value.user
            if (currentUser == null || currentUser.uid.isEmpty()) {
                _uiState.value = _uiState.value.copy(profileUploadError = "Usuário não autenticado ou UID inválido.")
                return@launch
            }
            _uiState.value = _uiState.value.copy(isUploadingProfilePicture = true, profileUploadError = null)
            try {
                val storageRef = storage.reference.child("profile_pictures/${currentUser.uid}/profile.jpg")
                val uploadTask = storageRef.putFile(uri).await()
                val downloadUrl = uploadTask.storage.downloadUrl.await().toString()
                firestore.collection("users").document(currentUser.uid)
                    .update("profilePictureUrl", downloadUrl) // Isso atualiza diretamente no Firestore
                    .await()
                _uiState.update { currentState ->
                    // Atualiza o user local com a nova URL da foto
                    val updatedUserWithPic = currentState.user?.copy(profilePictureUrl = downloadUrl)
                    currentState.copy(
                        isUploadingProfilePicture = false,
                        user = updatedUserWithPic
                    )
                }
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Falha no upload da foto de perfil", e)
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

    fun clearProfileSaveSuccessMessage() {
        _uiState.update { it.copy(profileSaveSuccessMessage = null) }
    }

    fun clearProfileSaveErrorMessage() {
        _uiState.update { it.copy(profileSaveErrorMessage = null) }
    }

    // ATENÇÃO: A lógica de signOut precisará ser melhorada para definir presenceStatus="Offline"
    // e lastSeen no Firestore. Isso será feito em uma etapa posterior com o sistema de presença.
    fun signOut() {
        //viewModelScope.launch {
        // Futuramente: userRepository.setUserOffline(currentUser.uid)
        //}
        authRepository.signOut()
    }
}

