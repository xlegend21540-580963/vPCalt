package com.aistudio.vortexpc.core.scanner

import com.aistudio.vortexpc.core.model.PeArchitecture
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class PeHeaderParserTest {

  private fun buildMinimalPe(machine: Int): ByteArray {
    val bytes = ByteArray(256)
    val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    buf.putShort(0, 0x5A4D.toShort()) // MZ
    buf.putInt(0x3C, 0x80)            // e_lfanew -> 0x80
    buf.putInt(0x80, 0x00004550)      // PE\0\0
    buf.putShort(0x84, machine.toShort())
    return bytes
  }

  @Test
  fun `detects x86 PE32 header`() {
    val arch = PeHeaderParser.detectArchitecture(buildMinimalPe(0x014C))
    assertEquals(PeArchitecture.X86, arch)
  }

  @Test
  fun `detects x64 PE32+ header`() {
    val arch = PeHeaderParser.detectArchitecture(buildMinimalPe(0x8664))
    assertEquals(PeArchitecture.X64, arch)
  }

  @Test
  fun `detects ARM64EC header`() {
    val arch = PeHeaderParser.detectArchitecture(buildMinimalPe(0xA641))
    assertEquals(PeArchitecture.ARM64EC, arch)
  }

  @Test
  fun `returns UNKNOWN on non-PE data`() {
    val arch = PeHeaderParser.detectArchitecture(ByteArray(128))
    assertEquals(PeArchitecture.UNKNOWN, arch)
  }
}
