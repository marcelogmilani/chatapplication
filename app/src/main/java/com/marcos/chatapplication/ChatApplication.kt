package com.marcos.chatapplication

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ChatApplication : Application() {

    companion object {
        private const val TAG = "ChatApplication" // Mantenha esta TAG
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: ChatApplication - BuildConfig.DEBUG = ${BuildConfig.DEBUG}") // Log para verificar BuildConfig.DEBUG

        try {
            initializeFirebaseSafely()
        } catch (e: Exception) {
            Log.e(TAG, "Erro GERAL na inicialização do Firebase em onCreate: ${e.message}", e)
        }
    }

    private fun initializeFirebaseSafely() {
        try {
            Log.d(TAG, "initializeFirebaseSafely: INICIO")
            FirebaseApp.initializeApp(this)
            Log.d(TAG, "initializeFirebaseSafely: FirebaseApp.initializeApp SUCESSO")

            val firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                .build()

            try {
                FirebaseFirestore.getInstance().firestoreSettings = firestoreSettings
                Log.d(TAG, "initializeFirebaseSafely: Firestore configurado com sucesso")
            } catch (e: Exception) {
                Log.e(TAG, "initializeFirebaseSafely: Erro ao configurar Firestore: ${e.message}", e)
            }

            Log.d(TAG, "initializeFirebaseSafely: Configurando App Check...")
            try {
                val firebaseAppCheck = FirebaseAppCheck.getInstance()
                Log.d(TAG, "initializeFirebaseSafely: FirebaseAppCheck.getInstance() SUCESSO. Instância: $firebaseAppCheck")

                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "initializeFirebaseSafely: MODO DEBUG DETECTADO (BuildConfig.DEBUG == true)")
                    Log.d(TAG, "initializeFirebaseSafely: Tentando instalar DebugAppCheckProviderFactory...")
                    firebaseAppCheck.installAppCheckProviderFactory(
                        DebugAppCheckProviderFactory.getInstance()
                    )
                    Log.d(TAG, "initializeFirebaseSafely: installAppCheckProviderFactory(Debug) CHAMADO.")
                    Log.d(TAG, "initializeFirebaseSafely: App Check configurado em modo DEBUG")
                } else {
                    Log.d(TAG, "initializeFirebaseSafely: MODO PRODUÇÃO DETECTADO (BuildConfig.DEBUG == false)")
                    Log.d(TAG, "initializeFirebaseSafely: Tentando instalar PlayIntegrityAppCheckProviderFactory...")
                    firebaseAppCheck.installAppCheckProviderFactory(
                        PlayIntegrityAppCheckProviderFactory.getInstance()
                    )
                    Log.d(TAG, "initializeFirebaseSafely: installAppCheckProviderFactory(PlayIntegrity) CHAMADO.")
                    Log.d(TAG, "initializeFirebaseSafely: App Check configurado em modo PRODUÇÃO")
                }
            } catch (e: Exception) {
                Log.e(TAG, "initializeFirebaseSafely: Erro CRÍTICO ao configurar o App Check: ${e.message}", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "initializeFirebaseSafely: Erro CRÍTICO GERAL na inicialização do Firebase: ${e.message}", e)
        }
        Log.d(TAG, "initializeFirebaseSafely: FIM")
    }
}