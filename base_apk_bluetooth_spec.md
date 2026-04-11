# Bluetooth Interaction Specification for `base.apk`

## Scope and confidence

This specification is based on **static reverse engineering** of the APK at `/mnt/data/base.apk`. It is not based on live packet capture or runtime instrumentation. That means:

- **High confidence**: package identity, permissions/features present in the manifest, Bluetooth library used, scan filters, service/characteristic UUIDs, the existence of notify/write/MTU/retry flows, supported device-family names, and the broad command surface.
- **Medium confidence**: some timeout/retry values and some higher-level flow details inferred from raw opcode/structure review where full decompilation was not available.
- **Lower confidence / not yet fully decoded**: exact byte-level payload schemas for every application-layer command, exact advertisement byte offsets for every device metadata field, and runtime-only branching driven by server config or device firmware responses.

---

## 1. APK identity

- **Package**: `com.jtkj.led1248`
- **Version name**: `2.7.2`
- **Version code**: `108`
- **Min SDK**: `24`
- **Compile SDK attribute present**: `36`
- **Application class**: `com.jtkj.led1248.CoolLED`

The APK is multi-dex and contains four DEX files:
- `classes.dex`
- `classes2.dex`
- `classes3.dex`
- `classes4.dex`

The Bluetooth-facing application logic is primarily in:

- `com.jtkj.led1248.light.device.*` in **`classes3.dex`**
- vendor BLE library `com.jtkj.library.fastble.*` in **`classes.dex`**

---

## 2. Manifest-level Bluetooth and related capabilities

### 2.1 Bluetooth feature
The manifest declares BLE capability through:

- `android.hardware.bluetooth_le`

### 2.2 Permissions

### Directly recovered from structured manifest parsing
- `android.permission.BLUETOOTH`
- `android.permission.BLUETOOTH_ADMIN`
- `android.permission.BLUETOOTH_SCAN`
- `android.permission.READ_EXTERNAL_STORAGE`
- `android.permission.WRITE_EXTERNAL_STORAGE`

### Present in the manifest string pool and very likely declared
These strings are embedded in the compiled manifest and strongly indicate corresponding manifest usage:

- `android.permission.ACCESS_COARSE_LOCATION`
- `android.permission.ACCESS_FINE_LOCATION`
- `android.permission.ACCESS_NETWORK_STATE`
- `android.permission.ACCESS_WIFI_STATE`
- `android.permission.BLUETOOTH_ADVERTISE`
- `android.permission.BLUETOOTH_CONNECT`
- `android.permission.BLUETOOTH_PRIVILEGED`
- `android.permission.CAMERA`
- `android.permission.FOREGROUND_SERVICE`
- `android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE`
- `android.permission.INTERNET`
- `android.permission.MODIFY_AUDIO_SETTINGS`
- `android.permission.READ_MEDIA_AUDIO`
- `android.permission.READ_MEDIA_IMAGES`
- `android.permission.RECORD_AUDIO`
- `android.permission.VIBRATE`

Practical meaning:
- The app is built to scan/connect to BLE devices on modern Android.
- It likely handles device discovery under both legacy and newer permission models.
- It also has media, storage, camera, and foreground-service capabilities, which fits an LED-control app with content upload, microphone/music modes, and possible OTA/update flows.

---

## 3. Bluetooth stack used by the APK

The app does **not** appear to talk directly to the Android BLE stack everywhere. Instead, it wraps BLE operations through an internal/vendor library:

- `com.jtkj.library.fastble.BleManager`
- `com.jtkj.library.fastble.bluetooth.BleBluetooth`
- `com.jtkj.library.fastble.bluetooth.BleConnector`
- `com.jtkj.library.fastble.bluetooth.SplitWriter`
- `com.jtkj.library.fastble.scan.BleScanner`
- `com.jtkj.library.fastble.scan.BleScanPresenter`
- `com.jtkj.library.fastble.scan.BleScanRuleConfig`
- `com.jtkj.library.fastble.data.BleDevice`

