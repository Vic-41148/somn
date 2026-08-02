package dev.vic41148.somn.feature.settings

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies [guardedCollect] — the flow-failure guard every SettingsViewModel init-block
 * subscription (collectInto) funnels through. A stream that dies mid-collection must never crash
 * the app: values emitted before the failure are preserved and the exception is routed to
 * [onFailure] instead of escaping.
 */
class GuardedCollectTest {

    @Test
    fun `failing flow is swallowed and last emitted value is preserved`() = runTest {
        val failures = mutableListOf<Exception>()
        // Fold emissions into state the way collectInto folds them into SettingsState.
        var state = "initial"
        val flow = flow {
            emit("first")
            emit("second")
            throw IllegalStateException("stream died")
        }

        guardedCollect(flow, onEmit = { state = it }, onFailure = { failures += it })

        // No crash: the collect returned normally and the failure was routed to onFailure.
        assertEquals(1, failures.size)
        assertTrue("expected IllegalStateException, got ${failures.single()}", failures.single() is IllegalStateException)
        // Values emitted before the failure were delivered — the last known state is preserved.
        assertEquals("second", state)
    }

    @Test
    fun `pre-emission failure never reaches onEmit and is routed to onFailure`() = runTest {
        val emitted = mutableListOf<String>()
        val failures = mutableListOf<Exception>()
        // A stream that dies before its first emission — e.g. a corrupted DataStore that fails
        // while opening. There is no last value to preserve: onEmit must never have been called.
        val flow = flow<String> { throw IllegalStateException("corrupt store") }

        guardedCollect(flow, onEmit = { emitted += it }, onFailure = { failures += it })

        // No crash: the collect returned normally and the failure was routed to onFailure.
        assertEquals(1, failures.size)
        assertTrue(
            "expected IllegalStateException, got ${failures.single()}",
            failures.single() is IllegalStateException
        )
        assertTrue("onEmit must never be called for a pre-emission failure", emitted.isEmpty())
    }

    @Test
    fun `cancellation is rethrown not reported as a failure`() = runTest {
        val failures = mutableListOf<Exception>()
        val flow = flow<String> { throw CancellationException("scope cancelled") }

        val thrown = runCatching {
            guardedCollect(flow, onEmit = {}, onFailure = { failures += it })
        }.exceptionOrNull()

        assertTrue("expected CancellationException, got $thrown", thrown is CancellationException)
        assertTrue("cancellation must not be routed to onFailure", failures.isEmpty())
    }
}
