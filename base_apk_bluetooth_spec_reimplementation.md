# `base.apk` Bluetooth Reimplementation Specification

## Scope

This document is a **static reverse-engineering specification** for the uploaded APK:

- package: `com.jtkj.led1248`
- version: `2.7.2` (`versionCode=108`)
- minSdk: `24`
- targetSdk: `36`

The goal is not just to describe that the app uses Bluetooth, but to document the protocol and state machine deeply enough that you can build a compatible implementation.

This is based on:

- manifest inspection
- decompilation of the Bluetooth manager and protocol utility classes
- reconstruction of outbound packet builders
- reconstruction of the inbound response parser

This is **not** based on live sniffing, so anything that depends on runtime-only values is explicitly marked.

---

## Confidence legend

- **Confirmed**: directly evidenced by decompiled code
- **High-confidence inference**: strongly implied by multiple code paths, but not recovered from a single canonical method
- **Unresolved**: present in code, but full wire details were not recovered in this pass

---

## 1. Android / BLE stack

### 1.1 Bluetooth library

The app uses an embedded BLE library under:

- `com.jtkj.library.fastble.*`

The manager class that orchestrates BLE behavior is:

- `com.jtkj.led1248.light.device.DeviceManager`

### 1.2 Requested Android capabilities

Manifest-level findings:

- BLE feature required: `android.hardware.bluetooth_le`
- also requests camera and audio-related permissions/features because the app does more than BLE transport

Relevant permissions include:

- `android.permission.BLUETOOTH`
- `android.permission.BLUETOOTH_ADMIN`
- `android.permission.BLUETOOTH_CONNECT`
- `android.permission.BLUETOOTH_SCAN`
- `android.permission.BLUETOOTH_ADVERTISE`
- `android.permission.ACCESS_FINE_LOCATION`
- `android.permission.ACCESS_COARSE_LOCATION`
- `android.permission.RECORD_AUDIO`
- `android.permission.MODIFY_AUDIO_SETTINGS`
- `android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE`

The audio permissions line up with local-mic / rhythm modes and clock audio features.

---

## 2. Device families supported by the APK

The scan layer is not generic. It is explicitly aimed at these name families:

- `CoolLED536`
- `CoolLED`
- `CoolLEDX`
- `CoolLEDA`
- `CoolLEDS`
- `CoolLEDM`
- `CoolLEDU`
- `CoolLEDUX`
- `iDevilEyes`
- `iLedHat`
- `iLedHatC`
- `iLedOpen`
- `iLedCar`
- `iLedBike`
- `iLedClock`

For reimplementation purposes, the families that are materially reconstructed in this pass are:

- **CoolLEDM**
- **CoolLEDU**
- **CoolLEDUX**
- **iLedClock**

`CoolLEDX` and `CoolLEDS` are definitely supported by the app, but their full command builder set was not fully reconstructed in this pass.

---

## 3. BLE transport topology

### 3.1 GATT endpoints

Confirmed constants used by the write path:

- Service UUID: `0000fff0-0000-1000-8000-00805f9b34fb`
- Characteristic UUID: `0000fff1-0000-1000-8000-00805f9b34fb`
- CCCD: `00002902-0000-1000-8000-00805f9b34fb`

The app uses **FFF1 as the main command channel** for both:

- outbound writes
- inbound notifications

### 3.2 Scan configuration

`DeviceManager.initBleManager()` configures the BLE layer with:

- logging disabled: `enableLog(false)`
- reconnect count: `5`
- reconnect interval: `1000 ms`
- connection timeout: `5000 ms`
- operation timeout: `5000 ms`
- scan timeout: `3000 ms`
- auto-connect: `false`

The scan rule is not just name-based; the app also filters around the `FFF0` service.

### 3.3 Multi-device behavior

The send path maintains maps of connected devices and per-device send-enable flags.

The core queue sender:

- pulls the next encoded packet from an internal queue
- iterates connected devices
- writes the same packet to each device whose send flag is enabled

This is used for content/program push scenarios.

Password operations and some management flows are tied to a single active device object.

---

## 4. Common wire format

This is the most important part of the protocol. The M/U/UX/Clock families all share the same outer framing model.

## 4.1 Integer encoding

### Single byte

Helper methods render integers as uppercase hex byte strings:

