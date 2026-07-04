# Testing

## Status
Current test coverage and execution caveats audited against repo + local command results.

## Unit tests in repo
Unit tests are under `app/src/test/java/com/cooled/core/protocol` and currently cover:
- `FrameCodec` encode/decode and escaping roundtrip.
- `CoolLedCrc` determinism check.
- Chunk splitting and chunk XOR-tail packet validation.
- Parser vectors for password/device/transfer/clock families.
- Unknown/malformed parser fallthrough behavior.
- `LzssCodec` roundtrip and literal-flag vector behavior.
- Family-aware start header behavior (`02` typed trailer and `1A` alternate opcode case).
- Command opcode checks for advanced mode/reset paths.
- `TransferStateMachine` success and retry-exhaustion transitions.
- `ProgramComposer` package/chunk generation.
- `FakeBleTransport` scripted response and I/O direction behavior.

## Local commands
```bash
java -version
./gradlew testDebugUnitTest
```

## Current container result (2026-07-04)
`./gradlew testDebugUnitTest` now runs end-to-end and **passes: 69 tests, 0 failures**, including
the new `FontBitmapRotationTest`. Two environment blockers were fixed to get here (this is an
aarch64 proot/Termux container, not a normal Linux desktop):

1. **Android SDK not configured.** A working SDK checkout already existed on this machine at
   `/termux-home/lib/android-sdk-9123335` (platforms 24/28/35/36, build-tools, licenses all
   present) but nothing pointed Gradle at it. Fixed with a local (gitignored) `local.properties`:
   `sdk.dir=/termux-home/lib/android-sdk-9123335`.
2. **aapt2 can't run natively.** Google only publishes `aapt2` for `linux-x86_64`, `osx`, and
   `windows` - there is no `linux-aarch64` build, and this device is aarch64. The Termux-packaged
   native aarch64 `aapt2` exists but is too old to parse the `android-35`/`android-36` resource
   table format. Fix (same approach already used by a sibling project on this machine, see
   `/root/icsee-local-camera/BUILDING_IN_PROOT.md`): extract the **exact AGP-version-matched**
   x86_64 `aapt2` binary from the cached Maven artifact
   (`com.android.tools.build:aapt2:8.7.3-12006047:linux`, a zip containing the binary) into
   `toolchain/aapt2-bin/aapt2-x86_64`, and run it under `qemu-x86_64-static` via a wrapper script
   named exactly `aapt2` (AGP's `android.aapt2FromMavenOverride` validates the override path
   literally ends in the filename `aapt2`). `qemu-x86_64-static` and the amd64 glibc it needs
   (`/usr/lib/x86_64-linux-gnu`) were already installed on this machine.

   **This override must not go in the project's own `gradle.properties`** - real CI runners and
   most contributors' machines are plain x86_64/arm64-with-native-aapt2 and would be broken by an
   absolute path into this specific container's `toolchain/` directory. It's set instead in this
   machine's `$GRADLE_USER_HOME/gradle.properties` (`~/.gradle/gradle.properties`, not committed
   anywhere), which Gradle reads as a machine-local default that the project's own
   `gradle.properties` would override if it ever needed to (it doesn't). On a normal x86_64
   Linux/macOS/Windows dev machine or in CI, no override is needed at all - the stock
   Maven-distributed aapt2 just works.

Neither `local.properties` nor the extracted `aapt2-x86_64` binary is committed (machine-local /
large binary respectively - see `.gitignore`); the wrapper script at `toolchain/aapt2-bin/aapt2`
is committed since it's portable across any aarch64-without-native-aapt2 environment that chooses
to point their own `~/.gradle/gradle.properties` at it.

## Expected toolchain
- JDK `17` (this container has `17.0.19`, not `25.0.1` as an earlier pass recorded - the JDK
  itself was never actually the blocker once SDK/aapt2 were fixed)
- AGP `8.7.3`
- Gradle wrapper `8.9` (wrapper files are present)
- Android SDK/API 35+36 installed and discoverable via `local.properties`

## `assembleDebug` also verified (2026-07-04)

`./gradlew assembleDebug` succeeds end-to-end and produces a real, installable
`app/build/outputs/apk/debug/app-debug.apk` (~108MB, dominated by the extracted original-APK
asset tree under `app/src/main/assets/coolled-original/`). One more environment fix was needed
beyond the SDK/aapt2 ones above:

- **No UTF-8 locale configured** (`locale` reports `LC_CTYPE=POSIX`, only `C.utf8` is installed,
  `LANG`/`LC_ALL` are unset). Some extracted original-APK assets have CJK filenames (e.g.
  `.../pdf/user manual/iledbike/iLedBike使用说明书.pdf`). Under the `POSIX` locale, the JVM's
  default platform charset mangles these into `?`-filled paths when Gradle's `mergeDebugAssets`
  task walks the asset tree, so it computes a hash for a path that then doesn't exist on disk and
  fails with `Failed to create MD5 hash for file '...iLedBike???????????????.pdf' as it does not
  exist`. Fix: run Gradle with `LANG=C.utf8 LC_ALL=C.utf8` set, e.g.
  `LANG=C.utf8 LC_ALL=C.utf8 ./gradlew assembleDebug`. `testDebugUnitTest` does not touch the
  asset merge task so it doesn't need this, but any assemble/bundle/lint task that packages
  assets does.

## Hardware validation
Unit tests do not replace on-device parity checks. Use `docs/REAL_DEVICE_VALIDATION.md` for hardware coverage.

## CI (2026-07-04)

`.github/workflows/ci.yml` runs `testDebugUnitTest` then `assembleDebug` on every push/PR to
`main`, on GitHub's stock `ubuntu-latest` runners - no aapt2 override needed there (see above),
just `LANG`/`LC_ALL=C.UTF-8` for the same CJK-filename reason as local `assembleDebug` runs. Debug
APK and test reports are uploaded as workflow artifacts.

`.github/workflows/release.yml` runs on pushing a `v*` tag (or manual dispatch with an existing
tag): same build, then publishes a GitHub Release with the debug-signed APK attached via
`softprops/action-gh-release`, using the workflow's built-in `GITHUB_TOKEN` - no personal access
token needed. The APK is the debug build (Android's auto-generated debug keystore), not a
release-signed one; there's no release signing config/keystore secret set up yet, so this is what
"grab an installable APK from a release" means for now.
