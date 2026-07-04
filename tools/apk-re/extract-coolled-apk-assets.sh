#!/usr/bin/env bash
set -euo pipefail

apk_path="${1:-}"
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
out_dir="$repo_root/app/src/main/assets/coolled-original"

if [[ -z "$apk_path" ]]; then
  echo "Usage: $0 /path/to/base.apk" >&2
  exit 2
fi

if [[ ! -f "$apk_path" ]]; then
  echo "APK not found: $apk_path" >&2
  exit 2
fi

rm -rf "$out_dir"
mkdir -p "$out_dir/fonts" "$out_dir/raw-assets"

tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

unzip -q "$apk_path" -d "$tmp_dir"

copy_font() {
  local name="$1"
  local src="$tmp_dir/assets/$name"
  if [[ -f "$src" ]]; then
    cp "$src" "$out_dir/fonts/$name"
    echo "copied font assets/$name"
  else
    echo "missing font assets/$name" >&2
  fi
}

copy_tree_if_present() {
  local rel="$1"
  local src="$tmp_dir/$rel"
  local dest="$out_dir/raw-assets/$rel"
  if [[ -e "$src" ]]; then
    mkdir -p "$(dirname "$dest")"
    cp -R "$src" "$dest"
    echo "copied $rel"
  fi
}

copy_font "8_small"
copy_font "32_32_large"
copy_font "32_32_small"

# Preserve LED-facing local payload/content assets for later exact ports. This intentionally
# excludes Google/Firebase/store/cloud scaffolding; it copies only bundled APK content that can
# affect generated LED bytes.
while IFS= read -r asset; do
  base="$(basename "$asset")"
  case "$base" in
    8_small|32_32_large|32_32_small) continue ;;
  esac
  case "$asset" in
    assets/google*|assets/firebase*|assets/com.google*|assets/play*|assets/crashlytics*) continue ;;
  esac
  mkdir -p "$out_dir/raw-assets/$(dirname "$asset")"
  cp "$tmp_dir/$asset" "$out_dir/raw-assets/$asset"
  echo "copied $asset"
done < <(cd "$tmp_dir" && find assets -type f | sort)

# Resource drawables/raw may contain local LED icons/animations/templates referenced by the APK.
# Keep them in a separate bucket so the app can catalog them without reimplementing unrelated
# Android framework packaging.
copy_tree_if_present "res/drawable"
copy_tree_if_present "res/drawable-nodpi"
copy_tree_if_present "res/drawable-hdpi-v4"
copy_tree_if_present "res/drawable-xxhdpi-v4"
copy_tree_if_present "res/drawable-v21"
copy_tree_if_present "res/drawable-v23"
copy_tree_if_present "res/raw"

cat > "$out_dir/README.md" <<'EOF'
# Original CoolLED APK assets

These files are extracted from the original `com.jtkj.led1248` APK and are intentionally not hand-authored.

The clone ports LED/device behavior only. These extracted assets are local LED content inputs: fonts, emoji/icon tables, GIF/animation resources, and other payload-generation assets. Google Play Services, Firebase, ads, telemetry, update prompts, and unrelated Android scaffolding are intentionally excluded from the source port.

Expected font files:

- `fonts/8_small` — 128 glyphs × 8 bytes = 1,024 bytes
- `fonts/32_32_large` — 65,536 glyphs × 128 bytes = 8,388,608 bytes
- `fonts/32_32_small` — 65,536 glyphs × 128 bytes = 8,388,608 bytes

Additional extracted LED-relevant assets are stored under:

- `raw-assets/assets/`
- `raw-assets/res/drawable*/`
- `raw-assets/res/raw/`

Extraction command:

```bash
tools/apk-re/extract-coolled-apk-assets.sh /path/to/base.apk
```
EOF

classify_asset() {
  local rel="$1"
  local lower="${rel,,}"
  case "$lower" in
    *32_32_large|*32_32_small|*8_small) echo "font" ;;
    *.gif|*gif*) echo "animation" ;;
    *.png|*.webp|*.jpg|*.jpeg|*.bmp|*.xml) echo "image" ;;
    *emoji*|*emot*|*face*) echo "emoji" ;;
    *icon*|*ico*) echo "icon" ;;
    *clock*|*time*|*date*) echo "clock-template" ;;
    *weather*|*temp*|*humid*) echo "sensor-template" ;;
    *) echo "payload-asset" ;;
  esac
}

manifest="$out_dir/LED_ASSET_MANIFEST.tsv"
printf 'kind\tpath\tsize_bytes\tsha256\n' > "$manifest"
while IFS= read -r -d '' file; do
  rel="${file#$out_dir/}"
  case "$rel" in
    README.md|SHA256SUMS|LED_ASSET_MANIFEST.tsv) continue ;;
  esac
  kind="$(classify_asset "$rel")"
  size="$(wc -c < "$file" | tr -d ' ')"
  sha="$(sha256sum "$file" | awk '{print $1}')"
  printf '%s\t%s\t%s\t%s\n' "$kind" "$rel" "$size" "$sha" >> "$manifest"
done < <(find "$out_dir" -type f -print0 | sort -z)

{
  echo "# SHA256 manifest for extracted original APK assets"
  find "$out_dir" -type f ! -name SHA256SUMS -print0 | sort -z | xargs -0 sha256sum
} | tee "$out_dir/SHA256SUMS" >/dev/null

echo "done: $out_dir"