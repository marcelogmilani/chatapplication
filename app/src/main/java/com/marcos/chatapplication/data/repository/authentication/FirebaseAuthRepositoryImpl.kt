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
import com.google.firebase.firestore.ListenerRegistration // NOVO IMPORT
import com.marcos.chatapplication.domain.contracts.AuthRepository
import com.marcos.chatapplication.domain.contracts.AuthState
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
    private var userDocumentListener: ListenerRegistration? = null // NOVO

    init {
        firebaseAuth.addAuthStateListener { auth ->
            val firebaseUser = auth.currentUser
            if (firebaseUser != null) {
                // Remove o listener anterior, se existir, para evitar múltiplos listeners no mesmo documento
                userDocumentListener?.remove()
                userDocumentListener = firestore.collection("users").document(firebaseUser.uid)
                    .addSnapshotListener { documentSnapshot, firebaseFirestoreException ->
                        if (firebaseFirestoreException != null) {
                            Log.w("FirebaseAuthRepo", "Listen error no documento do usuário", firebaseFirestoreException)
                            _authState.update {
                                it.copy(
                                    // Mesmo em erro de listen, tentamos criar um usuário com o que temos do FirebaseUser
                                    user = firebaseUser.toDomainUser(null, null, null, null, null),
                                    isInitialLoading = false
                                )
                            }
                            return@addSnapshotListener
                        }

                        if (documentSnapshot != null && documentSnapshot.exists()) {
                            val userFromFirestore = documentSnapshot.toObject(User::class.java)
                            _authState.update {
                                it.copy(
                                    user = firebaseUser.toDomainUser(
                                        usernameFromFirestore = userFromFirestore?.username,
                                        usernameLowercaseFromFirestore = userFromFirestore?.username_lowercase,
                                        profilePictureUrlFromFirestore = userFromFirestore?.profilePictureUrl,
                                        emailFromFirestore = userFromFirestore?.email,
                                        birthDateFromFirestore = userFromFirestore?.birthDate
                                    ),
                                    isInitialLoading = false
                                )
                            }
                        } else {
                            Log.w("FirebaseAuthRepo", "Documento do usuário não encontrado ou nulo no snapshot listener.")
                            // Usuário autenticado mas sem documento no Firestore (pode acontecer em casos raros ou deleção)
                            _authState.update {
                                it.copy(
                                    user = firebaseUser.toDomainUser(null, null, null, null, null), // Dados do Firestore são nulos
                                    isInitialLoading = false
                                )
                            }
                        }
                    }
            } else {
                // Usuário fez logout ou não está autenticado
                userDocumentListener?.remove() // Remove o listener ao fazer logout
                userDocumentListener = null
                _authState.update { it.copy(user = null, isInitialLoading = false) }
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
                    val userDocument = mapOf(
                        "uid" to firebaseUser.uid,
                        "username" to username,
                        "username_lowercase" to usernameLowercase,
                        "phone" to firebaseUser.phoneNumber,
                        "profilePictureUrl" to null,
                        "email" to null,
                        "birthDate" to null
                    )

                    val usernameQuery = firestore.collection("users")
                        .whereEqualTo("username_lowercase", usernameLowercase)
                        .get()
                        .await()

                    if (!usernameQuery.isEmpty) {
                        firebaseUser.delete().await()
                        return Result.failure(Exception("Username already taken. Please choose another one."))
                    }

                    val userRef = firestore.collection("users").document(firebaseUser.uid)
                    userRef.set(userDocument).await()

                    // O snapshot listener no init deve pegar essa atualização,
                    // mas podemos emitir um estado inicial aqui também para garantir.
                    // No entanto, para evitar emissões duplas, confiaremos no listener.
                    // Se houver um delay perceptível, podemos reconsiderar uma emissão imediata aqui.
                }
                // Para usuários existentes ou novos, o AuthState será atualizado pelo listener no init.
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
        // O AuthStateListener no init irá lidar com a remoção do snapshotListener
        // e a atualização do _authState.
        firebaseAuth.signOut()
    }
}

// A função toDomainUser permanece como na última correção, pois está correta.
private fun FirebaseUser.toDomainUser(
    usernameFromFirestore: String?,
    usernameLowercaseFromFirestore: String?,
    profilePictureUrlFromFirestore: String?,
    emailFromFirestore: String?,
    birthDateFromFirestore: String?
): com.marcos.chatapplication.domain.model.User {
    return com.marcos.chatapplication.domain.model.User(
        uid = this.uid,
        username = usernameFromFirestore,
        username_lowercase = usernameLowercaseFromFirestore,
        profilePictureUrl = profilePictureUrlFromFirestore,
        phone = this.phoneNumber,
        email = emailFromFirestore,
        birthDate = birthDateFromFirestore,
        fcmToken = null
    )
}
