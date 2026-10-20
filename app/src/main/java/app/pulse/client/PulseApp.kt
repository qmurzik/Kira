package app.pulse.client

import android.app.Application
import app.pulse.client.di.AppContainer

class PulseApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
