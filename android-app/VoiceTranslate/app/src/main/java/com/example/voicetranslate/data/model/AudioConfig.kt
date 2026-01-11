package com.example.voicetranslate.data.model

/**
 * Audio configuration constants
 */
data class AudioConfig(
    val sampleRate: Int = 16000,
    val channels: Int = 1,
    val encoding: Int = android.media.AudioFormat.ENCODING_PCM_16BIT,
    val chunkSize: Int = 6400,
    val sendThreshold: Int = 80000
)
