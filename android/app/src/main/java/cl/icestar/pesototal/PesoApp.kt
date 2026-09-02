package cl.icestar.pesototal

import android.app.Application

class PesoApp : Application() {

    lateinit var settings: Settings
        private set

    lateinit var tally: Tally
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        settings = Settings(this)
        tally = Tally(settings.tallyStore())
    }

    companion object {
        lateinit var instance: PesoApp
            private set
    }
}
