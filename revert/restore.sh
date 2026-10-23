#!/usr/bin/env bash
# Undo the debloat: re-enable every package in debloat.txt and give Home back to the stock Google TV launcher.
# Usage: TV_HOST=<tv ip> revert/restore.sh      (then reboot the TV)
set -euo pipefail
cd "$(dirname "$0")/.."
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0}"
tools/restore.sh debloat.txt com.google.android.apps.tv.launcherx
