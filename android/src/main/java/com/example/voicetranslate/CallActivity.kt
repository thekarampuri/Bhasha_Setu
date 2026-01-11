package com.example.voicetranslate

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Call screen (placeholder)
 * 
 * TODO: Implement call UI with:
 * - Connection status
 * - Mute/Speaker/End call buttons
 * - WebRTC integration
 */
class CallActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val callId = intent.getStringExtra("CALL_ID") ?: ""
        val userId = intent.getStringExtra("USER_ID") ?: ""
        
        // TODO: Implement call UI
        // For now, just finish to return to MainActivity
        finish()
    }
}