- `0` -> `00`
- `1` -> `01`
- `15` -> `0f` / `0F` depending helper path

### Two-byte values

Two-byte helpers are **big-endian**:

- `0x1234` -> `12 34`

### Four-byte values

Four-byte helpers are **big-endian**:

- `0x00012345` -> `00 01 23 45`

The code builds these values by left-padding the hex string and slicing from most-significant byte to least-significant byte.

---

## 4.2 Outer frame format

Confirmed from `getSendDataWithInfo(...)` in the M/U/UX/Clock utility classes.

### Raw structure before escaping

```text
01  <escaped( len_hi len_lo payload_bytes )>  03
```

Where:

- start delimiter = `0x01`
- end delimiter = `0x03`
- `len_hi len_lo` is the **payload length only**, not including the delimiters or the length field itself
- the bytes inside `len + payload` are escaped before transmission

### Important note

There is **no universal outer checksum** added by `getSendDataWithInfo()`.

Some commands add their own inner checksum fields, but the generic wrapper does not.

---

## 4.3 Escaping / byte stuffing

Confirmed from `convertData(List, int)`.

Reserved bytes in the inner stream are:

- `0x01`
- `0x02`
- `0x03`

If any byte in `len_hi len_lo payload...` equals one of those values, it is replaced with:

```text
02  (byte XOR 04)
```

Examples:

- `01` -> `02 05`
- `02` -> `02 06`
- `03` -> `02 07`

Bytes `00` and `04`..`FF` are sent unchanged.

### Decoder rule

To unframe a notify payload for these families:

1. verify first byte is `01`
2. verify last byte is `03`
3. unescape the interior by reversing `02 xx` -> `(xx XOR 04)`
4. parse first two unescaped bytes as payload length
5. parse the next `length` bytes as the payload body

That gives you the family-specific command or response payload.

---

## 4.4 Simple command bodies

For ordinary state-setting and query commands, the payload body is usually just:

```text
<opcode> [subcommand] [parameters...]
```

No extra checksum is added unless the specific builder adds one.

---

## 4.5 XOR checksum helper used in chunk payloads

Confirmed from `convertEnd(List)`.

`convertEnd(...)` computes a **1-byte XOR checksum** over all bytes in the provided list.

This checksum is used in packetized transfer chunks and in password bodies.

---

## 5. Password protocol

Families using the password model:

- CoolLEDM
- CoolLEDU
- CoolLEDUX
- iLedClock
- plus several sibling brands routed through the same behavior

The app also auto-checks saved/default passwords after connection for many of these devices.

### 5.1 Default password behavior

The default fallback password is:

- `000000`

It is stored per device MAC under preference keys like:

- `DeviceManager_DEVICE_PASSWORD_FOR_>>>MAC`

### 5.2 Password check packet

Opcode:

- `0D`

Body before outer framing:

```text
0D  rand  enc_digit_0  enc_digit_1 ... enc_digit_n  xor_tail
```

Where:

- `rand` is a random byte `0..255`
- the password string is processed **character by character**
- each character is treated as a single hex nibble by parsing `"0" + char`
  - example `'5'` -> `0x05`
- each nibble-byte is XORed with `rand`
- `xor_tail` is the XOR of all encrypted password bytes only, starting at index 2 of the body

### 5.3 Password set packet

Opcode:

- `0E`

Encoding is identical to `0D`, just with opcode `0E`.

### 5.4 Password responses

The parser treats responses as:

```text
0D status
0E status
```

Status semantics:

- `00` = success
- nonzero = failure

On password-check success, the device is marked verified and the password is persisted.

---

## 6. Common basic control opcodes across M/U/UX/Clock

These families reuse a common core.

