# Real Device Validation

## Status
Hardware checklist only. This file does **not** claim parity has been achieved.

## Highest-risk areas requiring hardware checks
1. Transfer timing/retry behavior on unstable BLE links.
2. Advanced program rendering behavior on CoolLEDX/CoolLEDS.
3. iLedClock advanced start-header/programType branches.
4. Clock reset subcommands (`0F 02`, `10 02`) and related state responses.

## Manual validation flow

### 1) Baseline connection + capability detection
1. Launch app and scan.
2. Connect one device per family (`CoolLEDUX`, `CoolLEDX`, `CoolLEDS`, `iLedClock`).
3. Confirm reported family/capabilities and `READY` state + MTU.

Capture:
- family/capability event line
- raw TX/RX readiness lines

### 2) Core control sanity
1. Send power on/off.
2. Send brightness.
3. Send mirror and color mode (where supported).

Capture:
- parsed response lines
- any unknown/parse-error lines

### 3) Transfer robustness
1. Start program upload.
2. Exercise timeout/cancel during upload.
3. Disconnect during transfer; reconnect.

Capture:
- transfer state transitions
- cleanup-on-disconnect event
- convergence to completed/failed/cancelled (no unbounded retries)

### 4) iLedClock feature path
1. Countdown start/reset, stopwatch start/reset.
2. Query alarms/reminders/temp-humidity/tomato.
3. Upload text program with iLedClock selected.

Capture:
- parsed timeline for clock models
- unknown/parse-error events

### 5) CoolLEDX/CoolLEDS program path
1. Connect CoolLEDX and CoolLEDS devices.
2. Upload text program + send color mode.
3. Confirm rendered panel behavior.

Capture:
- start opcode and chunk stream in TX logs
- transfer ack behavior + final state

## Still-unconfirmed behaviors
- Exact semantics of all advanced `programType` values.
- Full OEM parity for complex animation/grouped content classes.
- Family-specific timeout thresholds for throughput vs retransmit stability.
