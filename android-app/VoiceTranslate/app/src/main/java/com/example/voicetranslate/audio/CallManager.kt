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
    
    private val CHUNK_SIZE = 6400  // 0.2s chunks for capture
    private val SEND_THRESHOLD = 20000  // Send every 0.625s for real-time processing (matches backend)
    
    private val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, ENCODING).coerceAtLeast(CHUNK_SIZE) * 2

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var isActive = false
    private var isMuted = false
    
    private val GAIN_FACTOR = 3.0f
    
    private val vad = VoiceActivityDetector(
        energyThreshold = 0.01f,  // LOWERED from 0.02f for better speech detection
        minSpeechDurationMs = 300  // REDUCED from 500ms to 300ms
    )
    
    private var lastTranscript = ""
    private var lastTranscriptTime = 0L

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
                        } else {
                            Log.d("CallManager", "Suppressed duplicate: $source")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("CallManager", "Error parsing JSON: ${e.message}")
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                val rawBytes = bytes.toByteArray()
                val processedBytes = applyGain(rawBytes, GAIN_FACTOR)
                audioTrack?.write(processedBytes, 0, processedBytes.size)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                listener.onError("Connection failed: ${t.message}")
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
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION, 
            SAMPLE_RATE, 
            CHANNEL_IN, 
            ENCODING, 
            bufferSize
        )
        
        // Fix: Explicitly import AcousticEchoCanceler and NoiseSuppressor
        if (AcousticEchoCanceler.isAvailable()) {
            val aec = AcousticEchoCanceler.create(audioRecord!!.audioSessionId)
            aec?.enabled = true
            Log.d("CallManager", "✅ Acoustic Echo Canceler enabled")
        }
        
        if (NoiseSuppressor.isAvailable()) {
            val ns = NoiseSuppressor.create(audioRecord!!.audioSessionId)
            ns?.enabled = true
            Log.d("CallManager", "✅ Noise Suppressor enabled")
        }
        
        audioRecord?.startRecording()
        
        Log.d("CallManager", "🎙️ Audio capture started: sampleRate=$SAMPLE_RATE, chunkSize=$CHUNK_SIZE, sendThreshold=$SEND_THRESHOLD")
        
        Thread {
            val captureBuffer = ByteArray(CHUNK_SIZE)
            val sendBuffer = mutableListOf<Byte>()
            var totalBytesSent = 0
            
            while (isActive) {
                val read = audioRecord?.read(captureBuffer, 0, CHUNK_SIZE) ?: 0
                if (read > 0) {
                    if (!isMuted) {
                        webSocket?.send(captureBuffer.sliceArray(0 until read).toByteString())
                        totalBytesSent += read
                        
                        if (totalBytesSent % 32000 == 0) {  // Log every ~1 second
                            Log.d("CallManager", "📤 Sent ${totalBytesSent} bytes total")
                        }
                        
                        sendBuffer.addAll(captureBuffer.sliceArray(0 until read).toList())
                        
                        if (sendBuffer.size >= SEND_THRESHOLD) {
                            val audioChunk = sendBuffer.toByteArray()
                            if (vad.isSpeech(audioChunk, SAMPLE_RATE)) {
                                Log.d("CallManager", "✅ Speech detected, sending ${audioChunk.size} bytes for STT")
                            } else {
                                Log.d("CallManager", "🔇 No speech detected, skipping STT")
                            }
                            sendBuffer.clear()
                        }
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
