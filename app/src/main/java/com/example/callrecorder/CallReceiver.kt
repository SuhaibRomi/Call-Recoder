package com.example.callrecorder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

class CallReceiver : BroadcastReceiver() {

    companion object {
        private var lastState = TelephonyManager.CALL_STATE_IDLE
        private var activeNumber: String? = null
        private var isIncoming = false
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action

        // Outgoing call number
        if (action == Intent.ACTION_NEW_OUTGOING_CALL) {
            val outNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER)
            if (!outNumber.isNullOrEmpty()) {
                activeNumber = outNumber
            }
            isIncoming = false
            return
        }

        // Phone state changes (Incoming & Call Transitions)
        if (action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

            if (!incomingNumber.isNullOrEmpty()) {
                activeNumber = incomingNumber
                isIncoming = true
            }

            var state = TelephonyManager.CALL_STATE_IDLE
            when (stateStr) {
                TelephonyManager.EXTRA_STATE_RINGING -> {
                    state = TelephonyManager.CALL_STATE_RINGING
                    if (!incomingNumber.isNullOrEmpty()) {
                        activeNumber = incomingNumber
                        isIncoming = true
                    }
                }
                TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                    state = TelephonyManager.CALL_STATE_OFFHOOK
                }
                TelephonyManager.EXTRA_STATE_IDLE -> {
                    state = TelephonyManager.CALL_STATE_IDLE
                }
            }

            handleStateTransition(context, state)
        }
    }

    private fun handleStateTransition(context: Context, currentState: Int) {
        if (lastState == currentState) return

        when (currentState) {
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                // Call connected -> Resolve name accurately
                val currentNumber = activeNumber
                val displayName = getContactNameOrFallback(context, currentNumber)

                val serviceIntent = Intent(context, RecordingService::class.java).apply {
                    action = "START_RECORDING"
                    putExtra("CALL_IDENTIFIER", displayName)
                }
                ContextCompat.startForegroundService(context, serviceIntent)
            }

            TelephonyManager.CALL_STATE_IDLE -> {
                // Call disconnected -> Stop & CLEANUP memory to prevent mixing up next call
                val serviceIntent = Intent(context, RecordingService::class.java).apply {
                    action = "STOP_RECORDING"
                }
                context.startService(serviceIntent)

                // Pure memory reset for next call
                activeNumber = null
                isIncoming = false
            }
        }

        lastState = currentState
    }

    private fun getContactNameOrFallback(context: Context, number: String?): String {
        if (number.isNullOrBlank()) return "Unknown"

        var resolvedName: String? = null
        val uri: Uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(number)
        )
        val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)

        try {
            val cursor: Cursor? = context.contentResolver.query(uri, projection, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        val fetched = it.getString(nameIndex)
                        if (!fetched.isNullOrBlank()) {
                            resolvedName = fetched
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Agar name mil jaye toh Name use karein, warna original Phone Number use karein
        val finalTarget = resolvedName ?: number

        return finalTarget
            .replace("[^a-zA-Z0-9_+\\s-]".toRegex(), "")
            .trim()
            .replace("\\s+".toRegex(), "_")
    }
}
