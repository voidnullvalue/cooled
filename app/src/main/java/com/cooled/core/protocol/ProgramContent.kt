package com.cooled.core.protocol

import com.cooled.core.assets.OriginalLedAssetByteSources
import com.cooled.core.compression.LzssCodec
import com.cooled.core.model.DeviceFamily

sealed class ProgramContent {
    data class Text(
        val text: String,
        val speed: Int,
        val effect: Int,
        val displayColumns: Int? = null,
        val displayRows: Int? = null,
        /**
         * Explicit font size override (one of 8/12/14/16/32, matching the
         * asset-backed glyph tables CoolleduxFontSources can actually read -
         * see CoolleduxGlyphPipeline). Null auto-picks the largest size that
         * fits [displayRows] (see CoolleduxProgramBytecode.text), which is
         * usually what you want, but there was previously no way for a user
         * to see or override that choice at all.
         */
        val fontSize: Int? = null
    ) : ProgramContent()

    data class OriginalAsset(
        val assetPath: String,
        val kind: String,
        val speed: Int = 255,
        val effect: Int = 2,
        val displayColumns: Int? = null,
        val displayRows: Int? = null
    ) : ProgramContent()

    data class CoolLedUxText(
        val content: CoolLedUxTextContentProgramContent
    ) : ProgramContent()

    data class Drawing(val width: Int, val height: Int, val rgbBytes: ByteArray) : ProgramContent()
    data class PresetMode(val mode: Int, val intensity: Int) : ProgramContent()

    /**
     * CoolLEDUX business-hours display template - port of
     * CoolledUXUtils.getDataWithGraffitiBusinessHourCombineProgram(
     *   DeviceManager.CoolleduxGraffitiProgramBusinessHourContent) and its
     * DeviceManager.CoolleduxGraffitiProgramBusinessHourContent field layout.
     *
     * The APK carries the pre-rasterized pixel matrices in a HashMap<String,Object>
     * `data` map; the meaningful keys are modeled here as typed fields:
     *  - businessType 0/2: a single image (`imageMatrixData`), rendered as one
     *    graffiti block sized `showHeight` high x (pixels/showHeight) wide.
     *  - businessType 1 ("minimalism"): two stacked images (`topImageMatrixData`
     *    over `bottomImageMatrixData`) with fixed per-device-size dimensions,
     *    rendered as two graffiti blocks. `minimalismShowMode` becomes the top
     *    block's mode; the bottom block's mode is a hard-coded 1.
     *  - any other businessType: no content (matches getContentNumber()==0).
     *
     * Matrix colors are flat 0xRRGGBB / 0xAARRGGBB ints in row-major order, the
     * result of toRGBList255 / toRGBList255Second flattening the APK's nested
     * List<List<List<Integer>>> / List<List<Integer>> pixel data.
     */
    data class BusinessHours(
        val businessType: Int,
        val layerType: Int = 1,
        val mode: Int = 2,
        val speed: Int = 255,
        val stayTime: Int = 3,
        val showWidth: Int = 96,
        val showHeight: Int = 16,
        val minimalismShowMode: Int = 0,
        val imageMatrixData: List<Int> = emptyList(),
        val topImageMatrixData: List<Int> = emptyList(),
        val bottomImageMatrixData: List<Int> = emptyList()
    ) : ProgramContent()

    /**
     * CoolLEDUX live clock-face display template - port of
     * CoolledUXUtils.getDataWithClockCombineProgram(DeviceManager.CoolleduxClockProgramContent)
     * (reverse/jadx/sources/com/jtkj/led1248/light/utils/CoolledUXUtils.java:2594-3681,
     * cross-checked against reverse/apktool/smali_classes3/.../CoolledUXUtils.smali
     * lines 5610-9149 - jadx `-m simple` renders this method's mode-byte dispatch
     * with dead-looking gotos to labels (`L10`/`L11`) that jadx prints ~1000 lines
     * away at the very end of the method, purely a bytecode-layout artifact, not a
     * real re-entry; smali's `:cond_*`/`:goto_*` graph resolves it unambiguously).
     *
     * Fields mirror DeviceManager.CoolleduxClockProgramContent 1:1 (defaults match
     * its constructor). `isDateShowMode` is read by the real function but never
     * consulted afterwards - confirmed via smali (the register holding it is
     * immediately clobbered by the `isSpaceShing` read before any branch), not a
     * decompiler artifact - so it is kept here only for field-layout fidelity and
     * has no effect on the encoded bytes, exactly like the APK. `showAmpm` is
     * likewise never read inside this specific builder (it presumably gates
     * whether callers populate/attach a CoolleduxClockProgramContent at all,
     * upstream of this function) and is kept for the same reason.
     *
     * `deviceRows`/`deviceColumns` stand in for the APK's DeviceManager.DEVICE_ROW/
     * DEVICE_COLUMN static globals, which the real function reads directly instead
     * of taking them as parameters.
     *
     * SCOPE (see docs/APK_REVERSE_ENGINEERING_NOTES.md, "getDataWithClockCombineProgram
     * ... scoped, not ported"): the real function is a sequence of independent
     * display-element blocks (hour digits, hour/minute separator, minute digits,
     * seconds, am/pm), each of which starts from one literal comma-separated-byte
     * digit-bitmap table and then runs a long if/else dispatch keyed on
     * (DEVICE_ROW, DEVICE_COLUMN, styleIndex) that may swap in a size/style-specific
     * variant table - confirmed dozens of such literal tables across the full
     * function. Porting every literal table is a large, error-prone hand-
     * transcription task flagged in the notes as not yet started. This port is
     * therefore intentionally scoped to exactly the DEFAULT tables (the literals
     * used whenever the device's (rows, columns) do not match ANY of the function's
     * explicit overrides) - which is a real, independently well-defined, and fully
     * smali/jadx-cross-checked subset, not a guess. `CoolleduxProgramBytecode.clock(...)`
     * throws a clear error for any (deviceRows, deviceColumns) pair that the real
     * function *would* special-case, rather than silently emitting the wrong digit
     * bitmaps for that size. Everything else - header/reserved bytes, the
     * is24HourShowMode/isSpaceShing mode-byte truth table, showTime/numHeight/
     * numWidth, per-element field order (table+color+position for hour; color+
     * position then the shared separator table for spaceHour; color+position for
     * minute/spaceMinute with the conditional separator-table reuse gated by
     * showSpaceMinuteColor; color+position for seconds/ampm; the trailing optional
     * am/pm glyph table, empty/zero-length in the supported device-size scope) is
     * byte-exact and independent of which literal table ends up selected.
     */
    data class Clock(
        val styleIndex: Int,
        val deviceRows: Int,
        val deviceColumns: Int,
        val layerType: Int = 0,
        val is24HourShowMode: Boolean = true,
        val isDateShowMode: Boolean = false,
        val isSpaceShing: Boolean = false,
        val showTime: Int = 10,
        val numHeight: Int = 1,
        val numWidth: Int = 1,
        val hourColor: Int = 0,
        val hourStartColumn: Int = 0,
        val hourStartRow: Int = 0,
        val hourWidth: Int = 0,
        val hourHeight: Int = 0,
        val spaceHourColor: Int = 0,
        val spaceHourStartColumn: Int = 0,
        val spaceHourStartRow: Int = 0,
        val spaceHourWidth: Int = 0,
        val spaceHourHeight: Int = 0,
        val minuteColor: Int = 0,
        val minuteStartColumn: Int = 0,
        val minuteStartRow: Int = 0,
        val minuteWidth: Int = 0,
        val minuteHeight: Int = 0,
        val showSpaceMinuteColor: Boolean = false,
        val spaceMinuteColor: Int = 0,
        val spaceMinuteStartColumn: Int = 0,
        val spaceMinuteStartRow: Int = 0,
        val spaceMinuteWidth: Int = 0,
        val spaceMinuteHeight: Int = 0,
        val secondsColor: Int = 0,
        val secondsStartColumn: Int = 0,
        val secondsStartRow: Int = 0,
        val secondsWidth: Int = 0,
        val secondsHeight: Int = 0,
        val showAmpm: Boolean = false,
        val ampmColor: Int = 0,
        val ampmStartColumn: Int = 0,
        val ampmStartRow: Int = 0,
        val ampmWidth: Int = 0,
        val ampmHeight: Int = 0
    ) : ProgramContent()
}

