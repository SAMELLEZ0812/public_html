package com.willi.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.willi.app.data.models.CreatorProfile
import com.willi.app.databinding.ActivitySetupBinding
import com.willi.app.voice.SpeakerIdentification
import kotlinx.coroutines.launch

class SetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetupBinding
    private lateinit var speakerIdentification: SpeakerIdentification
    private val PERM_REQUEST = 100
    private var step = 0 // 0=nom, 1=voix, 2=api, 3=done

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.parseColor("#050005")

        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        speakerIdentification = SpeakerIdentification(this)

        requestPermissions()
        setupUI()
    }

    private fun requestPermissions() {
        val perms = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.INTERNET
        )
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERM_REQUEST)
        }
    }

    private fun setupUI() {
        showStep(0)

        binding.btnNext.setOnClickListener {
            when (step) {
                0 -> validateAndSaveName()
                1 -> startVoiceEnrollment()
                2 -> validateAndSaveApiKey()
            }
        }

        binding.btnSkipVoice.setOnClickListener {
            if (step == 1) {
                lifecycleScope.launch {
                    saveSetup("", binding.etName.text.toString().trim())
                }
            }
        }
    }

    private fun showStep(s: Int) {
        step = s
        binding.stepIndicator.text = "ÉTAPE ${s + 1}/3"

        when (s) {
            0 -> {
                binding.tvTitle.text = "INITIALISATION"
                binding.tvSubtitle.text = "Comment dois-je vous appeler ?"
                binding.layoutName.visibility = View.VISIBLE
                binding.layoutVoice.visibility = View.GONE
                binding.layoutApi.visibility = View.GONE
                binding.btnNext.text = "CONTINUER"
                binding.btnSkipVoice.visibility = View.GONE
            }
            1 -> {
                binding.tvTitle.text = "EMPREINTE VOCALE"
                binding.tvSubtitle.text = "Parlez pendant 5 secondes pour que je reconnaisse votre voix"
                binding.layoutName.visibility = View.GONE
                binding.layoutVoice.visibility = View.VISIBLE
                binding.layoutApi.visibility = View.GONE
                binding.btnNext.text = "ENREGISTRER MA VOIX"
                binding.btnSkipVoice.visibility = View.VISIBLE
            }
            2 -> {
                binding.tvTitle.text = "CLÉ API CLAUDE"
                binding.tvSubtitle.text = "Entrez votre clé API Anthropic pour activer mon cerveau"
                binding.layoutName.visibility = View.GONE
                binding.layoutVoice.visibility = View.GONE
                binding.layoutApi.visibility = View.VISIBLE
                binding.btnNext.text = "ACTIVER WILLI"
                binding.btnSkipVoice.visibility = View.GONE
            }
        }
    }

    private fun validateAndSaveName() {
        val name = binding.etName.text.toString().trim()
        if (name.isBlank()) {
            binding.etName.error = "Entrez votre prénom"
            return
        }
        showStep(1)
    }

    private fun startVoiceEnrollment() {
        val name = binding.etName.text.toString().trim()
        binding.btnNext.isEnabled = false
        binding.tvVoiceStatus.text = "Parlez maintenant..."
        binding.progressVoice.visibility = View.VISIBLE

        lifecycleScope.launch {
            val success = speakerIdentification.enrollCreator(name) { progress ->
                runOnUiThread {
                    binding.progressVoice.progress = (progress * 100).toInt()
                    binding.tvVoiceStatus.text = "Enregistrement ${(progress * 100).toInt()}%..."
                }
            }

            runOnUiThread {
                binding.progressVoice.visibility = View.GONE
                binding.btnNext.isEnabled = true
                if (success) {
                    binding.tvVoiceStatus.text = "Voix enregistrée avec succès !"
                    showStep(2)
                } else {
                    binding.tvVoiceStatus.text = "Échec. Réessayez."
                }
            }
        }
    }

    private fun validateAndSaveApiKey() {
        val apiKey = binding.etApiKey.text.toString().trim()
        if (apiKey.isBlank() || !apiKey.startsWith("sk-ant-")) {
            binding.etApiKey.error = "Clé API invalide (doit commencer par sk-ant-)"
            return
        }

        val name = binding.etName.text.toString().trim()
        lifecycleScope.launch {
            getSharedPreferences("willi_prefs", MODE_PRIVATE)
                .edit().putString("api_key", apiKey).apply()
            saveSetup(apiKey, name)
        }
    }

    private suspend fun saveSetup(apiKey: String, name: String) {
        val db = WilliApplication.instance.database
        val existing = db.creatorDao().get()
        db.creatorDao().save(
            (existing ?: CreatorProfile()).copy(name = name)
        )

        runOnUiThread {
            Toast.makeText(this, "Bonjour $name ! Je suis WILLI.", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}
