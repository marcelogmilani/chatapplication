package com.marcos.chatapplication.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.marcos.chatapplication.domain.contracts.UserRepository
import com.marcos.chatapplication.domain.model.User
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class UserRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth
) : UserRepository {

    override suspend fun searchUsersByUsername(query: String): Result<List<User>> {
        return try {
            if (query.isBlank()) {
                return Result.success(emptyList())
            }

            val lowercaseQuery = query.lowercase()
            val currentUserId = firebaseAuth.currentUser?.uid ?: ""

            Log.d("UserSearchRepo", "Buscando por (case-insensitive): '$lowercaseQuery', Usuário atual: $currentUserId")


            val result = firestore.collection("users")
                .whereGreaterThanOrEqualTo("username_lowercase", lowercaseQuery)
                .whereLessThanOrEqualTo("username_lowercase", lowercaseQuery + '\uf8ff')
                .limit(20)
                .get()
                .await()

            Log.d("UserSearchRepo", "Firestore encontrou ${result.size()} documentos.")

            val users = result.toObjects(User::class.java)
                .filter { it.uid != currentUserId } // Manter a lógica de filtro

            Log.d("UserSearchRepo", "Após filtrar o usuário atual, restaram ${users.size} usuários.")

            Result.success(users)
        } catch (e: Exception) {
            Log.e("UserSearchRepo", "Erro na busca: ", e)
            Result.failure(e)
        }
    }

    override suspend fun getUserById(userId: String): Result<User?> {
        return try {
            val document = firestore.collection("users").document(userId).get().await()
            val user = document.toObject(User::class.java)
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}