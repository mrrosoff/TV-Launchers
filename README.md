# BraviaTV-UI

A debloated **Sony BRAVIA 4K VH2** (Google TV, Android 12) with a minimal, dot-matrix home screen.
Everything here is reversible: packages are *disabled*, never uninstalled, and nothing is rooted or flashed.

The home screen (`launcher/`, Kotlin, ~50 KB):

- **App wheel** on the left: your apps in alphabetical order as LED dot-matrix names on an arc, the
  selected one in the service's own colour with a matching glow. Up/Down spin it, OK opens. It starts on
  the app you last opened.
- **Clock, date and weather** on the right. Weather is the TV's current conditions from
  [Open-Meteo](https://open-meteo.com) for the location of its public IP (looked up once a day at
  [ipwho.is](https://ipwho.is)), refreshed at most every 30 minutes while Home is open.
- **Wi-Fi bars and Settings** at the bottom right. Settings opens the TV's full Settings (Sony's Picture and
  Sound pages are under Display & Sound). Hold OK on it for **Home screen settings**: show or hide apps,
  burn-in care, and the remote buttons.
- **Remote buttons on Home:** Input opens the Inputs menu (HDMI 1–4, AV, Live TV) and the gear opens
  Settings. In apps and on HDMI the remote behaves as stock. The helper that catches those two buttons
  stops held buttons from repeating, so it only runs while Home is on screen; holding Volume+ in an app
  ramps as usual.
- **Burn-in care** for the OLED panel: the static parts of Home drift a few pixels every 3 minutes. The TV's
  own screensaver (after 10 minutes) and standby (after 30) still work as normal.
- **Seasonal themes**, each with its own greeting, colours, emblem and a light dot-particle effect that only
  runs while Home is on screen: Halloween (Oct 24–31, bats), winter break (Dec 18–30, snow; "Happy new year"
  Dec 31–Jan 1), the Fourth of July (fireworks), and **birthdays** (all day, cake and confetti).

| Path | What it is |
|---|---|
| `debloat.txt` | The 26 packages disabled on this TV, grouped, with what was kept and why |
| `install.sh` | Build the launcher and make it the home screen |
| `revert/` | Undo scripts and a record of how the TV was before (see `revert/README.md`) |
| `launcher/` | The home screen: `src/` (Kotlin: `home/`, `dots/`, `data/`, `remote/`), `res/`, `AndroidManifest.xml` |
| `tools/` | `tv.py` (ADB client, no `adb` binary needed), `disable.sh`, `restore.sh`, `adb-closed.py` |

## Before you start

On the TV: **Settings → System → About → Build**, press OK 7 times, then **Settings → System → Developer
options → USB debugging: on**. The TV shows an *Allow USB debugging?* prompt on the first connection; accept it.

On the computer:

```sh
python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt
export TV_HOST=192.168.1.50          # the TV's IP: Settings → Network & Internet → your network
tools/venv/bin/python tools/tv.py --probe
```

## Apply

```sh
TV_HOST=<tv ip> tools/disable.sh debloat.txt   # disable the list
TV_HOST=<tv ip> ./install.sh                   # build + install the home screen, make it Home
```

`install.sh` needs JDK 17 and the Android SDK. On a Mac:

```sh
brew install openjdk@17 && brew install --cask android-commandlinetools
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
yes | sdkmanager --sdk_root=$ANDROID_HOME --licenses
sdkmanager --sdk_root=$ANDROID_HOME "platforms;android-34" "build-tools;34.0.0"
```

`disable.sh` refuses to disable the setup wizard (`setupwraith`) while it owns a saved Wi-Fi network,
because Android deletes that network when it is disabled. Check with
`tools/venv/bin/python tools/tv.py "dumpsys wifi | grep -o 'cname=[^ ]*'"`, and forget/re-join that
network in Settings first if you need it.

## Show or hide an app

On the TV: hold OK on **Settings** → Home screen settings → the app's row. Or change the defaults in
`launcher/app/src/main/res/values/config.xml` (`hidden_apps`) and run `./install.sh` again.

## Re-enable a disabled app

```sh
tools/venv/bin/python tools/tv.py 'pm enable com.google.android.youtube.tvmusic'
```

Or comment the line out of `debloat.txt` so it stays enabled next time you apply the list.
`pm list packages -d` shows everything currently disabled.

## Birthdays and themes

Names and birthdays stay off GitHub: copy `launcher/personal.properties.example` to
`launcher/personal.properties` (gitignored), fill in `birthdays=Name:MM-DD,Name:MM-DD`, and run `./install.sh`.

To see a theme outside its dates, hold OK on **Settings** → Home screen settings → **Theme**, or
`tools/venv/bin/python tools/tv.py 'am start -n dev.mrrosoff.braviatv/.home.MainActivity --es preview_theme halloween'`
(`holidays`, `newyear`, `july4`, `birthday`; `''` goes back to following the date). The dates live in
`launcher/src/data/Occasions.kt`.

## Weather settings

`config.xml` holds the units (`weather_units`, `fahrenheit` or `celsius`) and the city used when the IP
lookup fails (`weather_fallback_*`). The launcher's only network use is those two requests.

## Cast and AirPlay

- **Google Cast works** with the setup wizard disabled (checked after a reboot). Unlike a Chromecast, this
  BRAVIA doesn't need the wizard for Cast credentials, so no workaround such as castfix is needed. Sony's
  system service logs a harmless retry while the wizard is off (about 0.1% of one CPU core).
- **AirPlay:** Sony's AirPlay receiver is running and advertises itself, but on this TV it refused
  connections at its access check (`acl=1`), before any stream started; nothing that `debloat.txt` disables
  is involved. The likely fix is **Settings → Channels & Inputs → Apple AirPlay & HomeKit → Allow access →
  Everyone on the same network**. Its settings screen can't be closed with Home or Back once opened over
  ADB; `am force-stop com.sony.dtv.airplayapp` clears it.

## Undo everything

```sh
TV_HOST=<tv ip> revert/restore.sh
tools/venv/bin/python tools/tv.py reboot
```

To remove the home screen too, run `revert/remove-launcher.sh`, which restores the stock Home first.

## Optional: ad-blocking DNS

Private DNS pointed at AdGuard blocks ad and tracker domains for every app on the TV. It does **not**
block YouTube ads.

```sh
tools/venv/bin/python tools/tv.py 'settings put global private_dns_specifier dns.adguard-dns.com'
tools/venv/bin/python tools/tv.py 'settings put global private_dns_mode hostname'
```

Undo: `tools/venv/bin/python tools/tv.py 'settings put global private_dns_mode opportunistic'`.

## When you're done: close ADB

Port 5555 is a plaintext shell for anyone on the network who has been authorised. When finished:

1. **Developer options → Revoke USB debugging authorisations**, then switch **USB debugging off**.
2. Reboot the TV.
3. `python3 tools/adb-closed.py <tv ip>` must report the port closed (*connection refused*).

## Credits

- **[cleanyourtv](https://github.com/Gathin23/cleanyourtv)** by **Gathin** and its contributors (MIT): the
  ADB tools in `tools/` (`tv.py`, `b64push.py`, `disable.sh`, `restore.sh`, `recovery.py`, `adb-closed.py`),
  the debloat procedure and safety rules, the input-switching approach and the original home screen this
  one grew out of. `debloat.txt` started from its tested `examples/sony-bravia-4k-vh2/` list.
- **[Seeker TV](https://github.com/TWBankston/Seeker-TV-Launcher)** by **TWBankston** (MIT): the wheel's
  geometry and the dot-matrix style, including the sun, cloud, rain, snow and storm icons.
- [Open-Meteo](https://open-meteo.com) (weather, CC BY 4.0 data) and [ipwho.is](https://ipwho.is) (location).
- [adb-shell](https://github.com/JeffLIrion/adb_shell) (Apache 2.0), used by `tools/tv.py` so no `adb` binary is needed.

Licensed MIT; see `LICENSE`, which keeps the original copyright notice.
