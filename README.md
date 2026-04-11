# CoolLED BLE Reimplementation (Native Android, No Play Services)

This repository contains a native Kotlin Android app that reimplements the BLE transport/protocol behavior evidenced in reverse-engineering artifacts for `com.jtkj.led1248`.

## No Google Play Services
This app intentionally does **not** use Google Play Services, Firebase, Nearby, Play Integrity, or any GMS BLE wrappers. BLE uses only Android native APIs:
- `android.bluetooth.*`
- `android.bluetooth.le.*`

## Supported families (current)
- CoolLEDM (core controls + transfer primitives)
- CoolLEDU (core controls + transfer primitives)
- CoolLEDUX (core controls + transfer primitives + capability flags)
- iLedClock (core controls + clock opcode scaffolding)
- CoolLEDX / CoolLEDS (detected and capability-scaffolded; command parity incomplete)

## Build
```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

## Permissions model
- Android 12+: `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`
- Android <= 11: classic Bluetooth permissions + `ACCESS_FINE_LOCATION` for scan compatibility

## Implemented features
- BLE scan with `FFF0` service filter
- Connect + notify setup on `FFF1`
- MTU request path
- Protocol frame encode/decode and escaping
- Password check/set command encoding
- Basic controls (power, brightness, rhythm/mic/music selector, mirror/rotate value)
- Program/OTA transfer primitives (start header + chunk packets)
- Debug UI showing state, MTU, parsed packets, and raw device list

## Feature support matrix
| Area | Status |
|---|---|
| BLE scanning/connection/session | Implemented |
| Family detection/capabilities | Implemented |
| Basic controls | Implemented |
| Password check/set | Implemented |
| Program chunk transport | Implemented |
| OTA transport primitives | Implemented (safety-gated by docs only) |
| Full UX/iLedClock preset catalogs | Partial |
| Full response parser parity | Partial |
| Exact LZSS parity | Partial (TODO/UNRESOLVED) |

## Known gaps
- Full parity for large preset tables and grouped start-header subtypes requires finishing extraction from bulky decompiled utility tables.
- End-to-end transfer retry state machine/ack handling requires physical device verification.
- Parser currently decodes frame/opcode shells, not every family-specific payload schema.
