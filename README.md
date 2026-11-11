# DriveSanity

Live driving data on your phone and in **Android Auto**: speed, a g-force circle and how
hard you're braking. Barebones on purpose. It's a first try before adding an OBD2 adapter.

## What it shows

| | Where it comes from |
|---|---|
| **Speed** | Phone GPS (about once a second), big in **MPH** with km/h underneath; the car's own speed over OBD2 when GPS drops out. Tap the phone dashboard to swap units. |
| **Brake** | How hard you're slowing down, in g: **light** ≥ 0.10, **moderate** ≥ 0.25, **hard** ≥ 0.40, **very hard** ≥ 0.60. A normal stop is 0.1–0.3 g; an emergency stop in a road car is 0.7–1.0 g. |
| **RPM** | OBD2. A segmented bar, 0–8,000, white fading to yellow and red from the 6,300 redline. Change the range in `Revs` in `DriveMath.kt`. |
| **Boxes** | **Coolant** (blue while the engine is still cold, red if it runs hot), **intake air**, **battery** (amber under 12.2 V, red under 11.8 V or over 15.2 V) and **trip** distance. Temperatures in °F with MPH, °C with km/h. |
| **Fuel** | OBD2 fuel level (PID 2F), E to F; amber under a quarter, red under an eighth. Not every car reports it; if yours doesn't, the meter says so. |
| **G-meter** | Phone accelerometer, smoothed over ~0.2 s. The dot is the car's acceleration: up = speeding up, down = braking, left/right = turning. Rings every 0.25 g, with a 2-second trail. |
| **Trip stats** (phone) | Distance, moving time, average and top speed, peak brake/accel/turn g, and counts of hard brakes (≥ 0.40 g), hard launches (≥ 0.35 g) and hard turns (≥ 0.40 g). Long-press **Reset** to start over. |
| **Recording** | **Rec** writes a CSV row every 0.1 s; **Share** sends the latest one. |

On a wide screen (the car, or the phone on its side) brake, speed, RPM and the boxes stack on
the left, with a small g-meter and the fuel meter on the right. On a tall phone screen fuel and
the g-meter go underneath.

CSV columns: `time_s, lat, lon, gps_speed_kmh, gps_accuracy_m, gps_accel_g, long_g, lat_g,
brake_g, brake_level, rpm, obd_speed_kmh, throttle_pct, load_pct, coolant_c, intake_c, fuel_pct, battery_v`.

## OBD2

Any ELM327-compatible Bluetooth adapter: classic Bluetooth or Bluetooth LE.

1. Plug the adapter into the car's OBD2 port (under the dash, near the steering column) and
   turn the ignition on.
2. **Classic Bluetooth adapters:** pair it first in the phone's Bluetooth settings (the PIN is
   usually `1234` or `0000`). **Bluetooth LE adapters** don't need pairing.
3. In DriveSanity tap **OBD2**, allow "Nearby devices", and pick the adapter from the list.

That's the only setup. From then on the app connects by itself whenever it's running, on the
phone or in the car, and retries every few seconds if the car is off or the adapter is
unplugged. The status line shows **OBD2 ●** in green when it's live; the phone's stats show
what it's doing. Tap **OBD2** again to switch or forget the adapter.

The app reads RPM several times a second; coolant, intake, throttle, load and car speed in
turn; fuel level and battery voltage every 10 s. It only asks for what your car says it
supports.

## Set it up

1. **Install**: open the latest [`DriveSanity.apk`](https://github.com/byjoelsamuel/DriveSanity/releases/latest/download/DriveSanity.apk)
   on your phone and allow installs from your browser. Allow location when the app asks.
2. **Mount the phone firmly.** A loose phone measures its own wobble. Either:
   - on the dash or a vent, **screen facing you** (portrait or landscape), or
   - **flat**, screen up, with the **top of the phone pointing to the front of the car**.
3. **Calibrate**: with the car stopped on level ground, tap **Calibrate** and keep still for
   1.5 s. It's remembered; do it again whenever you move the mount.

## Android Auto

The car screen shows the same dashboard with **Calibrate** and **Rec** buttons. It's
registered as a navigation app because that's the only kind Android Auto lets draw its own
screen; it never starts navigation.

**Installing from the APK isn't enough.** Android Auto only runs apps built on Google's Car
App Library (like this one) if Google Play installed them. Its "Unknown sources" developer
setting covers older media and messaging apps, not this kind, so a sideloaded DriveSanity
works on the phone but never appears in the car. Two ways round it:

- **Google Play internal testing** (the proper route). Upload the app to a Play Console
  internal test track (one-time $25 developer fee) and install it from the Play Store as a
  tester. Android Auto then treats it like any Play app, and Play delivers updates.
- **[UnknownAuto](https://github.com/GitMag/UnknownAuto)** (free, unofficial, not checked by
  us). It uses [Shizuku](https://shizuku.rikka.app/) to install the APK so it's recorded as
  coming from Google Play. Every update has to go through it too: installing a new APK any
  other way drops the mark and the app disappears from the car again.

## Good to know

- **Hills read as acceleration.** An accelerometer can't tell a slope from speeding up, so
  driving up a 5 % grade shows about 0.05 g "accel". Calibrating on a slope adds the same offset.
- **Speed lags a little.** GPS updates about once a second and drops out in tunnels and garages.
- **Keep the app open while recording on the phone.** It also keeps going in the background
  (there's a notification), and Android Auto keeps it running while connected.
- **Eyes on the road.** The car screen is for glances; look at the trip stats when parked.

## Build

Android Studio, or `./gradlew assembleDebug` with JDK 17+ and the Android SDK.
`./gradlew testDebugUnitTest` runs the unit tests for the maths in `DriveMath.kt`.

Releases come from CI: bump `appVersion` in `gradle.properties` and merge to `main`, and
GitHub Actions builds, signs and publishes `DriveSanity.apk`. Signing needs four repo secrets:
`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`,
`ANDROID_KEY_PASSWORD`. Without them CI builds a debug APK only.

## Code map

| File | What it does |
|---|---|
| `DriveMath.kt` | Calibration (finding the car's axes), brake levels, smoothing, trip stats. No Android, unit-tested. |
| `DriveEngine.kt` | Reads GPS and the accelerometer; shared by the phone and car screens. |
| `DashPainter.kt` | Draws the dashboard; used by both screens. |
| `MainActivity.kt` | The phone screen. |
| `car/` | Android Auto: the service Android Auto connects to, and the car screen. |
| `DriveService.kt` | Foreground service that keeps GPS running in the background. |
| `TripLog.kt` | CSV recording. |
| `obd/Elm.kt` | ELM327 commands and reply parsing; no Android, unit-tested against a fake adapter. |
| `obd/Transports.kt` | Classic Bluetooth (serial) and Bluetooth LE links to the adapter. |
| `obd/Obd.kt` | Keeps the adapter connected on its own thread and holds the latest readings. |
| `obd/ObdPicker.kt` | The one-time "pick your adapter" list. |

## Next

A check that uses GPS to confirm which way is forward, and choosing what goes in the boxes.
