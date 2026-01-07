package com.example.voicetranslate

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.voicetranslate.audio.MicLoopback
import com.example.voicetranslate.databinding.ActivityCallBinding

class CallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCallBinding
    private val micLoopback = MicLoopback()
    private var isTestingAudio = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            toggleAudioTest()
        } else {
            Toast.makeText(this, "Microphone permission required", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnPushToTalk.text = "Start Audio Test"

        binding.btnPushToTalk.setOnClickListener {
            handleAudioTestClick()
        }

        binding.btnEndCall.setOnClickListener {
            finish()
        }
    }

    private fun handleAudioTestClick() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            toggleAudioTest()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun toggleAudioTest() {
        if (isTestingAudio) {
            micLoopback.stop()
            binding.btnPushToTalk.text = "Start Audio Test"
            binding.tvCallStatus.text = "Status: Idle"
            isTestingAudio = false
        } else {
            micLoopback.start()
            binding.btnPushToTalk.text = "Stop Audio Test"
            binding.tvCallStatus.text = "Status: Testing Audio Loopback"
            isTestingAudio = true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        micLoopback.stop()
    }
}