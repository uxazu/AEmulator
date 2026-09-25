package app.aemu

import android.app.Application
import app.aemu.core.Keeper

class AemuApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Keeper.ensureChannel(this)
    }
}
