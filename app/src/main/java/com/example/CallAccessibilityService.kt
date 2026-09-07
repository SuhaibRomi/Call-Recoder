package com.example.callrecorder

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class CallAccessibilityService : AccessibilityService() {

    companion object {
        var currentCallerId: String = "Unknown"
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val rootNode = rootInActiveWindow ?: return
        extractCallerInfo(rootNode)
    }

    private fun extractCallerInfo(node: AccessibilityNodeInfo) {
        val text = node.text?.toString()?.trim()
        if (!text.isNullOrEmpty() && text.length >= 3) {
            if (text.any { it.isDigit() } || text.contains("Calling") || text.contains("Incoming")) {
                val cleaned = text.replace("Calling", "").replace("Incoming", "").trim()
                if (cleaned.isNotEmpty()) {
                    currentCallerId = cleaned
                }
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            extractCallerInfo(child)
            child.recycle()
        }
    }

    override fun onInterrupt() {}
}
