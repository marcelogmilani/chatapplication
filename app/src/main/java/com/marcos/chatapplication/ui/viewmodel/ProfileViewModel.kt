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
import java.util.TimeZone // Import TimeZone
import javax.inject.Inject
import kotlinx.coroutines.tasks.await

// Estado da UI para a tela de perfil
data class ProfileUiState(
    val user: User? = null,
    val isLoadingUser: Boolean = true,

    // Para edição de perfil
    val editableUsername: String = "",
    val editableEmail: String = "",
    val editableBirthDate: String = "", // Deve ser "dd/MM/yyyy" representando o dia em UTC
    val showDatePickerDialog: Boolean = false,

    // Para upload de foto
    val isUploadingProfilePicture: Boolean = false,
    val profileUploadError: String? = null,

    // Para salvar perfil
    val isSavingProfile: Boolean = false,
    val profileSaveSuccessMessage: String? = null,
    val profileSaveErrorMessage: String? = null
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val storage: FirebaseStorage,
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    // Formatter para converter Long (UTC millis) para String ("dd/MM/yyyy" em UTC) e vice-versa
    private val utcDateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    init {
        viewModelScope.launch {
            authRepository.getAuthState().collectLatest { authState ->
                _uiState.update { currentState ->
                    // Evita sobrescrever campos editáveis se já estiverem em edição,
                    // a menos que o usuário tenha mudado.
                    val isDifferentUser = currentState.user?.uid != null && currentState.user.uid != authState.user?.uid
                    val shouldResetEditableFields = currentState.editableUsername.isEmpty() ||
                            currentState.editableEmail.isEmpty() ||
                            currentState.editableBirthDate.isEmpty() ||
                            isDifferentUser

                    currentState.copy(
                        user = authState.user,
                        isLoadingUser = authState.isInitialLoading,
                        editableUsername = if (shouldResetEditableFields) authState.user?.username ?: "" else currentState.editableUsername,
                        editableEmail = if (shouldResetEditableFields) authState.user?.email ?: "" else currentState.editableEmail,
                        editableBirthDate = if (shouldResetEditableFields) authState.user?.birthDate ?: "" else currentState.editableBirthDate
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
        // Geralmente não será chamado se o campo for readOnly e apenas o DatePicker for usado
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
            // dateInMillis é da meia-noite UTC do dia selecionado.
            // Formate-o usando o utcDateFormatter para obter "dd/MM/yyyy" desse dia em UTC.
            val selectedDateString = utcDateFormatter.format(Date(dateInMillis))
            _uiState.update { it.copy(editableBirthDate = selectedDateString) }
        }
        onDatePickerDialogDismissed()
    }


    fun saveProfile() {
        val currentUser = _uiState.value.user
        if (currentUser == null || currentUser.uid.isEmpty()) {
            _uiState.update { it.copy(profileSaveErrorMessage = "Usuário não autenticado.") }
            return
        }

        val newUsername = _uiState.value.editableUsername.trim()
        val newEmail = _uiState.value.editableEmail.trim()
        val newBirthDate = _uiState.value.editableBirthDate.trim() // Já deve estar "dd/MM/yyyy" (UTC day)

        if (newUsername.isEmpty()) {
            _uiState.update { it.copy(profileSaveErrorMessage = "Nome de usuário não pode estar vazio.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSavingProfile = true, profileSaveErrorMessage = null, profileSaveSuccessMessage = null) }
            val result = userRepository.updateUserProfile(
                userId = currentUser.uid,
                newUsername = newUsername,
                newEmail = newEmail.ifBlank { null },
                newBirthDate = newBirthDate.ifBlank { null } // Salva a string "dd/MM/yyyy" (UTC day)
            )

            result.onSuccess {
                _uiState.update { currentState ->
                    val updatedUser = currentState.user?.copy(
                        username = newUsername,
                        email = newEmail.ifBlank { currentState.user.email },
                        birthDate = newBirthDate.ifBlank { currentState.user.birthDate }
                    )
                    currentState.copy(
                        isSavingProfile = false,
                        profileSaveSuccessMessage = "Perfil atualizado com sucesso!",
                        user = updatedUser,
                        editableUsername = newUsername,
                        editableEmail = newEmail,
                        editableBirthDate = newBirthDate // Mantém o valor formatado
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
                    .update("profilePictureUrl", downloadUrl)
                    .await()
                _uiState.update { currentState ->
                    val updatedUserWithPic = currentState.user?.copy(profilePictureUrl = downloadUrl)
                    currentState.copy(
                        isUploadingProfilePicture = false,
                        user = updatedUserWithPic
                    )
                }
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

    fun clearProfileSaveSuccessMessage() {
        _uiState.update { it.copy(profileSaveSuccessMessage = null) }
    }

    fun clearProfileSaveErrorMessage() {
        _uiState.update { it.copy(profileSaveErrorMessage = null) }
    }

    fun signOut() {
        authRepository.signOut()
    }
}
