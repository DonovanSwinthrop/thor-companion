#!/bin/sh
# Copies the Radical Red nuzlocke overlay (the PC overlay's web/ folder) into the app's assets for the NUZLOCKE tab.
# Its sprites and data come from the game and the community docs, so app/src/main/assets/overlay/ is never
# committed (.gitignore): run this before a local build. Without it the NUZLOCKE tab doesn't show.
#   OVERLAY_WEB=~/Documents/"RR Nuzlocke Overlay"/web  (the default)
set -e
SRC="${OVERLAY_WEB:-$HOME/Documents/RR Nuzlocke Overlay/web}"
DST="$(dirname "$0")/../app/src/main/assets/overlay"
[ -f "$SRC/overlay.html" ] || { echo "No overlay at $SRC"; exit 1; }
rm -rf "$DST" && mkdir -p "$DST"
for f in "$SRC"/*; do
  case "$(basename "$f")" in manifest.webmanifest|*.pre_*|*.bak*) continue ;; esac
  cp -r "$f" "$DST/"   # packs/<game>/pack.js too (other games' data, built by packs/<game>/build_pack.py)
done
echo "Overlay copied: $(ls "$DST" | wc -l) files, $(du -sh "$DST" | cut -f1)"
