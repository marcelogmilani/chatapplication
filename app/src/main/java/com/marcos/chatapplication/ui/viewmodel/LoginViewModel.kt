package com.marcos.chatapplication.ui.viewmodel

import android.app.Activity
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.messaging.FirebaseMessaging
import com.marcos.chatapplication.domain.contracts.AuthRepository
import com.marcos.chatapplication.domain.contracts.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val isLoading: Boolean = false,
    val loginSuccess: Boolean = false,
    val errorMessage: String? = null,
    val phoneNumberError: String? = null,
    val codeError: String? = null,
    val isCodeSent: Boolean = false,
    val verificationId: String? = null
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState = _uiState.asStateFlow()

    private val verificationCallbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            _uiState.update { it.copy(isLoading = false) }
        }

        override fun onVerificationFailed(e: com.google.firebase.FirebaseException) {
            _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
        }

        override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isCodeSent = true,
                    verificationId = verificationId
                )
            }
        }
    }

    fun startPhoneNumberVerification(phoneNumber: String, activity: Activity) {
        if (phoneNumber.isBlank()) {
            _uiState.update { it.copy(phoneNumberError = "Phone number cannot be empty") }
            return
        } else {
            _uiState.update { it.copy(phoneNumberError = null) }
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            authRepository.verifyPhoneNumber(phoneNumber, activity, verificationCallbacks)
        }
    }

    fun signInWithCode(code: String) {
        val verificationId = _uiState.value.verificationId

        if (verificationId == null) {
            _uiState.update { it.copy(errorMessage = "Processo de verificação não iniciado.") }
            return
        }

        if (code.length != 6) {
            _uiState.update { it.copy(codeError = "O código deve ter 6 dígitos.") }
            return
        } else {
            _uiState.update { it.copy(codeError = null) }
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val credential = PhoneAuthProvider.getCredential(verificationId, code)
            val result = authRepository.signInWithPhoneAuthCredential(credential, "")

            result.onSuccess {
                getAndSaveFcmToken()
                _uiState.update { it.copy(isLoading = false, loginSuccess = true) }
            }.onFailure { exception ->
                _uiState.update { it.copy(isLoading = false, errorMessage = exception.message) }
            }
        }
    }

    private fun getAndSaveFcmToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("FCM", "A obtenção do token FCM falhou", task.exception)
                return@addOnCompleteListener
            }

            // Obter o novo token de registo FCM
            val token = task.result
            Log.d("FCM", "Token FCM obtido: $token")

            // Guardá-lo no Firestore através do repositório
            viewModelScope.launch {
                userRepository.saveFcmToken(token).onFailure { exception ->
                    // A falha em guardar o token não deve impedir o login.
                    // Apenas registamos o erro.
                    Log.e("FCM", "Falha ao guardar o token FCM", exception)
                }
            }
        }
    }

    fun onErrorMessageShown() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun onLoginHandled() {
        _uiState.update { it.copy(loginSuccess = false) }
    }
}