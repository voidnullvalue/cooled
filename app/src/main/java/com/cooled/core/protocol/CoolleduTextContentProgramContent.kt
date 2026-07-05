package com.cooled.core.protocol

/**
 * Port of DeviceManager.CoolleduTextContentProgramContent - the CoolLEDU
 * (plain "U", not "UX") family's text-program content model, consumed by
 * CoolledUUtils.getDataWithTextContentProgramContent /
 * FontUtils.getFontByteDataCoolleduForEmoji.
 *
 * This is a distinct, simpler protocol from CoolLEDUX's
 * (CoolLedUxTextContentProgramContent): no per-glyph [colCount][itemType]
 * framing, no emoji/image tokens (CoolLEDU's emoji mechanism is a
 * completely different reverse-indexed-list lookup, not yet ported - see
 * docs/APK_REVERSE_ENGINEERING_NOTES.md), and an `isMirror` field applied
 * as a post-processing step on the rendered bytes rather than a separate
 * hardware command.
 */
data class CoolleduTextContentProgramContent(
    val text: String,
    val layerType: Int = 0,
    val startColumn: Int = 0,
    val startRow: Int = 0,
    val showWidth: Int = 32,
    val showHeight: Int = 16,
    val mode: Int = 0,
    val speed: Int = 1,
    val stayTime: Int = 0,
    val textSize: Int = showHeight,
    val textSpacing: Int = 1,
    val isTextBold: Boolean = false,
    val textRotate: Int = 0,
    val isMirror: Boolean = false
)
