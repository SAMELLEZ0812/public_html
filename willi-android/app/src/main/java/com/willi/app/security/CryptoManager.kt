package com.willi.app.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.util.Base64

/**
 * Chiffrement AES-256-GCM via Android Keystore.
 * Les clés ne quittent jamais le Keystore matériel du téléphone.
 */
object CryptoManager {

    private const val KEYSTORE_ALIAS = "WilliMasterKey"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val AES_GCM = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128

    // ─── Clé secrète dans Android Keystore ───────────────────────────────────

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).also { it.load(null) }

        keyStore.getKey(KEYSTORE_ALIAS, null)?.let { return it as SecretKey }

        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        keyGen.init(
            KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false) // auth gérée par BiometricGuard
                .build()
        )
        return keyGen.generateKey()
    }

    // ─── Chiffrement ─────────────────────────────────────────────────────────

    fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())

        val iv = cipher.iv
        val cipherBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        // Format: base64(iv) + "." + base64(ciphertext)
        return Base64.encodeToString(iv, Base64.NO_WRAP) + "." +
               Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
    }

    fun decrypt(encrypted: String): String {
        val parts = encrypted.split(".")
        if (parts.size != 2) return encrypted

        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val cipherBytes = Base64.decode(parts[1], Base64.NO_WRAP)

        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH, iv))

        return String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
    }

    // ─── SharedPreferences chiffrées ──────────────────────────────────────────

    fun getEncryptedPrefs(context: Context): android.content.SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            "willi_secure_vault",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // ─── Clé de chiffrement base de données SQLCipher ────────────────────────

    fun getDatabasePassphrase(context: Context): ByteArray {
        val prefs = getEncryptedPrefs(context)
        val stored = prefs.getString("db_passphrase", null)

        if (stored != null) {
            return Base64.decode(stored, Base64.NO_WRAP)
        }

        // Génère une clé de 32 bytes aléatoires
        val key = ByteArray(32)
        java.security.SecureRandom().nextBytes(key)
        prefs.edit().putString("db_passphrase", Base64.encodeToString(key, Base64.NO_WRAP)).apply()
        return key
    }
}
