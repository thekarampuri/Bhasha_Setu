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
import com.example.voicetranslate.util.Constants

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

    private val SAMPLE_RATE = Constants.Audio.SAMPLE_RATE
    private val CHANNEL_IN = Constants.Audio.CHANNEL_IN
    private val CHANNEL_OUT = Constants.Audio.CHANNEL_OUT
    private val ENCODING = Constants.Audio.ENCODING
    
    private val CHUNK_SIZE = Constants.Audio.CHUNK_SIZE
    private val SEND_THRESHOLD = Constants.Audio.SEND_THRESHOLD
    
    private val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, ENCODING).coerceAtLeast(CHUNK_SIZE) * 2

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var isActive = false
    private var isMuted = false
    
    private val GAIN_FACTOR = Constants.Audio.GAIN_FACTOR
    
    private val vad = VoiceActivityDetector(
        energyThreshold = Constants.VAD.ENERGY_THRESHOLD,
        minSpeechDurationMs = Constants.VAD.MIN_SPEECH_DURATION_MS
    )
    
    private var lastTranscript = ""
    private var lastTranscriptTime = 0L
    
    // Push-to-Talk mode
    private var isPushToTalkMode = false
    private var isPushToTalkActive = false

    fun setMuted(muted: Boolean) {
        isMuted = muted
    }
    
    fun setPushToTalkMode(enabled: Boolean) {
        isPushToTalkMode = enabled
        Log.d(Constants.Log.TAG_CALL_MANAGER, "Push-to-Talk mode: ${if (enabled) "ENABLED" else "DISABLED"}")
    }
    
    fun setPushToTalkActive(active: Boolean) {
        if (isPushToTalkMode) {
            isPushToTalkActive = active
            Log.d(Constants.Log.TAG_CALL_MANAGER, "Push-to-Talk: ${if (active) "PRESSED" else "RELEASED"}")
        }
    }
    
    private fun shouldSendAudio(): Boolean {
        // Don't send if muted
        if (isMuted) return false
        
        // In PTT mode, only send when button is pressed
        if (isPushToTalkMode) {
            return isPushToTalkActive
        }
        
        // In continuous mode, always send
        return true
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
                            Log.d(Constants.Log.TAG_CALL_MANAGER, "Suppressed duplicate: $source")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(Constants.Log.TAG_CALL_MANAGER, "Error parsing JSON: ${e.message}")
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
            Log.d(Constants.Log.TAG_CALL_MANAGER, "✅ Acoustic Echo Canceler enabled")
        }
        
        if (NoiseSuppressor.isAvailable()) {
            val ns = NoiseSuppressor.create(audioRecord!!.audioSessionId)
            ns?.enabled = true
            Log.d(Constants.Log.TAG_CALL_MANAGER, "✅ Noise Suppressor enabled")
        }
        
        audioRecord?.startRecording()
        
        Log.d(Constants.Log.TAG_CALL_MANAGER, "🎙️ Audio capture started: sampleRate=$SAMPLE_RATE, chunkSize=$CHUNK_SIZE, sendThreshold=$SEND_THRESHOLD")
        
        Thread {
            val captureBuffer = ByteArray(CHUNK_SIZE)
            val sendBuffer = mutableListOf<Byte>()
            var totalBytesSent = 0
            
            while (isActive) {
                val read = audioRecord?.read(captureBuffer, 0, CHUNK_SIZE) ?: 0
                if (read > 0) {
                    if (shouldSendAudio()) {
                        webSocket?.send(captureBuffer.sliceArray(0 until read).toByteString())
                        totalBytesSent += read
                        
                        if (totalBytesSent % 32000 == 0) {  // Log every ~1 second
                            Log.d(Constants.Log.TAG_CALL_MANAGER, "📤 Sent ${totalBytesSent} bytes total")
                        }
                        
                        sendBuffer.addAll(captureBuffer.sliceArray(0 until read).toList())
                        
                        if (sendBuffer.size >= SEND_THRESHOLD) {
                            val audioChunk = sendBuffer.toByteArray()
                            if (vad.isSpeech(audioChunk, SAMPLE_RATE)) {
                                Log.d(Constants.Log.TAG_CALL_MANAGER, "✅ Speech detected, sending ${audioChunk.size} bytes for STT")
                            } else {
                                Log.d(Constants.Log.TAG_CALL_MANAGER, "🔇 No speech detected, skipping STT")
                            }
                            sendBuffer.clear()
                        }
                    } else {
                        // Clear buffer when not sending to prevent stale audio
                        sendBuffer.clear()
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
