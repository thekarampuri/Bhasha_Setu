package com.example.voicetranslate

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.voicetranslate.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Load saved URL if any (optional, but good for UX)
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val savedUrl = prefs.getString("backend_url", "http://192.168.1.10:8000")
        binding.etBackendUrl.setText(savedUrl)

        binding.btnStartCall.setOnClickListener {
            val url = binding.etBackendUrl.text.toString().trim()
            if (url.isEmpty()) {
                binding.etBackendUrl.error = "Enter backend URL"
                return@setOnClickListener
            }

            // Save for next time
            prefs.edit().putString("backend_url", url).apply()

            val intent = Intent(this, CallActivity::class.java).apply {
                putExtra("BACKEND_URL", url)
            }
            startActivity(intent)
        }
    }
}