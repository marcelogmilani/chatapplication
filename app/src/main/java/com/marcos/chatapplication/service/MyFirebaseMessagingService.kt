package com.marcos.chatapplication.service

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.marcos.chatapplication.domain.model.MessageStatus

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "Novo token gerado: $token")
        sendTokenToServer(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d("FCM", "Mensagem recebida de: ${remoteMessage.from}")

        remoteMessage.data.let { data ->
            val conversationId = data["conversationId"]
            val messageId = data["messageId"]

            if (!conversationId.isNullOrBlank() && !messageId.isNullOrBlank()) {
                Log.d("FCM", "Confirmando entrega para a mensagem: $messageId na conversa $conversationId")

                Firebase.firestore
                    .collection("conversations").document(conversationId)
                    .collection("messages").document(messageId)
                    .update("status", MessageStatus.DELIVERED)
                    .addOnSuccessListener { Log.d("FCM", "Status da mensagem atualizado para DELIVERED no Firestore.") }
                    .addOnFailureListener { e -> Log.w("FCM", "Erro ao atualizar o status da mensagem.", e) }
            }
        }
    }

    private fun sendTokenToServer(token: String) {
        val userId = Firebase.auth.currentUser?.uid
        if (userId != null) {
            Firebase.firestore.collection("users").document(userId)
                .update("fcmToken", token)
                .addOnSuccessListener { Log.d("FCM", "Token atualizado no Firestore.") }
                .addOnFailureListener { e -> Log.w("FCM", "Erro ao atualizar o token.", e) }
        }
    }
}