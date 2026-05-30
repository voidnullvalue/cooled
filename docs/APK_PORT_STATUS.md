# APK LED feature port status

This tracks the LED/device behavior port, excluding Google Play Services, Firebase, analytics, ads, update/store prompts, and generic Android scaffolding.

## Rough completion estimate

Current branch is approximately **70-75% through the LED-facing port**.

The core BLE/control command surface is mostly present. The remaining risk is still concentrated in exact display-content byte parity for non-text assets and templates, plus live-device validation of the final payload bytes.

## Exact / substantially ported

- BLE scan/connect/discover/notify/write path.
- Permission handling for Android BLE/location behavior.
- APK-style write splitting/pacing: 180-byte writes after MTU 247, 20-byte fallback, 15 ms pacing.
- Core frame codec, escaping, CRC, LZSS compression, start headers, data chunks, ACK parsing, transfer retries.
- CoolLEDUX start opcode/chunk opcode path.
- Device info query and parser.
- OTA version query and parser.
- Power, brightness, rhythm/music/mic, mirror/rotate, color mode.
- Password check/set frame generation.
- Drive-state query/set and parser.
- APK-style time sync.
- APK-style timer-switch command surface.
- Countdown query/reset/start/stop.
- Stopwatch query/reset/start/stop.
- Scoreboard query/reset/start/stop.
- Volume.
- Tomato query.
- Temperature/humidity query and parser.
- Alarm query/set and parser.
- Reminder list/detail/delete and parser.
- Night mode set/off and parser.
- Original APK asset extraction helper.
- Original APK LED asset manifest generation.
- Runtime LED asset catalog loader.
- Runtime byte reader for extracted original APK LED assets.
- Upload-oriented original LED asset catalog helpers.
- Original APK asset program planner.
- Original APK asset upload route through the ViewModel and UI.
- Scan-result LED metadata model.
- Deterministic scan-record metadata parser for the recovered manufacturer-data layout:
  - device id: bytes 0-1, big-endian
  - rows: byte 4
  - columns: byte 5
  - color type: byte 6
- Scan-derived matrix dimensions feeding CoolLEDUX text and asset-program generation.
- Asset-backed CoolLEDUX font readers for:
  - `fonts/8_small` (8 bytes/glyph)
  - `fonts/32_32_large` (128 bytes/glyph, bold 32x32)
  - `fonts/32_32_small` (128 bytes/glyph, regular 32x32)
  - `raw-assets/assets/UNICODE16` / `UNICODE16_bold` (32 bytes/glyph)
  - `raw-assets/assets/UNICODE12` / `UNICODE12_BOLD` (24 bytes/glyph)
  - `raw-assets/assets/flutter_assets/assets/coolledux/font_library/unicode_14_bold` (28 bytes/glyph)
  - `raw-assets/assets/flutter_assets/assets/coolledux/font_library/unicode_16_bold` fallback (32 bytes/glyph)
- CoolLEDUX text program byte generation uses asset-backed glyph records for HELLO/digits/punctuation/symbols when the extracted font assets are installed.
- APK-shaped `getDataWithTextContentProgramContent(...)`, `getDataWithTextCombineProgram(...)`, `getDataForCombineProgram(...)`, `getDataForProgram(...)`, `getDataWithProgram(...)`, and compressed `getDataResult(...)` helpers are present.
- Main UI surface for most LED-facing command features.

## Partially ported / structurally tested

- CoolLEDUX text program generation.
  - Model/defaults are present.
  - 32px, 16px, 14px-bold, 12px, and 8px asset-backed glyph loading is present.
  - Matrix dimensions can come from scan metadata.
  - Staged APK-style program/combine/layer functions are present.
  - Exact text-content framing is covered by tests with verbatim glyph bytes and asset-backed HELLO glyph embedding.
  - Still needs live-device confirmation for final visual output.
- Original asset support.
  - Extraction/cataloging/runtime reads are present.
  - Asset upload routing is present.
  - Raw `.jt` template assets are preserved as raw payload inputs instead of being rasterized.
  - Bitmap payload encoder scaffold exists for bitmap extensions.
  - Exact icon/animation/GIF/template transforms are not yet proven against original APK golden vectors.
- Template/content generators.
  - Extracted business-hours `.jt` assets are identified as raw template payload inputs.
  - Template assets are wired through the content-program system as `OriginalAsset` selections, not random standalone writes.
  - Dynamic clock/date/weather/scoreboard/time-count template builders still need APK source/golden-vector confirmation before being marked exact.
- Family/capability detection.
  - Prefix coverage remains stable for `CoolLEDM`, `CoolLEDU`, `CoolLEDUX`, `CoolLEDX`, `CoolLEDS`, and `iLedClock`.
  - Deeper APK capability gating by device-info/model/color-type remains pending.
- Program playlist behavior.
  - Start-header index/count/showCount handling is implemented and tested as playlist-level metadata.
  - Multi-program sequencing beyond single package construction still needs live upload validation.

## Still approximate / blocked

1. `FontUtils.getFontByteDataCoolleduxForEmoji(...)` transform details are now asset-backed for glyph table reads, but the exact original high-level transform/rotation/mirroring/color handling remains blocked because the recovered source set still does not include `FontUtils.java`.
2. Icon/image/GIF encoding is not fully exact. The extracted tree currently contains no `.gif` files, so no GIF golden vector can be produced from local assets yet. Bitmap-extension rasterization remains a scaffold until APK builders and golden vectors are recovered.
3. Clock/date/weather/temp/humidity/scoreboard/time-count/business-hours template composition still needs exact APK functions or vectors. Raw `.jt` payload handling is covered, but dynamic template assembly is not proven.
4. Scan-record parsing is deterministic for the recovered manufacturer layout, but additional real raw advertisements should be added if devices advertise other CoolLED layouts.
5. Live-device validation is still required to claim that text/icon/GIF/template payloads render exactly as the original APK.

## APK functions ported or represented

- `CoolledUXUtils.getDataWithTextContentProgramContent(...)`
- `CoolledUXUtils.getDataWithTextCombineProgram(...)` for normal text content
- `CoolledUXUtils.getDataForCombineProgram(...)` for known LED-facing text content
- `CoolledUXUtils.getDataForProgram(...)`
- `CoolledUXUtils.getDataWithProgram(...)`
- `CoolledUXUtils.getDataResult(...)` as body + LZSS compressed chunks
- `FontUtils.readUnicode3232(...)` / `readUnicode3232Bold(...)` equivalent table reads
- `FontUtils.readUnicode16(...)` / `readUnicode16Bold(...)` equivalent table reads
- `FontUtils.readUnicode12(...)` / `readUnicode12Bold(...)` equivalent table reads
- Small 8px font table reads from `8_small`

## Current priority

Continue porting display-content byte parity. Command/control wiring is no longer the bottleneck; exact content generation and live-device visual validation are.
