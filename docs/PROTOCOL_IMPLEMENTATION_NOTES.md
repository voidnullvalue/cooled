# Protocol Implementation Notes

## Status
Implementation notes audited against current source.

## Implemented and wired
- UUID/constants + scan-name table: `BleProtocolConstants`.
- Frame encode/decode + escape rules: `FrameCodec`.
- CRC routine: `CoolLedCrc`.
- Family detection/capabilities: `FamilyDetector` + `CapabilityMap`.
- Command builders for core, clock-class, color mode, password, start headers, OTA start, and chunk frames: `CommandBuilders`.
- Program packaging flow from typed content through compression/chunking: `ProgramComposer`.
- Transfer ack/retry/timeout/cancel state model: `TransferStateMachine`.
- Typed parser dispatch for core, transfer, and clock-class response families: `ProtocolParsers`.

## Parser coverage currently present
`ProtocolParsers` has typed branches for:
- Core: brightness/power/mirror/password (`04/05/0C/0D/0E`)
- Device/OTA info (`1F` / `FD`)
- Clock/timer classes (`09/0A/0B/0F/10/11/14/15/16/19/1A/1E`)
- Transfer start/chunk responses (`02/03/FE/FF`)
- Unknown and malformed fallthrough (`Unknown`, `ParseError`)

## LZSS status
- Implemented tokenized LZSS compressor/decompressor with `N=512`, `F=18`, `THRESHOLD=2`.
- Flag-bit order is implemented as LSB-first.

## Confirmed partial / unresolved
- Advanced `programType` semantics are only partially decoded; unknown values use fallback trailer encoding.
- High-level parity for all OEM advanced program composition classes is not complete.
- Runtime timing/performance behavior (retry/timeout tuning) still requires hardware validation.

## Evidence boundary
- “Implemented” in this doc means code exists and is called by repo/viewmodel paths.
- “Validated” requires either passing tests or hardware evidence; broad hardware parity is still open.
