# Testing

## Without hardware
- Use `FakeBleTransport` default wiring in `AppViewModel`.
- Scan returns deterministic sample families.
- Writes are looped back as RX for parser/debug validation.

Commands:
```bash
./gradlew testDebugUnitTest
```

## With hardware
1. Swap dependency injection from `FakeBleTransport` to `AndroidBleTransport`.
2. Grant Bluetooth runtime permissions.
3. Scan and connect to `CoolLED*` / `iLedClock*` targets.
4. Validate in order:
   - notify readiness
   - MTU negotiation
   - info query response
   - power + brightness + mode controls
   - password check/set
   - chunk transfer primitives (start + data)

## Known BLE caveats
- Android BLE callbacks are asynchronous and OEM-fragile; reconnection and write queue robustness require on-device soak testing.
- Some devices may require stricter inter-packet spacing than current generic path.
