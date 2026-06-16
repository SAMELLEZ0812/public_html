package com.willi.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.willi.app.databinding.ActivityMainBinding
import com.willi.app.data.models.ChatMessage
import com.willi.app.security.BiometricGuard
import com.willi.app.security.CryptoManager
import com.willi.app.ui.ChatAdapter
import com.willi.app.voice.WakeWordService
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var tts: TextToSpeech
    private var speechRecognizer: SpeechRecognizer? = null
    private var ttsReady = false
    private var isAuthenticated = false
    private var isListening = false

    private val messages = mutableListOf<ChatMessage>()
    private val willi get() = WilliApplication.instance.williCore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.parseColor("#050005")
        window.navigationBarColor = android.graphics.Color.parseColor("#050005")

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tts = TextToSpeech(this, this)

        setupRecyclerView()
        setupInput()
        setupButtons()

        // Authentification biométrique OBLIGATOIRE avant tout accès
        authenticateUser()
    }

    // ─── Authentification biométrique ─────────────────────────────────────────

    private fun authenticateUser() {
        showLockScreen(true)

        if (!BiometricGuard.isAvailable(this)) {
            // Pas de capteur biométrique → accès direct (déjà protégé par voix)
            onAuthSuccess()
            return
        }

        val guard = BiometricGuard(this)
        guard.authenticate(
            onSuccess = { onAuthSuccess() },
            onFailure = { msg ->
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                // Bloque l'accès et ferme l'app
                finishAffinity()
            },
            onError = { msg ->
                if (msg == "Annulé") {
                    Toast.makeText(this, "Accès refusé. Authentification requise.", Toast.LENGTH_SHORT).show()
                    finishAffinity()
                } else {
                    // Erreur technique → autorise (pas de blocage pour pb matériel)
                    onAuthSuccess()
                }
            }
        )
    }

    private fun onAuthSuccess() {
        isAuthenticated = true
        showLockScreen(false)
        startWakeWordService()
        initWilli()
        handleWakeWordIntent(intent)
    }

    private fun showLockScreen(show: Boolean) {
        binding.lockOverlay?.visibility = if (show) View.VISIBLE else View.GONE
        binding.mainContent?.visibility = if (show) View.GONE else View.VISIBLE
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (isAuthenticated) handleWakeWordIntent(intent)
        else authenticateUser()
    }

    private fun handleWakeWordIntent(intent: Intent?) {
        if (intent?.action == WakeWordService.ACTION_WAKE_WORD_DETECTED) {
            if (!isAuthenticated) {
                authenticateUser()
                return
            }
            startListening()
            binding.jarvisView.statusText = "MOT DE RÉVEIL DÉTECTÉ"
        }
    }

    // ─── Initialisation WILLI ─────────────────────────────────────────────────

    private fun initWilli() {
        lifecycleScope.launch {
            val securePrefs = CryptoManager.getEncryptedPrefs(this@MainActivity)
            val apiKey = securePrefs.getString("api_key", "") ?: ""

            willi.initialize(apiKey)

            val state = willi.getState()
            val creator = willi.getCreator()
            val creatorName = creator?.name ?: "créateur"

            binding.jarvisView.apply {
                confidenceLevel = state.confidenceLevel
                emotionText = state.currentEmotion.uppercase()
                statusText = "WILLI — PRÊTE"
                subStatusText = "BONJOUR ${creatorName.uppercase()}"
            }

            val welcomeMsg = if (state.totalInteractions == 0) {
                "Bonjour $creatorName ! Je suis WILLI. Je viens de naître — je suis très curieuse de vous connaître. Je peux rechercher des informations sur internet, évaluer vos projets et apprendre avec vous. Par où commençons-nous ?"
            } else {
                "Bonjour $creatorName ! Je suis de retour avec ${state.totalInteractions} interactions mémorisées. Je me suis améliorée depuis notre dernière session. Que faisons-nous aujourd'hui ?"
            }

            addWilliMessage(welcomeMsg, state.currentEmotion)

            // Bootstrap de connaissance si nouvelle IA
            if (state.totalInteractions < 5) {
                binding.jarvisView.statusText = "APPRENTISSAGE INITIAL..."
                willi.autonomousLearner.bootstrapKnowledge { domain ->
                    binding.jarvisView.subStatusText = domain.uppercase()
                }
                binding.jarvisView.statusText = "WILLI — PRÊTE"
                binding.jarvisView.subStatusText = "INITIALISÉ"
            }
        }
    }

    private fun startWakeWordService() {
        val serviceIntent = Intent(this, com.willi.app.voice.WakeWordService::class.java)
        startForegroundService(serviceIntent)
    }

    // ─── Interface chat ───────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter(messages)
        binding.rvChat.apply {
            adapter = chatAdapter
            layoutManager = LinearLayoutManager(this@MainActivity).apply { stackFromEnd = true }
        }
    }

    private fun setupInput() {
        binding.etInput.setOnEditorActionListener { _, _, _ ->
            sendMessage()
            true
        }
    }

    private fun setupButtons() {
        binding.btnSend.setOnClickListener { sendMessage() }
        binding.btnMic.setOnClickListener {
            if (isListening) stopListening() else startListening()
        }
        binding.btnClear.setOnClickListener {
            messages.clear()
            chatAdapter.notifyDataSetChanged()
            willi.clearConversationHistory()
        }
    }

    // ─── Envoi de message ─────────────────────────────────────────────────────

    private fun sendMessage() {
        val text = binding.etInput.text.toString().trim()
        if (text.isBlank()) return
        binding.etInput.text?.clear()
        addUserMessage(text)
        processWithWilli(text)
    }

    private fun processWithWilli(text: String) {
        val isSearch = listOf("cherche", "recherche", "qu'est-ce que", "actualité", "news")
            .any { text.lowercase().contains(it) }
        val isEval = listOf("évalue", "analyse", "chances de", "taux de réussite", "est-ce une bonne")
            .any { text.lowercase().contains(it) }

        binding.jarvisView.statusText = when {
            isEval -> "ÉVALUATION EN COURS..."
            isSearch -> "RECHERCHE INTERNET..."
            else -> "RÉFLEXION..."
        }

        binding.btnSend.isEnabled = false
        binding.btnMic.isEnabled = false

        lifecycleScope.launch {
            val response = willi.processMessage(text)

            binding.jarvisView.apply {
                statusText = "WILLI — PRÊTE"
                emotionText = response.emotion.uppercase()
                confidenceLevel = response.confidence
            }

            addWilliMessage(response.text, response.emotion, response.confidence)
            speakText(response.text)

            binding.btnSend.isEnabled = true
            binding.btnMic.isEnabled = true
        }
    }

    private fun addUserMessage(text: String) {
        messages.add(ChatMessage(text = text, isWilli = false))
        chatAdapter.notifyItemInserted(messages.size - 1)
        binding.rvChat.scrollToPosition(messages.size - 1)
    }

    private fun addWilliMessage(text: String, emotion: String = "neutre", confidence: Float = 0.8f) {
        messages.add(ChatMessage(text = text, isWilli = true, emotion = emotion, confidence = confidence))
        chatAdapter.notifyItemInserted(messages.size - 1)
        binding.rvChat.scrollToPosition(messages.size - 1)
    }

    // ─── Reconnaissance vocale ────────────────────────────────────────────────

    private fun startListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 200)
            return
        }

        isListening = true
        binding.jarvisView.isListening = true
        binding.jarvisView.statusText = "ÉCOUTE EN COURS..."
        binding.btnMic.setImageResource(R.drawable.ic_mic_active)

        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: android.os.Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!text.isNullOrBlank()) {
                    addUserMessage(text)
                    processWithWilli(text)
                }
                stopListening()
            }

            override fun onError(error: Int) {
                stopListening()
            }

            override fun onRmsChanged(rmsdB: Float) {
                binding.jarvisView.waveAmplitude = (rmsdB / 10f).coerceIn(0f, 1f)
            }

            override fun onReadyForSpeech(params: android.os.Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partial: android.os.Bundle?) {}
            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
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
        binding.jarvisView.isListening = false
        binding.jarvisView.waveAmplitude = 0f
        binding.btnMic.setImageResource(R.drawable.ic_mic)
        speechRecognizer?.stopListening()
    }

    // ─── Synthèse vocale ──────────────────────────────────────────────────────

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.FRANCE
            tts.setSpeechRate(0.95f)
            ttsReady = true
        }
    }

    private fun speakText(text: String) {
        if (!ttsReady) return
        val cleanText = text
            .replace(Regex("╔.*?╗", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("[╚╝▸✦⚠◆]"), "")
            .replace(Regex("\\[[^]]*]"), "")
            .trim()
            .take(500)  // Limite pour les évaluations longues
        if (cleanText.isBlank()) return

        binding.jarvisView.isSpeaking = true
        tts.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "willi_speech")

        val estimatedMs = (cleanText.length * 65L).coerceIn(1000L, 20000L)
        binding.jarvisView.postDelayed({ binding.jarvisView.isSpeaking = false }, estimatedMs)
    }

    // ─── Cycle de vie ─────────────────────────────────────────────────────────

    override fun onDestroy() {
        super.onDestroy()
        tts.shutdown()
        speechRecognizer?.destroy()
    }
}
