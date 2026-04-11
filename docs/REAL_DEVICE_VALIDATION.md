# Real Device Validation

## Highest-risk areas requiring hardware checks
1. **Transfer timing and retry behavior** on noisy BLE links (all families).
2. **Advanced program content playback** on CoolLEDX/CoolLEDS.
3. **Typed iLedClock advanced start-header classes** (`programType` branches).
4. **Clock reset subcommands** (`0F 02`, `10 02`) behavior consistency.

## Manual validation flow

### 1) Baseline connection + capabilities
1. Launch app and scan.
2. Connect one device per family (`CoolLEDUX`, `CoolLEDX`, `CoolLEDS`, `iLedClock`).
3. Verify displayed family and capability summary.
4. Verify `State=READY` and MTU display.

Inspect:
- debug event feed lines containing `Device family=` and capability dump.
- raw TX/RX entries around service readiness.

### 2) Core control sanity
1. Send power on/off.
2. Send brightness.
3. Send mirror and color-mode.

Inspect:
- parsed responses for known opcodes.
- unknown fallthrough lines (should be absent for standard acks).

### 3) Transfer robustness exercises
1. Start a program upload.
2. While active, toggle timeout/cancel controls.
3. Disconnect during active transfer and reconnect.

Inspect:
- transfer state line transitions (`AwaitingStartAck` -> `SendingChunk` -> completion/failure/cancelled).
- debug lines `Transfer cleanup on disconnect`.
- no unbounded retry loop (state must converge to completed/failed/cancelled).

### 4) iLedClock advanced controls
1. Run countdown start/reset and stopwatch start/reset.
2. Query alarms/reminders/temp-humidity/tomato.
3. Upload a text program with iLedClock selected.

Inspect:
- parsed timeline for clock response models.
- unknown packet entries that may indicate missing parser branches.

### 5) CoolLEDX/CoolLEDS content validation
1. Connect CoolLEDX then CoolLEDS device.
2. Send text program upload and color-mode command.
3. Confirm rendered output on panel.

Inspect:
- start header opcode (`02` or `1A`) and chunk packet stream in raw TX logs.
- progress/ack behavior and final transfer state.

## Logs/screens to capture
- Family/capability summary line.
- Raw TX/RX timeline around transfer start and retry/error moments.
- Parsed timeline lines for unknown/parse-error packets.
- Final transfer state for each scenario.

## Still-unconfirmed behaviors
- Exact semantic meaning of all advanced `programType` values beyond known trailer formats.
- Full OEM parity for complex animation/gif/grouped content classes.
- Device-specific timeout thresholds for best throughput without retransmit storms.
