#!/usr/bin/env bash
# Undo the changes made to one TV.
#
# Usage: TV_HOST=<tv ip> ./revert.sh tvs/<tv> [--all] [--remove-launcher [launchers/<launcher>]]
#
#   (default)          re-enable every package in the TV's debloat.txt and give Home back to its stock launcher
#   --all              re-enable every disabled package except those the TV came with disabled
#                      (factory-disabled.txt), even ones disabled by hand
#   --remove-launcher  also uninstall the launcher (launchers/dot unless another is named)
#
# Then reboot the TV: tools/venv/bin/python tools/tv.py reboot
set -euo pipefail
cd "$(dirname "$0")"
TV_DIR="${1:?usage: TV_HOST=<tv ip> $0 tvs/<tv> [--all] [--remove-launcher [launchers/<launcher>]]}"
shift
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0 $TV_DIR}"
export TV_HOST
[ -f "$TV_DIR/tv.properties" ] || { echo "ERROR: no $TV_DIR/tv.properties" >&2; exit 1; }
ALL=0
REMOVE=""
while [ $# -gt 0 ]; do
  case "$1" in
    --all) ALL=1 ;;
    --remove-launcher)
      REMOVE=launchers/dot
      case "${2:-}" in launchers/*) REMOVE="$2"; shift ;; esac ;;
    *) echo "unknown option: $1" >&2; exit 1 ;;
  esac
  shift
done

prop() { sed -n "s/^$1=//p" "$TV_DIR/tv.properties" | head -n 1; }
STOCK_HOME=$(prop stock_launcher)
PY=tools/venv/bin/python
tv() { "$PY" -I tools/tv.py "$@"; }

if [ "$ALL" = 1 ]; then
  for p in $(tv 'pm list packages -d' | tr -d '\r' | sed 's/^package://'); do
    grep -qx "$p" "$TV_DIR/factory-disabled.txt" 2>/dev/null && continue
    echo "enabling $p"
    tv "pm enable --user 0 $p"
  done
  tv "cmd package set-home-activity --user 0 $STOCK_HOME"
else
  tools/restore.sh "$TV_DIR/debloat.txt" "${STOCK_HOME%%/*}"
fi

if [ -n "$REMOVE" ]; then
  PKG=$(sed -n "s/^ *applicationId '\(.*\)'.*/\1/p" "$REMOVE/build.gradle")
  tv "pm uninstall $PKG"
fi
echo "Done. Reboot the TV: $PY tools/tv.py reboot"