| Function | Opcode / body | Notes |
|---|---:|---|
| Get device info | `1F` | Query |
| Set brightness | `04 <value>` | 1-byte brightness |
| Power on/off | `05 <00/01>` | switch state |
| Rhythm / mic mode | `06 <value>` | M/U/UX/Clock families |
| Sync time | `09 ...` | UX/Clock |
| Timer switch set | `0A ...` | UX/Clock |
| Timer switch get | `0B` | UX/Clock |
| Mirror / rotate | `0C <value>` | M/U interpret as mirror; UX/Clock use as rotate in builder |
| Password check | `0D ...` | encrypted form |
| Password set | `0E ...` | encrypted form |
| Countdown | `0F <subcmd> ...` | UX/Clock |
| Stopwatch | `10 <subcmd> ...` | UX/Clock |
| Scoreboard | `11 <subcmd> ...` | UX/Clock |
| Color subsystem | `13 <subcmd> ...` | UX/Clock |
| Night mode | `14 <subcmd> ...` | iLedClock |
| Tomato clock | `15 <subcmd> ...` | iLedClock |
| Alarm clock | `16 <subcmd> ...` | iLedClock |
| Reminder / alternate start-program on some families | `1A ...` | meaning depends on family |
| Drive state get/set | `1C <subcmd> ...` | UX; U uses related path |
| Device info toggles / volume | `1E <subcmd> ...` | UX/Clock |
| OTA version query | `FD` | UX/Clock |
| OTA/program start | `FE ...` or `02 ...` / `1A ...` | family-dependent |

---

## 7. Family: CoolLEDM

## 7.1 Device-info query

Request:

```text
1F
```

Response body is 7 bytes total:

```text
1F switch brightness mirror mic_supported mic_enabled unknown
```

The parser uses:

- byte 1 = switch
- byte 2 = brightness
- byte 3 = mirror
- byte 4 = local mic supported
- byte 5 = local mic enabled
- byte 6 = parsed but not used in the decompiled path

## 7.2 Basic setters

Confirmed builders:

- brightness: `04 <brightness>`
- power: `05 <00/01>`
- rhythm type: `06 <value>`
- mirror: `0C <00/01>`
- password check: `0D ...`
- password set: `0E ...`

Response shapes:

- `04 <brightness>`
- `05 <00/01>`
- `0C <00/01>`
- `0D <status>`
- `0E <status>`

## 7.3 Program start header

Builder:

```text
02  <crc?>  <total_len_u32_be>  <program_index_u8>  <program_count_u8>
```

The exact CRC algorithm lives in `CoolledMUtils$CrcCode.getCrcCode(...)` and was not fully recovered in this pass.

## 7.4 OTA start header

Builder:

```text
FE  <crc?>  <total_len_u32_be>
```

## 7.5 OTA data chunks

`getOTAUpdate(...)` uses:

- `LzssCompress.getLzssCompressData(...)`
- then packetizes the compressed result with command byte `FF`

### Chunk inner structure

Each chunk payload is:

```text
00  total_len_u32_be  chunk_index_u16_be  chunk_len_u16_be  chunk_bytes...  xor_checksum
```

Then the app prepends the outer command byte, usually `FF`, and outer-wraps the result.

---

## 8. Family: CoolLEDU

This is structurally close to CoolLEDM, but adds drive-state management.

## 8.1 Device-info query/response

Request:

```text
1F
```

Response body is 7 bytes total:

```text
1F switch brightness mirror mic_supported mic_enabled unknown
```

Parsed fields:

- byte 1 = switch
- byte 2 = brightness
- byte 3 = mirror
- byte 4 = local mic supported
- byte 5 = local mic enabled
- byte 6 = parsed but not used in the displayed event

After parsing `1F`, the app immediately issues a drive-state query.

## 8.2 Drive-state response

The parser accepts either:

- `1E <state>`
- `1C <state>`

and publishes a drive-state response event.

The exact query builder for this family was referenced via `CoolledUDUtils.getDriveState()` in the manager and not fully reconstructed in this pass, but the response contract is clear.

## 8.3 Basic setters

Confirmed builders:

- brightness: `04 <value>`
- power: `05 <00/01>`
- rhythm: `06 <value>`
- mirror: `0C <00/01>`
- password check: `0D ...`
- password set: `0E ...`

## 8.4 Program start headers

Two variants are confirmed.

### Standard program start

```text
02  <crc?>  <total_len_u32_be>  <program_index_u8>  <program_count_u8>
```

### Alternate start

```text
1A  <crc?>  <total_len_u32_be>  <value_u8>
```

The alternate `1A` path is likely used for a different content/program mode rather than the reminder feature that `1A` means on iLedClock.

## 8.5 OTA start/data

### Start

```text
FE  <crc?>  <total_len_u32_be>
```

### Data

- compressed with LZSS
- chunked into inner `00 totalLen idx len data xor` structures
- wrapped with command `FF`

