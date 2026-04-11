# Protocol Implementation Notes

## Mapping evidence -> code
- UUIDs and scan names were mapped into `BleProtocolConstants`.
- Frame/escape rules were mapped into `FrameCodec`.
- CRC algorithm from `*Utils$CrcCode` was mapped into `CoolLedCrc`.
- Common and clock-class opcodes from clone spec were mapped into `CommandBuilders`.
- Chunk packet format and split behavior mapped into `buildDataChunk` and `splitChunks`.
- Family names/capabilities mapped into `FamilyDetector` and `CapabilityMap`.
- Upload retry/ack state handling mapped into `TransferStateMachine`.
- Content composition mapped into `ProgramComposer` + `ProgramContent`.

## Confirmed implemented commands
- Core controls/info/password: `1F`, `04`, `05`, `06`, `0C`, `0D`, `0E`
- Color mode: `13 03 <mode>`
- Clock/time/timer/state: `09`, `0A`, `0B`, `0F`, `10`, `11`, `14`, `15`, `16`, `19`, `1A`, `1E`
- Clock reset subcommands: `0F 02`, `10 02`
- Transfer primitives:
  - program start headers (`02` / `1A`, including typed trailer branch)
  - OTA start (`FE`, standard + preamble style)
  - data chunk builders (`03` / `FF`)

## Parser coverage
Typed parser dispatch covers:
- brightness/power/mirror/password acks
- device info and OTA capability/version (`1F` / `FD`)
- timer switch list and time/timer acks (`0B`, `09`, `0A`)
- countdown/stopwatch/scoreboard (`0F`, `10`, `11`)
- night mode (`14`), tomato (`15`), alarms (`16`), reminders (`1A`), temp/humidity (`19`), volume (`1E`)
- transfer start/chunk acks (`02`, `03`, `FE`, `FF`)
- unknown and malformed packet fallthrough paths

## LZSS parity status
Implemented tokenized LZSS codec with recovered constants and token format:
- `N=512`, `F=18`, `THRESHOLD=2`
- grouped flag bytes controlling 8 tokens
- two-byte back-reference encoding

### Flag-bit order resolution
Flag bit order is now locked to **LSB-first** based on decompiled compressor loops in
`CoolledUUtils$LzssCompress.lazssCompress` (and sibling family compressors): mask starts at `1` and shifts left per token.

## Remaining partial areas
- Full semantic naming of every advanced `programType` value is still incomplete; unknown values are still sent via raw fallback trailer format.
- Full high-level program-content parity for every OEM composition class (especially animation/gif/complex grouped modes) is still partial.
- CoolLEDX/CoolLEDS runtime behavior still needs broader on-device validation.
- Transfer timing constants still need physical-device tuning.
