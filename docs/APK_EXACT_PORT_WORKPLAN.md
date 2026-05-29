# Exact APK port workplan

This branch is the stop-guessing branch. The goal is to port from the original APK behavior, not from live-device trial-and-error.

## Source APK facts already established

Original package:

- `com.jtkj.led1248`
- version `2.7.2 / 108`
- BLE service `0000fff0-0000-1000-8000-00805f9b34fb`
- BLE characteristic `0000fff1-0000-1000-8000-00805f9b34fb`
- CCCD `00002902-0000-1000-8000-00805f9b34fb`

Your hardware identifies as `CoolLEDUX`, so the implementation priority is CoolLEDUX first. Other app families can be added after the UX path is stable.

## Current branch baseline

Working / validated from device testing:

- Android BLE scan/connect/discover/notify/write reaches `READY`.
- Core commands work: power, brightness, and several clock-class controls produce valid responses.
- Program transfer mechanics are mostly recovered:
  - start opcode `0x02`
  - chunk opcode `0x03`
  - start CRC/length use uncompressed program data for CoolLEDUX
  - streamed chunks use compressed data
  - chunk XOR excludes the leading chunk message type byte
  - start and chunk ACKs are parsed
  - chunk transfer can complete

Not yet finished:

- Exact CoolLEDUX text-program payload body.
- Exact `FontUtils.getFontByteDataCoolleduxForEmoji(...)` behavior.
- Exact 32px text asset path.
- Exact color/effect metadata before glyph data.
- Exact advertisement metadata extraction for rows/columns/color/device-id.

## APK code path to port exactly

For CoolLEDUX text upload, the target chain is:

```text
DeviceManager$CoolleduxTextContentProgramContent
  -> CoolledUXUtils.getDataWithTextContentProgramContent(...)
  -> FontUtils.getFontByteDataCoolleduxForEmoji(...)
  -> CoolledUXUtils.getDataWithTextCombineProgram(...)
  -> CoolledUXUtils.getDataForCombineProgram(...)
  -> CoolledUXUtils.getDataForProgram(...)
  -> CoolledUXUtils.getDataResult(...)
  -> DeviceManager.sendCoolleduxStartSendProgramData(...)
```

The default CoolLEDUX text object observed from the APK is not an 8px text object. It is a 32px auto-sized/bold text object:

```text
layerType      = 1
startRow       = 0
startColumn    = 0
showHeight     = 32
showWidth      = 128
mode           = 2
speed          = 255
stayTime       = 3
isTextBold     = true
textRotate     = 0
isAutoTextSize = true
textSize       = 32
textSpacing    = 1
```

## Binary assets required later

Use the helper added in this branch:

```bash
tools/apk-re/extract-coolled-apk-assets.sh /path/to/base.apk
```

Expected output:

```text
app/src/main/assets/coolled-original/fonts/8_small
app/src/main/assets/coolled-original/fonts/32_32_large
app/src/main/assets/coolled-original/fonts/32_32_small
```

Expected sizes:

```text
8_small       = 1,024 bytes       = 128 glyphs * 8 bytes
32_32_large   = 8,388,608 bytes   = 65,536 glyphs * 128 bytes
32_32_small   = 8,388,608 bytes   = 65,536 glyphs * 128 bytes
```

## Port sequence

### 1. Freeze transport and command basics

Do not continue mutating the working BLE transport unless logs prove transport failure. Current transport is already good enough to prove app-layer protocol changes.

### 2. Port exact APK command builders

This branch starts that by aligning:

- sync time: `09 yy mm dd weekday hh mm ss`
- timer switches: `0A count [enable hour minute weekdayMask onOff 00]...`
- drive state query/set: `1C 02` and `1C 01 state`
- OTA version query: `FD`
- core setters: `04`, `05`, `06`, `0C`, `13`, `1E`

### 3. Port advertisement parsing

Implement the raw scan-record methods equivalent to:

- `getDeviceId(byte[])`
- `getDeviceRow(byte[])`
- `getDeviceColumn(byte[])`
- `getDeviceColorTye(byte[])`

Then use those values instead of hardcoding text dimensions.

### 4. Port exact text payload

Implement these models first:

- `CoolleduxTextProgramContent`
- `CoolleduxCombineProgram`
- `CoolleduxProgram`

Then port byte builders in this order:

1. `getDataWithTextContentProgramContent(...)`
2. `getDataWithTextCombineProgram(...)`
3. `getDataForCombineProgram(...)`
4. `getDataForProgram(...)`
5. `getDataResult(...)`

The current temporary `ProgramContent.Text` path must be replaced, not tweaked further.

### 5. Port font reader after assets land

Implement exact equivalent of:

- `FontUtils.readUnicode3232(...)`
- `FontUtils.readUnicode3232Bold(...)`
- `FontUtils.getFontByteDataCoolleduxForEmoji(...)`

The loader should read 128 bytes per 32x32 glyph:

```text
offset = codePoint * 128
length = 128
```

Which asset to select depends on bold/small/large behavior recovered from `FontUtils`.

### 6. Add golden-vector tests

Before any more live-device testing, add tests that generate stable byte payloads for:

- `HELLO`
- one timer switch
- one password check with deterministic random byte
- one drive-state query
- one transfer start header
- one data chunk

This prevents regressions like the earlier `1A` start opcode and fake pixel matrix changes.

## Rule for future changes

No future CoolLEDUX text changes should be merged unless they are tied to one of:

- a decompiled APK method name and recovered byte layout
- an extracted APK asset read path
- a reproducible golden-vector test
- a live BLE log proving expected ACK/response behavior
