# CoolLED / CoolLEDU* / iLedClock BLE Clone Specification (from `base.apk`)

## Scope

This document is a static reverse-engineering specification derived from the uploaded APK (`com.jtkj.led1248`, version 2.7.2 / 108).

It is intended to be detailed enough to reimplement the BLE transport and the primary application-layer protocol used by these device families:

- `CoolLEDM`
- `CoolLEDU`
- `CoolLEDUX`
- `iLedClock`
- related name families discovered by the app: `CoolLED`, `CoolLED536`, `CoolLEDX`, `CoolLEDA`, `CoolLEDS`, `iLedBike`, `iDevilEyes`, `iLedHat`, `iLedHatC`, `iLedOpen`, `iLedCar`

This is a static analysis result. It does **not** claim live-air validation of every opcode, but it is grounded in decompiled packet builders, parsers, connection logic, CRC code, and compression code present inside the APK.

---

## 1. BLE transport and discovery

### 1.1 Package and core classes

- Package: `com.jtkj.led1248`
- Main BLE orchestration class: `com.jtkj.led1248.light.device.DeviceManager`
- Clock-specific manager: `com.jtkj.led1248.light.device.ILedClockManager`
- BLE library: `com.jtkj.library.fastble.*`

### 1.2 BLE UUIDs

The app hardcodes the following GATT UUIDs:

- Service: `0000fff0-0000-1000-8000-00805f9b34fb`
- Characteristic for write/notify: `0000fff1-0000-1000-8000-00805f9b34fb`
- CCCD used by Android for notification enable: `00002902-0000-1000-8000-00805f9b34fb`

### 1.3 Scan rules

`DeviceManager.initBleManager()` configures scan filtering with:

- service UUID filter: `FFF0`
- exact/allowed name set:
  - `CoolLED536`
  - `CoolLED`
  - `CoolLEDX`
  - `CoolLEDA`
  - `CoolLEDS`
  - `CoolLEDM`
  - `CoolLEDU`
  - `iLedBike`
  - `CoolLEDUX`
  - `iDevilEyes`
  - `iLedHat`
  - `iLedHatC`
  - `iLedOpen`
  - `iLedCar`
  - `iLedClock`
- `autoConnect = false`
- scan timeout = `3000 ms`

### 1.4 Connection policy

BLE library initialization sets:

- reconnect count: `5`
- reconnect interval: `1000 ms`
- connect timeout: `5000 ms`
- operation timeout: `5000 ms`

After connection, the app delays approximately `500 ms` before enabling notifications.

### 1.5 Notification setup and retries

Notifications are enabled on characteristic `FFF1`.

Behavior:

- if notify setup succeeds, incoming bytes are delivered to the response parser
- if notify setup fails, the app retries up to **3** times
- if notification setup still fails after that, the device is disconnected

### 1.6 MTU negotiation and write split size

The app requests **MTU 247** for recognized modern families:

- `CoolLEDM`
- `CoolLEDU`
- `CoolLEDUX`
- `iDevilEyes`
- `iLedHat`
- `iLedHatC`
- `iLedOpen`
- `iLedBike`
- `iLedClock`
- `iLedCar`

On MTU success:

- write-split chunk size is set to `180 bytes`

On MTU failure or on unrecognized families:

- write-split chunk size falls back to `20 bytes`

### 1.7 Inter-packet timing and retries

DeviceManager constants show these protocol expectations:

- `MAX_RETRY_TIMES_FOR_ALL = 3`
- `MAX_RETRY_TIMES_FOR_PACKAGE = 3`
- `MAX_TIMES_TO_SET_BLE_NOTIFY = 3`
- `MESSAGE_SEND_OVERTIME = 5000 ms`
- `INTERNAL_BETWEEN_TWO_PACKAGE = 15 ms`

Interpretation: when streaming multi-packet content, the app spaces packets by about `15 ms`, expects acknowledgments/timeouts within `5 s`, and retries failed transfers or failed packet phases up to 3 times.

---

## 2. Outer frame format

All examined device-family helpers (`CoolledMUtils`, `CoolledUUtils`, `CoolledUXUtils`, `ILedClockUtils`) use the same outer framing.

