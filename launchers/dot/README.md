# Dot

An LED dot-matrix home screen for Android TV / Google TV. Kotlin, Android 8 and up.

- **Wheel:** apps on an endless arc in their brand colours. Up/Down spin, OK opens, hold OK for settings.
- **Time and weather:** from Open-Meteo, located by IP.
- **Remote:** on Home, Input opens an Inputs menu and the gear opens Settings.
- **Idle:** after 90 seconds untouched, everything but the clock fades and the clock moves to the middle.
- **Background and themes:** a slow dot-grid wave, burn-in drift, and seasonal themes (`src/data/Occasions.kt`).

## Home Assistant

Optional. Set `ha_url` and `ha_token` (a long-lived access token) in the TV's `personal.properties`, plus:

- **Lights:** `ha_lights=light.a,light.b` tints them in each app's colour while the wheel spins, and fades
  them back when an app opens or the TV turns off.
- **Movie mode:** `ha_movie_lights` dim to 30% while one of `tv.properties`' `movie_apps` is on, and fade
  back when you leave it or turn the TV off.
- **Laundry:** `ha_washer` and `ha_dryer` (sensors that turn `done`) show a five-second banner over any app.
  A washer finishing while the dryer runs waits, and becomes "Laundry done" when the dryer finishes. The
  dryer's own banner waits while one of `tv.properties`' `movie_apps` is on. While a machine runs, Home shows
  the time left under the weather, from the same entities as Muni-Display.

New integrations go in `src/homeassistant/`.

## Build

`./install.sh tvs/<tv>` from the repo root, or `./gradlew assembleRelease -Ptv=../../tvs/<tv>`.

Preview a theme: `tools/tv.py 'am start -n dev.mrrosoff.tv.dot/.home.MainActivity --es preview_theme halloween'`.
