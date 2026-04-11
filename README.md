# CoolLED BLE Reimplementation (Native Android, No Play Services)

This repository contains a native Kotlin Android app that reimplements the BLE transport/protocol behavior evidenced in reverse-engineering artifacts for `com.jtkj.led1248`.

## No Google Play Services
This app intentionally does **not** use Google Play Services, Firebase, Nearby, Play Integrity, or any GMS BLE wrappers. BLE uses only Android native APIs:
- `android.bluetooth.*`
- `android.bluetooth.le.*`

## Supported families (current)
- CoolLEDM
- CoolLEDU
- CoolLEDUX
- CoolLEDX
- CoolLEDS
- iLedClock

## Build
```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

## Implemented feature coverage (this pass)
- BLE scan/connect/notify/MTU architecture with native Android stack
- Typed response parser dispatch for core + clock + transfer ack families
- LZSS compressor/decompressor with recovered `N=512/F=18/THRESHOLD=2` token format
- Family-aware capability mapping and advanced program/OTA start-header builders
- Clock-class command builders (alarms/reminders/night mode/timer/countdown/stopwatch/scoreboard/volume/tomato/temp-humidity/time sync)
- Transfer ack/retry session state machine with explicit finite retries
- Fake transport scripted-RX support for deterministic protocol-flow tests
- Compose UI sections for clock controls, transfer state, and parsed debug event stream

## Feature support matrix
| Area | Status |
|---|---|
| BLE scanning/connection/session | Implemented |
| Family detection/capabilities | Implemented (expanded) |
| Basic controls | Implemented |
| Password check/set | Implemented |
| Program/OTA start headers and chunking | Implemented |
| Transfer ack/retry state machine | Implemented (device validation pending) |
| Full response parser parity for documented families | Implemented |
| Exact LZSS parity | Implemented with isolated flag-bit-order ambiguity |
| Full semantic naming of all advanced program types | Partial |
| CoolLEDX/CoolLEDS behavior parity on hardware | Partial |

## Remaining ambiguities / validation needs
- LZSS flag-byte bit order still has one static-analysis ambiguity; implementation isolates it to one enum-controlled path.
- Advanced `programType` semantic naming is still partially unresolved, though recovered trailer bytes are implemented.
- Physical-device validation is still required for transfer timing and family-specific runtime behavior.
