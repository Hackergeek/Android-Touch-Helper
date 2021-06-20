package com.zfdang.touchhelper

import android.content.*
import android.util.Log
import com.zfdang.touchhelper.service.TouchHelperService

class TouchHelperServiceReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "TouchHelperServiceReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        // an Intent broadcast, just dispatch message to TouchHelperService
        val action = intent.action
        Log.d(TAG, action)
        if (action!!.contains("PACKAGE_ADDED") || action.contains("PACKAGE_REMOVED")) {
            if (TouchHelperService.serviceImpl != null) {
                TouchHelperService.serviceImpl!!.receiverHandler.sendEmptyMessage(
                    TouchHelperService.ACTION_REFRESH_PACKAGE
                )
            }
        }
    }
}