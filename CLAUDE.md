# DriveSanity

Android app (Kotlin, Jetpack Compose, `androidx.car.app`) that shows live driving data on the phone and on Android Auto
car screens, for a Mazda3. Read `README.md` for what it does; this file is for working on it.

## Build, test, lint
- `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` is the full check (JDK 17). Keep it green.
- `tools/lint.sh` runs ktlint and detekt (pinned, checksum-verified); `tools/lint.sh format` fixes style. Rules are in
  `.editorconfig` and `config/detekt/`; detekt's old findings are in `config/detekt/baseline.xml`, so only new ones fail.
- Tests are JUnit plus Robolectric (native graphics, SDK 35) and Compose UI tests. `WalkthroughTest` draws every phone tab and
  car screen and presses buttons; set `SHOTS_DIR=/some/dir` to save PNGs of them and look at the result.
- Offline: set `ROBOLECTRIC_DIR` to a folder that already has the Android jars (`app/build.gradle.kts` reads it).
- CI flakes to rerun, not fix: Robolectric failing to fetch its jar (`MavenArtifactFetcher … IOException`), and a rare native
  crash in the test JVM. Rerun failed jobs; if it fails the same way twice, wait a few minutes (Maven Central rate limits).

## Where things are (`app/src/main/java/io/github/byjoelsamuel/drivesanity/`)
- `DashPainter` picks a screen by `Prefs.Layout`; each screen is a Canvas painter (`GaugesPainter`, `ClusterPainter`
  for map/launch/music, `MusicPainter`, `DiagPainter`, `RetroPainter`, `LaunchPainter`). Painters take the safe area `r` and the
  whole surface `full`.
- `car/` is the Android Auto side (`DashScreen` draws onto the surface and builds the buttons). `ui/` is the phone app
  (Compose); settings are staged in `SettingsDraft` and only take effect on Apply.
- `DriveEngine` (GPS, sensors, recording), `obd/` (read-only OBD2 over Bluetooth; `CommandGuard` blocks anything that writes),
  `map/` and `nav/` (OpenStreetMap tiles, routing, search, Google Maps notification reader), `media/`, `update/` (in-app updates).
- A new car screen means a `Prefs.Layout` entry, a `DashPainter` branch, a line in `ScreenPickerScreen.blurb` and
  `SettingsTab.layoutNote`, and the README; the walkthrough test then draws it automatically.

## Working rules
- Style: calm, non-vibrant palette; Poppins; thin LED-tick bars; no round dials. README and PR text in plain language.
- OBD2 stays read-only. No API keys or secrets in the repo (map data is OpenStreetMap only). Phone sensors (g-force) are
  not trusted as car data.
- Be honest about what is untested: nothing here has been verified in a real car unless the user says so.
- Android Auto only lists the app if it was installed with the Google Play mark (UnknownAuto + Shizuku); installing through Android's
  own installer drops it. See the README's "If something goes wrong".

## Git and releases
- One branch per change, `git push -u origin <branch>`. GitHub GraphQL is blocked here, so `gh pr create/list` fails: use REST
  (`gh api repos/byjoelsamuel/DriveSanity/pulls …`). If the Stop hook says a branch has no remote, fetch it and set
  `branch.<name>.remote/merge`.
- Raise `appVersion` in `gradle.properties` for any user-visible change (versionCode is derived from it). Merging to `main` publishes
  `v<appVersion>` with the signed APK, using the squash commit's message body as release notes, so write that body for users.
- Only merge when the user says so ("merge it"). Squash merge via `PUT …/pulls/N/merge`, then wait for the main build and check the
  release exists. If the PR was stacked on another, rebase it on main after the base is squash-merged.
- Commits end with the attribution lines the session gives.
