package com.example.voicetranslate

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
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
    private lateinit var backendUrl: String
    private val client = OkHttpClient()
    private val gson = Gson()

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

        backendUrl = intent.getStringExtra("BACKEND_URL") ?: "http://192.168.1.10:8000"
        binding.btnPushToTalk.text = "🎙 Hold to Speak"

        binding.btnPushToTalk.setOnClickListener {
            if (isRecording) {
                stopRecordingAndUpload()
            } else {
                checkPermissionAndStart()
            }
        }

        binding.btnEndCall.setOnClickListener {
            finish()
        }
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
        binding.tvCallStatus.text = "Status: Processing STT..."
        
        uploadAudio(File(cacheDir, "recording.wav"))
    }

    private fun uploadAudio(file: File) {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, file.asRequestBody("audio/wav".toMediaType()))
            .build()

        val request = Request.Builder()
            .url("$backendUrl/stt")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    binding.tvCallStatus.text = "Status: Error - ${e.message}"
                    Toast.makeText(this@CallActivity, "Network Error", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (response.isSuccessful && body != null) {
                    val sttResponse = gson.fromJson(body, SttResponse::class.java)
                    runOnUiThread {
                        binding.tvCallStatus.text = "Status: Idle"
                        binding.tvYouPlaceholder.text = sttResponse.text
                    }
                } else {
                    runOnUiThread {
                        binding.tvCallStatus.text = "Status: Server Error"
                    }
                }
            }
        })
    }

    data class SttResponse(val success: Boolean, val text: String, val language: String?)
}