### 2.1 Structure

Transmitted BLE application frames are:

```text
01 <escaped(len_hi len_lo payload...)> 03
```

Where:

- `01` = start-of-frame sentinel
- `03` = end-of-frame sentinel
- `len_hi len_lo` = **2-byte big-endian payload length**
- `payload` = protocol command body
- `escaped(...)` = byte-stuffed version of the inner bytes `len_hi len_lo payload...`

### 2.2 Length field

The length field is the length of the **payload only**, not including:

- start sentinel `01`
- end sentinel `03`
- the 2-byte length field itself

### 2.3 Escaping / byte stuffing

The app escapes inner bytes before transmission.

If any byte in `len_hi len_lo payload...` equals `01`, `02`, or `03`, it is replaced by two bytes:

- `01` -> `02 05`
- `02` -> `02 06`
- `03` -> `02 07`

General rule:

```text
escaped = 02, (original_byte XOR 04)
```

Only the inner bytes are escaped. The outer sentinel bytes remain literal `01` and `03`.

### 2.4 Unescaping and payload recovery

On receive, the app:

1. strips leading `01` and trailing `03`
2. reverses stuffing sequences `02 xx` back to the original byte by XORing the second byte with `04`
3. removes the first two bytes (the big-endian length)
4. treats the remaining bytes as the protocol payload

---

## 3. Shared integer encoding rules

Across the helpers, integer-to-byte formatting is consistent.

### 3.1 Endianness

All observed multibyte integers are **big-endian**.

### 3.2 Helpers inferred from code

- one-byte integer: `00`..`ff`
- two-byte integer: `high low`
- four-byte integer: `b3 b2 b1 b0`

Examples:

- decimal `5` as 1 byte -> `05`
- decimal `300` as 2 bytes -> `01 2c`
- decimal `1024` as 4 bytes -> `00 00 04 00`

---

## 4. Checksums and CRC

There are **two** integrity mechanisms in the protocol.

### 4.1 Simple XOR checksum for chunk packets and password payloads

For several packet bodies, the app computes a one-byte XOR checksum:

```text
checksum = byte0 XOR byte1 XOR ... XOR byteN
```

This is used at least in:

- password-check and password-set payload tails
- streamed data chunk bodies created by `getDataPacket(...)`

### 4.2 CRC32-like algorithm for transfer start headers

The app includes an actual CRC implementation in inner classes such as:

- `CoolledUXUtils$CrcCode`
- `CoolledUUtils$CrcCode`
- `CoolledMUtils$CrcCode`
- `ILedClockUtils$CrcCode`

The algorithm used by `getCrcCode(...)` is **not** the usual reflected Ethernet CRC-32 implementation, even though it uses the same polynomial.

#### 4.2.1 Polynomial

- polynomial: `0x04C11DB7`

#### 4.2.2 Initial value

- initial CRC value: `0xFFFFFFFF`

#### 4.2.3 Bit order

- processed **MSB-first** in a custom bit loop

#### 4.2.4 Decompiled behavior

Equivalent pseudocode:

```text
crc = 0xFFFFFFFF
for each byte b in input:
    mask = 0x80000000
    repeat 32 times:
        if (crc & 0x80000000) != 0:
            crc = (crc << 1) ^ 0x04C11DB7
        else:
            crc = (crc << 1)

        if ((b & 0xFF) & mask) != 0:
            crc = crc ^ 0x04C11DB7

        mask >>= 1

return crc & 0xFFFFFFFF
```

The result is then encoded as **4-byte big-endian**.

#### 4.2.5 Important caution

Do **not** assume a stock library call like `zlib.crc32()` or a standard reflected CRC-32 implementation will match. The APK code uses its own bitwise routine.

---

## 5. Compression used before streaming data

The APK implements LZSS-style compression in inner classes such as:

- `CoolledUXUtils$LzssCompress`
- `CoolledUUtils$LzssCompress`
- `CoolledMUtils$LzssCompress`
- `ILedClockUtils$LzssCompress`

### 5.1 LZSS parameters

From decompiled code:

