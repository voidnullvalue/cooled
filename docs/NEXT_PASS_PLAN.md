# Next Pass Plan (April 11, 2026)

This document enumerates unresolved implementation areas from the current codebase and defines the execution checklist for this pass.

## Repo-state audit summary

### Unresolved markers found
- `app/src/main/java/com/cooled/core/compression/LzssCodec.kt`
  - `TODO/UNRESOLVED`: compressor is literal pass-through, no tokenized LZSS parity.
- `docs/PROTOCOL_IMPLEMENTATION_NOTES.md`
  - parser parity, iLedClock advanced handling, and LZSS parity marked partial.
- `README.md`
  - feature matrix marks parser parity and exact LZSS parity as partial.

### Structural gaps found in code
- `ProtocolParsers` only returns generic `Ack(opcode,data)` or `Unknown`; no typed opcode/subcommand models.
- `CommandBuilders` only covers basic controls + generic start/chunk helpers; no family-aware advanced headers and no clock-class builders.
- `Capabilities` only models five coarse flags; missing feature-level gating needed by UX/Clock flows.
- No transfer session state machine (start/chunk ack/nack/retry/timeout state handling not implemented).
- `FakeBleTransport` is loopback-only; cannot script multi-step protocol replay for transfer and parser tests.
- UI exposes only basic controls and a raw parsed debug line; no clock feature sections, transfer visibility, or parsed event list.
- Tests cover frame/CRC/chunk basics only; no parser vectors, LZSS parity vectors, family header builders, retry transitions, or clock parsing.

## Execution checklist for this pass

## Phase B – Parser parity
- [ ] Introduce typed sealed response model hierarchy for documented response families.
- [ ] Implement deterministic parser dispatch for:
  - [ ] alarm responses (`16`)
  - [ ] reminder responses (`1A`)
  - [ ] tomato/pomodoro (`15`)
  - [ ] temp/humidity (`19`)
  - [ ] timer/countdown/stopwatch/scoreboard (`0B/0F/10/11`)
  - [ ] night mode (`14`)
  - [ ] transfer start/chunk acks (`02/03/FE/FF` and equivalent)
  - [ ] password responses (`0D/0E`)
  - [ ] device info/capability responses (`1F/FD`)
  - [ ] clock-class state acks (`09/0A/1E`)
- [ ] Add parser unit vectors (hex->model assertions) for each implemented response family.

## Phase C – LZSS parity
- [ ] Replace pass-through codec with N=512/F=18/THRESHOLD=2 tokenized LZSS implementation.
- [ ] Add decompressor parity path matching token representation.
- [ ] If bit-order ambiguity remains, isolate it to one helper and document it precisely.
- [ ] Add encode/decode tests plus fixed compressed token vectors.

## Phase D – Family-specific advanced builders + clock features
- [ ] Expand capability model to feature-level flags used by repository/UI.
- [ ] Add family-aware start-header builders (`02`/`1A`/extended typed/grouped variants/`FE` preamble style).
- [ ] Normalize advanced type trailer/preset data into structured tables/constants.
- [ ] Add clock-class command builders for alarms/reminders/night mode/timer/stopwatch/countdown/scoreboard/volume/tomato/temp-humidity/time sync.

## Phase E – Transfer state machine + app wiring
- [ ] Implement upload session model with finite retries, timeout handling, resend, cancel, cleanup.
- [ ] Parse and apply transfer start/chunk responses to state transitions.
- [ ] Keep transfer execution on coroutines (no UI thread blocking).
- [ ] Expose transfer status and parsed events to debug UI.
- [ ] Extend `FakeBleTransport` for scripted RX replay flows.

## Phase F – Tests + docs update
- [ ] Add tests for parser vectors, LZSS, family headers, clock command builders, and transfer state machine transitions.
- [ ] Run `./gradlew testDebugUnitTest` and fix failures.
- [ ] Update README + protocol docs with completed vs still-partial inventory and explicit ambiguities.

## Known ambiguity to isolate (if still unresolved)
- LZSS flag-bit consumption order (LSB-first vs MSB-first) is the only expected ambiguous area from static evidence unless an exact decompiled branch provides direct confirmation.