data class CoolLedUxTextContentProgramContent(
    val text: String,
    val layerType: Int = 0,
    val startColumn: Int = 0,
    val startRow: Int = 0,
    val showWidth: Int = 32,
    val showHeight: Int = 16,
    val mode: Int = 0,
    val speed: Int = 1,
    val stayTime: Int = 0,
    val moveSpace: Int = 0,
    val fontWidth: Int = 8,
    val fontHeight: Int = 16,
    /** Font point size the glyph is read/rescaled at - FontUtils.CoolleduxTextContentProgramContent.textSize. */
    val textSize: Int = fontHeight,
    val textSpacing: Int = 1,
    val isTextBold: Boolean = false,
    val textRotate: Int = 0,
    val languageCode: String = "",
    val glyphBytes: ByteArray? = null
)

data class CoolLedUxProgram(
    val combinePrograms: List<CoolLedUxCombineProgram>,
    val showCount: Int = 1
) {
    fun getContentNumber(): Int = combinePrograms.size
}

sealed class CoolLedUxCombineProgram {
    abstract val type: Int

    data class TextCombine(
        val textContentProgramContent: CoolLedUxTextContentProgramContent
    ) : CoolLedUxCombineProgram() {
        override val type: Int = 3
    }
}

data class ProgramPackage(
    val metadata: ProgramMetadata,
    val compressed: ByteArray,
    val startHeaderFrame: ByteArray,
    val chunkFrames: List<ByteArray>
)

data class ProgramMetadata(
    val family: DeviceFamily,
    val messageType: Int,
    val chunkCount: Int,
    val uncompressedSize: Int,
    val compressedSize: Int,
    val usedCompression: Boolean
)

data class CoolLedUxDataResult(
    val body: ByteArray,
    val compressed: ByteArray,
    val chunks: List<ByteArray>
)

object ProgramComposer {
    fun compose(
        family: DeviceFamily,
        content: ProgramContent,
        index: Int,
        count: Int,
        showCount: Int,
        programType: Int? = null,
        extraTypeByte: Int? = null
    ): ProgramPackage {
        val body = encodeContent(family, content)
        val compressed = LzssCodec.compress(body)
        val start = CommandBuilders.buildProgramStartHeader(
            family = family,
            request = ProgramStartRequest(
                compressed = compressed,
                index = index,
                count = count,
                showCount = showCount,
                useAlternateOpcode = family == DeviceFamily.COOLLEDU,
                programType = programType,
                extraTypeByte = extraTypeByte,
                // The start header's CRC and length are computed over the
                // *uncompressed* body for every family, not just CoolLEDUX -
                // confirmed identical across CoolledMUtils/CoolledUUtils/
                // CoolledUDUtils/ILedClockUtils/CoolledUXUtils's
                // getStartDataForProgram (all call getDataResult on the
                // pre-compression body). Compression only ever applies to
                // the per-chunk data that follows. An earlier version of
                // this code only set startSource for COOLLEDUX and silently
                // fell back to hashing/measuring the *compressed* bytes for
                // every other family, which would fail the receiving
                // firmware's own CRC/length check on every real transfer.
                startSource = body
            )
        )
        val chunks = CommandBuilders.splitChunks(compressed).mapIndexed { i, c ->
            CommandBuilders.buildDataChunk(messageType = 0x03, totalCompressedLength = compressed.size, chunkIndex = i, chunk = c)
        }
        return ProgramPackage(
            metadata = ProgramMetadata(
                family = family,
                messageType = 0x03,
                chunkCount = chunks.size,
                uncompressedSize = body.size,
                compressedSize = compressed.size,
                usedCompression = true
            ),
            compressed = compressed,
            startHeaderFrame = start,
            chunkFrames = chunks
        )
    }

