# TV-Launchers

Home screens for Android TV / Google TV sets, and everything needed to put them on a real TV: debloat
lists, install and revert scripts, and ADB tools that need no `adb` binary. Everything is reversible:
packages are *disabled*, never uninstalled, and nothing is rooted or flashed.

| Path | What it is |
|---|---|
| `launchers/` | The home screens. Nothing in here is specific to one TV. |
| `launchers/dot/` | **Dot**: an LED dot-matrix home screen in Kotlin (~60 KB). See its [README](launchers/dot/README.md). |
| `tvs/` | One folder per TV: its `tv.properties` (the only TV-specific settings the launchers and scripts read), its debloat list, and notes. |
| `tvs/sony-bravia-a80cj/` | Sony BRAVIA XR-65A80CJ (Google TV, Android 12). See its [README](tvs/sony-bravia-a80cj/README.md). |
| `install.sh` | Build a launcher for a TV and make it that TV's home screen |
| `revert.sh` | Undo everything on a TV |
| `tools/` | `tv.py` (ADB client), `disable.sh` / `restore.sh` (apply / undo a debloat list), `adb-closed.py` |

## Before you start

On the TV: **Settings → System → About → Build**, press OK 7 times, then **Settings → System → Developer
options → USB debugging: on**. The TV shows an *Allow USB debugging?* prompt on the first connection; accept it.

On the computer (macOS shown):

```sh
python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt
export TV_HOST=192.168.1.50          # the TV's IP: Settings → Network & Internet → your network
tools/venv/bin/python tools/tv.py --probe

brew install openjdk@17 && brew install --cask android-commandlinetools      # to build launchers
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
yes | sdkmanager --sdk_root=$ANDROID_HOME --licenses
sdkmanager --sdk_root=$ANDROID_HOME "platforms;android-34" "build-tools;34.0.0"
```

## Set up a TV

```sh
TV_HOST=<tv ip> tools/disable.sh tvs/<tv>/debloat.txt     # disable the TV's bloat
TV_HOST=<tv ip> ./install.sh tvs/<tv>                     # build launchers/dot for it, make it Home
TV_HOST=<tv ip> ./install.sh tvs/<tv> launchers/<other>   # or another launcher
```

`install.sh` only disables the TV's stock launcher after the new one is Home, running, and not crashing.
`disable.sh` refuses to disable Google TV's setup wizard while it owns a saved Wi-Fi network, because
Android deletes that network when it is disabled.

## Add a TV

Make `tvs/<brand-model>/` with:

- `tv.properties`: copy one from another TV and change it: stock launcher, Live TV app, apps to hide by
  default, remote scancodes (read them with `tools/venv/bin/python tools/tv.py 'getevent -lt'` while pressing
  the button), and the weather fallback city.
- `debloat.txt`: one package per line; never Android's core packages (`disable.sh` refuses them).
- `factory-disabled.txt`: `pm list packages -d` before you change anything, so `revert.sh --all` leaves those alone.
- `personal.properties` (gitignored, optional): `birthdays=Name:MM-DD,Name:MM-DD`, from `personal.properties.example`.

## Undo

```sh
TV_HOST=<tv ip> ./revert.sh tvs/<tv>                      # re-enable the debloat list, stock Home back
TV_HOST=<tv ip> ./revert.sh tvs/<tv> --all                # re-enable everything that wasn't disabled at the factory
TV_HOST=<tv ip> ./revert.sh tvs/<tv> --remove-launcher    # ...and uninstall the launcher
tools/venv/bin/python tools/tv.py reboot
```

One app only: `tools/venv/bin/python tools/tv.py 'pm enable --user 0 <package>'`.

## Optional: ad-blocking DNS

Private DNS pointed at AdGuard blocks ad and tracker domains for every app on the TV (not YouTube ads):
`tools/tv.py 'settings put global private_dns_specifier dns.adguard-dns.com'`, then
`tools/tv.py 'settings put global private_dns_mode hostname'`. Undo: `private_dns_mode opportunistic`.

## When you're done: close ADB

Port 5555 is a plaintext shell for anyone on the network who has been authorised. Switch **USB debugging**
off (and **Revoke USB debugging authorisations** when you're done for good), reboot, and check that
`python3 tools/adb-closed.py <tv ip>` reports the port closed.

## Credits

- **[cleanyourtv](https://github.com/Gathin23/cleanyourtv)** by **Gathin** and its contributors (MIT): the
  ADB tools in `tools/`, the debloat procedure and safety rules, the input-switching approach, and the
  original home screen Dot grew out of. The BRAVIA's debloat list started from its tested VH2 list.
- **[Seeker TV](https://github.com/TWBankston/Seeker-TV-Launcher)** by **TWBankston** (MIT): Dot's wheel
  geometry and dot-matrix style, including its weather icons.
- **[Lucide](https://lucide.dev)** (ISC): Dot's Wi-Fi and settings icons.
- [Open-Meteo](https://open-meteo.com) (weather, CC BY 4.0 data), [ipwho.is](https://ipwho.is) (location),
  [adb-shell](https://github.com/JeffLIrion/adb_shell) (Apache 2.0).

Licensed MIT; see `LICENSE`, which keeps the original copyright notices.
