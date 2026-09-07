package com.example.callrecorder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

class CallReceiver : BroadcastReceiver() {

    companion object {
        private var lastState = TelephonyManager.CALL_STATE_IDLE
    }

    override fun onReceive(context: Context, intent: Intent) {
        val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val state = when (stateStr) {
            TelephonyManager.EXTRA_STATE_OFFHOOK -> TelephonyManager.CALL_STATE_OFFHOOK
            TelephonyManager.EXTRA_STATE_IDLE -> TelephonyManager.CALL_STATE_IDLE
            else -> return
        }

        if (state != lastState) {
            when (state) {
                TelephonyManager.CALL_STATE_OFFHOOK -> {
                    val serviceIntent = Intent(context, RecordingService::class.java).apply {
                        action = "ACTION_START"
                    }
                    ContextCompat.startForegroundService(context, serviceIntent)
                }
                TelephonyManager.CALL_STATE_IDLE -> {
                    val serviceIntent = Intent(context, RecordingService::class.java).apply {
                        action = "ACTION_STOP"
                    }
                    context.startService(serviceIntent)
                }
            }
            lastState = state
        }
    }
}

