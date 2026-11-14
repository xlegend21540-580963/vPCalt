package com.aistudio.vortexpc.spike

import android.content.Context
import android.os.Build
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class SpikeLine(
    val testId: String,
    val targetSdk: Int,
    val location: String,
    val method: String,
    val status: String,
    val errno: String,
    val note: String
) {
    fun format(): String {
        val cleanNote = note.replace('\n', ' ').replace('\r', ' ').replace('|', ' ').trim()
        return "$testId | $targetSdk | $location | $method | $status | $errno | $cleanNote"
    }
}

object SpikeRunner {
    const val RESULTS_FILENAME = "results.txt"

    fun resultsFile(context: Context): File = File(context.filesDir, RESULTS_FILENAME)

    fun runAll(context: Context, onProgress: (String) -> Unit = {}): String {
        val sb = StringBuilder()
        fun emit(line: String) {
            sb.appendLine(line)
            onProgress(sb.toString())
        }

        val appInfo = context.applicationInfo
        val targetSdk = appInfo.targetSdkVersion
        val nativeDirPath = appInfo.nativeLibraryDir
        val nativeDir = if (nativeDirPath.isNullOrBlank()) {
            File(context.filesDir, "__missing_native_lib_dir__")
        } else {
            File(nativeDirPath)
        }
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).format(Date())

        emit("# VortexPC Feasibility Spike Results")
        emit("# timestamp=$timestamp")
        emit("# package=${context.packageName} targetSdk=$targetSdk minSdk=${appInfo.minSdkVersion}")
        emit("# sdk_int=${Build.VERSION.SDK_INT} release=${Build.VERSION.RELEASE} security_patch=${Build.VERSION.SECURITY_PATCH}")
        emit("# device=${Build.MANUFACTURER} ${Build.MODEL} hardware=${Build.HARDWARE} board=${Build.BOARD}")
        emit("# fingerprint=${Build.FINGERPRINT}")
        emit("# abis=${Build.SUPPORTED_ABIS.joinToString(",")}")
        emit("# nativeLibraryDir=${nativeDir.absolutePath}")
        emit("# filesDir=${context.filesDir.absolutePath}")
        emit("# codeCacheDir=${context.codeCacheDir.absolutePath}")
        emit("# cacheDir=${context.cacheDir.absolutePath}")
        val extDir = context.getExternalFilesDir(null)
        emit("# externalFilesDir=${extDir?.absolutePath ?: "<null>"}")

        val uname = runCatching { Os.uname() }.getOrNull()
        val pageSize = runCatching { Os.sysconf(OsConstants._SC_PAGESIZE) }.getOrDefault(-1L)
        if (uname != null) {
            emit("# os_uname=${uname.sysname} ${uname.release} ${uname.version} (${uname.machine}) | page_size=$pageSize")
        }
        if (SpikeNative.isLoaded) {
            emit("# native_env=${SpikeNative.nativeReadEnvironment()}")
        } else {
            emit("# native_env=JNI_LOAD_FAILED (${SpikeNative.loadError})")
        }
        emit("#")
        emit("TEST | targetSdk | location | method | PASS/FAIL | errno | note")

        val sourceExec = File(nativeDir, "libspike_exec.so")
        val sourceShlib = File(nativeDir, "libspike_jni.so")

        val locations = LinkedHashMap<String, File?>()
        locations["nativeLibraryDir"] = sourceExec
        locations["filesDir"] = File(context.filesDir, "spike_exec_copy.so")
        locations["codeCacheDir"] = File(context.codeCacheDir, "spike_exec_copy.so")
        locations["cacheDir"] = File(context.cacheDir, "spike_exec_copy.so")
        locations["externalFilesDir"] = extDir?.let { File(it, "spike_exec_copy.so") }

