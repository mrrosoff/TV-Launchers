#!/usr/bin/env bash
# Remove the custom launcher completely. Runs restore.sh first so Home has somewhere to go.
# Usage: TV_HOST=<tv ip> revert/remove-launcher.sh
set -euo pipefail
cd "$(dirname "$0")/.."
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0}"
revert/restore.sh
tools/venv/bin/python tools/tv.py 'pm uninstall dev.mrrosoff.braviatv'
tools/venv/bin/python tools/tv.py reboot