    internal fun encodeContentForTest(family: DeviceFamily, content: ProgramContent): ByteArray = encodeContent(family, content)

    // NOT byte-exact for any family except COOLLEDUX and COOLLEDU-within-
    // CoolleduGlyphPipeline's scope. The underlying pipeline
    // (CoolleduGlyphPipeline/CoolleduStreamText/CoolleduCombineText/
    // CoolleduMirror) now supports showHeight in {16,32}, textSize rescale,
    // and isMirror - but ProgramContent.Text (this dispatch's input type) has
    // no textSize/isMirror fields to plumb through yet, so the
    // `text(text, speed, effect, displayColumns)` convenience overload this
    // branch calls always renders showHeight=16/textSize=16/isMirror=false.
    // Callers needing the wider scope should build a
    // CoolleduTextContentProgramContent directly and call
    // CoolleduProgramBytecode.text(content) instead (see its doc). Still
    // genuinely unported: CoolLEDU's emoji-token path, and every other
    // family's own text-content builder in the APK
    // (CoolledMUtils/CoolledUDUtils/ILedClockUtils) - each is a separate,
    // sizable undertaking of the same shape as the CoolLEDUX text/emoji
    // pipeline, not a quick follow-up. The `[0x54, speed, effect, len, text]`
    // shape below is an unverified placeholder that must not be assumed
    // correct; do not "fix" it without first doing the same smali-verified,
    // golden-vector-tested reverse-engineering pass CoolLEDUX (and now
    // CoolLEDU) got.
    private fun encodeContent(family: DeviceFamily, content: ProgramContent): ByteArray = when (content) {
        is ProgramContent.Text -> if (family == DeviceFamily.COOLLEDUX) {
            CoolleduxProgramBytecode.text(
                text = content.text,
                speed = content.speed,
                effect = content.effect,
                displayColumns = content.displayColumns,
                displayRows = content.displayRows,
                fontSizeOverride = content.fontSize
            )
        } else if (
            (family == DeviceFamily.COOLLEDU || family == DeviceFamily.COOLLEDM || family == DeviceFamily.COOLLEDUD) &&
            CoolleduProgramBytecode.supports(content.text)
        ) {
            // COOLLEDUD (iLedBike) is the easy case: CoolledUDUtils.
            // getDataWithTextContentProgramContent/getTextDataForEmoji call
            // DeviceManager.CoolleduTextContentProgramContent and
            // FontUtils.getFontByteDataCoolleduForEmoji *directly* - the
            // exact same types/functions CoolLEDU itself uses, not just a
            // structurally-similar copy.
            //
            // COOLLEDM is the "confirmed by reading, not by name" case:
            // CoolledMUtils.getDataWithTextContentProgramContent/
            // FontUtils.getFontByteDataCoolledmForEmoji are a separate
            // function with a separate name, but byte-for-byte structurally
            // identical to CoolLEDU's own versions (same tokenizer algorithm
            // in TextEmojiManager32128, same field set on
            // DeviceManager.TextContentProgramContent, same checkSegment/
            // rotate/deleteEmptyColumn/getCenteredDataBytes call sequence
            // and function names) - confirmed by direct side-by-side read.
            //
            // Both reuse the exact same Kotlin pipeline as CoolLEDU rather
            // than needing their own port. Real, verified encoding - text-only
            // (CoolLEDU's emoji-token path is still unported), and only at the
            // showHeight=16/textSize=16/isMirror=false case this dispatch's
            // convenience overload renders (see the class doc on
            // CoolleduProgramBytecode.text(text,...) above - the underlying
            // pipeline itself now also supports showHeight=32/rescale/mirror,
            // just not reachable from ProgramContent.Text yet). Falls through
            // to the unverified placeholder below for anything outside that
            // scope rather than guessing at the unported emoji-token path.
            //
            // NOT included: DeviceFamily.ILEDCLOCK. Despite an almost
            // identical getDataWithTextContentProgramContent framing (one
            // real difference: an extra 2-byte moveSpace field, and the
            // rendered bytes aren't length-prefixed), it calls a genuinely
            // different, ~1000-line function
            // (FontUtils.getFontByteDataILedClockForEmoji) - comparable in
            // scale to the original CoolLEDUX pipeline, not reusable from
            // this one, and not yet ported.
            CoolleduProgramBytecode.text(
                text = content.text,
                speed = content.speed,
                effect = content.effect,
                displayColumns = content.displayColumns
            )
        } else {
            val textBytes = content.text.encodeToByteArray()
            byteArrayOf(0x54, content.speed.toByte(), content.effect.toByte(), textBytes.size.toByte()) + textBytes
        }

        is ProgramContent.CoolLedUxText -> {
            require(family == DeviceFamily.COOLLEDUX) { "CoolLedUxText content is only valid for CoolLEDUX" }
            getDataWithProgram(getDataForProgram(content.content))
        }

        is ProgramContent.OriginalAsset -> if (family == DeviceFamily.COOLLEDUX) {
            CoolleduxProgramBytecode.originalAsset(
                assetPath = content.assetPath,
                kind = content.kind,
                speed = content.speed,
                effect = content.effect,
                displayColumns = content.displayColumns,
                displayRows = content.displayRows
            )
        } else {
            val bytes = OriginalLedAssetByteSources.active.read(content.assetPath) ?: ByteArray(0)
            byteArrayOf(0x41, content.kind.take(1).encodeToByteArray().firstOrNull() ?: 0x00, bytes.size.coerceIn(0, 255).toByte()) + bytes
        }

        is ProgramContent.Drawing -> {
            require(content.rgbBytes.size == content.width * content.height * 3) { "Drawing rgbBytes must be width*height*3" }
            byteArrayOf(0x49, content.width.toByte(), content.height.toByte()) + content.rgbBytes
        }

        is ProgramContent.PresetMode -> byteArrayOf(0x4D, content.mode.toByte(), content.intensity.toByte())

        is ProgramContent.BusinessHours -> {
            require(family == DeviceFamily.COOLLEDUX) { "BusinessHours content is only valid for CoolLEDUX" }
            CoolleduxProgramBytecode.businessHours(content)
        }

        is ProgramContent.Clock -> {
            require(family == DeviceFamily.COOLLEDUX) { "Clock content is only valid for CoolLEDUX" }
            CoolleduxProgramBytecode.clock(content)
        }
    }

