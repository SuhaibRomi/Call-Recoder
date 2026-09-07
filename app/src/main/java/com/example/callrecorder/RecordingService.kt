package com.example.callrecorder

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream

class RecordingService : Service() {

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var workerThread: Thread? = null

    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val gainMultiplier = 2.5f

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "ACTION_START" -> startRecording()
            "ACTION_STOP" -> stopRecording()
        }
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startRecording() {
        showNotification()
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            sampleRate,
            channelConfig,
            audioFormat,
            minBufferSize * 2
        )

        val tempPcmFile = File(filesDir, "temp_stream.pcm")
        isRecording = true
        audioRecord?.startRecording()

        workerThread = Thread {
            val buffer = ShortArray(minBufferSize)
            val outputStream = DataOutputStream(FileOutputStream(tempPcmFile).buffered())

            try {
                while (isRecording) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    for (i in 0 until read) {
                        val boosted = (buffer[i] * gainMultiplier).toInt()
                        val clamped = boosted.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                        outputStream.writeShort(java.lang.Short.reverseBytes(clamped).toInt())
                    }
                }
            } finally {
                outputStream.flush()
                outputStream.close()

                val caller = CallAccessibilityService.currentCallerId.replace("[^a-zA-Z0-9+]".toRegex(), "_")
                val finalM4aFile = File(getExternalFilesDir(null), "Call_${caller}_${System.currentTimeMillis()}.m4a")

                AudioConverter.convertPcmToM4a(tempPcmFile, finalM4aFile, sampleRate, 1)
            }
        }.also { it.start() }
    }

    private fun stopRecording() {
        isRecording = false
        workerThread?.join(1500)
        audioRecord?.apply {
            if (state == AudioRecord.STATE_INITIALIZED) stop()
            release()
        }
        audioRecord = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun showNotification() {
        val channelId = "recorder_channel"
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(channelId, "Call Recorder", NotificationManager.IMPORTANCE_LOW)
        manager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Call Recording Active")
            .setContentText("Recording in progress...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()

        startForeground(101, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