- window size `N = 512`
- lookahead `F = 18`
- threshold `THRESHOLD = 2`
- null tree marker `NIL = 512`

### 5.2 Ring buffer initialization

The ring buffer is initialized with zero bytes for the region before the initial input load.

Specifically:

- `N - F = 494` bytes of zero-preload behavior appear in the implementation

### 5.3 Token format

The compressor emits groups controlled by a single flags byte.

For each group of up to 8 tokens:

- each token is either a literal byte or a match reference
- the flags byte indicates which token type applies

### 5.4 Match encoding

For a back-reference token, the emitted representation is 2 bytes:

- byte 1: `match_position & 0xFF`
- byte 2 high nibble: `(match_position >> 4) & 0xF0`
- byte 2 low nibble: `match_length - 3`

Thus:

- minimum encoded match length = `3`
- maximum encoded match length = `18`

### 5.5 Literal rule

A literal is emitted when:

- `match_length <= 2`

That matches the threshold constant.

---

## 6. Streaming/chunked transfer format

Program uploads and OTA updates are not sent as one giant payload. The app builds:

1. a **start header** packet
2. one or more **data chunk** packets

### 6.1 Common chunk builder

All observed modern families use a `getDataPacket(...)` style helper.

The compressed payload is split into chunks, defaulting to `1024 bytes` per chunk in the utility code.

### 6.2 Chunk body format

For each chunk, the packet body is built as:

```text
<messageType>
00
<total_compressed_length:4 bytes BE>
<chunk_index:2 bytes BE>
<chunk_length:2 bytes BE>
<chunk_data:chunk_length bytes>
<xor_checksum:1 byte>
```

This whole body is then outer-framed as described earlier.

### 6.3 Meaning of fields

- `messageType`
  - `03` for program data chunks
  - `ff` for OTA data chunks
  - family-specific alternates may exist, but these are the main ones confirmed in the code paths
- `00`
  - constant subfield present in the streamed chunk format
- `total_compressed_length`
  - total size of the entire compressed object being transferred, not just this chunk
- `chunk_index`
  - chunk sequence number, big-endian 16-bit
- `chunk_length`
  - actual number of bytes in this chunk
- `xor_checksum`
  - XOR of all prior bytes in the chunk body excluding the outer frame

### 6.4 Sending cadence

The app spaces chunk packets by roughly `15 ms` and uses timeout/retry logic if the next stage does not complete in time.

---

## 7. Password protocol

The password-check and password-set formats are identical across the examined families.

### 7.1 Check password

Opcode:

- `0d`

Structure:

```text
0d
<rand:1>
<digit1 XOR rand>
<digit2 XOR rand>
...
<digitN XOR rand>
<xor_tail>
```

Where:

- `rand` is a random byte chosen by the app
- each password character is parsed as a single hex digit using `Integer.valueOf("0" + char, 16)`
  - this strongly suggests password digits are treated as hexadecimal-style nibbles (`0-9`, possibly `A-F` if UI permits)
- `xor_tail` is computed by XORing bytes from index 2 onward, i.e. the obfuscated password bytes only, not the opcode or random byte

### 7.2 Set password

Opcode:

- `0e`

Same structure as password check, only opcode differs.

### 7.3 Implication for clone implementation

To be protocol-compatible:

1. choose a random 1-byte mask
2. XOR each password nibble value with that mask
3. XOR all obfuscated password bytes together to produce the tail
4. send opcode `0d` or `0e` accordingly

---

## 8. Core control opcodes shared by multiple families

The following commands are directly confirmed by decompiled packet builder methods.

### 8.1 Common simple controls

| Function | Payload body |
|---|---|
| Query device info | `1f` |
| Set brightness | `04 <brightness>` |
| Set device power | `05 <00|01>` |
| Set rhythm type | `06 <type>` |
| Synchronize RTC | `09 <yy> <mm> <dd> <weekday> <hh> <mm> <ss>` |
| Read timer switches | `0b` |
| Set mirror / rotate depending on family | `0c <value>` |
| Check password | `0d ...` |
| Set password | `0e ...` |

### 8.2 Weekday mapping used in time sync

The APK maps Java/Android weekdays to device weekday numbers like this:

