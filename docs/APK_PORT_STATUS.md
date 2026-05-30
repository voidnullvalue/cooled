# APK port status

## Current pass status

- Branch setup: local repository did not contain a remote named `origin`; a local `apk-exact-port` branch was created from the checked-out worktree.
- Build environment: Gradle must run on JDK 17 in this container (`JAVA_HOME=$(mise where java@17.0.2)`). The default JDK 25 causes Kotlin/Gradle script parsing to fail before configuration.
- Android SDK: the container initially lacked an SDK; Android command-line tools plus API 35/build-tools were installed under `/opt/android-sdk` for validation. Use `ANDROID_HOME=/opt/android-sdk` with the JDK 17 `JAVA_HOME` shown above.

## Validation

- `ANDROID_HOME=/opt/android-sdk JAVA_HOME=$(mise where java@17.0.2) ./gradlew test` passes.
- `ANDROID_HOME=/opt/android-sdk JAVA_HOME=$(mise where java@17.0.2) ./gradlew assembleDebug` passes.

## Implemented in this pass

- Replaced the generic text body for advanced CoolLEDUX-family devices with a port-shaped CoolLEDUX program-body pipeline in `ProgramContent.kt`:
  - `getDataWithTextContentProgramContent(...)`
  - `getDataWithTextCombineProgram(...)`
  - `getDataForCombineProgram(...)`
  - `getDataForProgram(...)`
  - `getDataWithProgram(...)`
  - `getDataResult(...)`
- Added unit-test coverage for the recovered text-content block layout and `getDataWithProgram(...)` wrapper structure.
- Added reverse-engineering notes with exact source-function mapping and remaining unknowns.

## Exact / approximate matrix

- Exact from recovered APK: CoolLEDUX text-content framing, content-block length prefix, text combine type dispatch, program wrapper, and playlist index/count/showCount placement.
- Still approximate: `FontUtils.getFontByteDataCoolleduxForEmoji(...)` glyph generation when recovered glyph bytes are not supplied. The port accepts `glyphBytes` verbatim to support future golden vectors.
- Not yet ported: exact icon/image/GIF asset transforms, template content generators, and deterministic scan-record parsing offsets.

## Constraints preserved

- No Google Play Services, Firebase, ads, analytics, store/update prompts, or account/cloud features were added.
- Existing BLE command, frame, compression, and transfer code was not removed.
