package com.marcos.chatapplication.domain.contracts

import com.marcos.chatapplication.domain.model.User

interface UserRepository {
    suspend fun searchUsersByUsername(query: String): Result<List<User>>
    suspend fun getUserById(userId: String): Result<User?>
}