### 3.1 Consequence of this architecture
The app-level code delegates nearly all BLE transport work to `BleManager`, while application logic lives in `DeviceManager` and protocol-builder utility classes.

This separation is important:

- **FastBle layer** handles:
  - scanning
  - connecting/disconnecting
  - notifications/indications
  - reads/writes
  - MTU operations
  - split writes
  - callback routing

- **App layer** handles:
  - deciding which devices are relevant
  - auto-connect behavior
  - parsing advertisement/device identity info
  - building command payloads
  - queueing/retrying transfers
  - interpreting notify responses

---

## 4. Low-level BLE API behavior

### 4.1 Scanning API used
The embedded FastBle library uses the legacy BLE scan API:

- `BluetoothAdapter.startLeScan(...)`

`BleScanPresenter.onLeScan(BluetoothDevice, int, byte[])` wraps:
- the discovered `BluetoothDevice`
- RSSI
- raw `scanRecord`
- timestamp

into a `BleDevice` object.

### 4.2 Notification setup
`BleConnector.setCharacteristicNotification(...)` uses:
- `BluetoothGatt.setCharacteristicNotification(...)`

and writes the standard CCCD descriptor:

- **CCCD UUID**: `00002902-0000-1000-8000-00805f9b34fb`

This is standard BLE behavior and strongly confirms that the app enables notifications on the target characteristic, rather than polling.

### 4.3 Write path
`BleConnector.writeCharacteristic(...)` uses:
- `BluetoothGattCharacteristic.setWriteType(...)`
- `BluetoothGattCharacteristic.setValue(...)`
- `BluetoothGatt.writeCharacteristic(...)`

### 4.4 Split write support
The library includes:

- `SplitWriter.splitWrite(...)`

Meaning:
- if outgoing data exceeds the allowed BLE characteristic payload size, the app/library can segment it into chunks and transmit them sequentially.

This is consistent with:
- text/image/program transfers
- OTA/update flows
- larger animation/frame payloads

---

## 5. App-level BLE orchestrator

The central coordinator is:

- `com.jtkj.led1248.light.device.DeviceManager`

This class is the main Bluetooth control plane for the app.

Important methods discovered:

- `initBleManager()`
- `startSearchDevice()`
- `connectDevice(BleDevice)`
- `disConnectDevice(BleDevice)`
- `getDeviceInfo(BleDevice)`
- `setMtu(BleDevice)`
- `setBleResponse(BleDevice, String serviceUuid, String characteristicUuid)`
- `addDataStrings(List)`
- `sendDataToDevice()`
- `monitorSendData(List)`
- `processMessage(Message)`
- `saveDeviceConnectSuccess(BleDevice)`
- `setAllDeviceEnableToSendData()`
- `setOnlyTheDeviceForCheckOrSetPasswordCanSendData(BleDevice)`

This tells you the app is built around:

1. discover devices
2. connect
3. enable notify
4. optionally negotiate MTU
5. queue commands
6. send commands
7. parse notify responses
8. retry/recover if needed

---

## 6. Scan configuration and discovery policy

### 6.1 Service UUID filter
During `DeviceManager.initBleManager()`, the app builds a `BleScanRuleConfig` and applies a service UUID filter for:

- **Service UUID**: `0000fff0-0000-1000-8000-00805f9b34fb`

This is highly significant:
- The app is not scanning for arbitrary BLE devices.
- It is specifically targeting devices advertising or exposing service `FFF0`.

### 6.2 Device-name targeting
The following device names are embedded in the scan config / device matching logic:

- `CoolLED536`
- `CoolLED`
- `CoolLEDX`
- `CoolLEDA`
- `CoolLEDS`
- `CoolLEDM`
- `CoolLEDU`
- `iLedBike`
- `CoolLEDUX`
- `iDevilEyes`
- `iLedHat`
- `iLedHatC`
- `iLedOpen`
- `iLedCar`
- `iLedClock`

