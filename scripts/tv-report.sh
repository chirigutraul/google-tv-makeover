#!/usr/bin/env bash
# Read-only snapshot of the TV: model, memory, swap, storage, biggest processes, apps, current tweaks.
# Changes nothing.
#
# Usage: scripts/tv-report.sh <ip:port>

set -euo pipefail

tv="${1:-${TV_ADDR:-}}"
[[ -n "$tv" ]] || { echo "Usage: $0 <ip:port>   (from Developer options > Wireless debugging)"; exit 1; }

adb connect "$tv" >/dev/null
adb -s "$tv" get-state >/dev/null 2>&1 || { echo "Error: TV not reachable at $tv. Accept 'Allow debugging?' on the TV if shown."; exit 1; }
S() { adb -s "$tv" shell "$@"; }

echo "== TV"
echo "Model:   $(S getprop ro.product.model)"
echo "Android: $(S getprop ro.build.version.release)"
echo "Screen:  $(S wm size | tail -1)"

echo
echo "== Memory"
S cat /proc/meminfo | grep -E "MemTotal|MemAvailable|SwapTotal|SwapFree"

echo
echo "== Storage"
S df -h /data | tail -1

echo
echo "== Biggest processes (RAM)"
S dumpsys meminfo | sed -n '/Total PSS by process/,/Total PSS by OOM/p' | grep "K:" | head -20

echo
echo "== Apps you installed"
S pm list packages -3 | sed 's/package://' | sort

echo
echo "== Current tweaks"
for k in window_animation_scale transition_animation_scale animator_duration_scale; do
  echo "$k = $(S settings get global $k)"
done
echo "max_cached_processes = $(S device_config get activity_manager max_cached_processes)"
echo "home app = $(S cmd role get-role-holders android.app.role.HOME)"
echo "disabled packages:"
S pm list packages -d | sed 's/package:/  /'
