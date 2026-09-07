package com.example.callrecorder

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 80, 50, 50)
        }

        val infoText = TextView(this).apply {
            text = "Auto Call Recorder\n\n1. Grant Phone & Audio Permissions\n2. Turn ON Accessibility Service"
            textSize = 18f
        }
        layout.addView(infoText)

        val btnPermissions = Button(this).apply {
            text = "1. Grant Permissions"
            setOnClickListener {
                ActivityCompat.requestPermissions(
                    this@MainActivity,
                    arrayOf(
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.READ_PHONE_STATE,
                        Manifest.permission.READ_CALL_LOG,
                        Manifest.permission.POST_NOTIFICATIONS
                    ),
                    100
                )
            }
        }
        layout.addView(btnPermissions)

        val btnAccessibility = Button(this).apply {
            text = "2. Open Accessibility Settings"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        layout.addView(btnAccessibility)

        setContentView(layout)
    }
}
