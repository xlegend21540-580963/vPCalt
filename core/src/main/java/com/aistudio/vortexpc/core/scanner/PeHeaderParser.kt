package com.aistudio.vortexpc.core.scanner

import com.aistudio.vortexpc.core.model.PeArchitecture
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object PeHeaderParser {
  private const val IMAGE_DOS_SIGNATURE = 0x5A4D // 'MZ'
  private const val IMAGE_NT_SIGNATURE = 0x00004550 // "PE\0\0"
  private const val MACHINE_I386 = 0x014C
  private const val MACHINE_AMD64 = 0x8664
  private const val MACHINE_ARM64 = 0xAA64
  private const val MACHINE_ARM64EC = 0xA641

  fun detectArchitecture(input: InputStream): PeArchitecture {
    val header = ByteArray(4096)
    val bytesRead = readFullyUpTo(input, header)
    if (bytesRead < 64) return PeArchitecture.UNKNOWN
    return detectArchitecture(header, bytesRead)
  }

  fun detectArchitecture(bytes: ByteArray, length: Int = bytes.size): PeArchitecture {
    if (length < 64) return PeArchitecture.UNKNOWN
    val buf = ByteBuffer.wrap(bytes, 0, length).order(ByteOrder.LITTLE_ENDIAN)
    val mz = buf.getShort(0).toInt() and 0xFFFF
    if (mz != IMAGE_DOS_SIGNATURE) return PeArchitecture.UNKNOWN

    val peOffset = buf.getInt(0x3C)
    if (peOffset < 0 || peOffset + 6 > length) return PeArchitecture.UNKNOWN

    val peSig = buf.getInt(peOffset)
    if (peSig != IMAGE_NT_SIGNATURE) return PeArchitecture.UNKNOWN

    val machine = buf.getShort(peOffset + 4).toInt() and 0xFFFF
    return when (machine) {
      MACHINE_I386 -> PeArchitecture.X86
      MACHINE_AMD64 -> PeArchitecture.X64
      MACHINE_ARM64, MACHINE_ARM64EC -> PeArchitecture.ARM64EC
      else -> PeArchitecture.UNKNOWN
    }
  }

  private fun readFullyUpTo(input: InputStream, dst: ByteArray): Int {
    var total = 0
    while (total < dst.size) {
      val read = input.read(dst, total, dst.size - total)
      if (read <= 0) break
      total += read
    }
    return total
  }
}
