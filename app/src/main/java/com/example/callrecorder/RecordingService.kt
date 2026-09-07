package com.example.callrecorder

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class RecordingService : Service() {

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingThread: Thread? = null

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val callIdentifier = intent?.getStringExtra("CALL_IDENTIFIER") ?: "Unknown"

        if (action == "START_RECORDING" && !isRecording) {
            startAudioRecordCapture(callIdentifier)
        } else if (action == "STOP_RECORDING" && isRecording) {
            stopAudioRecordCapture()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startForegroundService() {
        val channelId = "call_recording_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Call Recorder Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Call Recorder Active")
            .setContentText("Recording call audio...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()

        startForeground(1, notification)
    }

    @SuppressLint("MissingPermission")
    private fun startAudioRecordCapture(callIdentifier: String) {
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            return
        }

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize * 2
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            return
        }

        isRecording = true
        audioRecord?.startRecording()

        val dir = File(getExternalFilesDir(null), "Recordings")
        if (!dir.exists()) dir.mkdirs()

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        // File save format: ContactName_Date_Time.pcm ya PhoneNum_Date_Time.pcm
        val pcmFile = File(dir, "${callIdentifier}_$timestamp.pcm")

        recordingThread = thread(start = true) {
            val buffer = ByteArray(bufferSize)
            var outputStream: FileOutputStream? = null
            try {
                outputStream = FileOutputStream(pcmFile)
                while (isRecording) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        outputStream.write(buffer, 0, read)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                outputStream?.flush()
                outputStream?.close()
            }
        }
    }

    private fun stopAudioRecordCapture() {
        isRecording = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            audioRecord = null
            recordingThread = null
        }
    }

    override fun onDestroy() {
        stopAudioRecordCapture()
        super.onDestroy()
    }
}
