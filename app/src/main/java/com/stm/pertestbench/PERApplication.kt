package com.stm.pertestbench

import android.app.Application
import timber.log.Timber

/**
 * Creates an accessible instance of the application class and
 * installs timber for debug logging if in debug build configuration.
 *
 * @author Claudio Vertemara
 */

class PERApplication: Application() {
    override fun onCreate() {
        super.onCreate()
        app = this

        // Install Timber for Logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }

    /**
     * Contains the application instance.
     *
     * @property app Application Instance
     */
    companion object {
        lateinit var app: Application
    }
}