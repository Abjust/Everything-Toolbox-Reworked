package com.msst.toolbox

import android.app.Application

class ToolboxApplication : Application() {
    companion object {
        lateinit var instance: ToolboxApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}