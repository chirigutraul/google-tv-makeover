#!/usr/bin/env bash
# Copy a folder of photos to the TV as screensavers.
#
# Usage: TV_ADDR=<ip:port> scripts/push-screensavers.sh <folder> [--replace] [--dry-run]
#   --replace   also delete photos on the TV that aren't in <folder>
#   --dry-run   show what would happen, change nothing
#
# Env: TV_ADDR, your TV's address from Developer options > Wireless debugging

set -euo pipefail

TV_ADDR="${TV_ADDR:-}"
TV_DIR="/sdcard/Pictures/Screensavers"
MAX_WIDTH=1920

src="" replace=false dry=false
for arg in "$@"; do
  case "$arg" in
    --replace) replace=true ;;
    --dry-run) dry=true ;;
    -h|--help) sed -n '2,8p' "$0"; exit 0 ;;
    *) src="$arg" ;;
  esac
done

[[ -n "$TV_ADDR" ]] || { echo "Error: set TV_ADDR, e.g. TV_ADDR=192.168.1.50:5555 $0 <folder>"; exit 1; }
[[ -n "$src" && -d "$src" ]] || { echo "Error: pass a folder of photos. Example: TV_ADDR=192.168.1.50:5555 scripts/push-screensavers.sh ~/Pictures/tv"; exit 1; }
command -v adb >/dev/null || { echo "Error: adb not found. Install it: brew install --cask android-platform-tools"; exit 1; }

photos=()
while IFS= read -r -d '' f; do photos+=("$f"); done < <(find "$src" -maxdepth 1 -type f \( -iname '*.jpg' -o -iname '*.jpeg' -o -iname '*.png' -o -iname '*.webp' \) -print0 | sort -z)
(( ${#photos[@]} > 0 )) || { echo "Error: no .jpg, .png or .webp files in $src"; exit 1; }

echo "Found ${#photos[@]} photo(s) in $src"

if $dry; then
  for f in "${photos[@]}"; do echo "  would copy: $(basename "$f")"; done
  $replace && echo "  would delete photos on the TV that aren't in this folder"
  echo "Dry run: nothing changed."
  exit 0
fi

adb connect "$TV_ADDR" >/dev/null
adb -s "$TV_ADDR" get-state >/dev/null 2>&1 || { echo "Error: TV not reachable at $TV_ADDR. Is it on, with wireless debugging enabled?"; exit 1; }
S() { adb -s "$TV_ADDR" shell "$@"; }

S mkdir -p "$TV_DIR"

# Resize to 1080p width on the Mac first, so photos load fast and use little TV memory
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

names=()
for f in "${photos[@]}"; do
  name="$(basename "$f")"
  names+=("$name")
  cp "$f" "$tmp/$name"
  width=$(sips -g pixelWidth "$tmp/$name" 2>/dev/null | awk '/pixelWidth/{print $2}')
  if [[ -n "$width" && "$width" -gt $MAX_WIDTH ]]; then
    sips --resampleWidth $MAX_WIDTH "$tmp/$name" >/dev/null
  fi
  adb -s "$TV_ADDR" push "$tmp/$name" "$TV_DIR/$name" >/dev/null
  echo "  copied: $name"
done

if $replace; then
  while IFS= read -r existing; do
    existing="${existing%$'\r'}"
    [[ -z "$existing" ]] && continue
    keep=false
    for n in "${names[@]}"; do [[ "$n" == "$existing" ]] && keep=true && break; done
    if ! $keep; then S rm -f "$TV_DIR/$existing"; echo "  removed: $existing"; fi
  done < <(S ls -1 "$TV_DIR" 2>/dev/null)
fi

echo "Done: ${#photos[@]} photo(s) on the TV in $TV_DIR"
