# Architecture

## Status
Concise architecture snapshot audited against current code.

## Module/component layout
- `core/ble`: BLE transport contract (`BleTransport`), Android GATT implementation (`AndroidBleTransport`), and scripted fake transport (`FakeBleTransport`) with raw I/O events.
- `core/protocol`: framing codec, command builders, typed parsers, transfer state machine, program content/composer.
- `core/crc`: custom CRC routine used by transfer start headers.
- `core/compression`: LZSS codec (tokenized compress/decompress, LSB-first flags).
- `core/model`: family detector + capability map.
- `data/repositories`: protocol/transport orchestration API used by UI.
- `data/persistence`: remembered-device store.
- `ui`: `AppViewModel` state/event wiring; Compose UI in `MainActivity`.

## Runtime wiring in this branch
- `AppViewModel` currently instantiates `FakeBleTransport` directly.
- `AndroidBleTransport` is implemented but not currently selected by app runtime wiring.

## BLE session lifecycle (implemented in Android transport)
1. Scan by service UUID `FFF0`.
2. Connect and discover services.
3. Enable notifications on `FFF1` and write CCCD.
4. Request MTU (`247` target).
5. Transition to `READY` state.
6. Send framed writes and receive notify frames.
7. Emit raw TX/RX `BleIoEvent` timeline and parse RX frames.

## Upload/program lifecycle (implemented wiring)
1. Create `ProgramContent` (`Text`/`Drawing`/`PresetMode`).
2. Encode content body in `ProgramComposer`.
3. Compress with `LzssCodec`.
4. Build start header (`02`/`1A`; `FE` for OTA path).
5. Split compressed bytes and build chunk frames (`03`/`FF`).
6. Process parsed transfer acks through `TransferStateMachine`.
7. Surface state transitions (`AwaitingStartAck`, `SendingChunk`, `Completed`, `Failed`, `Cancelled`) in ViewModel/UI.

## Observability path
- Transport emits raw timestamped TX/RX events.
- ViewModel appends raw timeline lines plus parsed-model lines.
- Unknown and parse-error payloads are explicit in event text.

## Build baseline (from Gradle files)
- AGP `8.7.3`, Kotlin `2.0.21`, Gradle wrapper `8.9`, Java 17 target.
