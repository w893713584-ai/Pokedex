package com.idex.android

import com.idex.android.audio.Pcm
import com.idex.android.audio.RoboVoice
import com.idex.android.audio.VoiceStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class RoboVoiceTest {

    private fun tone(sr: Int = 22050, seconds: Double = 0.5): Pcm {
        val n = (sr * seconds).toInt()
        return Pcm(ShortArray(n) { (sin(2 * PI * 440 * it / sr) * 12000).toInt().toShort() }, sr)
    }

    @Test
    fun processKeepsLengthAndNormalizes() {
        val input = tone()
        for (style in VoiceStyle.ALL) {
            val out = RoboVoice.process(input, style)
            assertEquals(input.samples.size, out.samples.size)
            assertEquals(input.sampleRate, out.sampleRate)
            val peak = out.samples.maxOf { abs(it.toInt()) }
            assertTrue("${style.id} peak $peak", peak in 1000..32767)
        }
    }

    @Test
    fun wavRoundTrip() {
        val file = File.createTempFile("dex", ".wav")
        try {
            writeWav(file, tone())
            val back = RoboVoice.readWav(file)
            assertEquals(22050, back.sampleRate)
            assertEquals(tone().samples.size, back.samples.size)
        } finally {
            file.delete()
        }
    }

    @Test
    fun wavSupportsStereoAnd8BitPcm() {
        // 8-bit unsigned stereo (4 frames = 8 bytes)
        val data = byteArrayOf(
            0x80.toByte(), 0x80.toByte(), // 0
            0xFF.toByte(), 0x81.toByte(), // (127<<8 + 1<<8)/2
            0x00, 0x00,
            0xC0.toByte(), 0x40.toByte(),
        )
        val wav = buildCustomWav(formatTag = 1, channels = 2, sampleRate = 22050, bits = 8, data = data)
        val pcm = RoboVoice.readWav(wav)
        assertEquals(22050, pcm.sampleRate)
        assertEquals(4, pcm.samples.size)
        assertEquals(0, pcm.samples[0].toInt())
    }

    @Test(expected = IllegalArgumentException::class)
    fun wavRejectsNonPcmFormatTag() {
        val wav = buildCustomWav(formatTag = 3, channels = 1, sampleRate = 22050, bits = 16, data = ByteArray(8))
        RoboVoice.readWav(wav)
    }

    @Test(expected = IllegalArgumentException::class)
    fun wavRejectsUnsupportedBitDepth() {
        val wav = buildCustomWav(formatTag = 1, channels = 1, sampleRate = 22050, bits = 24, data = ByteArray(12))
        RoboVoice.readWav(wav)
    }

    @Test(expected = IllegalArgumentException::class)
    fun wavRejectsNegativeChunkLengthLoop() {
        val bb = ByteBuffer.allocate(56).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(48); put("WAVE".toByteArray())
            put("JUNK".toByteArray()); putInt(-8) // Would loop forever if unchecked
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
            putInt(22050); putInt(44100); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(4); putInt(0)
        }
        RoboVoice.readWav(bb.array())
    }

    @Test(expected = IllegalArgumentException::class)
    fun wavRejectsTruncatedFmtChunk() {
        val bb = ByteBuffer.allocate(48).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(40); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(8) // Too short (< 16)
            putShort(1); putShort(1); putInt(22050)
            put("data".toByteArray()); putInt(4); putInt(0)
        }
        RoboVoice.readWav(bb.array())
    }

    private fun buildCustomWav(
        formatTag: Short,
        channels: Short,
        sampleRate: Int,
        bits: Short,
        data: ByteArray,
    ): ByteArray {
        val blockAlign = (channels * (bits / 8)).toShort()
        val byteRate = sampleRate * blockAlign
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + data.size); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(formatTag); putShort(channels)
            putInt(sampleRate); putInt(byteRate); putShort(blockAlign); putShort(bits)
            put("data".toByteArray()); putInt(data.size)
        }
        return header.array() + data
    }

    private fun writeWav(file: File, pcm: Pcm) {
        val data = ByteBuffer.allocate(pcm.samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        pcm.samples.forEach { data.putShort(it) }
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + data.capacity()); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
            putInt(pcm.sampleRate); putInt(pcm.sampleRate * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(data.capacity())
        }
        file.writeBytes(header.array() + data.array())
    }
}
