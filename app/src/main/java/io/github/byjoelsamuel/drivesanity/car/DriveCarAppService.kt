package io.github.byjoelsamuel.drivesanity.car

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.validation.HostValidator
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import io.github.byjoelsamuel.drivesanity.BuildConfig
import io.github.byjoelsamuel.drivesanity.DriveEngine

/**
 * Android Auto's way in. The app registers as a navigation app, because that's the only kind
 * Android Auto lets draw its own screen; it never starts navigation. Since it isn't on the Play
 * Store, Android Auto only shows it with "Unknown sources" turned on in its developer settings.
 */
class DriveCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator =
        if (BuildConfig.DEBUG) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(applicationContext)
                .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
                .build()
        }

    override fun onCreateSession(sessionInfo: SessionInfo): Session = DriveSession()
}

class DriveSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        DriveEngine.hold(carContext, DriveEngine.CAR)
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) = DriveEngine.release(DriveEngine.CAR)
        })
        return DashScreen(carContext)
    }
}