- Sunday -> `7`
- Monday -> `1`
- Tuesday -> `2`
- Wednesday -> `3`
- Thursday -> `4`
- Friday -> `5`
- Saturday -> `6`

### 8.3 Timer-switch write format

Used by UX and iLedClock, and likely the same semantic structure for related families.

Packet body starts with:

```text
0a <count>
```

Then, for each timer item:

```text
<enable:1>
<hour:1>
<minute:1>
<weekday_mask:1>
<set_device_on:1>
00
```

Weekday bit mask:

- Monday = `1`
- Tuesday = `2`
- Wednesday = `4`
- Thursday = `8`
- Friday = `16`
- Saturday = `32`
- Sunday = `64`
- `00` if the item is marked as `never`

`set_device_on`:

- `00` = switch device off at that timer
- `01` = switch device on at that timer

Final trailing `00` is always appended per timer item in the builder.

---

## 9. CoolLEDM family

## 9.1 Core commands

Confirmed from `CoolledMUtils`:

| Function | Payload body |
|---|---|
| Check password | `0d <rand> <masked...> <xor>` |
| Query device info | `1f` |
| Set brightness | `04 <brightness>` |
| Set mirror | `0c <00|01>` |
| Set password | `0e <rand> <masked...> <xor>` |
| Set rhythm type | `06 <type>` |
| Switch power | `05 <00|01>` |

## 9.2 Program-transfer start header

`CoolledMUtils.getStartDataForProgram(List compressedData, int index, int count)` builds:

```text
02
<crc32:4>
<compressed_length:4>
<index:1>
<count:1>
```

Then outer-frame it.

Interpretation:

- opcode `02` = program transfer start
- `index` = program slot or program index
- `count` = total program count or related transfer count field

## 9.3 OTA start header

`CoolledMUtils.getStartOTAUpdate(List compressedFirmware)` builds:

```text
fe
<crc32:4>
<compressed_length:4>
```

Then outer-frame it.

## 9.4 OTA chunk stream

`getOTAUpdate(...)` compresses via the built-in LZSS implementation and sends chunk packets with message type `ff`.

---

## 10. CoolLEDU family

## 10.1 Core commands

Confirmed from `CoolledUUtils`:

| Function | Payload body |
|---|---|
| Check password | `0d <rand> <masked...> <xor>` |
| Query device info | `1f` |
| Set brightness | `04 <brightness>` |
| Set mirror | `0c <00|01>` |
| Set password | `0e <rand> <masked...> <xor>` |
| Set rhythm type | `06 <type>` |
| Switch power | `05 <00|01>` |

## 10.2 Start headers

### Program start, simple slot form

`CoolledUUtils.getStartDataForProgram(List compressedData, int index)`:

```text
1a
<crc32:4>
<compressed_length:4>
<index:1>
```

### Program start, counted form

`CoolledUUtils.getStartDataForProgram(List compressedData, int index, int count)`:

```text
02
<crc32:4>
<compressed_length:4>
<index:1>
<count:1>
```

### OTA start

`CoolledUUtils.getStartDataForOtaUpgrade(List compressedFirmware)`:

```text
fe
<crc32:4>
<compressed_length:4>
```

Interpretation:

- U-family supports both a single-slot style (`1a`) and a counted program-transfer style (`02`)
- OTA header is simpler than UX raw-firmware bootstrap form

---

## 11. CoolLEDUX family

This family exposes the broadest control surface in the APK.

## 11.1 Core control commands

Confirmed from `CoolledUXUtils`:

| Function | Payload body |
|---|---|
| Check password | `0d <rand> <masked...> <xor>` |
| Query device info | `1f` |
| Query OTA version | `fd` |
| Query drive state | `1c 02` |
| Set drive state | `1c 01 <state>` |
| Set brightness | `04 <brightness>` |
| Set power | `05 <00|01>` |
| Set rhythm type | `06 <type>` |
| Sync time | `09 <yy> <mm> <dd> <weekday> <hh> <mm> <ss>` |
| Read timer switches | `0b` |
| Set mirror | `0c <00|01>` |
| Set rotate | `0c <rotate>` |
| Set password | `0e <rand> <masked...> <xor>` |
| Countdown status | `0f 01` |
| Countdown reset | `0f 02 <hh> <mm> <ss>` |
| Countdown start/stop | `0f 03 <00|01>` |
| Stopwatch status | `10 01` |
| Stopwatch reset | `10 02` |
| Stopwatch start/stop | `10 03 <00|01>` |
| Scoreboard status | `11 01` |
| Scoreboard set core | `11 02 <a:2> <b:2> <c:1> <d:1>` |
| Scoreboard set time | `11 03 <min:1> <sec:1> <00|01>` |
| Scoreboard start/stop | `11 04 <00|01>` |
| Set solid color | `13 01 <rgb444-like>` |
| Set color-speed | `13 02 <speed>` |
| Set color-mode preset | `13 03 ...` |
| Set device-info flag | `1e <selector> <00|01>` |

## 11.2 Device-info selector write

`setDeviceInfo(int selector, boolean enable)` emits:

```text
1e <selector_code> <00|01>
```

Selector mapping observed in code:

- selector `1` -> byte `01`
- selector `2` -> byte `02`
- selector `3` -> byte `03`

The semantic meaning of each selector is not named in the code. It appears to toggle device-level display/info options.

## 11.3 Color control

### Set a direct color

`setColor(...)` emits:

```text
13 01 <encoded_color>
```

The encoded color is produced through RGB444-related conversion helpers.

### Set color speed

`setColorSpeed(int speed)` emits:

```text
13 02 <speed>
```

### Set color mode / preset

`setColorMode(int mode)` emits:

```text
13 03 ...
```

and then appends large hardcoded mode-specific tables.

The APK contains many preset branches inside this method. A faithful clone that wants preset parity will need to copy those lookup tables or re-derive them from the decompiled code.

This is one of the few remaining areas where the protocol is known but the full human-normalized catalog is still tedious rather than conceptually unclear.

## 11.4 Start headers

### Program start, single-slot form

`getStartDataForProgram(List compressedData, int index)`:

```text
1a
<crc32:4>
<compressed_length:4>
<index:1>
```

### Program start, counted form

`getStartDataForProgram(List compressedData, int index, int count, int showCount)`:

```text
02
<crc32:4>
<compressed_length:4>
<index:1>
<count:1>
<showCount:1>
```

### Program start, extended grouping form

`getStartDataForProgram(List compressedData, int index, int count, int showCount, int groupType, int groupParam)`:

Starts as:

```text
02
<crc32:4>
<compressed_length:4>
<index:1>
<count:1>
<showCount:1>
```

Then appends extra zero-filled/group fields used for grouped or scheduled playback modes. Decompiled logic indicates at least three `groupType` branches:

- `groupType == 0`
- `groupType == 1`
- `groupType == 2`

The exact field purpose is not named, but the wire layout is reproducible from the code.

### OTA bootstrap using raw firmware bytes

`getStartDataForOtaUpgrade(byte[] rawFirmware)` emits:

```text
fe
<crc32_of_raw_firmware:4>
<raw_firmware_length:4>
<first_chunk_length:1>
<first_64_bytes_of_raw_firmware>
```

In the APK, `first_chunk_length` is set to `0x40`.

This is more than a mere metadata header: it primes the device with the first 64 raw firmware bytes directly in the start packet.

## 11.5 Program/OTA data chunks

UX uses the shared chunk packet structure described earlier.

Program chunks use `messageType = 03`.

OTA chunks use `messageType = ff`.

---

## 12. iLedClock family

This family inherits much of the UX-style command set and adds clock-centric commands.

## 12.1 Core commands

Confirmed from `ILedClockUtils`:

