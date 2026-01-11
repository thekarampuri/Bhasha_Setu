package com.example.voicetranslate.audio

import android.annotation.SuppressLint
import android.media.*
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import okhttp3.*
import okio.ByteString.Companion.toByteString
import java.util.concurrent.TimeUnit
import java.util.*
import org.json.JSONObject
import kotlin.math.*

class CallManager(
    private val backendUrl: String, 
    private val callId: String,
    private val sourceLang: String,
    private val targetLang: String,
    private val listener: CallListener
) {
    interface CallListener {
        fun onTranscriptionReceived(source: String, translated: String)
        fun onConnected()
        fun onError(msg: String)
        fun onDisconnected()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS) // Increased timeout for slower local networks
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()
    
    private var webSocket: WebSocket? = null

    private val SAMPLE_RATE = 16000
    private val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
    private val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
    private val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    
    private val CHUNK_SIZE = 6400 
    private val SEND_THRESHOLD = 80000 
    
    private val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, ENCODING).coerceAtLeast(CHUNK_SIZE) * 2

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var isActive = false
    private var isMuted = false
    
    private val GAIN_FACTOR = 3.0f
    
    private val vad = VoiceActivityDetector(
        energyThreshold = 0.01f,
        minSpeechDurationMs = 300
    )
    
    private var lastTranscript = ""
    private var lastTranscriptTime = 0L
    
    private var isPushToTalkMode = false
    private var isPushToTalkActive = false

    fun setMuted(muted: Boolean) {
        isMuted = muted
    }
    
    fun setPushToTalkMode(enabled: Boolean) {
        isPushToTalkMode = enabled
    }
    
    fun setPushToTalkActive(active: Boolean) {
        if (isPushToTalkMode) isPushToTalkActive = active
    }
    
    private fun shouldSendAudio(): Boolean = if (isMuted) false else if (isPushToTalkMode) isPushToTalkActive else true

    fun startCall() {
        // Remove any protocol prefixes or leading slashes from user input
        val host = backendUrl.trim()
            .replace(Regex("^(http://|https://|ws://|wss://|/+)"), "")
            .replace(Regex("/+$"), "")

        val wsUrl = "ws://$host/ws/call/$callId/$sourceLang/$targetLang"
        
        Log.d("CallManager", "Connecting to WebSocket: $wsUrl")
        
        val request = Request.Builder().url(wsUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isActive = true
                startCaptureLoop()
                startPlayback()
                listener.onConnected()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    if (json.getString("type") == "transcription") {
                        val source = json.getString("source")
                        val translated = json.getString("translated")
                        val currentTime = System.currentTimeMillis()
                        if (source != lastTranscript || (currentTime - lastTranscriptTime) > 10000) {
                            lastTranscript = source
                            lastTranscriptTime = currentTime
                            listener.onTranscriptionReceived(source, translated)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("CallManager", "JSON Error: ${e.message}")
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                val rawBytes = bytes.toByteArray()
                val processedBytes = applyGain(rawBytes, GAIN_FACTOR)
                audioTrack?.write(processedBytes, 0, processedBytes.size)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val rawMessage = t.message ?: "Unknown Connection Error"
                Log.e("CallManager", "Connection Failure: $rawMessage")
                
                // CLEANUP: Hide the leading slash "/" from the IP address in the error message
                var displayError = rawMessage.replace(Regex("connect to /", RegexOption.IGNORE_CASE), "connect to ")
                
                // Add troubleshooting guidance based on common network errors
                displayError = when {
                    displayError.contains("timeout", ignoreCase = true) -> 
                        "$displayError. Please check if your PC firewall is blocking port 8000 and ensure both devices are on the SAME Wi-Fi."
                    displayError.contains("refused", ignoreCase = true) -> 
                        "$displayError. Ensure the Python backend is actually running on $host."
                    else -> displayError
                }

                listener.onError("Connection failed: $displayError")
                stopCall()
            }
            
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                listener.onDisconnected()
                stopCall()
            }
        })
    }

    private fun applyGain(data: ByteArray, gain: Float): ByteArray {
        if (gain == 1.0f) return data
        val result = ByteArray(data.size)
        for (i in 0 until data.size step 2) {
            if (i + 1 >= data.size) break
            var sample = ((data[i + 1].toInt() shl 8) or (data[i].toInt() and 0xFF)).toShort()
            val amplified = (sample * gain).toInt()
            val clipped = amplified.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            result[i] = (clipped.toInt() and 0xFF).toByte()
            result[i + 1] = ((clipped.toInt() shr 8) and 0xFF).toByte()
        }
        return result
    }

    @SuppressLint("MissingPermission")
    private fun startCaptureLoop() {
        try {
            audioRecord = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, SAMPLE_RATE, CHANNEL_IN, ENCODING, bufferSize)
            
            audioRecord?.audioSessionId?.let { sessionId ->
                if (sessionId != AudioRecord.ERROR_BAD_VALUE) {
                    if (AcousticEchoCanceler.isAvailable()) {
                        AcousticEchoCanceler.create(sessionId)?.enabled = true
                    }
                    if (NoiseSuppressor.isAvailable()) {
                        NoiseSuppressor.create(sessionId)?.enabled = true
                    }
                }
            }
            
            audioRecord?.startRecording()
            
            Thread {
                val captureBuffer = ByteArray(CHUNK_SIZE)
                val sendBuffer = mutableListOf<Byte>()
                while (isActive) {
                    val read = audioRecord?.read(captureBuffer, 0, CHUNK_SIZE) ?: 0
                    if (read > 0 && shouldSendAudio()) {
                        webSocket?.send(captureBuffer.sliceArray(0 until read).toByteString())
                        sendBuffer.addAll(captureBuffer.sliceArray(0 until read).toList())
                        if (sendBuffer.size >= SEND_THRESHOLD) {
                            val audioChunk = sendBuffer.toByteArray()
                            if (vad.isSpeech(audioChunk, SAMPLE_RATE)) {
                                Log.d("CallManager", "Speech detected")
                            }
                            sendBuffer.clear()
                        }
                    }
                }
            }.start()
        } catch (e: Exception) {
            Log.e("CallManager", "Capture loop error: ${e.message}")
        }
    }

    private fun startPlayback() {
        try {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(AudioFormat.Builder()
                    .setEncoding(ENCODING).setSampleRate(SAMPLE_RATE).setChannelMask(CHANNEL_OUT).build())
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            audioTrack?.play()
        } catch (e: Exception) {
            Log.e("CallManager", "Playback error: ${e.message}")
        }
    }

    fun stopCall() {
        isActive = false
        audioRecord?.apply { try { stop(); release() } catch(e: Exception) {} }
        audioTrack?.apply { try { stop(); release() } catch(e: Exception) {} }
        webSocket?.close(1000, "Done")
        audioRecord = null
        audioTrack = null
        webSocket = null
    }
}
