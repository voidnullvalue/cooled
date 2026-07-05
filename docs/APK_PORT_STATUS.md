# APK LED feature port status

This tracks the LED/device behavior port, excluding Google Play Services, Firebase, analytics, ads, update/store prompts, and generic Android scaffolding.

## Rough completion estimate

Current branch is approximately **80-85% through the LED-facing port** (2026-07-05).

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
  - All text modes now go through an exact `FontUtils.getFontByteDataCoolleduxForEmoji` port for plain text: scrolling (modes 2/3) via `CoolleduxStreamText.kt`, combine-canvas (modes 1, 4-13) via `CoolleduxCombineText.kt` (word-wrapped, row-centered canvas plus the APK's own byte-pattern-matching realignment pass, verified via a reconstruction-invariant test as well as a hand-traced golden vector). Only a verbatim `glyphBytes` override still uses the old placeholder shape.
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
  - **Business-hours display template is now exactly ported (2026-07-05)**: `CoolledUXUtils.getDataWithGraffitiBusinessHourCombineProgram` (hand-traced from smali - jadx's control flow for the minimalism branch is scrambled) as `ProgramContent.BusinessHours`/`CoolleduxProgramBytecode.businessHours(...)`, with a golden-vector test.
  - `CoolledUXUtils.getDataWithClockCombineProgram(...)` (`ProgramContent.Clock`, `CoolleduxProgramBytecode.clock(...)`) is **partially ported (2026-07-05)**: the outer framing (header/reserved bytes, the `is24HourShowMode`/`isSpaceShing` mode-byte truth table, `showTime`/`numHeight`/`numWidth`, and the exact field order/conditional logic for every display element - hour, hour/minute separator, minute, minute/second separator with its `showSpaceMinuteColor`-gated table reuse, seconds, am/pm, and the trailing optional am/pm glyph table) is byte-exact and smali-cross-checked (a real jadx `-m simple` control-flow bug was found and routed around: the mode-byte dispatch's `L10`/`L11` targets are rendered ~1000 lines away at the very end of the method, a bytecode-layout artifact, not a real re-entry). The digit-bitmap tables themselves are only ported for the **default (device-size-independent) case** - the real function embeds dozens of literal per-(row, column, style) override tables for hour/separator/am-pm glyphs that have not been transcribed (data-volume problem, not an algorithmic one - see `docs/APK_REVERSE_ENGINEERING_NOTES.md`). `CoolleduxProgramBytecode.clock(...)` throws a clear error for any device size that would hit one of those overrides rather than emitting the wrong glyph shapes. Golden-vector-tested: `ClockTemplateTest.kt`.
  - `getDataWithTimeCountCombineProgram`/`getDataWithScoreBoardCombineProgram` and the remaining literal digit-bitmap override tables for the clock builder still need APK source/golden-vector confirmation before being marked exact.
- Family/capability detection.
  - Prefix coverage remains stable for `CoolLEDM`, `CoolLEDU`, `CoolLEDUX`, `CoolLEDX`, `CoolLEDS`, and `iLedClock`.
  - Deeper APK capability gating by device-info/model/color-type remains pending.
- Program playlist behavior.
  - Start-header index/count/showCount handling is implemented and tested as playlist-level metadata.
  - Multi-program sequencing beyond single package construction still needs live upload validation.

## `FontUtils.getFontByteDataCoolleduxForEmoji(...)` - now exactly ported for plain text (2026-07-04)

The "source unavailable" blocker recorded in earlier passes is resolved: `FontUtils.java` was always present in the extracted APK tree, but jadx's default decompile silently failed on this method (and ~20 other LED-relevant files) and emitted an `UnsupportedOperationException("Method not decompiled")` stub instead of real code. Forcing `jadx -m simple` fixed the decompile, though its rendered *control flow* (not its instruction-level expressions) turned out to have multiple bugs of its own for this specific method and had to be cross-checked against raw smali at every branch point - see `docs/APK_REVERSE_ENGINEERING_NOTES.md` for the confirmed jadx control-flow bugs found this way.

Both mode branches are now ported and wired into the real upload path (`ProgramComposer.getDataWithTextContentProgramContent` dispatches by `mode`, falling back to a verbatim placeholder only when `glyphBytes` is explicitly supplied):

- **Stream mode** (`mode` not in {1,4,5,6,7,8,9,10,11,12,13} - chiefly scrolling text, modes 2/3): `CoolleduxStreamText.kt`. Per-glyph read/rescale/rotate/trim (including the verified double-trim-pass for 90/270 rotation), inter-glyph spacing, and the exact `[2-byte token count][4-byte running column total][per-glyph (colCount, type, bytes) chunks]` final framing.
- **Combine-canvas mode** (modes 1, 4-13): `CoolleduxCombineText.kt`. Word-wraps every glyph onto a shared canvas (`checkSegmentN`), row-centers the finished canvas (`getCenteredDataBytes`), then re-derives each output chunk by literally searching for that glyph's original bytes within the finished canvas and turning the gaps around the match into left/right padding - exactly replicating the APK's own byte-pattern-matching realignment pass rather than approximating it. Verified with both a hand-traced golden vector and a reconstruction-invariant test (concatenating every emitted chunk must reproduce the centered canvas exactly).
- Shared per-glyph shaping pipeline: `CoolleduxGlyphPipeline.kt`.
- Still out of scope for either mode: `ArabicCharDotMatrixGenerator` runtime glyph *rasterization* (RTL/CJK runtime-drawn glyphs - genuinely not JVM-portable, needs on-device Android Canvas/Typeface rendering, see below) and emoji/image tokens from `TextEmojiManagerCoolLEDUX` - both currently throw a clear error rather than silently producing wrong output. Live-device visual confirmation is also still outstanding.
- `ArabicCharDotMatrixGenerator`'s script-detection gates and `getVisualText`/`reverseIfArabic` (bidi/shaping via the same ICU4J version - `72.1` - the APK bundles) **are** ported and tested: `ScriptDetection.kt`, `ScriptVisualText.kt`. Not yet wired into the tokenizer/glyph pipeline, and the actual per-character glyph *rasterization* those scripts need is a separate, much larger piece of work requiring real Android rendering rather than a JVM-portable algorithm.
- **Emoji/image token support is now fully wired in (2026-07-04)**: `tools/apk-re/extract-coolled-apk-assets.sh` pulls in `res/drawable-{hdpi-v4,xxhdpi-v4,v21,v23}`, which is where the ~4,400 `emoji_fc_*` GIF/PNG resources `TextEmojiManagerCoolLEDUX.getDrawItemsFromBitmap` reads by name actually live (they were never in plain `res/drawable`/`-nodpi`). `EmojiGlyphEncoder.kt` ports `getImageData(...)` (monochrome bit-packing, same column-major/bytesPerColumn convention as font glyphs), the pixel side of `CoolledUXUtils.getDrawListDataFColor(...)` (RGB444 encoding), `FontUtils.rotate90Degree(List<DrawItem>, int)`/`rotate(int, List<DrawItem>, int)` (a different, list/pixel-based rotation than the byte-array one text uses, though the same clockwise formula), and `CoolledUXUtils.getDrawListDataColorAndDeleteEmptyColumn(...)` (a narrower, different blank-pixel test than `getImageData`'s - a real, documented APK inconsistency, not a bug in this port) - all against a platform-agnostic `PixelGrid`. `ShapedGlyph.kt`/`TokenGlyphShaper.kt` unify text and image tokens behind one interface: every padding/word-wrap/combine-mode-realignment decision is made using the shared monochrome representation, then applied in lockstep to whichever payload representation matches the item type (monochrome bytes for text, RGB444 bytes for images) via `ShapedGlyph.withPadding`. `CoolleduxStreamText`/`CoolleduxCombineText` now handle both branches; both have real golden-vector tests exercising a single image token (including one that needs `getCenteredDataBytes` row-centering in combine mode), not just synthetic unit coverage of the underlying primitives.

## Still approximate / blocked

1. ~~Icon/image/GIF encoding...~~ **Resolved (2026-07-05)**: `OriginalLedAssetPayloadEncoder`'s RGB444 pixel encoding was cross-checked against the newer, confirmed-exact `EmojiGlyphEncoder` primitives - same column-major order, same threshold constants (238/47/14), same byte-pair packing. No divergence found; `OriginalLedAssetPayloadEncoderTest` now pins both to the shared `rgb444TransferColorBytes` primitive so they can't silently drift apart. The **real GIF golden vector** (`OriginalLedAssetPayloadEncoderTest.realGifAssetGoldenVectorForRawAnimationWrapping`) also still stands.
2. Clock/date/weather/time-count/scoreboard-display template composition: business-hours is now exact (see above); the other four (`getDataWithClockCombineProgram`, `getDataWithDateCombineProgram`, `getDataWithTimeCountCombineProgram`, `getDataWithScoreBoardCombineProgram`) still need APK source/golden-vector confirmation - in progress. Raw `.jt` payload handling is covered, but dynamic template assembly for these four is not yet proven. Temp/humidity has its own already-ported query/parse path (see "Exact / substantially ported" above) - this item is only about the *display template* builders.
3. Scan-record parsing is deterministic for the recovered manufacturer layout, but additional real raw advertisements should be added if devices advertise other CoolLED layouts.
4. Live-device validation is still required to claim that text/icon/GIF/template payloads render exactly as the original APK.
5. Arabic/Hebrew/Hindi/Thai runtime glyph *rasterization* (`ArabicCharDotMatrixGenerator`'s Canvas/Typeface draw) is now ported - `ArabicDotMatrix.kt` (pure pixel-readback math, JVM-tested) + `AndroidGlyphRasterizer.kt` (the real on-device Canvas/Typeface implementation, with two confirmed smali-verified jadx decompile-bug fixes in the Vietnamese baseline-shift branch) behind a `GlyphRasterizer` interface - but **not yet wired into the tokenizer/glyph-shaping pipeline**: nothing calls `GlyphRasterizers.active` yet. `CoolleduxStreamText`/`CoolleduxCombineText` still only handle font-table text and image tokens.

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

Plain-text and emoji/image CoolLEDUX text generation (both mode branches) is now exact and wired end-to-end for every language *except* Arabic/Hebrew/Hindi/Thai specifically (confirmed: `MultiLangTextEmojiParser` only special-cases those four - zh-CN, vi, and everything else already use the tokenizer this repo ports). CoolLEDU's text pipeline (real, verified, for showHeight in {16,32}/rescale/mirror/text-only) turned out to cover CoolLEDM and CoolLEDUD ("iLedBike") too - confirmed by direct/side-by-side reading, not assumed from naming - so all three families now share it. **Rescale and mirror are now ported (2026-07-05)**: `CoolleduGlyphPipeline`'s rescale reuses the already-verified `FontGlyphRescale.transfer` (the same 15 `transfer<N>FontTo<M>` functions CoolLEDUX's rescale uses), and `isMirror` is `CoolleduxMirror`'s port of `FontUtils.mirror`/`splitBytes`/`addAllSpiltedBytes` (one more confirmed smali-verified jadx control-flow bug found and fixed along the way: a dropped loop back-edge made `mirror`'s stride-2 branch look like a no-loop copy). Still unported for CoolLEDU: its emoji-token path (a completely different reverse-indexed-list lookup, not the same mechanism CoolLEDUX's tokens use). Also still outside CoolLEDU/M/UD's scope: `DeviceFamily.ILEDCLOCK` (its own separate, ~1000-line, not-yet-ported text function) still falls back to the documented placeholder. Remaining highest-value gaps: wiring the now-ported Arabic/Hebrew/Hindi/Thai glyph *rasterization* (`ArabicDotMatrix.kt`/`AndroidGlyphRasterizer.kt` - ported but not yet called from the tokenizer/glyph-shaping pipeline) into `CoolleduxStreamText`/`CoolleduxCombineText`, CoolLEDU's own emoji-token path, iLedClock's own text pipeline, and the four still-unported CoolLEDUX display templates (clock/date-weather/time-count/scoreboard - business-hours is done) - then live-device visual validation across all of it.

## Correctness audit (2026-07-05)

A full-repo correctness re-review (three parallel passes: command/control protocol, BLE transport, core frame/transfer protocol) found and fixed a substantial batch of real bugs that had been sitting undetected because nothing had specifically re-checked earlier work against APK ground truth since it was written. Every fix below was independently re-verified against `reverse/jadx/sources`/smali before being applied, not taken on trust from the reviewing pass. Fixed:

- **Critical**: `ProgramComposer.compose` computed the start header's CRC/length over the *compressed* body for every family except CoolLEDUX (should always be the uncompressed body - confirmed universal across all five families' `getStartDataForProgram`). Would have failed real hardware's CRC/length check on every non-CoolLEDUX transfer.
- `LedScanRecordParser` used an invented manufacturer-data extraction step with made-up offsets; real `DeviceManager.getDeviceId/Row/Column/ColorTye` index directly into the raw advertisement bytes (id@9-10, row@17, column@18-19, colorType@20).
- `AndroidBleTransport`: write-type priority was inverted vs `BleConnector.writeCharacteristic`; GATT operations (descriptor write, MTU request) weren't serialized; MTU 247 was requested for every device instead of the real name allowlist; `BluetoothGatt.close()` was never called on unexpected disconnect (GATT client pool leak); a stale write-ack callback could complete the wrong write's deferred; scan results updated non-atomically.
- `setColorMode` sent a bare mode-index byte; the real protocol sends a full literal RGB444 color table per style (31 styles, ported from `ILedClockUtils.setColorMode`'s dispatch and its 20 named table constants).
- `setVolume` used the wrong opcode (`0x1E/0x03` is an unrelated command; real is `0x1E/0x06`).
- `resetScoreboard` encoded 2-byte score fields as 1 byte (silently truncating/misframing) and was missing two 1-byte set-count fields entirely.
- `parseDeviceInfo` was an entirely fabricated model/firmware/matrix-size tuple (that layout actually belongs to the scan record, not the 0x1F response). `parseTimerSwitches` read 1 byte/item instead of the real 6-byte stride. `parseReminder` read `year` as 2 bytes instead of 1, shifting every later field.
- `setNightMode`/`parseNightMode` were missing 5 of 10 real fields and had no query builder at all.
- `Capabilities.kt` mis-assigned drive-state support to plain CoolLEDU (a real, separate family - `Cool_LED_UD`/"iLedBike" - had no `DeviceFamily` entry at all) and volume support to CoolLEDUX (which has zero volume methods in the APK).
- `TransferStateMachine`/`AppViewModel.handleProgramTransferAck` both treated a chunk NACK as a retry trigger; the real protocol treats it as terminal (only a no-ack-at-all timeout legitimately retries).
- `RememberedDeviceStore` was in-memory only and its output was never actually consumed anywhere in the UI. Now backed by real Android SharedPreferences persistence (matching how the original APK itself persists device metadata) and surfaced as a "Recently connected" quick-reconnect list.
- `buildOtaStartHeader` had the same compressed-vs-uncompressed CRC/length bug as the program-start header fix above (not yet exploitable - nothing calls it, OTA upload isn't wired into the UI - but latent until it was).

**Not yet fixed, tracked for follow-up**: `TransferStateMachine` is missing the APK's two-tier retry/restart-from-zero structure (robustness gap, not a correctness bug); non-CoolLEDUX/non-CoolLEDU families' text/asset content encoding (`ProgramContent.encodeContent`'s else-branches), CoolLEDU's own emoji-token path (rescale and mirror are now ported, see above), and iLedClock's own ~1000-line text function remain an explicitly-documented unverified placeholder, not a quick fix - each needs the same reverse-engineering rigor CoolLEDUX (and now part of CoolLEDU) got.

## Build/test environment (2026-07-04)

`./gradlew testDebugUnitTest` now builds and passes (69/69) in this container. It required a local (gitignored) `local.properties` pointing at a pre-existing Android SDK checkout, and an `aapt2` fix for aarch64 (no native `linux-aarch64` aapt2 exists upstream) - see `docs/TESTING.md` for the exact fix. This does not change LED protocol behavior; it just means CI-equivalent checks are actually runnable here now instead of failing before any test code executes.
