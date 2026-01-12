package com.example.voicetranslate

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.voicetranslate.data.model.CallState
import com.example.voicetranslate.data.repository.CallRepository
import com.example.voicetranslate.data.repository.UserRepository
import com.example.voicetranslate.databinding.ActivityCallBinding
import kotlinx.coroutines.launch

/**
 * Call screen with WebRTC integration
 * 
 * Features:
 * - WebRTC audio calling
 * - Call status display
 * - Mute/Speaker/End call controls
 */
class CallActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityCallBinding
    private lateinit var callRepository: CallRepository
    
    private var callId: String = ""
    private var serverUrl: String = "192.168.1.10:8000" // TODO: Get from settings
    
    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCallBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        callId = intent.getStringExtra("CALL_ID") ?: ""
        
        if (callId.isEmpty()) {
            Toast.makeText(this, "Invalid Call ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // Initialize repository
        val userRepository = UserRepository(this)
        callRepository = CallRepository(this, userRepository)
        
        setupUI()
        checkPermissionsAndStartCall()
    }
    
    private fun setupUI() {
        binding.tvCallId.text = "Call ID: $callId"
        
        // End call button
        binding.btnEndCall.setOnClickListener {
            endCall()
        }
        
        // Mute button (will be enabled when connected)
        binding.btnMute.setOnClickListener {
            callRepository.toggleMute()
        }
        
        // Speaker button (disabled for now)
        binding.btnSpeaker.isEnabled = false
        
        // Observe call state
        lifecycleScope.launch {
            callRepository.callState.collect { state ->
                updateCallStatus(state)
            }
        }
        
        // Observe mute state
        lifecycleScope.launch {
            callRepository.isMuted.collect { muted ->
                updateMuteButton(muted)
            }
        }
    }
    
    private fun checkPermissionsAndStartCall() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            // Request permission
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                PERMISSION_REQUEST_CODE
            )
        } else {
            // Permission already granted
            startCall()
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCall()
            } else {
                Toast.makeText(this, "Microphone permission required", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
    
    private fun startCall() {
        lifecycleScope.launch {
            callRepository.startCall(serverUrl, callId)
        }
    }
    
    private fun updateCallStatus(state: CallState) {
        val statusText = when (state) {
            CallState.IDLE -> "Idle"
            CallState.CALLING -> "Calling..."
            CallState.RINGING -> "Ringing..."
            CallState.CONNECTING -> "Connecting..."
            CallState.CONNECTED -> "Connected"
            CallState.ENDED -> "Call Ended"
        }
        
        binding.tvCallStatus.text = statusText
        
        // Enable mute button when connected
        binding.btnMute.isEnabled = (state == CallState.CONNECTED)
        
        // End call if state is ENDED
        if (state == CallState.ENDED) {
            finish()
        }
    }
    
    private fun updateMuteButton(muted: Boolean) {
        // Update button appearance based on mute state
        binding.cardMute.alpha = if (muted) 1.0f else 0.5f
    }
    
    private fun endCall() {
        callRepository.endCall()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        callRepository.endCall()
    }
}
