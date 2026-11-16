package dev.hubend.app.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Prova da guarda de ciclo de vida da ponte (AR-01), executada na JVM. */
class BridgeLifecycleGateTest {

    @Test
    fun acceptsCallsWhileActive() {
        val gate = BridgeLifecycleGate<String>()
        assertTrue(gate.track("a"))
        assertTrue(gate.isActive())
        assertEquals(1, gate.pendingCount())
        gate.complete("a")
        assertEquals(0, gate.pendingCount())
    }

    @Test
    fun rejectsNewCallsAfterClosing() {
        val gate = BridgeLifecycleGate<String>()
        gate.beginClosing()
        assertFalse(gate.track("b"))
        assertFalse(gate.isActive())
        assertEquals(BridgeLifecycleGate.State.CLOSING, gate.state())
    }

    @Test
    fun deliversOnlyWhileActive() {
        val gate = BridgeLifecycleGate<String>()
        gate.track("a")
        var delivered: String? = null
        assertTrue(gate.deliver("a") { delivered = it })
        assertEquals("a", delivered)

        gate.track("b")
        gate.beginClosing()
        var lateDelivered = false
        assertFalse(gate.deliver("b") { lateDelivered = true })
        assertFalse(lateDelivered)
    }

    @Test
    fun closingIsIdempotentAndReturnsPendingOnce() {
        val gate = BridgeLifecycleGate<String>()
        gate.track("a")
        gate.track("b")
        assertEquals(listOf("a", "b"), gate.beginClosing().sorted())
        assertEquals(emptyList<String>(), gate.beginClosing())
        assertEquals(emptyList<String>(), gate.seal())
        assertEquals(BridgeLifecycleGate.State.DESTROYED, gate.state())
    }

    @Test
    fun lateDeliveryFailureNeverPropagates() {
        val gate = BridgeLifecycleGate<String>()
        gate.track("a")
        assertFalse(gate.deliver("a") { throw IllegalStateException("webview destruida") })
        assertEquals(0, gate.pendingCount())
    }

    @Test
    fun sealDiscardsPendingCalls() {
        val gate = BridgeLifecycleGate<String>()
        gate.track("a")
        assertEquals(listOf("a"), gate.seal())
        assertFalse(gate.track("b"))
        assertEquals(0, gate.pendingCount())
    }
}