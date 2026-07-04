# APK LED feature port status

This tracks the LED/device behavior port, excluding Google Play Services, Firebase, analytics, ads, update/store prompts, and generic Android scaffolding.

## Rough completion estimate

Current branch is approximately **75-80% through the LED-facing port**.

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
- CoolLEDUX text program byte generation uses `FontUtils.getFontByteDataCoolleduxForEmoji(...)`-shaped framing (glyph count, total width, per-glyph widths, glyph bytes) and asset-backed glyph records for HELLO/digits/punctuation/symbols when the extracted font assets are installed.
- APK-shaped `getDataWithTextContentProgramContent(...)`, `getDataWithTextCombineProgram(...)`, `getDataForCombineProgram(...)`, `getDataForProgram(...)`, `getDataWithProgram(...)`, and compressed `getDataResult(...)` helpers are present.
- CoolLEDUX high-level text and original-asset program generation now uses the recovered APK program layout directly: 8 reserved program bytes, content count, reserved byte, then one or more length-prefixed content blocks.
- Raw GIF/animation payload wrapping now mirrors the recovered `CoolledUXUtils.getDataWithAnimationCombineProgram(...)`/`CoolleduxGifAnimationProgramContent` shape: content type `0x0c`, seven reserved bytes, layer type, reserved byte, start column/row, show width/height, 4-byte payload length, then raw file bytes.
- Static icon/image/emoji bitmap payload wrapping now mirrors the recovered `CoolledUXUtils.getDataWithGraffitiCombineProgram(...)` block shape (`0x02` plus coordinates, mode/speed/stayTime, 4-byte payload length). Bitmap pixels are encoded column-major as RGB444 transfer pairs to match `getDrawListDataFColor(...)` / `TextEmojiManagerCoolLEDUX.getColorDataWithColorWithRGB444Transfer(...)` structure.
- Main UI surface for most LED-facing command features.

## Partially ported / structurally tested

- CoolLEDUX text program generation.
  - Model/defaults are present.
  - 32px, 16px, 14px-bold, 12px, and 8px asset-backed glyph loading is present.
  - Matrix dimensions can come from scan metadata.
  - Staged APK-style program/combine/layer functions are present.
  - Exact text-content framing is covered by tests with verbatim glyph bytes, asset-backed HELLO glyph embedding, digits, punctuation, and a symbol.
  - High-level `ProgramContent.Text` now uses the same recovered `getDataWithProgram(...)` + text-content-block path instead of an extra synthetic combine-length wrapper.
  - Non-combine-mode text (scrolling, modes 2/3) now goes through the exact `FontUtils.getFontByteDataCoolleduxForEmoji` stream-mode port (`CoolleduxStreamText.kt`) - real per-glyph read/rescale/rotate/trim/spacing and exact final framing, not a placeholder. Combine-canvas modes (1, 4-13) still use the old placeholder.
  - Still needs live-device confirmation for final visual output.
