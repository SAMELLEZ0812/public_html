package com.willi.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.willi.app.databinding.ActivityMainBinding
import com.willi.app.data.models.ChatMessage
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

    private val messages = mutableListOf<ChatMessage>()
    private val willi get() = WilliApplication.instance.williCore
    private var isListening = false

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
        startWakeWordService()
        initWilli()
        handleWakeWordIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleWakeWordIntent(intent)
    }

    private fun handleWakeWordIntent(intent: Intent?) {
        if (intent?.action == WakeWordService.ACTION_WAKE_WORD_DETECTED) {
            startListening()
            binding.jarvisView.statusText = "MOT DE RÉVEIL DÉTECTÉ"
        }
    }

    // ─── Initialisation ───────────────────────────────────────────────────────

    private fun initWilli() {
        lifecycleScope.launch {
            val apiKey = getSharedPreferences("willi_prefs", MODE_PRIVATE)
                .getString("api_key", "") ?: ""

            willi.initialize(apiKey)

            val state = willi.getState()
            val creator = willi.getCreator()
            val creatorName = creator?.name ?: "créateur"

            binding.jarvisView.confidenceLevel = state.confidenceLevel
            binding.jarvisView.emotionText = state.currentEmotion.uppercase()
            binding.jarvisView.statusText = "WILLI — PRÊTE"
            binding.jarvisView.subStatusText = "BONJOUR ${creatorName.uppercase()}"

            // Message de bienvenue
            val welcomeMsg = if (state.totalInteractions == 0) {
                "Bonjour $creatorName ! Je suis WILLI. Je viens de naître et je suis très curieuse de vous connaître. Comment puis-je vous aider ?"
            } else {
                "Bonjour $creatorName ! Je suis de retour. J'ai mémorisé nos ${state.totalInteractions} échanges précédents. Que faisons-nous aujourd'hui ?"
            }

            addWilliMessage(welcomeMsg, state.currentEmotion)
        }
    }

    private fun startWakeWordService() {
        val serviceIntent = Intent(this, WakeWordService::class.java)
        startForegroundService(serviceIntent)
    }

    // ─── Interface chat ───────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter(messages)
        binding.rvChat.apply {
            adapter = chatAdapter
            layoutManager = LinearLayoutManager(this@MainActivity).apply {
                stackFromEnd = true
            }
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
        binding.jarvisView.statusText = "TRAITEMENT EN COURS..."
        binding.btnSend.isEnabled = false
        binding.btnMic.isEnabled = false

        lifecycleScope.launch {
            val response = willi.processMessage(text)

            binding.jarvisView.apply {
                statusText = "WILLI — PRÊTE"
                emotionText = response.emotion.uppercase()
                confidenceLevel = response.confidence
            }

            addWilliMessage(response.text, response.emotion)
            speakText(response.text)

            binding.btnSend.isEnabled = true
            binding.btnMic.isEnabled = true
        }
    }

    private fun addUserMessage(text: String) {
        val msg = ChatMessage(text = text, isWilli = false)
        messages.add(msg)
        chatAdapter.notifyItemInserted(messages.size - 1)
        binding.rvChat.scrollToPosition(messages.size - 1)
    }

    private fun addWilliMessage(text: String, emotion: String = "neutre", confidence: Float = 0.8f) {
        val msg = ChatMessage(text = text, isWilli = true, emotion = emotion, confidence = confidence)
        messages.add(msg)
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
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()
                if (!text.isNullOrBlank()) {
                    addUserMessage(text)
                    processWithWilli(text)
                }
                stopListening()
            }

            override fun onError(error: Int) {
                stopListening()
                if (error != SpeechRecognizer.ERROR_NO_MATCH) {
                    Toast.makeText(this@MainActivity, "Erreur micro: $error", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onReadyForSpeech(params: android.os.Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                binding.jarvisView.waveAmplitude = (rmsdB / 10f).coerceIn(0f, 1f)
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partial: android.os.Bundle?) {}
            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        speechRecognizer?.startListening(intent)
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

        val cleanText = text.replace(Regex("\\[[^]]*]"), "").trim()
        binding.jarvisView.isSpeaking = true

        tts.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "willi_speech")

        // Arrête l'animation de parole après estimation du temps
        val estimatedMs = (cleanText.length * 65L).coerceAtLeast(1000L)
        binding.jarvisView.postDelayed({
            binding.jarvisView.isSpeaking = false
        }, estimatedMs)
    }

    // ─── Cycle de vie ─────────────────────────────────────────────────────────

    override fun onDestroy() {
        super.onDestroy()
        tts.shutdown()
        speechRecognizer?.destroy()
    }
}
