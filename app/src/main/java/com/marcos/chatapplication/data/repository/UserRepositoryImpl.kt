package com.marcos.chatapplication.data.repository

import android.net.Uri // Importar Uri
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage // IMPORTAR FirebaseStorage
import com.marcos.chatapplication.domain.contracts.UserRepository
import com.marcos.chatapplication.domain.model.User
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class UserRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth,
    private val storage: FirebaseStorage
) : UserRepository {

    override suspend fun searchUsersByUsername(query: String): Result<List<User>> {
        return try {
            if (query.isBlank()) {
                return Result.success(emptyList())
            }

            val lowercaseQuery = query.lowercase()


            Log.d("UserSearchRepo", "Buscando por (case-insensitive): '$lowercaseQuery'")


            val result = firestore.collection("users")
                .whereGreaterThanOrEqualTo("username_lowercase", lowercaseQuery)
                .whereLessThanOrEqualTo("username_lowercase", lowercaseQuery + "\uf8ff")
                .limit(20)
                .get()
                .await()

            Log.d("UserSearchRepo", "Firestore encontrou ${result.size()} documentos.")

            // Filtrar o usuário atual dos resultados, se o firebaseAuth.currentUser?.uid estiver disponível
            val currentUserId = firebaseAuth.currentUser?.uid
            val users = result.toObjects(User::class.java).filter { user ->
                currentUserId == null || user.uid != currentUserId
            }


            Log.d("UserSearchRepo", "Após filtrar o usuário atual (se houver), restaram ${users.size} usuários.")

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
            Log.e("UserSearchRepo", "Error getting user by ID", e) // Adicionada mensagem de erro mais específica
            Result.failure(e)
        }
    }


    override suspend fun updateUserProfilePicture(userId: String, imageUri: Uri): Result<String> {
        return try {
            // Caminho no Storage: profile_pictures/UID_DO_USUARIO/profile.jpg
            val storageRef = storage.reference.child("profile_pictures/$userId/profile.jpg")

            // Faz upload da imagem
            val uploadTask = storageRef.putFile(imageUri).await()

            // Obtém a URL de download
            val downloadUrl = uploadTask.storage.downloadUrl.await().toString()

            // Atualiza o documento do usuário no Firestore
            firestore.collection("users").document(userId)
                .update("profilePictureUrl", downloadUrl)
                .await()

            Log.d("UserRepositoryImpl", "Profile picture updated successfully. URL: $downloadUrl")
            Result.success(downloadUrl)
        } catch (e: Exception) {
            Log.e("UserRepositoryImpl", "Error updating profile picture", e)
            Result.failure(e)
        }
    }
}
