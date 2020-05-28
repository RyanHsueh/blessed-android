package com.welie.blessedexample

import android.app.Application
import timber.log.Timber

class BleApp: Application() {

    override fun onCreate() {
        super.onCreate()

        Timber.d("onCreate")
    }

}