package com.example.voicetranslate

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.media.AudioManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
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
    
    private var savedAudioMode: Int = AudioManager.MODE_NORMAL

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
        
        // Save current audio mode and configure for voice call
        savedAudioMode = audioManager.mode
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        
        backendUrl = intent.getStringExtra("BACKEND_URL") ?: ""
        callId = intent.getStringExtra("CALL_ID") ?: ""
        sourceLang = intent.getStringExtra("SOURCE_LANG") ?: "en"
        targetLang = intent.getStringExtra("TARGET_LANG") ?: "en"
        
        android.util.Log.d("CallActivity", "Received intent extras: URL=$backendUrl, CallID=$callId, Source=$sourceLang, Target=$targetLang")
        
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
        
        // Clear the placeholder message
        binding.llConversationHistory.removeAllViews()
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
    }

    private fun toggleMute() {
        isMuted = !isMuted
        audioManager.isMicrophoneMute = isMuted
        callManager?.setMuted(isMuted)
        binding.btnMute.text = if (isMuted) "Unmute" else "Mute"
    }

    override fun onTranscriptionReceived(source: String, translated: String) {
        runOnUiThread {
            addMessageToConversation(source, translated, isLocal = false)
        }
    }
    
    private fun addMessageToConversation(sourceText: String, translatedText: String, isLocal: Boolean) {
        val messageContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 24)
            }
            setPadding(16, 12, 16, 12)
            setBackgroundColor(
                if (isLocal) 
                    ContextCompat.getColor(this@CallActivity, android.R.color.holo_blue_dark)
                else 
                    ContextCompat.getColor(this@CallActivity, android.R.color.darker_gray)
            )
        }
        
        // Label (You / Them)
        val labelView = TextView(this).apply {
            text = if (isLocal) "You:" else "Them:"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@CallActivity, android.R.color.white))
        }
        
        // Source text
        val sourceView = TextView(this).apply {
            text = sourceText
            textSize = 16f
            setTextColor(ContextCompat.getColor(this@CallActivity, android.R.color.white))
            setPadding(0, 4, 0, 8)
        }
        
        // Translation label
        val transLabelView = TextView(this).apply {
            text = "Translation:"
            textSize = 11f
            setTextColor(ContextCompat.getColor(this@CallActivity, android.R.color.darker_gray))
        }
        
        // Translated text
        val translatedView = TextView(this).apply {
            text = translatedText
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@CallActivity, android.R.color.white))
            alpha = 0.9f
        }
        
        messageContainer.addView(labelView)
        messageContainer.addView(sourceView)
        messageContainer.addView(transLabelView)
        messageContainer.addView(translatedView)
        
        binding.llConversationHistory.addView(messageContainer)
        
        // Auto-scroll to bottom
        binding.scrollViewTranscript.post {
            binding.scrollViewTranscript.fullScroll(View.FOCUS_DOWN)
        }
        
        android.util.Log.d("CallActivity", "Added message: $sourceText -> $translatedText")
    }

    override fun onError(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            binding.tvCallStatus.text = "Error: $message"
        }
    }

    override fun onConnected() {
        runOnUiThread {
            binding.tvCallStatus.text = "Status: Connected - Speaking..."
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
        
        // Restore original audio settings
        audioManager.mode = savedAudioMode
        audioManager.isSpeakerphoneOn = false
        audioManager.isMicrophoneMute = false
    }
}
