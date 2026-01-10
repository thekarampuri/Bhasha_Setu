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
    private val listener: CallListener
) {
    interface CallListener {
        fun onTranscriptionReceived(source: String, translated: String)
        fun onConnected()
        fun onError(msg: String)
        fun onDisconnected()
    }

    private val userId = UUID.randomUUID().toString().substring(0, 4)
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private var webSocket: WebSocket? = null

    // PCM 16bit, 16kHz, Mono = 32,000 bytes per second
    private val SAMPLE_RATE = 16000
    private val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
    private val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
    private val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    
    // 40ms chunk = (16000 * 0.040) * 2 bytes = 1280 bytes
    private val CHUNK_SIZE = 1280 
    private val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, ENCODING).coerceAtLeast(CHUNK_SIZE)

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var isActive = false

    fun startCall() {
        // Sanitize the URL
        val sanitizedUrl = backendUrl
            .replace("http://", "")
            .replace("https://", "")
            .replace("ws://", "")
            .replace("wss://", "")
            .removeSuffix("/")

        val wsUrl = "ws://$sanitizedUrl/ws/call/$callId/$userId"
        Log.d("CallManager", "Connecting to relay: $wsUrl")
        
        val request = Request.Builder().url(wsUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isActive = true
                startCaptureLoop()
                startPlayback()
                listener.onConnected()
            }

            override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                // Play received raw PCM immediately
                audioTrack?.write(bytes.toByteArray(), 0, bytes.size)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                listener.onError(t.message ?: "Connection failed")
                stopCall()
            }
            
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                listener.onDisconnected()
                stopCall()
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun startCaptureLoop() {
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC, 
                SAMPLE_RATE, 
                CHANNEL_IN, 
                ENCODING, 
                bufferSize
            )
            audioRecord?.startRecording()
            
            Thread {
                val data = ByteArray(CHUNK_SIZE)
                while (isActive) {
                    val read = audioRecord?.read(data, 0, CHUNK_SIZE) ?: 0
                    if (read > 0) {
                        // Send small 40ms packets
                        webSocket?.send(data.sliceArray(0 until read).toByteString())
                    }
                }
            }.start()
        } catch (e: Exception) {
            listener.onError("Mic Error: ${e.message}")
        }
    }

    private fun startPlayback() {
        try {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build())
                .setAudioFormat(AudioFormat.Builder()
                    .setEncoding(ENCODING)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL_OUT)
                    .build())
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            audioTrack?.play()
        } catch (e: Exception) {
            listener.onError("Speaker Error: ${e.message}")
        }
    }

    fun stopCall() {
        isActive = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
        } catch (e: Exception) {
            Log.e("CallManager", "Cleanup Error: ${e.message}")
        }
        webSocket?.close(1000, "Done")
        webSocket = null
    }
}
