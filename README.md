# CoolLED BLE Reimplementation (Native Android, No Play Services)

This repository contains a native Kotlin Android app that reimplements BLE transport/protocol behavior evidenced in reverse-engineering artifacts for `com.jtkj.led1248`.

## No Google Play Services
This app intentionally does **not** use Google Play Services, Firebase, Nearby, Play Integrity, or GMS BLE wrappers. BLE uses only Android native APIs:
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
- Native BLE scan/connect/notify/MTU architecture
- Typed parser dispatch for core + clock + transfer responses
- LZSS compressor/decompressor with confirmed **LSB-first** flag-bit order
- Family-aware program/OTA start headers + chunk packet builders
- Centralized program content composer (text/drawing/preset) with compression+chunk packaging
- Clock-class builders including advanced reset commands (`0F 02`, `10 02`)
- Color mode command path (`13 03 <mode>`)
- Transfer ack/retry state machine with finite retries, timeout, cancel
- Scriptable fake transport scenarios for transfer robustness testing
- Debug timeline with raw TX/RX hex + parsed/unknown event distinctions

## Feature support matrix
| Area | Status |
|---|---|
| BLE scanning/connection/session | Implemented |
| Family detection/capabilities | Implemented |
| Basic controls + password | Implemented |
| Program/OTA start headers + chunking | Implemented |
| Program content composer (text/drawing/preset) | Implemented (initial parity scope) |
| Transfer ack/retry state machine | Implemented (hardware timing tuning pending) |
| Full documented parser families | Implemented |
| LZSS flag-bit order | Resolved (LSB-first) |
| Full semantic labeling of all advanced program types | Partial |
| Full CoolLEDX/CoolLEDS runtime parity on hardware | Partial |

## Remaining validation needs
- End-to-end hardware verification of advanced content classes on real CoolLEDX/CoolLEDS and iLedClock targets.
- Physical timing tuning for noisy BLE links (inter-packet delays/timeouts).
- Additional reverse-engineered vectors for uncommon program types beyond currently implemented typed trailer map.


## Build environment notes
- Use JDK 21 (or 17). Running Gradle with JDK 25 currently fails during Kotlin DSL bootstrap (`IllegalArgumentException: 25.0.1`).
- Android Gradle Plugin artifacts are resolved from Google Maven; builds fail in restricted/offline environments that block `google()` repository access.
