package com.example.voicetranslate

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.voicetranslate.databinding.ActivityCallBinding

/**
 * Call screen
 * 
 * UI Elements:
 * - Call status display
 * - Mute button (disabled)
 * - Speaker button (disabled)
 * - End call button (functional)
 * 
 * TODO: Add WebRTC functionality
 */
class CallActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityCallBinding
    private var callId: String = ""
    private var userId: String = ""
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCallBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        callId = intent.getStringExtra("CALL_ID") ?: ""
        userId = intent.getStringExtra("USER_ID") ?: ""
        
        setupUI()
    }
    
    private fun setupUI() {
        // Display call ID
        binding.tvCallId.text = "Call ID: $callId"
        
        // End call button
        binding.btnEndCall.setOnClickListener {
            endCall()
        }
        
        // Mute and Speaker buttons are disabled (no functionality yet)
        binding.btnMute.isEnabled = false
        binding.btnSpeaker.isEnabled = false
    }
    
    private fun endCall() {
        // TODO: Disconnect WebRTC and signaling
        finish()
    }
}
