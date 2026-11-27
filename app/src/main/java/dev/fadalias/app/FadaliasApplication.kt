package dev.fadalias.app

import android.app.Application

class FadaliasApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Services.get(this).syncer.syncAsync() // replay queued edits as soon as the process starts
    }
}
