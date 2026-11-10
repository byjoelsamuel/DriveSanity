# DriveSanity

Live driving data on your phone and in **Android Auto**: speed, a g-force circle and how
hard you're braking. Barebones on purpose. It's a first try before adding an OBD2 adapter.

## What it shows

| | Where it comes from |
|---|---|
| **Speed** | Phone GPS (about once a second). Tap the dashboard to switch km/h ↔ mph. |
| **G-force circle** | Phone accelerometer, smoothed over ~0.2 s. The dot is the car's acceleration: up = speeding up, down = braking, left/right = turning. Rings every 0.25 g, with a 2-second trail. |
| **Braking** | How hard you're slowing down, in g: **light** ≥ 0.10, **moderate** ≥ 0.25, **hard** ≥ 0.40, **very hard** ≥ 0.60. A normal stop is 0.1–0.3 g; an emergency stop in a road car is 0.7–1.0 g. |
| **Trip** | Distance, moving time, average and top speed, peak brake/accel/turn g, and counts of hard brakes (≥ 0.40 g), hard launches (≥ 0.35 g) and hard turns (≥ 0.40 g). Long-press **Reset** to start over. |
| **GPS speed change** | Acceleration worked out from GPS speed. A rough cross-check on the accelerometer. |
| **Recording** | **Rec** writes a CSV row every 0.1 s; **Share** sends the latest one. |

CSV columns: `time_s, lat, lon, gps_speed_kmh, gps_accuracy_m, gps_accel_g, long_g, lat_g, brake_g, brake_level`.

## Set it up

1. **Install**: open the latest [`DriveSanity.apk`](https://github.com/byjoelsamuel/DriveSanity/releases/latest/download/DriveSanity.apk)
   on your phone and allow installs from your browser. Allow location when the app asks.
2. **Mount the phone firmly.** A loose phone measures its own wobble. Either:
   - on the dash or a vent, **screen facing you** (portrait or landscape), or
   - **flat**, screen up, with the **top of the phone pointing to the front of the car**.
3. **Calibrate**: with the car stopped on level ground, tap **Calibrate** and keep still for
   1.5 s. It's remembered; do it again whenever you move the mount.

## Android Auto

Android Auto only shows apps from the Play Store unless you allow others:

1. On the phone: **Settings → search "Android Auto"** → scroll to the bottom and tap
   **Version** about 10 times → OK.
2. **⋮ menu → Developer settings** → tick **Unknown sources**.
3. Reconnect to the car. DriveSanity shows up in the app launcher (if not, check
   **Customise launcher** in Android Auto's settings).

On the car screen you get the same dashboard with **Calibrate** and **Rec** buttons. It's
registered as a navigation app because that's the only kind Android Auto lets draw its own
screen. It never starts navigation.

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

## Next

OBD2 over Bluetooth (RPM, throttle, coolant, boost), then a check that uses GPS to confirm
which way is forward.
