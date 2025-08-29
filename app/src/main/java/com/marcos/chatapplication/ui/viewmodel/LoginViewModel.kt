package com.marcos.chatapplication.ui.viewmodel

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import com.marcos.chatapplication.domain.contracts.AuthRepository
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
    private val authRepository: AuthRepository
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
            _uiState.update { it.copy(errorMessage = "Verification process not started.") }
            return
        }

        if (code.length != 6) {
            _uiState.update { it.copy(codeError = "The code must be 6 digits long.") }
            return
        } else {
            _uiState.update { it.copy(codeError = null) }
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val credential = PhoneAuthProvider.getCredential(verificationId, code)
            val result = authRepository.signInWithPhoneAuthCredential(credential, "")
            result.onSuccess {
                _uiState.update { it.copy(isLoading = false, loginSuccess = true) }
            }.onFailure { exception ->
                _uiState.update { it.copy(isLoading = false, errorMessage = exception.message) }
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