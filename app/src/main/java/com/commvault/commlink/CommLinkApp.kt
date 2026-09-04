package com.commvault.commlink

import android.app.Application

class CommLinkApp : Application() {
    companion object {
        lateinit var instance: CommLinkApp
            private set
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