This strongly indicates a single application supporting multiple hardware families and form factors under a related LED-product ecosystem.

### 6.3 Scan timing / retry-related values
Static opcode inspection suggests the app configures:
- reconnect count around **5**
- reconnect interval around **1000 ms**
- connect timeout around **5000 ms**
- operation timeout around **5000 ms**

These values are plausible and consistent with the call structure seen in `initBleManager()`, but because they were inferred from opcode-level review rather than a clean Java decompile, they should be treated as **medium-confidence**.

### 6.4 Auto-connect behavior
The scan callback logic suggests the app performs its **own auto-connect decision-making** during scan results, rather than blindly using Android-level auto-connect behavior for every scan hit.

The app checks whether a scanned device "needs" auto-connect and whether it is already connected/connecting before attempting a connection.

---

## 7. Exact GATT topology used by the app

This is the most concrete part of the reverse engineering.

### 7.1 Primary service
- **Service UUID**: `0000fff0-0000-1000-8000-00805f9b34fb`

### 7.2 Primary characteristic
- **Characteristic UUID**: `0000fff1-0000-1000-8000-00805f9b34fb`

### 7.3 Transport model
The app uses the **same characteristic (`FFF1`) for both**:
- outbound application writes
- inbound notifications

That means this is effectively a **single full-duplex application channel** over:

- service `FFF0`
- characteristic `FFF1`

### 7.4 Notification enablement
After connect success, the app calls a path that ends in:

- `setBleResponse(bleDevice, FFF0, FFF1)`

which then calls:
- `BleManager.notify(...)`

This enables notifications on `FFF1`.

### 7.5 Write operations
Outgoing payloads are sent via:

- `BleManager.write(bleDevice, FFF0, FFF1, bytes, callback)`

This is explicit evidence that the write path targets `FFF1`.

### 7.6 Overall GATT contract
So the application-layer protocol appears to be:

- discover device
- connect
- enable notifications on `FFF1`
- write protocol frames to `FFF1`
- receive responses/events back as notifications from `FFF1`

That is the core Bluetooth interaction model of the APK.

---

## 8. Connection lifecycle

### 8.1 Device search
`startSearchDevice()` dispatches work to an executor and eventually invokes:
- `BleManager.scan(...)`

The scan callback posts internal `SearchDeviceEvent` objects on:
- scan start
- scan finish
- device discovery progress

### 8.2 Auto-connect during scanning
The scan callback checks something equivalent to:
- whether the device should be auto-connected
- whether the device is already connected/connecting

If eligible, it logs and initiates connection.

### 8.3 Connect entry point
`connectDevice(BleDevice)` does more than just call BLE connect:
- it checks device configuration through `OkHttpUtils.getInstance().getDeviceConfig(...)`
- it checks internal success-state / previous-connection bookkeeping
- it submits an async connect task
- it also schedules a delayed retry/watchdog runnable

This indicates the BLE connection process is intertwined with:
- app-side device policy
- possibly server-driven configuration
- retry handling

### 8.4 Connect callbacks
The observed callback flow includes:

- `onStartConnect`
- `onConnectFail`
- `onConnectSuccess`
- `onDisConnected`

#### On connect success
The app does all of the following:
- logs success
- records the successful connection
- calls `getDeviceInfo(bleDevice)`
- calls `setMtu(bleDevice)`
- posts connection/check events
- schedules `setBleResponse(bleDevice, FFF0, FFF1)`

This is important because it reveals connection sequencing:
1. transport connection established
2. device info extraction/initialization
3. MTU negotiation
4. notifications enabled
5. further protocol exchange begins

### 8.5 Disconnect / failure cleanup
On connection failure or disconnect, the app removes/updates internal state tracking and emits events.

This matters because the app does not appear stateless; it keeps per-device connection success/failure bookkeeping.

---

## 9. MTU handling

The app has an explicit MTU-management path:

- `setMtu(BleDevice)`
- `DeviceManager$5`
- `DeviceManager$5$1.onMtuChanged(int mtu)`

