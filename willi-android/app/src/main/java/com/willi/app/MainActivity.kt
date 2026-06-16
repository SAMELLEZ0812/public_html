package com.willi.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.willi.app.databinding.ActivityMainBinding
import com.willi.app.security.BiometricGuard
import com.willi.app.security.CryptoManager
import com.willi.app.voice.WakeWordService
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var tts: TextToSpeech
    private var speechRecognizer: SpeechRecognizer? = null
    private var ttsReady = false
    private var isAuthenticated = false
    private var isListening = false

    private val willi get() = WilliApplication.instance.williCore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor     = 0xFF000000.toInt()
        window.navigationBarColor = 0xFF000000.toInt()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tts = TextToSpeech(this, this)

        setupButtons()
        authenticateUser()
    }

    // ─── Authentification biométrique ─────────────────────────────────────────

    private fun authenticateUser() {
        showLock(true)

        if (!BiometricGuard.isAvailable(this)) {
            onAuthSuccess()
            return
        }

        BiometricGuard(this).authenticate(
            onSuccess = { onAuthSuccess() },
            onFailure = { finishAffinity() },
            onError   = { msg ->
                if (msg == "Annulé") finishAffinity() else onAuthSuccess()
            }
        )
    }

    private fun onAuthSuccess() {
        isAuthenticated = true
        showLock(false)
        startWakeWordService()
        initWilli()
        handleWakeWordIntent(intent)
    }

    private fun showLock(show: Boolean) {
        binding.lockOverlay.visibility  = if (show) View.VISIBLE else View.GONE
        binding.mainContent.visibility  = if (show) View.GONE   else View.VISIBLE
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (isAuthenticated) handleWakeWordIntent(intent) else authenticateUser()
    }

    private fun handleWakeWordIntent(intent: Intent?) {
        if (intent?.action == WakeWordService.ACTION_WAKE_WORD_DETECTED) {
            if (!isAuthenticated) { authenticateUser(); return }
            startListening()
            binding.jarvisView.statusText = "MOT DE RÉVEIL"
        }
    }

    // ─── Init WILLI ───────────────────────────────────────────────────────────

    private fun initWilli() {
        lifecycleScope.launch {
            val prefs  = CryptoManager.getEncryptedPrefs(this@MainActivity)
            val apiKey = prefs.getString("api_key", "") ?: ""

            willi.initialize(apiKey)

            val state   = willi.getState()
            val creator = willi.getCreator()
            val name    = creator?.name ?: "créateur"

            binding.jarvisView.apply {
                confidenceLevel = state.confidenceLevel
                emotionText     = state.currentEmotion.uppercase()
                statusText      = "WILLI"
            }

            val welcome = if (state.totalInteractions == 0) {
                "Bonjour $name. Je suis WILLI. Je viens de naître — je suis prêt à apprendre avec vous."
            } else {
                "Bonjour $name. De retour avec ${state.totalInteractions} interactions en mémoire. Que faisons-nous aujourd'hui ?"
            }

            showText(welcome)
            speakText(welcome)

            if (state.totalInteractions < 5) {
                binding.jarvisView.statusText = "APPRENTISSAGE..."
                willi.autonomousLearner.bootstrapKnowledge { domain ->
                    binding.jarvisView.emotionText = domain.uppercase()
                }
                binding.jarvisView.statusText = "WILLI"
            }
        }
    }

    private fun startWakeWordService() {
        startForegroundService(Intent(this, WakeWordService::class.java))
    }

    // ─── Boutons ──────────────────────────────────────────────────────────────

    private fun setupButtons() {
        binding.btnMic.setOnClickListener {
            if (!isAuthenticated) return@setOnClickListener
            if (isListening) stopListening() else startListening()
        }

        // Tap n'importe où sur le symbiote = parler
        binding.jarvisView.setOnClickListener {
            if (!isAuthenticated) return@setOnClickListener
            if (isListening) stopListening() else startListening()
        }
    }

    // ─── Traitement voix ──────────────────────────────────────────────────────

    private fun startListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 200)
            return
        }

        isListening = true
        binding.jarvisView.isListening  = true
        binding.jarvisView.statusText   = "ÉCOUTE..."
        binding.btnMic.setImageResource(R.drawable.ic_mic_active)

        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!text.isNullOrBlank()) {
                    stopListening()
                    processMessage(text)
                } else {
                    stopListening()
                }
            }
            override fun onError(error: Int) { stopListening() }
            override fun onRmsChanged(rmsdB: Float) {
                binding.jarvisView.waveAmplitude = (rmsdB / 10f).coerceIn(0f, 1f)
            }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partial: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer?.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
        )
    }

    private fun stopListening() {
        isListening = false
        binding.jarvisView.isListening   = false
        binding.jarvisView.waveAmplitude = 0f
        binding.jarvisView.statusText    = "WILLI"
        binding.btnMic.setImageResource(R.drawable.ic_mic)
        speechRecognizer?.stopListening()
    }

    private fun processMessage(text: String) {
        binding.jarvisView.statusText = "RÉFLEXION..."

        lifecycleScope.launch {
            val response = willi.processMessage(text)

            binding.jarvisView.apply {
                statusText      = "WILLI"
                emotionText     = response.emotion.uppercase()
                confidenceLevel = response.confidence
            }

            showText(response.text)
            speakText(response.text)
        }
    }

    private fun showText(text: String) {
        binding.tvWilliText.apply {
            this.text    = text.take(400)
            visibility   = View.VISIBLE
        }
    }

    // ─── Synthèse vocale (voix masculine naturelle) ───────────────────────────

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return

        tts.language = Locale.FRANCE
        tts.setPitch(0.82f)        // Voix grave/masculine
        tts.setSpeechRate(0.88f)   // Débit naturel (moins robotique)

        // Cherche une voix masculine française
        val voices = tts.voices
        if (voices != null) {
            val maleFrench = voices.filter { v ->
                v.locale.language == "fr" &&
                !v.name.contains("female", ignoreCase = true) &&
                !v.name.contains("femme",  ignoreCase = true) &&
                !v.features.contains(android.speech.tts.TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
            }.minByOrNull { v ->
                if (v.isNetworkConnectionRequired) 1 else 0
            }
            maleFrench?.let { tts.voice = it }
        }

        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?)   { runOnUiThread { binding.jarvisView.isSpeaking = true  } }
            override fun onDone(utteranceId: String?)    { runOnUiThread { binding.jarvisView.isSpeaking = false } }
            override fun onError(utteranceId: String?)   { runOnUiThread { binding.jarvisView.isSpeaking = false } }
        })

        ttsReady = true
    }

    private fun speakText(text: String) {
        if (!ttsReady) return
        val clean = text
            .replace(Regex("╔.*?╗", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("[╚╝▸✦⚠◆║═]"), "")
            .replace(Regex("\\[[^]]*]"), "")
            .replace(Regex("\\*+"), "")
            .trim()
            .take(600)
        if (clean.isBlank()) return

        tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "willi_speech")
    }

    // ─── Cycle de vie ─────────────────────────────────────────────────────────

    override fun onDestroy() {
        super.onDestroy()
        tts.shutdown()
        speechRecognizer?.destroy()
    }
}
