# Dot

An LED dot-matrix home screen for Android TV / Google TV. Kotlin with no dependencies, about 60 KB,
Android 8 and up.

- **App wheel:** apps in alphabetical order on an endless arc, the selected one in the service's own
  colour. Up/Down spin it, OK opens, hold OK for Home screen settings. It starts on the last app you opened.
- **Time and weather:** current conditions from Open-Meteo for the TV's location (by IP, once a day),
  refreshed at most every 30 minutes while Home is open. These are its only network requests.
- **Voice search:** press Left on the wheel and speak. Say an app's name to open it; anything else is
  offered to the apps that can search for it (YouTube, Netflix, Disney+, Hulu). Uses the TV's own speech
  recogniser and the remote's microphone.
- **Remote:** on Home, Input opens an Inputs menu (HDMI, AV, Live TV) and the gear opens Settings. In apps
  the remote is left alone, so held buttons keep repeating.
- **Background:** a faint dot grid with slow waves of light crossing it, only while Home is on screen.
- **Burn-in care:** the static parts drift a few pixels every few minutes.
- **Themes:** Halloween, winter break, New Year, the Fourth of July and birthdays (`src/data/Occasions.kt`).

## Build

`./install.sh tvs/<tv>` from the repo root builds and installs it. By hand:

```bash
./gradlew assembleRelease -Ptv=../../tvs/<tv>    # with that TV's tv.properties
```

Preview a theme on the TV: `tools/tv.py 'am start -n dev.mrrosoff.tv.dot/.home.MainActivity --es preview_theme halloween'`.

## Code

| | |
|---|---|
| `src/home/` | `MainActivity`, `WheelView`, `InfoPanel`, `InputsMenu`, `Ambience`, `HomeSettingsActivity` |
| `src/dots/` | `DotFont`, `DotTextView`, `DotFieldView` (background), `ParticleView` (theme effects) |
| `src/data/` | `Apps`, `BrandColors`, `Inputs`, `Weather`, `Occasions`, `Prefs`, `TvConfig` |
| `src/remote/` | `KeyService`, the Input and gear buttons |
