package com.marcos.chatapplication.domain.contracts

import android.net.Uri
import com.marcos.chatapplication.domain.model.User

interface UserRepository {
    suspend fun searchUsersByUsername(query: String): Result<List<User>>
    suspend fun getUserById(userId: String): Result<User?>
    suspend fun updateUserProfilePicture(userId: String, imageUri: Uri): Result<String>

    suspend fun saveFcmToken(token: String): Result<Unit>
}