        val stagedStatus = LinkedHashMap<String, String>()
        if (!sourceExec.exists()) {
            emit(
                SpikeLine(
                    testId = "STAGE",
                    targetSdk = targetSdk,
                    location = "nativeLibraryDir",
                    method = "exists",
                    status = "FAIL",
                    errno = "2",
                    note = "Missing ${sourceExec.absolutePath}; dir_contents=${nativeDir.list()?.joinToString(",") ?: "<empty>"}"
                ).format()
            )
        } else {
            emit(
                SpikeLine(
                    testId = "STAGE",
                    targetSdk = targetSdk,
                    location = "nativeLibraryDir",
                    method = "exists",
                    status = "PASS",
                    errno = "0",
                    note = "size=${sourceExec.length()} canExec=${sourceExec.canExecute()}"
                ).format()
            )
        }

        for ((locName, destFile) in locations) {
            if (locName == "nativeLibraryDir") continue
            if (destFile == null) {
                stagedStatus[locName] = "externalFilesDir returned null"
                emit(
                    SpikeLine(
                        testId = "STAGE",
                        targetSdk = targetSdk,
                        location = locName,
                        method = "copy+chmod755",
                        status = "FAIL",
                        errno = "2",
                        note = "getExternalFilesDir(null) returned null"
                    ).format()
                )
                continue
            }
            if (!sourceExec.exists()) {
                stagedStatus[locName] = "source libspike_exec.so missing"
                continue
            }
            val stageRes = stageBinaryCopy(sourceExec, destFile)
            emit(
                SpikeLine(
                    testId = "STAGE",
                    targetSdk = targetSdk,
                    location = locName,
                    method = "copy+chmod755",
                    status = if (stageRes.passed) "PASS" else "FAIL",
                    errno = stageRes.errno.toString(),
                    note = stageRes.note
                ).format()
            )
            if (!stageRes.passed) {
                stagedStatus[locName] = stageRes.note
            }
        }

        // Stage copies of libspike_jni.so (actual ET_DYN shared library) for dlopen comparison
        val shlibCopies = LinkedHashMap<String, File?>()
        shlibCopies["nativeLibraryDir"] = sourceShlib
        shlibCopies["filesDir"] = File(context.filesDir, "spike_jni_copy.so")
        shlibCopies["codeCacheDir"] = File(context.codeCacheDir, "spike_jni_copy.so")
        shlibCopies["cacheDir"] = File(context.cacheDir, "spike_jni_copy.so")
        shlibCopies["externalFilesDir"] = extDir?.let { File(it, "spike_jni_copy.so") }
        if (sourceShlib.exists()) {
            for ((locName, dest) in shlibCopies) {
                if (locName == "nativeLibraryDir" || dest == null) continue
                stageBinaryCopy(sourceShlib, dest)
            }
        }