- Original asset support.
  - Extraction/cataloging/runtime reads are present.
  - Asset upload routing is present.
  - Raw `.jt` template assets are preserved as raw payload inputs instead of being rasterized.
  - Raw `.gif`/animation bytes are now preserved and wrapped in the recovered APK `0x0c` raw animation block instead of being generically rasterized.
  - Bitmap extensions are rasterized into recovered graffiti block structure with column-major RGB444 transfer payloads.
  - **Confirmed exact against ground truth (2026-07-04)**, now that `CoolledUXUtils.java` and `TextEmojiManagerCoolLEDUX.java` fully decompile (see the jadx fix above - these files previously had failed-decompile methods too): `getDataWithAnimationCombineProgram(CoolleduxGifAnimationProgramContent)` (raw GIF `0x0c` block: content type, 7 reserved bytes, layerType, reserved byte, u16 col/row/width/height, u32 length, raw file bytes) and `getDataWithGraffitiCombineProgram(...)` -> `getDrawListDataFColor(...)` -> `TextEmojiManagerCoolLEDUX.getColorDataWithColorWithRGB444Transfer(...)`/`rgb444Transfer(...)` (column-major outer loop, row inner loop, thresholds 238/47/14, byte pair `[0|redNibble][greenNibble<<4|blueNibble]`) match `CoolleduxProgramBytecode.rawGifContentBlock`/`graffitiContentBlock` and `OriginalLedAssetPayloadEncoder.rgb444TransferColorBytes`/`bitmapToRgb444TransferColumnMajor` in this repo byte-for-byte.
  - Live-device validation is still required to confirm the LED matrix actually renders these payloads as expected (byte-exactness vs. the APK's own builder is now confirmed; hardware rendering behavior is a separate, still-open question).
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

1. `FontUtils.getFontByteDataCoolleduxForEmoji(...)`: **the "source unavailable" blocker is resolved** - `FontUtils.java` was always present in the extracted APK tree, but jadx's default decompile silently failed on this method (and ~20 other LED-relevant files) and emitted an `UnsupportedOperationException("Method not decompiled")` stub instead of real code. Forcing `jadx -m simple` fixed the decompile, though its rendered *control flow* (not its instruction-level expressions) turned out to have multiple bugs of its own for this specific method and had to be cross-checked against raw smali at every branch point - see `docs/APK_REVERSE_ENGINEERING_NOTES.md` for the three confirmed jadx control-flow bugs found this way.
   - **The stream-mode branch (mode NOT in {1,4,5,6,7,8,9,10,11,12,13} - chiefly scrolling text, modes 2/3) is now ported and wired into the real upload path** for plain (non-emoji, font-table-supported-script) text: `app/src/main/java/com/cooled/core/protocol/CoolleduxStreamText.kt`, dispatched from `ProgramComposer.getDataWithTextContentProgramContent` whenever `mode` isn't a combine-canvas mode and no verbatim `glyphBytes` override is supplied. Covers per-glyph read/rescale/rotate/trim (including the verified double-trim-pass for 90/270 rotation), inter-glyph spacing, and the exact `[2-byte token count][4-byte running column total][per-glyph (colCount, type, bytes) chunks]` final framing.
   - **The combine-canvas branch (modes 1, 4-13) is still the old placeholder** - it needs the word-wrapped canvas assembly plus a byte-pattern-matching re-alignment pass against the original per-glyph bytes (to work out how much padding checkSegment/getCenteredDataBytes inserted around each glyph) that the stream branch doesn't need. `checkSegmentN`/`addEmptyColumnForData*ToThe*` (word-wrap), `deleteEmptyColumnFor*` (trim), `getCenteredDataBytes`/`processBytesCenteredN` (centering), and `transfer<N>FontTo<M>` (rescale) are all already ported and available for this; what's missing is the assembly logic itself.
   - Still out of scope for either mode: `ArabicCharDotMatrixGenerator` (RTL/CJK runtime-drawn glyphs) and emoji/image tokens from `TextEmojiManagerCoolLEDUX` - both currently throw a clear error rather than silently producing wrong output.
2. Icon/image/GIF encoding is closer to the recovered APK builders (`0x02` graffiti and `0x0c` raw GIF/animation wrappers), but not fully proven exact. The extracted tree currently contains no `.gif` files, so no GIF golden vector can be produced from local assets yet; bitmap RGB444 byte order still needs comparison against `TextEmojiManagerCoolLEDUX` vectors.
3. Clock/date/weather/temp/humidity/scoreboard/time-count/business-hours template composition still needs exact APK functions or vectors. Raw `.jt` payload handling is covered, but dynamic template assembly is not proven.
4. Scan-record parsing is deterministic for the recovered manufacturer layout, but additional real raw advertisements should be added if devices advertise other CoolLED layouts.
5. Live-device validation is still required to claim that text/icon/GIF/template payloads render exactly as the original APK.

## APK functions ported or represented

- `CoolledUXUtils.getDataWithAnimationCombineProgram(...)` for raw image-id/file GIF payloads
- `CoolledUXUtils.getDataWithGraffitiCombineProgram(...)` block wrapping and column-major RGB444 payload ordering
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

Continue porting display-content byte parity. Command/control wiring is no longer the bottleneck; exact content generation and live-device visual validation are. Concretely: replace `ProgramComposer.getFontByteDataCoolleduxForEmoji` in `app/src/main/java/com/cooled/core/protocol/ProgramContent.kt` with the mode-dependent combine/stream layout described in `docs/APK_REVERSE_ENGINEERING_NOTES.md`, porting the canvas-dedup helper functions (`checkSegmentN`/`addEmptyColumn*`/`deleteEmptyColumn*`/`processBytesCenteredN`/`transfer<N>FontTo<M>`/`getCenteredDataBytes`) alongside it.

## Build/test environment (2026-07-04)

`./gradlew testDebugUnitTest` now builds and passes (69/69) in this container. It required a local (gitignored) `local.properties` pointing at a pre-existing Android SDK checkout, and an `aapt2` fix for aarch64 (no native `linux-aarch64` aapt2 exists upstream) - see `docs/TESTING.md` for the exact fix. This does not change LED protocol behavior; it just means CI-equivalent checks are actually runnable here now instead of failing before any test code executes.