    /** FontUtils.getFontByteDataCoolleduxForEmoji(...) modes that use the shared word-wrapped, row-centered canvas (CoolleduxCombineText) instead of the per-glyph stream (CoolleduxStreamText). */
    private val combineCanvasModes = setOf(1, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13)

    /** Port of CoolledUXUtils.getDataWithTextContentProgramContent(...). */
    fun getDataWithTextContentProgramContent(content: CoolLedUxTextContentProgramContent): ByteArray {
        val inner = mutableListOf<Byte>()
        inner += 0x01
        repeat(7) { inner += 0x00 }
        inner += one(content.layerType)
        inner += two(content.startColumn)
        inner += two(content.startRow)
        inner += two(content.showWidth)
        inner += two(content.showHeight)
        inner += one(content.mode)
        inner += one(content.speed)
        inner += one(content.stayTime)
        inner += two(content.moveSpace)
        val textBytes = when {
            content.glyphBytes != null -> getFontByteDataCoolleduxForEmoji(content)
            content.mode in combineCanvasModes -> CoolleduxCombineText.encode(content)
            else -> CoolleduxStreamText.encode(content)
        }
        inner += textBytes.toList()
        return (four(inner.size + 4) + inner).toByteArray()
    }

    /** Named shim matching the APK's text-combine branch inside getDataForCombineProgram(...). */
    fun getDataWithTextCombineProgram(program: CoolLedUxCombineProgram.TextCombine): ByteArray =
        getDataWithTextContentProgramContent(program.textContentProgramContent)

    /** Port of CoolledUXUtils.getDataForCombineProgram(...) for known LED-facing content types. */
    fun getDataForCombineProgram(program: CoolLedUxCombineProgram): ByteArray = when (program) {
        is CoolLedUxCombineProgram.TextCombine -> getDataWithTextCombineProgram(program)
    }

    /** Convenience wrapper for a single APK text combine program. */
    fun getDataForProgram(content: CoolLedUxTextContentProgramContent): CoolLedUxProgram =
        CoolLedUxProgram(listOf(CoolLedUxCombineProgram.TextCombine(content)))

    /** Port of CoolledUXUtils.getDataForProgram(...). */
    fun getDataForProgram(program: CoolLedUxProgram): ByteArray =
        program.combinePrograms.flatMap { getDataForCombineProgram(it).asIterable() }.toByteArray()

    /** Port of CoolledUXUtils.getDataWithProgram(...). */
    fun getDataWithProgram(program: CoolLedUxProgram): ByteArray {
        val out = mutableListOf<Byte>()
        repeat(8) { out += 0x00 }
        out += one(program.getContentNumber())
        out += 0x00
        out += getDataForProgram(program).toList()
        return out.toByteArray()
    }

    /** Port-shaped helper for the APK's getDataResult(...) body/chunk result. */
    fun getDataResult(program: CoolLedUxProgram, packageSize: Int = 1024): CoolLedUxDataResult {
        val body = getDataWithProgram(program)
        val compressed = LzssCodec.compress(body)
        return CoolLedUxDataResult(body, compressed, CommandBuilders.splitChunks(compressed, packageSize))
    }

    /**
     * Plain-text port of FontUtils.getFontByteDataCoolleduxForEmoji(...).
     *
     * APK font records are read from recovered font-library assets. Emoji/non-BMP
     * input is deliberately rejected until that APK branch is fully ported; the
     * production upload path must never synthesize fake glyph bytes.
     */
    fun getFontByteDataCoolleduxForEmoji(content: CoolLedUxTextContentProgramContent): ByteArray {
        content.glyphBytes?.let { return it.copyOf() }
        val codePoints = content.text.codePoints().toArray()
        require(codePoints.none { it > 0xFFFF }) {
            "CoolLEDUX emoji/non-BMP text is not yet ported from FontUtils.getFontByteDataCoolleduxForEmoji"
        }
        val widthPerGlyph = content.fontWidth.coerceAtLeast(1)
        val height = content.fontHeight.coerceAtLeast(1)
        val bytesPerColumn = (height + 7) / 8
        val bytesPerGlyph = widthPerGlyph * bytesPerColumn
        val glyphs = codePoints.map { cp ->
            // Matches FontUtils.readFontData(...): the APK returns a zero-filled
            // glyph (not an error) whenever the font table can't produce one -
            // see docs/APK_REVERSE_ENGINEERING_NOTES.md, "readFontData ... blank-glyph fallback".
            readFontGlyph(cp, widthPerGlyph, height, bytesPerGlyph) ?: ByteArray(bytesPerGlyph)
        }
        val out = mutableListOf<Byte>()
        out += two(codePoints.size)
        out += two(codePoints.size * widthPerGlyph)
        glyphs.forEach { out += two(widthPerGlyph) }
        glyphs.forEach { out += it.toList() }
        return out.toByteArray()
    }

    private fun readFontGlyph(codePoint: Int, width: Int, height: Int, bytesPerGlyph: Int): ByteArray? {
        val source = CoolleduxFontSources.active
        val glyph = when {
            height >= 32 || bytesPerGlyph >= 128 -> source.readGlyph32(codePoint, bold = true)
            height >= 16 || bytesPerGlyph >= 32 -> source.readGlyph16(codePoint, bold = false)
            height >= 14 && bytesPerGlyph >= 28 -> source.readGlyph14Bold(codePoint)
            height >= 12 || bytesPerGlyph >= 24 -> source.readGlyph12(codePoint, bold = false)
            else -> source.readGlyph8(codePoint)
        }
        return glyph?.fitTo(bytesPerGlyph)
    }

