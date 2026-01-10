package com.example.voicetranslate

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.voicetranslate.audio.CallManager
import com.example.voicetranslate.databinding.ActivityCallBinding

class CallActivity : AppCompatActivity(), CallManager.CallListener {

    private lateinit var binding: ActivityCallBinding
    private var callManager: CallManager? = null
    
    private var isMuted = false
    private var isSpeakerOn = false
    
    private lateinit var backendUrl: String
    private lateinit var callId: String
    private lateinit var sourceLang: String
    private lateinit var targetLang: String
    private lateinit var audioManager: AudioManager

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            initiateCall()
        } else {
            Toast.makeText(this, "Microphone permission required", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        backendUrl = intent.getStringExtra("BACKEND_URL") ?: ""
        callId = intent.getStringExtra("CALL_ID") ?: ""
        sourceLang = intent.getStringExtra("SOURCE_LANG") ?: "en"
        targetLang = intent.getStringExtra("TARGET_LANG") ?: "en"
        
        setupUI()
        checkPermissionAndStart()
    }

    private fun setupUI() {
        binding.tvCallStatus.text = "Connecting to Room: $callId..."
        
        binding.btnSpeaker.setOnClickListener { toggleSpeaker() }
        binding.btnMute.setOnClickListener { toggleMute() }
        binding.btnEndCall.setOnClickListener { 
            callManager?.stopCall()
            finish() 
        }

        // In WebSocket mode, PTT is replaced by continuous streaming
        binding.btnPushToTalk.visibility = View.GONE 
    }

    private fun checkPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            initiateCall()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun initiateCall() {
        callManager = CallManager(backendUrl, callId, sourceLang, targetLang, this)
        callManager?.startCall()
    }

    private fun toggleSpeaker() {
        isSpeakerOn = !isSpeakerOn
        audioManager.isSpeakerphoneOn = isSpeakerOn
        binding.btnSpeaker.text = if (isSpeakerOn) "Speaker On" else "Speaker Off"
        Toast.makeText(this, if (isSpeakerOn) "Speaker On" else "Speaker Off", Toast.LENGTH_SHORT).show()
    }

    private fun toggleMute() {
        isMuted = !isMuted
        binding.btnMute.text = if (isMuted) "Unmute" else "Mute"
        Toast.makeText(this, if (isMuted) "Muted" else "Unmuted", Toast.LENGTH_SHORT).show()
    }

    // CallManager.CallListener Implementation
    override fun onTranscriptionReceived(source: String, translated: String) {
        runOnUiThread {
            binding.tvYouPlaceholder.text = source
            binding.tvTranslatedPlaceholder.text = translated
            binding.tvCallStatus.text = "Status: Live"
        }
    }

    override fun onError(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            binding.tvCallStatus.text = "Error: $message"
        }
    }

    override fun onConnected() {
        runOnUiThread {
            binding.tvCallStatus.text = "Status: Connected"
            Toast.makeText(this, "Call Started", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDisconnected() {
        runOnUiThread {
            binding.tvCallStatus.text = "Status: Disconnected"
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        callManager?.stopCall()
    }
}