---

## 9. Family: CoolLEDUX

This is a more advanced protocol family. It adds package-size negotiation, richer device metadata, drive-state commands, timers, stopwatch/countdown/scoreboard, color control, and OTA version queries.

## 9.1 Device-info query

Request:

```text
1F
```

## 9.2 Device-info response layout

Parsed response body:

```text
1F
  switch
  brightness
  rotate
  mic_supported
  mic_enabled
  local_mic_mode
  show_device_id
  max_program_number
  remote_enable
  ...
  [optional package size at bytes 19..20]
```

Confirmed parser fields:

- byte 1 = switch
- byte 2 = brightness
- byte 3 = rotate
- byte 4 = mic supported
- byte 5 = mic enabled
- byte 6 = local mic mode
- byte 7 = show-device-id flag
- byte 8 = max program number
- byte 9 = remote-enable flag

### Package size negotiation

If the device-info response includes a 2-byte package-size field at indexes `19..20`, the app uses that value if:

- it is nonzero
- it is `<= 4096`

Otherwise it falls back to:

- `1024`

The app stores this per-device UX package size.

This is important for reimplementation: **do not hardcode 1024 for UX if the device advertises a larger package size**.

## 9.3 Automatic post-connect actions

After successful `1F` parsing, the app does at least these follow-ups:

- posts a synchronize-time event
- schedules further management calls after a short delay

## 9.4 Drive-state commands

### Query

```text
1C 02
```

### Set

```text
1C 01 <state>
```

### Response

The parser accepts:

```text
1C 02 <state>
1C 03 <state>
```

and treats byte 2 as the current drive state.

## 9.5 Time synchronization

Builder:

```text
09  year_since_2000  month  day  weekday  hour  minute  second
```

Weekday mapping used by the app:

- Sunday -> `7`
- Monday -> `1`
- Tuesday -> `2`
- Wednesday -> `3`
- Thursday -> `4`
- Friday -> `5`
- Saturday -> `6`

The response is treated as a simple ACK. The decompiled logger shows the app expects:

```text
09 <ack>
```

## 9.6 Timer-switch subsystem

### Get timers

Request:

```text
0B
```

### Set timers

Request starts with:

```text
0A count [entries...]
```

Each entry is 6 bytes:

```text
enable  hour  minute  repeat_mask  device_on  reserved_zero
```

Weekday bitmask:

- bit 0 = Monday
- bit 1 = Tuesday
- bit 2 = Wednesday
- bit 3 = Thursday
- bit 4 = Friday
- bit 5 = Saturday
- bit 6 = Sunday

If no days are set, the app treats that as `isNever = true`.

### Get-timers response

Parsed as:

```text
0B count [count * 6-byte entries]
```

with the same entry layout as above.

### Set-timers response

Parsed as:

```text
0A status
```

- `00` = success
- nonzero = failure

## 9.7 Countdown subsystem

### Query status

```text
0F 01
```

### Reset / set

```text
0F 02 <hour> <minute> <second>
```

### Start/stop

```text
0F 03 <00/01>
```

The decompiler renders primitive booleans as `== null` in several places. For reimplementation, treat these as standard false/true mappings:

- false -> `00`
- true -> `01`

### Parsed response model

The parser handles subcommands under opcode `0F` and reconstructs:

- set hour
- set minute
- set seconds
- left hour
- left minute
- left seconds
- running state

The detailed response layout is clearly parsed, but the exact top-level branch shape is cumbersome in the decompiled output. The important fact is that the UX family expects countdown responses to come back under opcode `0F` with subcommand-specific bodies.

## 9.8 Stopwatch subsystem

### Query status

```text
10 01
```

### Reset

```text
10 02
```

### Start/stop

```text
10 03 <00/01>
```

Parsed status fields:

- status/running flag
- hour
- minute
- second

## 9.9 Scoreboard subsystem

### Query status

```text
11 01
```

### Set scores

```text
11 02  hostScore_u16_be  visitScore_u16_be  hostTotal_u8  visitTotal_u8
```

### Set time

```text
11 03  minute_u8  second_u8  countdown_flag_u8
```

### Start/stop

```text
11 04 <00/01>
```

### Parsed scoreboard response

When the parser handles the full scoreboard state, it extracts:

