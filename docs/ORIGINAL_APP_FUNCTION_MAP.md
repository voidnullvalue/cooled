# Original CoolLED APK function map

Source APK inspected locally: `base(1).apk`.

This document tracks the original Android app classes/functions that need to be matched or ported into this Kotlin reimplementation. The APK contains several thousand generated/support classes; this map focuses on the proprietary BLE/protocol/content-generation surface.

## Primary implementation classes

### `com.jtkj.led1248.light.device.DeviceManager`
Central BLE/protocol orchestration class. This is the main source of truth for CoolLEDM/CoolLEDU/CoolLEDUX/CoolLEDX/iLedClock transport behavior.

Important methods found:

- `init()`
- `initBleManager()`
- `startSearchDevice()`
- `connectDevice(BleDevice)`
- `disConnectDevice(BleDevice)`
- `setMtu(BleDevice)`
- `sendDataToDevice()`
- `monitorSendData(List)`
- `processMessage(Message)`
- `clearAllMessages()`
- `addDataStrings(List)`
- `setBleResponse(BleDevice, String, String)`
- `saveDeviceConnectSuccess(BleDevice)`
- `deleteDeviceDisconnectOrConnectFailure(BleDevice)`
- `checkPassWordAfterConnectSuccess(BleDevice)`
- `getDeviceInfo(BleDevice)`
- `updateDeviceInfo(BleDevice)`

Device-family response parsers:

- `checkCoolLEDMMessages(List)`
- `checkCoolLEDUMessages(List)`
- `checkCoolLEDUXMessages(List)`
- `checkCoolLEDXMessages(List)`
- `checkILedClockMessages(List)`

Transfer/retry state machines:

- `sendCoolledmStartSendProgramData(List, int, int)`
- `sendCoolleduStartSendProgramData(List, int, int)`
- `sendCoolleduStartSendProgramData(List, int, int, int)`
- `sendCoolleduxStartSendProgramData(List, int, int)`
- `sendILedClockStartSendProgramData(List, int, int)`
- `sendCoolledmProgramData(int)`
- `sendCooleduProgramData(int)`
- `sendCooleduxProgramData(int)`
- `sendILedClockProgramData(int)`
- `retrySendCoolledmStartSendProgramData(List, int, int)`
- `retrySendCoolleduStartSendProgramData(List, int, int)`
- `retrySendCoolleduxStartSendProgramData(List, int, int)`
- `retrySendILedClockStartSendProgramData(List, int, int)`
- `checkRetryTimesSendStartSendProgramDataForCoolLEDM()`
- `checkRetryTimesSendStartSendProgramDataForCoolLEDU()`
- `checkRetryTimesSendStartSendProgramDataForCoolLEDUX()`
- `checkRetryTimesSendStartSendProgramDataForILedClock()`
- `checkRetryTimesSendProgramDataForCoolLEDM()`
- `checkRetryTimesSendProgramDataForCoolLEDU()`
- `checkRetryTimesSendProgramDataForCoolLEDUX()`
- `checkRetryTimesSendProgramDataForILedClock()`

OTA transfer paths:

- `sendCoolleduStartSendOTAUpgradeData()`
- `sendCoolleduxStartSendOTAUpgradeData()`
- `sendILedClockStartSendOTAUpgradeData()`
- `sendCooleduOtaUpgradeData(int)`
- `sendCooleduxOtaUpgradeData(int)`
- `sendILedClockOtaUpgradeData(int)`
- `checkRetryTimesSendCooleduOtaUpgradeData()`
- `checkRetryTimesSendCooleduxOtaUpgradeData()`
- `checkRetryTimesSendILedClockOtaUpgradeData()`
- `checkRetryTimesSendCoolleduStartSendOTAUpgradeData()`
- `checkRetryTimesSendCoolleduxStartSendOTAUpgradeData()`
- `checkRetryTimesSendILedClockStartSendOTAUpgradeData()`

Event handlers that map directly to app features:

- `onEvent(BrightCoolLEDMSetEvent)`
- `onEvent(BrightCoolLEDUSetEvent)`
- `onEvent(BrightCoolLEDUXSetEvent)`
- `onEvent(BrightDataEvent)`
- `onEvent(CoolLEDMSwitchEvent)`
- `onEvent(CoolLEDUSwitchEvent)`
- `onEvent(CoolLEDUXSwitchEvent)`
- `onEvent(MirrorCoolLEDMSetEvent)`
- `onEvent(MirrorCoolLEDUSetEvent)`
- `onEvent(MirrorSetEvent)`
- `onEvent(RotateCoolLEDUXSetEvent)`
- `onEvent(MusicDataEvent)`
- `onEvent(RhythmEvent)`
- `onEvent(SpeedDataEvent)`
- `onEvent(SendSpeedEvent)`
- `onEvent(ModeDataEvent)`
- `onEvent(TextDataEvent)`
- `onEvent(TextIconEvent)`
- `onEvent(Icon536DataEvent)`
- `onEvent(UxColorEvent)`
- `onEvent(GetDriveStateEvent)`
- `onEvent(SetDriveStateEvent)`
- `onEvent(SetDriveStateCoolleduProgramDataEvent)`
- `onEvent(GetTimerSwitchEvent)`
- `onEvent(SetTimerSwitchEvent)`
- `onEvent(ScoreBoardEvent)`
- `onEvent(StopWatchEvent)`
- `onEvent(CountDownEvent)`
- `onEvent(SynchronizeTimeEvent)`
- `onEvent(CheckPasswordEvent)`
- `onEvent(SetPasswordEvent)`
- `onEvent(GetCoolLEDUDeviceOTAVersionEvent)`
- `onEvent(GetCoolLEDUXDeviceOTAVersionEvent)`
- `onEvent(GetDeviceCoollEDMInfoEvent)`
- `onEvent(GetDeviceCoollEDUInfoEvent)`
- `onEvent(GetDeviceCoollEDUXInfoEvent)`
- `onEvent(SetDeviceInfoEvent)`

### `com.jtkj.led1248.light.utils.CoolledUXUtils`
CoolLEDUX packet/content builder. This is the first major port target because the current reimplementation still uses a toy `ProgramContent.Text` payload and the device rejects the start header for text upload.

Critical methods:

- `getDataResult(CoolleduxProgram, int)`
- `getDataResult(CoolleduxProgram, int, int)`
- `getDataResult(CoolleduxProgram, int, int, int)`
- `getDataResult(CoolleduxProgram, int, int, int, int, int)`
- `getDataForProgram(CoolleduxProgram)`
- `getDataForCombineProgram(CoolleduxCombineProgram)`
- `getDataPacket(List, String)`
- `getDataPacket(List, String, int)`
- `convertData(List, int)`
- `convertEnd(List)`
- `getSwitchData(boolean)`
- `getBrightData(int)`
- `getBrightData(int, int)`
- `getMirrorData(int)`
- `getMirrorData(int, int)`
- `getMusicData(int)`
- `getRhythmData(int)`
- `getModeData(int)`
- `getSpeedData(int)`
- `getSendSpeedData(int)`
- `getColorData(List)`
- `getSetDeviceInfo(int, boolean)`
- `getSetDriveState(int)`
- `getDriveState()`
- `getCheckPasswordData(String)`
- `getSetPasswordData(String)`
- `getSynchronizeTimeData()`
- `getTimerSwitch()`
- `setTimerSwitch(List)`
- `getStopWatchStatus()`
- `getStopWatchStartOrStop(boolean)`
- `getStopWatchReset(int)`
- `getCountDownStatus()`
- `getCountDownStartOrStop(boolean)`
- `getCountDownReset(int, int, int)`
- `getScoreboardStartOrStop(boolean)`
- `getScoreboardReset(List)`

Program/content builders:

