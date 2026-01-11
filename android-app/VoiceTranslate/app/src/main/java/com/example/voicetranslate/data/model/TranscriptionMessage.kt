package com.example.voicetranslate.data.model

/**
 * Message received from transcription service
 */
data class TranscriptionMessage(
    val type: String,
    val source: String,
    val translated: String,
    val sender: String
)
