package com.cooled.core.protocol

import com.cooled.core.model.DeviceFamily
import org.junit.Assert.assertArrayEquals
import org.junit.Test

/**
 * Hand-traced golden vector for CoolledUXUtils.getDataWithGraffitiBusinessHourCombineProgram
 * wrapped in getDataWithProgram, businessType==0 (single-image) branch.
 */
class BusinessHoursTemplateTest {
    @Test
    fun businessTypeZeroGoldenVectorSingleOnePixelImage() {
        // A 1x1 "matrix": one fully-white ARGB pixel -> rgb444Transfer(0xFF)=15
        // for every channel -> payload bytes [0x0F, 0xFF] (red nibble, green<<4|blue).
        val content = ProgramContent.BusinessHours(
            businessType = 0,
            showHeight = 1,
            imageMatrixData = listOf(0xFFFFFFFF.toInt())
        )
        val encoded = ProgramComposer.encodeContentForTest(DeviceFamily.COOLLEDUX, content)

        val payload = byteArrayOf(0x0F, 0xFF.toByte())
        val inner = byteArrayOf(0x02) + ByteArray(7) +
            byteArrayOf(1) + // layerType default
            byteArrayOf(0, 0) + // startColumn
            byteArrayOf(0, 0) + // startRow
            byteArrayOf(0, 1) + // showWidth = colors.size/height = 1
            byteArrayOf(0, 1) + // showHeight = 1
            byteArrayOf(2) + // mode default
            byteArrayOf(0xFF.toByte()) + // speed default = 255
            byteArrayOf(3) + // stayTime default
            byteArrayOf(0, 0, 0, 2) + // payload length
            payload
        val block = byteArrayOf(0, 0, 0, (inner.size + 4).toByte()) + inner
        val expected = ByteArray(8) + byteArrayOf(1, 0) + block // wrapProgram: 8 zero bytes, contentCount=1, reserved, block
        assertArrayEquals(expected, encoded)
    }

    @Test
    fun unknownBusinessTypeProducesNoContentBlocks() {
        val content = ProgramContent.BusinessHours(businessType = 9)
        val encoded = ProgramComposer.encodeContentForTest(DeviceFamily.COOLLEDUX, content)
        // wrapProgram with zero blocks: 8 zero bytes + contentCount=0 + reserved byte only.
        assertArrayEquals(ByteArray(10), encoded)
    }
}
