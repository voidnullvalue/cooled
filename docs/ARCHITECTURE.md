# Architecture

## Module/component layout
- `core/ble`: BLE transport abstraction + Android implementation + fake scripted transport + raw I/O events.
- `core/protocol`: frame codec, command builders, parser, transfer state machine, content/program composer.
- `core/crc`: custom CRC implementation from APK evidence.
- `core/compression`: LZSS codec with recovered token format and confirmed LSB flag order.
- `core/model`: family/capability model.
- `core/logging`: Android log helpers.
- `data/repositories`: transport/protocol orchestration API for ViewModel.
- `data/persistence`: remembered-device persistence.
- `ui`: app view model + Compose UI controls/debug feed.

## BLE session lifecycle
1. Start scan with `FFF0` filter.
2. Connect selected address.
3. Discover services.
4. Enable notification on `FFF1` + write CCCD.
5. Request MTU (247 target).
6. Enter `READY`.
7. Write framed protocol commands.
8. Capture raw RX/TX timeline events and parse RX into typed payloads.

## Upload/program lifecycle
1. Build typed content (`ProgramContent`).
2. Encode content body (`ProgramComposer`).
3. Compress via LZSS.
4. Build start header (`02`/`1A`/typed variants).
5. Split compressed bytes into chunks.
6. Build chunk packets (`03` program / `FF` OTA).
7. Transfer state machine tracks start ack, chunk acks, retries, completion/failure.
8. Timeout/cancel/disconnect path forces cleanup (`Cancelled`/`Failed`).

## Debug observability path
- Transport emits timestamped `BleIoEvent` for TX/RX raw frames.
- ViewModel appends raw + parsed timeline entries.
- Parser fallthrough is explicitly shown as `Unknown` / `ParseError`.
- Transfer-state transitions are visible in UI and event feed.
