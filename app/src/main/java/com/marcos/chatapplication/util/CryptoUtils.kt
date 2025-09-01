// CryptoUtils.kt
package com.marcos.chatapplication.util

import android.util.Base64
import com.google.firebase.auth.FirebaseAuth
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CryptoUtils @Inject constructor(private val cryptoManager: CryptoManager) {

    companion object {
        private const val RSA_MODE = "RSA/ECB/PKCS1Padding"
    }

    fun e2eeEncrypt(plainText: String, publicKeyString: String): String {
        return try {
            val publicKey = stringToPublicKey(publicKeyString)
            cryptoManager.encrypt(plainText, publicKey)
        } catch (e: Exception) {
            throw RuntimeException("Falha ao criptografar mensagem", e)
        }
    }

    fun e2eeDecrypt(encryptedText: String, alias: String): String {
        return try {
            val privateKey = cryptoManager.getPrivateKey(alias)
                ?: throw Exception("Chave privada não encontrada")
            cryptoManager.decrypt(encryptedText, privateKey)
        } catch (e: Exception) {
            throw RuntimeException("Falha ao descriptografar mensagem", e)
        }
    }

    fun getMyPublicKey(): String {
        val alias = getCurrentUserAlias()
        cryptoManager.generateKeyPair(alias)
        return cryptoManager.getPublicKeyString(alias)
            ?: throw Exception("Não foi possível obter chave pública")
    }

    private fun stringToPublicKey(publicKeyString: String): PublicKey {
        val keyBytes = Base64.decode(publicKeyString, Base64.DEFAULT)
        val keySpec = X509EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("RSA")
        return keyFactory.generatePublic(keySpec)
    }

    private fun getCurrentUserAlias(): String {
        // Você precisará obter o UID do usuário atual aqui
        // Por exemplo, usando FirebaseAuth.getInstance().currentUser?.uid
        return "user_key_${FirebaseAuth.getInstance().currentUser?.uid}"
    }
}