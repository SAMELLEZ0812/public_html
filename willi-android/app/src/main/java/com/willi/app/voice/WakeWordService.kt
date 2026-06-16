package com.willi.app.voice

import android.app.*
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import com.willi.app.MainActivity
import com.willi.app.R
import com.willi.app.WilliApplication
import kotlinx.coroutines.*

/**
 * Service de fond qui écoute en permanence le mot de réveil "dis-moi Willy".
 * Utilise la reconnaissance vocale Android pour détecter la phrase d'activation.
 */
class WakeWordService : Service() {

    companion object {
        const val ACTION_WAKE_WORD_DETECTED = "com.willi.app.WAKE_WORD_DETECTED"
        const val WAKE_PHRASE = "dis-moi willy"
        const val NOTIFICATION_ID = 1001
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isListening = false
    private var speakerVerifier: SpeakerIdentification? = null
    private var lastWakeWordTime = 0L

    override fun onCreate() {
        super.onCreate()
        speakerVerifier = SpeakerIdentification(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        startListening()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopListening()
        serviceScope.cancel()
    }

    // ─── Écoute du mot de réveil ──────────────────────────────────────────────

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(createWakeListener())
        restartListening()
    }

    private fun restartListening() {
        if (!isListening) {
            isListening = true
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            }
            speechRecognizer?.startListening(intent)
        }
    }

    private fun stopListening() {
        isListening = false
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    private fun createWakeListener() = object : RecognitionListener {
        override fun onResults(results: android.os.Bundle?) {
            isListening = false
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val detected = matches?.any { it.lowercase().contains(WAKE_PHRASE) } == true

            if (detected) {
                val now = System.currentTimeMillis()
                if (now - lastWakeWordTime > 3000) { // Anti-double déclenchement
                    lastWakeWordTime = now
                    onWakeWordDetected()
                }
            }

            // Relance l'écoute immédiatement
            serviceScope.launch {
                kotlinx.coroutines.delay(500)
                restartListening()
            }
        }

        override fun onPartialResults(partial: android.os.Bundle?) {
            val matches = partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (matches?.any { it.lowercase().contains(WAKE_PHRASE) } == true) {
                val now = System.currentTimeMillis()
                if (now - lastWakeWordTime > 3000) {
                    lastWakeWordTime = now
                    onWakeWordDetected()
                }
            }
        }

        override fun onError(error: Int) {
            isListening = false
            serviceScope.launch {
                kotlinx.coroutines.delay(1000)
                restartListening()
            }
        }

        override fun onReadyForSpeech(params: android.os.Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
    }

    private fun onWakeWordDetected() {
        // Ouvre MainActivity avec l'intent de réveil
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_WAKE_WORD_DETECTED
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(launchIntent)

        // Vibre pour feedback
        val vibrator = getSystemService(android.os.Vibrator::class.java)
        vibrator?.vibrate(android.os.VibrationEffect.createOneShot(200, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
    }

    // ─── Notification persistante ─────────────────────────────────────────────

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, WilliApplication.CHANNEL_WAKE)
            .setContentTitle("WILLI — En écoute")
            .setContentText("Dites « dis-moi Willy » pour m'activer")
            .setSmallIcon(R.drawable.ic_willi_notif)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
