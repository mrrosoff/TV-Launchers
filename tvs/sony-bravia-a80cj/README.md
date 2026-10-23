# Sony BRAVIA XR-65A80CJ

65" OLED, platform BRAVIA_VH2, Google TV / Android 12 (API 31), build `BRAVIA_VH2_M_UC-user 12
STT2.230505.001.S136`, US firmware. Set up 22 Oct 2026.

| File | |
|---|---|
| `tv.properties` | What the launchers and scripts need to know about this TV |
| `debloat.txt` | The 26 packages disabled, grouped, with what was kept and why |
| `factory-disabled.txt` | The three packages Sony ships disabled; `revert.sh --all` leaves them alone |
| `personal.properties` | Gitignored: birthdays (see `personal.properties.example`) |
| `original-state/` | Gitignored (it holds the serial number): the raw snapshot from before any change |

```sh
TV_HOST=<tv ip> tools/disable.sh tvs/sony-bravia-a80cj/debloat.txt
TV_HOST=<tv ip> ./install.sh tvs/sony-bravia-a80cj
```

## The TV as it was before any change

| | Original value | Put it back with |
|---|---|---|
| Home | `com.google.android.apps.tv.launcherx/.home.HomeActivity` | `./revert.sh tvs/sony-bravia-a80cj` |
| Disabled packages | the three in `factory-disabled.txt` (Sony's own) | leave these disabled |
| Display | physical 3840x2160, override 1920x1080 (Sony's own UI setting), density 320, no forced density | nothing was changed |
| Private DNS | not set (Android default, automatic) | `tools/tv.py 'settings put global private_dns_mode opportunistic'` |

## Things a revert cannot bring back

- **The hidden Wi-Fi network saved during first setup.** Disabling the setup wizard deletes the networks it
  saved; re-enabling it does not restore them. Re-join it in **Settings → Network & Internet**.
- **The stock home screen's layout** (app order, hidden rows) may reset to Google TV's defaults.

If the TV ever fails to show a home screen, **Settings** is still reachable from the remote's gear button, and
a factory reset (**Settings → System → About → Reset**) undoes everything, along with your sign-ins.

## Remote, Cast and AirPlay

- **Remote:** the voice remote ("SONY TV VRC 001") sends scancode `0x233` for Input and `0xc3` for the gear;
  the Netflix button sends `KEY_ASSISTANT`. Every button works with the stock launcher disabled.
- **Google Cast works** with the setup wizard disabled, including after a reboot: unlike a Chromecast, this
  BRAVIA doesn't need the wizard for Cast credentials, so no workaround such as castfix is needed. Sony's
  system service logs a harmless retry while the wizard is off (about 0.1% of one CPU core).
- **AirPlay:** Sony's receiver runs and advertises itself, but refused connections at its access check
  (`acl=1`) before any stream started; nothing in `debloat.txt` is involved. The likely fix is **Settings →
  Channels & Inputs → Apple AirPlay & HomeKit → Allow access → Everyone on the same network**. Its settings
  screen covers Home if opened over ADB; `am force-stop com.sony.dtv.airplayapp` clears it.
- **Voice search (open):** the Assistant transcribes, but search results ("show me comedies") fail with
  "can't process request": Google TV shows them in the stock launcher's search screen, which is disabled.
  Direct commands ("open YouTube") still work. Fix to try: re-enable `launcherx` while keeping the new
  launcher as Home, and check that Home survives a reboot.
- **Screensaver and standby:** Google TV's ambient screensaver starts after 10 minutes idle and the TV goes to
  standby after 30, with the new launcher as Home.
