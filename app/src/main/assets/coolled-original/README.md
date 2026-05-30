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
