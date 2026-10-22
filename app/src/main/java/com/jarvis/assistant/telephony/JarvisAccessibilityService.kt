package com.jarvis.assistant.telephony

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class JarvisAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "JarvisAccessibilityService connected and ready.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Accessibility events can be observed for dialer UI changes
    }

    override fun onInterrupt() {
        Log.w(TAG, "JarvisAccessibilityService interrupted.")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
    }

    /**
     * Attempts to find and click the Answer/Accept button across various OEM dialers
     */
    fun answerCallFallback(): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val answerKeywords = listOf(
            "answer", "accept", "receive", "incoming call",
            "swipe to answer", "slide to answer",
            "উত্তর", "ধরুন", "कॉल उठाएं", "स्वीकार"
        )

        return try {
            val clicked = searchAndClick(rootNode, answerKeywords)
            Log.d(TAG, "Accessibility answer call result: $clicked")
            clicked
        } catch (e: Exception) {
            Log.e(TAG, "Error in answerCallFallback", e)
            false
        } finally {
            rootNode.recycle()
        }
    }

    /**
     * Attempts to find and click the End/Hangup button across various OEM dialers
     */
    fun endCallFallback(): Boolean {
        val rootNode = rootInActiveWindow ?: return performGlobalAction(GLOBAL_ACTION_BACK)
        val endKeywords = listOf(
            "end", "end call", "hang up", "reject", "decline",
            "disconnect", "কাটুন", "বাতিল", "कॉल समाप्त", "अस्वीकार"
        )

        return try {
            val clicked = searchAndClick(rootNode, endKeywords)
            Log.d(TAG, "Accessibility end call result: $clicked")
            if (!clicked) {
                // Fallback to back gesture
                performGlobalAction(GLOBAL_ACTION_BACK)
            } else {
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in endCallFallback", e)
            performGlobalAction(GLOBAL_ACTION_BACK)
        } finally {
            rootNode.recycle()
        }
    }

    private fun searchAndClick(node: AccessibilityNodeInfo, keywords: List<String>): Boolean {
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""

        val matches = keywords.any { text.contains(it) || desc.contains(it) }
        if (matches && (node.isClickable || node.parent?.isClickable == true)) {
            val target = if (node.isClickable) node else node.parent
            val success = target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (success) return true
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = searchAndClick(child, keywords)
            child.recycle()
            if (found) return true
        }

        return false
    }

    companion object {
        private const val TAG = "JarvisAccessibility"

        @Volatile
        var instance: JarvisAccessibilityService? = null
            private set

        fun isRunning(): Boolean = instance != null
    }
}