- `getDataWithTextCombineProgram(CoolleduxTextProgramContent)`
- `getDataWithTextCombineProgram(int, int, int, int, int, int, List, String)`
- `getDataWithDynamicTextCombineProgram(CoolleduxDynamicTextProgramContent)`
- `getDataWithAnimationCombineProgram(CoolleduxAnimationProgramContent)`
- `getDataWithAnimationCombineProgram(CoolleduxGifAnimationProgramContent)`
- `getDataWithAnimationCombineProgram(int, int, int, int, int, int)`
- `getDataWithAnimationCombineProgram(int, int, int, int, int, String)`
- `getDataWithAnimationCombineProgramEncryped(int, int, int, int, int, String)`
- `getDataWithClockCombineProgram(CoolleduxClockProgramContent)`
- `getDataWithDateCombineProgram(CoolleduxDateProgramContent)`
- `getDataWithFrameCombineProgram(CoolleduxFrameProgramContent)`
- `getDataWithScoreBoardCombineProgram(CoolleduxScoreBoardProgramContent)`
- `getDataWithTemperatureCombineProgram(CoolleduxTemperatureProgramContent)`
- `getDataWithTimeCountCombineProgram(CoolleduxTimeCountProgramContent)`

Encoding/compression/checksum helpers:

- `CoolledUXUtils.CrcCode.getCrc32CheckCode(byte[])`
- `CoolledUXUtils.CrcCode.getCrc32CheckCode2(byte[])`
- `CoolledUXUtils.CrcCode.getCrcCode(List)`
- `CoolledUXUtils.LzssCompress.lazssCompress(byte[])`
- `CoolledUXUtils.LzssCompress.getLzssCompressData(List)`
- `CoolledUXUtils.LzssCompress.InitTree()`
- `CoolledUXUtils.LzssCompress.InsertNode(int)`
- `CoolledUXUtils.LzssCompress.DeleteNode(int)`

### `com.jtkj.led1248.light.utils.FontUtils`
Font/bitmap content generator. Needed for real text upload; the original app does not send plain UTF-8 text as the display program.

Key methods:

- `readFontData(char, int)`
- `readUnicode8(char)` / `readUnicode8Bold(char)`
- `readUnicode1236(char)`
- `readUnicode1248(char)`
- `readUnicode16(char)` / `readUnicode16Bold(char)`
- `readUnicode3214(char)` / `readUnicode3214Bold(char)`
- `readUnicode3216(char)` / `readUnicode3216Bold(char)`
- `readUnicode3224(char)` / `readUnicode3224Bold(char)`
- `readUnicode3232(char)` / `readUnicode3232Bold(char)`
- `splitBytes(byte[], int)` / `splitBytes(byte[], int, int)`
- `splitArray(byte[], int)`
- `addAllSpiltedBytes(List)` / `addAllSpiltedBytes(List, int)`
- `mirror(byte[])` / `mirror(byte[], int)`
- `rotate(int, byte[])` / `rotate(int, byte[], int)`
- `rotate90Degree(byte[])` / `rotate90Degree(byte[], int)`
- `flip180(byte[], int, int)`
- `deleteEmptyColumnFor8/12/14/16/20/24/32(...)`
- `addEmptyColumnForData8/16/24/32...(...)`
- `addStaticDataForCoolledux8/12/14/16/24/32(...)`

## Immediate port order

1. Replace toy text upload with a real CoolLEDUX text-program pipeline:
   - model `CoolleduxProgram`
   - model `CoolleduxTextProgramContent`
   - port `FontUtils` glyph extraction for the installed `assets/UNICODE*` font tables
   - port `CoolledUXUtils.getDataWithTextCombineProgram(...)`
   - port `CoolledUXUtils.getDataResult(...)`
2. Replace guessed start-header composition with the original `beginDataForProgram` output generated by `CoolledUXUtils.getDataResult(...)`.
3. Keep staged ACK-driven chunking already added in this branch, but feed it original-app packet data instead of the toy compressed payload.
4. Port CoolLEDUX control commands next: switch, brightness, color, mirror/rotate, rhythm/music/mic, timers, countdown, stopwatch, scoreboard.
5. Then port iLedClock-only features: alarms, reminders, night mode, tomato clock, temp/humidity, OTA.

## Current branch status

The branch already has working BLE scan/connect/GATT/notify/write for CoolLEDUX, and the app reaches `READY`. The remaining failure is application-layer payload generation: text upload start is rejected because the current reimplementation still does not generate the original app's `CoolledUXUtils` program data.