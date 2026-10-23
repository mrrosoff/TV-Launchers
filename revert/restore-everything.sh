#!/usr/bin/env bash
# Belt and braces: re-enable EVERY package that is disabled now except the three Sony disabled at the
# factory (factory-disabled.txt), even ones not in debloat.txt, then put Home back on the stock launcher.
# Usage: TV_HOST=<tv ip> revert/restore-everything.sh      (then reboot the TV)
set -euo pipefail
cd "$(dirname "$0")/.."
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0}"
PY=tools/venv/bin/python
for p in $($PY tools/tv.py 'pm list packages -d' | tr -d '\r' | sed 's/^package://'); do
  grep -qx "$p" revert/factory-disabled.txt && continue
  echo "enabling $p"; $PY tools/tv.py "pm enable --user 0 $p"
done
$PY tools/tv.py 'cmd package set-home-activity --user 0 com.google.android.apps.tv.launcherx/.home.HomeActivity'
echo "Done. Reboot the TV: $PY tools/tv.py reboot"
