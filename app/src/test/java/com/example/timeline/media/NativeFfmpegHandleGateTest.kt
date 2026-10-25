package com.example.timeline.media

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeFfmpegHandleGateTest {
    @Test
    fun retiredHandleCannotBeReadOrInstalledOverAnActiveHandle() {
        val gate = NativeFfmpegHandleGate()
        assertTrue(gate.installIf(11L, canInstall = { true }) { })
        assertFalse(gate.installIf(22L, canInstall = { true }) { })
        assertEquals(11L, gate.current())

        val retired = gate.take()
        assertEquals(11L, retired)
        assertEquals(0L, gate.current())
        assertNull(gate.read(retired) { it })
        assertFalse(gate.invoke(retired) { })
    }

    @Test
    fun closeWaitsForAnInFlightNativeCallAndThenClosesTheRetiredHandle() {
        val gate = NativeFfmpegHandleGate()
        assertTrue(gate.installIf(41L, canInstall = { true }) { })

        val nativeCallEntered = CountDownLatch(1)
        val finishNativeCall = CountDownLatch(1)
        val closeStarted = CountDownLatch(1)
        val closeFinished = CountDownLatch(1)
        val closeRan = AtomicBoolean(false)
        val nativeCall = Thread {
            gate.read(41L) {
                nativeCallEntered.countDown()
                check(finishNativeCall.await(2, TimeUnit.SECONDS))
                it
            }
        }
        nativeCall.start()
        assertTrue(nativeCallEntered.await(2, TimeUnit.SECONDS))

        val close = Thread {
            closeStarted.countDown()
            val detached = gate.take()
            gate.closeDetached(detached) {
                closeRan.set(true)
                closeFinished.countDown()
            }
        }
        close.start()
        assertTrue(closeStarted.await(2, TimeUnit.SECONDS))
        assertFalse(closeFinished.await(100, TimeUnit.MILLISECONDS))

        finishNativeCall.countDown()
        assertTrue(closeFinished.await(2, TimeUnit.SECONDS))
        nativeCall.join(2_000)
        close.join(2_000)
        assertFalse(nativeCall.isAlive)
        assertFalse(close.isAlive)
        assertTrue(closeRan.get())
        assertNull(gate.read(41L) { it })
    }

    @Test
    fun failedInitializationDoesNotPublishTheHandle() {
        val gate = NativeFfmpegHandleGate()
        try {
            gate.installIf(7L, canInstall = { true }) { error("decoder init failed") }
        } catch (_: IllegalStateException) {
            // The failed open is retired by the player after this call returns.
        }

        assertEquals(0L, gate.current())
        assertNull(gate.read(7L) { it })
    }
}
