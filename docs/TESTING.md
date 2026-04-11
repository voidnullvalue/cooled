# Testing

## Status
Current test coverage and execution caveats audited against repo + local command results.

## Unit tests in repo
Unit tests are under `app/src/test/java/com/cooled/core/protocol` and currently cover:
- `FrameCodec` encode/decode and escaping roundtrip.
- `CoolLedCrc` determinism check.
- Chunk splitting and chunk XOR-tail packet validation.
- Parser vectors for password/device/transfer/clock families.
- Unknown/malformed parser fallthrough behavior.
- `LzssCodec` roundtrip and literal-flag vector behavior.
- Family-aware start header behavior (`02` typed trailer and `1A` alternate opcode case).
- Command opcode checks for advanced mode/reset paths.
- `TransferStateMachine` success and retry-exhaustion transitions.
- `ProgramComposer` package/chunk generation.
- `FakeBleTransport` scripted response and I/O direction behavior.

## Local commands
```bash
./gradlew testDebugUnitTest
```

## Current container result (April 11, 2026)
- Command fails before task execution under JDK `25.0.1` with Kotlin/Gradle script error:
  `java.lang.IllegalArgumentException: 25.0.1`
- This is a toolchain/runtime mismatch in this environment, not a proven unit-test assertion failure.

## Expected toolchain
- JDK `17` or `21`
- AGP `8.7.3`
- Gradle wrapper `8.9`
- Android SDK/API 35 installed and discoverable

## Hardware validation
Unit tests do not replace on-device parity checks. Use `docs/REAL_DEVICE_VALIDATION.md` for hardware coverage.
