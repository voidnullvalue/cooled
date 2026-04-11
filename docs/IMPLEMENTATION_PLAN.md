# CoolLED Native Reimplementation Plan

## Phase 1 – Evidence extraction (completed)
Sources reviewed:
- `base_apk_bluetooth_spec.md`
- `base_apk_bluetooth_spec_reimplementation.md`
- `base_apk_bluetooth_clone_spec.md`
- `base_apk_protocol_sources.zip` (decompiled `DeviceManager`, `CoolledM/U/UX`, `ILedClock` helpers)

Confirmed protocol facts captured for implementation:
- BLE service `FFF0`, command/notify characteristic `FFF1`, CCCD `0x2902`.
- Scan name families include `CoolLEDM`, `CoolLEDU`, `CoolLEDUX`, `CoolLEDX`, `CoolLEDS`, `iLedClock` and additional iLed variants.
- Framing: `01` + escaped(`len_hi len_lo payload`) + `03`.
- Escape rule: `01/02/03 -> 02 (byte XOR 04)`.
- Length field: payload bytes only, big-endian.
- Chunk packet format and XOR tail.
- CRC variant (poly `0x04C11DB7`, init `0xFFFFFFFF`, custom MSB-first loop).
- Password check/set obfuscation (`0D` / `0E`, random mask, nibble xor, xor tail).
- MTU target 247 for modern families, fallback behavior and chunk sizing evidence.

## Phase 2 – Protocol core (implemented)
- Centralized constants and UUIDs.
- Frame encode/decode and escape/unescape.
- CRC implementation per decompiled routine.
- Command builders for confirmed common opcodes.
- Chunk splitter + chunk body builder.
- Family detector and capability map.
- Parser scaffold for framed inbound payload dispatch.

## Phase 3 – BLE transport/session (implemented, with fake + real)
- Transport abstraction independent from protocol.
- `AndroidBleTransport` using only `android.bluetooth.*` / `android.bluetooth.le.*`.
- Notification enable on `FFF1` with CCCD write.
- MTU request support.
- Scan filtering on `FFF0`.
- `FakeBleTransport` for offline/integration testing and UI development.

## Phase 4 – UI (implemented baseline)
- Scanner + connect list.
- Connection state + MTU display.
- Basic controls (power, brightness, rhythm/music/mic, mirror/rotate value path).
- Password check/set controls.
- Debug section with parsed RX summary.

## Phase 5 – tests/docs/build (implemented)
- Unit tests: CRC, frame encode/decode, chunk format, chunk split, parser.
- Architecture/protocol/testing docs.
- README with feature matrix and unresolved gaps.

## Confirmed vs unresolved implementation policy
- Confirmed behavior is implemented in code paths.
- Complex family-specific payload tables (e.g., full UX/iLedClock color preset tables and extended grouped program headers) are documented and marked TODO/UNRESOLVED instead of guessed.
- LZSS currently uses conservative pass-through fallback pending full parity reconstruction from large decompiled token writer methods.