- host score: bytes 2..3 as u16 BE
- visit score: bytes 4..5 as u16 BE
- host total score: byte 6
- visit total score: byte 7
- device minute: byte 8
- device second: byte 9
- running status: byte 10
- configured minute: byte 11
- configured second: byte 12
- time mode / countdown flag: byte 13

The parser turns that into:

- `isStartOrStop`
- `isCountDown`
- score and timer fields

## 9.10 Color subsystem

This is richer than a simple RGB setter.

### Set explicit color

```text
13 01 <RGB444-derived payload>
```

The builder calls:

- `TextEmojiManagerCoolLEDUX.getColorDataWithColorWithRGB444Transfer(...)`

This means the wire format is **not plain RGB888**. It is converted into a 4-bit-per-channel style payload before transport.

### Set color speed

```text
13 02 <value>
```

### Set color mode / palette mode

```text
13 03 <mode_header...> <palette_data...>
```

This command is implemented with a large hardcoded palette/gradient lookup in `setColorMode(int)`.

Recovered structure:

```text
13 03 <v3> [v4_if_nonnegative] <entry_count> <palette_bytes...>
```

Where:

- `v3` is a mode-group/control byte
- `v4` is present for many modes and acts like a sub-mode byte
- `entry_count` is the number of palette entries / bytes implied by the mode
- the actual palette bytes are built from hardcoded comma-separated hex strings

### What that means operationally

If you want exact app parity for built-in color modes, you need to port the lookup table from `setColorMode(int)`. The transport shape is recovered, but this pass did **not** normalize every single mode ID into a human-readable table.

## 9.11 Device-info toggles

Builder:

```text
1E <subcmd> <00/01>
```

Recovered mappings:

- `1E 01 <00/01>`
- `1E 02 <00/01>`
- `1E 03 <00/01>`

The app uses these for device-level display/control toggles. The decompiled manager makes it clear these are tied to `show device id`, remote enable, and related info flags.

## 9.12 OTA version query

Request:

```text
FD
```

Parsed response layout:

```text
FD ota_supported local_version_hi local_version_lo file_name_len file_name_bytes...
```

Semantics:

- byte 1 = OTA support flag
- bytes 2..3 = local OTA version, big-endian u16
- byte 4 = remote OTA filename length
- bytes 5.. = ASCII filename

The app then calls its HTTP layer to check remote OTA metadata using that filename.

## 9.13 Program start headers

### Standard program header

```text
02  <crc?>  <total_len_u32_be>  <program_index_u8>  <program_count_u8>  <show_count_u8>
```

### Alternate program header

```text
1A  <crc?>  <total_len_u32_be>  <value_u8>
```

### Extended program header

Recovered from the 6-argument builder:

```text
02
  <crc?>
  <total_len_u32_be>
  <program_index_u8>
  <program_count_u8>
  <show_count_u8>
  00 00 00 00 00 00 00 00
  00
  <special_mode>
  [mode-specific trailer]
```

Mode-specific trailer behavior recovered:

- mode `0`: append `00` + `00 00 00 01`
- mode `1`: append `00 00 00 00 00 00 00 00`
- mode `2`: append `<value>` + `00 00 00 00 00 00 00`

This is clearly used for more advanced program/display modes.

## 9.14 OTA start

Two start styles are present.

### Standard start

```text
FE  <crc?>  <total_len_u32_be>
```

### Hardware-upgrade start

```text
FE  <crc?>  <total_len_u32_be>  40  <first_64_bytes>
```

The `0x40` literal is a 64-byte preamble length marker. The builder copies the first 64 bytes of the binary into the start packet.

## 9.15 OTA data

Builder uses:

- `LzssCompress.getLzssCompressData(...)`
- then `getDataPacket(..., "ff")`

Default chunk size is `1024`, but the family also supports a custom chunk size overload.

---

## 10. Family: iLedClock

The clock family is UX-like plus clock-specific features.

## 10.1 Device-info response

Parsed fields under opcode `1F`:

- switch
- brightness
- rotate
- mic supported
- mic enabled
- local mic mode
- show device id
- max program number
- remote enable
- mute state
- volume
- optional package size

Specifically recovered parser positions:

