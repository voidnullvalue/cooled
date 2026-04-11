# Protocol Implementation Notes

## Mapping evidence -> code
- UUIDs and scan names were mapped into `BleProtocolConstants`.
- Frame/escape rules were mapped into `FrameCodec`.
- CRC algorithm from `*Utils$CrcCode` was mapped into `CoolLedCrc`.
- Common opcodes in clone spec were mapped into `CommandBuilders`.
- Chunk packet format and split behavior mapped into `buildDataChunk` and `splitChunks`.
- Family names/capabilities mapped into `FamilyDetector` and `CapabilityMap`.

## Confirmed implemented commands
- `1F` query device info
- `04` set brightness
- `05` power switch
- `06` rhythm type
- `0B` timer switch query
- `0C` mirror/rotate value path
- `0D` check password
- `0E` set password
- start headers + chunk builders for program/OTA primitives

## Unresolved / partial areas
- Full mode-table payloads for `13 03` family presets are not fully reconstructed in this pass.
- Full iLedClock extended program-header variants are only partially scaffolded.
- Parser dispatch for all response schemas (alarm/reminder/tomato/temp-humidity/etc.) remains partial.
- LZSS currently pass-through; exact token stream parity is TODO/UNRESOLVED.

## Family notes
- CoolLEDM / CoolLEDU share compact core command set.
- CoolLEDUX adds drive-state, color family, scoreboard, countdown/stopwatch controls.
- iLedClock extends with alarm/reminder/night-mode/tomato/temp-humidity and volume selectors.
