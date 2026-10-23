#!/usr/bin/env bash
# Build a launcher for one TV and make it that TV's home screen.
#
# Order matters, so the TV is never left without a Home: install, start it, make it the default Home,
# check that Home really opens it and doesn't crash, and only then disable the TV's stock launcher (unless
# the TV's tv.properties keeps it) and remove launchers this one replaces. Needs JDK 17 and the Android SDK.
#
# Usage: TV_HOST=<tv ip> ./install.sh tvs/<tv> [launchers/<launcher>]      (launcher defaults to launchers/dot)
set -euo pipefail
cd "$(dirname "$0")"
TV_DIR="${1:?usage: TV_HOST=<tv ip> $0 tvs/<tv> [launchers/<launcher>]}"
LAUNCHER="${2:-launchers/dot}"
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0 $TV_DIR}"
export TV_HOST
[ -f "$TV_DIR/tv.properties" ] || { echo "ERROR: no $TV_DIR/tv.properties" >&2; exit 1; }
[ -f "$LAUNCHER/build.gradle" ] || { echo "ERROR: no launcher at $LAUNCHER" >&2; exit 1; }
TV_DIR="$(cd "$TV_DIR" && pwd)"

prop() { sed -n "s/^$1=//p" "$TV_DIR/tv.properties" | head -n 1; }
STOCK_HOME=$(prop stock_launcher)
STOCK=${STOCK_HOME%%/*}
KEEP_STOCK=$(prop keep_stock_launcher)
PKG=$(sed -n "s/^ *applicationId '\(.*\)'.*/\1/p" "$LAUNCHER/build.gradle")
# Earlier names of launchers from this repo, and the cleanyourtv launcher it started from.
REPLACES="dev.mrrosoff.braviatv com.gathin.tvhome"

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
[ -f "$LAUNCHER/local.properties" ] || echo "sdk.dir=$ANDROID_HOME" > "$LAUNCHER/local.properties"

SDK=$(tv 'getprop ro.build.version.sdk' | tr -dc '0-9')
[ "${SDK:-0}" -ge 26 ] || { echo "ERROR: the TV runs SDK ${SDK:-?}; the launchers need 26 (Android 8) or newer." >&2; exit 1; }

(cd "$LAUNCHER" && ./gradlew --no-daemon -q --console=plain assembleRelease "-Ptv=$TV_DIR")
APK=$(ls "$LAUNCHER"/build/outputs/apk/release/*.apk | head -n 1)

tools/venv/bin/python -I tools/b64push.py "$APK" /data/local/tmp/launcher.apk
OUT=$(tv 'pm install -r /data/local/tmp/launcher.apk; rm -f /data/local/tmp/launcher.apk')
echo "$OUT"
case "$OUT" in *Success*) ;; *) echo "ERROR: install failed; nothing else was changed." >&2; exit 1 ;; esac

# A Home candidate appearing makes Android forget the chosen Home, so a kept stock launcher is enabled
# before ours is chosen, never after.
if [ "$KEEP_STOCK" = true ] && [ -n "$STOCK" ]; then tv "pm enable --user 0 $STOCK" >/dev/null; fi

HOME_ACTIVITY=$(tv "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME $PKG" | tail -n 1 | tr -d '\r')
case "$HOME_ACTIVITY" in "$PKG"/*) ;; *) echo "ERROR: $PKG has no Home activity." >&2; exit 1 ;; esac

# WRITE_SECURE_SETTINGS lets a launcher turn its remote-button service on, and back on after a force stop.
tv "pm grant $PKG android.permission.WRITE_SECURE_SETTINGS" 2>/dev/null || true
# The microphone, for launchers with voice search.
tv "pm grant $PKG android.permission.RECORD_AUDIO" 2>/dev/null || true
tv "am start -W -n $HOME_ACTIVITY" | grep -E 'Status|Error' || true
tv "cmd package set-home-activity --user 0 $HOME_ACTIVITY"
tv 'logcat -c -b crash; input keyevent KEYCODE_HOME'
# A fresh install can take a few seconds to draw its first frame, so give it up to 15.
for _ in 1 2 3 4 5; do
  sleep 3
  FOCUS=$(tv 'dumpsys activity activities | grep mFocusedApp')
  case "$FOCUS" in *"$PKG"/*) break ;; esac
done
echo "$FOCUS"
case "$FOCUS" in
  *"$PKG"/*) ;;
  *) echo "ERROR: Home still opens something else; the stock launcher was left alone." >&2
     echo "On Google TV, check that com.google.android.tungsten.setupwraith is disabled (see the TV's debloat.txt)." >&2
     exit 1 ;;
esac
# Android keeps listing a crashed Home as focused, so also require a live process and a clean crash log.
if ! tv "pidof $PKG" | grep -q '[0-9]' || tv 'logcat -d -b crash' | grep -q "Process: $PKG"; then
  tv 'logcat -d -b crash' | grep -A3 'FATAL' | head -8 >&2
  echo "ERROR: the new Home crashes on start (above). The stock launcher was left alone." >&2
  exit 1
fi

# Home is ours: now the stock launcher can go (unless kept), and anything this launcher replaces.
if [ "$KEEP_STOCK" != true ] && [ -n "$STOCK" ] && tv "pm list packages -e $STOCK" | grep -qx "package:$STOCK"; then
  tv "pm disable-user --user 0 $STOCK"
fi
for old in $REPLACES; do
  [ "$old" = "$PKG" ] && continue
  if tv "pm list packages $old" | grep -qx "package:$old"; then tv "pm uninstall $old"; fi
done
tv "cmd package compile -m speed -f $PKG" >/dev/null || true
echo "Installed. Home now opens $HOME_ACTIVITY."
