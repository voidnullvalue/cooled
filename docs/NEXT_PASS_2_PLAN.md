# Next Pass 2 Audit Ledger (April 11, 2026)

## Status
This document is **historical** and now serves as an audited ledger of what that pass actually landed. It is not the active work queue.

## Confirmed complete in code
- LZSS ambiguity closure: `LzssCodec` implements tokenized codec and uses LSB-first flag consumption.
- Advanced composer/upload path: `ProgramComposer` + `CommandBuilders` cover content encode, compression, start headers, and chunk framing.
- Family-specific command additions: color mode (`13 03`), countdown reset (`0F 02`), stopwatch reset (`10 02`), U-family alternate start opcode support.
- Transfer state handling: retry/timeout/cancel/failure/completion modeled in `TransferStateMachine`.
- Fake transport scripting: scripted payload/raw replay supported in `FakeBleTransport`.
- Observability: raw TX/RX + parsed/unknown event distinctions are surfaced through `AppViewModel`.
- Unit tests exist for parser vectors, LZSS roundtrip/vector, builders, transfer state transitions, and fake transport scripting.
- Docs for architecture/protocol/testing/hardware validation exist and are now re-audited.

## Confirmed partial after audit
- Deep CoolLEDX/CoolLEDS content semantics are still partial (advanced `programType` naming and rich OEM composition classes not fully reconstructed).
- Hardware-runtime parity remains partial; current evidence is code + unit tests, not broad real-device runs.

## Still unresolved
- Real-device timing/performance tuning under noisy BLE conditions.
- Broad physical validation coverage across all supported families and advanced content classes.

## Verification note
- In this container, `./gradlew testDebugUnitTest` currently fails before task execution under JDK `25.0.1` due Kotlin/Gradle Java version parsing (`IllegalArgumentException: 25.0.1`).
- This is an environment/toolchain mismatch, not a protocol-feature regression signal.
