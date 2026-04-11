# Implementation Plan (authoritative, audited April 11, 2026)

This is the active plan. It is grounded in the current Kotlin/Gradle codebase, not in legacy checklist markdown.

## 1) Evidence / scope

Audit inputs used:
- docs + build files: `README.md`, `docs/ARCHITECTURE.md`, `docs/NEXT_PASS_PLAN.md`, `docs/NEXT_PASS_2_PLAN.md`, `docs/PROTOCOL_IMPLEMENTATION_NOTES.md`, `docs/REAL_DEVICE_VALIDATION.md`, `docs/TESTING.md`, `build.gradle.kts`, `settings.gradle.kts`, `app/build.gradle.kts`, `gradle/wrapper/gradle-wrapper.properties`.
- implementation source of truth: `app/src/main/**` and `app/src/test/**`.

Audit rule used: code and tests override stale markdown.

## 2) Implemented foundation

### Build/tooling baseline (confirmed from Gradle files)
- AGP `8.7.3`, Kotlin `2.0.21`, Gradle wrapper `8.9`.
- App targets `compileSdk 35`, `targetSdk 35`, `minSdk 24`.
- Java/Kotlin target is 17 (`sourceCompatibility`/`targetCompatibility` + `JvmTarget.JVM_17`).
- Wrapper scripts are present (`gradlew`, `gradlew.bat`).

### Runtime structure currently in code
- `BleTransport` abstraction exists with both `AndroidBleTransport` and `FakeBleTransport` implementations.
- Repository layer (`DeviceRepository`) wires transport + command builders + parser.
- View model currently instantiates **FakeBleTransport** directly for runtime behavior in this app branch.
- Compose UI exposes scan/connect controls, protocol command controls, transfer script controls, and debug timeline output.

## 3) Implemented protocol/runtime features

### Confirmed implemented (code + tests)
- Frame codec: envelope + escaping/unescaping (`FrameCodec`).
- CRC routine (`CoolLedCrc`) used in start headers.
- Parser has typed handling for core/device/transfer and clock-family responses (`ProtocolParsers` opcodes `04/05/0C/0D/0E/09/0A/0B/0F/10/11/14/15/16/19/1A/1E/1F/FD/02/03/FE/FF`).
- LZSS codec is implemented as tokenized LZSS (`N=512`, `F=18`, `THRESHOLD=2`) with LSB-first flag consumption (`LzssCodec.compress/decompress`).
- Command builders include color-mode path (`13 03`) and advanced clock reset commands (`0F 02`, `10 02`).
- Program composer path exists end-to-end (`ProgramContent` -> encode -> compress -> start header -> chunk frames).
- Transfer state machine implements start/chunk ack handling, retry exhaustion, timeout ticks, cancel, completion/failure states.
- Fake transport supports scripted payload/frame injection and emits raw TX/RX IO events.

### Confirmed partial
- Advanced `programType` semantics are only partially reconstructed (`typedProgramTrailer` has known branches + generic fallback for unknowns).
- Support for rich OEM content classes is partial; currently exposed composition types are `Text`, `Drawing`, `PresetMode`.
- Broad runtime parity claims are not justified by current hardware evidence.

## 4) Testing status

### Unit tests that exist
`app/src/test/java/com/cooled/core/protocol` currently covers:
- Frame roundtrip.
- CRC determinism.
- Chunk split + chunk XOR packet validation.
- Parser vectors (password/device/transfer/clock families) and malformed/unknown handling.
- LZSS roundtrip + literal-vector behavior.
- Program header family behavior including COOLLEDU alternate start opcode.
- Advanced opcode builder checks (color mode and reset commands).
- Transfer state machine success and retry-exhaustion behavior.
- Program composer package/chunk generation.
- Fake transport scripted-response and IO-direction behavior.

### Current execution caveat (container)
- `./gradlew testDebugUnitTest` fails before task execution under installed JDK `25.0.1` (`IllegalArgumentException: 25.0.1`).
- This is a local toolchain mismatch against project baseline (JDK 17/21 expected), not direct evidence of protocol regressions.

## 5) Remaining gaps

### Must fix for build/runtime correctness
1. Run CI/local unit tests with JDK 17 or 21 and record a clean baseline run.
2. Decide production wiring for transport in this branch: app currently uses `FakeBleTransport` in `AppViewModel`; Android transport exists but is not the active runtime path.
3. Add automated assertions for parser coverage gaps currently only manually inferred (especially less-common clock/reminder edge payloads).

### Must validate on hardware
1. Validate transfer retry/timeout behavior on unstable BLE links (disconnect/reconnect + partial ack patterns).
2. Validate color mode + advanced start-header behavior on CoolLEDX/CoolLEDS/iLedClock devices.
3. Validate countdown/stopwatch reset command behavior (`0F 02`, `10 02`) against real responses/state.
4. Validate full upload lifecycle parity (start/chunk ack semantics and render results) across family variants.

### Nice-to-have parity work
1. Expand `ProgramContent` with additional reverse-engineered OEM composition classes.
2. Improve semantic labeling for known `programType` values and response models.
3. Add integration-style tests for scripted transfer sequences spanning mixed ack/nack/timeouts.

### Deferred / needs more reverse-engineering evidence
1. Complete semantics for all unknown/rare `programType` trailer formats.
2. Exact family-specific timing thresholds for robust throughput without over-retry.
3. Full parity for obscure OEM command variants not yet represented in parser/builder contracts.

## 6) Ordered next steps (actionable)

1. **Toolchain baseline pass**
   - Re-run `./gradlew testDebugUnitTest` under JDK 17/21 and capture results in `docs/TESTING.md`.
2. **Transport runtime decision**
   - Either wire `AndroidBleTransport` into app runtime (with fallback/test-mode fake transport) or explicitly document demo-only fake transport mode.
3. **Hardware validation pass**
   - Execute `docs/REAL_DEVICE_VALIDATION.md` workflow and store concrete evidence logs per device family.
4. **Protocol parity pass**
   - Close highest-value parser/composer gaps backed by captured traffic (not assumptions).
5. **Documentation pass**
   - Keep `docs/IMPLEMENTATION_PLAN.md` as the only active plan; keep next-pass docs as historical ledgers.
