package com.example.voicetranslate

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.voicetranslate.databinding.ActivityCallBinding

class CallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCallBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnEndCall.setOnClickListener {
            finish()
        }
    }
}