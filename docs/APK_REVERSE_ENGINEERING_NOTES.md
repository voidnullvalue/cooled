# APK reverse-engineering notes

Source inspected: `base_apk_protocol_sources.zip`, especially `com.jtkj.led1248.light.utils.CoolledUXUtils.java` and `com.jtkj.led1248.light.device.DeviceManager.java`.

## CoolLEDUX program generation recovered in this pass

The original APK builds CoolLEDUX display uploads in these stages:

1. `getDataWithTextContentProgramContent(...)`
   - Creates a length-prefixed content block.
   - Inner payload starts with content type `0x01`, seven reserved zero bytes, `layerType`, `startColumn`, `startRow`, `showWidth`, `showHeight`, `mode`, `speed`, `stayTime`, `moveSpace`, then `FontUtils.getFontByteDataCoolleduxForEmoji(...)` bytes.
   - The four-byte prefix is `inner.size + 4` in big-endian order.
2. `getDataForCombineProgram(...)`
   - Dispatches by combine-program type.
   - Text combine programs use type `3` and append text custom color, text auto color, normal text content, then optional frame content. This app currently ports the normal text-content branch.
3. `getDataForProgram(...)`
   - Concatenates encoded combine-program blocks.
4. `getDataWithProgram(...)`
   - Prepends eight zero bytes, one-byte content count, one zero byte, then `getDataForProgram(...)` bytes.
5. `getDataResult(...)`
   - Compresses `getDataWithProgram(...)` through the APK LZSS path.
   - Builds start headers with index/count/showCount and splits compressed bytes into transfer chunks.

## Start-header / playlist behavior

`DeviceManager` stores `mCoolleduxProgramList`, `mCoolleduxProgramIndex`, and `mCoolleduxProgramCount`; after a successful program ACK it advances the index and starts the next program until `mCoolleduxProgramIndex == mCoolleduxProgramCount - 1`. This confirms that index/count/showCount are playlist-level fields rather than fields inside the display-content body.

## Remaining unproven areas

- GIF/image/icon functions are visible in `CoolledUXUtils` and show length-prefixed raw/encrypted file payload use for some animation branches, but asset-type mapping from app assets to those builders still needs more source/assets before claiming exact parity.
- Scan-record parsing offsets were not recoverable from the protocol-only ZIP in this pass.

## jadx decompile gap and fix (2026-07-04)

A full APK (`coolled.apk`, package `com.jtkj.led1248`, base split, `compileSdkVersion=36`) was placed in the repo root and decompiled locally into `reverse/` (gitignored, local-only artifacts). The `apktool`/`jadx` output previously checked into this container had **failed to decompile several LED-relevant methods**, including the single most important one: `FontUtils.getFontByteDataCoolleduxForEmoji(...)`. jadx's default `auto` mode emitted a stub body:

```java
throw new UnsupportedOperationException("Method not decompiled: ...");
```

for that method and ~20 other files under `com/jtkj/led1248/` (`CoolledUXUtils.java`, `DeviceManager.java`, `TextEmojiManagerCoolLEDUX.java`, `ArabicCharDotMatrixGenerator.java`, all the `LightXXXXUtils.java` family classes, etc). This is *why* earlier passes recorded `FontUtils.java` as unavailable/blocked - the file existed but its most important method didn't decompile.

Fix: `jadx --single-class <FQCN> -m simple` (forcing the "simple" linear/goto decompilation mode instead of the default `auto`/`restructure` CFG-reconstruction mode) succeeds where `auto` gives up. A full re-decompile with `-m simple -r -j 4` across the whole APK produces **zero** `Method not decompiled` stubs anywhere under `com/jtkj/led1248/`. `reverse/jadx/sources` in this container has been replaced with that output.