- byte 1 = switch
- byte 2 = brightness
- byte 3 = rotate
- byte 4 = mic supported
- byte 5 = mic enabled
- byte 6 = local mic mode
- byte 7 = show device id
- byte 8 = max program number
- byte 9 = remote enable
- byte 22 = mute flag
- byte 23 = volume
- bytes 19..20 = package size when present

As with UX, package size defaults to `1024` if not present or unreasonable.

## 10.2 Basic setters inherited from UX shape

Confirmed builders:

- brightness: `04 <value>`
- power: `05 <00/01>`
- rhythm: `06 <value>`
- sync time: `09 ...`
- timer set/get: `0A` / `0B`
- rotate/mirror path: `0C <value>`
- password check/set: `0D` / `0E`
- countdown: `0F ...`
- stopwatch: `10 ...`
- scoreboard: `11 ...`
- color: `13 ...`
- OTA version: `FD`

## 10.3 Volume

Builder:

```text
1E 06 <volume>
```

## 10.4 Night mode

### Set

```text
14 01
  enabled
  start_hour
  start_minute
  end_hour
  end_minute
  brightness
  device_state_enabled
  voice_control_enabled
  wake_up_duration
  voice_sensitivity
```

### Get

```text
14 02
```

### Parsed get response

The parser reconstructs:

- `nightModeEnabled`
- `startTimeHour`
- `startTimeMinute`
- `endTimeHour`
- `endTimeMinute`
- `brightness`
- `deviceStateEnabled`
- `voiceControlEnabled`
- `wakeUpDuration`
- `voiceSensitivity`

## 10.5 Tomato clock

### Set

```text
15 01 count [time_0] [time_1] ...
```

Each item contributes one byte `timeValue`.

### Get

```text
15 02
```

### Parsed response

```text
15 02 count values...
```

Each value becomes a `TomatoClockItem(timeValue)`.

## 10.6 Alarm clock

### Set

```text
16 01 count [entries...]
```

Each alarm entry is 7 bytes:

```text
enable  hour  minute  repeat_mask  duration_u16_be  reminder_duration_u8
```

The weekday repeat mask uses the same Monday..Sunday bit layout as timer switches.

### Get

```text
16 02
```

### Parsed get response

```text
16 02 count [count * 7-byte entries]
```

Each entry is decoded into:

- `enable`
- `hour`
- `minute`
- weekday repeat bits
- `duration` (2 bytes, BE)
- `reminderDuration` (1 byte)
- `isNever` when repeat mask is zero

## 10.7 Reminder subsystem

Opcode family:

- `1A`

### List reminders

```text
1A 01
```

Response:

```text
1A 01 count id_0 id_1 ...
```

The parser turns that into a list of reminder IDs.

### Get reminder detail

```text
1A 02 <id>
```

Parsed response fields:

- id
- sound
- year
- month
- day
- hour
- minute
- repeatType
- one ignored byte
- duration (2 bytes, BE)
- content length
- UTF-8 content bytes

### Delete reminder

```text
1A 03 <id>
```

Response is just logged as a result code.

## 10.8 Temperature / humidity

Request:

```text
19 <type>
```

The parser explicitly handles a response with subcommand/type `1` and decodes temperature from two bytes plus humidity from one byte.

### Decoding formula

Recovered from parser:

- combine bytes 2 and 3 as a 16-bit value `raw`
- sign bit is bit 15
- magnitude uses:
  - integer part: `(raw & 0x7FF0) >> 4`
  - fractional part: `(raw & 0x000F) * 0.1`
- if sign bit is set, negate the temperature
- humidity = byte 4 as integer percent-like value

So:

```text
temperature = sign ? -(((raw & 0x7FF0) >> 4) + ((raw & 0x000F) * 0.1))
                  :  (((raw & 0x7FF0) >> 4) + ((raw & 0x000F) * 0.1))
humidity    = byte4
```

## 10.9 OTA version query

Same shape as UX:

```text
FD ota_supported version_hi version_lo file_name_len ascii_file_name...
```

## 10.10 Program headers

The clock family supports multiple start-header variants.

### Simple header

```text
02  <crc?>  <total_len_u32_be>  <program_index_u8>  <program_count_u8>  <show_count_u8>
```

### Alternate header

```text
1A  <crc?>  <total_len_u32_be>  <value_u8>
```

### Typed header

Recovered from builders taking `programType`:

