// MyFirebaseMessagingService.kt
package com.marcos.chatapplication.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.marcos.chatapplication.MainActivity
import com.marcos.chatapplication.R
import com.marcos.chatapplication.domain.model.MessageStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "Novo token gerado: $token")
        sendTokenToServer(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        Log.d("FCM", "Mensagem recebida: ${remoteMessage.data}")

        // Verificar se a mensagem contém dados de notificação
        if (remoteMessage.data.isNotEmpty()) {
            handleDataMessage(remoteMessage.data)
        }

        // Verificar se contém notificação (para quando app está em background)
        remoteMessage.notification?.let { notification ->
            val title = notification.title ?: "Nova mensagem"
            val body = notification.body ?: "Você tem uma nova mensagem"
            showNotification(title, body, remoteMessage.data)
        }
    }

    private fun handleDataMessage(data: Map<String, String>) {
        val messageId = data["messageId"]
        val conversationId = data["conversationId"]
        val senderId = data["senderId"]
        val senderName = data["senderName"] ?: "Alguém"
        val messageText = data["message"] ?: "Nova mensagem"
        val messageType = data["type"] ?: "text"

        Log.d("FCM", "Notificação de dados recebida para a mensagem: $messageId")

        // Mostrar notificação
        showNotification(senderName, messageText, data)

        // Atualizar status da mensagem se necessário
        messageId?.let {
            CoroutineScope(Dispatchers.IO).launch {
                updateMessageStatus(it, MessageStatus.DELIVERED)
            }
        }
    }

    private fun showNotification(title: String, message: String, data: Map<String, String>) {
        val conversationId = data["conversationId"]
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            conversationId?.let {
                putExtra("conversationId", it)
                putExtra("openChat", true)
            }
            putExtra("fromNotification", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = "chat_messages_channel"
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val vibrationPattern = longArrayOf(0, 250, 250, 250)

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setVibrate(vibrationPattern)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)

        // Para mensagens de imagem
        if (data["type"] == "image") {
            notificationBuilder.setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("📷 $message")
            )
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Criar canal de notificação (obrigatório para Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Mensagens de Chat",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificações de mensagens do chat"
                enableVibration(true)
                setVibrationPattern(vibrationPattern) // CORREÇÃO AQUI
                setSound(defaultSoundUri, null)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Usar ID único para cada notificação
        val notificationId = System.currentTimeMillis().toInt()
        notificationManager.notify(notificationId, notificationBuilder.build())

        Log.d("FCM", "Notificação exibida: $title - $message")
    }

    private suspend fun updateMessageStatus(messageId: String, status: String) {
        try {
            // Encontrar a mensagem no Firestore e atualizar o status
            val query = Firebase.firestore.collectionGroup("messages")
                .whereEqualTo("id", messageId)
                .get()
                .await()

            if (!query.isEmpty) {
                val document = query.documents.first()
                document.reference.update("status", status).await()
                Log.d("FCM", "Status da mensagem $messageId atualizado para: $status")
            }
        } catch (e: Exception) {
            Log.w("FCM", "Erro ao atualizar status da mensagem", e)
        }
    }

    private fun sendTokenToServer(token: String) {
        val userId = Firebase.auth.currentUser?.uid
        if (userId != null) {
            Firebase.firestore.collection("users").document(userId)
                .update("fcmToken", token)
                .addOnSuccessListener { Log.d("FCM", "Token atualizado no Firestore.") }
                .addOnFailureListener { e -> Log.w("FCM", "Erro ao atualizar o token.", e) }
        } else {
            // Se não há usuário logado, salvar token temporariamente
            saveTokenForLater(token)
        }
    }

    private fun saveTokenForLater(token: String) {
        val sharedPrefs = getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().putString("pending_token", token).apply()
        Log.d("FCM", "Token salvo temporariamente para atualização posterior")
    }

    fun sendPendingTokenIfNeeded(userId: String) {
        val sharedPrefs = getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
        val pendingToken = sharedPrefs.getString("pending_token", null)

        pendingToken?.let { token ->
            Firebase.firestore.collection("users").document(userId)
                .update("fcmToken", token)
                .addOnSuccessListener {
                    Log.d("FCM", "Token pendente atualizado para o usuário: $userId")
                    sharedPrefs.edit().remove("pending_token").apply()
                }
                .addOnFailureListener { e ->
                    Log.w("FCM", "Erro ao atualizar token pendente", e)
                }
        }
    }
}