    private fun ByteArray.fitTo(size: Int): ByteArray = when {
        this.size == size -> copyOf()
        this.size > size -> copyOf(size)
        else -> copyOf(size)
    }

    private fun one(value: Int): Byte = (value and 0xFF).toByte()

    private fun two(value: Int): List<Byte> = listOf(
        ((value ushr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte()
    )

    private fun four(value: Int): List<Byte> = listOf(
        ((value ushr 24) and 0xFF).toByte(),
        ((value ushr 16) and 0xFF).toByte(),
        ((value ushr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte()
    )
}

data class CoolleduxTextProgramContent(
    val text: String,
    val layerType: Int = 1,
    val startRow: Int = 0,
    val startColumn: Int = 0,
    val showHeight: Int = 32,
    val showWidth: Int = 128,
    val mode: Int = 2,
    val speed: Int = 255,
    val stayTime: Int = 3,
    val isTextBold: Boolean = true,
    val textRotate: Int = 0,
    val isAutoTextSize: Boolean = true,
    val textSize: Int = 32,
    val textSpacing: Int = 1
)

sealed interface CoolleduxLayer

data class CoolleduxTextLayer(
    val layerType: Int,
    val startRow: Int,
    val startColumn: Int,
    val showHeight: Int,
    val showWidth: Int,
    val mode: Int,
    val speed: Int,
    val stayTime: Int,
    val textRotate: Int,
    val isAutoTextSize: Boolean,
    val textSize: Int,
    val textSpacing: Int,
    val isTextBold: Boolean,
    val glyphBytes: ByteArray
) : CoolleduxLayer

data class CoolleduxAssetLayer(
    val layerType: Int,
    val assetKind: String,
    val startRow: Int,
    val startColumn: Int,
    val showHeight: Int,
    val showWidth: Int,
    val mode: Int,
    val speed: Int,
    val stayTime: Int,
    val payloadBytes: ByteArray
) : CoolleduxLayer

data class CoolleduxCombineProgram(
    val layers: List<CoolleduxLayer>
)

data class CoolleduxProgram(
    val combinePrograms: List<CoolleduxCombineProgram>
)

object CoolleduxProgramBytecode {
    /** Font sizes with an asset-backed glyph table (see CoolleduxGlyphPipeline/CoolleduxFontSources). */
    val supportedFontSizes = listOf(8, 12, 14, 16, 32)

    fun text(text: String, speed: Int, effect: Int, displayColumns: Int?, displayRows: Int?, fontSizeOverride: Int? = null): ByteArray {
        val rows = displayRows?.coerceIn(8, 128) ?: 32
        val columns = displayColumns?.coerceIn(8, 512) ?: 128
        val fontSize = fontSizeOverride?.takeIf { it in supportedFontSizes } ?: when {
            rows >= 32 -> 32
            rows >= 16 -> 16
            rows >= 12 -> 12
            else -> 8
        }
        val content = CoolLedUxTextContentProgramContent(
            text = text.ifBlank { "HELLO" }.take(128),
            layerType = 0,
            startColumn = 0,
            startRow = 0,
            showWidth = columns,
            showHeight = rows,
            mode = effect.coerceIn(0, 255),
            speed = speed.coerceIn(0, 255),
            stayTime = 0,
            moveSpace = 0,
            fontWidth = fontSize,
            fontHeight = fontSize
        )
        return ProgramComposer.getDataWithProgram(ProgramComposer.getDataForProgram(content))
    }

    fun originalAsset(assetPath: String, kind: String, speed: Int, effect: Int, displayColumns: Int?, displayRows: Int?): ByteArray {
        val rows = displayRows?.coerceIn(8, 128) ?: 32
        val columns = displayColumns?.coerceIn(8, 512) ?: 128
        val encoded = OriginalLedAssetPayloadEncoder.encode(assetPath, kind, columns, rows)
        val block = when (encoded.format) {
            "raw-gif" -> rawGifContentBlock(
                payload = encoded.bytes,
                layerType = assetLayerType(kind),
                startColumn = 0,
                startRow = 0,
                showWidth = columns,
                showHeight = rows
            )
            else -> graffitiContentBlock(
                payload = encoded.bytes,
                layerType = assetLayerType(kind),
                startColumn = 0,
                startRow = 0,
                showWidth = encoded.width ?: columns,
                showHeight = encoded.height ?: rows,
                mode = effect.coerceIn(0, 255),
                speed = speed.coerceIn(0, 255),
                stayTime = 3
            )
        }
        return wrapProgram(listOf(block))
    }

    /**
     * Port of CoolledUXUtils.getDataWithGraffitiBusinessHourCombineProgram(...)
     * wrapped in getDataWithProgram(...) for a standalone single-program upload.
     *
     * Control flow was hand-traced from
     * reverse/apktool/smali_classes3/com/jtkj/led1248/light/utils/CoolledUXUtils.smali
     * (method at smali line 11563): jadx's `-m simple` output for this method has
     * the scrambled/label-collided control flow this file's methods are known
     * for - it renders bogus L29/L31/L33 back-edges in the minimalism branch that
     * do not exist in the bytecode. The smali is a clean if/else dispatch:
     *  - businessType == 0 or == 2  -> one image block from imageMatrixData.
     *  - businessType == 1          -> top + bottom blocks (minimalism).
     *  - otherwise                  -> empty (0 blocks).
     *
     * The number of graffiti blocks equals
     * CoolleduxGraffitiProgramBusinessHourContent.getContentNumber() (1 / 2 / 0),
     * which is exactly the content-count byte getDataWithProgram emits - so
     * wrapProgram(blocks) reproduces the standalone program framing.
     */
    fun businessHours(c: ProgramContent.BusinessHours): ByteArray =
        wrapProgram(businessHourBlocks(c))

    private fun businessHourBlocks(c: ProgramContent.BusinessHours): List<ByteArray> = when (c.businessType) {
        // businessType 0 (:cond_8) and 2 (:goto_4) share the single-image path.
        0, 2 -> {
            val colors = c.imageMatrixData
            val height = c.showHeight
            val width = if (height == 0) 0 else colors.size / height
            listOf(
                graffitiContentBlock(
                    payload = graffitiBusinessHourPayload(colors, height, width),
                    layerType = c.layerType,
                    startColumn = 0,
                    startRow = 0,
                    showWidth = width,
                    showHeight = height,
                    mode = c.mode,
                    speed = c.speed,
                    stayTime = c.stayTime
                )
            )
        }
        // businessType 1: minimalism - two stacked graffiti blocks with
        // fixed per-device-size dimensions (only 24x48 and 32x64 are supported;
        // any other size collapses to 0-sized blocks, matching :cond_2/:cond_5).
        1 -> {
            val top = c.topImageMatrixData
            val (topHeight, topWidth) = when {
                c.showHeight == 24 && c.showWidth == 48 -> 15 to (top.size / 15)
                c.showHeight == 32 && c.showWidth == 64 -> 17 to (top.size / 17)
                else -> 0 to 0
            }
            // Top block: startRow 0, mode = minimalismShowMode, speed = literal 255.
            val topBlock = graffitiContentBlock(
                payload = graffitiBusinessHourPayload(top, topHeight, topWidth),
                layerType = c.layerType,
                startColumn = 0,
                startRow = 0,
                showWidth = topWidth,
                showHeight = topHeight,
                mode = c.minimalismShowMode,
                speed = 255,
                stayTime = c.stayTime
            )
            val bottom = c.bottomImageMatrixData
            val (bottomHeight, bottomWidth) = when {
                c.showHeight == 24 && c.showWidth == 48 -> 9 to (bottom.size / 9)
                c.showHeight == 32 && c.showWidth == 64 -> 15 to (bottom.size / 15)
                else -> 0 to 0
            }
            // Bottom block: startRow = top block's height (stacked below it),
            // mode = literal 1, speed = content.speed.
            val bottomBlock = graffitiContentBlock(
                payload = graffitiBusinessHourPayload(bottom, bottomHeight, bottomWidth),
                layerType = c.layerType,
                startColumn = 0,
                startRow = topHeight,
                showWidth = bottomWidth,
                showHeight = bottomHeight,
                mode = 1,
                speed = c.speed,
                stayTime = c.stayTime
            )
            listOf(topBlock, bottomBlock)
        }
        else -> emptyList()
    }

    private fun graffitiBusinessHourPayload(colors: List<Int>, height: Int, width: Int): ByteArray =
        drawListDataFColor(graffitiBusinessHourShowDrawItems(colors, height, width), width, height)

    /**
     * Port of CoolledUXUtils.getGraffitiBusinessHourShowDrawItems(List<Integer>, int, int):
     * transposes a column-major-indexed color list into a row-major DrawItem list.
     * Returns empty when the color count doesn't match width*height (guard matches
     * the APK; downstream indexing then produces an empty payload).
     */
    private fun graffitiBusinessHourShowDrawItems(colors: List<Int>, height: Int, width: Int): List<Int> {
        if (colors.size != height * width) return emptyList()
        val out = ArrayList<Int>(height * width)
        for (row in 0 until height) {
            for (col in 0 until width) {
                out += colors[col * height + row]
            }
        }
        return out
    }

    /**
     * Port of CoolledUXUtils.getDrawListDataFColor(items, width, height) /
     * getGraffitiData(...): emits every pixel column-major (column outer, row
     * inner) as TextEmojiManagerCoolLEDUX.getColorDataWithColorWithRGB444Transfer's
     * 2-byte pair.
     */
    private fun drawListDataFColor(items: List<Int>, width: Int, height: Int): ByteArray {
        val out = ArrayList<Byte>(width * height * 2)
        for (col in 0 until width) {
            for (row in 0 until height) {
                out.addAll(colorDataWithColorWithRgb444Transfer(items[row * width + col]).asIterable())
            }
        }
        return out.toByteArray()
    }

    /**
     * Port of TextEmojiManagerCoolLEDUX.getColorDataWithColorWithRGB444Transfer(int)
     * (TextEmojiManagerCoolLEDUX.java:403) + rgb444Transfer(int) (line 413).
     *
     * NOTE: this is deliberately NOT OriginalLedAssetPayloadEncoder.rgb444TransferColorBytes:
     * the APK function applies rgb444Transfer to Color.red/green/blue *unconditionally*
     * and never consults alpha, whereas the original-asset encoder zeroes all
     * channels when alpha==0. Business-hours matrix colors arrive as 0xRRGGBB ints
     * (alpha byte 0), so the alpha-zeroing variant would blank every pixel - this
     * faithful variant must be used here.
     */
    private fun colorDataWithColorWithRgb444Transfer(color: Int): ByteArray {
        val r = rgb444Transfer((color ushr 16) and 0xFF)
        val g = rgb444Transfer((color ushr 8) and 0xFF)
        val b = rgb444Transfer(color and 0xFF)
        return byteArrayOf(r.toByte(), ((g shl 4) or b).toByte())
    }

    private fun rgb444Transfer(channel: Int): Int = when {
        channel >= 238 -> 15
        channel <= 47 -> 0
        else -> ((channel - 47) / 14) + 1
    }

    /**
     * Port of CoolledUXUtils.getDataWithClockCombineProgram(...) - see the doc
     * comment on ProgramContent.Clock for the full scope/verification notes.
     * Wrapped as a standalone single-program upload the same way
     * businessHours(...) wraps its blocks via wrapProgram(...).
     */
    fun clock(c: ProgramContent.Clock): ByteArray = wrapProgram(listOf(clockContentBlock(c)))

    /**
     * (DEVICE_ROW, DEVICE_COLUMN) pairs the real function special-cases with
     * their own literal digit-bitmap tables (hour/separator/am-pm elements
     * combined - the union of every explicit override guard found across the
     * whole function; see docs/APK_REVERSE_ENGINEERING_NOTES.md). Any size
     * matching this predicate is out of scope for this port; anything else is
     * guaranteed - by the mechanical "guard chain only overwrites on an exact
     * (row, column[, style]) match" shape confirmed via smali - to fall through
     * untouched to the default literal tables this port does implement.
     */
    private fun isSpecialCasedClockDeviceSize(rows: Int, columns: Int): Boolean = when (rows) {
        16 -> columns == 32 || columns == 64 || columns >= 96
        20 -> columns == 64
        24 -> columns == 48 || columns == 64 || columns >= 96
        32 -> columns == 64 || columns >= 96
        else -> false
    }

    /** Default (row/column-independent) hour-digit bitmap table - CoolledUXUtils.java:2617. 140 bytes = 10 digits x 14 bytes. */
    private const val CLOCK_DEFAULT_HOUR_DIGIT_TABLE =
        "127, 224, 255, 240, 192, 48, 192, 48, 255, 240, 127, 224, 0, 0, 32, 48, 96, 48, 255, 240, 255, 240, 0, 48, 0, 48, 0, 0, 96, 240, 225, 240, 195, 48, 198, 48, 252, 48, 120, 48, 0, 0, 96, 96, 224, 112, 198, 48, 198, 48, 255, 240, 121, 224, 0, 0, 31, 128, 63, 128, 97, 128, 255, 240, 255, 240, 1, 128, 0, 0, 252, 96, 252, 112, 204, 48, 204, 48, 207, 240, 199, 224, 0, 0, 127, 224, 255, 240, 204, 48, 204, 48, 207, 240, 199, 224, 0, 0, 192, 0, 192, 0, 199, 240, 207, 240, 248, 0, 240, 0, 0, 0, 123, 224, 255, 240, 198, 48, 198, 48, 255, 240, 123, 224, 0, 0, 124, 96, 254, 112, 198, 48, 198, 48, 255, 240, 127, 224, 0, 0"

    /** Default (row/column-independent) hour/minute-separator bitmap table - CoolledUXUtils.java:2680. Reused verbatim for the minute/second separator when showSpaceMinuteColor is set. */
    private const val CLOCK_DEFAULT_SEPARATOR_TABLE = "51, 0, 51, 0"

    private fun clockContentBlock(c: ProgramContent.Clock): ByteArray {
        require(!isSpecialCasedClockDeviceSize(c.deviceRows, c.deviceColumns)) {
            "ProgramContent.Clock: device size ${c.deviceRows}x${c.deviceColumns} hits one of " +
                "CoolledUXUtils.getDataWithClockCombineProgram's explicit per-size/per-style literal " +
                "digit-bitmap overrides, which have not been transcribed yet (see " +
                "docs/APK_REVERSE_ENGINEERING_NOTES.md) - only the default-table fallback path is ported."
        }

        val body = mutableListOf<Byte>()
        body += 0x07.toByte() // CoolleduxCombineProgram.CLOCK
        repeat(7) { body += 0x00.toByte() }
        body += c.layerType.coerceIn(0, 255).toByte()
        // Mode-byte truth table (CoolledUXUtils.java:2607-2613/3672-3681, smali
        // :cond_1/:cond_2/:cond_3/:goto_1 at CoolledUXUtils.smali:5670-5714+9-
        // -jadx's `-m simple` rendering makes this look like it falls into two
        // dead-looking labels (L10/L11) that only appear ~1000 lines later at
        // the very end of the method; smali confirms there is no re-entry, just
        // an ordinary bytecode-layout artifact.
        body += clockModeByte(c.is24HourShowMode, c.isSpaceShing)
        body += u16(c.showTime)
        body += u16(c.numHeight)
        body += u16(c.numWidth)

        val hourTable = CommaSeparatedByteTable.parse(CLOCK_DEFAULT_HOUR_DIGIT_TABLE)
        body += u16(hourTable.size)
        body += hourTable.toList()
        body += colorDataWithColor(c.hourColor)
        body += u16(c.hourStartColumn)
        body += u16(c.hourStartRow)
        body += u16(c.hourWidth)
        body += u16(c.hourHeight)

        body += colorDataWithColor(c.spaceHourColor)
        body += u16(c.spaceHourStartColumn)
        body += u16(c.spaceHourStartRow)
        body += u16(c.spaceHourWidth)
        body += u16(c.spaceHourHeight)

        val separatorTable = CommaSeparatedByteTable.parse(CLOCK_DEFAULT_SEPARATOR_TABLE)
        body += u16(separatorTable.size)
        body += separatorTable.toList()

        body += colorDataWithColor(c.minuteColor)
        body += u16(c.minuteStartColumn)
        body += u16(c.minuteStartRow)
        body += u16(c.minuteWidth)
        body += u16(c.minuteHeight)

        body += colorDataWithColor(c.spaceMinuteColor)
        body += u16(c.spaceMinuteStartColumn)
        body += u16(c.spaceMinuteStartRow)
        body += u16(c.spaceMinuteWidth)
        body += u16(c.spaceMinuteHeight)

        if (c.showSpaceMinuteColor) {
            body += u16(separatorTable.size)
            body += separatorTable.toList()
        } else {
            body += u16(0)
        }

        body += colorDataWithColor(c.secondsColor)
        body += u16(c.secondsStartColumn)
        body += u16(c.secondsStartRow)
        body += u16(c.secondsWidth)
        body += u16(c.secondsHeight)

        body += colorDataWithColor(c.ampmColor)
        body += u16(c.ampmStartColumn)
        body += u16(c.ampmStartRow)
        body += u16(c.ampmWidth)
        body += u16(c.ampmHeight)

        // Trailing optional am/pm glyph table (r0 in the decompiled source):
        // default "" (empty) for every device size this port supports - only
        // (16,64,style==7) and (16,>=96,style==7) ever populate it, both of
        // which are already excluded by isSpecialCasedClockDeviceSize's
        // row==16 && columns>=96 (and ==64) branches above, confirmed via
        // smali (CoolledUXUtils.smali:8967-9033, :goto_13 default path).
        body += u16(0)

        return (u32(body.size + 4) + body).toByteArray()
    }

    /**
     * Port of the is24HourShowMode/isSpaceShing mode-byte dispatch at the top
     * of CoolledUXUtils.getDataWithClockCombineProgram, smali-confirmed
     * (CoolledUXUtils.smali:5670-5714 for the branch entry, :9672-9681
     * jadx-line-numbered as L10/L11/L13/L14 for the actual "01"/"02"/"00"
     * targets jadx renders far away from their real logical position).
     */
    private fun clockModeByte(is24HourShowMode: Boolean, isSpaceShing: Boolean): Byte = when {
        is24HourShowMode && isSpaceShing -> 0x03
        is24HourShowMode && !isSpaceShing -> 0x01
        !is24HourShowMode && isSpaceShing -> 0x02
        else -> 0x00
    }.toByte()

    /**
     * Port of TextEmojiManagerCoolLEDUX.getColorDataWithColor(int)
     * (TextEmojiManagerCoolLEDUX.java:393) - a plain 2-byte RGB444-shaped
     * quantization (channel/16 per nibble), deliberately WITHOUT the
     * threshold-based rgb444Transfer(...) quantization
     * getColorDataWithColorWithRGB444Transfer uses. Byte layout:
     * [0x0 | redNibble][greenNibble<<4 | blueNibble].
     */
    private fun colorDataWithColor(color: Int): List<Byte> {
        val r = ((color ushr 16) and 0xFF) / 16
        val g = ((color ushr 8) and 0xFF) / 16
        val b = (color and 0xFF) / 16
        return listOf(r.toByte(), ((g shl 4) or b).toByte())
    }

    private fun wrapProgram(blocks: List<ByteArray>): ByteArray {
        val out = mutableListOf<Byte>()
        repeat(8) { out += 0x00.toByte() }
        out += blocks.size.coerceIn(0, 255).toByte()
        out += 0x00.toByte()
        blocks.forEach { out += it.toList() }
        return out.toByteArray()
    }

    private fun rawGifContentBlock(payload: ByteArray, layerType: Int, startColumn: Int, startRow: Int, showWidth: Int, showHeight: Int): ByteArray {
        val inner = mutableListOf<Byte>()
        inner += 0x0c.toByte()
        repeat(7) { inner += 0x00.toByte() }
        inner += layerType.coerceIn(0, 255).toByte()
        inner += 0x00.toByte()
        inner += u16(startColumn)
        inner += u16(startRow)
        inner += u16(showWidth)
        inner += u16(showHeight)
        inner += u32(payload.size)
        inner += payload.toList()
        return (u32(inner.size + 4) + inner).toByteArray()
    }

    private fun graffitiContentBlock(payload: ByteArray, layerType: Int, startColumn: Int, startRow: Int, showWidth: Int, showHeight: Int, mode: Int, speed: Int, stayTime: Int): ByteArray {
        val inner = mutableListOf<Byte>()
        inner += 0x02.toByte()
        repeat(7) { inner += 0x00.toByte() }
        inner += layerType.coerceIn(0, 255).toByte()
        inner += u16(startColumn)
        inner += u16(startRow)
        inner += u16(showWidth)
        inner += u16(showHeight)
        inner += mode.coerceIn(0, 255).toByte()
        inner += speed.coerceIn(0, 255).toByte()
        inner += stayTime.coerceIn(0, 255).toByte()
        inner += u32(payload.size)
        inner += payload.toList()
        return (u32(inner.size + 4) + inner).toByteArray()
    }

    private fun assetLayerType(kind: String): Int = when (kind.lowercase()) {
        "animation" -> 3
        "emoji" -> 4
        "icon" -> 5
        "image" -> 6
        "clock-template" -> 7
        "sensor-template" -> 8
        else -> 9
    }

    private fun u16(value: Int): List<Byte> = listOf(((value ushr 8) and 0xFF).toByte(), (value and 0xFF).toByte())
    private fun u32(value: Int): List<Byte> = listOf(
        ((value ushr 24) and 0xFF).toByte(),
        ((value ushr 16) and 0xFF).toByte(),
        ((value ushr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte()
    )
}


private object CoolleduxFontByteBuilder {
    fun renderText(content: CoolleduxTextProgramContent): ByteArray {
        val out = mutableListOf<Byte>()
        val codePoints = content.text.codePoints().toArray()
        codePoints.forEachIndexed { idx, cp ->
            val glyph = when {
                content.textSize >= 32 -> CoolleduxFontSources.active.readGlyph32(cp, content.isTextBold)
                content.textSize >= 16 -> CoolleduxFontSources.active.readGlyph16(cp, content.isTextBold)
                content.textSize >= 14 && content.isTextBold -> CoolleduxFontSources.active.readGlyph14Bold(cp)
                content.textSize >= 12 -> CoolleduxFontSources.active.readGlyph12(cp, content.isTextBold)
                else -> CoolleduxFontSources.active.readGlyph8(cp)
            }
            // Matches FontUtils.readFontData(...): the APK returns a zero-filled
            // glyph (not an error) whenever the font table can't produce one -
            // see docs/APK_REVERSE_ENGINEERING_NOTES.md, "readFontData ... blank-glyph fallback".
            out += (glyph ?: ByteArray(bytesPerColumn(content.textSize) * content.textSize)).toList()
            if (idx != codePoints.lastIndex && content.textSpacing > 0) {
                repeat(content.textSpacing) {
                    repeat(bytesPerColumn(content.textSize)) { out += 0x00.toByte() }
                }
            }
        }
        return out.toByteArray()
    }

    private fun bytesPerColumn(textSize: Int): Int = when {
        textSize >= 32 -> 4
        textSize >= 24 -> 3
        textSize >= 16 -> 2
        else -> 1
    }
}
