# CoolLED BLE Reimplementation (Native Android, No Play Services)

Native Kotlin Android app reimplementing BLE transport/protocol behavior evidenced in reverse-engineering artifacts for `com.jtkj.led1248`.

## No Google Play Services
This project intentionally avoids Google Play Services/Firebase/Nearby wrappers and uses native Android BLE APIs (`android.bluetooth.*`, `android.bluetooth.le.*`).

## Supported device-name families (detection)
- CoolLEDM
- CoolLEDU
- CoolLEDUX
- CoolLEDX
- CoolLEDS
- iLedClock

## Current status summary

### Implemented
- BLE transport abstraction + Android implementation + scripted fake transport implementation.
- App runtime now constructs `AndroidBleTransport` by default and exposes permission-gated real BLE scan/connect/write controls.
- BLE scan/connect/discover/notify flow for `FFF0/FFF1` + CCCD enable + MTU request in `AndroidBleTransport`.
- Android 12+ BLE runtime permission handling for scan/connect paths.
- Frame codec with escaping/unescaping and payload-length envelope handling.
- Malformed/empty frame tolerance so startup/default RX frames do not crash parsing.
- CRC implementation and use in transfer start headers.
- Typed parser coverage for core controls, transfer acks, device info/OTA info, and clock-class families.
- LZSS tokenized compress/decompress implementation (`N=512`, `F=18`, `THRESHOLD=2`) with LSB-first flag handling.
- Command builders for core controls, password, clock-class queries/commands, color-mode, start headers, and chunks.
- Program composition path (`ProgramContent` -> encode -> compress -> start header -> chunk frames).
- Transfer retry/timeout/cancel state machine.
- Debug timeline surfacing raw TX/RX events and parsed-vs-unknown parser outcomes.

### Runtime modes
- Default app runtime: real Android BLE transport.
- Fake/scripted transport: still available through `AppViewModel` dependency injection for deterministic tests and non-device demo flows.
- Fake-only transfer script buttons are disabled/guarded when the app is running in Android BLE mode.

### Partial
- Advanced `programType` semantic labeling is incomplete (fallback trailer branch is still used for unknown/less-understood values).
- Full runtime parity for all advanced OEM content classes is incomplete.
- Physical transfer timing tuning and broad family-by-family runtime parity remain incomplete.

### Validated
- Unit test coverage exists for frame/CRC/chunk/parser/LZSS/builders/transfer/fake-transport paths.
- Broad real-device parity is **not** yet validated; see `docs/REAL_DEVICE_VALIDATION.md`.

## Source-of-truth plan docs
- Active plan: `docs/IMPLEMENTATION_PLAN.md`
- Historical ledgers: `docs/NEXT_PASS_PLAN.md`, `docs/NEXT_PASS_2_PLAN.md`

## Build/test
```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

## Toolchain notes
- Android Gradle Plugin: `8.7.3`
- Gradle wrapper: `8.9`
- Kotlin plugins: `2.0.21`
- `compileSdk` / `targetSdk`: `35`
- JDK: use `17` or `21` (JDK `25` currently breaks Gradle/Kotlin script evaluation in this repo)

## Environment caveats
- Android SDK/build-tools must be installed and discoverable (`ANDROID_HOME`/`ANDROID_SDK_ROOT`).
- Plugin/dependency resolution requires access to `google()` and `mavenCentral()` repositories.
- Real BLE testing requires a device/emulator environment with Bluetooth hardware and runtime permissions granted.