**Caveat discovered while reading the `simple`-mode output**: jadx's `simple` mode pseudocode reuses label text (e.g. two unrelated blocks both rendered as `L28:`/`L29:`) for methods with very large numbers of basic blocks - these are visually identical labels for *different* bytecode offsets, not a real backward jump. Do not infer control flow (loops, re-entry) from label name equality in `simple`-mode output; always cross-check suspected backward branches against the raw smali (`reverse/apktool/smali_classes3/.../FontUtils.smali`), where `goto` targets are unambiguous per-method-unique labels. (Concretely: reading the pseudocode naively suggested `getFontByteDataCoolleduxForEmoji` re-enters its own body in a second pass when `mode` is 2/3; grepping the smali for backward `goto :goto_1`/`goto :goto_2` disproved that - there is no re-entry, it's a label collision artifact.)

## `FontUtils.getFontByteDataCoolleduxForEmoji` structure (recovered, not yet fully ported)

Signature: `List<String> getFontByteDataCoolleduxForEmoji(DeviceManager.CoolleduxTextContentProgramContent content)` - returns a list of two-hex-char byte strings (like the rest of this codebase's recovered builders), not raw bytes directly.

Traced from `reverse/apktool/smali_classes3/com/jtkj/led1248/light/utils/FontUtils.smali` (method starts at smali line 42801) via `jadx --single-class ... -m simple`:

1. `content.text` is bidi/shaped via `ArabicCharDotMatrixGenerator.getVisualText(languageCode, text)`, then tokenized into `TextEmojiManager.TextEmoji32Item` records by `TextEmojiManagerCoolLEDUX.getTextEmojiItems(languageCode, visualText, "emoji_fc_\\d{2}_\\d{3}|emoji_fc_\\d{3}")` (splits text vs. embedded emoji image tokens). Every item then gets `isBold`/`rotate`/`textSize` copied from the content object (uniform formatting, no per-glyph override from the token stream itself at this stage).
2. `mode` selects one of two rendering strategies for the **entire** content (single pass, not per-glyph):
   - `mode in {1,4,5,6,7,8,9,10,11,12,13}` -> **combine/canvas mode**: lay every glyph/image onto one shared canvas byte buffer that word-wraps at `showWidth` columns via `checkSegment8/12/14/16/24/32(canvas, glyph, showWidth, textSpacing)` (ported: `FontCanvasWordWrap.kt` - **not** a dedup function as an earlier draft of this note guessed; it inserts either `textSpacing` blank columns between glyphs that share a row, or enough blank columns to skip to the next row boundary when the next glyph would overflow the current row), then walks the assembled canvas once at the end (per-height-specific loop keyed on `showHeight` 8/12/14/16/20/24/32) building `LightUtils.getHexListStringForWithOneByte(columnCount)` + `getHexListStringForWithOneByte(itemType)` + payload chunks.
   - anything else (chiefly `mode` 2/3, i.e. scrolling text) -> **stream mode**: each glyph is rendered independently (no shared canvas/dedup) and immediately appended as `[1-byte length/height-divisor][1-byte itemType][payload bytes]` chunks.
3. Per-glyph rendering (both modes call into the same glyph-rasterization step first):
   - Text glyphs: `readFontData(charCode, textSize)` reads the raw asset-backed glyph (or `ArabicCharDotMatrixGenerator.readFontDataFromDraw(...)` for RTL/Vietnamese/unsupported-charset text, which draws glyphs at runtime instead of reading a font table), then a chain of `transfer<N>FontTo<M>(bytes)` helpers upscales/downscales the raw glyph to the content's actual `showHeight` (e.g. a 12px glyph gets `transfer12FontTo32` when `showHeight == 32`).
   - Image/emoji glyphs: `TextEmojiManagerCoolLEDUX.getDrawItemsFromBitmap(imageNameNN, srcSize, showHeight)` + `getImageData(...)` / `CoolledUXUtils.getDrawListDataFColor(...)`.
   - `textRotate` (0/90/180/270) is then applied via `FontUtils.rotate(angle, bytes, showHeight)` (ported: `app/src/main/java/com/cooled/core/protocol/FontBitmapRotation.kt`), which repeatedly calls `rotate90Degree(byte[], int)` - a standard clockwise 90-degree transpose of a column-major, MSB-first-packed square bitmap. **Verified via direct smali trace (not just jadx pseudocode) that trimming happens twice when rotate is 90 or 270**: `deleteEmptyColumnFor<showHeight>(rotated, textSize)` runs unconditionally right after rotation (keyed purely by `showHeight`, regardless of rotation angle), and *then*, only when the angle is 90 or 270, the result additionally goes through `processBytesCentered<showHeight>(...)` followed by a second `deleteEmptyColumnFor<showHeight>(..., textSize)` call. This is a real double-processing in the APK, not a decompile artifact - both passes are ported (`FontColumnTrimming`, `FontCentering.processBytesCentered`) and must both run for exact parity.
4. **Corrected (2026-07-04) after smali cross-check - both modes share the same tail shape**: `[2-byte count][4-byte running total column count][accumulated hex chunks]`. Only the *meaning* of the 2-byte count differs: combine mode uses the count of non-space processed items (`r82.size()`), stream mode uses the count of *all* original tokens including spaces (`r12.size()`). An earlier draft of this note, based on naively reading jadx `-m simple` pseudocode, wrongly concluded stream mode omits the 4-byte running total entirely - that pseudocode had ANOTHER label-collision artifact right at the method's tail (making it look like the method re-entered itself in a second pass with `mode`-independent framing). Raw smali has no such re-entry and both branches converge to one shared tail (`:goto_4f` in `reverse/apktool/.../FontUtils.smali`). This is now the third confirmed instance of jadx `-m simple` control-flow rendering bugs found while tracing this one method - do not trust its rendered control flow for this method without a smali cross-check at every branch, only its instruction-level expressions.

### What's ported vs. not (as of this note)

- Ported and tested:
  - `rotate`/`rotate90Degree` bitmap-rotation primitives (`FontBitmapRotation.kt`, `FontBitmapRotationTest.kt`).
  - `deleteEmptyColumnFor{8,12,14,16,20,24,32}(bytes[, textSize])`, generalized into one function parameterized by `bytesPerColumn = ceil(textSize/8)` since all seven size variants are identical modulo that stride (verified against every size's smali/jadx-decompiled source, including the blank-glyph fallback width of exactly `textSize/2` columns in every variant): `FontColumnTrimming.kt`, `FontColumnTrimmingTest.kt`.
  - `checkSegment{8,12,14,16,24,32}(canvas, glyph, showWidth, textSpacing)` word-wrap layout and `addEmptyColumnForData*ToThe{Left,Right}(bytes[, count])`, generalized the same way: `FontCanvasWordWrap.kt`, `FontCanvasWordWrapTest.kt`.
  - `processBytesCenteredN` (per-glyph centering used only for the 90/270-rotated text case) and `getCenteredDataBytes`/`splitArray`/`dealDataWithCenteredN`/`dealSegmentResultN` (row-centering of the assembled combine-mode canvas), generalized into `FontCentering.kt` (`FontCenteringTest.kt`). These two centering mechanisms use *opposite* rounding bias for an odd leftover column (per-glyph square-centering puts it on the right; row-centering of a wrapped canvas's trailing partial row puts it on the left) - verified against multiple size variants, not assumed. `processBytesCenteredN` also has a genuine APK copy/paste quirk preserved here: the 12px and 20px variants trim using the 14px and 24px column-trim constants (same byte stride, different blank-fallback width) rather than their own.
  - `transfer<N>FontTo<M>` (glyph rescaling when a font asset's native size doesn't match `showHeight`), generalized into `FontGlyphRescale.kt` (`FontGlyphRescaleTest.kt`, with vectors cross-checked in an independent Python re-implementation): widen the column byte stride if needed (zero bytes appended at the bottom of each column), shift every column's bit value right by `(toSize-fromSize)/2` bits (multi-byte carry between bytes in the same column), then pad `(toSize-fromSize)/2` blank columns on each side. Verified against all four distinct byte-stride transitions the 15 APK variants cover (2->2, 2->3, 3->3, 3->4 bytes/column), not just one example.
  - `TextEmojiManagerCoolLEDUX.getTextEmojiItems(text, pattern)` (the default/fallback tokenizer that `MultiLangTextEmojiParser.getTextEmojiItemsByLanguage(...)` delegates to for any language without its own special branch): `TextEmojiTokenizer.kt`. **Another jadx `-m simple` control-flow rendering bug found while tracing this one**: the pseudocode is missing the backward branch that closes the method's main loop, making it look like it only ever processes index 0 and returns. The raw smali has an unambiguous `goto/16 :goto_0`. This is a different failure mode than the label-reuse bug found in `getFontByteDataCoolleduxForEmoji` (a dropped edge instead of a relabeled one) - reinforces that `-m simple` output must be cross-checked against smali for backward branches on any method of this shape, not just very large ones.
- **Not yet ported**: `ArabicCharDotMatrixGenerator` bidi shaping and runtime glyph drawing, the per-language dispatch in `MultiLangTextEmojiParser.getTextEmojiItemsByLanguage(...)` (zh-CN/vi/ar and others may tokenize differently than the default path just ported), and the mode-dependent list-assembly wrapper (`getFontByteDataCoolleduxForEmoji` itself) that ties all of the above together. Every byte-shaping primitive plus the default-language tokenizer are now ported and tested: `FontBitmapRotation`, `FontColumnTrimming`, `FontCanvasWordWrap`, `FontCentering`, `FontGlyphRescale`, `TextEmojiTokenizer`.

### `FontUtils.readFontData(char, int)` - the actual font-file read (not yet wired up)

Traced but not yet ported into this repo's `CoolleduxFontSource`. Two findings worth recording:

- **File layout is directly codepoint-indexed, not a lookup table**: `glyphSize = ceil(textSize/8) * textSize` bytes, and the read offset is `charCode * glyphSize` into a `RandomAccessFile` opened by `startReadFontData(textSize, bold)`/`copyFontLibFileToCache(...)`. This matches the already-recovered asset sizes in `docs/APK_PORT_STATUS.md` exactly (e.g. `32_32_large` = 65,536 glyphs * 128 bytes = a full-BMP-range direct index, not a sparse map).
- **Missing/unreadable glyph data returns a zero-filled byte array, not an error.** The APK wraps the actual file read in a try/catch that falls back to a same-sized zero array (`CoolLED.reportError(e)` is called for logging/crash-reporting, but the function still returns successfully with blank bytes) whenever `mFontLibFileStream` is null or the read throws. **This repo previously disagreed and has been corrected to match**: `ProgramComposer.getFontByteDataCoolleduxForEmoji`/`CoolleduxFontByteBuilder.renderText` used to throw `IllegalStateException` on a missing glyph; both now return a zero-filled glyph of the correct size instead, per the "APK behavior is authoritative" rule. `CoolleduxProgramBytecodeParityTest.fontUtilsReturnsZeroFilledGlyphForMissingFontsLikeTheApkDoesButStillRejectsEmoji` covers this. Non-BMP/emoji text is a separate, still-unimplemented feature (the APK's emoji subsystem draws bitmap images via `TextEmojiManagerCoolLEDUX`, not font-table glyphs) and correctly still throws - that's a missing feature, not missing data.
- The current `ProgramComposer.getFontByteDataCoolleduxForEmoji` in `app/src/main/java/com/cooled/core/protocol/ProgramContent.kt` is a **placeholder with a different, non-APK byte layout** (`[2-byte codepoint count][2-byte total width][per-glyph 2-byte widths][raw glyph bytes]`, versus the APK's mode-dependent `[2-byte count][4-byte total]` or `[2-byte count]`-only framing described above). It must not be described as byte-exact; replacing it is the next concrete step and should be done together with a live-device byte capture or a `HexUtil`/`LightUtils` golden vector, since getting the framing subtly wrong will not throw at build/test time - it will just show garbage on the LED matrix.
