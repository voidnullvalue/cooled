# Implementation Status (audited April 11, 2026)

## Status
This file is now a **status summary**, not an execution checklist. Status labels below are based on current code wiring and tests, not historical checkboxes.

## Confirmed complete

### Protocol core
- Frame envelope + escaping/unescaping (`01 ... 03`, `02` escape with `xor 0x04`) is implemented in `FrameCodec`.
- Custom CRC routine is implemented in `CoolLedCrc` and used by start-header builders.
- Core command builders (power/brightness/rhythm/mirror/password/info) are implemented and wired through `DeviceRepository`.

### Upload primitives
- Program/OTA start header builders (`02`, `1A`, `FE`) and chunk packet builder (`03`/`FF`) are implemented in `CommandBuilders`.
- Program composer path (`ProgramContent` -> encode -> LZSS -> start header -> chunk frames) is implemented in `ProgramComposer`.
- Transfer session model with start/chunk ack handling, retry exhaustion, timeout tick, cancel, and failed/completed states is implemented in `TransferStateMachine`.

### Parser and family model
- Typed parser coverage exists for core controls, transfer acks, device info/OTA info, and clock-class responses (`0B/0F/10/11/14/15/16/19/1A/1E`).
- Family detection and capability map are implemented (`FamilyDetector`, `CapabilityMap`).

### Fake transport + observability
- Fake transport supports scripted payload/raw-frame injection (not just loopback).
- Raw TX/RX timeline events are emitted by transport and surfaced in `AppViewModel` events.
- Parsed vs unknown parse-path events are surfaced in the same debug feed.

## Confirmed partial
- Advanced `programType` semantic mapping is still partial; unknown values still use fallback trailer encoding in `typedProgramTrailer`.
- CoolLEDX/CoolLEDS and iLedClock end-to-end hardware parity remains partial (code exists, broad device validation evidence does not).
- Transfer timing constants/retry tuning are implemented but not physically tuned across unstable links.

## Still unresolved
- Full OEM parity for complex/less-common composition classes (beyond current `Text`, `Drawing`, `PresetMode`) is unresolved.
- Hardware validation breadth remains unresolved; see `docs/REAL_DEVICE_VALIDATION.md`.

## Historical notes
- Earlier notes that LZSS was pass-through are stale; tokenized LZSS encode/decode is now present.
- Earlier roadmap checklists should be treated as historical planning artifacts, not current source of truth.
