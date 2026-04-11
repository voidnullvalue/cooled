# Testing

## Without hardware
- `AppViewModel` uses `FakeBleTransport` by default.
- Scan returns deterministic sample families.
- Fake transport supports scripted RX payloads to replay parser and transfer flows.

Commands:
```bash
./gradlew testDebugUnitTest
```

Coverage in unit tests now includes:
- frame codec and escaping round-trips
- CRC determinism
- chunk packet structure + XOR validation
- typed parser vectors (password/device/transfer/alarm/reminder/temp-humidity)
- LZSS encode/decode parity checks
- family-specific typed start-header trailer verification
- transfer state machine transitions
- clock command opcode builder checks

## With hardware
1. Switch transport wiring to `AndroidBleTransport`.
2. Grant Bluetooth runtime permissions.
3. Scan and connect to `CoolLED*` / `iLedClock*` targets.
4. Validate in order:
   - notify readiness
   - MTU negotiation
   - info query response
   - power + brightness + mode controls
   - password check/set
   - clock class queries (alarm/reminder/night/tomato/temp)
   - transfer start/chunk ack progression

## Known BLE caveats
- Android BLE callbacks are asynchronous and OEM-fragile; reconnection and write queue robustness still require on-device soak testing.
- Some devices may require stricter inter-packet spacing than current generic path.
