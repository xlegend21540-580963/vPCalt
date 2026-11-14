package com.aistudio.vortexpc.spike

data class NativeProbeResult(
    val passed: Boolean,
    val errno: Int,
    val note: String
)

object SpikeNative {
    var isLoaded: Boolean = false
        private set
    var loadError: String? = null
        private set

    init {
        try {
            System.loadLibrary("spike_jni")
            isLoaded = true
        } catch (t: Throwable) {
            isLoaded = false
            loadError = "${t::class.java.simpleName}: ${t.message}"
        }
    }

    external fun nativeReadEnvironment(): String
    external fun nativeExecve(execPath: String, argv: Array<String>): String
    external fun nativeDlopen(path: String): String
    external fun nativeMmapFileExec(path: String): String
    external fun nativeMmapFileRwToRx(path: String): String
    external fun nativeJitAnonRwToRx(): String
    external fun nativeMmapAnonRwx(): String
    external fun nativeMemfdExec(): String
    external fun nativeAshmemExec(): String

    fun parseResult(raw: String): NativeProbeResult {
        val parts = raw.split('|', limit = 3)
        if (parts.size < 3) {
            return NativeProbeResult(passed = false, errno = -1, note = raw.replace('|', ' '))
        }
        val passed = parts[0] == "PASS"
        val errno = parts[1].toIntOrNull() ?: -1
        val note = parts[2]
        return NativeProbeResult(passed = passed, errno = errno, note = note)
    }
}
