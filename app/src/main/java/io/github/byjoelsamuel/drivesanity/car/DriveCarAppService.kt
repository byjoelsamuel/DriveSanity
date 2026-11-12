package io.github.byjoelsamuel.drivesanity.car

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.validation.HostValidator
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import io.github.byjoelsamuel.drivesanity.BuildConfig
import io.github.byjoelsamuel.drivesanity.DriveEngine
import io.github.byjoelsamuel.drivesanity.Prefs
import io.github.byjoelsamuel.drivesanity.nav.GeoTarget
import io.github.byjoelsamuel.drivesanity.nav.Nav

/**
 * Android Auto's way in. The app registers as a navigation app, because that's the only kind
 * Android Auto lets draw its own screen. It can take a destination, typed or spoken on the car
 * screen, or sent by the Assistant ("navigate to …"), and shows the route on the Map screen.
 * Since it isn't on the Play Store, Android Auto only shows it if it was installed with the Play
 * mark (see the README), or with "Unknown sources" turned on in its developer settings.
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
    override fun onNewIntent(intent: Intent) {
        handleNavigate(intent)
    }

    /** A "navigate to" request: a spot goes straight to routing; words are looked up first. */
    private fun handleNavigate(intent: Intent) {
        if (intent.action != CarContext.ACTION_NAVIGATE) return
        val uri = intent.data ?: return
        val target = GeoTarget.parse(uri.schemeSpecificPart.substringBefore('?'), uri.getQueryParameter("q")) ?: return
        Prefs.setLayout(carContext, Prefs.Layout.MAP)
        when (target) {
            is GeoTarget.At -> Nav.start(target.place)
            is GeoTarget.Search -> Nav.search(target.words, DriveEngine.snapshot().position) { r ->
                r.getOrNull()?.firstOrNull()?.let(Nav::start)
            }
        }
    }

    override fun onCreateScreen(intent: Intent): Screen {
        handleNavigate(intent)
        DriveEngine.hold(carContext, DriveEngine.CAR)
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) = DriveEngine.release(DriveEngine.CAR)
        })
        return DashScreen(carContext)
    }
}
