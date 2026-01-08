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
        Language("Assamese", "as"),
        Language("Bengali", "bn"),
        Language("Bodo", "brx"),
        Language("Dogri", "doi"),
        Language("English", "en"),
        Language("Gujarati", "gu"),
        Language("Hindi", "hi"),
        Language("Kannada", "kn"),
        Language("Konkani", "kok"),
        Language("Kashmiri", "ks"),
        Language("Maithili", "mai"),
        Language("Malayalam", "ml"),
        Language("Manipuri", "mni"),
        Language("Marathi", "mr"),
        Language("Nepali", "ne"),
        Language("Odia", "or"),
        Language("Punjabi", "pa"),
        Language("Sanskrit", "sa"),
        Language("Santali", "sat"),
        Language("Sindhi", "sd"),
        Language("Tamil", "ta"),
        Language("Telugu", "te"),
        Language("Urdu", "ur")
    )
    private var selectedLanguageCode: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupLanguageDropdown()
        setupBackendUrl()

        binding.btnStartCall.setOnClickListener {
            val url = binding.etBackendUrl.text.toString().trim()
            if (url.isEmpty()) {
                binding.etBackendUrl.error = "Enter backend URL"
                return@setOnClickListener
            }
            if (selectedLanguageCode == null) {
                Toast.makeText(this, "Please select a language", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Save URL for next time
            getSharedPreferences("app_prefs", MODE_PRIVATE).edit().putString("backend_url", url).apply()

            val intent = Intent(this, CallActivity::class.java).apply {
                putExtra("BACKEND_URL", url)
                putExtra("LANGUAGE_CODE", selectedLanguageCode)
            }
            startActivity(intent)
        }
    }

    private fun setupLanguageDropdown() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, languages)
        binding.actvLanguage.setAdapter(adapter)
        binding.actvLanguage.setOnItemClickListener { parent, _, position, _ ->
            val selectedLanguage = parent.adapter.getItem(position) as Language
            selectedLanguageCode = selectedLanguage.code
        }
    }

    private fun setupBackendUrl() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val savedUrl = prefs.getString("backend_url", "http://192.168.1.10:8000")
        binding.etBackendUrl.setText(savedUrl)
    }
}