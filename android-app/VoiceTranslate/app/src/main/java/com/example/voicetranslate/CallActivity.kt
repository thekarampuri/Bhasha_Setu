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

        // Push-to-Talk button (optional mode for noisy environments)
        binding.btnPushToTalk.visibility = View.VISIBLE
        binding.btnPushToTalk.text = "Enable PTT Mode"
        
        var isPTTMode = false
        
        // Click listener for toggling PTT mode
        binding.btnPushToTalk.setOnClickListener {
            if (!isPTTMode) {
                // Enable PTT mode
                isPTTMode = true
                callManager?.setPushToTalkMode(true)
                
                binding.btnPushToTalk.text = "🎙️ Hold to Speak"
                binding.btnPushToTalk.setBackgroundColor(
                    ContextCompat.getColor(this, android.R.color.holo_red_dark)
                )
                binding.btnPushToTalk.alpha = 0.7f
                binding.tvCallStatus.text = "Status: PTT Mode - Hold button to speak"
                
                // Remove click listener and add touch listener
                binding.btnPushToTalk.setOnClickListener(null)
                binding.btnPushToTalk.setOnTouchListener { view, event ->
                    when (event.action) {
                        android.view.MotionEvent.ACTION_DOWN -> {
                            callManager?.setPushToTalkActive(true)
                            view.alpha = 1.0f
                            binding.tvCallStatus.text = "Status: 🔴 Recording..."
                            true
                        }
                        android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                            callManager?.setPushToTalkActive(false)
                            view.alpha = 0.7f
                            binding.tvCallStatus.text = "Status: PTT Mode - Hold button to speak"
                            true
                        }
                        else -> false
                    }
                }
            }
        }
        
        // Add long-press to disable PTT mode
        binding.btnPushToTalk.setOnLongClickListener {
            if (isPTTMode) {
                isPTTMode = false
                callManager?.setPushToTalkMode(false)
                callManager?.setPushToTalkActive(false)
                
                binding.btnPushToTalk.text = "Enable PTT Mode"
                binding.btnPushToTalk.setBackgroundColor(
                    ContextCompat.getColor(this, android.R.color.holo_blue_dark)
                )
                binding.btnPushToTalk.alpha = 1.0f
                binding.tvCallStatus.text = "Status: Continuous Mode"
                
                // Remove touch listener and restore click listener
                binding.btnPushToTalk.setOnTouchListener(null)
                setupUI() // Re-setup to restore click listener
                true
            } else {
                false
            }
        }
        
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
            // Messages from remote user (not from this device)
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
        
        // Label (You / Them) - FIXED: Now properly shows who spoke
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
            setTextColor(ContextCompat.getColor(this@CallActivity, android.R.color.white))
            alpha = 0.7f
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
        
        android.util.Log.d("CallActivity", "Added message: ${if (isLocal) "You" else "Them"}: $sourceText -> $translatedText")
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
