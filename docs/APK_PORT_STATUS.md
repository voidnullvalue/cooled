# APK LED feature port status

This tracks the LED/device behavior port, excluding Google Play Services, Firebase, analytics, ads, update/store prompts, and generic Android scaffolding.

## Rough completion estimate

Current branch is approximately **65-70% through the LED-facing port**.

That estimate is intentionally conservative. The core BLE/control command surface is mostly present. The remaining risk is concentrated in exact display-content byte parity: text, emoji/font transforms, icon/image payloads, GIF/animation payloads, original APK template mapping, and exact scan-record metadata offsets.

## Ported or substantially wired

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
- Initial scan-record metadata parser shell.
- Scan-derived matrix dimensions feeding CoolLEDUX text and asset-program generation.
- 32px font asset reader plumbing for `32_32_large`, `32_32_small`, and `8_small`.
- Main UI surface for most LED-facing command features.
- Bitmap payload encoder scaffold for original APK LED image/icon/emoji/animation assets.

## Partially ported

- CoolLEDUX text program generation.
  - Model/defaults are present.
  - 32px asset-backed glyph loading is present.
  - Matrix dimensions can come from scan metadata.
  - Staged APK-style program/combine/layer functions are present.
  - Exact `CoolledUXUtils` byte layout is still not fully proven.
- Scan-record parsing.
  - Metadata shell exists.
  - Exact original offsets still need to be verified against decompiled APK methods.
- Original asset support.
  - Extraction/cataloging/runtime reads are present.
  - Asset upload routing is present.
  - Bitmap payload encoder scaffold exists.
  - Exact icon/animation/GIF/template transforms are not yet proven.

## Remaining major work

### High risk / high value

1. Replace the current staged CoolLEDUX text-program byte layout with exact recovered APK layouts for:
   - `getDataWithTextContentProgramContent(...)`
   - `getDataWithTextCombineProgram(...)`
   - `getDataForCombineProgram(...)`
   - `getDataForProgram(...)`
   - `getDataResult(...)`

2. Port exact `FontUtils.getFontByteDataCoolleduxForEmoji(...)` transform behavior.

3. Finish exact icon/image program generation.

4. Finish exact GIF/animation program generation.

5. Port clock/date/frame/weather/temp/scoreboard/time-count program-content templates if they generate LED display payloads rather than only control commands.

### Medium risk

6. Replace scan-record parser heuristics with exact APK offsets.

7. Replace generic capability detection with APK-style family/feature detection using name, scan record, device info, and color type.

8. Add program-slot/show-count behavior matching original app for multi-program playlists.

9. Add local asset selection/picker models for original APK icon/animation/template categories.

### Lower risk

10. UI organization into tabs/screens similar to original LED feature areas.

11. Better summaries for parsed alarm/reminder/timer state.

12. Golden-vector tests after exact byte layouts are ported.

## Current priority

Continue porting display-content byte parity. Command/control wiring is no longer the bottleneck; exact content generation is.