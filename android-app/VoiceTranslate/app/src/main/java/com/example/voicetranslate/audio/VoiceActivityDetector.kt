package com.example.voicetranslate.audio

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Voice Activity Detection (VAD) to filter out silence and low-energy audio.
 * This prevents sending non-speech audio to the backend, reducing Whisper hallucinations.
 */
class VoiceActivityDetector(
    private val energyThreshold: Float = 0.02f,  // Minimum energy for speech
    private val minSpeechDurationMs: Long = 500   // Minimum duration for valid speech
) {
    private var speechStartTime: Long = 0
    private var isSpeechActive = false
    
    /**
     * Analyzes audio data to determine if it contains speech.
     * @param audioData PCM 16-bit audio samples
     * @param sampleRate Sample rate in Hz
     * @return true if speech is detected, false otherwise
     */
    fun isSpeech(audioData: ByteArray, sampleRate: Int = 16000): Boolean {
        // Calculate audio duration
        val durationMs = (audioData.size / 2.0 / sampleRate * 1000).toLong()
        
        // Reject very short audio chunks (< 300ms, reduced from 500ms)
        if (durationMs < minSpeechDurationMs) {
            android.util.Log.d("VAD", "⏭️ Audio too short: ${durationMs}ms < ${minSpeechDurationMs}ms")
            return false
        }
        
        // Calculate energy (RMS)
        val energy = calculateEnergy(audioData)
        
        // Calculate zero-crossing rate for additional validation
        val zcr = calculateZeroCrossingRate(audioData)
        
        // Speech typically has:
        // - Energy above threshold
        // - Moderate zero-crossing rate (not too high like noise, not too low like silence)
        val hasEnergy = energy > energyThreshold
        val hasValidZCR = zcr in 0.1f..0.5f
        
        val isSpeech = hasEnergy && hasValidZCR
        
        android.util.Log.d("VAD", "🎤 VAD Analysis: duration=${durationMs}ms, energy=${"%.4f".format(energy)}, zcr=${"%.3f".format(zcr)}, threshold=${"%.4f".format(energyThreshold)}, isSpeech=$isSpeech")
        
        return isSpeech
    }
    
    /**
     * Calculates the Root Mean Square (RMS) energy of the audio signal.
     * Higher values indicate louder audio.
     */
    private fun calculateEnergy(audioData: ByteArray): Float {
        if (audioData.isEmpty()) return 0f
        
        var sumSquares = 0.0
        var sampleCount = 0
        
        // Convert byte array to 16-bit samples and calculate energy
        for (i in 0 until audioData.size - 1 step 2) {
            val sample = ((audioData[i + 1].toInt() shl 8) or (audioData[i].toInt() and 0xFF)).toShort()
            val normalized = sample / 32768.0f  // Normalize to [-1, 1]
            sumSquares += normalized * normalized
            sampleCount++
        }
        
        return if (sampleCount > 0) {
            sqrt(sumSquares / sampleCount).toFloat()
        } else {
            0f
        }
    }
    
    /**
     * Calculates the Zero-Crossing Rate (ZCR) of the audio signal.
     * ZCR helps distinguish between speech, music, and noise.
     * Speech typically has moderate ZCR.
     */
    private fun calculateZeroCrossingRate(audioData: ByteArray): Float {
        if (audioData.size < 4) return 0f
        
        var crossings = 0
        var prevSample = 0
        
        for (i in 0 until audioData.size - 1 step 2) {
            val sample = ((audioData[i + 1].toInt() shl 8) or (audioData[i].toInt() and 0xFF)).toShort().toInt()
            
            if (i > 0 && ((prevSample >= 0 && sample < 0) || (prevSample < 0 && sample >= 0))) {
                crossings++
            }
            prevSample = sample
        }
        
        val totalSamples = audioData.size / 2
        return if (totalSamples > 0) crossings.toFloat() / totalSamples else 0f
    }
    
    /**
     * Checks if the audio is mostly silence (very low energy).
     */
    fun isSilent(audioData: ByteArray): Boolean {
        val energy = calculateEnergy(audioData)
        return energy < (energyThreshold * 0.5f)  // Even stricter threshold for silence
    }
}
