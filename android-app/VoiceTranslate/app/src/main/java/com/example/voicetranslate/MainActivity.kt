package com.example.voicetranslate

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.voicetranslate.databinding.ActivityMainBinding
import com.example.voicetranslate.data.model.Language
import com.example.voicetranslate.data.repository.PreferencesRepository
import com.example.voicetranslate.util.Constants

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefsRepository: PreferencesRepository
    
    private val languages = Language.SUPPORTED_LANGUAGES
    
    private var sourceLanguageCode: String? = "en"  // Default to English
    private var targetLanguageCode: String? = "hi"  // Default to Hindi

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        prefsRepository = PreferencesRepository(this)

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
            if (sourceLanguageCode == null || sourceLanguageCode.isNullOrEmpty()) {
                Toast.makeText(this, "Please select source language", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (targetLanguageCode == null || targetLanguageCode.isNullOrEmpty()) {
                Toast.makeText(this, "Please select target language", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Save URL for next time
            prefsRepository.saveBackendUrl(url)

            android.util.Log.d(Constants.Log.TAG_MAIN, "Starting call with: URL=$url, CallID=$callId, Source=$sourceLanguageCode, Target=$targetLanguageCode")

            val intent = Intent(this, CallActivity::class.java).apply {
                putExtra(Constants.Extras.BACKEND_URL, url)
                putExtra(Constants.Extras.CALL_ID, callId)
                putExtra(Constants.Extras.SOURCE_LANG, sourceLanguageCode)
                putExtra(Constants.Extras.TARGET_LANG, targetLanguageCode)
            }
            startActivity(intent)
        }
    }

    private fun setupLanguageDropdowns() {
        // Use a simple layout for the dropdown items
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, languages)
        
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
        
        // Ensure the dropdown shows on click even if text is empty
        binding.actvSourceLanguage.setOnClickListener { binding.actvSourceLanguage.showDropDown() }
        binding.actvTargetLanguage.setOnClickListener { binding.actvTargetLanguage.showDropDown() }
    }

    private fun setupBackendUrl() {
        val savedUrl = prefsRepository.getBackendUrl()
        binding.etBackendUrl.setText(savedUrl)
    }
}
