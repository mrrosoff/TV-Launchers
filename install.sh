#!/usr/bin/env bash
# Build the launcher and make it the TV's home screen.
#
# Order matters, so the TV is never left without a Home: install, start it, make it the default Home,
# check that Home really opens it, and only then disable the stock Google TV launcher (and remove the
# earlier cleanyourtv launcher, if it is installed). Needs JDK 17 and the Android SDK; see README.
#
# Usage: TV_HOST=<tv ip> ./install.sh
set -euo pipefail
cd "$(dirname "$0")"
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0}"
export TV_HOST
PKG=dev.mrrosoff.braviatv
HOME_ACTIVITY=$PKG/.home.MainActivity
STOCK=com.google.android.apps.tv.launcherx
OLD=com.gathin.tvhome

[ -x tools/venv/bin/python ] || { python3 -m venv tools/venv && tools/venv/bin/pip install -q -r tools/requirements.txt; }
tv() { tools/venv/bin/python -I tools/tv.py "$@"; }

# JDK 17 and the SDK. A JAVA_HOME that isn't a working 17 (an older or Intel-only JDK) is replaced.
java17() { case "$("$1/bin/java" -version 2>&1 || true)" in *'version "17.'*) return 0 ;; *) return 1 ;; esac; }
if [ -z "${JAVA_HOME:-}" ] || ! java17 "$JAVA_HOME"; then
  JAVA_HOME="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
  [ -n "$JAVA_HOME" ] && java17 "$JAVA_HOME" || JAVA_HOME=/opt/homebrew/opt/openjdk@17
fi
java17 "$JAVA_HOME" || { echo "ERROR: JDK 17 not found: brew install openjdk@17" >&2; exit 1; }
export JAVA_HOME PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME="${ANDROID_HOME:-$(for d in ~/Library/Android/sdk /opt/homebrew/share/android-commandlinetools; do [ -d "$d" ] && echo "$d" && break; done)}"
[ -f launcher/local.properties ] || echo "sdk.dir=$ANDROID_HOME" > launcher/local.properties

SDK=$(tv 'getprop ro.build.version.sdk' | tr -dc '0-9')
[ "${SDK:-0}" -ge 26 ] || { echo "ERROR: the TV runs SDK ${SDK:-?}; this launcher needs 26 (Android 8) or newer." >&2; exit 1; }

(cd launcher && ./gradlew --no-daemon -q --console=plain assembleRelease)
APK=launcher/build/outputs/apk/release/BraviaTV-UI-release.apk

tools/venv/bin/python -I tools/b64push.py "$APK" /data/local/tmp/braviatv.apk
OUT=$(tv 'pm install -r /data/local/tmp/braviatv.apk; rm -f /data/local/tmp/braviatv.apk')
echo "$OUT"
case "$OUT" in *Success*) ;; *) echo "ERROR: install failed; nothing else was changed." >&2; exit 1 ;; esac

# WRITE_SECURE_SETTINGS lets Home turn its remote-button service on, and back on after a force stop.
tv "pm grant $PKG android.permission.WRITE_SECURE_SETTINGS" || true
tv "am start -W -n $HOME_ACTIVITY" | grep -E 'Status|Error' || true
tv "cmd package set-home-activity --user 0 $HOME_ACTIVITY"
tv 'logcat -c -b crash; input keyevent KEYCODE_HOME'
sleep 3
FOCUS=$(tv 'dumpsys activity activities | grep mFocusedApp')
echo "$FOCUS"
case "$FOCUS" in
  *"$PKG"/*) ;;
  *) echo "ERROR: Home still opens something else; the stock launcher was left alone." >&2
     echo "On Google TV, check that com.google.android.tungsten.setupwraith is disabled (see debloat.txt)." >&2
     exit 1 ;;
esac
# Android keeps listing a crashed Home as focused, so also require a live process and a clean crash log.
if ! tv "pidof $PKG" | grep -q '[0-9]' || tv 'logcat -d -b crash' | grep -q "Process: $PKG"; then
  tv 'logcat -d -b crash' | grep -A3 'FATAL' | head -8 >&2
  echo "ERROR: the new Home crashes on start (above). The stock launcher was left alone." >&2
  exit 1
fi

# Home is ours: now the stock launcher can go, and the earlier launcher if it is there.
if tv "pm list packages -e $STOCK" | grep -qx "package:$STOCK"; then tv "pm disable-user --user 0 $STOCK"; fi
if tv "pm list packages $OLD" | grep -qx "package:$OLD"; then tv "pm uninstall $OLD"; fi
tv "cmd package compile -m speed -f $PKG" >/dev/null || true
echo "Installed. Home now opens $HOME_ACTIVITY."
