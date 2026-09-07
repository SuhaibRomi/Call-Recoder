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
import java.io.FileInputStream
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
    private val channels = 1
    private val bitsPerSample = 16

    private var currentPcmFile: File? = null
    private var currentWavFile: File? = null

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
        currentPcmFile = File(dir, "temp_${callIdentifier}_$timestamp.raw")
        currentWavFile = File(dir, "${callIdentifier}_$timestamp.wav")

        recordingThread = thread(start = true) {
            val buffer = ByteArray(bufferSize)
            var outputStream: FileOutputStream? = null
            try {
                outputStream = FileOutputStream(currentPcmFile)
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

        // Raw audio ko direct playable .wav file mein convert karein
        val pcm = currentPcmFile
        val wav = currentWavFile
        if (pcm != null && wav != null && pcm.exists()) {
            thread(start = true) {
                convertRawToWav(pcm, wav)
                pcm.delete() // temporary file delete
            }
        }
    }

    private fun convertRawToWav(rawFile: File, wavFile: File) {
        val rawData = ByteArray(rawFile.length().toInt())
        try {
            FileInputStream(rawFile).use { it.read(rawData) }
            FileOutputStream(wavFile).use { out ->
                val totalAudioLen = rawData.size.toLong()
                val totalDataLen = totalAudioLen + 36
                val byteRate = (sampleRate * channels * bitsPerSample / 8).toLong()

                val header = ByteArray(44)
                header[0] = 'R'.code.toByte()
                header[1] = 'I'.code.toByte()
                header[2] = 'F'.code.toByte()
                header[3] = 'F'.code.toByte()
                header[4] = (totalDataLen and 0xff).toByte()
                header[5] = (totalDataLen shr 8 and 0xff).toByte()
                header[6] = (totalDataLen shr 16 and 0xff).toByte()
                header[7] = (totalDataLen shr 24 and 0xff).toByte()
                header[8] = 'W'.code.toByte()
                header[9] = 'A'.code.toByte()
                header[10] = 'V'.code.toByte()
                header[11] = 'E'.code.toByte()
                header[12] = 'f'.code.toByte()
                header[13] = 'm'.code.toByte()
                header[14] = 't'.code.toByte()
                header[15] = ' '.code.toByte()
                header[16] = 16
                header[17] = 0
                header[18] = 0
                header[19] = 0
                header[20] = 1
                header[21] = 0
                header[22] = channels.toByte()
                header[23] = 0
                header[24] = (sampleRate and 0xff).toByte()
                header[25] = (sampleRate shr 8 and 0xff).toByte()
                header[26] = (sampleRate shr 16 and 0xff).toByte()
                header[27] = (sampleRate shr 24 and 0xff).toByte()
                header[28] = (byteRate and 0xff).toByte()
                header[29] = (byteRate shr 8 and 0xff).toByte()
                header[30] = (byteRate shr 16 and 0xff).toByte()
                header[31] = (byteRate shr 24 and 0xff).toByte()
                header[32] = (channels * bitsPerSample / 8).toByte()
                header[33] = 0
                header[34] = bitsPerSample.toByte()
                header[35] = 0
                header[36] = 'd'.code.toByte()
                header[37] = 'a'.code.toByte()
                header[38] = 't'.code.toByte()
                header[39] = 'a'.code.toByte()
                header[40] = (totalAudioLen and 0xff).toByte()
                header[41] = (totalAudioLen shr 8 and 0xff).toByte()
                header[42] = (totalAudioLen shr 16 and 0xff).toByte()
                header[43] = (totalAudioLen shr 24 and 0xff).toByte()

                out.write(header, 0, 44)
                out.write(rawData)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        stopAudioRecordCapture()
        super.onDestroy()
    }
}
