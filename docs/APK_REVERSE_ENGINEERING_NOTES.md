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
   - `textRotate` (0/90/180/270) is then applied via `FontUtils.rotate(angle, bytes, showHeight)` (ported: `app/src/main/java/com/cooled/core/protocol/FontBitmapRotation.kt`), which repeatedly calls `rotate90Degree(byte[], int)` - a standard clockwise 90-degree transpose of a column-major, MSB-first-packed square bitmap.
4. Final list layout differs by mode: combine mode prefixes `[2-byte glyph count][4-byte total column/row count]` before the payload chunks; stream mode prefixes `[2-byte original token count]` only (no running total prefix - the per-glyph headers inside the stream already carry per-glyph lengths).

### What's ported vs. not (as of this note)

- Ported and tested:
  - `rotate`/`rotate90Degree` bitmap-rotation primitives (`FontBitmapRotation.kt`, `FontBitmapRotationTest.kt`).
  - `deleteEmptyColumnFor{8,12,14,16,20,24,32}(bytes[, textSize])`, generalized into one function parameterized by `bytesPerColumn = ceil(textSize/8)` since all seven size variants are identical modulo that stride (verified against every size's smali/jadx-decompiled source, including the blank-glyph fallback width of exactly `textSize/2` columns in every variant): `FontColumnTrimming.kt`, `FontColumnTrimmingTest.kt`.
  - `checkSegment{8,12,14,16,24,32}(canvas, glyph, showWidth, textSpacing)` word-wrap layout and `addEmptyColumnForData*ToThe{Left,Right}(bytes[, count])`, generalized the same way: `FontCanvasWordWrap.kt`, `FontCanvasWordWrapTest.kt`.
- **Not yet ported**: `processBytesCenteredN` (used only for the 90/270-rotated case, per the `getFontByteDataCoolleduxForEmoji` trace above), `transfer<N>FontTo<M>` (glyph up/downscaling when a font asset's native size doesn't match `showHeight`), `getCenteredDataBytes` (initial canvas allocation/centering), `ArabicCharDotMatrixGenerator` bidi shaping and runtime glyph drawing, `TextEmojiManagerCoolLEDUX` tokenization/emoji image handling, and the mode-dependent list-assembly wrapper (`getFontByteDataCoolleduxForEmoji` itself) that ties all of the above together.
- The current `ProgramComposer.getFontByteDataCoolleduxForEmoji` in `app/src/main/java/com/cooled/core/protocol/ProgramContent.kt` is a **placeholder with a different, non-APK byte layout** (`[2-byte codepoint count][2-byte total width][per-glyph 2-byte widths][raw glyph bytes]`, versus the APK's mode-dependent `[2-byte count][4-byte total]` or `[2-byte count]`-only framing described above). It must not be described as byte-exact; replacing it is the next concrete step and should be done together with a live-device byte capture or a `HexUtil`/`LightUtils` golden vector, since getting the framing subtly wrong will not throw at build/test time - it will just show garbage on the LED matrix.
