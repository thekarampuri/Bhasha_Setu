package com.example.voicetranslate.audio

import android.annotation.SuppressLint
import android.media.*
import android.util.Log
import okhttp3.*
import okio.ByteString.Companion.toByteString
import java.util.concurrent.TimeUnit
import java.util.*
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
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private var webSocket: WebSocket? = null

    // Increased sample rate for better quality (16kHz to 44.1kHz or 48kHz is standard, but keeping 16kHz for low data usage and compatibility)
    // 16kHz is usually sufficient for voice. To improve quality, we'll ensure optimal buffer handling.
    private val SAMPLE_RATE = 16000
    private val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
    private val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
    private val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    private val CHUNK_SIZE = 1280 
    private val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, ENCODING).coerceAtLeast(CHUNK_SIZE) * 2

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var isActive = false
    private var isMuted = false
    
    // Gain control (e.g., 2.0 to double the volume)
    private val GAIN_FACTOR = 3.0f 

    fun setMuted(muted: Boolean) {
        isMuted = muted
    }

    fun startCall() {
        val sanitizedUrl = backendUrl
            .replace("http://", "")
            .replace("ws://", "")
            .removeSuffix("/")

        val wsUrl = "ws://$sanitizedUrl/ws/call/$callId/$sourceLang/$targetLang"
        
        val request = Request.Builder().url(wsUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isActive = true
                startCaptureLoop()
                startPlayback()
                listener.onConnected()
            }

            override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                val rawBytes = bytes.toByteArray()
                // Apply gain to incoming audio to increase volume
                val processedBytes = applyGain(rawBytes, GAIN_FACTOR)
                audioTrack?.write(processedBytes, 0, processedBytes.size)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                listener.onError("Connection failed: ${t.message}")
                stopCall()
            }
        })
    }

    /**
     * Boosts the volume of raw PCM 16-bit audio
     */
    private fun applyGain(data: ByteArray, gain: Float): ByteArray {
        if (gain == 1.0f) return data
        val result = ByteArray(data.size)
        for (i in 0 until data.size step 2) {
            if (i + 1 >= data.size) break
            
            // Convert to short (16-bit)
            var sample = ((data[i + 1].toInt() shl 8) or (data[i].toInt() and 0xFF)).toShort()
            
            // Apply gain and clip to prevent distortion
            val amplified = (sample * gain).toInt()
            val clipped = amplified.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            
            // Back to bytes
            result[i] = (clipped.toInt() and 0xFF).toByte()
            result[i + 1] = ((clipped.toInt() shr 8) and 0xFF).toByte()
        }
        return result
    }

    @SuppressLint("MissingPermission")
    private fun startCaptureLoop() {
        // Use VOICE_COMMUNICATION for better mic quality (tunes for speech)
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION, 
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
                    if (!isMuted) {
                        // Optionally apply gain to outgoing mic audio too
                        val micBoosted = applyGain(data.sliceArray(0 until read), 1.5f)
                        webSocket?.send(micBoosted.toByteString())
                    }
                }
            }
        }.start()
    }

    private fun startPlayback() {
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
    }

    fun stopCall() {
        isActive = false
        audioRecord?.apply { stop(); release() }
        audioTrack?.apply { stop(); release() }
        webSocket?.close(1000, "Done")
    }
}
