package com.zfdang.touchhelper.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class TouchHelperService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        if (serviceImpl == null) {
            serviceImpl = TouchHelperServiceImpl(this)
        }
        if (serviceImpl != null) {
            serviceImpl!!.onServiceConnected()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (serviceImpl != null) {
            serviceImpl!!.onAccessibilityEvent(event)
        }
    }

    override fun onInterrupt() {
        if (serviceImpl != null) {
            serviceImpl!!.onInterrupt()
        }
    }

    override fun onUnbind(intent: Intent): Boolean {
        if (serviceImpl != null) {
            serviceImpl!!.onUnbind(intent)
            serviceImpl = null
        }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        serviceImpl = null
        super.onDestroy()
    }

    companion object {
        const val TAG = "TouchHelperService"
        const val ACTION_REFRESH_KEYWORDS = 1
        const val ACTION_REFRESH_PACKAGE = 2
        const val ACTION_REFRESH_CUSTOMIZED_ACTIVITY = 3
        const val ACTION_ACTIVITY_CUSTOMIZATION = 4
        const val ACTION_STOP_SERVICE = 5

        @SuppressLint("StaticFieldLeak")
        var serviceImpl: TouchHelperServiceImpl? = null
    }
}