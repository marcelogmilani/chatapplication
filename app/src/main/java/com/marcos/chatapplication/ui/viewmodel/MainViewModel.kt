package com.marcos.chatapplication.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.marcos.chatapplication.domain.contracts.AuthRepository
import com.marcos.chatapplication.domain.contracts.AuthState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    val authState: StateFlow<AuthState> = authRepository.getAuthState()
}
