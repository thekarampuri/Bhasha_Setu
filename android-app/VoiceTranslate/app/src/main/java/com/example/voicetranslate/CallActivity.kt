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
        
        setupUI()
        checkPermissionAndStart()
    }

    private fun setupUI() {
        binding.tvCallStatus.text = "Connecting to Relay: $callId..."
        
        binding.btnSpeaker.setOnClickListener { toggleSpeaker() }
        binding.btnMute.setOnClickListener { toggleMute() }
        binding.btnEndCall.setOnClickListener { 
            callManager?.stopCall()
            finish() 
        }

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
        // Simplified constructor for Relay test
        callManager = CallManager(backendUrl, callId, this)
        callManager?.startCall()
    }

    private fun toggleSpeaker() {
        isSpeakerOn = !isSpeakerOn
        audioManager.isSpeakerphoneOn = isSpeakerOn
        binding.btnSpeaker.text = if (isSpeakerOn) "Speaker On" else "Speaker Off"
    }

    private fun toggleMute() {
        isMuted = !isMuted
        binding.btnMute.text = if (isMuted) "Unmute" else "Mute"
    }

    // CallManager.CallListener Implementation
    override fun onTranscriptionReceived(source: String, translated: String) {
        // Not used in basic relay mode
    }

    override fun onError(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            binding.tvCallStatus.text = "Error: $message"
        }
    }

    override fun onConnected() {
        runOnUiThread {
            binding.tvCallStatus.text = "Status: Relay Connected"
            Toast.makeText(this, "Audio Streaming Active", Toast.LENGTH_SHORT).show()
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
