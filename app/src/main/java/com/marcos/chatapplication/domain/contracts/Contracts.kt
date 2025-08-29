package com.marcos.chatapplication.domain.contracts

import com.marcos.chatapplication.domain.model.User


data class AuthState(
    val user: User? = null,
    val isInitialLoading: Boolean = true
)

