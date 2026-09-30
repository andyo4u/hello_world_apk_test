package app.quarterhour.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import app.quarterhour.core.budget.Signer
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val ANDROID_KEYSTORE = "AndroidKeyStore"

private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

/** HMAC-SHA256 with a non-exportable Keystore key; used to detect edits to the budget file. */
class KeystoreSigner(private val alias: String = "qh_budget_hmac") : Signer {

    private fun key(): SecretKey {
        (keyStore().getKey(alias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, ANDROID_KEYSTORE)
        gen.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN).build())
        return gen.generateKey()
    }

    override fun sign(payload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(key())
        return Base64.getEncoder().encodeToString(mac.doFinal(payload.toByteArray()))
    }

    override fun verify(payload: String, signature: String): Boolean =
        MessageDigest.isEqual(sign(payload).toByteArray(), signature.toByteArray())
}

/** AES-GCM with a Keystore key, for social account tokens. */
class KeystoreCipher(private val alias: String = "qh_secrets_aes") {

    private fun key(): SecretKey {
        (keyStore().getKey(alias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val out = cipher.iv + cipher.doFinal(plain.toByteArray())
        return Base64.getEncoder().encodeToString(out)
    }

    fun decrypt(encoded: String): String? = runCatching {
        val bytes = Base64.getDecoder().decode(encoded)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, 12))
        String(cipher.doFinal(bytes, 12, bytes.size - 12))
    }.getOrNull()
}
