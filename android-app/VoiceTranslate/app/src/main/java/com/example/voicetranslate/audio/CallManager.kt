package com.example.voicetranslate.audio

import android.annotation.SuppressLint
import android.media.*
import android.util.Log
import okhttp3.*
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.util.concurrent.TimeUnit

class CallManager(
    private val backendUrl: String, // e.g., "192.168.1.10:8000"
    private val callId: String,
    private val sourceLang: String,
    private val targetLang: String,
    private val listener: CallListener
) {
    interface CallListener {
        fun onTranscriptionReceived(source: String, translated: String)
        fun onError(message: String)
        fun onConnected()
        fun onDisconnected()
    }

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    
    private var webSocket: WebSocket? = null
    
    // Audio Config: 16kHz, Mono, 16-bit PCM
    private val sampleRate = 16000
    private val channelIn = AudioFormat.CHANNEL_IN_MONO
    private val channelOut = AudioFormat.CHANNEL_OUT_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelIn, audioFormat)

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var isCalling = false

    fun startCall() {
        // Construct WebSocket URL: ws://host/ws/call/id/src/target
        val wsUrl = "ws://$backendUrl/ws/call/$callId/$sourceLang/$targetLang"
        Log.d("CallManager", "Connecting to $wsUrl")

        val request = Request.Builder().url(wsUrl).build()
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isCalling = true
                startRecording()
                startPlayback()
                listener.onConnected()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                // Handle JSON metadata (transcriptions)
                try {
                    val json = android.util.JsonReader(text.reader())
                    var source = ""
                    var translated = ""
                    json.beginObject()
                    while (json.hasNext()) {
                        when (json.nextName()) {
                            "source" -> source = json.nextString()
                            "translated" -> translated = json.nextString()
                            else -> json.skipValue()
                        }
                    }
                    json.endObject()
                    listener.onTranscriptionReceived(source, translated)
                } catch (e: Exception) {
                    Log.e("CallManager", "JSON Parse Error: ${e.message}")
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                // Received translated raw PCM from backend
                playAudio(bytes.toByteArray())
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                listener.onError("Connection Failed: ${t.message}")
                stopCall()
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                listener.onDisconnected()
                stopCall()
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun startRecording() {
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelIn,
                audioFormat,
                bufferSize
            )
            
            audioRecord?.startRecording()

            Thread {
                val audioData = ByteArray(bufferSize)
                while (isCalling) {
                    val read = audioRecord?.read(audioData, 0, audioData.size) ?: 0
                    if (read > 0) {
                        // Send raw PCM bytes
                        webSocket?.send(audioData.sliceArray(0 until read).toByteString())
                    }
                }
            }.start()
        } catch (e: Exception) {
            listener.onError("Recording Error: ${e.message}")
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
                    .setEncoding(audioFormat)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelOut)
                    .build())
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            
            audioTrack?.play()
        } catch (e: Exception) {
            listener.onError("Playback Error: ${e.message}")
        }
    }

    private fun playAudio(data: ByteArray) {
        audioTrack?.write(data, 0, data.size)
    }

    fun stopCall() {
        isCalling = false
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
        webSocket?.close(1000, "Call Ended")
        webSocket = null
    }
}