### 9.1 What the code clearly shows
- MTU setup is device-family aware.
- On successful MTU change, the app calls:
  - `BleManager.getInstance().setSplitWriteNum(...)`
- On MTU failure, it also calls:
  - `setSplitWriteNum(...)`

Meaning:
- split-write chunk sizing depends on MTU negotiation result or fallback logic
- the app is designed to handle devices where MTU negotiation succeeds and devices where it does not

### 9.2 Device-name-sensitive MTU handling
Branching in `setMtu()` checks names such as:
- `CoolLEDM`
- `CoolLEDU`
- `CoolLEDUX`
- `iDevilEyes`
- `iLedHat`
- `iLedHatC`

This strongly suggests different device families have different optimal packet sizes or transfer behavior.

### 9.3 Exact requested MTU
The code very likely requests a specific MTU value, but I did not fully decompile that constant into a verified exact number in this pass. So the safe statement is:

- the app explicitly negotiates MTU and adapts split-write sizing based on the result

---

## 10. Notification / inbound response path

### 10.1 Notify setup
`setBleResponse(...)` schedules a task that calls:
- `BleManager.notify(...)`

### 10.2 Notify callback behavior
The notify callback class handles:

- `onNotifySuccess()`
- `onNotifyFailure(BleException)`
- `onCharacteristicChanged(byte[])`

### 10.3 On notify success
The code:
- logs device address/name/id
- resets some password-check-related state
- triggers post-connect password checking logic

This strongly suggests the app uses an **application-layer password/authentication command**, rather than relying solely on BLE pairing/bonding.

### 10.4 On notify failure
The app logs the failure and may disconnect after retries exceed a limit.

### 10.5 On characteristic changed
The app:
- logs the MAC/device context
- logs the incoming data as hex via `LightUtils.bytesToHexString(...)`
- converts byte arrays into string-array form
- continues downstream parsing

This tells you:
- notify packets are part of a structured binary/text protocol
- the app has its own parser above raw BLE
- raw responses are important enough that developers included hex logging

---

## 11. Outbound command/write architecture

### 11.1 Queueing
The app does not write directly from every UI action. Instead, commands go through a queue-oriented path:

- `addDataStrings(List)`
- `sendDataToDevice()`
- `monitorSendData(List)`
- `processMessage(Message)`

### 11.2 Data representation before write
Command payloads are often handled internally as:
- lists of strings
- then converted to bytes using:
  - `LightUtils.fromListStringToByteArray(...)`

That strongly suggests protocol builders produce frame data in a hex-string or segmented-string format before transmission.

### 11.3 Write target
Outgoing data is written to:
- service `FFF0`
- characteristic `FFF1`

via:
- `BleManager.write(...)`

### 11.4 Write callbacks
The write callback logs:
- success: address, current chunk, total chunks
- failure: address and exception

This confirms that many transmissions are chunked multi-part writes rather than a single one-off packet.

### 11.5 Multi-device fan-out
`sendDataToDevice()` iterates enabled target devices and sends the same logical payload to one or more devices.

This is a major design point:
- the app supports **broadcasting commands/content to multiple devices**
- not every action is single-device only

### 11.6 Single-device restriction for sensitive commands
The method:
- `setOnlyTheDeviceForCheckOrSetPasswordCanSendData(BleDevice)`

indicates that password check/set operations are intentionally narrowed to a single target device.

So the app appears to distinguish:
- ordinary data/program content that may target multiple devices
- sensitive identity/security/configuration operations that must target one device only

---

## 12. Retry, resend, and transfer management

The presence of `processMessage(Message)` and related methods shows the app contains a transfer supervision layer on top of BLE.

Observed behavior includes:
- retrying OTA chunks
- retrying OTA start packets
- retrying program data packets
- retrying start-send-program packets
- device-family-specific validation paths

