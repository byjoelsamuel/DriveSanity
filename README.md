# DriveSanity

An Android app that shows live driving data on your phone and on your car's
Android Auto screen: speed, braking, g-force, RPM, fuel and engine temperatures.

The phone and the car each get their own view:

- **On the phone**, a regular app with three tabs: **Drive** (live numbers in plain words),
  **Trips** (this trip's stats and your recordings) and **Setup** (calibration, OBD2, units,
  and which screen the car shows).
- **In the car**, a full-screen dashboard. Pick one of three in Setup → Car screen:

![The Android Auto screen, with sample data](docs/car-screen.png)

- **Gauges** (above): the everyday screen.
- **Map:** a live map with simple navigation.
- **Retro:** everything in Saab green, after early-2000s dashboards.

![The map screen, with sample data](docs/map-screen.png)

*This picture was made where the map server can't be reached, so its streets are a stand-in.
In the app the map is the real OpenStreetMap.*

![The retro screen, with sample data](docs/retro-screen.png)

## What you see

- **Speed** in MPH, with km/h underneath.
- **Brake**: how hard you're braking, from light to very hard.
- **RPM**: a big round dial that goes from white to sand to red as you near the 6,300 rpm redline, with the speed and gear inside it.
- **G-meter**: a dot that moves when you speed up, brake or turn.
- **Coolant and fuel**: ring gauges on the left.
- **Slim readouts**: throttle, battery voltage, intake air temperature and trip distance.

The map screen swaps the g-meter for a map, and shows the gear you're in, the speed limit
and the next turn when you're navigating.

The phone also shows trip stats: distance, average and top speed, peak g-force, and a count
of hard brakes, launches and turns.

## How it works

```mermaid
flowchart LR
    GPS[Phone GPS] --> App[DriveSanity]
    Motion[Phone motion sensor] --> App
    OBD[OBD2 adapter<br>over Bluetooth] --> App
    App --> Phone[Phone screen]
    App --> Car[Android Auto]
    App --> CSV[Recording<br>CSV file]
```

| Data | Comes from |
|---|---|
| Speed | Phone GPS. If GPS drops out, for example in a tunnel, the car's own speed from OBD2 |
| Braking and g-force | The phone's motion sensor |
| RPM, fuel, temperatures, battery | The OBD2 adapter |
| Gear | Worked out from speed and RPM (see below) |
| Map, routes, address search, speed limits | OpenStreetMap, through free public services |

Without an OBD2 adapter you still get speed, braking, g-force and trip stats.

## Getting started

