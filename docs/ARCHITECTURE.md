# Architecture

## Module/component layout (single module package separation)
- `core/ble`: BLE transport abstraction + Android/native implementation + fake transport.
- `core/protocol`: UUID/constants, frame codec, command builders, payload parser.
- `core/crc`: custom CRC implementation from APK evidence.
- `core/compression`: LZSS hook (currently documented fallback).
- `core/model`: family/capability model.
- `core/logging`: TX/RX structured hex logging.
- `data/repositories`: protocol+transport orchestration API for ViewModel.
- `data/persistence`: remembered device store scaffold.
- `ui`: app view model and Compose UI.

## BLE session lifecycle
1. Start scan with `FFF0` filter.
2. Connect selected address.
3. Discover services.
4. Enable notification on `FFF1` + write CCCD.
5. Request MTU (247 target).
6. State enters `READY`.
7. Commands are protocol-built then written.
8. Notify frames are parsed and exposed to UI/debug streams.

## Upload/transfer lifecycle
1. Build source bytes.
2. Compress (LZSS hook).
3. Build start header (`02` / `1A` / `FE` family-specific).
4. Split compressed bytes into 1024-byte chunks.
5. Build chunk packets (`03` program / `FF` OTA) with XOR tail.
6. Transmit queue (retry/ack-state machine pending full parity extraction).