Referenced validation/recovery methods include:
- `recoverDataList(...)`
- `checkCoolLEDMMessages(...)`
- `checkCoolLEDUMessages(...)`
- `checkCoolLEDUXMessages(...)`
- `checkCoolLEDXMessages(...)`
- `checkILedClockMessages(...)`

Interpretation:
- the app expects devices to acknowledge or respond to writes in a structured way
- it monitors whether expected responses occur
- it retries or repairs transfer state when needed

This is more than a simple "send a command and hope" BLE client. It is a stateful transport/protocol engine.

---

## 13. Device identification and metadata extraction

The app appears to derive device characteristics from advertisement / scan data.

Methods include:
- `getDeviceColorTye(BleDevice)`
- `getDeviceRow(BleDevice)`
- `getDeviceColumn(BleDevice)`
- `getDeviceId(BleDevice)`

and raw-byte-oriented variants that operate on scan records.

### 13.1 Likely advertisement-derived properties
The app appears to infer:
- device ID
- display rows
- display columns
- color type

from BLE advertisement bytes.

This means the scan record is not just used for discovery; it is also a **metadata channel** for device capabilities/identity.

### 13.2 Simulation support
The presence of a `BleDeviceSimulator` with methods like:
- `generateScanRecord(DeviceInfo)`
- `generateRandomMacAddress()`
- `setRandomColumn()`
- `setRandomDeviceId()`

is strong additional evidence that the app’s logic depends on meaningful structured data inside the advertisement record.

---

## 14. Supported device families and product variants

The APK contains multiple device/protocol families. Important utility classes include:

- `CoolledMUtils`
- `CoolledUUtils`
- `CoolledUXUtils`
- `CoolledUDUtils`
- `Light1696Utils`
- `ILedClockUtils`

This strongly suggests that while the BLE transport is shared, the **application-layer command framing differs by device family**.

Likely product families covered include:
- CoolLED M
- CoolLED U
- CoolLED UX
- CoolLED UD / related variants
- 16x96 / Light1696-style devices
- iLedClock
- bike/car/hat/open/devil-eyes style products named in scan filters

---

## 15. Application-layer command surface

This is where the APK becomes more sophisticated than a basic LED controller. The utility classes and event handlers reveal the types of BLE commands the app can generate.

## 15.1 Authentication / access control style commands
Observed builder methods:
- `getCheckPasswordData(String)`
- `getSetPasswordData(String)`

Interpretation:
- the device protocol supports checking and setting a password over BLE
- this is likely an application-layer password, not simple BLE bonding

## 15.2 Device identity / info / status commands
Observed methods:
- `getDeviceInfo()`
- `getDeviceOTAVersion()`
- device info event handlers for multiple families

Interpretation:
- devices can be queried for identity/state/version information after connect

## 15.3 Power and switch commands
Observed methods/events:
- `getSwitchData(...)`
- `getSwitchDataString(...)`
- `SwitchDataEvent`
- family-specific switch events

Interpretation:
- the protocol includes explicit on/off or enable/disable control

## 15.4 Brightness / speed / mode / color commands
Observed methods/events:
- `getSetBrightness(...)`
- `getBrightDataString(...)`
- `BrightDataEvent`
- `SpeedDataEvent`
- `SendSpeedEvent`
- `ModeDataEvent`
- `UxColorEvent`
- `setColor(...)`
- `setColorMode(...)`
- `setColorSpeed(...)`

Interpretation:
- the app can set brightness, effect speed, effect mode, and color properties

## 15.5 Music / microphone / rhythm reactive commands
Observed methods/events:
- `getMusicDataString(...)`
- `MusicDataEvent`
- `DeviceMicEvent`
- `RhythmEvent`

Interpretation:
- at least some devices support audio-reactive or microphone-driven visual modes

## 15.6 Text / icon / drawing / graffiti / frame transfer
Observed methods/events:
- `TextDataEvent`
- `TextIconEvent`
- `DrawDataEvent`
- `Icon536DataEvent`
- utility methods for program data and sending framed content