| Function | Payload body |
|---|---|
| Alarm list query | `16 02` |
| Reminder list query | `1a 01` |
| Reminder detail query | `1a 02 <id>` |
| Set alarm list | `16 01 <count> ...` |
| Set night mode | `14 01 <10 bytes>` |
| Tomato timer set | `15 01 <count> <time1> ...` |
| Tomato timer query | `15 02` |
| Temperature/humidity query | `19 <type>` |
| Check password | `0d <rand> <masked...> <xor>` |
| Countdown status | `0f 01` |
| Countdown reset | `0f 02 <hh> <mm> <ss>` |
| Countdown start/stop | `0f 03 <00|01>` |
| Scoreboard status | `11 01` |
| Scoreboard set core | `11 02 <a:2> <b:2> <c:1> <d:1>` |
| Scoreboard set time | `11 03 <min> <sec> <00|01>` |
| Scoreboard start/stop | `11 04 <00|01>` |
| Set brightness | `04 <brightness>` |
| Set mirror | `0c <00|01>` |
| Set rhythm type | `06 <type>` |
| Set volume | `1e 06 <volume>` |
| Power switch | `05 <00|01>` |
| Sync time | `09 <yy> <mm> <dd> <weekday> <hh> <mm> <ss>` |
| Query timer switches | `0b` |
| Query tomato timers | `15 02` |
| Rotate | `0c <rotate>` |
| Set timer switches | `0a ...` |
| Set device-info flag | `1e <selector> <00|01>` |

## 12.2 Alarm-clock write format

`getSetAlarmClockTime(List alarms)` emits:

```text
16 01 <count>
```

Then, for each alarm item:

```text
<enable:1>
<hour:1>
<minute:1>
<weekday_mask_or_00:1>
<duration:2 bytes BE>
<reminderDuration:1>
```

Weekday mask uses the same bit allocation as timer switches:

- Monday = `1`
- Tuesday = `2`
- Wednesday = `4`
- Thursday = `8`
- Friday = `16`
- Saturday = `32`
- Sunday = `64`
- `00` when `isNever` is true

## 12.3 Night mode format

`getSetNightMode(...)` emits:

```text
14 01 <p2> <p3> <p4> <p5> <p6> <p7> <p8> <p9> <p10> <p11>
```

The builder takes 10 one-byte parameters. The code does not label them semantically, but this is enough to reproduce wire-compatible writes.

## 12.4 Tomato timer format

Set:

```text
15 01 <count> <value1> <value2> ...
```

Query:

```text
15 02
```

Each timer value is emitted as a one-byte integer in the builder observed.

## 12.5 Temperature / humidity query

```text
19 <type>
```

Where `type` is a one-byte mode/selector passed by the caller.

## 12.6 iLedClock program start headers

The clock family supports multiple start-header variants.

### Simple slot form

```text
1a
<crc32:4>
<compressed_length:4>
<index:1>
```

### Counted form

```text
02
<crc32:4>
<compressed_length:4>
<index:1>
<count:1>
<showCount:1>
```

### Extended typed form

The APK also includes a richer start-header builder whose behavior branches on a program type value. The emitted layout begins like this:

```text
02
<crc32:4>
<compressed_length:4>
<index:1>
<count:1>
00
00 00 00 00 00 00 00 00
...
```

and then appends different subtype fields depending on the program type.

This is reproducible from the code, but the semantic labels for the type values are not explicitly documented in the APK. This matters mainly for advanced clock-specific program classes, not for basic control or basic upload mechanics.

### OTA bootstrap using raw bytes

Like UX, iLedClock also has a start-header builder that includes raw firmware size/CRC and initial raw bytes.

---

## 13. Response handling and parser behavior

Static analysis of `DeviceManager` shows that notifications are converted from bytes to hex strings and then routed into response handlers keyed by device family and command type.

The APK contains explicit parsing support for at least these response domains:

- device info
- password verification result
- timer switch state
- countdown state
- stopwatch state
- scoreboard state
- night mode state
- alarm clock list and details
- reminder list and reminder detail
- tomato clock / pomodoro data
- temperature and humidity
- drive-state / mode-state queries
- transfer progress / acknowledgments for program and OTA flows

The earlier analysis pass also identified that the app keeps separate retry and stage counters, implying the notification parser is part of a transfer state machine rather than a stateless request/response setup.

A clone therefore needs:

1. unframing and unescaping
2. payload dispatch by opcode/subopcode
3. state-machine handling for content/OTA transfers
4. timeout and retry handling consistent with the app’s constants

---

## 14. Device-family routing / type system

The APK maintains an internal device-type taxonomy. Relevant constants extracted from `DeviceManager` include types for:

