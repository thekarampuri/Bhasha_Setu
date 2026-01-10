package com.example.voicetranslate.audio

import android.annotation.SuppressLint
import android.media.*
import android.util.Log
import okhttp3.*
import okio.ByteString.Companion.toByteString
import java.util.concurrent.TimeUnit
import java.util.*

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
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private var webSocket: WebSocket? = null

    private val SAMPLE_RATE = 16000
    private val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
    private val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
    private val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    private val CHUNK_SIZE = 1280 
    private val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, ENCODING).coerceAtLeast(CHUNK_SIZE)

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var isActive = false
    private var isMuted = false

    fun setMuted(muted: Boolean) {
        isMuted = muted
        Log.d("CallManager", "Mute state changed: $muted")
    }

    fun startCall() {
        val sanitizedUrl = backendUrl
            .replace("http://", "")
            .replace("ws://", "")
            .removeSuffix("/")

        // Matches Backend: /ws/call/{call_id}/{source_lang}/{target_lang}
        val wsUrl = "ws://$sanitizedUrl/ws/call/$callId/$sourceLang/$targetLang"
        Log.d("CallManager", "Connecting to: $wsUrl")
        
        val request = Request.Builder().url(wsUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("CallManager", "WebSocket connected successfully")
                isActive = true
                startCaptureLoop()
                startPlayback()
                listener.onConnected()
            }

            override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                val bytesReceived = bytes.size
                audioTrack?.write(bytes.toByteArray(), 0, bytesReceived)
                Log.v("CallManager", "Received audio: $bytesReceived bytes")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("CallManager", "WebSocket failed: ${t.message}", t)
                listener.onError("Connection failed: ${t.message}")
                stopCall()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("CallManager", "WebSocket closed: $code - $reason")
                listener.onDisconnected()
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun startCaptureLoop() {
        // Use VOICE_COMMUNICATION source for echo cancellation and noise suppression
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            SAMPLE_RATE,
            CHANNEL_IN,
            ENCODING,
            bufferSize
        )
        
        // Enable acoustic echo cancellation if available
        if (android.media.audiofx.AcousticEchoCanceler.isAvailable()) {
            val aec = android.media.audiofx.AcousticEchoCanceler.create(audioRecord?.audioSessionId ?: 0)
            aec?.enabled = true
            Log.d("CallManager", "Acoustic Echo Cancellation enabled")
        }
        
        // Enable noise suppression if available
        if (android.media.audiofx.NoiseSuppressor.isAvailable()) {
            val ns = android.media.audiofx.NoiseSuppressor.create(audioRecord?.audioSessionId ?: 0)
            ns?.enabled = true
            Log.d("CallManager", "Noise Suppression enabled")
        }
        
        audioRecord?.startRecording()
        Log.d("CallManager", "Audio recording started")
        
        Thread {
            val data = ByteArray(CHUNK_SIZE)
            while (isActive) {
                val read = audioRecord?.read(data, 0, CHUNK_SIZE) ?: 0
                if (read > 0) {
                    // Only send audio if not muted
                    if (!isMuted) {
                        webSocket?.send(data.sliceArray(0 until read).toByteString())
                    }
                }
            }
            Log.d("CallManager", "Audio capture loop ended")
        }.start()
    }

    private fun startPlayback() {
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .setFlags(AudioAttributes.FLAG_LOW_LATENCY)
                .build())
            .setAudioFormat(AudioFormat.Builder()
                .setEncoding(ENCODING)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(CHANNEL_OUT)
                .build())
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
        audioTrack?.play()
        Log.d("CallManager", "Audio playback started with low-latency mode")
    }

    fun stopCall() {
        Log.d("CallManager", "Stopping call")
        isActive = false
        audioRecord?.apply { 
            stop()
            release()
        }
        audioTrack?.apply { 
            stop()
            release() 
        }
        webSocket?.close(1000, "Done")
        listener.onDisconnected()
    }
}
