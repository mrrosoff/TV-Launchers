# Dot

An LED dot-matrix home screen for Android TV / Google TV. Kotlin, Android 8 and up.

- **Wheel:** apps on an endless arc in their brand colours. Up/Down spin, OK opens, hold OK for settings.
- **Time and weather:** from Open-Meteo, located by IP.
- **Remote:** on Home, Input opens an Inputs menu and the gear opens Settings.
- **Background and themes:** a slow dot-grid wave, burn-in drift, and seasonal themes (`src/data/Occasions.kt`).

## Home Assistant

Optional. Set `ha_url` and `ha_token` (a long-lived access token) in the TV's `personal.properties`, plus:

- **Lights:** `ha_lights=light.a,light.b` tints them in each app's colour while the wheel spins, and fades
  them back when an app opens or the TV turns off.
- **Laundry:** `ha_laundry=sensor.washer_status,sensor.dryer_status` shows a five-second flag over any app
  when one turns `done`.

New integrations go in `src/homeassistant/`.

## Build

`./install.sh tvs/<tv>` from the repo root, or `./gradlew assembleRelease -Ptv=../../tvs/<tv>`.

Preview a theme: `tools/tv.py 'am start -n dev.mrrosoff.tv.dot/.home.MainActivity --es preview_theme halloween'`.
