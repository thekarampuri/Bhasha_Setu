package com.example.voicetranslate.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

class MicLoopback {

    companion object {
        private const val TAG = "MicLoopback"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_MONO
        private const val CHANNEL_CONFIG_OUT = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var isRunning = AtomicBoolean(false)
    private var loopbackThread: Thread? = null

    private val minBufferSize = AudioRecord.getMinBufferSize(
        SAMPLE_RATE,
        CHANNEL_CONFIG_IN,
        AUDIO_FORMAT
    )

    @SuppressLint("MissingPermission")
    fun start() {
        if (isRunning.get()) return

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG_IN,
                AUDIO_FORMAT,
                minBufferSize
            )

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AUDIO_FORMAT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_CONFIG_OUT)
                        .build()
                )
                .setBufferSizeInBytes(minBufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED ||
                audioTrack?.state != AudioTrack.STATE_INITIALIZED
            ) {
                Log.e(TAG, "AudioRecord or AudioTrack failed to initialize")
                release()
                return
            }

            isRunning.set(true)
            loopbackThread = Thread {
                runLoopback()
            }.apply { start() }

        } catch (e: Exception) {
            Log.e(TAG, "Error starting loopback", e)
            release()
        }
    }

    private fun runLoopback() {
        val buffer = ShortArray(minBufferSize / 2)
        
        try {
            audioRecord?.startRecording()
            audioTrack?.play()

            while (isRunning.get()) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    audioTrack?.write(buffer, 0, read)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in loopback thread", e)
        } finally {
            try {
                audioRecord?.stop()
                audioTrack?.stop()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun stop() {
        isRunning.set(false)
        loopbackThread?.join(500)
        release()
    }

    private fun release() {
        audioRecord?.release()
        audioTrack?.release()
        audioRecord = null
        audioTrack = null
        loopbackThread = null
    }
}