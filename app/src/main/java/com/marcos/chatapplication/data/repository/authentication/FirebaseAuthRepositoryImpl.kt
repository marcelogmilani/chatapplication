package com.marcos.chatapplication.data.repository.authentication

import android.app.Activity
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.marcos.chatapplication.domain.contracts.AuthRepository
import com.marcos.chatapplication.domain.contracts.AuthState
// Import da data class User para o username_lowercase
import com.marcos.chatapplication.domain.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

class FirebaseAuthRepositoryImpl(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : AuthRepository {

    private val _authState = MutableStateFlow(AuthState(isInitialLoading = true))

    init {
        firebaseAuth.addAuthStateListener { auth ->
            try {
                val firebaseUser = auth.currentUser
                if (firebaseUser != null) {
                    // Tentativa de buscar o usuário completo do Firestore, incluindo username_lowercase se existir
                    firestore.collection("users").document(firebaseUser.uid).get()
                        .addOnSuccessListener { document ->
                            val userFromFirestore = if (document != null && document.exists()) {
                                document.toObject(User::class.java) // Agora pode ter username_lowercase
                            } else {
                                null
                            }
                            _authState.update {
                                it.copy(
                                    user = firebaseUser.toDomainUser(
                                        usernameFromFirestore = userFromFirestore?.username,
                                        // Passando também o username_lowercase se disponível
                                        usernameLowercaseFromFirestore = userFromFirestore?.username_lowercase
                                    ),
                                    isInitialLoading = false
                                )
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.w("FirebaseAuthRepo", "Erro ao buscar dados do usuário: ${e.message}", e)
                            // Se falhar ao buscar do Firestore, usa apenas o que tem do FirebaseUser
                            _authState.update {
                                it.copy(
                                    user = firebaseUser.toDomainUser(null, null), // username e username_lowercase serão null
                                    isInitialLoading = false
                                )
                            }
                        }
                } else {
                    _authState.update { it.copy(user = null, isInitialLoading = false) }
                }
            } catch (e: Exception) {
                Log.e("FirebaseAuthRepo", "Erro crítico no auth listener", e)
                _authState.update { it.copy(isInitialLoading = false) } // Garante que o loading termine
            }
        }
    }

    override suspend fun checkIfPhoneNumberExists(phoneNumber: String): Result<Boolean> {
        return try {
            val query = firestore.collection("users")
                .whereEqualTo("phone", phoneNumber)
                .limit(1)
                .get()
                .await()

            Result.success(!query.isEmpty)
        } catch (e: Exception) {
            Log.e("FirebaseAuthRepo", "Erro ao verificar se o número de telefone existe", e)
            Result.failure(e)
        }
    }

    override fun getAuthState(): StateFlow<AuthState> = _authState.asStateFlow()

    override suspend fun verifyPhoneNumber(
        phoneNumber: String,
        activity: Activity,
        callbacks: PhoneAuthProvider.OnVerificationStateChangedCallbacks
    ) {
        val options = PhoneAuthOptions.newBuilder(firebaseAuth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    override suspend fun signInWithPhoneAuthCredential(
        credential: PhoneAuthCredential,
        username: String
    ): Result<Unit> {
        return try {
            val authResult = firebaseAuth.signInWithCredential(credential).await()
            val firebaseUser = authResult.user
            val isNewUser = authResult.additionalUserInfo?.isNewUser ?: false

            if (firebaseUser != null) {
                val usernameLowercase = username.lowercase()

                if (isNewUser) {

                    val usernameQuery = firestore.collection("users")
                        .whereEqualTo("username_lowercase", usernameLowercase) // USAR username_lowercase para a checagem
                        .get()
                        .await()

                    if (!usernameQuery.isEmpty) {
                        firebaseUser.delete().await()
                        return Result.failure(Exception("Username already taken. Please choose another one."))
                    }


                    val userDocument = mapOf(
                        "uid" to firebaseUser.uid,
                        "username" to username,
                        "username_lowercase" to usernameLowercase,
                        "phone" to firebaseUser.phoneNumber
                    )

                    val batch = firestore.batch()
                    val userRef = firestore.collection("users").document(firebaseUser.uid)
                    batch.set(userRef, userDocument)
                    batch.commit().await()
                }

                // Atualizar o AuthState com o usuário, incluindo o username_lowercase
                // Idealmente, buscaria o usuário do Firestore aqui para ter o objeto User completo,
                // mas para simplificar, vamos passar os dois campos para toDomainUser.
                 _authState.update {
                    it.copy(user = firebaseUser.toDomainUser(username, usernameLowercase))
                }

                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to sign in (Firebase Auth user is null)."))
            }
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Log.w("FirebaseAuthRepo", "Sign in failed: Invalid verification code.", e)
            Result.failure(Exception("The verification code is invalid."))
        } catch (e: Exception) {
            Log.e("FirebaseAuthRepo", "Sign in failed: ${e.message}", e)
            Result.failure(Exception("An unexpected error occurred during sign in: ${e.message}"))
        }
    }

    override fun signOut() {
        firebaseAuth.signOut()
    }
}

// Modificar FirebaseUser.toDomainUser para aceitar username_lowercase
private fun FirebaseUser.toDomainUser(
    usernameFromFirestore: String?,
    usernameLowercaseFromFirestore: String? // NOVO PARÂMETRO
): com.marcos.chatapplication.domain.model.User {
    return com.marcos.chatapplication.domain.model.User(
        uid = this.uid,
        username = usernameFromFirestore,
        username_lowercase = usernameLowercaseFromFirestore, // ATRIBUIR AO CAMPO
        phone = this.phoneNumber
        // O campo 'email' não está presente no FirebaseUser por padrão com Phone Auth,
        // então ele continuará null a menos que você o adicione de outra forma.
    )
}
