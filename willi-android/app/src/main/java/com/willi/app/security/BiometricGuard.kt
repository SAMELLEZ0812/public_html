package com.willi.app.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Authentification biométrique — empreinte digitale / visage.
 * Bloque l'accès à WILLI si la biométrie échoue ou si ce n'est pas le créateur.
 */
class BiometricGuard(private val activity: FragmentActivity) {

    companion object {
        fun isAvailable(context: Context): Boolean {
            val manager = BiometricManager.from(context)
            return manager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG
            ) == BiometricManager.BIOMETRIC_SUCCESS
        }
    }

    fun authenticate(
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationFailed() {
                onFailure("Biométrie non reconnue. WILLI n'obéit qu'à son créateur.")
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                when (errorCode) {
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON -> {
                        // L'utilisateur a annulé — on laisse passer si voix ok
                        onError("Annulé")
                    }
                    BiometricPrompt.ERROR_NO_BIOMETRICS -> {
                        // Pas de biométrie configurée — accès via voix uniquement
                        onSuccess()
                    }
                    else -> onError("Erreur biométrique ($errorCode): $errString")
                }
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("WILLI — Vérification d'identité")
            .setSubtitle("Seul le créateur peut accéder à WILLI")
            .setDescription("Posez votre doigt sur le capteur ou regardez la caméra")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        prompt.authenticate(promptInfo)
    }
}
