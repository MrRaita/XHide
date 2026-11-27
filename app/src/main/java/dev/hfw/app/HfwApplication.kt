package dev.hfw.app

import android.app.Application

class HfwApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Services.get(this).syncer.syncAsync() // replay queued edits as soon as the process starts
    }
}
