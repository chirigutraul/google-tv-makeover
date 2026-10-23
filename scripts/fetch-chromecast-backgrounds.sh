#!/usr/bin/env bash
# Download N random Chromecast backgrounds (1920x1080) into a folder, named after their photographers.
# Source: https://github.com/dconnolly/chromecast-backgrounds (Google's Ambient mode photos; personal use).
#
# Usage: scripts/fetch-chromecast-backgrounds.sh [count=100] [folder=screensavers]

set -euo pipefail

count="${1:-100}"
dest="${2:-screensavers}"
list_url="https://raw.githubusercontent.com/dconnolly/chromecast-backgrounds/master/backgrounds.json"

command -v python3 >/dev/null || { echo "Error: python3 is needed to read the photo list"; exit 1; }
mkdir -p "$dest"

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

curl -sfL "$list_url" -o "$tmp/backgrounds.json" || { echo "Error: couldn't download the photo list"; exit 1; }

# Shuffle, and name each file "Photo by <author>.jpg" (numbered when an author repeats).
python3 -I - "$tmp/backgrounds.json" "$((count * 2))" > "$tmp/picks.tsv" <<'PY'
import json, random, re, sys
entries = json.load(open(sys.argv[1]))
random.shuffle(entries)
seen = {}
for e in entries[: int(sys.argv[2])]:
    author = re.sub(r"[^A-Za-z0-9 .'-]", "", e.get("author") or "Unknown").strip() or "Unknown"
    seen[author] = seen.get(author, 0) + 1
    suffix = "" if seen[author] == 1 else f" {seen[author]}"
    print(f"{e['url']}\tPhoto by {author}{suffix}.jpg")
PY

# Many links in the list are old; keep going until enough valid photos are saved.
ok=0
while IFS=$'\t' read -r url name; do
  (( ok >= count )) && break
  [[ -e "$dest/$name" ]] && continue
  if curl -sfL --max-time 20 "$url" -o "$tmp/photo" && file -b --mime-type "$tmp/photo" | grep -q '^image/jpeg$'; then
    mv "$tmp/photo" "$dest/$name"
    ok=$((ok + 1))
    printf '\r  downloaded %d/%d' "$ok" "$count"
  fi
done < "$tmp/picks.tsv"

echo
echo "Done: $ok photo(s) in $dest. Delete any you don't like, then push them with scripts/push-screensavers.sh"