        // EXPERIMENT 1: exec matrix
        for ((locName, file) in locations) {
            if (file == null || !file.exists()) {
                val reason = stagedStatus[locName] ?: "file missing: ${file?.absolutePath ?: "<null>"}"
                for (m in listOf("ProcessBuilder", "Runtime.exec", "native_execve", "dlopen_exec", "dlopen_shlib", "linker64_pb", "linker64_execve")) {
                    emit(SpikeLine("EXP1", targetSdk, locName, m, "FAIL", "2", reason).format())
                }
                continue
            }
            val path = file.absolutePath

            val pb = runJavaProcess(listOf(path), expectExecOk = true)
            emit(SpikeLine("EXP1", targetSdk, locName, "ProcessBuilder", if (pb.passed) "PASS" else "FAIL", pb.errno.toString(), pb.note).format())

            val rt = runRuntimeExec(arrayOf(path), expectExecOk = true)
            emit(SpikeLine("EXP1", targetSdk, locName, "Runtime.exec", if (rt.passed) "PASS" else "FAIL", rt.errno.toString(), rt.note).format())

            val jniExec = runJni { SpikeNative.nativeExecve(path, arrayOf(path)) }
            emit(SpikeLine("EXP1", targetSdk, locName, "native_execve", if (jniExec.passed) "PASS" else "FAIL", jniExec.errno.toString(), jniExec.note).format())

            val dlExec = runJni { SpikeNative.nativeDlopen(path) }
            emit(SpikeLine("EXP1", targetSdk, locName, "dlopen_exec", if (dlExec.passed) "PASS" else "FAIL", dlExec.errno.toString(), dlExec.note).format())

            val shlibFile = shlibCopies[locName]
            if (shlibFile != null && shlibFile.exists()) {
                val dlShlib = runJni { SpikeNative.nativeDlopen(shlibFile.absolutePath) }
                emit(SpikeLine("EXP1", targetSdk, locName, "dlopen_shlib", if (dlShlib.passed) "PASS" else "FAIL", dlShlib.errno.toString(), dlShlib.note).format())
            } else {
                emit(SpikeLine("EXP1", targetSdk, locName, "dlopen_shlib", "FAIL", "2", "libspike_jni copy unavailable").format())
            }

            val l64Pb = runJavaProcess(listOf("/system/bin/linker64", path), expectExecOk = true)
            emit(SpikeLine("EXP1", targetSdk, locName, "linker64_pb", if (l64Pb.passed) "PASS" else "FAIL", l64Pb.errno.toString(), l64Pb.note).format())

            val l64Execve = runJni {
                SpikeNative.nativeExecve("/system/bin/linker64", arrayOf("/system/bin/linker64", path))
            }
            emit(SpikeLine("EXP1", targetSdk, locName, "linker64_execve", if (l64Execve.passed) "PASS" else "FAIL", l64Execve.errno.toString(), l64Execve.note).format())
        }

        // EXPERIMENT 2: mmap matrix
        for (locName in listOf("nativeLibraryDir", "filesDir", "codeCacheDir")) {
            val file = locations[locName]
            if (file == null || !file.exists()) {
                val reason = stagedStatus[locName] ?: "file missing"
                emit(SpikeLine("EXP2", targetSdk, locName, "mmap_file_rx", "FAIL", "2", reason).format())
                emit(SpikeLine("EXP2", targetSdk, locName, "mmap_file_rw_to_rx", "FAIL", "2", reason).format())
            } else {
                val rx = runJni { SpikeNative.nativeMmapFileExec(file.absolutePath) }
                emit(SpikeLine("EXP2", targetSdk, locName, "mmap_file_rx", if (rx.passed) "PASS" else "FAIL", rx.errno.toString(), rx.note).format())

                val rwRx = runJni { SpikeNative.nativeMmapFileRwToRx(file.absolutePath) }
                emit(SpikeLine("EXP2", targetSdk, locName, "mmap_file_rw_to_rx", if (rwRx.passed) "PASS" else "FAIL", rwRx.errno.toString(), rwRx.note).format())
            }
        }

        val anonJit = runJni { SpikeNative.nativeJitAnonRwToRx() }
        emit(SpikeLine("EXP2", targetSdk, "anonymous", "anon_rw_to_rx_jit", if (anonJit.passed) "PASS" else "FAIL", anonJit.errno.toString(), anonJit.note).format())

        val anonRwx = runJni { SpikeNative.nativeMmapAnonRwx() }
        emit(SpikeLine("EXP2", targetSdk, "anonymous", "anon_rwx_wx_check", if (anonRwx.passed) "PASS" else "FAIL", anonRwx.errno.toString(), anonRwx.note).format())

        val memfd = runJni { SpikeNative.nativeMemfdExec() }
        emit(SpikeLine("EXP2", targetSdk, "memfd", "memfd_create_rx", if (memfd.passed) "PASS" else "FAIL", memfd.errno.toString(), memfd.note).format())

        val ashmem = runJni { SpikeNative.nativeAshmemExec() }
        emit(SpikeLine("EXP2", targetSdk, "ashmem", "ashmem_setProt_rx", if (ashmem.passed) "PASS" else "FAIL", ashmem.errno.toString(), ashmem.note).format())

