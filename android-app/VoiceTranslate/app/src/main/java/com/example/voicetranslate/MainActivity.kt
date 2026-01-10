package com.example.voicetranslate

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.voicetranslate.databinding.ActivityMainBinding

data class Language(val name: String, val code: String) {
    override fun toString(): String = name
}

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val languages = listOf(
        Language("English", "en"),
        Language("Marathi", "mr"),
        Language("Hindi", "hi"),
        Language("Bengali", "bn"),
        Language("Gujarati", "gu"),
        Language("Kannada", "kn"),
        Language("Malayalam", "ml"),
        Language("Punjabi", "pa"),
        Language("Tamil", "ta"),
        Language("Telugu", "te")
    )
    
    private var sourceLanguageCode: String? = null
    private var targetLanguageCode: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupLanguageDropdowns()
        setupBackendUrl()

        binding.btnStartCall.setOnClickListener {
            val url = binding.etBackendUrl.text.toString().trim()
            val callId = binding.etCallId.text.toString().trim()

            if (url.isEmpty()) {
                binding.etBackendUrl.error = "Enter backend URL"
                return@setOnClickListener
            }
            if (callId.isEmpty()) {
                binding.etCallId.error = "Enter Call ID"
                return@setOnClickListener
            }
            if (sourceLanguageCode == null || targetLanguageCode == null) {
                Toast.makeText(this, "Please select both languages", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Save URL for next time
            getSharedPreferences("app_prefs", MODE_PRIVATE).edit()
                .putString("backend_url", url)
                .apply()

            val intent = Intent(this, CallActivity::class.java).apply {
                putExtra("BACKEND_URL", url)
                putExtra("CALL_ID", callId)
                putExtra("SOURCE_LANG", sourceLanguageCode)
                putExtra("TARGET_LANG", targetLanguageCode)
            }
            startActivity(intent)
        }
    }

    private fun setupLanguageDropdowns() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, languages)
        
        binding.actvSourceLanguage.setAdapter(adapter)
        binding.actvSourceLanguage.setOnItemClickListener { parent, _, position, _ ->
            val selectedLanguage = parent.adapter.getItem(position) as Language
            sourceLanguageCode = selectedLanguage.code
        }

        binding.actvTargetLanguage.setAdapter(adapter)
        binding.actvTargetLanguage.setOnItemClickListener { parent, _, position, _ ->
            val selectedLanguage = parent.adapter.getItem(position) as Language
            targetLanguageCode = selectedLanguage.code
        }
    }

    private fun setupBackendUrl() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val savedUrl = prefs.getString("backend_url", "192.168.1.10:8000")
        binding.etBackendUrl.setText(savedUrl)
    }
}