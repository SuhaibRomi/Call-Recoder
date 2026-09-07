package com.example.callrecorder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private var mediaPlayer: MediaPlayer? = null
    private var recordingFiles = ArrayList<File>()

    private val requiredPermissions = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        add(Manifest.permission.READ_PHONE_STATE)
        add(Manifest.permission.READ_CALL_LOG)
        add(Manifest.permission.READ_CONTACTS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnPermissions = findViewById<Button>(R.id.btnGrantPermissions)
        val btnAccessibility = findViewById<Button>(R.id.btnOpenAccessibility)
        listView = findViewById(R.id.recordingsListView)

        btnPermissions.setOnClickListener {
            requestAppPermissions()
        }

        btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        // Tap to play audio
        listView.setOnItemClickListener { _, _, position, _ ->
            val fileToPlay = recordingFiles[position]
            playAudio(fileToPlay)
        }
    }

    override fun onResume() {
        super.onResume()
        loadRecordings()
    }

    private fun loadRecordings() {
        val dir = File(getExternalFilesDir(null), "Recordings")
        recordingFiles.clear()
        val fileDisplayNames = ArrayList<String>()

        if (dir.exists()) {
            val files = dir.listFiles { file -> file.extension.equals("wav", ignoreCase = true) }
            if (files != null) {
                // Newest recording first
                files.sortByDescending { it.lastModified() }
                for (f in files) {
                    recordingFiles.add(f)
                    fileDisplayNames.add("▶  ${f.nameWithoutExtension.replace('_', ' ')}")
                }
            }
        }

        if (fileDisplayNames.isEmpty()) {
            fileDisplayNames.add("No recordings found yet.")
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, fileDisplayNames)
        listView.adapter = adapter
    }

    private fun playAudio(file: File) {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                start()
            }
            Toast.makeText(this, "Playing: ${file.name}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to play audio", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestAppPermissions() {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 101)
        } else {
            Toast.makeText(this, "All permissions granted!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        mediaPlayer?.release()
        mediaPlayer = null
        super.onDestroy()
    }
}
