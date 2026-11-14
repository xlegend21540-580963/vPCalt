package com.aistudio.vortexpc.spike

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SpikeRunnerTest {

    @Test
    fun parseNativeResult_parsesPassAndFailLines() {
        val pass = SpikeNative.parseResult("PASS|0|execve ok|extra")
        assertTrue(pass.passed)
        assertEquals(0, pass.errno)
        assertEquals("execve ok|extra", pass.note)

        val fail = SpikeNative.parseResult("FAIL|13|Permission denied")
        assertFalse(fail.passed)
        assertEquals(13, fail.errno)
        assertEquals("Permission denied", fail.note)
    }

    @Test
    fun spikeLine_sanitizesPipesAndNewlines() {
        val formatted = SpikeLine(
            testId = "EXP1",
            targetSdk = 34,
            location = "filesDir",
            method = "native_execve",
            status = "FAIL",
            errno = "13",
            note = "line1\nline2|pipe"
        ).format()

        assertEquals(
            "EXP1 | 34 | filesDir | native_execve | FAIL | 13 | line1 line2 pipe",
            formatted
        )
    }

    @Test
    fun runAll_writesResultsFileWithHeaderAndMatrix() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val output = SpikeRunner.runAll(context)
        assertTrue(output.contains("TEST | targetSdk | location | method | PASS/FAIL | errno | note"))
        assertTrue(output.contains("EXP1 |"))
        assertTrue(output.contains("EXP2 |"))
        assertTrue(SpikeRunner.resultsFile(context).exists())
    }

    @Test
    fun mainActivity_launchesCleanly() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(activity.packageName.startsWith("com.aistudio.vortexpc.spike"))
            }
        }
    }
}
