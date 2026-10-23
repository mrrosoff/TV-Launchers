# Reverting

Everything in this repo can be undone from this folder. Set `TV_HOST` to the TV's IP first, with USB
debugging switched on again (README, "Before you start").

| Script | What it does |
|---|---|
| `restore.sh` | Re-enables every package in `../debloat.txt` and makes the stock Google TV launcher Home again. Start here. |
| `restore-everything.sh` | Re-enables *every* disabled package except the three Sony disabled at the factory, whether or not it is in the list. Use it if something was disabled by hand. |
| `remove-launcher.sh` | `restore.sh`, then uninstalls the custom launcher and reboots. |

Reboot the TV after either restore script: `tools/venv/bin/python tools/tv.py reboot`.

One app only: `tools/venv/bin/python tools/tv.py 'pm enable --user 0 <package>'`.

## The TV as it was before any change (22 Oct 2026)

`original-state/` (on the computer that did the setup only; gitignored because it holds the serial number) has
the raw snapshot: package lists, every system setting, `getprop`, and the batches that were disabled. The
table below is the part that matters.

| | Original value | Put it back with |
|---|---|---|
| Home | `com.google.android.apps.tv.launcherx/.home.HomeActivity` | the scripts above |
| Disabled packages | the three in `factory-disabled.txt` (Sony's own) | leave these disabled |
| Display | physical 3840x2160, override 1920x1080 (Sony's own UI setting), density 320, no forced density | nothing was changed |
| Private DNS | not set (Android default, automatic) | `tools/tv.py 'settings put global private_dns_mode opportunistic'` |

## Things a restore cannot bring back

- **The hidden Wi-Fi network saved during first setup.** Disabling the setup wizard deletes the networks it
  saved; re-enabling it does not restore them. Re-join that network in **Settings → Network & Internet**.
- **The stock home screen's layout** (app order, hidden rows) may reset to Google TV's defaults after it is
  re-enabled.

If the TV ever fails to show a home screen, **Settings** is still reachable from the remote's gear button,
and a factory reset (**Settings → System → About → Reset**) undoes everything here, along with your sign-ins.
