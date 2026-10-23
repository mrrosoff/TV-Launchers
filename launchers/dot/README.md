# Dot

A minimal home screen for Android TV / Google TV, drawn as an LED dot matrix. Kotlin, no dependencies,
about 60 KB, `minSdk` 26 (Android 8).

- **App wheel** on the left: apps in alphabetical order as dot-matrix names on an arc; the selected one is
  largest and in the service's own colour, with a soft matching glow. Up/Down spin it, OK opens. It starts on
  the app you last opened.
- **Clock, date and weather** at the top right, with the network signal and the Settings gear in the corner.
  Settings opens the TV's full Settings; hold OK on it for **Home screen settings** (show or hide apps,
  burn-in care, the remote buttons, theme preview).
- **Weather** from [Open-Meteo](https://open-meteo.com) for the TV's public-IP location (looked up once a
  day at [ipwho.is](https://ipwho.is)), refreshed at most every 30 minutes while Home is open. Those are
  the only network requests.
- **Remote buttons on Home:** Input opens an Inputs menu (HDMI ports, AV, Live TV) and the gear opens
  Settings. The helper that catches them stops held buttons from repeating, so it only runs while Home is on
  screen; in apps the remote behaves as stock.
- **A living background:** a faint grid of dots that slowly breathes and ripples, only while Home is visible.
- **Burn-in care** for OLED panels: the static parts of Home drift a few pixels every 3 minutes.
- **Seasonal themes**, each with a greeting, colours, an emblem and a light particle effect: Halloween
  (Oct 24–31), winter break (Dec 18–30; "Happy new year" Dec 31–Jan 1), the Fourth of July, and **birthdays**
  from the TV's gitignored `personal.properties`. The dates are in `src/data/Occasions.kt`.

## Build

`./install.sh tvs/<tv>` from the repo root builds and installs it. To build by hand:

```sh
./gradlew assembleRelease -Ptv=../../tvs/<tv>      # with that TV's tv.properties and personal.properties
./gradlew assembleRelease                          # generic: nothing hidden, no Live TV row, keycodes only
```

## Preview a theme

On the TV, hold OK on Settings → Home screen settings → **Theme**, or over ADB:
`tools/tv.py 'am start -n dev.mrrosoff.tv.dot/.home.MainActivity --es preview_theme halloween'`
(`holidays`, `newyear`, `july4`, `birthday`; `''` goes back to following the date).

## Code

| Folder | |
|---|---|
| `src/home/` | `MainActivity` and the pieces of Home: `WheelView`, `InfoPanel`, `InputsMenu`, `NetworkIndicator`, `Ambience`, `HomeSettingsActivity` |
| `src/dots/` | The LED drawing: `DotFont`, `DotTextView`, `DotIconView`, `DotFieldView` (background), `ParticleView` (themes) |
| `src/data/` | `Apps`, `BrandColors`, `Inputs`, `Weather`, `Occasions`, `Prefs`, `TvConfig` (the TV's `tv.properties`) |
| `src/remote/` | `KeyService`, the Input and gear buttons |
