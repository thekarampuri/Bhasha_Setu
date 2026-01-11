package com.example.voicetranslate.data.model

/**
 * Configuration for a call session
 */
data class CallConfig(
    val backendUrl: String,
    val callId: String,
    val sourceLang: String,
    val targetLang: String
)