        val finalOutput = sb.toString()
        runCatching {
            resultsFile(context).writeText(finalOutput)
        }.onFailure { err ->
            emit("# write_results_error=${err::class.java.simpleName}: ${err.message}")
        }
        return sb.toString()
    }

    private fun stageBinaryCopy(source: File, dest: File): NativeProbeResult {
        return try {
            dest.parentFile?.mkdirs()
            if (dest.exists()) dest.delete()
            FileInputStream(source).use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
            }
            try {
                Os.chmod(dest.absolutePath, 493) // 0755 octal
                NativeProbeResult(
                    passed = true,
                    errno = 0,
                    note = "staged=${dest.absolutePath} size=${dest.length()} chmod=0755 canExec=${dest.canExecute()}"
                )
            } catch (e: ErrnoException) {
                NativeProbeResult(
                    passed = false,
                    errno = e.errno,
                    note = "copied size=${dest.length()} but chmod(0755) failed: ${Os.strerror(e.errno)} canExec=${dest.canExecute()}"
                )
            }
        } catch (t: Throwable) {
            val errno = extractErrno(t) ?: -1
            NativeProbeResult(
                passed = false,
                errno = errno,
                note = "stage copy failed: ${t::class.java.simpleName}: ${t.message}"
            )
        }
    }

    private fun runJavaProcess(command: List<String>, expectExecOk: Boolean): NativeProbeResult {
        return try {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(5, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return NativeProbeResult(false, OsConstants.ETIMEDOUT, "ProcessBuilder timed out after 5s")
            }
            val output = process.inputStream.bufferedReader().readText().trim()
            val exit = process.exitValue()
            val ok = (exit == 0) && (!expectExecOk || output.contains("EXEC_OK"))
            NativeProbeResult(
                passed = ok,
                errno = if (ok) 0 else exit,
                note = "exit=$exit out=${output.ifEmpty { "<empty>" }}"
            )
        } catch (t: Throwable) {
            val errno = extractErrno(t) ?: -1
            val msg = t.message ?: t::class.java.simpleName
            NativeProbeResult(
                passed = false,
                errno = errno,
                note = "${t::class.java.simpleName}: $msg"
            )
        }
    }

    private fun runRuntimeExec(command: Array<String>, expectExecOk: Boolean): NativeProbeResult {
        return try {
            val process = Runtime.getRuntime().exec(command)
            val finished = process.waitFor(5, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return NativeProbeResult(false, OsConstants.ETIMEDOUT, "Runtime.exec timed out after 5s")
            }
            val out = process.inputStream.bufferedReader().readText().trim()
            val err = process.errorStream.bufferedReader().readText().trim()
            val combined = listOf(out, err).filter { it.isNotEmpty() }.joinToString(" / ")
            val exit = process.exitValue()
            val ok = (exit == 0) && (!expectExecOk || combined.contains("EXEC_OK"))
            NativeProbeResult(
                passed = ok,
                errno = if (ok) 0 else exit,
                note = "exit=$exit out=${combined.ifEmpty { "<empty>" }}"
            )
        } catch (t: Throwable) {
            val errno = extractErrno(t) ?: -1
            NativeProbeResult(
                passed = false,
                errno = errno,
                note = "${t::class.java.simpleName}: ${t.message}"
            )
        }
    }

    private inline fun runJni(block: () -> String): NativeProbeResult {
        if (!SpikeNative.isLoaded) {
            return NativeProbeResult(false, -1, "spike_jni not loaded: ${SpikeNative.loadError}")
        }
        return try {
            SpikeNative.parseResult(block())
        } catch (t: Throwable) {
            val errno = extractErrno(t) ?: -1
            NativeProbeResult(false, errno, "${t::class.java.simpleName}: ${t.message}")
        }
    }

    private fun extractErrno(t: Throwable): Int? {
        var cur: Throwable? = t
        while (cur != null) {
            if (cur is ErrnoException) {
                return cur.errno
            }
            val msg = cur.message
            if (msg != null) {
                val match = Regex("""error=(\d+)""").find(msg)
                if (match != null) {
                    return match.groupValues[1].toIntOrNull()
                }
            }
            cur = cur.cause
        }
        return null
    }
}
