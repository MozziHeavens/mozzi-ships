#!/bin/bash
# Baja los ships de sus releases y los mete en assets/ships
set -u
shopt -s nullglob
D=app/src/main/assets/ships
mkdir -p "$D"
T=$(mktemp -d)
AAPT=$(ls "$ANDROID_HOME"/build-tools/*/aapt 2>/dev/null | sort -V | tail -1)

bajar() { # id repo tag patron
  local id=$1 repo=$2 tag=$3 patron=$4
  rm -rf "$T/$id"; mkdir -p "$T/$id"
  if [ -n "$tag" ]; then
    gh release download "$tag" -R "$repo" -p "$patron" -D "$T/$id" 2>/dev/null || return 1
  else
    gh release download -R "$repo" -p "$patron" -D "$T/$id" 2>/dev/null || return 1
  fi
  for z in "$T/$id"/*.zip "$T/$id"/*.ZIP; do (cd "$T/$id" && unzip -o -q "$(basename "$z")"); done
  local apk
  apk=$(find "$T/$id" -iname '*.apk' | head -1)
  [ -z "$apk" ] && return 1
  cp "$apk" "$D/$id.apk"
  echo "✅ $id ← $repo ${tag:-latest}"
}

bajar ocarina MozziHeavens/Shipwright-Android soh-universal-v1 '*.apk' \
  || echo "::warning::Ocarina (SoH Universal) no se pudo bajar"
bajar majora MozziHeavens/2ship2harkinian-Android 2ship-universal-v1 '*.apk' \
  || bajar majora linkzenic/2ship2harkinian-Android "" '*.apk' \
  || echo "::warning::Majora no se pudo bajar"
bajar mario64 HarbourMasters/Ghostship "" '*ndroid*' \
  || echo "::warning::Mario 64 (Ghostship) no se pudo bajar"
bajar starfox izzy2lost/Starship "" '*.apk' \
  || echo "::warning::Star Fox 64 (Starship) no se pudo bajar"

for f in "$D"/*.apk; do
  id=$(basename "$f" .apk)
  pkg=$("$AAPT" dump badging "$f" 2>/dev/null | sed -n "s/^package: name='\([^']*\)'.*/\1/p")
  abis=$(unzip -l "$f" | grep -o 'lib/[^/]*/' | sort -u | sed 's#lib/##;s#/##' | jq -R . | jq -s -c .)
  jq -n --arg id "$id" --arg a "$id.apk" --arg p "$pkg" --argjson abis "$abis" \
    '{id:$id, archivo:$a, pkg:$p, abis:$abis}'
done | jq -s . > "$D/info.json"

echo "===== Ships incluidos ====="
cat "$D/info.json"
ls -lh "$D"
