package com.example.voicetranslate

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.voicetranslate.audio.WavRecorder
import com.example.voicetranslate.databinding.ActivityCallBinding
import com.google.gson.Gson
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException

class CallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCallBinding
    private var wavRecorder: WavRecorder? = null
    private var isRecording = false
    private var isMuted = false
    private var isSpeakerOn = false
    
    private lateinit var backendUrl: String
    private lateinit var languageCode: String
    private val client = OkHttpClient()
    private val gson = Gson()
    private lateinit var audioManager: AudioManager

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startRecordingProcess()
        } else {
            Toast.makeText(this, "Microphone permission required", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        backendUrl = intent.getStringExtra("BACKEND_URL") ?: ""
        languageCode = intent.getStringExtra("LANGUAGE_CODE") ?: "en"
        
        setupUI()
    }

    private fun setupUI() {
        binding.btnPushToTalk.text = "🎙 Hold to Speak"

        binding.btnPushToTalk.setOnClickListener {
            if (isMuted) {
                Toast.makeText(this, "Microphone is muted", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (isRecording) {
                stopRecordingAndUpload()
            } else {
                checkPermissionAndStart()
            }
        }

        binding.btnSpeaker.setOnClickListener { toggleSpeaker() }
        binding.btnMute.setOnClickListener { toggleMute() }
        binding.btnEndCall.setOnClickListener { finish() }
        
        updateButtonStates()
    }

    private fun toggleSpeaker() {
        isSpeakerOn = !isSpeakerOn
        audioManager.isSpeakerphoneOn = isSpeakerOn
        updateButtonStates()
        Toast.makeText(this, if (isSpeakerOn) "Speaker On" else "Speaker Off", Toast.LENGTH_SHORT).show()
    }

    private fun toggleMute() {
        isMuted = !isMuted
        updateButtonStates()
        Toast.makeText(this, if (isMuted) "Microphone Muted" else "Microphone Unmuted", Toast.LENGTH_SHORT).show()
    }

    private fun updateButtonStates() {
        // Visual feedback for speaker and mute buttons
    }

    private fun checkPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startRecordingProcess()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startRecordingProcess() {
        val audioFile = File(cacheDir, "recording.wav")
        wavRecorder = WavRecorder(audioFile)
        wavRecorder?.startRecording()
        isRecording = true
        binding.btnPushToTalk.text = "⏹ Stop Recording"
        binding.tvCallStatus.text = "Status: Recording..."
    }

    private fun stopRecordingAndUpload() {
        wavRecorder?.stopRecording()
        isRecording = false
        binding.btnPushToTalk.text = "🎙 Hold to Speak"
        binding.tvCallStatus.text = "Status: Processing..."
        
        uploadAudio(File(cacheDir, "recording.wav"))
    }

    private fun uploadAudio(file: File) {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, file.asRequestBody("audio/wav".toMediaType()))
            .addFormDataPart("language", languageCode)
            .build()

        val request = Request.Builder()
            .url("$backendUrl/stt")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { binding.tvCallStatus.text = "Status: Error - ${e.message}" }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (response.isSuccessful && body != null) {
                    try {
                        val result = gson.fromJson(body, TranslateResponse::class.java)
                        runOnUiThread {
                            if (result.success) {
                                binding.tvCallStatus.text = "Status: Done"
                                binding.tvYouPlaceholder.text = result.source_text
                                binding.tvTranslatedPlaceholder.text = result.translated_text
                            } else {
                                binding.tvCallStatus.text = "Status: Processing Failed"
                            }
                        }
                    } catch (e: Exception) {
                        runOnUiThread { binding.tvCallStatus.text = "Status: Parsing Error" }
                    }
                } else {
                    runOnUiThread { binding.tvCallStatus.text = "Status: Server Error" }
                }
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        audioManager.isSpeakerphoneOn = false
    }

    data class TranslateResponse(
        val success: Boolean,
        val source_text: String?,
        val translated_text: String?
    )
}

