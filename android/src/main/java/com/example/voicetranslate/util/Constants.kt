package com.example.voicetranslate.util

/**
 * Constants for the Bhasha Setu application
 */
object Constants {
    
    // Audio Configuration
    object Audio {
        const val SAMPLE_RATE = 16000
        const val CHANNEL_IN = android.media.AudioFormat.CHANNEL_IN_MONO
        const val CHANNEL_OUT = android.media.AudioFormat.CHANNEL_OUT_MONO
        const val ENCODING = android.media.AudioFormat.ENCODING_PCM_16BIT
        
        const val CHUNK_SIZE = 6400  // 0.2s chunks for capture
        const val SEND_THRESHOLD = 80000  // Send every 2.5s for complete sentences
        
        const val GAIN_FACTOR = 3.0f
    }
    
    // Voice Activity Detection
    object VAD {
        const val ENERGY_THRESHOLD = 0.01f
        const val MIN_SPEECH_DURATION_MS = 300L
    }
    
    // Network Configuration
    object Network {
        const val DEFAULT_BACKEND_URL = "192.168.1.10:8000"
        const val WEBSOCKET_PATH_TEMPLATE = "/ws/call/%s/%s/%s"  // call_id, source_lang, target_lang
    }
    
    // Preferences
    object Prefs {
        const val NAME = "app_prefs"
        const val KEY_BACKEND_URL = "backend_url"
    }
    
    // Intent Extras
    object Extras {
        const val BACKEND_URL = "BACKEND_URL"
        const val CALL_ID = "CALL_ID"
        const val SOURCE_LANG = "SOURCE_LANG"
        const val TARGET_LANG = "TARGET_LANG"
    }
    
    // Logging
    object Log {
        const val TAG_MAIN = "MainActivity"
        const val TAG_CALL = "CallActivity"
        const val TAG_CALL_MANAGER = "CallManager"
        const val TAG_VAD = "VAD"
    }
}
