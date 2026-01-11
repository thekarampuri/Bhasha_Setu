package com.example.voicetranslate.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavRecorder(private val outputFile: File) {

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingThread: Thread? = null

    @SuppressLint("MissingPermission")
    fun startRecording() {
        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            minBufferSize
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e("WavRecorder", "AudioRecord initialization failed")
            return
        }

        isRecording = true
        audioRecord?.startRecording()

        recordingThread = Thread {
            writeAudioDataToFile()
        }.apply { start() }
    }

    private fun writeAudioDataToFile() {
        val data = ByteArray(1024)
        FileOutputStream(outputFile).use { out ->
            // Write placeholder for WAV header
            out.write(ByteArray(44))

            while (isRecording) {
                val read = audioRecord?.read(data, 0, data.size) ?: 0
                if (read > 0) {
                    out.write(data, 0, read)
                }
            }
        }
        updateWavHeader(outputFile)
    }

    fun stopRecording() {
        isRecording = false
        recordingThread?.join()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    private fun updateWavHeader(file: File) {
        val fileSize = file.length()
        val dataSize = fileSize - 44
        val header = createWavHeader(dataSize)
        
        val randomAccessFile = java.io.RandomAccessFile(file, "rw")
        randomAccessFile.seek(0)
        randomAccessFile.write(header)
        randomAccessFile.close()
    }

    private fun createWavHeader(dataSize: Long): ByteArray {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray())
        header.putInt((dataSize + 36).toInt())
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16) // Subchunk1Size
        header.putShort(1.toShort()) // AudioFormat (PCM)
        header.putShort(1.toShort()) // NumChannels
        header.putInt(SAMPLE_RATE)
        header.putInt(SAMPLE_RATE * 2) // ByteRate
        header.putShort(2.toShort()) // BlockAlign
        header.putShort(16.toShort()) // BitsPerSample
        header.put("data".toByteArray())
        header.putInt(dataSize.toInt())
        return header.array()
    }
}