```text
02
  <crc?>
  <total_len_u32_be>
  <program_index_u8>
  <program_count_u8>
  00
  00 00 00 00 00 00 00 00
  [type trailer]
```

Recovered type trailers:

- type `8`  -> `01`
- type `9`  -> `02`
- type `11` -> `03`
- type `7`  -> `04 01 00 00 00 0A`
- type `6`  -> `04 01 00 00 00 05`
- type `19` -> `04 01 00 00 00 05`
- type `14` -> `05` or `05 <extraByte>` depending overload
- default   -> `00 00 <len_u32_be>`

This is enough to reproduce the typed-header behavior even though the semantic names of all `programType` values were not fully recovered.

## 10.11 OTA start

Two styles are confirmed, same as UX.

### Standard

```text
FE  <crc?>  <total_len_u32_be>
```

### Hardware-style preamble start

```text
FE  <crc?>  <total_len_u32_be>  40  <first_64_bytes>
```

---

## 11. Upload / transfer state machine

The APK has a nontrivial retry and skip logic for content/program and OTA pushes.

## 11.1 Packetized transfer chunk layout

For families using `getDataPacket(...)`, each chunk body is:

```text
00
  total_len_u32_be
  chunk_index_u16_be
  chunk_len_u16_be
  chunk_bytes...
  xor_checksum_of_all_above_bytes
```

That block is then prefixed by a command byte and outer-framed.

For OTA data builders recovered here, the command byte is:

- `FF`

## 11.2 Start-header response semantics

The manager treats 2-byte responses to program/OTA start headers roughly like this:

```text
<start_opcode> status
```

Recovered meaning:

- `00` = start accepted, now begin sending chunk 0
- `01` = start accepted and data transfer can be skipped
  - the app treats this as “no need send from package”
  - likely means the device already has matching content by CRC/version
- `02` = device error
- `03` = data error
- other = unknown error

This logic appears in both program and OTA flows.

## 11.3 Chunk response semantics

The manager also handles 5-byte transfer responses shaped like:

```text
<opcode> <unknown_or_subtype> <chunk_index_hi> <chunk_index_lo> <status>
```

Recovered meaning of `status`:

- `00` = chunk accepted
- `01` = send error / retry condition
- `02` = device error
- `03` = data error
- other = unknown error

The parser updates its internal `mSendIndex` from bytes 2..3 and uses that to continue with the next chunk or restart/retry.

### Practical implementation note

Even though the exact meaning of byte 1 is not fully normalized in this pass, bytes 2..3 and byte 4 are enough to replicate the upload state machine.

## 11.4 Retry policy

Recovered manager behavior:

- package retry counters reset to `3`
- whole-transfer retry counters reset to `3`
- delayed timeout messages are scheduled at `5000 ms`
- successful completion advances:
  - next chunk
  - next program
  - next device

The app supports pushing the same content across multiple enabled devices in sequence.

---

## 12. Response grammar summary

This is the shortest useful decoder summary for a reimplementation.

### M / U family simple state responses

- `04 xx` -> brightness state
- `05 xx` -> switch state
- `0C xx` -> mirror state
- `0D xx` -> password-check status
- `0E xx` -> password-set status
- `1F ...` -> device info
- `1C/1E xx` -> drive state for U-family paths

### UX / Clock extended responses

- `09 xx` -> time-sync ack
- `0A xx` -> timer-set ack
- `0B count ...` -> timer list
- `0F <subcmd> ...` -> countdown
- `10 <subcmd> ...` -> stopwatch
- `11 <subcmd> ...` -> scoreboard
- `13 <subcmd> xx` -> color ack
- `14 <subcmd> ...` -> night mode (clock)
- `15 <subcmd> ...` -> tomato clock (clock)
- `16 <subcmd> ...` -> alarm clock (clock)
- `19 <type> ...` -> temperature/humidity (clock)
- `1A <subcmd> ...` -> reminders (clock) or alternate content start on other families
- `1C <subcmd> ...` -> drive state (UX)
- `1E <subcmd> ...` -> device settings / volume
- `1F ...` -> device info
- `FD ...` -> OTA capability/version + remote filename
- `FE/02/...` -> program or OTA transfer state

---

## 13. Things you can reimplement now with high confidence

You can build the following now without more reverse engineering:

1. **BLE discovery and connection layer**
   - scan by service/name
   - connect to FFF0/FFF1
   - enable notifications