Interpretation:
- the app transfers content payloads, not just simple mode commands
- there are likely specialized encodings for text, icons, drawings, animations, and screen programs

## 15.7 Program/playlist/composition transfer
Observed methods:
- `getSendDataWithInfo(...)`
- `getStartDataForProgram(...)`
- multiple `ProgramDataEvent` classes

Interpretation:
- the protocol supports multi-part uploads of structured “programs” or compositions to the display

## 15.8 OTA / firmware-upgrade style commands
Observed methods/events:
- `getOTAUpdate(...)`
- `getStartOTAUpdate(...)`
- `getStartDataForOtaUpgrade([B])`
- OTA upgrade events for multiple families

Interpretation:
- the app supports firmware/update-like transfers over BLE
- chunking/retry logic strongly aligns with OTA workloads

## 15.9 Mirror / rotate / orientation / drive-state commands
Observed methods/events:
- `getSetMirror(...)`
- `setRotate(...)`
- `RotateCoolLEDUXSetEvent`
- `GetDriveStateEvent`
- `SetDriveStateEvent`
- `getDriveState(...)`
- `setDriveState(...)`

Interpretation:
- displays can be oriented or mirrored
- some devices have a “drive state” concept, likely relating to animation/display behavior or vehicle-specific modes

## 15.10 Time / timer / alarm / scoreboard / stopwatch / countdown commands
Observed methods/events:
- `getSynchronizeTime()`
- `SynchronizeTimeEvent`
- `getTimerSwitch()`
- `setTimerSwitch(...)`
- `CountDownEvent`
- `StopWatchEvent`
- scoreboard methods
- extensive `ILedClockUtils` time/alarm/reminder-related methods

Interpretation:
- clock-capable devices support time sync and timer/alarm features
- scoreboard/countdown/stopwatch-capable devices have dedicated protocol commands

---

## 16. Event-driven BLE control model inside the app

`DeviceManager` contains many `onEvent(...)` handlers. These appear to be the bridge from UI/business logic into BLE protocol transmission.

Observed event families include:

- `BeginTransferEvent`
- `BrightCoolLEDMSetEvent`
- `BrightCoolLEDUSetEvent`
- `BrightCoolLEDUXSetEvent`
- `BrightDataEvent`
- `CheckDeviceEvent`
- `CheckPasswordEvent`
- `CoolLEDMProgramDataEvent`
- `CoolLEDMSwitchEvent`
- `CoolLEDUSwitchEvent`
- `CoolLEDUXSwitchEvent`
- `CoolleduOTAUpgradeEvent`
- `CoolleduProgramDataEvent`
- `CoolleduxOTAUpgradeEvent`
- `CoolleduxProgramDataEvent`
- `CountDownEvent`
- `DeviceMicEvent`
- `DrawDataEvent`
- `GetCoolLEDUDeviceInfoEvent`
- `GetCoolLEDUXDeviceInfoEvent`
- `GetCoolLEDUXDeviceOTAVersionEvent`
- `GetDeviceCoollEDMInfoEvent`
- `GetDriveStateEvent`
- `GetTimerSwitchEvent`
- `Icon536DataEvent`
- `MirrorCoolLEDMSetEvent`
- `MirrorCoolLEDUSetEvent`
- `MirrorSetEvent`
- `ModeDataEvent`
- `MusicDataEvent`
- `RhythmEvent`
- `RotateCoolLEDUXSetEvent`
- `ScoreBoardEvent`
- `SendSpeedEvent`
- `SetDeviceInfoEvent`
- `SetDriveStateCoolleduProgramDataEvent`
- `SetDriveStateEvent`
- `SetPasswordEvent`
- `SetTimerSwitchEvent`
- `SpeedDataEvent`
- `StopWatchEvent`
- `SwitchDataEvent`
- `SynchronizeTimeEvent`
- `TextDataEvent`
- `TextIconEvent`
- `UxColorEvent`

