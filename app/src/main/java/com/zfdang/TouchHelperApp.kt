package com.zfdang

import android.app.Application
import android.content.Context

class TouchHelperApp : Application() {
    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
    }

    companion object {
        lateinit var appContext: Context
            private set
    }
}