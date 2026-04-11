# Protocol Implementation Notes

## Mapping evidence -> code
- UUIDs and scan names were mapped into `BleProtocolConstants`.
- Frame/escape rules were mapped into `FrameCodec`.
- CRC algorithm from `*Utils$CrcCode` was mapped into `CoolLedCrc`.
- Common and clock-class opcodes from clone spec were mapped into `CommandBuilders`.
- Chunk packet format and split behavior mapped into `buildDataChunk` and `splitChunks`.
- Family names/capabilities mapped into `FamilyDetector` and `CapabilityMap`.
- Upload retry/ack state handling mapped into `TransferStateMachine`.

## Confirmed implemented commands
- Core controls/info/password: `1F`, `04`, `05`, `06`, `0C`, `0D`, `0E`
- Clock/time/timer/state: `09`, `0A`, `0B`, `0F`, `10`, `11`, `14`, `15`, `16`, `19`, `1A`, `1E`
- Transfer primitives:
  - program start headers (`02` / `1A`, including typed trailer branch)
  - OTA start (`FE`, standard + preamble style)
  - data chunk builders (`03` / `FF`)

## Parser coverage in this pass
Typed parser dispatch now covers:
- brightness/power/mirror/password acknowledgments
- device info and OTA capability/version response (`1F` / `FD`)
- timer switch list and time/timer acks (`0B`, `09`, `0A`)
- countdown/stopwatch/scoreboard state shells (`0F`, `10`, `11`)
- night mode (`14`), tomato (`15`), alarms (`16`), reminders (`1A`), temp/humidity (`19`), volume (`1E`)
- transfer start/chunk acknowledgments (`02`, `03`, `FE`, `FF`)

## LZSS parity status
Implemented tokenized LZSS codec with recovered constants and token format:
- `N=512`, `F=18`, `THRESHOLD=2`
- grouped flag bytes controlling 8 tokens
- two-byte back-reference token encoding

### Remaining ambiguity (isolated)
- Flag-bit consumption order in token groups (LSB-first vs MSB-first) was not conclusively proven in the extracted textual artifacts.
- Implementation isolates this ambiguity to `LzssCodec.FlagBitOrder`; default path is `LSB_FIRST`.

## Remaining partial areas
- Full semantic labeling of every advanced `programType` value is still incomplete; builder supports recovered trailers but names for all values are not fully reconstructed.
- CoolLEDX/CoolLEDS family-specific high-level UI workflows still need on-device behavior validation.
- Transfer timing constants (exact inter-packet delay and OEM-specific timeout tuning) still need physical-device tuning.
