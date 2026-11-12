package io.github.byjoelsamuel.drivesanity

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import io.github.byjoelsamuel.drivesanity.obd.Obd

/**
 * What the phone needs for the app to keep working while you drive, each with its fix. Phone makers
 * (Samsung, OnePlus and the rest) put their own battery savers on top of Android's, and those are
 * what usually stops the app from running with the screen off or in the background.
 */
object PhoneCheck {
    enum class Fix { APP_SETTINGS, NOTIFICATIONS, BATTERY, INSTALL_HELP }

    data class Item(val ok: Boolean, val title: String, val detail: String, val fix: Fix?, val fixLabel: String = "Fix")

    private const val PLAY = "com.android.vending"
    const val INSTALL_HELP_URL = "https://github.com/byjoelsamuel/DriveSanity#on-the-phone-and-in-the-car-android-auto"

    /** Whether the app was installed so Android Auto will list it (the Play mark). Null if it can't be told. */
    fun hasPlayMark(ctx: Context): Boolean? = runCatching {
        val pm = ctx.packageManager
        val installer = if (Build.VERSION.SDK_INT >= 30) {
            pm.getInstallSourceInfo(ctx.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            pm.getInstallerPackageName(ctx.packageName)
        }
        installer == PLAY
    }.getOrNull()

    fun items(ctx: Context): List<Item> = buildList {
        when (hasPlayMark(ctx)) {
            true -> add(Item(true, "Installed for Android Auto", "Android Auto will list the app.", null))
            false -> add(
                Item(
                    false, "Not installed for Android Auto",
                    "It was installed without the Google Play mark, so Android Auto won't list it. On the phone it works fine. For the car, start Shizuku and install again with UnknownAuto.",
                    Fix.INSTALL_HELP, "Show the steps",
                ),
            )
            null -> {}
        }
        val fine = ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        add(
            Item(
                fine, "Precise location",
                if (fine) "Speed comes from GPS." else "Allow \"Precise\" location, or there's no speed from GPS. Choose \"While using the app\".",
                Fix.APP_SETTINGS,
            ),
        )
        if (Build.VERSION.SDK_INT >= 33) {
            val on = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
            add(
                Item(
                    on, "Notifications",
                    if (on) "The \"reading speed\" notification can show, which keeps the app awake." else "Without the notification Android may stop the app when you switch away from it.",
                    Fix.NOTIFICATIONS,
                ),
            )
        }
        if (Build.VERSION.SDK_INT >= 31) {
            val bt = Obd.hasBluetoothPermission(ctx)
            add(
                Item(
                    bt, "Nearby devices (Bluetooth)",
                    if (bt) "The OBD2 adapter can connect." else "Allow \"Nearby devices\" so the app can find your OBD2 adapter.",
                    Fix.APP_SETTINGS,
                ),
            )
        }
        val awake = ctx.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(ctx.packageName) == true
        add(
            Item(
                awake, "Battery: not restricted",
                if (awake) "Android won't put the app to sleep while you drive." else "Android may put the app to sleep with the screen off, which drops the OBD2 link and GPS.",
                Fix.BATTERY, "Allow",
            ),
        )
    }

    /** Extra, phone-maker-specific steps, or null for a phone with nothing special. */
    fun brandTip(manufacturer: String = Build.MANUFACTURER): String? = when (manufacturer.lowercase()) {
        "samsung" -> "Samsung: Settings → Battery → Background usage limits → turn off \"Put unused apps to sleep\", and add DriveSanity to \"Never sleeping apps\". In Settings → Apps → DriveSanity → Battery choose \"Unrestricted\"."
        "oneplus", "oppo", "realme" -> "OnePlus / Oppo / Realme: Settings → Apps → DriveSanity → Battery usage → \"Allow background activity\", and turn on \"Allow auto-launch\" if it's there. Settings → Battery → Battery optimization → DriveSanity → \"Don't optimize\". Then open the recent apps view, tap the app's icon and choose \"Lock\" so it isn't cleared."
        "xiaomi", "redmi", "poco" -> "Xiaomi: Settings → Apps → Manage apps → DriveSanity → Autostart on, and Battery saver → \"No restrictions\"."
        "huawei", "honor" -> "Huawei / Honor: Settings → Apps → DriveSanity → Battery → turn on \"Launch\" settings manually and allow auto-launch, secondary launch and background activity."
        "vivo", "iqoo" -> "Vivo: Settings → Battery → Background power consumption → DriveSanity → \"Allow\". Also allow autostart."
        else -> null
    }

    /** Opens the right settings page. Falls back to the app's own page if the phone doesn't have the one asked for. */
    fun fix(ctx: Context, fix: Fix) {
        val pkg = Uri.parse("package:${ctx.packageName}")
        val tries: List<Intent> = when (fix) {
            Fix.APP_SETTINGS -> listOf(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg))
            Fix.NOTIFICATIONS -> listOf(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName),
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg),
            )
            Fix.BATTERY -> listOf(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg),
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg),
            )
            Fix.INSTALL_HELP -> listOf(Intent(Intent.ACTION_VIEW, Uri.parse(INSTALL_HELP_URL)))
        }
        for (i in tries) {
            try {
                ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
    }
}
