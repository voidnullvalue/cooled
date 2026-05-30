# APK reverse-engineering notes

Source inspected: `base_apk_protocol_sources.zip`, especially `com.jtkj.led1248.light.utils.CoolledUXUtils.java` and `com.jtkj.led1248.light.device.DeviceManager.java`.

## CoolLEDUX program generation recovered in this pass

The original APK builds CoolLEDUX display uploads in these stages:

1. `getDataWithTextContentProgramContent(...)`
   - Creates a length-prefixed content block.
   - Inner payload starts with content type `0x01`, seven reserved zero bytes, `layerType`, `startColumn`, `startRow`, `showWidth`, `showHeight`, `mode`, `speed`, `stayTime`, `moveSpace`, then `FontUtils.getFontByteDataCoolleduxForEmoji(...)` bytes.
   - The four-byte prefix is `inner.size + 4` in big-endian order.
2. `getDataForCombineProgram(...)`
   - Dispatches by combine-program type.
   - Text combine programs use type `3` and append text custom color, text auto color, normal text content, then optional frame content. This app currently ports the normal text-content branch.
3. `getDataForProgram(...)`
   - Concatenates encoded combine-program blocks.
4. `getDataWithProgram(...)`
   - Prepends eight zero bytes, one-byte content count, one zero byte, then `getDataForProgram(...)` bytes.
5. `getDataResult(...)`
   - Compresses `getDataWithProgram(...)` through the APK LZSS path.
   - Builds start headers with index/count/showCount and splits compressed bytes into transfer chunks.

## Start-header / playlist behavior

`DeviceManager` stores `mCoolleduxProgramList`, `mCoolleduxProgramIndex`, and `mCoolleduxProgramCount`; after a successful program ACK it advances the index and starts the next program until `mCoolleduxProgramIndex == mCoolleduxProgramCount - 1`. This confirms that index/count/showCount are playlist-level fields rather than fields inside the display-content body.

## Remaining unproven areas

- The recovered ZIP does not include `FontUtils.java`, so `FontUtils.getFontByteDataCoolleduxForEmoji(...)` is not byte-exact yet. The Kotlin port has the exact text-content framing and accepts recovered glyph bytes verbatim, but its fallback glyph generator remains approximate.
- GIF/image/icon functions are visible in `CoolledUXUtils` and show length-prefixed raw/encrypted file payload use for some animation branches, but asset-type mapping from app assets to those builders still needs more source/assets before claiming exact parity.
- Scan-record parsing offsets were not recoverable from the protocol-only ZIP in this pass.