1. Download [`DriveSanity.apk`](https://github.com/byjoelsamuel/DriveSanity/releases/latest/download/DriveSanity.apk)
   and install it. Allow location when it asks.
2. Mount your phone firmly, either facing you or lying flat with its top pointing to the
   front of the car.
3. Stop on level ground and tap **Calibrate** (on the Drive tab the first time, or in Setup).
   The app uses this to work out which way is forward. Do it again if you move the mount.
4. Drive.

To record a drive, tap **Start recording** on the Drive tab. Recordings appear in **Trips**,
where you can share them as a CSV file or delete them.

## Connecting OBD2

Any ELM327 Bluetooth adapter works, whether it's classic Bluetooth or Bluetooth LE.

1. Plug the adapter into the OBD2 port under the dashboard and turn the ignition on.
2. If it's a classic Bluetooth adapter, pair it in your phone's Bluetooth settings first.
   The PIN is usually 1234 or 0000. Bluetooth LE adapters don't need pairing.
3. In the app, go to **Setup** → **Choose adapter** and pick it from the list.

You only do this once. After that the app connects by itself whenever it's open. Some cars
don't report fuel level; if yours doesn't, the fuel meter will say so.

**If it keeps disconnecting**

- Open Android's settings → Apps → DriveSanity → Battery and choose **Unrestricted**, so Android
  doesn't pause the app while you drive.
- Open the app on the phone once before you drive. That starts the notification that keeps the
  link going when you switch apps or the screen turns off. It stops a few minutes after the
  car goes quiet.
- Close any other app that talks to the adapter (the adapter's own app, Torque, Car Scanner).
  Most adapters only allow one connection at a time.
- **Setup → OBD2 adapter → Diagnostics** shows how many times the link restarted and why.
  "Adapter stopped answering" points at the adapter or the car; "Adapter disconnected" means
  Bluetooth dropped.

## The startup animation

The app opens with a short old-TV power-on: a bright line stretches across the screen, opens
up into the picture, and the name flickers in under scanlines. It plays on the phone, and again
in the car when Android Auto connects. Tap to skip, or turn it off in **Setup → Startup
animation**.

## The retro screen

Everything is lit in one green on black, like Saab's instrument lighting. The tachometer is a
row of bars that get taller toward the redline, like the C4 Corvette's digital dash, and the
numbers are LCD-style digits with the unlit segments showing faintly. Fuel, coolant, battery
and intake air each get a small bar gauge, and the labels are kept short. There are no colour
warnings on this screen, so warnings blink instead: the tach past the redline, low fuel, a hot
engine and a bad battery voltage.

## The map screen

Pick it in Setup → Car screen, or with the button in the car. On the phone, tap **Where to?**
on the Drive tab and type an address to get directions; the phone then shows a small map
too. Tap **End route** to stop.

- **Map:** OpenStreetMap, shown dark and turning with the car. It needs mobile data. Between
  GPS fixes (about one a second) the car's position is moved on along its heading, so the map
  glides instead of jumping.
- **Directions:** turn by turn, with the distance, time left and arrival time. If you leave the
  route the app finds a new one.
- **Speed limit:** from OpenStreetMap. The app downloads every road with a limit within 1.5 km
  and matches you to the one you're driving along, so the sign changes as soon as you turn.
  Many roads don't have their limit mapped yet, so the sign only shows where one is known.
  Your speed turns orange when you're more than 5 over.
- **Gear:** most cars don't report their gear over OBD2, so the app works it out from speed
  and RPM. It's set up for a 2023 Mazda3 2.5 AWD with the 6-speed automatic. It shows a dash
  while shifting, or when the torque converter is slipping a lot. For another car, change
  `MY_CAR` in `DriveMath.kt`, or set it to `null` and the app learns the gears by itself after
  a few minutes of driving.

The free services this uses ask apps to keep their traffic light, so they suit personal use.
An app with many users would need its own servers.

| Service | Used for |
|---|---|
| [OpenStreetMap tiles](https://www.openstreetmap.org) | The map |
| [Nominatim](https://nominatim.org) | Address search |
| [OSRM, run by FOSSGIS](https://routing.openstreetmap.de) | Directions |
| [Overpass API](https://overpass-api.de) | Speed limits |

Map data © OpenStreetMap contributors.

## Android Auto

Android Auto only shows apps of this kind if they were installed from Google Play, so a
normal APK install won't appear in the car. Install it with
[UnknownAuto](https://github.com/GitMag/UnknownAuto) instead, which marks the install as
coming from Play. Use UnknownAuto for every update as well.

## Things to know

- Hills show up as a small amount of acceleration, because the motion sensor can't tell a
  slope from speeding up.
- GPS speed updates about once a second.
- Keep your eyes on the road. The car screen is made for quick glances.

## Releases

Every change goes through a pull request. When it's merged into `main`, GitHub Actions runs
the tests, builds the app, signs it and publishes it as a new release.

```mermaid
flowchart LR
    PR[Pull request] --> Tests[Tests and build]
    Tests --> Merge[Merge to main]
    Merge --> Release[Signed APK<br>on Releases]
```

To publish a new version, raise `appVersion` in `gradle.properties` before merging.
Signing uses four repository secrets: `ANDROID_KEYSTORE_BASE64`,
`ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` and `ANDROID_KEY_PASSWORD`.

## Building it yourself

Open the project in Android Studio, or run `./gradlew assembleDebug` with JDK 17 and the
Android SDK. `./gradlew testDebugUnitTest` runs the tests.

| Folder or file | What's in it |
|---|---|
| `DriveMath.kt` | Calibration, braking levels and trip stats |
| `DriveEngine.kt` | Reads GPS and the motion sensor |
| `DashPainter.kt` | Draws the dashboard for both the phone and the car |
| `MainActivity.kt`, `ui/` | The phone app (Jetpack Compose) |
| `car/` | The Android Auto screen |
| `obd/` | The OBD2 Bluetooth connection |
| `map/` | Map tiles and drawing the map |
| `nav/` | Address search, directions and speed limits |

The font is [Poppins](https://fonts.google.com/specimen/Poppins), used under the SIL Open
Font License (see `licenses/Poppins-OFL.txt`).
