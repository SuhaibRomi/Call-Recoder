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
        private var savedNumber: String? = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_NEW_OUTGOING_CALL) {
            savedNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER)
            return
        }

        val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        if (!incomingNumber.isNullOrEmpty()) {
            savedNumber = incomingNumber
        }

        var state = TelephonyManager.CALL_STATE_IDLE
        if (stateStr == TelephonyManager.EXTRA_STATE_OFFHOOK) {
            state = TelephonyManager.CALL_STATE_OFFHOOK
        } else if (stateStr == TelephonyManager.EXTRA_STATE_IDLE) {
            state = TelephonyManager.CALL_STATE_IDLE
        }

        onCustomCallStateChanged(context, state, savedNumber)
    }

    private fun onCustomCallStateChanged(context: Context, state: Int, number: String?) {
        if (lastState == state) return

        when (state) {
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                // Call connect hui -> Start Recording with Contact Name / Number
                val displayName = getContactNameOrNumber(context, number)
                val serviceIntent = Intent(context, CallRecordingService::class.java).apply {
                    action = "START_RECORDING"
                    putExtra("CALL_IDENTIFIER", displayName)
                }
                ContextCompat.startForegroundService(context, serviceIntent)
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                // Call end hui -> Stop Recording
                val serviceIntent = Intent(context, CallRecordingService::class.java).apply {
                    action = "STOP_RECORDING"
                }
                context.startService(serviceIntent)
                savedNumber = null
            }
        }
        lastState = state
    }

    private fun getContactNameOrNumber(context: Context, number: String?): String {
        if (number.isNullOrBlank()) return "Unknown"

        var contactName = number
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
                        contactName = it.getString(nameIndex)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Clean file name characters (spaces to underscores, remove special chars)
        return contactName.replace("[^a-zA-Z0-9_+\\s-]".toRegex(), "").trim().replace("\\s+".toRegex(), "_")
    }
}
