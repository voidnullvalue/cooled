# Testing

## Without hardware
- `AppViewModel` uses `FakeBleTransport` by default.
- Fake transport supports scripted RX payloads/raw frames for transfer and parser-path replay.

Commands:
```bash
gradle testDebugUnitTest
```

Current unit coverage includes:
- frame codec and escaping round-trips
- CRC determinism
- chunk packet structure + XOR validation
- parser vectors (password/device/transfer/alarm/reminder/temp-humidity)
- malformed/unknown parser fallthrough handling
- LZSS encode/decode and literal-flag vector checks
- family header behavior (`02` typed trailer + `1A` alternate)
- advanced clock/mode builder opcode checks (`13 03`, `0F 02`, `10 02`)
- `ProgramComposer` compressed packaging checks
- transfer state-machine transitions (success, nack retries, exhaustion)
- fake transport scripted RX + raw I/O event behavior

## With hardware
See `docs/REAL_DEVICE_VALIDATION.md` for step-by-step real-device flows.

## Known environment caveat in this repo container
- Gradle wrapper (`./gradlew`) is not present.
- `gradle testDebugUnitTest` may still fail without a configured Android SDK.
