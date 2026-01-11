package com.example.voicetranslate.data.repository

import com.example.voicetranslate.data.model.CallConfig
import com.example.voicetranslate.data.model.CallState
import com.example.voicetranslate.data.model.TranscriptionMessage
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for call operations
 */
interface CallRepository {
    
    /**
     * Start a call with the given configuration
     */
    fun startCall(config: CallConfig)
    
    /**
     * Stop the current call
     */
    fun stopCall()
    
    /**
     * Send audio data
     */
    fun sendAudio(data: ByteArray)
    
    /**
     * Set mute state
     */
    fun setMuted(muted: Boolean)
    
    /**
     * Set push-to-talk mode
     */
    fun setPushToTalkMode(enabled: Boolean)
    
    /**
     * Set push-to-talk active state
     */
    fun setPushToTalkActive(active: Boolean)
    
    /**
     * Observe call state
     */
    fun observeCallState(): Flow<CallState>
    
    /**
     * Observe transcription messages
     */
    fun observeTranscriptions(): Flow<TranscriptionMessage>
    
    /**
     * Observe incoming audio
     */
    fun observeIncomingAudio(): Flow<ByteArray>
}
