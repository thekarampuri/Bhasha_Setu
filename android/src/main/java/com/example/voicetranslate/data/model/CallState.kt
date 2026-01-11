package com.example.voicetranslate.data.model

/**
 * Sealed class representing call state
 */
sealed class CallState {
    object Idle : CallState()
    object Connecting : CallState()
    object Connected : CallState()
    data class Error(val message: String) : CallState()
    object Disconnected : CallState()
}
