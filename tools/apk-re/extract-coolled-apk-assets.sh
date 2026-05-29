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

mkdir -p "$out_dir/fonts"

tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

unzip -q "$apk_path" -d "$tmp_dir"

copy_asset() {
  local name="$1"
  local src="$tmp_dir/assets/$name"
  if [[ -f "$src" ]]; then
    cp "$src" "$out_dir/fonts/$name"
    echo "copied assets/$name -> app/src/main/assets/coolled-original/fonts/$name"
  else
    echo "missing assets/$name" >&2
  fi
}

copy_asset "8_small"
copy_asset "32_32_large"
copy_asset "32_32_small"

cat > "$out_dir/README.md" <<'EOF'
# Original CoolLED APK assets

These files are extracted from the original `com.jtkj.led1248` APK and are intentionally not hand-authored.

Expected files:

- `fonts/8_small` — 128 glyphs × 8 bytes = 1,024 bytes
- `fonts/32_32_large` — 65,536 glyphs × 128 bytes = 8,388,608 bytes
- `fonts/32_32_small` — 65,536 glyphs × 128 bytes = 8,388,608 bytes

Extraction command:

```bash
tools/apk-re/extract-coolled-apk-assets.sh /path/to/base.apk
```
EOF

sha256sum "$out_dir"/fonts/* 2>/dev/null | tee "$out_dir/SHA256SUMS" || true

echo "done: $out_dir"