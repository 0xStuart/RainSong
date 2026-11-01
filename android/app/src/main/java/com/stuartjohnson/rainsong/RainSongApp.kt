package com.stuartjohnson.rainsong

import android.app.Application
import com.stuartjohnson.rainsong.session.RainSongSession

class RainSongApp : Application() {
    lateinit var session: RainSongSession
        private set

    override fun onCreate() {
        super.onCreate()
        session = RainSongSession(this)
    }
}