2. **The entire outer framing / escaping codec**
   - delimiters
   - 2-byte payload length
   - byte-stuffing for `01/02/03`

3. **Core device-management commands** for M/U/UX/Clock
   - info
   - brightness
   - power
   - mirror/rotate
   - password check/set
   - drive state
   - timers
   - countdown/stopwatch/scoreboard
   - night mode / tomato clock / alarm / reminders / temp-humidity for iLedClock

4. **The upload state machine shell**
   - start header
   - data chunk structure
   - chunk index tracking
   - retry/error handling

5. **OTA capability/version query parsing**

---

## 14. Remaining unresolved pieces

These are the main gaps if you want byte-perfect parity with every app feature.

### 14.1 CRC algorithm used by `CrcCode.getCrcCode(...)`

Present and required for:

- program start headers
- OTA start headers

It was referenced everywhere, but the exact implementation was not fully recovered in this pass.

### 14.2 LZSS variant details

The app clearly compresses OTA payloads and likely some content payloads with an internal `LzssCompress` implementation. The exact compressor parameters were not fully recovered here.

### 14.3 Full built-in color-mode table normalization

The transport structure for `13 03 ...` is recovered, but the meaning of every mode ID in the giant hardcoded palette lookup has not been normalized into a clean table yet.

### 14.4 Full CoolLEDX / CoolLEDS protocol family

The APK definitely supports them, including password gating, but their full command builder set was not fully reconstructed in this pass.

### 14.5 Advertisement-byte layout

The app derives some device metadata from advertisement/device naming helpers, including row/column/color type for certain families. The exact advertisement byte offsets were not fully reconstructed here.

---

## 15. Recommended implementation order

If the goal is a working clone rather than a museum piece, do it in this order.

### Phase 1: transport

- implement BLE scan/connect/notify/write
- implement outer frame encode/decode
- implement multi-device queueing only if you actually need broadcast behavior

### Phase 2: basic device control

- `1F`, `04`, `05`, `0C`, `0D`, `0E`
- parse device info and state acks
- implement saved/default password behavior

### Phase 3: UX/Clock management commands

- `1C`, `09`, `0A`, `0B`, `0F`, `10`, `11`, `13`, `14`, `15`, `16`, `19`

### Phase 4: content / OTA pipeline

- recover/port CRC
- recover/port LZSS
- implement start headers and chunking
- port retry logic

### Phase 5: parity work

- normalize built-in color modes
- decode X/S family fully
- validate against live captures

---

## 16. Minimal encoder pseudocode

```text
function encode_frame(payload_bytes):
    inner = u16_be(len(payload_bytes)) + payload_bytes
    escaped = []
    for b in inner:
        if b in {0x01, 0x02, 0x03}:
            escaped.append(0x02)
            escaped.append(b ^ 0x04)
        else:
            escaped.append(b)
    return [0x01] + escaped + [0x03]
```

### Password body pseudocode

```text
function build_password_body(opcode, password_digits_string):
    rand = random_byte()
    out = [opcode, rand]
    for ch in password_digits_string:
        nibble = int('0' + ch, 16)
        out.append(nibble ^ rand)
    tail = xor_all(out[2:])
    out.append(tail)
    return encode_frame(out)
```

### Chunk body pseudocode

```text
function build_chunk(command, full_data, chunk_index, chunk_bytes):
    inner = [0x00]
    inner += u32_be(len(full_data))
    inner += u16_be(chunk_index)
    inner += u16_be(len(chunk_bytes))
    inner += chunk_bytes
    inner += [xor_all(inner)]
    return encode_frame([command] + inner)
```

---

## 17. Bottom line

The APK is not using opaque vendor SDK magic. For the M/U/UX/Clock families, it uses a fairly regular protocol:

- one BLE service/characteristic pair
- framed packets with byte-stuffing
- mostly 1-byte opcodes and subcommands
- per-family device-info parsers
- predictable ACK and retry handling
- a chunked upload path with explicit indexes and status codes

What still blocks a **fully independent drop-in clone** is not the BLE transport anymore. It is the remaining application-specific internals:

- CRC implementation
- exact LZSS variant
- full color-mode lookup normalization
- the less-recovered X/S family command set

Everything else is sufficiently mapped to implement a working client for the reconstructed families.