- legacy CoolLED 12x48 / 5x36 / X / A / S
- multiple CoolLEDM geometries
- multiple CoolLEDU geometries
- many CoolLEDUX geometries
- `ILedClock = 50`

It also has color-type constants such as:

- single color = `0`
- seven color = `1`
- colorful = `2`
- colorful UX = `3`
- colorful iLedClock = `4`

This means the BLE protocol is only one layer. The app also uses advertisement parsing and/or device-info parsing to choose the correct content builder and feature set.

A faithful clone should therefore separate:

- transport layer
- family selection
- device capability model
- content encoder / program encoder

---

## 15. Minimum viable clone strategy

If the goal is to recreate real behavior rather than merely understand it, the implementation path is:

### Phase 1: transport parity

Implement:

- FFF0/FFF1 connect path
- MTU 247 request with fallback
- outer frame wrapping / unwrapping
- byte stuffing / unstuffing
- command send queue
- 15 ms inter-packet pacing
- timeout / retry behavior

### Phase 2: common control surface

Implement shared commands first:

- power
- brightness
- password check / set
- time sync
- timer switch read/write
- countdown / stopwatch / scoreboard

### Phase 3: transfer support

Implement:

- custom CRC routine from this spec
- LZSS compressor matching APK behavior
- start-header builders for M/U/UX/iLedClock
- 1024-byte chunking with XOR tail

### Phase 4: family-specific extras

Then add:

- UX drive state, color, color-speed, color-mode presets
- iLedClock alarms, night mode, tomato timer, volume, reminder/temperature queries

### Phase 5: content encoder parity

Only after the transport is working, replicate higher-level content generation:

- text layout
- animation/graffiti conversion
- frame/playlist/group program builders
- preset effect tables

---

## 16. What is fully pinned down vs. what is still loose

## 16.1 Fully pinned down from static code

These are directly supported by decompiled code and can be implemented with high confidence:

- BLE UUIDs
- scan-name families
- connect/notify/MTU strategy
- outer frame format
- escaping rules
- integer endianness
- XOR checksum behavior
- custom CRC routine used by start headers
- LZSS parameters and token format
- chunk packet structure
- password packet format
- most of the simple control command opcodes for M/U/UX/iLedClock
- clock alarm/timer/tomato/night-mode write layouts

## 16.2 Still somewhat loose

These are real but not yet fully normalized into a friendly human table:

- full semantic catalog for all UX color preset tables in `setColorMode(...)`
- full semantic labeling of advanced program-type values in the richer start-header builders
- full parity for every content-composition subtype used by the app’s UI when building complex programs

That said, these are not blockers to creating a functioning transport-compatible client. They mostly affect feature completeness and UI-level parity.

---

## 17. Evidence sources inside the APK

The following decompiled classes contain the important protocol logic:

- `com.jtkj.led1248.light.device.DeviceManager`
- `com.jtkj.led1248.light.utils.CoolledMUtils`
- `com.jtkj.led1248.light.utils.CoolledUUtils`
- `com.jtkj.led1248.light.utils.CoolledUXUtils`
- `com.jtkj.led1248.light.utils.ILedClockUtils`
- `CoolledMUtils$CrcCode`
- `CoolledUUtils$CrcCode`
- `CoolledUXUtils$CrcCode`
- `ILedClockUtils$CrcCode`
- `CoolledMUtils$LzssCompress`
- `CoolledUUtils$LzssCompress`
- `CoolledUXUtils$LzssCompress`
- `ILedClockUtils$LzssCompress`

---

## 18. Bottom line

You can recreate the BLE behavior from this APK.

The critical blockers are no longer conceptual:

- framing is known
- write path is known
- notify path is known
- CRC is known
- compression format is known
- chunking is known
- the main command families are known

What remains is engineering work, not mystery.

If you build a client that follows this document, it should be able to:

- discover the same devices
- connect the same way
- authenticate with password-capable devices
- control brightness/power/time/timers
- drive countdown/stopwatch/scoreboard features
- configure clock-specific features on `iLedClock`
- upload program and OTA payloads in the same structural format as the app