This reveals the internal architecture:
- UI or business logic emits a typed event
- `DeviceManager` maps that event to a protocol-builder utility
- the resulting frame list is queued
- the queue is written to BLE
- notification responses are parsed and routed back into state

---

## 17. Security model observations

### 17.1 Password handling is application-layer
The app clearly contains:
- password check commands
- password set commands
- post-connect password-check logic

This means the BLE security model is at least partly implemented **inside the application protocol**, not just through standard BLE pairing/bonding.

### 17.2 No evidence in this pass of strong cryptographic session establishment
In the analyzed code paths, the model looks more like:
- connect
- enable notifications
- exchange application commands including password-related frames

rather than:
- establish encrypted custom session keys
- perform a full cryptographic challenge/response protocol

That is not proof such logic does not exist elsewhere, but in this pass the evidence strongly favors a comparatively simple application-password scheme.

### 17.3 Multi-device vs single-device security handling
The existence of logic that narrows password operations to a single device indicates the developers were aware that security-related operations should not be broadcast indiscriminately.

---

## 18. Practical end-to-end behavior summary

A typical session likely looks like this:

1. App initializes FastBle manager
2. App configures scan rules:
   - target service `FFF0`
   - target product-name families
3. App starts BLE scanning
4. Each scan result is wrapped as a `BleDevice`
5. App extracts metadata from advertisement bytes:
   - ID / rows / columns / color type
6. If policy says to connect, app initiates connection
7. On connect success:
   - device state is recorded
   - device info path is triggered
   - MTU negotiation is attempted
   - notifications are enabled on `FFF1`
8. App may immediately perform application-layer password check
9. UI or internal events produce command frames via family-specific utility classes
10. Frames are queued, split if necessary, and written to `FFF1`
11. Device responses arrive as notifications on `FFF1`
12. App parses responses, tracks progress, retries if required
13. For large transfers such as program uploads or OTA, message supervision/retry logic remains active until completion or failure

---

## 19. What is still not fully specified at the byte level

The following areas would require a second pass focused specifically on protocol-frame reconstruction or live BLE capture:

- exact frame formats for each command family
- opcode values for all commands
- checksum/CRC rules, if any
- sequence-number layout for chunked transfers
- exact ack/nack semantics
- exact advertisement-byte offsets for:
  - row count
  - column count
  - color type
  - device ID
- exact MTU request value per family
- exact reconnect/timeout constants in every path
- any runtime server-config influence on BLE behavior

---

## 20. Most important hard conclusions

These are the strongest conclusions from the APK:

1. **This app is a BLE client for a family of LED display devices.**
2. **It scans for devices advertising/associated with service `FFF0`.**
3. **It uses characteristic `FFF1` as the main protocol channel for both writes and notifications.**
4. **It enables notifications via CCCD `2902` on `FFF1`.**
5. **It uses the FastBle library as the transport abstraction.**
6. **It implements a stateful command queue with chunking, retries, and transfer supervision.**
7. **It supports multiple product families with family-specific command builders.**
8. **It extracts hardware metadata from BLE advertisement bytes.**
9. **It implements password-related logic at the application protocol layer.**
10. **It supports high-level operations far beyond simple mode changes, including content transfer and OTA-like updates.**

---

## 21. Best next reverse-engineering step

If a deeper protocol spec is needed, the next highest-value step is:

1. fully decode the utility classes:
   - `CoolledMUtils`
   - `CoolledUUtils`
   - `CoolledUXUtils`
   - `CoolledUDUtils`
   - `Light1696Utils`
   - `ILedClockUtils`

2. reconstruct packet formats for:
   - password check
   - password set
   - get device info
   - set brightness
   - switch on/off
   - text/program transfer
   - OTA start/chunk/finalize

3. separately decode the advertisement parser methods:
   - `getDeviceId([B])`
   - `getDeviceRow([B])`
   - `getDeviceColumn([B])`
   - `getDeviceColorTye([B])`

That would allow conversion of this transport-level specification into a true wire-level protocol document.
